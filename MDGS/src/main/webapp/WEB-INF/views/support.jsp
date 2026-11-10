<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>MDGS - 고객지원</title>

<script src="https://kit.fontawesome.com/3dd9964fc0.js"></script>
<link rel="stylesheet" href="${ctx}/resources/css/mdgs.css">
</head>

<body>
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<main class="container">
    <section class="support-section">
        <h2>MDGS 고객지원</h2>

        <ul class="support-list">
            <li class="support-row">
                <details>
                    <summary>게임 및 소프트웨어 관련<span class="support-arrow">&#9654;</span></summary>
                    <div class="support-detail">어느 제품에 문제가 발생했나요? (알더라도 고쳐줄 수는 없습니다)</div>
                </details>
            </li>

            <li class="support-row">
                <details>
                    <summary>구매<span class="support-arrow">&#9654;</span></summary>
                    <div class="support-detail">결제 API를 받아오지 못했습니다</div>
                </details>
            </li>

            <li class="support-row">
                <details>
                    <summary>나의 계정<span class="support-arrow">&#9654;</span></summary>
                    <div class="support-detail">
                        <p>추가적인 도움을 위해 문제를 선택하세요</p>
                        <ul class="support-sublist">
                            <li class="support-subrow">
                                <details>
                                    <summary>계정 정보 관리<span class="support-arrow">&#9654;</span></summary>
                                    <div class="support-detail">
                                        <c:choose>
                                            <c:when test="${not empty sessionScope.loginId}">
                                                계정 정보는 <a href="${ctx}/mypage">마이페이지</a>에서 관리할 수 있습니다.
                                            </c:when>
                                            <c:otherwise>
                                                계정 정보를 관리하려면 <a href="${ctx}/login">로그인</a> 또는 <a href="${ctx}/register">회원가입</a>이 필요합니다.
                                            </c:otherwise>
                                        </c:choose>
                                    </div>
                                </details>
                            </li>
                            <li class="support-subrow">
                                <details>
                                    <summary>가족 모드<span class="support-arrow">&#9654;</span></summary>
                                    <div class="support-detail">자녀 계정의 이용 시간과 콘텐츠 등급을 관리할 수 있는 기능입니다</div>
                                </details>
                            </li>
                            <li class="support-subrow">
                                <details>
                                    <summary>MDGS Guard<span class="support-arrow">&#9654;</span></summary>
                                    <div class="support-detail">localhost, http에게 많은 것을 바라지 마십시오</div>
                                </details>
                            </li>
                            <li class="support-subrow">
                                <details>
                                    <summary>MDGS 계정 관련 데이터<span class="support-arrow">&#9654;</span></summary>
                                    <div class="support-detail">
                                        <c:choose>
                                            <c:when test="${isAdmin}">
                                                <p>관리자 계정으로 로그인되어 전체 회원 데이터를 표시합니다.</p>
                                                <table class="support-user-table">
                                                    <thead>
                                                        <tr>
                                                            <th>User_SID</th>
                                                            <th>아이디</th>
                                                            <th>이메일</th>
                                                            <th>나이</th>
                                                            <th>주소</th>
                                                            <th>카드번호</th>
                                                        </tr>
                                                    </thead>
                                                    <tbody>
                                                        <c:forEach var="u" items="${allUsers}">
                                                            <tr>
                                                                <td>${u.userSid}</td>
                                                                <td>${u.id}</td>
                                                                <td>${u.email}</td>
                                                                <td>${u.age}</td>
                                                                <td>${u.address}</td>
                                                                <td>${u.cardNum}</td>
                                                            </tr>
                                                        </c:forEach>
                                                    </tbody>
                                                </table>
                                            </c:when>
                                            <c:when test="${not empty sessionScope.loginId}">
                                                이전에 사용했던 닉네임: <strong>${sessionScope.loginId}</strong>
                                            </c:when>
                                            <c:otherwise>
                                                로그인 후 확인할 수 있습니다.
                                            </c:otherwise>
                                        </c:choose>
                                    </div>
                                </details>
                            </li>
                        </ul>
                    </div>
                </details>
            </li>

            <li class="support-row">
                <details>
                    <summary>교환, 선물, 장터 및 포인트<span class="support-arrow">&#9654;</span></summary>
                    <div class="support-detail">업데이트 예정은 있을겁니다? (아마도)</div>
                </details>
            </li>

            <li class="support-row">
                <details>
                    <summary>[MDGS] 클라이언트<span class="support-arrow">&#9654;</span></summary>
                    <div class="support-detail">빈 클라이언트입니다</div>
                </details>
            </li>

            <li class="support-row">
                <details>
                    <summary>[MDGS] 커뮤니티<span class="support-arrow">&#9654;</span></summary>
                    <div class="support-detail">제작 예정에 없습니다</div>
                </details>
            </li>

            <li class="support-row">
                <details>
                    <summary>[MDGS] 하드웨어<span class="support-arrow">&#9654;</span></summary>
                    <div class="support-detail">만들면 팔아도 돼요?</div>
                </details>
            </li>

            <li class="support-row support-row-highlight">
                <details>
                    <summary>나의 MDGS 고객지원 지원 요청 보기<span class="support-arrow">&#9654;</span></summary>
                    <div class="support-detail">권한이 부족합니다</div>
                </details>
            </li>

        </ul>
    </section>
</main>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
</body>
</html>
