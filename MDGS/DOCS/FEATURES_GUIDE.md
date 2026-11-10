# MDGS 기능 구현 설명서

이 문서는 MDGS에서 실제로 동작하는 모든 기능을 하나씩 짚어가며, **"화면에서 무엇이 보이는가"** 와 **"그걸 만드는 코드가 어디에 있고 어떻게 동작하는가"** 를 연결해서 설명합니다.

파일이 어디에 있는지에 대한 표 형태 정리는 [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md)를 참고하세요. 이 문서는 "왜/어떻게 동작하는가"에 집중합니다.

---

## 0. 먼저 알아야 할 기본 구조

MDGS의 모든 기능은 아래와 같은 동일한 흐름을 따릅니다. 이 흐름 하나만 이해하면 아래 모든 기능 설명이 쉬워집니다.

```
① 브라우저 요청           예: GET /mdgs/login
        │
        ▼
② DispatcherServlet       모든 요청을 가장 먼저 받는 프론트 컨트롤러 (Spring Boot가 자동 등록)
        │                  요청 URL에 맞는 Controller 메소드를 찾아 실행
        ▼
③ Controller               @GetMapping("/login") 같은 어노테이션이 붙은 메소드 실행 (game/member/support 패키지)
        │
        ▼
④ Service 호출 (필요할 때만)  비즈니스 로직 수행, 내부에서 Mapper 호출
        │
        ▼
⑤ Mapper(MyBatis) 호출      mapper/*.xml에 작성된 SQL을 실행해 DB에 접근
        │
        ▼
⑥ 결과를 Model에 담음        model.addAttribute("gameList", 결과값) 처럼 이름표를 붙여서 보관
        │
        ▼
⑦ 뷰 이름 반환 → forward     return "gameList"; 를 반환하면 ViewResolver가
        │                  /WEB-INF/views/gameList.jsp 로 자동 forward
        ▼
⑧ JSP가 화면을 그림          ${gameList} 같은 EL 표현식과 <c:forEach>, <c:if>, <c:choose> 같은
        │                  JSTL 태그로 Model/session에 담긴 값을 꺼내 HTML을 만듦
        ▼
⑨ 브라우저에 최종 HTML 응답
```

이 프로젝트는 서블릿을 직접 작성하지 않습니다. 모든 URL 연결은 Controller 클래스의 메소드 위에 붙은 `@GetMapping("/경로")` / `@PostMapping("/경로")` 어노테이션 한 줄로 끝납니다. 홈 화면(`/`)도 `GameController`의 `@GetMapping("/")`로 그냥 하나의 일반적인 매핑일 뿐입니다 (예전 서블릿 버전에서는 `""` 패턴이 welcome-file보다 우선한다는 특수 규칙이 있었는데, 지금은 그런 규칙이 필요 없습니다 — `web.xml`도, `index.html`도 프로젝트에 없습니다).

### 자주 등장하는 이름들

| 이름 | 종류 | 의미 |
|---|---|---|
| `${ctx}` | JSP 변수 | `${pageContext.request.contextPath}` (즉 `/mdgs`)의 줄임 표현. 모든 JSP 맨 위 `<c:set var="ctx" .../>` 한 줄로 선언 |
| `sessionScope.loginId` | 세션 속성 | 로그인한 사용자의 아이디 문자열. 로그인 성공 시 저장, 로그아웃/탈퇴 시 사라짐 |
| `sessionScope.userSid` | 세션 속성 | 로그인한 사용자의 `User_SID`(PK). DB 조회/수정/삭제할 때 어떤 행인지 특정하는 용도 |
| `errorMsg` / `successMsg` | Model 속성 | 방금 처리한 작업의 실패/성공 메시지. Controller가 `model.addAttribute("errorMsg", ...)`로 담고, JSP에서 `<c:if test="${not empty errorMsg}">`로 조건부 표시 |
| `gameList` | Model 속성 | `List<Game>`. 홈 화면·검색 결과 화면에서 게임 카드를 그릴 때 사용 |
| `game` | Model 속성 | `Game` 객체 하나. 게임 상세 페이지에서 사용 |
| `user` | Model 속성 | `User` 객체 하나. 마이페이지에서 사용 |
| `isAdmin` | Model 속성 | 관리자 로그인 여부(boolean). 마이페이지·고객지원 페이지에서 사용 |

