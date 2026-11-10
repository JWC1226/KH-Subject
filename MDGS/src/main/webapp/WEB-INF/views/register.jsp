<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>MDGS - 회원가입</title>

<script src="https://kit.fontawesome.com/3dd9964fc0.js"></script>
<link rel="stylesheet" href="${ctx}/resources/css/mdgs.css">
</head>

<body>
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<main class="container">
    <section class="auth-form-section">
        <h2>회원가입</h2>

        <c:if test="${not empty errorMsg}">
            <div class="notice-urgent">${errorMsg}</div>
        </c:if>

        <form action="${ctx}/register" method="post">
            <div class="form-row">
                <label for="id">아이디</label>
                <input type="text" id="id" name="id" required>
            </div>
            <div class="form-row">
                <label for="password">비밀번호</label>
                <input type="password" id="password" name="password" required>
            </div>
            <div class="form-row">
                <label for="email">이메일</label>
                <input type="email" id="email" name="email" required>
            </div>
            <div class="form-row">
                <label for="age">나이</label>
                <input type="number" id="age" name="age" min="1" max="150" required>
            </div>
            <div class="form-row">
                <label for="address">주소</label>
                <input type="text" id="address" name="address">
            </div>
            <div class="form-row">
                <label for="cardNum">카드번호</label>
                <input type="text" id="cardNum" name="cardNum">
            </div>
            <button type="submit">회원가입</button>
        </form>

        <p class="form-footer-link">이미 계정이 있으신가요? <a href="${ctx}/login">로그인</a></p>
    </section>
</main>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
</body>
</html>
