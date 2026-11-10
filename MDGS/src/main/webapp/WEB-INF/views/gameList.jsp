<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Modern Dark Game Store</title>

<script src="https://kit.fontawesome.com/3dd9964fc0.js"></script>
<link rel="stylesheet" href="${ctx}/resources/css/mdgs.css">
</head>

<body>
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<main class="container">
    <section class="hero-slider" id="heroSlider">
        <div class="slider-side prev" id="hidari">
            <span> &larr; </span>
        </div>
        <div class="slider-viewport">
            <div class="slider-track" id="sliderTrack">
                <div class="slide">
                    <img src="${ctx}/resources/images/MDGS_Theme.png" alt="메인 광고판" class="main-banner-img">
                </div>
                <c:forEach var="game" items="${gameList}">
                    <div class="slide">
                        <img src="${ctx}/${game.imagePath}" alt="${game.title} 배너 이미지" class="main-banner-img">
                    </div>
                </c:forEach>
            </div>
        </div>
        <div class="slider-side next" id="migi">
            <span> &rarr; </span>
        </div>
    </section>

    <section class="notice-section">
        <div class="notice-urgent" id="important-notice">
            【긴급】서버 점검 안내：02:00 ~ 04:00 게임 접속 불가</div>
        <div class="notice-general" id="normal-notice">
            【이벤트】여름맞이 대규모 할인 이벤트가 시작되었습니다!</div>
    </section>

    <section class="game-content-section">
        <div class="game-list-area">
            <c:if test="${empty gameList}">
                <c:choose>
                    <c:when test="${not empty param.q}">
                        <p>"${param.q}"에 대한 검색 결과가 없습니다.</p>
                    </c:when>
                    <c:otherwise>
                        <p>등록된 게임이 없습니다.</p>
                    </c:otherwise>
                </c:choose>
            </c:if>
            <c:forEach var="game" items="${gameList}">
                <div class="game-column">
                    <div class="game-category-title">${game.genre}</div>
                    <a href="${ctx}/gameDetail?id=${game.gameId}" class="game-card" data-image="${ctx}/${game.imagePath}">
                        <img src="${ctx}/${game.imagePath}" alt="${game.title} 대표 이미지" width="250">
                    </a>
                    <div class="game-title">${game.title}</div>
                </div>
            </c:forEach>
        </div>

        <aside class="game-preview-area" id="gamePreviewArea">
            <div class="preview-content">
                <p>게임에 마우스를 올려보세요</p>
            </div>
        </aside>
    </section>
</main>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
<script src="${ctx}/resources/newFile.js"></script>
</body>
</html>
