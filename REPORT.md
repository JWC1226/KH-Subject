# 시큐어 코딩 적용 보고서

- 이름: 정우찬
- 제출일: 2026-11-06
- 자가 점검 테스트 결과: `21 / 21` 통과

> 수동 확인에 쓴 전체 명령(PowerShell `curl.exe`)과 실행 순서는 `MANUAL_CHECK.md` 에 정리했다.
> 각 항목의 "실행 결과" 는 2026-11-05 에 실제로 실행한 응답이다. "수정 전" 은 원본 코드(원본 매퍼·JSP 포함)를 그대로 띄워 확인했고, 단계마다 `sql/01_schema.sql` 로 데이터를 초기화했다. `errorId` 는 실행마다 바뀌는 값이라 `…` 로 줄였다. 브라우저에서만 볼 수 있는 결과(알림창, `csrf-attack.html` 직접 열기)는 같은 요청을 curl 로 보내 확인했으므로 `(예상)` 표시를 남겼다.

## 항목별 수정 내역

항목마다 **취약 원인 → 공격 재현(수정 전) → 수정 내용 → 확인(수정 후)** 순서로 적는다.
수정하지 못한 항목은 비워 두지 말고 이유와 시도한 내용을 적는다.

### [1] SQL 삽입

- 취약 원인:
  - `LoanMapper.xml` 의 `findByCustomer` 가 `WHERE customer_id = '${customerId}'` 로 **문자열 치환**을 해서, 입력값이 값이 아니라 SQL 문법으로 해석된다.
  - `findSorted` 의 `ORDER BY ${sortBy}` 는 식별자 자리라 `#{}` 바인딩을 쓸 수 없는데, `LoanService.findSorted` 가 사용자 입력을 검증 없이 그대로 넘긴다.
- 공격 재현(수정 전): (C001 로그인 상태)
  - `GET /api/loans?customerId=' OR '1'='1` → 실행 SQL `WHERE customer_id = '' OR '1'='1'` → `200` + **전체 고객 대출 4건(1001~1004)**
  - `GET /api/loans/sorted?customerId=C001&sortBy=(CASE WHEN (SELECT SUBSTR(rrn,1,1) FROM customer WHERE customer_id='C002')='8' THEN balance ELSE loan_id END)` → 조건이 참이면 1004→1001, 거짓이면 1001→1004 순서로 응답 → **정렬 순서만으로 타인 주민번호를 한 글자씩 알아낼 수 있다**(블라인드 SQL 삽입)
  - `GET /api/loans/sorted?customerId=C001&sortBy=nosuch` → `500` + `ORA-00904` 와 SQL 문장 노출
  - 실행 결과 (2026-11-05, 원본 코드로 실행, C001 로그인):
    ```
    GET /api/loans?customerId=' OR '1'='1
    → 200, 전체 고객 대출 4건 (1001 C001, 1002 C002, 1003 C003, 1004 C001)
    GET /api/loans/sorted?customerId=C001&sortBy=(CASE … ='8' …)   [참]   → 1004, 1001 순서
    GET /api/loans/sorted?customerId=C001&sortBy=(CASE … ='9' …)   [거짓] → 1001, 1004 순서
    → 정렬 순서만으로 C002 주민번호 첫 글자가 '8' 임을 알아냄
    GET /api/loans/sorted?customerId=C001&sortBy=nosuch
    → 500 org.springframework.jdbc.BadSqlGrammarException: ### Error querying database. Cause:
      java.sql.SQLSyntaxErrorException: ORA-00904: "NOSUCH": 부적합한 식별자
      ### The error may exist in file [...\mapper\LoanMapper.xml] ...
    ```
- 수정 내용:
  - `src/main/resources/mapper/LoanMapper.xml`
    ```xml
    <!-- 변경 전 -->  WHERE customer_id = '${customerId}'
    <!-- 변경 후 -->  WHERE customer_id = #{customerId}
    ```
    매퍼 안의 `${` 는 `ORDER BY ${sortBy}` 한 곳만 남겼다(주석 포함).
  - `src/main/java/edu/secure/assign/service/LoanService.java` — 허용 목록을 **입력값 → 실제 컬럼명 매핑**으로 구현하고 DB 호출 전에 검사한다. 사용자 입력 문자열은 SQL 에 들어가지 않고, 매핑된 서버 상수만 `${sortBy}` 에 들어간다.
    ```java
    // 변경 전
    return loanMapper.findSorted(customerId, sortBy);

    // 변경 후
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "loanId", "loan_id", "loan_id", "loan_id", "balance", "balance", "status", "status");

    String column = (sortBy == null) ? null : SORT_COLUMNS.get(sortBy);
    if (column == null) {
        throw new IllegalArgumentException("허용되지 않은 정렬 기준입니다.");
    }
    return loanMapper.findSorted(customerId, column);
    ```
    (`loan_id` 는 기존 호출과의 호환을 위해 함께 허용했다. `Map.of().get(null)` 은 NPE 를 던지므로 null 을 먼저 거른다.)
- 확인(수정 후):
  - 자가 점검 `mapperHasNoStringSubstitution`, `sortByWhitelist` 통과 (`new LoanService(null)` 로 호출해도 `nosuch`, `balance; DROP TABLE loan`, `(SELECT 1 FROM dual)` 이 매퍼 호출 전에 `IllegalArgumentException`)
  - `sortBy=nosuch`, `sortBy=(CASE WHEN ...)` → `400 BAD_REQUEST` 공통 메시지 / `sortBy=balance` → `200` 정상 정렬
  - `customerId=' OR '1'='1` → `403` ([2]의 소유자 검사가 DB 호출 전에 거부. 그 검사가 없더라도 `#{}` 바인딩이라 문자열 그대로 비교되어 0건)
  - 바인딩 확인: 매퍼 로그를 DEBUG 로 켜면 `Preparing: ... WHERE customer_id = ?` / `Parameters: C001(String)` 이 찍힌다.
  - 실행 결과 (2026-11-05, C001 로그인):
    ```
    GET /api/loans?customerId=C001                → 200 (1001, 1004)
    GET /api/loans?customerId=' OR '1'='1         → 403 {"code":"FORBIDDEN","message":"접근 권한이 없습니다.","errorId":"…"}
    GET /api/loans/sorted?…&sortBy=balance        → 200 (1004 → 1001)
    GET /api/loans/sorted?…&sortBy=nosuch         → 400 {"code":"BAD_REQUEST","message":"요청 값이 올바르지 않습니다.","errorId":"…"}
    GET /api/loans/sorted?…&sortBy=(CASE …)       → 400 (같은 공통 메시지)
    매퍼 DEBUG 로그:
      ==>  Preparing: SELECT loan_id, customer_id, balance, status FROM loan WHERE customer_id = ?
      ==> Parameters: C001(String)
    ```

### [2] 접근 통제 (IDOR)

- 취약 원인:
  - 컨트롤러가 `LoginController.requireCustomer(session)` 로 **로그인 여부만** 확인하고, 반환값(세션 고객 ID)을 소유자 조건으로 쓰지 않았다.
  - `findById` 의 조건이 `WHERE loan_id = #{loanId}` 뿐이라 대출번호만 알면 누구의 대출이든 조회된다.
  - `/api/loans?customerId=`, `/api/loans/sorted`, `/api/customers/{customerId}` 는 요청에 담긴 고객 ID 를 그대로 조회한다.
  - 상환은 세션 ID 를 넘기지만 `selectBalance` / `updateBalance` 가 `loan_id` 만 조건으로 써서 **타인 대출 잔액이 차감**된다.
- 공격 재현(수정 전): (C001 로그인 상태)
  - `GET /api/loans/1002` → `200` + C002 대출 정보
  - `GET /api/loans?customerId=C002`, `GET /api/customers/C002` → `200` + C002 대출 / 주민번호·전화번호 원문
  - `POST /api/loans/1002/repay?amount=1000` → `{"success":true}`, C002 대출 잔액 1,000 감소
  - 실행 결과 (2026-11-05, 원본 코드로 실행, C001 로그인):
    ```
    GET /api/loans/1002            → 200 {"loanId":1002,"customerId":"C002","balance":30000000,"status":"ACTIVE"}
    GET /api/loans?customerId=C002 → 200 [{"loanId":1002,"customerId":"C002",…}]
    GET /api/customers/C002        → 200 {"customerId":"C002","customerName":"김영희","rrn":"850505-2345678","phone":"010-3333-4444","passwordHash":null}
    POST /api/loans/1002/repay?amount=1000 → {"success":true}
    DB: 1002 잔액 30,000,000 → 29,999,000
    ```
