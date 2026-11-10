# MDGS 프로젝트 구조 문서

Modern Dark Game Store (MDGS) — Spring Boot(JSP + MyBatis) 기반 게임 스토어 웹앱.
STS(Spring Tool Suite) / Maven / Spring Boot 4.1.0 / 내장 Tomcat / Java 17 / Oracle DB.
Context path: `/mdgs` → 접속 주소 `http://localhost:8888/mdgs/`

이 문서는 "무엇이 어디에 있는가"를 표로 정리한 참고 문서입니다. "왜/어떻게 동작하는가"를 코드와 함께 설명한 문서는 [FEATURES_GUIDE.md](FEATURES_GUIDE.md)를 참고하세요.

> **이력**: 원래 Eclipse Dynamic Web Project(순수 Servlet/JSP, `@WebServlet` + 직접 JDBC)로 만들었던 프로젝트를, 같은 기능을 유지한 채 Spring Boot(JSP + MyBatis) 구조로 전환했습니다. 지금 이 문서가 설명하는 것이 전환된 최신 상태입니다.

현재 구현된 기능: **로그인 / 회원가입 / 로그아웃 / 게임 목록 열람 / 게임 검색 / 게임 상세 페이지 / 마이페이지(계정 정보 수정·회원 탈퇴·장바구니 목업) / 고객지원(관리자 전용 회원 조회 포함)**. 실제 구매·결제, 리뷰 작성은 아직 구현되어 있지 않습니다 (7번 항목 참고).

---

## 1. 전체 디렉터리 구조

```
MDGS/
├── pom.xml                                     ← Maven 설정 (war 패키징, 의존성 관리)
├── DOCS/                                        ← 이 문서와 FEATURES_GUIDE.md
├── MDGS.sql                                     ← sql/schema.sql과 동일한 내용의 루트 복사본 (레거시 위치, 실행은 sql/schema.sql 기준)
├── sql/
│   └── schema.sql                               ← 계정 생성 + 테이블 + 시드 데이터 스크립트
├── src/main/java/com/kh/mdgs/
│   ├── MdgsApplication.java                     ← @SpringBootApplication, main() (내장 Tomcat 기동)
│   ├── game/
│   │   ├── Game.java                            ← DTO (POJO, 생성자+getter+setter)
│   │   ├── GameMapper.java                      ← MyBatis Mapper 인터페이스
│   │   ├── GameService.java                     ← 비즈니스 로직 (검색/전체조회 판단 등)
│   │   └── GameController.java                  ← "" (홈), "/gameDetail" 처리
│   ├── member/
│   │   ├── User.java                            ← DTO (POJO)
│   │   ├── UserMapper.java                      ← MyBatis Mapper 인터페이스
│   │   ├── UserService.java                     ← 회원가입/로그인/수정/탈퇴 비즈니스 로직
│   │   └── MemberController.java                ← "/login", "/logout", "/register", "/mypage" 처리
│   └── support/
│       └── SupportController.java               ← "/support" 처리
├── src/main/resources/
│   ├── application.properties                   ← 서버 포트, DB 접속, MyBatis, JSP ViewResolver 설정
│   └── mapper/
│       ├── GameMapper.xml                       ← GameMapper 인터페이스에 대응하는 SQL
│       └── UserMapper.xml                       ← UserMapper 인터페이스에 대응하는 SQL
└── src/main/webapp/
    ├── WEB-INF/
    │   └── views/                               ← 화면(JSP). ViewResolver를 통해서만 forward로 진입 (직접 URL 접근 불가)
    │       ├── common/
    │       │   ├── header.jsp                   ← 상단 배너+검색창+네비게이션
    │       │   └── footer.jsp                   ← 하단 푸터
    │       ├── gameList.jsp                     ← 게임 목록/검색 결과 (홈 화면 겸용) + 배너 슬라이드
    │       ├── gameDetail.jsp                   ← 게임 상세 화면
    │       ├── login.jsp
    │       ├── register.jsp
    │       ├── mypage.jsp
    │       └── support.jsp
    └── resources/                                ← 정적 리소스. war 패키징이라 별도 설정 없이 그대로 서빙됨
        ├── css/mdgs.css                          ← 전체 사이트 스타일시트
        ├── images/                               ← 게임 표지 이미지 + 로고 등
        └── newFile.js                            ← 호버 프리뷰 / 배너 슬라이드 / 회원탈퇴 확인창 스크립트
```

