package edu.secure.assign.web;

/** 공통 오류 응답: 오류 코드, 규격화된 공통 메시지(예외 메시지 아님), 서버 로그 추적용 errorId */
public record ApiError(String code, String message, String errorId) {
}
