<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<header class="top-banner">
    <div class="container banner-inner">
        <a class="logo" id="banner-logo" href="${ctx}/">
            <img src="${ctx}/resources/images/MDGS_Logo.png" alt="게임 홈페이지 로고">
            <span>Modern Dark</span>
        </a>

        <form class="search-box" id="banner-search" action="${ctx}/" method="get">
            <input type="text" name="q" placeholder="게임을 검색하세요.." value="${param.q}">
            <button class="btn" type="submit">
                <i class="fa-solid fa-magnifying-glass"></i>
            </button>
        </form>

        <div class="user-actions">
            <c:if test="${not empty sessionScope.loginId}">
                <a href="#" class="cart" id="banner-cart">장바구니</a>
            </c:if>
            <c:choose>
                <c:when test="${not empty sessionScope.loginId}">
                    <span class="welcome-msg">${sessionScope.loginId}님</span>
                    <a href="${ctx}/mypage" class="mypage-link" id="banner-mypage">내 페이지</a>
                </c:when>
                <c:otherwise>
                    <a href="${ctx}/login" class="login" id="banner-login">로그인</a>
                    <a href="${ctx}/register" class="signup" id="banner-signup">회원가입</a>
                </c:otherwise>
            </c:choose>
        </div>
    </div>
</header>

<nav class="main-menu" id="menu">
    <div class="container">
        <ul>
            <li>
                <a href="#">스토어</a>
                <ul class="sub-menu">
                    <li><a href="#">추천 게임</a></li>
                    <li><a href="#">신규 게임</a></li>
                    <li><a href="#">할인 게임</a></li>
                </ul>
            </li>
            <li>
                <a href="#">카테고리</a>
                <ul class="sub-menu">
                    <li><a href="#">액션 로그라이크</a></li>
                    <li><a href="#">FPS</a></li>
                    <li><a href="#">풍부한 스토리</a></li>
                    <li><a href="#">핵 앤 슬래시</a></li>
                </ul>
            </li>
            <li><a href="#">커뮤니티</a></li>
            <li><a href="${ctx}/support">고객지원</a></li>
        </ul>
    </div>
</nav>