---

## 2. HTML / CSS / 이미지 / JS는 어디에 있는가

| 종류 | 위치 | 비고 |
|---|---|---|
| 화면(JSP) | `src/main/webapp/WEB-INF/views/*.jsp` | `WEB-INF` 안에 있어서 브라우저가 직접 접근 불가. 반드시 Controller가 반환한 뷰 이름을 ViewResolver가 forward해줘야만 보임 |
| 공통 헤더/푸터 | `src/main/webapp/WEB-INF/views/common/*.jsp` | `<jsp:include page="/WEB-INF/views/common/header.jsp" />`로 각 화면에 삽입됨 |
| CSS | `src/main/webapp/resources/css/mdgs.css` | 다크 테마, `:root`에 색상 변수 정의 |
| 이미지 | `src/main/webapp/resources/images/` | 총 6개 파일 — 게임 표지 4장(`HADES_4k.jpg`/`R6_4k.jpg`/`Clair_Obscur_Expedition_33_4k.jpg`/`DIAVLO_IV_4k.png`) + 로고 1장(`MDGS_Logo.png`) + 메인 배너용 1장(`MDGS_Theme.png`). 파일명 공백은 전부 `_`로 치환됨 |
| JS | `src/main/webapp/resources/newFile.js` | 호버 프리뷰 / 배너 자동·수동 슬라이드 / 회원탈퇴 확인창 (바닐라 JS, 파일 하나) |

JSP에서는 전부 `${ctx}/resources/...` 형태로 정적 리소스를 참조합니다 (`${ctx}`는 `${pageContext.request.contextPath}`의 줄임 — 각 JSP 상단 `<c:set var="ctx" .../>`로 선언). `WEB-INF/views`와 달리 `webapp/resources`는 `WEB-INF` 밖에 있으므로 서블릿 컨테이너가 별도 설정 없이 그대로 서빙합니다.

---

## 3. Java 소스 — 계층별 역할

스프링에서 권장하는 **Controller → Service → Mapper(Repository 역할)** 구조를 따릅니다.

| 계층 | 클래스 | 역할 |
|---|---|---|
| Controller | `GameController` | `@GetMapping("/")`(홈), `@GetMapping("/gameDetail")`. 요청 파라미터를 받아 Service 호출 후 Model에 담아 뷰 이름 반환 |
| Controller | `MemberController` | `/login`, `/logout`, `/register`, `/mypage` GET/POST 처리 |
| Controller | `SupportController` | `/support` 처리 |
| Service | `GameService` | 검색어(`q`) 유무 판단 후 `GameMapper.search()`/`findAll()` 호출, `findById()` |
| Service | `UserService` | 회원가입/로그인/중복확인/조회/수정/탈퇴 — 내부적으로 `UserMapper` 호출 |
| Mapper | `GameMapper` + `GameMapper.xml` | `findAll`, `search`, `findById` SQL 실행 (MyBatis) |
| Mapper | `UserMapper` + `UserMapper.xml` | `insert`, `login`, `countById`, `findBySid`, `update`, `delete`, `findAll` SQL 실행 |
| DTO | `Game`, `User` | 순수 POJO (Lombok 없이 생성자+getter+setter 직접 작성) |
| 진입점 | `MdgsApplication` | `@SpringBootApplication`, `main()`에서 `SpringApplication.run()` 호출 → 내장 Tomcat 기동 |

기존 `config.DBConnection`(수동 `DriverManager` 연결)은 삭제되었습니다 — `application.properties`의 `spring.datasource.*` 설정을 Spring Boot가 읽어 HikariCP 커넥션 풀을 자동으로 구성합니다. 기존 `dao` 패키지(순수 JDBC)는 `mapper` 패키지(MyBatis)로 대체되었습니다.

---

## 4. URL 라우팅 표

