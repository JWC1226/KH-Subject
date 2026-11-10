# 수동 확인 절차 (PowerShell + curl.exe)

자가 점검 테스트로 확인할 수 없는 항목(IDOR, 동시성, CSRF, 실제 응답 형태)을 직접 호출해 확인하는 절차다.
각 명령 아래 `→` 줄이 **수정 후 기대 결과**다. 실제 결과는 `REPORT.md` 의 `(실행 결과 붙여넣기)` 자리에 붙인다.

> - PowerShell 의 `curl` 은 `Invoke-WebRequest` 별칭이므로 반드시 **`curl.exe`** 로 실행한다.
> - URL 에 `&` 가 있으면 반드시 큰따옴표로 감싼다.
> - 명령은 콘솔에 **복사해 붙여넣어** 실행한다. `.ps1` 파일로 저장해 실행하려면 **UTF-8 (BOM 포함)** 으로 저장해야 한다. (Windows PowerShell 5.1 은 BOM 없는 파일을 ANSI 로 읽어 한글·따옴표가 깨진다)
> - 상환·가입·동시성 확인은 DB 데이터를 바꾼다. 다시 확인하려면 0단계의 스키마 재실행부터 다시 한다.
> - `POST /login`, `POST /api/signup` 은 같은 PC(IP)에서 **1분에 20회**까지만 받는다(10단계). `429` 가 나오면 `Retry-After` 초만큼 기다렸다가 다시 한다.
> - 세션은 **10분** 동안 요청이 없으면 만료된다. 오래 쉬었다가 `401` 이 나오면 1단계 로그인부터 다시 한다.

---

## 0. 실행 순서

1. **DB 초기화**: SQL Developer 등에서 과제 계정으로 접속해 `sql/01_schema.sql` 전체 실행(F5)
2. **환경 변수 설정** (애플리케이션을 띄울 PowerShell 창에서)

   ```powershell
   cd C:\workspace\secure-coding-assignment
   $env:ASSIGN_DB_PASSWORD = "<본인 DB 비밀번호>"

   # AES-128 키(Base64) 생성
   $b = New-Object byte[] 16
   [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
   $env:ASSIGN_AES_KEY = [Convert]::ToBase64String($b)
   ```

   > 키를 새로 만들면 이전 키로 암호화해 둔 주민번호는 복호화되지 않는다(조회 시 `******-*******` 로 전체 마스킹).
   > 같은 데이터를 계속 쓰려면 같은 키를 다시 설정한다. 키 값은 파일·보고서에 적지 않는다.

3. **기동**: `.\mvnw.cmd spring-boot:run`
   - 환경 변수 없이 기동하면 `Could not resolve placeholder 'ASSIGN_AES_KEY'` 로 실패하는 것이 정상(fail-fast)
4. **브라우저**: `http://localhost:8080/login-page` → `C001` 로그인
5. **curl 확인**: 아래 명령은 **두 번째 PowerShell 창**에서 프로젝트 폴더로 이동한 뒤 실행한다.

> `.\mvnw.cmd` 가 `Non-readable settings C:\Users\<사용자>\.m2\settings.xml: input contained no data` 로 실패하면,
> 해당 `settings.xml` 이 0바이트 빈 파일인 것이다. 파일을 지우거나 최소 내용(`<settings/>`)을 넣으면 된다.

---

## 1. 로그인 (이후 요청은 `-b cookie.txt`)

```powershell
curl.exe -i -c cookie.txt -d "customerId=C001" http://localhost:8080/login
```
→ `200` `{"loggedInAs":"C001"}`, `Set-Cookie: JSESSIONID=...; Path=/; HttpOnly`

```powershell
curl.exe -i "http://localhost:8080/api/loans?customerId=C001"
```
→ (쿠키 없음) `401` `{"code":"UNAUTHORIZED","message":"로그인이 필요합니다.","errorId":"<UUID>"}` (수정 전에는 500 + `ResponseStatusException` 문자열)

---

## 2. [1] SQL 삽입

```powershell
curl.exe -i -b cookie.txt "http://localhost:8080/api/loans?customerId=%27%20OR%20%271%27%3D%271"
# 같은 요청을 curl 이 인코딩하게 하려면:
curl.exe -i -b cookie.txt -G "http://localhost:8080/api/loans" --data-urlencode "customerId=' OR '1'='1"
```
→ `403 FORBIDDEN` (세션 고객과 다른 ID라 DB 호출 전에 거부. 수정 전: `200` + 1001~1004 **전체 고객 대출 4건**)

