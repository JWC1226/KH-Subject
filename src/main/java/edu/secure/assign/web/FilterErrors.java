package edu.secure.assign.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** 필터에서 요청을 거부할 때 GlobalExceptionHandler 와 같은 형식의 JSON 오류 응답을 직접 쓰는 헬퍼 */
final class FilterErrors {

    private static final Logger log = LoggerFactory.getLogger(FilterErrors.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private FilterErrors() {
    }

    /** reason 은 로그용 거부 사유 코드(상수만 사용, 사용자 입력 금지) */
    static void reject(HttpServletResponse res, HttpStatus status, String reason) throws IOException {
        String errorId = UUID.randomUUID().toString();
        log.warn("errorId={} status={} type={}", errorId, status.value(), reason);
        byte[] body = JSON.writeValueAsBytes(
                new ApiError(status.name(), GlobalExceptionHandler.messageOf(status), errorId));
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.setContentLength(body.length);
        res.getOutputStream().write(body);
    }
}