> `Model`(`org.springframework.ui.Model`)에 `addAttribute`로 담은 값은 JSP 입장에서는 예전 `request.setAttribute`와 똑같이 `${이름}`으로 꺼내 쓸 수 있습니다 (내부적으로 requestScope에 저장되기 때문).

### 관리자 판별 방식

DB에 "권한" 컬럼이 따로 없습니다. 대신 `MemberController`와 `SupportController`에 각각 다음과 같은 상수가 있고,

```java
private static final String ADMIN_ID = "MDGS1226";
```

로그인 세션의 `loginId`가 이 문자열과 같은지만 비교합니다 (`ADMIN_ID.equals(session.getAttribute("loginId"))`). 이 아이디로 로그인하면 곧 관리자입니다. 학습 목적의 최소 구현이라 일부러 이렇게 단순하게 만들었습니다.

---

## 목차

1. [게임 목록 열람 (홈 화면)](#1-게임-목록-열람-홈-화면)
2. [메인 배너 자동·수동 슬라이드](#2-메인-배너-자동수동-슬라이드)
3. [게임 카드 호버 프리뷰](#3-게임-카드-호버-프리뷰)
4. [게임 검색](#4-게임-검색)
5. [게임 상세 페이지](#5-게임-상세-페이지)
6. [회원가입](#6-회원가입)
7. [로그인](#7-로그인)
8. [로그아웃](#8-로그아웃)
9. [헤더 로그인 상태 표시](#9-헤더-로그인-상태-표시)
10. [마이페이지](#10-마이페이지)
11. [고객지원 페이지](#11-고객지원-페이지)
12. [공통 헤더/푸터 (jsp:include)](#12-공통-헤더푸터-jspinclude)

---

## 1. 게임 목록 열람 (홈 화면)

`http://localhost:8888/mdgs/` 접속 시 DB에 저장된 게임들이 2열 그리드로 보이는 기능입니다.

**관련 파일**: `GameController.java` → `GameService.java` → `GameMapper.java`/`GameMapper.xml` → `gameList.jsp`

**동작 흐름**
1. `GameController`의 `list()` 메소드가 `@GetMapping("/")`로 홈 화면에 매핑되어 있습니다.
2. `GameService.getGameList(q)`를 호출합니다. `q`(검색어) 파라미터가 있으면 검색, 없으면 전체 조회로 내부에서 갈라집니다.
3. `model.addAttribute("gameList", ...)`로 이름표를 붙입니다.
4. `"gameList"`를 반환하면 `gameList.jsp`로 forward됩니다.
5. JSP는 `<c:forEach var="game" items="${gameList}">`로 목록을 순회하며 카드 하나씩 그립니다.

```java
// GameController.java
@GetMapping("/")
public String list(@RequestParam(value = "q", required = false) String q, Model model) {
    model.addAttribute("gameList", gameService.getGameList(q));
    return "gameList";
}
```

```java
// GameService.java
public List<Game> getGameList(String keyword) {
    if (keyword != null && !keyword.trim().isEmpty()) {
        return gameMapper.search(keyword.trim());   // 검색어가 있으면 검색 (4번 항목 참고)
    }
    return gameMapper.findAll();                     // 없으면 전체 목록
}
```

게임이 하나도 없을 때는 `<c:if test="${empty gameList}">`로 안내 문구를 대신 보여줍니다.

---

## 2. 메인 배너 자동·수동 슬라이드

화면 맨 위 큰 배너가 5초마다 자동으로 옆으로 넘어가고, 좌우 화살표로도 수동 전환됩니다.

**관련 파일**: `gameList.jsp` (구조) + `mdgs.css` (`.hero-slider`/`.slider-viewport`/`.slider-track`/`.slide`) + `newFile.js` (동작 로직)

**구조**: `.slider-track` 안에 `.slide`(이미지 하나씩)를 가로로 나열해두고, `.slider-viewport{overflow:hidden}`으로 한 장만 보이게 가려둡니다.

```jsp
<!-- gameList.jsp -->
<div class="slider-viewport">
    <div class="slider-track" id="sliderTrack">
        <div class="slide"><img src="${ctx}/resources/images/MDGS_Theme.png" ...></div>
        <c:forEach var="game" items="${gameList}">
            <div class="slide"><img src="${ctx}/${game.imagePath}" ...></div>
        </c:forEach>
    </div>
</div>
```

**이동 원리**: `.slider-track`을 `transform: translateX(-N%)`로 왼쪽으로 밀면, 다음 슬라이드가 오른쪽에서 나타나는 것처럼 보입니다. `transition: transform 0.6s`가 걸려있어서 순간 이동이 아니라 미끄러지듯 움직입니다.

**끊김 없는 무한 순환 (핵심 트릭)**: 슬라이드가 4장이라고 3번째 다음이 곧바로 1번째로 "점프"하면 화면이 뚝 끊겨 보입니다. 이를 자연스럽게 만들려고 `newFile.js`에서 첫 슬라이드의 복제본을 트랙 맨 끝에, 마지막 슬라이드의 복제본을 트랙 맨 앞에 미리 붙여둡니다.

```js
// newFile.js
var firstClone = realSlides[0].cloneNode(true);
var lastClone = realSlides[realCount - 1].cloneNode(true);
track.appendChild(firstClone);           // [복제-마지막, 1, 2, 3, 4, 복제-1]
track.insertBefore(lastClone, realSlides[0]);
```

마지막(진짜 4번)에서 오른쪽으로 한 번 더 넘기면 실제로는 "복제-1"로 이동합니다. 화면상 1번 이미지가 보이는 건 진짜와 똑같으므로, 이 순간 `transitionend` 이벤트에서 애니메이션 없이(`transition: none`) 진짜 1번 위치로 순간 이동시켜버립니다. 사용자는 이 순간 이동을 볼 수 없고, 그저 자연스럽게 계속 오른쪽으로 넘어가는 것처럼 느낍니다.

```js
track.addEventListener('transitionend', function () {
    if (currentIndex === totalCount - 1) {      // 복제-1(가짜 끝)에 도착했으면
        currentIndex = 1;
        setPosition(currentIndex, false);        // 트랜지션 없이 진짜 1번 위치로 순간 이동
    } else if (currentIndex === 0) {              // 복제-마지막(가짜 시작)에 도착했으면
        currentIndex = realCount;
        setPosition(currentIndex, false);
    }
});
```

**자동 재생과 화살표**: `setInterval(nextSlide, 5000)`으로 5초마다 자동 전환하고, `#hidari`(왼쪽)/`#migi`(오른쪽) 화살표를 누르면 즉시 전환 + 타이머를 리셋(`resetAutoSlide`)해서 방금 누른 조작이 곧바로 다음 자동 전환에 덮이지 않게 합니다.

---

## 3. 게임 카드 호버 프리뷰

목록 화면 하단, 게임 카드에 마우스를 올리면 오른쪽 미리보기 영역의 이미지가 바뀝니다.

**관련 파일**: `gameList.jsp` (`data-image` 속성) + `newFile.js` (이벤트 처리) + `mdgs.css`

카드마다 이미지 경로를 `data-image`라는 커스텀 속성에 미리 심어둡니다.

```jsp
<a href="${ctx}/gameDetail?id=${game.gameId}" class="game-card" data-image="${ctx}/${game.imagePath}">
    <img src="${ctx}/${game.imagePath}" alt="${game.title} 대표 이미지" width="250">
</a>
```

`newFile.js`가 모든 `.game-card`에 마우스 진입/이탈 이벤트를 걸어두고, 진입 시 오른쪽 `#gamePreviewArea`의 CSS 배경 이미지를 그 카드의 `data-image` 값으로 교체합니다.

```js
card.addEventListener('mouseenter', function () {
    previewArea.style.backgroundImage = "url('" + card.dataset.image + "')";
});
card.addEventListener('mouseleave', function () {
    previewArea.style.backgroundImage = '';
});
```

카드 개수가 몇 개든 상관없이 동작하는 방식입니다. 마우스가 올라가 있는 동안엔 CSS `:has()` 선택자로 "게임에 마우스를 올려보세요" 안내 문구를 숨깁니다.

```css
.game-content-section:has(.game-card:hover) .preview-content {
    display: none;
}
```

---

## 4. 게임 검색

헤더의 검색창에 게임 이름을 입력하면 DB에서 실제로 조회해 홈 화면에 결과만 보여줍니다.

**관련 파일**: `header.jsp` (검색창) → `GameController.java` → `GameService.java` → `GameMapper.xml`(`search`) → `gameList.jsp` (결과 표시)

검색창은 `<form method="get" action="${ctx}/">`로 감싸져 있어서, 어느 페이지에서 검색하든 항상 홈 화면(`GameController#list`)으로 이동합니다.

```jsp
<!-- header.jsp -->
<form class="search-box" id="banner-search" action="${ctx}/" method="get">
    <input type="text" name="q" placeholder="게임을 검색하세요.." value="${param.q}">
    <button class="btn" type="submit"><i class="fa-solid fa-magnifying-glass"></i></button>
</form>
```

`GameService.getGameList()`는 `q` 파라미터 존재 여부만 확인해서 검색/전체조회를 나눕니다 (1번 항목의 코드 참고). 실제 검색은 MyBatis XML의 `LIKE` 구문으로 부분 일치 + 대소문자 무시(`UPPER`) 처리합니다.

```xml
<!-- GameMapper.xml -->
<select id="search" resultType="Game">
    SELECT GAME_ID AS gameId, TITLE AS title, GENRE AS genre, PRICE AS price,
           RELEASE_DATE AS releaseDate, DEVELOPER AS developer, IMAGE_PATH AS imagePath
    FROM GAMES
    WHERE UPPER(TITLE) LIKE UPPER('%' || #{keyword} || '%')
</select>
```

`#{keyword}`는 `PreparedStatement`의 자리표시자(`?`) 역할을 대신하기 때문에, 검색어에 `'`처럼 SQL에서 특별한 의미를 갖는 문자가 들어가도 안전합니다 (SQL 인젝션 방지). 검색 결과가 0건이면 `gameList.jsp`가 `${param.q}` 값을 이용해 "'검색어'에 대한 검색 결과가 없습니다"라고 안내합니다.

---

## 5. 게임 상세 페이지

게임 카드(또는 검색 결과 카드)를 클릭하면 그 게임 하나의 상세 정보 화면으로 이동합니다.

**관련 파일**: `GameController.java`(`detail`) → `GameService.java` → `GameMapper.xml`(`findById`) → `gameDetail.jsp`

게임 카드 자체가 링크입니다: `<a href="${ctx}/gameDetail?id=${game.gameId}" class="game-card" ...>`.

```java
// GameController.java
@GetMapping("/gameDetail")
public String detail(@RequestParam(value = "id", required = false) String id, Model model) {
    Game game;
    try {
        game = gameService.getGameById(Integer.parseInt(id));
    } catch (NumberFormatException e) {
        game = null;
    }
    model.addAttribute("game", game);
    return "gameDetail";
}
```

`id`가 숫자가 아니거나(URL을 직접 이상하게 조작), 그런 `Game_ID`가 DB에 없으면 `game`이 `null`인 채로 넘어갑니다. `gameDetail.jsp`는 `<c:choose>`로 이 경우를 갈라서 "존재하지 않는 게임입니다" 안내 + 목록으로 돌아가는 링크를 보여줍니다. 정상일 땐 이미지·이름·장르·가격·출시일·개발사를 표시하는데, 가격/날짜는 JSTL의 `fmt` 태그 라이브러리(`jakarta.tags.fmt`)로 형식을 맞춥니다.

```jsp
<p class="gamedetail-price">$<fmt:formatNumber value="${game.price}" minFractionDigits="2" maxFractionDigits="2" /></p>
<li><strong>출시일</strong> <fmt:formatDate value="${game.releaseDate}" pattern="yyyy-MM-dd" /></li>
```

---

## 6. 회원가입

**관련 파일**: `register.jsp` → `MemberController.java`(`register`) → `UserService.java` → `UserMapper.xml`(`countById`, `insert`)

1. `register.jsp`의 폼이 `POST /register`로 아이디/비밀번호/이메일/나이/주소/카드번호를 보냅니다.
2. `MemberController.register()`가 먼저 `UserService.isDuplicateId(id)`로 중복 아이디인지 확인합니다. 중복이면 `errorMsg`를 담아 다시 폼을 보여줍니다.
3. 통과하면 `User_SID`를 직접 만들어서(`generateUserSid()`, 현재 시각 기반 문자열) `User` 객체를 만들고 `UserService.register()`로 INSERT합니다.
4. 성공하면 `redirect:/login`으로 이동합니다.

```java
// MemberController.java
@PostMapping("/register")
public String register(@RequestParam String id, @RequestParam String password,
        @RequestParam String email, @RequestParam String age,
        @RequestParam(required = false) String address,
        @RequestParam(required = false) String cardNum,
        Model model) {
    if (userService.isDuplicateId(id)) {
        model.addAttribute("errorMsg", "중복된 ID입니다.");
        return "register";
    }
    User newUser = new User(generateUserSid(), id, password, email,
            Integer.parseInt(age), address, cardNum);
    if (userService.register(newUser)) {
        return "redirect:/login";
    }
    model.addAttribute("errorMsg", "회원가입에 실패했습니다. 잠시 후 다시 시도해주세요.");
    return "register";
}

private String generateUserSid() {
    return "U_" + new SimpleDateFormat("yyyyMMddHHmmssSSS").format(new Date());
}
```

별도의 시퀀스나 auto-increment 없이, 이 프로젝트에서는 "현재 시각을 밀리초까지 문자열로 붙인 값"을 기본 키로 사용합니다. 같은 밀리초에 두 명이 동시에 가입하지 않는 한 충돌하지 않습니다.

---

## 7. 로그인

**관련 파일**: `login.jsp` → `MemberController.java`(`login`) → `UserService.java`(`login`) → `UserMapper.xml`(`login`)

```java
// UserService.java
public String login(String id, String password) {
    User found = userMapper.login(new User(null, id, password, null, 0, null, null));
    return (found != null) ? found.getUserSid() : null;
}
```

```xml
<!-- UserMapper.xml -->
<select id="login" resultType="User">
    SELECT User_SID AS userSid, ID AS id, Password AS password, Email AS email,
           Age AS age, Address AS address, Card_Num AS cardNum
    FROM USERS
    WHERE ID = #{id} AND Password = #{password}
</select>
```

`id`/`password`를 담은 `User` 객체를 그대로 파라미터로 넘겨서, `#{id}`/`#{password}`로 바인딩합니다 (MyBatis의 객체 파라미터 바인딩 방식).

`MemberController.login()`은 이 반환값이 `null`이 아니면 로그인 성공으로 간주하고, `HttpSession`에 `userSid`와 `loginId`(입력한 아이디)를 저장한 뒤 홈으로 리다이렉트합니다. `null`이면 `errorMsg`와 함께 로그인 폼을 다시 보여줍니다.

```java
// MemberController.java
@PostMapping("/login")
public String login(@RequestParam String id, @RequestParam String password,
        HttpSession session, Model model) {
    String userSid = userService.login(id, password);
    if (userSid != null) {
        session.setAttribute("userSid", userSid);
        session.setAttribute("loginId", id);
        return "redirect:/";
    }
    model.addAttribute("errorMsg", "아이디 또는 비밀번호가 올바르지 않습니다.");
    return "login";
}
```

이 두 세션 값이 곧 "로그인 상태"의 전부입니다. 다른 모든 기능(헤더 표시, 마이페이지 접근 제한, 관리자 판별)이 이 두 값의 존재 여부/내용만으로 동작합니다.

> 참고: 비밀번호는 암호화 없이 평문으로 DB에 저장/비교합니다. 학습용 프로젝트라 의도적으로 단순화한 부분입니다. (Spring Security의 비밀번호 암호화 기능은 이번 학습 범위 밖이라 사용하지 않습니다.)

---

## 8. 로그아웃

**관련 파일**: `MemberController.java`(`logout`, 헤더에는 링크 없음 — 9번 항목 참고), `mypage.jsp`의 "로그아웃" 링크

```java
// MemberController.java
@GetMapping("/logout")
public String logout(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session != null) {
        session.invalidate();   // 세션 자체를 통째로 폐기
    }
    return "redirect:/";
}
```

`getSession(false)`는 "세션이 이미 있으면 가져오고, 없으면 새로 만들지 않는다"는 의미입니다 (로그아웃 상태에서 굳이 새 세션을 만들 필요가 없으므로 — 컨트롤러 메소드 매개변수로 그냥 `HttpSession session`을 받으면 세션이 없을 때 새로 만들어버리기 때문에, 이 경우는 일부러 `HttpServletRequest`를 받아 `getSession(false)`로 직접 처리합니다). `session.invalidate()` 한 번으로 `loginId`/`userSid`를 포함한 세션 정보 전체가 사라집니다.

---

## 9. 헤더 로그인 상태 표시

헤더의 "장바구니 / 로그인 / 회원가입" 영역이 로그인 여부에 따라 다르게 보입니다.

**관련 파일**: `header.jsp`

```jsp
<c:if test="${not empty sessionScope.loginId}">
    <a href="#" class="cart" id="banner-cart">장바구니</a>
</c:if>
<c:choose>
    <c:when test="${not empty sessionScope.loginId}">
        <span class="welcome-msg">${sessionScope.loginId}님</span>
        <a href="${ctx}/mypage" class="mypage-link">내 페이지</a>
    </c:when>
    <c:otherwise>
        <a href="${ctx}/login" class="login">로그인</a>
        <a href="${ctx}/register" class="signup">회원가입</a>
    </c:otherwise>
</c:choose>
```

- 로그아웃 상태: 장바구니 링크는 아예 렌더링되지 않고(`<c:if>`), "로그인"/"회원가입" 링크만 보입니다.
- 로그인 상태: 장바구니가 나타나고, "로그인/회원가입" 대신 "{아이디}님"과 "내 페이지"가 보입니다. 헤더에는 로그아웃 버튼이 없습니다 — 로그아웃은 마이페이지 안으로 옮겨져 있습니다 (8번 항목).

`header.jsp`는 `<jsp:include>`로 모든 화면 맨 위에 삽입되므로, 이 조건은 사실상 사이트 전체에 공통 적용됩니다.

---

## 10. 마이페이지

로그인한 사용자만 접근 가능한 `/mypage` 화면으로, 계정 정보 수정 / 회원 탈퇴 / 장바구니(목업)를 한 곳에 모아둔 페이지입니다.

**관련 파일**: `MemberController.java`(`myPage`, `myPageSubmit`) → `UserService.java`(`getUserBySid`/`updateUser`/`deleteUser`) → `mypage.jsp`

**접근 제한**: `myPage`/`myPageSubmit` 둘 다 시작하자마자 세션에 `userSid`가 없으면 `/login`으로 돌려보냅니다.

```java
HttpSession session = request.getSession(false);
if (session == null || session.getAttribute("userSid") == null) {
    return "redirect:/login";
}
```

한 화면 안에 폼이 2개 있고(계정 정보 수정 / 회원 탈퇴), 둘 다 같은 URL(`/mypage`)로 POST하기 때문에 숨겨진 `action` 필드로 어떤 요청인지 구분합니다.

```jsp
<!-- 계정 정보 수정 폼 -->
<input type="hidden" name="action" value="update">
...
<!-- 회원 탈퇴 폼 -->
<input type="hidden" name="action" value="withdraw">
```

```java
// MemberController.java
@PostMapping("/mypage")
public String myPageSubmit(@RequestParam String action, ... , HttpServletRequest request, Model model) {
    ...
    if ("withdraw".equals(action)) {
        ... // 탈퇴 처리
        return "mypage"; // 또는 redirect:/
    }
    ... // action이 withdraw가 아니면 여기로 내려와서 정보 수정 처리
}
```

**계정 정보 수정**: 아이디는 `disabled` 처리되어 있어 폼 제출 시 아예 전송되지 않습니다(수정 불가 항목). 이메일/나이/주소/카드번호만 `@RequestParam`으로 받아 `UserService.updateUser()`로 UPDATE합니다.

**회원 탈퇴**: `UserService.deleteUser()`로 `USERS` 테이블에서 행을 DELETE한 뒤 `session.invalidate()`로 로그아웃까지 같이 처리합니다. 단, **관리자 계정(`MDGS1226`)은 탈퇴할 수 없도록 서버에서 막아둡니다** — `myPageSubmit` 안에서 `isAdmin`이면 `deleteUser()`를 아예 호출하지 않고 에러 메시지만 반환하고, `mypage.jsp`도 관리자일 땐 탈퇴 버튼 자체를 그리지 않습니다(`<c:choose>`로 분기). 화면에서 버튼을 숨기는 것과 서버에서 재검증하는 것 두 가지를 모두 하는 이유는, 버튼을 숨기는 것만으로는 URL을 직접 조작한 요청까지 막을 수 없기 때문입니다.

탈퇴 버튼을 누르면 `newFile.js`가 `confirm()` 팝업으로 한 번 더 확인합니다 (문구는 `newFile.js`의 `withdrawForm` 관련 블록에서 직접 수정 가능).

```js
withdrawForm.addEventListener('submit', function (e) {
    if (!confirm('...')) {
        e.preventDefault();   // 취소를 누르면 폼 제출 자체를 막음
    }
});
```

**장바구니/결제**: 순수 화면용(mock)입니다. `mypage.jsp`에 게임 이름/가격이 하드코딩되어 있고, 결제 버튼은 `disabled`라 클릭해도 아무 요청도 발생하지 않습니다. 서버로 통신하지 않습니다.

---

## 11. 고객지원 페이지

`/support`. Steam의 고객지원 카테고리 목록과 비슷한 형태의, 클릭하면 펼쳐지는 FAQ 목록입니다.

**관련 파일**: `SupportController.java` → `UserService.java`(`getAllUsers`) → `support.jsp`

**아코디언(펼침) UI는 JS가 아니라 순수 HTML**입니다. `<details>`/`<summary>` 태그는 브라우저가 기본적으로 클릭 시 펼침/접힘을 지원하는 네이티브 위젯이라, 자바스크립트 없이도 동작합니다.

```jsp
<details>
    <summary>나의 계정<span class="support-arrow">&#9654;</span></summary>
    <div class="support-detail"> ... </div>
</details>
```

화살표가 회전하는 효과는 CSS `details[open]` 상태 선택자로 처리합니다.

```css
.support-row details[open] .support-arrow,
.support-subrow details[open] .support-arrow {
    transform: rotate(90deg);
}
```

대부분의 항목은 고정된 안내 문구지만, **"나의 계정" 항목만 로그인 상태에 따라 내용이 실제로 바뀌는 동적 영역**입니다.

- "계정 정보 관리" 하위 항목: 로그인했으면 마이페이지 링크, 안 했으면 로그인/회원가입 링크를 `<c:choose>`로 갈라서 보여줍니다.
- "MDGS 계정 관련 데이터" 하위 항목: 3단계로 갈립니다.
  1. **관리자로 로그인**: `UserService.getAllUsers()`로 가져온 전체 회원 목록을 표로 보여줍니다 (비밀번호 컬럼은 화면 노출 방지를 위해 일부러 제외).
  2. **일반 회원으로 로그인**: 자신의 `loginId`(닉네임)만 보여줍니다.
  3. **비로그인**: "로그인 후 확인할 수 있습니다" 안내만 보여줍니다.

이 판단은 서버 쪽(`SupportController`)에서 미리 끝내둡니다.

```java
// SupportController.java
@GetMapping("/support")
public String support(HttpServletRequest request, Model model) {
    HttpSession session = request.getSession(false);
    String loginId = (session != null) ? (String) session.getAttribute("loginId") : null;
    boolean isAdmin = ADMIN_ID.equals(loginId);

    model.addAttribute("isAdmin", isAdmin);
    if (isAdmin) {
        model.addAttribute("allUsers", userService.getAllUsers());   // 관리자일 때만 전체 회원 조회
    }
    return "support";
}
```

일반 회원일 땐 `getAllUsers()`가 아예 호출되지 않으므로, 불필요한 DB 조회도 막아둔 셈입니다. `support.jsp`는 `isAdmin` / `sessionScope.loginId` 두 값만 가지고 `<c:choose>`로 세 갈래를 표시합니다.

푸터 하단의 "고객센터" 링크(`footer.jsp`)도 이 페이지로 연결되어 있어, 메인 메뉴와 푸터 두 군데에서 접근할 수 있습니다.

---

## 12. 공통 헤더/푸터 (jsp:include)

모든 화면 상단의 배너/네비게이션과 하단 푸터는 각 JSP가 중복으로 작성하지 않고, 공용 조각을 include해서 씁니다.

```jsp
<jsp:include page="/WEB-INF/views/common/header.jsp" />
...
<jsp:include page="/WEB-INF/views/common/footer.jsp" />
```

`<jsp:include>`는 "런타임에 다른 페이지를 실행한 결과를 그 자리에 끼워넣는" 방식(런타임 include)입니다. 컴파일 시점에 하나로 합쳐지는 정적 include(`<%@ include %>`)와 달리, `header.jsp`/`footer.jsp`는 완전히 독립된 번역 단위로 취급됩니다. 그래서 **`header.jsp`와 `footer.jsp` 각자가 자기 파일 맨 위에 `<%@ taglib %>`와 `<c:set var="ctx" .../>`를 따로 선언**해야 합니다 — 이걸 호출하는 쪽(`gameList.jsp` 등)에서 선언해도 include된 조각에는 전달되지 않습니다.

경로가 `/WEB-INF/views/common/header.jsp`처럼 `WEB-INF` 안쪽인데도 `<jsp:include>`가 정상 동작하는 이유는, `WEB-INF`가 막는 것은 **브라우저의 직접 URL 접근**뿐이고, 서버 내부의 forward/include(같은 서블릿 컨테이너 안에서 일어나는 이동)는 `WEB-INF` 안쪽 자원도 자유롭게 접근할 수 있기 때문입니다.

`header.jsp`는 로그인 상태 표시(9번)와 검색창(4번)을, `footer.jsp`는 하단 링크 4개(이용약관/개인정보처리방침/고객센터/문의사항 — 이 중 "고객센터"만 `/support`로 실제 연결되어 있고 나머지는 자리만 잡아둔 `href="#"` 상태) 담당합니다.