- 수정 내용:
  - `LoanMapper.xml` / `mapper/LoanMapper.java` — 단건 조회에 소유자 조건 추가
    ```xml
    <!-- 변경 전 -->  WHERE loan_id = #{loanId}
    <!-- 변경 후 -->  WHERE loan_id = #{loanId}
                        AND customer_id = #{customerId}
    ```
    ```java
    Loan findById(@Param("loanId") long loanId, @Param("customerId") String customerId);
    ```
  - `web/LoanController.java` — 세션 고객 ID 를 소유자 조건으로 넘기고, 결과가 없으면 404
    ```java
    // 변경 전
    LoginController.requireCustomer(session);
    return loanMapper.findById(loanId);

    // 변경 후
    String me = LoginController.requireCustomer(session);
    Loan loan = loanMapper.findById(loanId, me);
    if (loan == null) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND);  // 타인 소유 / 없음 구분 안 함
    }
    return loan;
    ```
  - `web/AccessGuard.java` (신규) — 고객 ID 를 파라미터로 받는 API 공통 검사. 세션 고객과 다르면 DB 호출 전에 403.
    ```java
    static String requireSelf(HttpSession session, String requestedCustomerId) {
        String me = LoginController.requireCustomer(session);
        if (!me.equals(requestedCustomerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return me;
    }
    ```
    `LoanController.loans`, `LoanController.sorted`, `CustomerController.customer` 에 적용하고, 조회에는 요청값이 아니라 반환된 세션 ID(`me`)를 쓴다.
  - 상환 UPDATE 에도 `AND customer_id = #{customerId}` 조건을 넣었다 ([7] 참고).
- 확인(수정 후):
  - 자가 점검 `ownerConditionInQueries` 통과 (조회 SQL 의 `loan_id` → `customer_id` 조건, UPDATE 의 `customer_id` 조건)
  - `GET /api/loans/1001` → `200` / `GET /api/loans/1002`, `GET /api/loans/9999` → 둘 다 `404`, 같은 본문 형태
  - `GET /api/loans?customerId=C002`, `GET /api/loans/sorted?customerId=C002&sortBy=balance`, `GET /api/customers/C002` → `403`
  - `POST /api/loans/1002/repay?amount=1000` → `{"success":false}`, 1002 잔액 변화 없음
  - 실행 결과 (2026-11-05, C001 로그인):
    ```
    GET /api/loans/1001 (본인) → 200
    GET /api/loans/1002 (타인) → 404 {"code":"NOT_FOUND","message":"요청한 정보를 찾을 수 없습니다.","errorId":"…"}
    GET /api/loans/9999 (없음) → 404 (위와 같은 본문)
    GET /api/loans?customerId=C002                   → 403 {"code":"FORBIDDEN","message":"접근 권한이 없습니다.","errorId":"…"}
    GET /api/loans/sorted?customerId=C002&sortBy=balance → 403
    GET /api/customers/C002                          → 403
    POST /api/loans/1002/repay?amount=1000 → {"success":false}
    DB: 1002 잔액 30,000,000 그대로
    ```
- 타인 자원 접근 시 응답 코드를 무엇으로 정했고, 그 이유는?

  | 요청 | 응답 | 이유 |
  |---|---|---|
  | `GET /api/loans/{loanId}` (타인 소유 / 없는 번호) | **404** | 소유자 조건을 SQL 에 넣었기 때문에 두 경우 모두 결과가 null 로 같다. 403 을 쓰면 "번호는 있지만 남의 것"임이 드러나 대출번호 열거(존재 여부 탐색)에 쓰일 수 있다. RFC 9110 §15.5.4 도 존재를 숨기려면 403 대신 404 를 쓸 수 있다고 한다. |
  | `POST /api/loans/{loanId}/repay` (타인 / 없음 / 잔액 부족) | **200 `{"success":false}`** | 조건부 UPDATE 한 문장의 영향 행 수(0)만 보므로 세 경우를 구분하지 않는다. 실패 원인을 나누어 알려 주면 그 자체가 존재·잔액 정보가 된다. |
  | `customerId` 를 받는 API 3개에 다른 고객 ID 요청 | **403** | DB 를 조회하기 **전에** 세션 값과 비교만 하므로 대상 고객이 있든 없든 항상 같은 403 이 나간다(존재 정보가 새지 않음). 숨길 정보가 없으므로 "본인 ID 만 요청할 수 있다"는 권한 문제임을 정확히 알리는 403 을 택했다. |
  | 로그인하지 않음 | **401** | 인증 자체가 없음을 알린다(`LoginController.requireCustomer`). |

### [3] 민감정보 노출

- 취약 원인: (응답 / 로그 각각)
  - 응답: `CustomerController` 가 DB 원본 객체 `Customer` 를 그대로 JSON 으로 반환 → 주민번호·전화번호 원문과 `passwordHash` 필드까지 노출.
  - 로그: `log.info("고객 조회: {}", c)` 가 `Customer.toString()`(모든 필드 출력)을 호출해 주민번호·해시가 로그에 남음. `log.info("가입 요청: {}", req)` 는 record 기본 `toString()` 이라 **주민번호와 비밀번호 평문**까지 로그에 남음.
- 공격 재현(수정 전):
  - `GET /api/customers/C001` → `{"customerId":"C001","customerName":"홍길동","rrn":"900101-1234567","phone":"010-1111-2222","passwordHash":null}`
  - 가입 후 서버 로그: `가입 요청: SignupRequest[customerId=C100, ..., rrn=950101-1234567, ..., password=Passw0rd!23]`
  - 실행 결과 (2026-11-05, 원본 코드로 실행):
    ```
    GET /api/customers/C001
    → {"customerId":"C001","customerName":"홍길동","rrn":"900101-1234567","phone":"010-1111-2222","passwordHash":null}
    서버 로그:
      고객 조회: Customer{customerId='C001', customerName='홍길동', rrn='900101-1234567', phone='010-1111-2222', passwordHash='null'}
      가입 요청: SignupRequest[customerId=C100, customerName=박민수, rrn=950101-1234567, phone=010-1234-5678, password=Passw0rd!23]
    ```
- 수정 내용:
  - `domain/CustomerResponse.java` (신규) — 응답 전용 record. 비밀번호 해시 필드는 아예 없고, 주민번호·전화번호는 `MaskingUtil` 로 마스킹한 값만 담는다. JSON 키 이름(`customerId`, `customerName`, `rrn`, `phone`)은 기존과 같게 유지했다.
    ```java
    public record CustomerResponse(String customerId, String customerName, String rrn, String phone) {
        public static CustomerResponse of(Customer c, String plainRrn) {
            return new CustomerResponse(c.getCustomerId(), c.getCustomerName(),
                    MaskingUtil.maskRrn(plainRrn), MaskingUtil.maskPhone(c.getPhone()));
        }
    }
    ```
  - `web/CustomerController.java` — `Customer` 대신 `CustomerResponse` 반환. 주민번호는 복호화 후 마스킹.
    - **레거시 평문 처리**: `sql/01_schema.sql` 초기 데이터의 주민번호는 암호화되지 않은 평문(`900101-1234567` 형식)이고 스키마는 수정할 수 없다. 이 값을 그대로 복호화하면 예외가 나서 `GET /api/customers/C001` 이 500 이 된다. AES-GCM 암호문은 Base64 라 `-` 를 포함할 수 없으므로, 저장값이 `\d{6}-\d{7}` 형식이면 레거시 평문으로 보고 **그대로 마스킹**한다. 그 외에는 복호화하고, 실패하면(키 불일치·데이터 손상) 응답은 `******-*******` 로 전체 마스킹하고 로그에는 식별자만 남긴다.
    ```java
    if (LEGACY_PLAIN_RRN.matcher(stored).matches()) {
        return stored;                         // 레거시 평문 → 그대로 마스킹
    }
    try {
        return crypto.decrypt(stored);
    } catch (RuntimeException e) {
        log.warn("주민번호 복호화 실패: customerId={}", c.getCustomerId());
        return null;                           // → MaskingUtil 이 전체 마스킹
    }
    ```
  - `domain/Customer.java` — `toString()` 이 `customerId` 만 출력
    ```java
    // 변경 전: "Customer{customerId=..., customerName=..., rrn=..., phone=..., passwordHash=...}"
    // 변경 후
    return "Customer{customerId='" + customerId + "'}";
    ```
  - `web/AuthController.java`, `web/CustomerController.java` — 로그에는 식별자만
    ```java
    log.info("가입 요청: customerId={}", req.customerId());   // 변경 전: log.info("가입 요청: {}", req)
    log.info("고객 조회: customerId={}", c.getCustomerId());  // 변경 전: log.info("고객 조회: {}", c)
    ```
  - `domain/SignupRequest.java` — record 기본 `toString()` 이 비밀번호 평문을 출력하므로, 누군가 실수로 `req` 를 로그에 남겨도 안전하도록 `customerId` 만 출력하게 재정의했다.
- 확인(수정 후):
  - 자가 점검 `controllersDoNotReturnEntity`, `customerToStringHasNoSecrets`, `signupDoesNotLogRequestObject` 통과
  - `GET /api/customers/C001` → `200` `{"customerId":"C001","customerName":"홍길동","rrn":"900101-1******","phone":"010-****-2222"}` (`passwordHash` 키 없음, 레거시 평문도 500 없이 마스킹)
  - C100 가입 후 C100 로 조회 → `"rrn":"950101-1******"` (GCM 복호화 후 마스킹)
  - 서버 로그: `고객 조회: customerId=C001`, `가입 요청: customerId=C100`
  - 실행 결과 (2026-11-05):
    ```
    GET /api/customers/C001 (C001 로그인)
    → 200 {"customerId":"C001","customerName":"홍길동","rrn":"900101-1******","phone":"010-****-2222"}
    C100 가입 후 C100 로그인 → GET /api/customers/C100
    → 200 {"customerId":"C100","customerName":"박민수","rrn":"950101-1******","phone":"010-****-5678"}
    서버 로그: 고객 조회: customerId=C001 / 가입 요청: customerId=C100
    → 로그 파일 전체에서 주민번호·비밀번호 평문 0건
    ```

