package edu.secure.assign.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** 모든 응답에 브라우저 보안 헤더를 붙인다. 거부 응답에도 붙도록 필터 중 가장 먼저 실행한다. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    /** CSP: 같은 출처 리소스만 허용하고 인라인 스크립트·외부 폼 제출·iframe 삽입을 막는다(XSS 2차 방어). */
    static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self'",
            "img-src 'self'",
            "object-src 'none'",
            "base-uri 'none'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest req, @NonNull HttpServletResponse res,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {
        res.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        res.setHeader("X-Frame-Options", "DENY");                 // 클릭재킹 방지(구형 브라우저용)
        res.setHeader("X-Content-Type-Options", "nosniff");       // 응답을 스크립트로 오인하는 것 방지
        res.setHeader("Referrer-Policy", "no-referrer");          // URL(검색어 등) 외부 유출 방지
        res.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()");   // 기기 기능 사용 금지
        res.setHeader("Cross-Origin-Opener-Policy", "same-origin");     // 다른 사이트 창과 분리
        res.setHeader("Cross-Origin-Resource-Policy", "same-origin");   // 다른 사이트가 응답을 가져다 쓰지 못함
        res.setHeader("Cache-Control", "no-store");               // 개인·금융정보 캐시 금지
        res.setHeader("Pragma", "no-cache");
        chain.doFilter(req, res);
    }
}
