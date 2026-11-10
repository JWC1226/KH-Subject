<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>MDGS - <c:out value="${empty game ? '게임 정보' : game.title}" /></title>

<script src="https://kit.fontawesome.com/3dd9964fc0.js"></script>
<link rel="stylesheet" href="${ctx}/resources/css/mdgs.css">
</head>

<body>
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<main class="container">
    <c:choose>
        <c:when test="${empty game}">
            <section class="gamedetail-section">
                <p>존재하지 않는 게임입니다.</p>
                <a href="${ctx}/" class="gamedetail-back">목록으로 돌아가기</a>
            </section>
        </c:when>
        <c:otherwise>
            <section class="gamedetail-section">
                <div class="gamedetail-image">
                    <img src="${ctx}/${game.imagePath}" alt="${game.title} 이미지">
                </div>
                <div class="gamedetail-info">
                    <h2>${game.title}</h2>
                    <p class="gamedetail-genre">${game.genre}</p>
                    <p class="gamedetail-price">$<fmt:formatNumber value="${game.price}" minFractionDigits="2" maxFractionDigits="2" /></p>
                    <!-- minFractionDigits = 소수점 아래 "n"자리까지 체크 -->
                    <ul class="gamedetail-meta">
                        <li><strong>출시일</strong> <fmt:formatDate value="${game.releaseDate}" pattern="yyyy-MM-dd" /></li>
                        <li><strong>개발사</strong> ${game.developer}</li>
                    </ul>
                    <a href="${ctx}/" class="gamedetail-back">목록으로 돌아가기</a>
                </div>
            </section>
        </c:otherwise>
    </c:choose>
</main>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
</body>
</html>