### [4] 에러 처리

- 취약 원인:
  - `GlobalExceptionHandler` 가 모든 예외를 `ResponseEntity.status(500).body(e.toString())` 로 응답 → 예외 클래스명, 메시지(SQL 문장, `ORA-` 코드, 테이블명, 입력값)가 그대로 노출된다.
  - `ResponseStatusException`(401/403/404)까지 500 으로 바꿔 버려 클라이언트가 원인을 구분할 수 없고, 정상적인 거부도 서버 오류로 기록된다.
- 공격 재현(수정 전):
  - `GET /api/interest?principal=1000000&rate=abc` → `500` `java.lang.NumberFormatException: For input string: "abc"`
  - `GET /api/loans/sorted?customerId=C001&sortBy=nosuch` → `500` `org.springframework.jdbc.BadSqlGrammarException: ... SELECT loan_id, ... ORDER BY nosuch ... ORA-00904`
  - 로그인 없이 `GET /api/loans?customerId=C001` → `500` `org.springframework.web.server.ResponseStatusException: 401 UNAUTHORIZED`
  - 실행 결과 (2026-11-05, 원본 코드로 실행):
    ```
    GET /api/interest?principal=1000000&rate=abc
    → 500 java.lang.NumberFormatException: For input string: "abc"
    GET /api/loans/sorted?customerId=C001&sortBy=nosuch (C001 로그인)
    → 500 org.springframework.jdbc.BadSqlGrammarException: … ORA-00904: "NOSUCH": 부적합한 식별자 … (매퍼 파일 경로 노출)
    비로그인 GET /api/loans?customerId=C001
    → 500 org.springframework.web.server.ResponseStatusException: 401 UNAUTHORIZED
    ```
- 수정 내용: (예외 종류별로 어떤 상태 코드/메시지를 내려주는지)
  - `web/ApiError.java` (신규) — 응답 본문 `{ "code": ..., "message": "규격화된 공통 메시지", "errorId": "<UUID>" }`
  - `web/GlobalExceptionHandler.java` — 예외별 처리 (인자 없는 생성자 유지)

    | 예외 | 상태 | code | message |
    |---|---|---|---|
    | `IllegalArgumentException` (`NumberFormatException` 포함: 금리 형식, 금액 0 이하, 정렬 기준 등) | 400 | `BAD_REQUEST` | 요청 값이 올바르지 않습니다. |
    | `MethodArgumentNotValidException` (`@Valid` 실패) | 400 | `BAD_REQUEST` | 〃 (틀린 **필드명만** 로그에 남김, 값은 남기지 않음) |
    | `MethodArgumentTypeMismatchException` (`/api/loans/abc`, `amount=abc`) | 400 | `BAD_REQUEST` | 〃 |
    | `MissingServletRequestParameterException` (필수 파라미터 누락) | 400 | `BAD_REQUEST` | 〃 |
    | `HttpMessageNotReadableException` (깨진 JSON 본문) | 400 | `BAD_REQUEST` | 〃 |
    | `ResponseStatusException` 401 | 401 | `UNAUTHORIZED` | 로그인이 필요합니다. |
    | `ResponseStatusException` 403 | 403 | `FORBIDDEN` | 접근 권한이 없습니다. |
    | `ResponseStatusException` 404 | 404 | `NOT_FOUND` | 요청한 정보를 찾을 수 없습니다. |
    | `DuplicateKeyException` (중복 가입) | 409 | `CONFLICT` | 이미 등록된 정보입니다. |
    | 스프링 MVC 표준 예외(`ErrorResponse` 구현체: 405 메서드 미지원 등) | 원래 상태 | 해당 상태명 | 405: 지원하지 않는 요청 방식입니다. / 기타 4xx: 요청을 처리할 수 없습니다. |
    | 그 외 모든 `Exception` | 500 | `INTERNAL_SERVER_ERROR` | 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요. |

  - 로그 정책: 5xx 는 `log.error("errorId={}", errorId, e)` 로 **스택 트레이스를 서버 로그에만** 남긴다. 4xx 는 정상적인 거부이므로 `errorId / 상태 / 예외 클래스 단순명`만 WARN 으로 남긴다(예외 메시지에는 사용자 입력이 섞여 있어 로그 위조에 쓰일 수 있으므로 남기지 않음). 사용자는 응답의 `errorId` 로 문의하고, 운영자는 같은 값으로 로그를 찾는다.
  - 프로젝트의 모든 `.java` 파일에서 `e.toString()`, `printStackTrace` 문자열을 없앴다(주석 포함).
- 확인(수정 후):
  - 자가 점검 `errorResponseHasNoInternals`, `noToStringOrPrintStackTrace` 통과
  - `rate=abc` → `400` `{"code":"BAD_REQUEST","message":"요청 값이 올바르지 않습니다.","errorId":"<UUID>"}`
  - 비로그인 → `401`, (로그인 상태) `/api/loans/abc` → `400`, `GET /api/loans/1001/repay` → `405` (모두 같은 JSON 형태, `Content-Type: application/json`)
  - 서블릿 필터가 거부하는 요청(403 출처 불일치, 413 본문 초과, 429 횟수 초과)도 `FilterErrors` 로 같은 `{code, message, errorId}` 형식을 쓴다 ("추가로 발견한 문제" 참고).
  - 앱을 틀린 DB 비밀번호로 띄우고 로그인 → 응답은 `500` 공통 메시지뿐이고, `ORA-01017` 과 스택 트레이스는 같은 `errorId` 로 서버 로그에만 남는 것을 확인했다.
  - 실행 결과 (2026-11-05):
    ```
    GET /api/interest?principal=1000000&rate=abc
    → 400 {"code":"BAD_REQUEST","message":"요청 값이 올바르지 않습니다.","errorId":"9589ed96-4073-4684-b0b4-1e52a9623b4c"}
    비로그인 GET /api/loans?customerId=C001
    → 401 {"code":"UNAUTHORIZED","message":"로그인이 필요합니다.","errorId":"…"}
    GET /api/loans/1001/repay
    → 405 {"code":"METHOD_NOT_ALLOWED","message":"지원하지 않는 요청 방식입니다.","errorId":"…"}
    틀린 DB 비밀번호로 기동 후 POST /login
    → 500 {"code":"INTERNAL_SERVER_ERROR","message":"일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.","errorId":"f14ce9bf-…"}
    서버 로그:
      WARN  … errorId=9589ed96-4073-4684-b0b4-1e52a9623b4c status=400 type=IllegalArgumentException
      ERROR … errorId=f14ce9bf-… (+ 스택 트레이스, Caused by: java.sql.SQLException: ORA-01017 … ← 서버 로그에만 남음)
    ```

### [5] 입력값 검증

- 취약 원인:
  - `SignupRequest` 에 제약이 없고 `signup(@RequestBody ...)` 에 `@Valid` 가 없어 어떤 값이든 그대로 암호화·해시·저장된다.
  - SQL/스크립트 특수문자가 든 ID·이름이 저장되면 이 값을 출력하는 다른 화면에서 저장형 XSS 나 로그 위조로 이어질 수 있다.
  - 컬럼 길이를 넘는 값은 DB 에서 `ORA-12899` 로 실패해 500 과 내부 정보 노출로 이어진다. 형식이 틀린 전화번호는 `MaskingUtil` 이 `***` 로 처리해 데이터 품질도 깨진다.