```powershell
curl.exe -i -b cookie.txt "http://localhost:8080/api/loans/sorted?customerId=C001&sortBy=nosuch"
```
→ `400 BAD_REQUEST` 공통 메시지 (수정 전: `500` + `BadSqlGrammarException ... ORA-00904` 등 SQL 오류 원문 노출)

```powershell
# ORDER BY 를 이용한 블라인드 SQL 삽입: C002 주민번호 첫 글자가 '8' 이면 balance 순(1004,1001), 아니면 loan_id 순(1001,1004)
curl.exe -s -b cookie.txt -G "http://localhost:8080/api/loans/sorted" --data-urlencode "customerId=C001" --data-urlencode "sortBy=(CASE WHEN (SELECT SUBSTR(rrn,1,1) FROM customer WHERE customer_id='C002')='8' THEN balance ELSE loan_id END)"
```
→ `400` (수정 전: `200` + 1004, 1001 순서 → 타인 주민번호를 한 글자씩 알아낼 수 있음)

```powershell
curl.exe -s -b cookie.txt "http://localhost:8080/api/loans/sorted?customerId=C001&sortBy=balance"
```
→ `200` 정상 정렬 결과 (1004 8,000,000 → 1001 12,500,000). 허용값: `loanId`, `loan_id`, `balance`, `status`