| URL | 메서드 | 처리 Controller | 동작 |
|---|---|---|---|
| `/` | GET | `GameController#list` | `q` 파라미터 없으면 전체 게임 목록, 있으면 검색 결과 → `gameList` 뷰 |
| `/gameDetail?id=N` | GET | `GameController#detail` | 게임 하나 조회 → `gameDetail` 뷰 |
| `/login` | GET | `MemberController#loginForm` | `login` 뷰 표시 |
| `/login` | POST | `MemberController#login` | 로그인 검증 → 성공 시 세션 생성 후 `redirect:/`, 실패 시 에러 메시지와 함께 `login` 뷰 재표시 |
| `/logout` | GET | `MemberController#logout` | 세션 종료 후 `redirect:/` |
| `/register` | GET | `MemberController#registerForm` | `register` 뷰 표시 |
| `/register` | POST | `MemberController#register` | 중복 ID 체크 → 가입 처리 → 성공 시 `redirect:/login` |
| `/mypage` | GET | `MemberController#myPage` | 로그인 필요(없으면 `redirect:/login`) → `mypage` 뷰 |
| `/mypage` | POST | `MemberController#myPageSubmit` | 숨김 필드 `action`이 `withdraw`면 탈퇴, 아니면 계정 정보 수정 |
| `/support` | GET | `SupportController#support` | 로그인 불필요. 관리자 로그인 시 전체 회원 목록도 함께 조회 → `support` 뷰 |

Controller 메소드가 문자열(예: `"gameList"`)을 반환하면, `application.properties`의 `spring.mvc.view.prefix`/`suffix` 설정에 따라 `/WEB-INF/views/gameList.jsp`로 자동 forward됩니다. `"redirect:/login"`처럼 `redirect:` 접두사를 붙이면 forward 대신 브라우저 리다이렉트가 발생합니다.

**세션/Model 속성 이름 규칙** (기존 서블릿 버전의 request 속성 이름을 그대로 유지)
- 세션(`HttpSession`): `loginId` (로그인한 아이디 문자열), `userSid` (User_SID, DB 조회/수정/삭제 시 사용)
- Model(`model.addAttribute(...)`, JSP에서는 request 속성과 동일하게 `${...}`로 접근): `errorMsg`/`successMsg`, `gameList` (`List<Game>`), `game` (`Game`), `user` (`User`), `isAdmin` (boolean), `allUsers` (`List<User>`, 관리자 전용)

헤더(`WEB-INF/views/common/header.jsp`)는 `sessionScope.loginId` 유무로 장바구니 노출 여부와 "로그인/회원가입" ↔ "{아이디}님 · 내 페이지" 표시를 전환합니다 (로그아웃 버튼은 헤더가 아니라 마이페이지 안에 있음). 관리자 판별은 `MemberController`/`SupportController`에 각각 있는 `ADMIN_ID = "MDGS1226"` 상수와 `loginId`를 비교하는 방식입니다 — 자세한 동작은 [FEATURES_GUIDE.md](FEATURES_GUIDE.md) 참고.

---

## 5. 데이터베이스 스키마

Oracle, 계정 `C##MDGS` (SID: `xe`). 실제 스크립트는 `sql/schema.sql` 참고 — SYS 계정으로 사용자 생성 → `C##MDGS` 계정으로 테이블 생성까지 처음부터 전부 포함되어 있습니다.

**USERS**
| 컬럼 | 타입 | 비고 |
|---|---|---|
| User_SID | VARCHAR2(30) | PK |
| ID | VARCHAR2(50) | UNIQUE, 로그인 아이디 |
| Password | VARCHAR2(100) | 평문 저장 (해싱 미적용) |
| Email | VARCHAR2(100) | |
| Age | NUMBER(3) | |
| Address | VARCHAR2(200) | |
| Card_Num | VARCHAR2(30) | |

관리자 계정 하나(`ID = MDGS1226`)가 DB에 직접 미리 생성되어 있습니다. 일반 회원가입 절차를 거치지 않고 만들어졌고, 이 `ID` 문자열 자체가 곧 "관리자 여부"를 가르는 값입니다 (자세한 동작은 [FEATURES_GUIDE.md](FEATURES_GUIDE.md)의 11번 항목 참고).