- 수정 내용: (필드별 검증 규칙과 그렇게 정한 이유)
  - `domain/SignupRequest.java` — record 컴포넌트에 제약 어노테이션, `web/AuthController.java` — `signup(@Valid @RequestBody SignupRequest req)`. 실패 시 `MethodArgumentNotValidException` → 400 ([4]).

    | 필드 | 규칙 | 이유 |
    |---|---|---|
    | 고객 ID | `@NotBlank` + `^[A-Za-z0-9]{4,10}$` | 영문/숫자만 허용해 `'`, `;`, `<`, 공백·개행 같은 SQL·스크립트·로그 위조 문자를 원천 차단한다. 상한 10 은 `customer_id VARCHAR2(10)` 컬럼 길이(초과 시 ORA-12899 → 500). 하한 4 는 기존 ID 체계(`C001`)와 맞추고 1~3자 ID 처럼 추측·오입력이 쉬운 값을 막기 위해서다. |
    | 이름 | `@NotBlank` + `@Size(min=2, max=16)` + `^[가-힣A-Za-z]+( [가-힣A-Za-z]+)*$` | 한글 완성형/영문만 허용해 `<script>` 같은 태그·스크립트 문자를 차단한다. 공백은 단어 사이 1칸만(영문 이름 `Gil Dong Hong`), 앞뒤·연속 공백은 거부. `customer_name VARCHAR2(50)` 은 바이트 기준이고 한글은 UTF-8 에서 1자 3바이트이므로 16자 × 3 = 48바이트 ≤ 50 이 되도록 상한을 16자로 정했다. 하한 2 는 실명 최소 길이. |
    | 주민번호 | `@NotBlank` + `^\d{2}(0[1-9]\|1[0-2])(0[1-9]\|[12]\d\|3[01])-[1-4]\d{6}$` | `YYMMDD-성별자리+6자리` 형식을 강제한다. 권장 규칙 `^\d{6}-[1-4]\d{6}$` 에 **월(01~12)·일(01~31) 범위**를 더해 `951301` 같은 불가능한 생년월일도 거부한다. 성별 자리는 내국인(1~4)만 허용했다(외국인등록번호 5~8 은 이 서비스 범위 밖으로 판단, 필요하면 `[1-8]` 로 확장). `MaskingUtil.maskRrn` 이 `-` 위치(6)를 전제하는 것과도 맞는다. |
    | 전화번호 | `@NotBlank` + `^01[016789]-\d{3,4}-\d{4}$` | 휴대전화(010/011/016/017/018/019) 형식만 허용. `MaskingUtil.maskPhone` 이 `-` 3분할을 전제하므로 형식을 강제해야 마스킹이 정상 동작한다. 최대 13자로 `phone VARCHAR2(20)` 안에 들어간다. |
    | 비밀번호 | `@NotNull` + `@Size(min=8, max=64)` + 영문·숫자·특수문자 각 1자 이상, 공백 제외 출력 가능 ASCII(`[!-~]`)만 | 8자 이상 + 3종 조합으로 무차별 대입·사전 공격 내성을 높인다. 상한 64 는 느린 해시(PBKDF2 600,000회)에 매우 긴 입력을 반복해 넣는 DoS 를 막기 위해서다. 공백·한글 등 비 ASCII 를 뺀 이유는 입력기 상태(한/영)나 OS별 유니코드 정규화(NFC/NFD) 차이로 같은 비밀번호가 다른 바이트가 되어 로그인할 수 없는 문제, 복사·붙여넣기 시 앞뒤 공백이 섞이는 문제를 막기 위해서다. |

  - 모든 필드에 `@NotBlank`/`@NotNull` 을 둔 이유: `@Pattern`·`@Size` 는 null 을 "유효"로 처리하므로 null 은 별도로 막아야 한다.
  - `@Valid` 를 통과한 뒤에만 메서드 본문이 실행되므로, 가입 로그의 `customerId` 에는 개행 등 로그 위조 문자가 들어갈 수 없다.
- 확인(수정 후): (정상 요청 / 비정상 요청 각각)
  - 자가 점검 `validSignupPasses`, `invalidSignupRejected`, `signupUsesValid` 통과
  - 정상: `{"customerId":"C100","customerName":"박민수","rrn":"950101-1234567","phone":"010-1234-5678","password":"Passw0rd!23"}` → `200` `{"customerId":"C100"}`, 같은 요청 재전송 → `409`
  - 비정상: 빈 ID / `C1' OR '1'='1` / 11자 ID / `<script>alert(1)</script>` 이름 / `not-a-rrn` / `951301-1234567` / `123` 전화 / `1234`·`Password123` 비밀번호 / 깨진 JSON → 모두 `400` 공통 메시지
  - Bean Validation 으로 확인한 경계값: 통과 — 10자 ID, 한글 16자 이름, `Gil Dong Hong`, `010-123-4567`, 64자 비밀번호 / 거부 — `C100\n`(개행), 3자·11자 ID, 한글 17자, 앞 공백·연속 공백 이름, 1자 이름, `950132-…`, `950101-5…`, `02-123-4567`, `01012345678`, 공백 포함 비밀번호, 65자 비밀번호
  - 실행 결과 (2026-11-05):
    ```
    정상 가입 C100                → 200 {"customerId":"C100"}
    같은 요청 재전송              → 409 {"code":"CONFLICT","message":"이미 등록된 정보입니다.","errorId":"…"}
    비정상 10건 → 모두 400 {"code":"BAD_REQUEST","message":"요청 값이 올바르지 않습니다.","errorId":"…"}
      빈 ID / C1' OR '1'='1 / 11자 ID(C1234567890) / 이름 <script>alert(1)</script> / rrn not-a-rrn /
      rrn 951301-1234567 / 전화 123 / 비밀번호 1234 / 비밀번호 Password123 / 깨진 JSON
    ```

### [6] 비밀번호 해시 / 암호화 / 키 관리

- 취약 원인: (해시, 암호화, DB 접속 정보, 키 각각)
  - 해시: `MD5` 단일 해시, 솔트 없음 → 같은 비밀번호는 항상 같은 해시(32자리 hex)라 레인보우 테이블·GPU 대입에 매우 빠르게 깨진다. 비교도 `String.equals` 라 상수 시간이 아니다.
  - 암호화: `AES/ECB/PKCS5Padding` → IV 가 없어 같은 평문은 항상 같은 암호문(같은 주민번호를 가진 고객을 식별 가능)이고, 무결성 검증이 없어 암호문 변조를 알아챌 수 없다.
  - 키: 16자리 문자열 상수 `KEY` 가 소스에 하드코딩되어 있고, 생성자로 주입받은 `base64Key` 는 **쓰이지도 않았다**. `@Value("${assign.aes-key:}")` 의 빈 기본값과 `assign.aes-key=` 때문에 키가 없어도 앱이 그대로 떴다.
  - DB 접속 정보: `application.properties` 에 DB 비밀번호가 평문으로 하드코딩되어 있어, 소스 저장소·압축본을 가진 누구나 DB 에 접속할 수 있었다.
- 수정 내용:
  - `security/PasswordHasher.java` — `hash`/`verify` 정적 시그니처 유지, JDK 기본 `PBKDF2WithHmacSHA256` 사용 (새 의존성 없음)
    ```java
    // 변경 전
    MessageDigest md = MessageDigest.getInstance("MD5");
    return HexFormat.of().formatHex(md.digest(password.getBytes(UTF_8)));

    // 변경 후 — 저장 형식: pbkdf2-sha256$반복횟수$솔트(Base64)$해시(Base64)
    byte[] salt = new byte[16];
    RANDOM.nextBytes(salt);                                   // SecureRandom, 매번 새 솔트
    byte[] dk = derive(password, salt, 600_000);              // PBEKeySpec(…, 256bit), 사용 후 clearPassword()
    return "pbkdf2-sha256$600000$" + b64(salt) + "$" + b64(dk);

    // verify: 저장값에서 반복 횟수·솔트를 꺼내 다시 계산 후
    return MessageDigest.isEqual(expected, actual);          // 상수 시간 비교
    ```
    - 반복 600,000회는 OWASP Password Storage Cheat Sheet 의 PBKDF2-HMAC-SHA256 권장값이다(이 PC 기준 검증 1회 약 130ms). 반복 횟수를 저장값에 함께 넣어(자기 기술적 형식) 나중에 횟수를 올려도 기존 해시를 그대로 검증할 수 있다.
    - 저장값이 null(초기 데이터)·형식 오류·반복 횟수 이상(1 미만, 1천만 초과)이면 예외 없이 `false` 를 반환한다. 저장값 길이는 약 90자로 `password_hash VARCHAR2(300)` 에 들어간다.
  - `security/AesCrypto.java` — `AES/GCM/NoPadding` 인증 암호화, 주입된 키만 사용
    ```java
    // 변경 전
    private static final String KEY = "<16자리 하드코딩 상수>";
    public AesCrypto(@Value("${assign.aes-key:}") String base64Key) { this.base64Key = base64Key; }   // 쓰이지 않음
    Cipher.getInstance(/* ECB 모드 */ ...); cipher.init(ENCRYPT_MODE, new SecretKeySpec(KEY.getBytes(UTF_8), "AES"));

    // 변경 후
    public AesCrypto(@Value("${assign.aes-key}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) throw new IllegalStateException("암호화 키(assign.aes-key)가 설정되지 않았습니다.");
        byte[] raw = Base64.getDecoder().decode(base64Key.trim());     // Base64 아니면 IllegalStateException
        if (raw.length != 16 && raw.length != 24 && raw.length != 32) throw new IllegalStateException(...);
        this.key = new SecretKeySpec(raw, "AES");
    }
    // encrypt: 매번 12바이트 SecureRandom IV, 128비트 태그 → Base64(IV || 암호문+태그)
    cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
    // decrypt: 태그 검증 실패(AEADBadTagException: 다른 키·변조)와 Base64 형식 오류를 IllegalStateException 으로 감쌈
    ```
    - 키가 없으면 **생성자에서 바로 실패(fail-fast)** 하므로 키 없이 앱이 뜨지 않는다. 키 형식 오류 메시지에는 원인 예외를 연결하지 않았다(Base64 디코더 메시지에 키 일부가 들어갈 수 있음).
    - 생성자에서 예외를 던지는 클래스는 하위 클래스의 finalizer 로 악용될 수 있어(SpotBugs `CT_CONSTRUCTOR_THROW`) 클래스를 `final` 로 했다.
    - **AAD(추가 인증 데이터) 바인딩**: `encrypt(plain, associatedData)` / `decrypt(token, associatedData)` 오버로드를 추가하고, 가입 시 주민번호를 **고객 ID 를 AAD 로 묶어** 암호화한다(`AuthController`, 복호화는 `CustomerController`). DB 권한을 가진 사람이 다른 고객 행으로 암호문을 옮겨 붙여도 태그 검증에 실패해 원문이 나오지 않는다. 기존 1인자 메서드는 AAD 없이 그대로 동작한다(자가 점검 테스트가 사용).
    - 소스 안에 ECB 모드 문자열은 주석으로도 남기지 않았다.
  - `src/main/resources/application.properties` — 기본값 없는 환경 변수 참조
    ```properties
    # 변경 전
    spring.datasource.password=<평문 비밀번호>
    assign.aes-key=
    # 변경 후
    spring.datasource.password=${ASSIGN_DB_PASSWORD}
    assign.aes-key=${ASSIGN_AES_KEY}
    ```
    실행 시 `$env:ASSIGN_DB_PASSWORD = "<본인 DB 비밀번호>"`, `$env:ASSIGN_AES_KEY = <RandomNumberGenerator 로 만든 16바이트 Base64>` 로 주입한다(`MANUAL_CHECK.md` 0단계). 키·비밀번호 값은 소스·설정·보고서 어디에도 적지 않았다.
