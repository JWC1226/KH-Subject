package edu.secure.assign.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/** 공통 예외 처리: 응답에는 코드·공통 메시지·errorId 만 주고, 예외 상세는 서버 로그에만 남긴다. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 400: 잘못된 값·형식, 필수 파라미터 누락, 본문 파싱 실패 */
    @ExceptionHandler({
            IllegalArgumentException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<ApiError> badRequest(Exception e) {
        return clientError(HttpStatus.BAD_REQUEST, e);
    }

    /** 400: @Valid 검증 실패. 로그에는 틀린 필드 이름만 남기고 입력값은 남기지 않는다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> invalid(MethodArgumentNotValidException e) {
        String errorId = newErrorId();
        log.warn("errorId={} status=400 type=MethodArgumentNotValidException fields={}", errorId,
                e.getBindingResult().getFieldErrors().stream().map(f -> f.getField()).distinct().toList());
        return body(HttpStatus.BAD_REQUEST, errorId);
    }

    /** 컨트롤러가 직접 던진 401/403/404 등: 상태 코드 유지 */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> status(ResponseStatusException e) {
        return statusOf(e.getStatusCode(), e);
    }

    /** 409: 중복 가입(PK 중복) */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiError> conflict(DuplicateKeyException e) {
        return clientError(HttpStatus.CONFLICT, e);
    }

    /** 500: 그 외 모든 예외. 단, 스프링 표준 예외(405, 415 등)는 원래 상태 코드 유지 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception e) {
        if (e instanceof ErrorResponse er) {
            return statusOf(er.getStatusCode(), e);
        }
        String errorId = newErrorId();
        log.error("errorId={}", errorId, e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, errorId);
    }

    // ------------------------------------------------------------------

    private ResponseEntity<ApiError> statusOf(HttpStatusCode code, Exception e) {
        HttpStatus status = HttpStatus.resolve(code.value());
        if (status == null || status.is5xxServerError()) {
            String errorId = newErrorId();
            log.error("errorId={}", errorId, e);
            return body(status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status, errorId);
        }
        return clientError(status, e);
    }

    /** 4xx 는 스택 트레이스·예외 메시지 없이 예외 종류만 로그에 남긴다(메시지에 사용자 입력이 섞일 수 있음). */
    private ResponseEntity<ApiError> clientError(HttpStatus status, Exception e) {
        String errorId = newErrorId();
        log.warn("errorId={} status={} type={}", errorId, status.value(), e.getClass().getSimpleName());
        return body(status, errorId);
    }

    private static ResponseEntity<ApiError> body(HttpStatus status, String errorId) {
        return ResponseEntity.status(status).body(new ApiError(status.name(), messageOf(status), errorId));
    }

    /** 상태별 규격화된 공통 메시지. 필터(FilterErrors)도 같은 메시지를 쓴다. */
    static String messageOf(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "요청 값이 올바르지 않습니다.";
            case UNAUTHORIZED -> "로그인이 필요합니다.";
            case FORBIDDEN -> "접근 권한이 없습니다.";
            case NOT_FOUND -> "요청한 정보를 찾을 수 없습니다.";
            case METHOD_NOT_ALLOWED -> "지원하지 않는 요청 방식입니다.";
            case CONFLICT -> "이미 등록된 정보입니다.";
            case PAYLOAD_TOO_LARGE -> "요청 크기가 허용 범위를 넘었습니다.";
            case TOO_MANY_REQUESTS -> "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.";
            default -> status.is4xxClientError()
                    ? "요청을 처리할 수 없습니다."
                    : "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.";
        };
    }

    private static String newErrorId() {
        return UUID.randomUUID().toString();
    }
}
