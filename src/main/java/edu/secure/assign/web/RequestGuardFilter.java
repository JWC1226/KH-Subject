package edu.secure.assign.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * POST 등 상태 변경 요청 방어: 본문 16KB 초과는 413, Origin/Referer 가 이 서버와 다르면 403(CSRF 2차 방어).
 * 두 헤더가 모두 없는 요청(curl 등 비브라우저)은 쿠키가 자동으로 실리지 않아 CSRF 대상이 아니므로 통과시킨다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class RequestGuardFilter extends OncePerRequestFilter {

    static final long MAX_BODY_BYTES = 16 * 1024;
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    /** CSRF 토큰으로 이미 보호되는 경로: 토큰 효과를 따로 확인할 수 있게 출처 검증에서 뺀다(정확히 일치할 때만). */
    private static final Set<String> TOKEN_PROTECTED_PATHS = Set.of("/repay-form");

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest req) {
        return SAFE_METHODS.contains(req.getMethod());
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest req, @NonNull HttpServletResponse res,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        if (req.getContentLengthLong() > MAX_BODY_BYTES) {   // 본문 크기 제한
            FilterErrors.reject(res, HttpStatus.PAYLOAD_TOO_LARGE, "RequestBodyTooLarge");
            return;
        }
        if (!TOKEN_PROTECTED_PATHS.contains(req.getServletPath()) && !isSameOrigin(req)) {   // 출처 검증
            FilterErrors.reject(res, HttpStatus.FORBIDDEN, "CrossOriginRequest");
            return;
        }
        chain.doFilter(req, res);
    }

    /** Origin 을 먼저, 없으면 Referer 를 서버 출처와 비교한다. 둘 다 없으면 true. */
    static boolean isSameOrigin(HttpServletRequest req) {
        String expected = asciiLower(serverOrigin(req));
        String origin = req.getHeader("Origin");
        if (origin != null) {
            return expected.equals(asciiLower(origin));   // file:// 페이지의 "null" 도 거부
        }
        String referer = req.getHeader("Referer");
        if (referer != null) {
            String r = asciiLower(referer);
            return r.equals(expected) || r.startsWith(expected + "/");
        }
        return true;
    }

    /** ASCII A~Z 만 소문자로 바꾼다. toLowerCase 는 'ſ'→'s' 같은 유니코드 변환으로 비교가 우회될 수 있다. */
    static String asciiLower(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            b.append(ch >= 'A' && ch <= 'Z' ? (char) (ch + ('a' - 'A')) : ch);
        }
        return b.substring(0);
    }

    /** 요청이 들어온 이 서버의 출처: scheme://host[:port] (기본 포트는 생략) */
    static String serverOrigin(HttpServletRequest req) {
        String scheme = req.getScheme();
        int port = req.getServerPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        return scheme + "://" + req.getServerName() + (defaultPort ? "" : ":" + port);
    }
}