(선택) 바인딩 확인: 매퍼 로그만 DEBUG 로 띄우고 `/api/loans?customerId=C001` 호출

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--logging.level.edu.secure.assign.mapper.LoanMapper=DEBUG"
```
→ 서버 로그에 `Preparing: ... WHERE customer_id = ?` / `Parameters: C001(String)` (값이 SQL 문장이 아니라 바인딩 파라미터로 전달됨)
> 확인 후에는 DEBUG 없이 다시 띄운다. (매퍼 DEBUG 로그는 파라미터 값을 남기므로 운영에서는 쓰지 않는다)

---

## 3. [2] 접근 통제 (IDOR)

```powershell
curl.exe -i -b cookie.txt http://localhost:8080/api/loans/1001
```
→ `200` 본인 대출(1001)

```powershell
curl.exe -i -b cookie.txt http://localhost:8080/api/loans/1002
curl.exe -i -b cookie.txt http://localhost:8080/api/loans/9999
```
→ 둘 다 `404 NOT_FOUND`, 본문도 같은 형태 (타인 소유와 존재하지 않음을 구분할 수 없음. 수정 전: 1002 는 `200` + C002 대출 정보)

```powershell
curl.exe -i -b cookie.txt "http://localhost:8080/api/loans?customerId=C002"
curl.exe -i -b cookie.txt "http://localhost:8080/api/loans/sorted?customerId=C002&sortBy=balance"
curl.exe -i -b cookie.txt http://localhost:8080/api/customers/C002
```
→ 모두 `403 FORBIDDEN` (수정 전: 모두 `200` + C002 의 대출 / 고객 정보(주민번호·전화번호 원문))

```powershell
curl.exe -i -b cookie.txt -X POST "http://localhost:8080/api/loans/1002/repay?amount=1000"
```
→ `200` `{"success":false}`, 1002 잔액 변화 없음 (수정 전: `{"success":true}` → **타인 대출 잔액이 차감됨**)

---

## 4. [3] 민감정보

```powershell
curl.exe -i -b cookie.txt http://localhost:8080/api/customers/C001
```
→ `200` `{"customerId":"C001","customerName":"홍길동","rrn":"900101-1******","phone":"010-****-2222"}`
  (`passwordHash` 키 없음. 초기 데이터의 평문 주민번호도 500 없이 마스킹. 수정 전: 주민번호·전화번호 원문 + `passwordHash`)
→ 서버 로그: `고객 조회: customerId=C001` (수정 전: `Customer{... rrn='900101-1234567', phone=..., passwordHash=...}`)

---

## 5. [4] 에러 처리

```powershell
curl.exe -i "http://localhost:8080/api/interest?principal=1000000&rate=abc"
```
→ `400` `{"code":"BAD_REQUEST","message":"요청 값이 올바르지 않습니다.","errorId":"<UUID>"}`
  (수정 전: `500` + `java.lang.NumberFormatException: For input string: "abc"`)

```powershell
curl.exe -i -b cookie.txt http://localhost:8080/api/loans/abc
curl.exe -i "http://localhost:8080/api/interest?principal=1000000"
curl.exe -i -b cookie.txt "http://localhost:8080/api/loans/1001/repay?amount=1"
```
→ 각각 `400`(형식 오류), `400`(필수 파라미터 누락), `405`(GET 미지원). 모두 같은 JSON 형태, 예외 클래스명·메시지 없음

DB 장애(500) 확인: 앱을 **틀린 DB 비밀번호**로 띄운 뒤 로그인 시도

```powershell
curl.exe -i -d "customerId=C001" http://localhost:8080/login
```
→ `500` `{"code":"INTERNAL_SERVER_ERROR","message":"일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.","errorId":"<UUID>"}`
→ 서버 로그에만 `errorId=<같은 UUID>` + 스택 트레이스(`ORA-01017 ...`). 응답의 errorId 로 로그를 찾을 수 있음

---

## 6. [5] 입력값 검증 (회원가입)

PowerShell 5.1 은 명령줄 인자 안의 큰따옴표를 제대로 넘기지 못하므로 JSON 은 파일로 만들어 보낸다.

```powershell
$ok = '{"customerId":"C100","customerName":"박민수","rrn":"950101-1234567","phone":"010-1234-5678","password":"Passw0rd!23"}'
[IO.File]::WriteAllText("$env:TEMP\signup-ok.json", $ok)
curl.exe -i -H "Content-Type: application/json" --data-binary "@$env:TEMP\signup-ok.json" http://localhost:8080/api/signup
```
→ `200` `{"customerId":"C100"}` / 같은 명령을 한 번 더 → `409 CONFLICT` "이미 등록된 정보입니다."

```powershell
# 필드 하나씩만 틀리게 바꿔 보내기
$base = @{ customerId = "C200"; customerName = "박민수"; rrn = "950101-1234567"; phone = "010-1234-5678"; password = "Passw0rd!23" }
$cases = @(
  @{ customerId = "" }, @{ customerId = "C1' OR '1'='1" }, @{ customerId = "ABCDEFGHIJK" },
  @{ customerName = "<script>alert(1)</script>" }, @{ rrn = "not-a-rrn" }, @{ rrn = "951301-1234567" },
  @{ phone = "123" }, @{ password = "1234" }, @{ password = "Password123" }
)
foreach ($c in $cases) {
  $body = $base.Clone(); foreach ($k in $c.Keys) { $body[$k] = $c[$k] }
  [IO.File]::WriteAllText("$env:TEMP\signup-case.json", ($body | ConvertTo-Json -Compress))
  $code = curl.exe -s -o NUL -w "%{http_code}" -H "Content-Type: application/json" --data-binary "@$env:TEMP\signup-case.json" http://localhost:8080/api/signup
  "{0}  <- {1}" -f $code, (($c.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join ",")
}
```
→ 모두 `400` (수정 전: 대부분 `200` 으로 저장되고, 컬럼 길이를 넘는 값은 `500` + `ORA-12899` 노출)

```powershell
[IO.File]::WriteAllText("$env:TEMP\signup-broken.json", '{"customerId": "C300", ')
curl.exe -i -H "Content-Type: application/json" --data-binary "@$env:TEMP\signup-broken.json" http://localhost:8080/api/signup
```
→ `400` (깨진 JSON, 파서 메시지 노출 없음)

---

## 7. [6] 해시 / 암호화 저장값 확인

6단계에서 C100 가입 후 SQL Developer 에서:

```sql
SELECT customer_id, rrn, password_hash FROM customer WHERE customer_id = 'C100';
```
→ `rrn` = Base64 56자(IV 12 + 암호문 14 + 태그 16 바이트), `password_hash` = `pbkdf2-sha256$600000$<솔트>$<해시>`
→ 같은 주민번호로 C101 을 하나 더 가입하면 `rrn` 암호문이 서로 다름(무작위 IV)

```powershell
curl.exe -i -c cookie100.txt -d "customerId=C100" http://localhost:8080/login
curl.exe -s -b cookie100.txt http://localhost:8080/api/customers/C100
```
→ `"rrn":"950101-1******"` (AES-GCM 복호화 후 마스킹)

AAD 바인딩(암호문 행 바꿔치기 차단) 확인: C101 을 가입한 뒤, C100 의 암호문을 C101 행에 복사한다.

```powershell
[IO.File]::WriteAllText("$env:TEMP\signup-101.json", $ok.Replace('"C100"', '"C101"'))
curl.exe -s -H "Content-Type: application/json" --data-binary "@$env:TEMP\signup-101.json" http://localhost:8080/api/signup
```
```sql
UPDATE customer SET rrn = (SELECT rrn FROM customer WHERE customer_id = 'C100') WHERE customer_id = 'C101';
COMMIT;
```
```powershell
curl.exe -s -c cookie101.txt -d "customerId=C101" http://localhost:8080/login
curl.exe -s -b cookie101.txt http://localhost:8080/api/customers/C101
```
→ `"rrn":"******-*******"` (C100 에 묶인 암호문이라 C101 로는 복호화 실패 → 전체 마스킹, 500 아님)
→ 서버 로그: `주민번호 복호화 실패: customerId=C101`

---

## 8. [7] 금액 계산 / 동시성

```powershell
curl.exe -s "http://localhost:8080/api/interest?principal=1000000&rate=0.035"
curl.exe -s "http://localhost:8080/api/interest?principal=100000&rate=0.0275"
```
→ `{"monthlyInterest":2917}`, `{"monthlyInterest":229}` (수정 전: 2916, 229 — double 계산 후 버림)

```powershell
curl.exe -i "http://localhost:8080/api/interest?principal=1000000&rate=-1"
curl.exe -i "http://localhost:8080/api/interest?principal=1000000&rate=1.5"
curl.exe -i "http://localhost:8080/api/interest?principal=0&rate=0.035"
```
→ 모두 `400` (수정 전: rate=-1 → `{"monthlyInterest":-83333}` 음수 이자 등이 그대로 계산됨)

```powershell
curl.exe -i -b cookie.txt -X POST "http://localhost:8080/api/loans/1001/repay?amount=-1000000"
curl.exe -s -b cookie.txt http://localhost:8080/api/loans/1001
```
→ `400`, 1001 잔액 그대로 (수정 전: `{"success":true}` 후 잔액 12,500,000 → **13,500,000 으로 증가**)

### 동시 상환 (대출 1004, C001, 잔액 8,000,000 → 1,000,000 씩 10건 동시)

방법 A — `Start-Job` (작업마다 PowerShell 프로세스가 떠서 시작 시점이 조금씩 어긋날 수 있음)

```powershell
$ck = (Resolve-Path .\cookie.txt).Path
$jobs = 1..10 | ForEach-Object {
  Start-Job -ArgumentList $ck -ScriptBlock {
    param($c)
    curl.exe -s -b $c -X POST "http://localhost:8080/api/loans/1004/repay?amount=1000000"
  }
}
$r = $jobs | Wait-Job | Receive-Job; $jobs | Remove-Job
$r
"성공: " + ($r | Select-String '"success":true').Count + " / 실패: " + ($r | Select-String '"success":false').Count
curl.exe -s -b cookie.txt http://localhost:8080/api/loans/1004
```

방법 B — curl 병렬 전송(`-Z`, 실제로 동시에 보내므로 경쟁 조건 재현에 더 적합)

```powershell
$r = curl.exe -s -Z --parallel-immediate --parallel-max 10 -b cookie.txt -X POST "http://localhost:8080/api/loans/1004/repay?amount=1000000&n=[1-10]" -w "\n"
$r
"성공: " + ($r | Select-String '"success":true').Count + " / 실패: " + ($r | Select-String '"success":false').Count
curl.exe -s -b cookie.txt http://localhost:8080/api/loans/1004
```
→ **성공 8 / 실패 2, 최종 잔액 0** (항상 성공 횟수 × 1,000,000 = 차감액)
  (수정 전: 성공이 8건을 넘거나, 성공 건수만큼 차감되지 않고 잔액이 남는 **갱신 유실** 발생)
> 방법 A·B 중 하나만 실행한다(둘 다 하려면 0단계의 스키마 재실행 후).

---

## 9. [8] XSS / CSRF

### XSS

```powershell
curl.exe -s -G "http://localhost:8080/search" --data-urlencode "q=<script>alert(1)</script>"
```
→ 본문에 `&lt;script&gt;alert(1)&lt;/script&gt;` 가 2곳(검색어, 재표시), 원문 `<script>alert(1)` 없음 (수정 전: 원문 그대로 2곳)

브라우저: `http://localhost:8080/search?q=<img src=x onerror=alert(1)>`
→ 알림창 없이 문자열 그대로 표시(3곳). 수정 전: 알림창 실행
> `innerHTML` 로 넣은 `<script>` 는 HTML5 규칙상 실행되지 않으므로, DOM XSS 는 `<img onerror>` 로 재현한다.

### CSRF — 토큰 검증 (curl)

```powershell
curl.exe -i -b cookie.txt -d "loanId=1001&amount=1000" http://localhost:8080/repay-form
curl.exe -i -b cookie.txt -d "loanId=1001&amount=1000&_csrf=forged" http://localhost:8080/repay-form
```
→ 둘 다 `403 FORBIDDEN` (토큰 없음 / 틀림)

```powershell
$html = curl.exe -s -b cookie.txt http://localhost:8080/repay-form
$tok = [regex]::Match(($html -join "`n"), 'name="_csrf" value="([^"]+)"').Groups[1].Value
"token length: " + $tok.Length
curl.exe -i -b cookie.txt -d "loanId=1001&amount=1000&_csrf=$tok" http://localhost:8080/repay-form
```
→ 토큰 43자, `200` `상환 처리: true` (정상 폼 흐름은 그대로 동작)

```powershell
curl.exe -i -d "loanId=1001&amount=1000" http://localhost:8080/repay-form
```
→ `401` (쿠키 없음 = 로그인 안 됨. 401 은 "쿠키 미전송", 403 은 "토큰 차단"으로 원인이 구분된다)

### CSRF — 공격 페이지 (브라우저)

1. 브라우저에서 `http://localhost:8080/login-page` → `C001` 로그인
2. 탐색기에서 `attack\csrf-attack.html` 을 더블클릭해 연다 (자동으로 1001 에 1,000,000 상환 POST)
3. 기대 결과: **`403`** `{"code":"FORBIDDEN","message":"접근 권한이 없습니다.",...}`, 1001 잔액 변화 없음
   - 개발자 도구(F12) → Network → `repay-form` 요청의 Request Headers 에 `Cookie: JSESSIONID=...` 가 있으면, 쿠키가 실렸는데도 **토큰 때문에** 막힌 것이다.
   - `401` 이 나오면 브라우저가 SameSite 기본 정책으로 쿠키를 보내지 않은 것이다(Chrome/Edge 는 로그인 후 약 2분이 지나면 외부 사이트 POST 에 쿠키를 싣지 않음). 로그인 직후 바로 열거나 Firefox 로 다시 확인한다.
4. 수정 전: `상환 처리: true`, 1001 잔액 1,000,000 감소

### REST 상환 API 의 CSRF (추가 발견 항목 확인용)

```powershell
@'
<!DOCTYPE html><html><body onload="document.forms[0].submit()">
<form action="http://localhost:8080/api/loans/1001/repay" method="post">
  <input type="hidden" name="amount" value="1000000">
</form></body></html>
'@ | Set-Content -Encoding UTF8 "$env:TEMP\csrf-api.html"
Start-Process "$env:TEMP\csrf-api.html"
```
→ `403 FORBIDDEN`, 1001 잔액 변화 없음 — REST API 는 CSRF 토큰이 없지만, 출처 검증 필터(`RequestGuardFilter`)가 외부 페이지가 보낸 요청(`Origin: null`)을 거부한다.
  (출처 검증을 넣기 전에는 쿠키가 실리면 `{"success":true}` 로 상환되었다)
→ 서버 로그: `FilterErrors : errorId=... status=403 type=CrossOriginRequest`
> 출처 검증은 토큰으로 보호되는 `/repay-form` 에는 적용하지 않았으므로, 위 `csrf-attack.html` 확인(403)은 **토큰 검증의 효과만** 보여 준다.
> SameSite 는 **토큰 검증 확인을 마친 뒤** `application.properties` 의 `#server.servlet.session.cookie.same-site=lax` 주석을 해제해 켠다. 켠 상태에서는 `csrf-attack.html` 이 쿠키 미전송(401)으로 막혀 토큰 효과를 따로 볼 수 없다.

---

## 10. 추가 보안 강화(심층 방어) 확인

### 보안 응답 헤더 / CSP

```powershell
curl.exe -s -D - -o NUL "http://localhost:8080/search?q=x"
```
→ `Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; object-src 'none'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'`
→ `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, `Cache-Control: no-store` 등
브라우저 `http://localhost:8080/search?q=test` → F12 Console 에 CSP 위반 경고 없이 요약 문장이 표시됨(외부 `/js/search.js` 로 동작)

### 출처(Origin) 검증 — 외부 사이트에서 보낸 상태 변경 요청 차단

```powershell
curl.exe -i -b cookie.txt -H "Origin: http://evil.example" -X POST "http://localhost:8080/api/loans/1001/repay?amount=1000"
curl.exe -i -b cookie.txt -H "Origin: null" -X POST "http://localhost:8080/api/loans/1001/repay?amount=1000"
```
→ 둘 다 `403`, 1001 잔액 변화 없음 (`Origin: null` 은 file:// 공격 페이지가 보내는 값)

```powershell
curl.exe -i -b cookie.txt -H "Origin: http://localhost:8080" -X POST "http://localhost:8080/api/loans/9999/repay?amount=1"
```
→ `200` `{"success":false}` (같은 출처는 통과. 없는 대출번호라 데이터 변화 없음)

```powershell
curl.exe -i -H "Origin: http://evil.example" -d "customerId=C001" http://localhost:8080/login
```
→ `403` (외부 사이트가 피해자를 몰래 로그인시키는 Login CSRF 차단)

### 본문 크기 제한

```powershell
[IO.File]::WriteAllText("$env:TEMP\big.json", '{"customerId":"' + ('A' * 20000) + '"}')
curl.exe -i -H "Content-Type: application/json" --data-binary "@$env:TEMP\big.json" http://localhost:8080/api/signup
```
→ `413` `{"code":"PAYLOAD_TOO_LARGE",...}`

### `/api/**` 로그인 필수(기본 거부)

```powershell
curl.exe -i http://localhost:8080/api/loans/abc
curl.exe -i http://localhost:8080/api/nosuch
```
→ 둘 다 `401` (쿠키 없음. 없는 API 경로도 존재 여부가 드러나지 않음). `/api/interest` 는 로그인 없이 `200`

### 로그인 시 세션 ID 교체(세션 고정 방지)

```powershell
curl.exe -s -c ckfix.txt http://localhost:8080/repay-form > $null
"로그인 전: " + (Select-String -Path ckfix.txt -Pattern 'JSESSIONID').Line.Split("`t")[-1]
curl.exe -s -b ckfix.txt -c ckfix.txt -d "customerId=C001" http://localhost:8080/login
"로그인 후: " + (Select-String -Path ckfix.txt -Pattern 'JSESSIONID').Line.Split("`t")[-1]
Remove-Item ckfix.txt
```
→ 두 값이 다름. 서버 로그 `AUDIT : LOGIN_SUCCESS customerId=C001 sessionRotated=true`

### 감사 로그 / 로그 위조 방지

```powershell
curl.exe -s -d "customerId=C001%0D%0AFAKE+AUDIT+LOGIN_SUCCESS+customerId%3DADMIN" http://localhost:8080/login
```
→ `401`. 서버 로그에 `AUDIT : LOGIN_FAILURE status=401 requestedId=C001__FAKE_AUDIT_LOG...` **한 줄만** 남음 (영문/숫자 외 문자는 `_` 로 바꾸고 20자에서 자름. 개행으로 가짜 로그 줄을 만들 수 없음)
→ 3·8·9단계의 상환 뒤 서버 로그: `AUDIT : REPAY customerId=C001 loanId=... amount=... result=SUCCESS|REJECTED`, CSRF 거부 시 `AUDIT : CSRF_REJECTED customerId=C001 tokenPresent=...`

### 요청 횟수 제한 (가장 마지막에 실행 — 1분 동안 가입이 막힘)

```powershell
[IO.File]::WriteAllText("$env:TEMP\bad.json", '{"customerId":"x"}')
$codes = 1..22 | ForEach-Object { curl.exe -s -o NUL -w "%{http_code}" -H "Content-Type: application/json" --data-binary "@$env:TEMP\bad.json" http://localhost:8080/api/signup }
$codes -join " "
curl.exe -s -D - -o NUL -H "Content-Type: application/json" --data-binary "@$env:TEMP\bad.json" http://localhost:8080/api/signup | Select-String "^HTTP|^Retry-After"
```
→ `400` 이 이어지다 1분 안의 21번째 요청부터 `429`, `Retry-After: <초>` (같은 1분 안에 앞서 보낸 가입 요청도 함께 센다)

### 세션 만료 (선택)

로그인 후 10분 동안 아무 요청도 보내지 않고 `curl.exe -i -b cookie.txt http://localhost:8080/api/loans/1001` → `401`

---

## 11. 정적 분석 (선택, 가산)

```powershell
.\mvnw.cmd -Psast compile spotbugs:spotbugs

# 결과 요약 (유형별 건수 / 상세)
[xml]$x = Get-Content -Raw -Encoding UTF8 target\spotbugsXml.xml
$x.BugCollection.BugInstance | Group-Object type | Sort-Object Count -Descending | Format-Table Count, Name -AutoSize
$x.BugCollection.BugInstance | ForEach-Object { "{0} P{1} {2} {3}:{4}" -f $_.category, $_.priority, $_.type, ($_.Class | Select-Object -First 1).classname, ($_.SourceLine | Select-Object -First 1).start }
```
- 수정 전 결과는 원본 배포본(수정 전 소스)에서 같은 명령으로 만든다. 두 결과의 유형별 건수를 `REPORT.md` "도구가 잡지 못한 항목" 표에 적는다.
- `.\mvnw.cmd -Psast spotbugs:gui` 로 GUI 에서 상세 위치를 볼 수 있다.

---

## 12. 제출 전 비밀 값 점검 / 압축

```powershell
cd C:\workspace\secure-coding-assignment
$root = (Get-Location).Path
$files = Get-ChildItem -LiteralPath $root -Recurse -Force -File |
  Where-Object { $_.FullName.Substring($root.Length + 1).Split('\')[0] -notin @('target', '.idea', '.vscode') -and $_.Name -ne 'cookie.txt' }
$files | Select-String -Pattern 'ASSIGN_', 'password', 'SECURE', 'key', '[A-Za-z0-9+/]{20,}={1,2}' -Encoding UTF8 |
  ForEach-Object { "{0}:{1}: {2}" -f $_.Path.Substring($root.Length + 1), $_.LineNumber, $_.Line.Trim() }
```
→ `application.properties` 는 `${ASSIGN_DB_PASSWORD}` / `${ASSIGN_AES_KEY}` 자리표시자만, Base64 키 형태 문자열 0건이어야 한다.
  (`README.md` 의 환경 변수 예시와 `sql/01_schema.sql` 주석의 계정 생성 예시는 과제 원본 파일 내용이다)

```powershell
# target/, .idea/, .vscode/, cookie.txt 제외. 점(.) 파일(.mvn, .gitignore 등) 포함.
# Windows PowerShell 5.1 의 Compress-Archive 는 zip 내부 경로를 '\' 로 저장해 macOS/Linux 에서 폴더가 깨질 수 있어 .NET ZipFile 을 쓴다.
Add-Type -AssemblyName System.IO.Compression, System.IO.Compression.FileSystem
$root = 'C:\workspace\secure-coding-assignment'
$zip  = 'C:\workspace\20261106_시큐어코딩_적용과제_정우찬.zip'
$excludeTop = @('target', '.idea', '.vscode', '.github', 'cookie.txt')
if (Test-Path $zip) { Remove-Item $zip }
$z = [IO.Compression.ZipFile]::Open($zip, 'Create')
try {
  Get-ChildItem -LiteralPath $root -Recurse -Force -File | ForEach-Object {
    $rel = $_.FullName.Substring($root.Length + 1)
    if ($excludeTop -notcontains $rel.Split('\')[0] -and $_.Name -ne 'cookie.txt') {
      [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($z, $_.FullName, $rel.Replace('\', '/'), 'Optimal')
    }
  }
} finally { $z.Dispose() }

# 압축 내용 확인
$r = [IO.Compression.ZipFile]::OpenRead($zip); $r.Entries.FullName; "entries=" + $r.Entries.Count; $r.Dispose()
```
- zip 은 프로젝트 **바깥**(`C:\workspace`)에 만든다(자기 자신이 압축에 들어가지 않도록).
- 빼고 싶은 파일이 더 있으면 `$excludeTop` 에 최상위 이름을 추가한다.