- 확인(수정 후):
  - 자가 점검 `passwordHashing`, `aesRoundTripAndRandomIv`, `aesKeyIsInjected`, `noHardcodedSecretsInProperties` 통과
  - 같은 비밀번호 두 번 해시 → 서로 다른 값, 둘 다 `verify` 성공, 틀린 비밀번호 `false` / 같은 평문 두 번 암호화 → 서로 다른 암호문, 둘 다 복호화 성공 / 다른 키로 복호화·암호문 1비트 변조 → `IllegalStateException`
  - AAD: `C100` 으로 묶은 암호문을 `C101` 로 복호화하거나 AAD 없이 복호화 → `IllegalStateException`. DB 에서 C100 의 `rrn` 을 C101 행에 복사한 뒤 C101 로 조회 → `"rrn":"******-*******"`(전체 마스킹) + 서버 로그 `주민번호 복호화 실패: customerId=C101`
  - 환경 변수 없이 `.\mvnw.cmd spring-boot:run` → `Could not resolve placeholder 'ASSIGN_AES_KEY'` 로 기동 실패, `ASSIGN_AES_KEY` 를 공백으로 주면 `aesCrypto` 빈 생성 중 `IllegalStateException` 으로 기동 실패 (로컬에서 확인)
  - DB 저장값: `rrn` = Base64 56자(IV 12 + 암호문 14 + 태그 16 바이트), `password_hash` = `pbkdf2-sha256$600000$...`
  - 실행 결과 (2026-11-05):
    ```
    DB 저장값 (C100)
      수정 전(원본 코드): rrn = Base64 24자 (ECB, 소스의 고정 키), password_hash = 솔트 없는 32자 16진수
      수정 후          : rrn = Base64 56자, '-' 없음 / password_hash = pbkdf2-sha256$600000$<솔트>$<해시> (90자)
    AAD 바꿔치기: C100 의 rrn 암호문을 C101 행에 복사(UPDATE) 후 C101 로 조회
      바꿔치기 전 → "rrn":"960202-2******"
      바꿔치기 후 → "rrn":"******-*******" (전체 마스킹, 500 없음)
      서버 로그: WARN … 주민번호 복호화 실패: customerId=C101
    환경 변수 없이 기동
      → Caused by: java.lang.IllegalArgumentException: Could not resolve placeholder 'ASSIGN_AES_KEY' in value "${ASSIGN_AES_KEY}" (기동 실패)
    ```

### [7] 금액 계산 / 동시성

- 취약 원인: (이자 계산, 상환 금액 검증, 경쟁 조건 각각)
  - 이자 계산: `double` 로 계산(이진 부동소수점 오차) 후 `(long)` 캐스팅으로 **버림** → `1,000,000 × 0.035 ÷ 12 = 2916.666…` 이 2917 이 아니라 2916. 원금 0 이하·음수 금리·숫자가 아닌 값의 범위 검사도 없다.
  - 상환 금액 검증: 금액 하한 검사가 없어 `amount=-1000000` 이면 `balance < amount` 가 거짓 → `balance - (-1,000,000)` 으로 **잔액이 늘어난다**.
  - 경쟁 조건: `selectBalance`(조회) → 자바에서 비교 → `updateBalance`(계산한 잔액으로 **덮어쓰기**) 가 나뉘어 있어, 동시 요청이 같은 잔액을 읽고 각자 덮어쓰면 갱신이 유실되거나 잔액보다 많이 상환된다. 소유자 조건도 없다([2]).
- 공격 재현(수정 전):
  - `GET /api/interest?principal=1000000&rate=0.035` → `{"monthlyInterest":2916}` / `rate=-1` → `{"monthlyInterest":-83333}`
  - `POST /api/loans/1001/repay?amount=-1000000` → `{"success":true}`, 1001 잔액 12,500,000 → 13,500,000
  - 대출 1004(잔액 8,000,000)에 1,000,000 씩 10건 동시 상환 → 성공이 8건을 넘거나, 성공 건수만큼 차감되지 않고 잔액이 남음
  - 실행 결과 (2026-11-05, 원본 코드로 실행, C001 로그인):
    ```
    GET /api/interest?principal=1000000&rate=0.035 → 200 {"monthlyInterest":2916}   (정답 2917, 버림)
    GET /api/interest?principal=1000000&rate=-1    → 200 {"monthlyInterest":-83333} (음수 금리 허용)
    POST /api/loans/1001/repay?amount=-1000000     → {"success":true}
    DB: 1001 잔액 12,500,000 → 13,500,000 (잔액 증가)
    1004(잔액 8,000,000)에 1,000,000 씩 10건 동시 상환
    → 10건 모두 {"success":true}, 최종 잔액 4,000,000
    → 잔액보다 많은 10건이 성공 처리됐는데 실제 차감은 4,000,000 뿐 (갱신 유실)
    ```
- 수정 내용:
  - `service/LoanService.java` — 이자 계산
    ```java
    // 변경 전
    double rate = Double.parseDouble(annualRate);
    return (long) (principal * rate / 12);

    // 변경 후
    if (principal <= 0) throw new IllegalArgumentException("원금은 0보다 커야 합니다.");
    BigDecimal rate = parseRate(annualRate);       // new BigDecimal(String), 숫자 아님/0 미만/1 초과 → IllegalArgumentException
    return new BigDecimal(principal).multiply(rate)
            .divide(BigDecimal.valueOf(12), 0, RoundingMode.HALF_UP)
            .longValueExact();
    ```
    - 금리 문자열은 길이 20자, 소수 자릿수(scale) 10 이하만 받는다. `1E-999999999` 처럼 극단적인 지수를 넣으면 `BigDecimal` 나눗셈 비용이 폭증해 서비스 거부(DoS)가 될 수 있기 때문이다.
    - **금리 0% 허용**: 무이자·프로모션 상품이 실제로 있고, 0 이면 이자가 0 으로 계산될 뿐 오류가 아니다. 상한은 과제 기준대로 1(100%) 포함. (업무적으로는 법정 최고금리 20% 를 상한으로 더 좁힐 수 있다)
  - `service/LoanService.java` — 상환은 금액 검증 후 조건부 UPDATE 한 문장
    ```java
    // 변경 전
    Long balance = loanMapper.selectBalance(loanId);
    if (balance == null || balance < amount) return false;
    loanMapper.updateBalance(loanId, balance - amount);
    return true;

    // 변경 후
    if (amount <= 0) throw new IllegalArgumentException("상환 금액은 0보다 커야 합니다.");   // DB 호출 전
    return loanMapper.repay(loanId, customerId, amount) == 1;   // 1 = 성공, 0 = 잔액 부족/타인 소유/없음
    ```
  - `LoanMapper.xml` / `mapper/LoanMapper.java` — 쓰지 않는 `selectBalance`/`updateBalance` 메서드와 쿼리를 삭제하고 `repay` 추가
    ```xml
    <update id="repay">
        UPDATE loan
           SET balance = balance - #{amount}
         WHERE loan_id = #{loanId}
           AND customer_id = #{customerId}
           AND balance >= #{amount}
    </update>
    ```
    잔액 확인과 차감이 DB 안에서 한 문장으로 원자적으로 일어나고, Oracle 은 갱신하는 행에 잠금을 걸고 커밋된 최신 값으로 조건을 다시 평가하므로 동시 요청이 와도 잔액이 음수가 되거나 갱신이 유실되지 않는다.
