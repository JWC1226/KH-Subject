// 검색 결과 요약 표시. CSP(script-src 'self')로 인라인 스크립트를 막기 위해 search.jsp 에서 분리했다.
(function () {
    "use strict";
    var q = new URLSearchParams(location.search).get("q") || "";
    // textContent 는 문자열을 HTML 로 해석하지 않고 텍스트 노드로만 넣는다 (DOM 기반 XSS 방지)
    document.getElementById("summary").textContent = q + " 에 대한 검색 결과가 없습니다.";
})();