**GAMES**
| 컬럼 | 타입 | 비고 |
|---|---|---|
| GAME_ID | NUMBER | PK |
| TITLE | VARCHAR2(200) | 검색 대상 컬럼 (`LIKE '%키워드%'`) |
| GENRE | VARCHAR2(50) | 목록 화면 카테고리 표시에 사용 |
| PRICE | NUMBER(10,2) | 상세 페이지에서 통화 형식으로 표시 |
| RELEASE_DATE | DATE | 상세 페이지에서 `yyyy-MM-dd` 형식으로 표시 |
| DEVELOPER | VARCHAR2(100) | |
| IMAGE_PATH | VARCHAR2(200) | `resources/images/...` 형태 상대 경로. 목록/배너/상세 페이지가 모두 이 컬럼을 사용 |

현재 시드 데이터: Hades / Rainbow Six Siege / Clair Obscur: Expedition 33 / Diablo IV (Game_ID 1~4).

MyBatis 매퍼 XML(`GameMapper.xml`, `UserMapper.xml`)에서는 이 컬럼명(`GAME_ID`, `Title` 등)과 Java DTO의 camelCase 필드명(`gameId`, `title` 등)이 다르므로, `SELECT ... AS gameId` 형태로 별칭을 맞춰서 매핑합니다.

---

## 6. 정적 프로토타입과의 관계

이 프로젝트가 시작되기 전 `MDGS.html` / `MDGS.css`로 만들어졌던 정적 시안이 있었습니다. 그 파일들의 디자인(다크 테마, 배너, 카테고리 메뉴, 게임 카드 그리드, 우측 프리뷰 패널)을 그대로 가져와 JSP로 재구성한 것이 `gameList.jsp` + `resources/css/mdgs.css`의 출발점입니다. 원본과 다른 점:
- 게임 카드가 하드코딩이 아니라 `<c:forEach>`로 DB 데이터를 순회하며 렌더링됨 (검색 결과도 같은 방식)
- 카드 hover 시 우측 프리뷰 이미지를 바꾸던 CSS(`nth-child` 기반)는 게임 개수에 따라 깨지므로 제거하고, 자바스크립트(`newFile.js`)로 대체
- 원본에 없던 화면(로그인/회원가입, 게임 상세, 마이페이지, 고객지원)과 기능(배너 자동 슬라이드, 검색)을 이후 작업에서 새로 추가

---

## 7. 구현 범위

**완료**: 로그인, 회원가입(중복 ID 체크 포함), 로그아웃, 게임 목록 열람(DB 연동), 게임 검색(제목 부분 일치), 게임 상세 페이지, 메인 배너 자동/수동 슬라이드, 게임 카드 호버 프리뷰, 로그인 상태에 따른 헤더 표시 전환, 마이페이지(계정 정보 수정 실반영, 회원 탈퇴 실반영·관리자 계정 제외, 장바구니/결제는 화면용 목업), 고객지원 페이지(정적 FAQ + 로그인 상태별 안내 + 관리자 전용 전체 회원 조회), **Controller → Service → Mapper 계층 분리**

**미구현 (의도적으로 범위 제외)**: 실제 게임 구매/결제, 리뷰 작성, Spring Security(로그인은 직접 만든 세션 기반 방식만 사용)

---

## 8. 실행 방법 (STS)

1. STS에서 `File > Import > Maven > Existing Maven Projects` → 이 폴더(`pom.xml` 위치) 선택 → Finish (의존성 자동 다운로드)
2. Oracle DB(`C##MDGS`/`mdgs`, `localhost:1521:xe`)가 실행 중인지 확인. 테이블이 없다면 `sql/schema.sql` 실행
3. `src/main/resources/application.properties`의 DB 접속 정보가 실제 환경과 일치하는지 확인
4. 프로젝트 우클릭 → `Run As > Spring Boot App` (내장 Tomcat이 8888 포트로 기동)
5. 브라우저에서 `http://localhost:8888/mdgs/` 접속