- 확인(수정 후):
  - 자가 점검 `interestRounding`(2917 / 229 / 0), `interestBoundaries`, `repayAmountValidation`, `repayIsAtomic` 통과
  - `rate=abc`, `rate=-1`, `rate=1.5`, `principal=0` → 모두 `400` / `rate=0` → 0, `rate=3.5E-2` → 2917
  - `POST /api/loans/1001/repay?amount=-1000000`, `amount=0` → `400`, 잔액 변화 없음
  - 동시 상환 10건 → **성공 8 / 실패 2, 최종 잔액 0** (성공 건수 × 금액 = 차감액)
  - 실행 결과 (2026-11-05, C001 로그인):
    ```
    rate=0.035 → 2917 / rate=3.5E-2 → 2917 / rate=0 → 0
    rate=abc, rate=-1, rate=1.5, principal=0 → 모두 400 BAD_REQUEST 공통 메시지
    POST /api/loans/1001/repay?amount=-1000000 → 400 / amount=0 → 400
    DB: 1001 잔액 12,500,000 그대로
    1004(잔액 8,000,000)에 1,000,000 씩 10건 동시 상환
    → success:true 8건 / success:false 2건, 최종 잔액 0
    감사 로그: REPAY customerId=C001 loanId=1004 amount=1000000 result=SUCCESS ×8, result=REJECTED ×2
    ```

### [8] XSS / CSRF

- 취약 원인:
  - XSS: `search.jsp` 에서 검색어를 `${q}`(JSP EL 은 기본적으로 HTML 이스케이프를 하지 않음)와 `<%= request.getParameter("q") %>` 로 그대로 출력(반사형 XSS)하고, 스크립트에서 URL 의 `q` 를 `innerHTML` 로 넣는다(DOM 기반 XSS).
  - CSRF: `POST /repay-form` 이 세션 쿠키만으로 상환을 처리하고, 요청이 우리 화면에서 나왔는지 확인할 예측 불가능한 값이 없다. 로그인한 사용자가 외부 페이지(`attack/csrf-attack.html`)를 열기만 해도 자동 제출된 폼으로 상환이 일어난다.
- 공격 재현(수정 전):
  - `GET /search?q=<script>alert(1)</script>` → 응답 HTML 에 `<script>alert(1)</script>` 원문 2곳
  - 브라우저 `http://localhost:8080/search?q=<img src=x onerror=alert(1)>` → 알림창 실행 (`innerHTML` 로 넣은 `<script>` 는 HTML5 규칙상 실행되지 않으므로 DOM XSS 는 `<img onerror>` 로 재현)
  - C001 로그인 후 `attack/csrf-attack.html` 열기 → `상환 처리: true`, 1001 잔액 1,000,000 감소
  - 실행 결과 (2026-11-05, 원본 코드로 실행, curl):
    ```
    GET /search?q=<script>alert(1)</script> → 응답 HTML 에 원문 2곳
      <p>검색어: <span><script>alert(1)</script></span></p>
      <p>검색어(재표시): <span><script>alert(1)</script></span></p>
      document.getElementById("summary").innerHTML = q + " 에 대한 검색 결과가 없습니다.";
    csrf-attack.html 과 같은 요청 (C001 세션 쿠키 O, 토큰 X, Origin: null)
    POST /repay-form loanId=1001&amount=1000000 → 200 "상환 처리: true"
    DB: 1001 잔액 13,500,000 → 12,500,000 (1,000,000 감소)
    ```
- 수정 내용:
  - `webapp/WEB-INF/jsp/search.jsp` — JSTL 출력 이스케이프, `textContent`
    ```jsp
    <%@ taglib prefix="c" uri="jakarta.tags.core" %>
    <!-- 변경 전 -->  <span>${q}</span>   /   <span><%= request.getParameter("q") %></span>
    <!-- 변경 후 -->  <span><c:out value="${q}"/></span>   /   <span><c:out value="${param.q}"/></span>
    ```
    ```js
    // 변경 후: 문자열을 HTML 로 해석하지 않고 텍스트 노드로만 넣는다
    document.getElementById("summary").textContent = q + " 에 대한 검색 결과가 없습니다.";
    ```
  - `web/PageController.java` — 세션 기반 동기화 토큰(Synchronizer Token)
    ```java
    // GET /repay-form: 세션에 토큰이 없으면 SecureRandom 32바이트 → Base64URL(43자)로 만들어 세션에 저장, 모델로 전달
    model.addAttribute("csrfToken", csrfToken(session));

    // POST /repay-form
    String me = LoginController.requireCustomer(session);   // 로그인 안 됨(쿠키 미전송 포함) → 401
    verifyCsrf(session, csrf);                              // 토큰 없음/불일치 → 403
    return "상환 처리: " + loanService.repay(loanId, me, amount);

    private static void verifyCsrf(HttpSession session, String requestToken) {
        Object sessionToken = session.getAttribute(CSRF_SESSION_KEY);
        if (!(sessionToken instanceof String expected) || requestToken == null
                || !MessageDigest.isEqual(expected.getBytes(UTF_8), requestToken.getBytes(UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }
    ```
  - `webapp/WEB-INF/jsp/repay-form.jsp` — hidden 필드
    ```jsp
    <input type="hidden" name="_csrf" value="<c:out value='${csrfToken}'/>">
    ```
  - 심층 방어 (`web/SecurityHeadersFilter.java`, `static/js/search.js`)
    - 모든 응답에 CSP `default-src 'self'; script-src 'self'; ...; object-src 'none'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'` 를 붙여 **인라인 스크립트 실행 자체를 막는다**. 이스케이프가 빠진 곳이 새로 생기더라도 주입된 `<script>`·`onerror` 가 실행되지 않는다.
    - 이를 위해 `search.jsp` 의 인라인 스크립트를 같은 출처의 외부 파일 `/js/search.js` 로 옮겼다(`textContent` 사용은 그대로).
    - `X-Frame-Options: DENY` + `frame-ancestors 'none'` 으로 상환 폼을 외부 페이지 `iframe` 에 넣는 클릭재킹을 막는다(토큰이 있어도 사용자 클릭을 유도하는 공격).
    - 외부 사이트에서 보내는 POST 를 출처(Origin/Referer)로 거부하는 필터는 `/repay-form` 에는 적용하지 않았다(토큰 검증 효과를 따로 확인하기 위해. "추가로 발견한 문제" 3번).
- 확인(수정 후): (`attack/csrf-attack.html` 실행 결과 포함)
  - 자가 점검 `xssInSearchPage`, `csrfProtection` 통과
  - `GET /search?q=<script>alert(1)</script>` → `&lt;script&gt;alert(1)&lt;/script&gt;` 2곳, 원문 없음 (로컬에서 확인) / 브라우저 `<img onerror>` → 알림창 없이 문자열로 3곳 표시
  - 응답 헤더에 `Content-Security-Policy`, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, `Cache-Control: no-store` 가 붙고, `search.jsp` 응답에 인라인 `<script>` 블록이 없음 (로컬에서 확인)
  - curl: 토큰 없음·틀린 토큰 → `403`, 폼에서 받은 토큰(43자) → `200 상환 처리: true`, 쿠키 없음 → `401`
  - 브라우저 C001 로그인 → `attack/csrf-attack.html` → **`403 FORBIDDEN`**, 1001 잔액 변화 없음. 개발자 도구 Network 에서 요청에 `Cookie: JSESSIONID` 가 실려 있는데도 403 이면 토큰 검증이 막은 것이다. (401 이면 브라우저 SameSite 기본 정책으로 쿠키가 안 실린 경우 → 로그인 직후 다시 열거나 Firefox 로 확인)
  - 실행 결과 (2026-11-05, curl):
    ```
    GET /search?q=<script>alert(1)</script>
    → 원문 0곳, &lt;script&gt;alert(1)&lt;/script&gt; 2곳
      <p>검색어: <span>&lt;script&gt;alert(1)&lt;/script&gt;</span></p>
      <p>검색어(재표시): <span>&lt;script&gt;alert(1)&lt;/script&gt;</span></p>
      <script src="/js/search.js"></script>   (인라인 스크립트 없음)
    응답 헤더
      Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; object-src 'none'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'
      X-Frame-Options: DENY / X-Content-Type-Options: nosniff / Referrer-Policy: no-referrer / Cache-Control: no-store
    CSRF (C001 로그인, 폼에서 받은 토큰 43자)
      csrf-attack.html 과 같은 요청 (쿠키 O, 토큰 X, Origin: null) → 403 {"code":"FORBIDDEN","message":"접근 권한이 없습니다.","errorId":"…"}
      토큰 없음 → 403 / 틀린 토큰 → 403 / 쿠키 없음 → 401
      올바른 토큰 → 200 "상환 처리: true" (1001 잔액 12,500,000 → 12,499,000)
      REST 상환 API 를 Origin: http://evil.example 로 호출 → 403
    세션 고정 방지: 로그인 전 세션 ID 7C639D6D… → 로그인 후 5D835E56… (교체), 로그인 전 쿠키로 /api/loans → 401
      감사 로그: LOGIN_SUCCESS customerId=C001 sessionRotated=true
    ```

## 도구가 잡지 못한 항목 (선택)

SpotBugs 4.8.6 + Find Security Bugs 1.13.0 (`.\mvnw.cmd -Psast compile spotbugs:spotbugs`, `effort=Max`, `threshold=Low`) 으로 수정 전(원본 Java 소스)과 수정 후를 비교했다. 결과는 `target/spotbugsXml.xml`.

