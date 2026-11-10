<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="ko">
<head><meta charset="UTF-8"><title>대출 상품 검색</title></head>
<body>
<h1>대출 상품 검색</h1>
<p>검색어: <span><c:out value="${q}"/></span></p>
<p>검색어(재표시): <span><c:out value="${param.q}"/></span></p>

<p>검색 결과 요약: <span id="summary"></span></p>
<%-- 요약 표시는 textContent 를 쓰는 외부 스크립트로 처리한다 (CSP 로 인라인 스크립트 차단) --%>
<script src="<c:url value='/js/search.js'/>"></script>
</body>
</html>
