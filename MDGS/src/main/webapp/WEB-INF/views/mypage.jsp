<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>MDGS - 마이페이지</title>

<script src="https://kit.fontawesome.com/3dd9964fc0.js"></script>
<link rel="stylesheet" href="${ctx}/resources/css/mdgs.css">
</head>

<body>
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<main class="container">
    <section class="mypage-section">
        <h2>마이페이지</h2>

        <c:if test="${not empty successMsg}">
            <div class="notice-general">${successMsg}</div>
        </c:if>
        <c:if test="${not empty errorMsg}">
            <div class="notice-urgent">${errorMsg}</div>
        </c:if>

        <div class="mypage-grid">
            <div class="mypage-panel">
                <h3>계정 정보 설정</h3>
                <form action="${ctx}/mypage" method="post">
                    <input type="hidden" name="action" value="update">
                    <div class="form-row">
                        <label for="id">아이디</label>
                        <input type="text" id="id" value="${user.id}" disabled>
                    </div>
                    <div class="form-row">
                        <label for="email">이메일</label>
                        <input type="email" id="email" name="email" value="${user.email}" required>
                    </div>
                    <div class="form-row">
                        <label for="age">나이</label>
                        <input type="number" id="age" name="age" min="1" max="150" value="${user.age}" required>
                    </div>
                    <div class="form-row">
                        <label for="address">주소</label>
                        <input type="text" id="address" name="address" value="${user.address}">
                    </div>
                    <div class="form-row">
                        <label for="cardNum">카드번호</label>
                        <input type="text" id="cardNum" name="cardNum" value="${user.cardNum}">
                    </div>
                    <button type="submit">정보 저장</button>
                </form>
            </div>

            <div class="mypage-panel">
                <h3>장바구니 / 결제</h3>
                <ul class="cart-mock-list">
                    <li class="cart-mock-item">
                        <span>Hades</span>
                        <span>$24.99</span>
                    </li>
                    <li class="cart-mock-item">
                        <span>Diablo IV</span>
                        <span>$39.99</span>
                    </li>
                </ul>
                <div class="cart-mock-total">
                    <span>합계</span>
                    <span>$64.98</span>
                </div>
                <button type="button" class="cart-mock-checkout" disabled>결제하기 (준비 중)</button>
            </div>

            <div class="mypage-panel mypage-danger">
                <h3>회원 탈퇴</h3>
                <c:choose>
                    <c:when test="${isAdmin}">
                        <p>관리자 계정은 탈퇴할 수 없습니다.</p>
                    </c:when>
                    <c:otherwise>
                        <p>탈퇴 시 계정 정보가 즉시 삭제되며 복구할 수 없습니다.</p>
                        <form action="${ctx}/mypage" method="post" id="withdrawForm">
                            <input type="hidden" name="action" value="withdraw">
                            <button type="submit" class="btn-danger">회원 탈퇴</button>
                        </form>
                    </c:otherwise>
                </c:choose>
            </div>
        </div>

        <div class="mypage-footer-actions">
            <a href="${ctx}/logout" class="logout-link">로그아웃</a>
        </div>
    </section>
</main>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
<script src="${ctx}/resources/newFile.js"></script>
</body>
</html>