| 유형 | 의미 | 수정 전 | 8개 영역 수정 후 | 심층 방어 추가 후(최종) |
|---|---|---|---|---|
| `ECB_MODE` (P1) | AES ECB 모드 | 2 | 0 | 0 |
| `CIPHER_INTEGRITY` (P1) | 무결성 없는 암호 | 2 | 0 | 0 |
| `WEAK_MESSAGE_DIGEST_MD5` (P1) | MD5 사용 | 1 | 0 | 0 |
| `CRLF_INJECTION_LOGS` | 로그 위조 가능성 | 2 (P2 1, P3 1) | 6 (P3) | 11 (P3, 아래 분석) |
| `URF_UNREAD_FIELD` | 쓰이지 않는 필드(주입받은 키) | 1 | 0 | 0 |
| `EI_EXPOSE_REP2` | 생성자 주입 객체 참조 보관 | 3 | 3 (오탐) | 3 (오탐) |
| `SPRING_ENDPOINT` | 엔드포인트 목록(정보성) | 12 | 12 | 12 |
| **합계 / 높은 우선순위(P1)** | | **23 / 5** | **21 / 0** | **26 / 0** |

- 작업 중 도구가 새로 알려 준 2건은 고쳐서 최종 결과에서 없앴다.
  - `CT_CONSTRUCTOR_THROW`: fail-fast 생성자를 가진 `AesCrypto` → `final` 클래스로 변경
  - `IMPROPER_UNICODE` 3건: 출처 비교에 `equalsIgnoreCase`/`toLowerCase` 를 쓰면 `ſ`→`S`, 켈빈 기호 `K`→`k` 같은 유니코드 대소문자 변환으로 다른 출처가 같게 판정될 수 있다 → ASCII A~Z 만 소문자로 바꾸고 정확히 일치하는지 비교. (`Origin: http://localhoſt:8080` → 403 확인)

- **도구가 잡지 못한 항목**: 8개 영역 중 도구가 직접 잡은 것은 [6] 암호화·해시뿐이다.
  - [1] MyBatis XML 의 `${}` 치환 — 바이트코드만 분석하므로 매퍼 XML 의 SQL 은 보지 못한다.
  - [2] IDOR, [7] 경쟁 조건·음수 금액·부동소수점 버림 — 업무 규칙(소유권, 금액 범위)과 실행 순서 문제라 정적 패턴으로 판단할 수 없다.
  - [3] 엔티티 직접 반환·`toString` 민감정보, [4] `e.toString()` 응답 노출 — 탐지하지 못했다.
  - [5] 입력값 검증 누락, [8] JSP 의 XSS(JSP 를 미리 컴파일하지 않아 분석 대상 아님)·CSRF 토큰 누락 — 탐지하지 못했다.
  - [6] 중 소스의 하드코딩 키(`HARD_CODE_KEY`)와 `application.properties` 의 평문 DB 비밀번호도 잡지 못했다(설정 파일은 분석 대상 아님).
- **수정 후 남은 경고 분석**
  - `CRLF_INJECTION_LOGS` 11건: 로그에 들어가는 값을 하나씩 확인했다.
    - `AuthController`(가입 `customerId`): `@Valid` 로 영문/숫자만 통과
    - `CustomerController` 2건, 감사 로그 `LoanService`(상환)·`PageController`(CSRF 거부)·`LoginSessionFilter`(로그인 성공): 로그인 때 DB 존재가 확인된 **세션의 고객 ID** 와 숫자
    - `LoginSessionFilter`(로그인 실패): 요청 ID 를 영문/숫자 외 `_` 로 바꾸고 20자로 자른 값
    - `FilterErrors`: 코드 상수인 거부 사유 / `GlobalExceptionHandler` 43행: Java 필드명 목록 / 67·77행: UUID 와 예외 객체
    - 즉 사용자 입력 문자열이 그대로 로그 메시지에 들어가는 곳은 없다. 그래도 도구의 지적처럼 앞으로 누군가 입력값을 로그에 넣는 실수를 할 수 있으므로, **로그 패턴에서 메시지의 CR/LF 를 공백으로 치환**(`logging.pattern.console` 의 `%replace(%m){'[\r\n]+', ' '}`)해 로그 위조를 전역으로 막았다. 도구는 설정 파일을 보지 못하므로 경고 수는 그대로 남는다.
  - `EI_EXPOSE_REP2` 3건: 스프링이 생성자로 주입하는 매퍼/빈 참조를 필드에 보관하는 것이라 오탐이다.
  - `SPRING_ENDPOINT` 12건: 공격 표면 목록을 보여 주는 정보성 항목이다.

## 추가로 발견한 문제 (선택)

위 8개 항목 외에 발견한 보안 약점과 조치 내용.

