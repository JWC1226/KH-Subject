<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<footer class="main-footer">
    <div class="container">
        <div class="footer-links" id="fl">
            <a href="#">이용약관</a>
            <span class="divider">|</span>
            <a href="#">개인정보 처리방침</a>
            <span class="divider">|</span>
            <a href="${ctx}/support">고객센터</a>
            <span class="divider">|</span>
            <a href="#">문의사항</a>
        </div>

        <div class="copyright" id="copy">
            Copyright &copy; 2026 Game Store All Right Reserved.
        </div>
    </div>
</footer>