| # | 문제 | 위험 | 조치 |
|---|---|---|---|
| 1 | `GET /api/loans?customerId=`, `/api/loans/sorted`, `GET /api/customers/{customerId}` 가 요청 파라미터의 고객 ID 를 그대로 조회 | 로그인만 하면 다른 고객의 대출 목록·고객 정보(주민번호 포함) 조회 | **적용**: `AccessGuard.requireSelf` 로 세션 고객과 다르면 403 ([2]) |
| 2 | 원본 상환 로직이 세션 고객 ID 를 받고도 쓰지 않음 (`selectBalance`/`updateBalance` 가 `loan_id` 만 조건) | 타인 대출 잔액 차감 | **적용**: 조건부 UPDATE 에 `customer_id` 조건 ([7]) |
| 3 | REST 상환 API `POST /api/loans/{id}/repay` 는 `@RequestParam` 이라 외부 사이트의 일반 HTML 폼(단순 요청, CORS 사전 요청 없음)으로 호출 가능, CSRF 토큰 없음. 회원가입·로그인도 같은 구조 | 상환 폼과 같은 CSRF | **적용**: `RequestGuardFilter` 가 상태를 바꾸는 모든 요청에서 브라우저가 붙이는 `Origin`(없으면 `Referer`)을 이 서버의 출처와 비교해 다르면 403 (file:// 공격 페이지의 `Origin: null` 포함). 두 헤더가 모두 없는 요청은 curl 같은 비브라우저 클라이언트라 쿠키가 자동으로 실리지 않으므로 통과시켜 기존 API 사용법은 그대로다. 토큰으로 보호되는 `/repay-form` 은 토큰 효과를 따로 확인할 수 있도록 이 검사에서 뺐다(경로가 정확히 일치할 때만 제외 → 변형 경로는 오히려 검사를 받음). **준비**: 세션 쿠키 `SameSite=Lax` 를 `application.properties` 에 주석으로 넣어 두었다. 켜면 `csrf-attack.html` 이 쿠키 미전송(401)으로 막혀 토큰 효과를 따로 볼 수 없으므로 **토큰 검증을 확인한 뒤 주석을 해제**한다. 확인: `MANUAL_CHECK.md` 9·10단계. |
| 4 | 원본 예외 처리가 `ResponseStatusException` 을 500 으로 바꿈 | 401/403/404 가 서버 오류로 보이고 원인 구분 불가 | **적용**: 원래 상태 코드 유지 + 스프링 표준 예외(405 등)도 상태 유지 ([4]) |
| 5 | `SignupRequest` record 의 기본 `toString()` 이 비밀번호 평문·주민번호를 출력 | 로그·디버거·예외 메시지로 평문 유출 | **적용**: `customerId` 만 출력하도록 재정의 ([3]) |
| 6 | 금리 문자열에 극단적 지수(`1E-999999999`) 허용 | `BigDecimal` 연산 비용 폭증(DoS) | **적용**: 길이 20자·소수 자릿수 10 이하만 허용 ([7]) |
| 7 | 초기 데이터의 주민번호가 평문으로 저장됨 (스키마 수정 불가) | DB 유출 시 원문 노출 | **적용(표시)**: 응답에서 형식 판별 후 마스킹 ([3]). **제안**: 평문 형식인 행을 찾아 `AesCrypto.encrypt` 로 일괄 재암호화하는 1회성 마이그레이션 |
| 8 | 로그인 시 세션 ID 를 바꾸지 않음 (`LoginController`, 수정 금지 대상) | 세션 고정(Session Fixation): 공격자가 심어 둔 세션 ID 로 피해자 로그인 후 그 세션을 그대로 사용 | **적용**: `LoginController` 는 그대로 두고 앞단 `LoginSessionFilter` 가 `POST /login` 처리 **전에** `request.changeSessionId()` 로 세션 ID 를 새로 발급하고, 권한이 바뀌는 시점이므로 CSRF 토큰도 버린다(다음 폼 요청에서 재발급). 옛 세션 ID 는 즉시 무효. **제안**: 고객 ID 만으로 로그인하는 구조 자체는 수정 범위 밖이라, 운영에서는 `PasswordHasher.verify` 를 쓰는 비밀번호 인증과 2단계 인증이 필요 |
| 9 | 응답에 보안 헤더가 없음 | 클릭재킹(상환 폼을 외부 `iframe` 에 넣고 클릭 유도), MIME 스니핑, 검색어가 Referer 로 외부 유출, 개인정보 응답 캐시 | **적용**: `SecurityHeadersFilter` — CSP(인라인 스크립트 차단, `frame-ancestors 'none'`, `form-action 'self'`), `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, `Permissions-Policy`, `Cross-Origin-Opener/Resource-Policy`, `Cache-Control: no-store`. 거부 응답(401/403/413/429)에도 붙는다 |
| 10 | 고객 ID 만으로 로그인되고 요청 횟수 제한이 없음, 회원가입 409 로 ID 존재 여부 확인 가능 | 고객 ID 대입으로 남의 계정 로그인, 계정 열거, 대량 가입 | **적용**: `RateLimitFilter` — `POST /login`, `POST /api/signup` 을 클라이언트 IP 당 1분 20회로 제한, 초과 시 `429` + `Retry-After`. 위조 가능한 `X-Forwarded-For` 는 쓰지 않는다. **제안**: 운영에서는 여러 서버가 공유하는 저장소(Redis 등)와 계정 단위 잠금, CAPTCHA 를 함께 쓴다 |
| 11 | AES-GCM 암호문이 행(고객)과 묶여 있지 않음 | DB 권한자가 다른 고객 행으로 암호문을 옮겨도 복호화됨 | **적용**: 고객 ID 를 AAD 로 넣어 바인딩([6]). **제안**: 키 교체를 위해 암호문 앞에 키 버전 표시. 무작위 96비트 IV 는 키 하나당 약 2^32 회 암호화까지만 쓴다(NIST SP 800-38D) |
| 12 | 세션 만료 시간이 기본값(30분), URL 에 `;jsessionid=` 가 붙을 수 있음, 쿠키 `Secure` 없음 | 자리를 비운 사이 세션 악용, URL·Referer·로그로 세션 ID 유출, HTTP 구간 세션 탈취 | **적용**: `server.servlet.session.timeout=10m`(금융권 통상 기준인 10분 무사용 시 종료), `tracking-modes=cookie`(URL 세션 ID 금지), `http-only=true` 명시. **제안**: 운영은 HTTPS + `server.servlet.session.cookie.secure=true` + HSTS (로컬 HTTP 에서는 Secure 쿠키를 켜면 로그인이 안 됨) |
| 13 | 인증 확인이 컨트롤러마다 흩어져 있음 | 새 API 를 추가할 때 `requireCustomer` 를 빠뜨리면 바로 무인증 노출 | **적용**: `LoginRequiredInterceptor` + `WebSecurityConfig` — `/api/**` 는 기본 로그인 필수, 공개 API(`/api/interest`, `/api/signup`)만 명시적으로 제외(기본 거부). 없는 API 경로도 비로그인이면 401 이라 경로 존재 여부가 드러나지 않는다 |
| 14 | 거래·인증 이력이 남지 않음 | 부정 거래·계정 도용 사후 추적 불가 | **적용**: 감사 로거(`AUDIT`)에 `LOGIN_SUCCESS`(세션 교체 여부 포함) / `LOGIN_FAILURE`(정제한 요청 ID) / `REPAY`(고객·대출번호·금액·결과) / `CSRF_REJECTED` 기록. 운영에서는 별도 파일·위변조 방지 저장소로 분리 보관 |
| 15 | 로그 메시지에 개행이 들어가면 가짜 로그 줄을 만들 수 있음 | 로그 위조(CWE-117)로 감사 기록 조작 | **적용**: `logging.pattern.console` 에서 메시지의 CR/LF 를 공백으로 치환. `customerId=C001\r\n...LOGIN_SUCCESS customerId=ADMIN` 이 한 줄로만 남는 것을 확인 |
| 16 | 요청 본문 크기 제한 없음, 오류 설정이 기본값에 의존 | 큰 본문으로 메모리·CPU 소모, 설정 변경 시 `/error` 응답에 상세 정보 노출 | **적용**: 상태 변경 요청 본문 16KB 초과 → `413`(`RequestGuardFilter`), 폼 본문 `max-http-form-post-size=16KB`, `server.error.include-*=never/false` 명시 |
| 17 | Spring Boot 3.3.5 (내장 Tomcat 10.1.31) — 같은 3.3 라인에 이후 보안 패치 8회 | 알려진 취약 구성요소 사용(OWASP A06) | **적용**: 부모 POM 을 3.3.13 으로 올림(내장 Tomcat 10.1.42). 같은 마이너 안의 패치라 API 변화 없이 21개 테스트·기동·화면 동작 확인. **제안**: 3.3 라인은 오픈소스 지원이 끝났으므로 지원 중인 최신 라인으로 이전하고, OWASP Dependency-Check 를 빌드에 넣어 주기적으로 점검 |

## 수업 내용과 다르게 적용한 부분 / 어려웠던 점

- **레거시 평문 주민번호 판별 순서**: "복호화를 시도하고 실패하면 평문으로 본다" 대신 **형식(`\d{6}-\d{7}`)을 먼저 판별**했다. 암호문은 Base64 라 `-` 가 들어갈 수 없어 오판이 없고, 초기 데이터 3건을 조회할 때마다 예외가 나며 로그가 쌓이는 것을 피할 수 있다. 복호화에 실패한 값은 평문으로 취급하지 않고 전체 마스킹해서, 키가 틀렸을 때 암호문 일부가 화면에 나가지 않게 했다.
- **403 / 404 를 섞어 쓴 이유**: 단건 조회는 DB 결과로 판단하므로 존재 여부를 숨기는 404, ID 파라미터 비교는 DB 조회 전에 판단해 숨길 정보가 없으므로 의미가 정확한 403 을 썼다([2] 표).
- **CSRF 검사 순서**: 로그인 확인(401) → 토큰 검사(403) 순으로 했다. 둘 다 세션만 읽는 검사라 보안상 차이는 없고, 공격 페이지 확인 시 "쿠키가 안 실림(401)"과 "토큰이 막음(403)"을 구분할 수 있다. 토큰은 세션당 하나(OWASP Synchronizer Token 의 per-session 방식)로 해서 여러 탭에서 폼을 열어도 동작한다.
- **입력 규칙을 권장안보다 조인 부분**: 주민번호에 월·일 범위, 고객 ID 하한 4자, 비밀번호는 공백·비 ASCII 제외. 이유는 [5] 표에 적었다.
- **에러 처리 범위**: 과제 기준의 예외 외에, 스프링 표준 예외(`ErrorResponse` 구현체)는 원래 상태 코드를 유지하도록 했다. 모든 예외를 잡는 `Exception` 핸들러만 두면 405(메서드 미지원) 같은 요청 오류도 500 이 되기 때문이다.
- **심층 방어를 넣으면서 지킨 원칙**: 수정 금지 파일(`LoginController` 등)은 건드리지 않고 필터·인터셉터로 앞단에서 보완했고, 기존 기능·자가 점검 테스트·수동 확인 명령이 그대로 동작하도록 했다. Spring Security 를 도입하면 더 넓게 막을 수 있지만, 새 의존성이 필요하고 모든 POST 에 토큰을 요구해 기존 API 호출 방식이 바뀌므로 이번 범위에서는 쓰지 않았다.
- **방어끼리 서로 가리지 않게 한 부분**: 출처 검증과 SameSite 는 `csrf-attack.html` 을 토큰보다 먼저 막아 버려 토큰 검증 효과를 확인할 수 없게 만든다. 그래서 출처 검증은 토큰이 없는 경로에만 걸고, SameSite 는 토큰 확인 뒤에 켜도록 주석으로 준비했다.
- **자가 점검 정규식과 맞추며 주의한 점**
  - `e.toString()` 검사는 단순 문자열 포함 검사라 `xxxe.toString()` 같은 정상 코드도 걸린다 → `toString()` 호출 자체를 쓰지 않았다.
  - `<update>` 안에 `<` 가 있으면 정규식이 끊기므로 UPDATE 안에는 주석·CDATA 를 넣지 않고, 설명 주석은 태그 바깥에 두었다(`>=` 는 XML 에서 이스케이프가 필요 없다).
  - `LoanService` 에 `selectBalance` 와 `updateBalance` 가 함께 있으면 실패하므로, 메서드를 `repay` 로 새로 만들고 쓰지 않는 기존 매퍼 메서드·쿼리를 삭제했다.
  - 조회 SQL 은 `loan_id = #{loanId}` 가 `customer_id = #{...}` 보다 먼저 나와야 한다.
- **어려웠던 점**
  - Windows PowerShell 5.1 에서 `curl.exe` 로 JSON 을 보낼 때 인자 속 큰따옴표가 사라져, JSON 을 파일로 만들어 `--data-binary @파일` 로 보냈다. BOM 없는 `.ps1` 은 ANSI 로 읽혀 한글·따옴표가 깨지므로 스크립트는 UTF-8(BOM)로 저장해야 했다.
  - 동시성은 `Start-Job` 만으로는 작업마다 PowerShell 프로세스가 떠서 요청 시점이 어긋나기 쉬워, `curl.exe -Z`(병렬 전송)로도 확인할 수 있게 했다.
  - Windows PowerShell 5.1 의 `Compress-Archive` 는 zip 내부 경로를 `\` 로 저장해 다른 OS 에서 폴더 구조가 깨질 수 있어, 제출 압축은 .NET `ZipFile` 로 만들었다.