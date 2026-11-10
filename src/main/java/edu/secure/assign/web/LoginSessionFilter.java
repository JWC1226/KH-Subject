package edu.secure.assign.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 로그인(POST /login) 보호 필터: 세션 ID 재발급(세션 고정 방지)과 CSRF 토큰 폐기, 로그인 감사 로그.
 * LoginController 를 수정하지 않으려고 필터로 앞뒤를 감쌌다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 40)
public class LoginSessionFilter extends OncePerRequestFilter {

    private static final Logger audit = LoggerFactory.getLogger("AUDIT");

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest req) {
        return !"POST".equals(req.getMethod()) || !"/login".equals(req.getServletPath());
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest req, @NonNull HttpServletResponse res,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        // 로그인 전: 기존 세션 ID 교체 + CSRF 토큰 폐기
        HttpSession existing = req.getSession(false);
        if (existing != null) {
            req.changeSessionId();
            existing.removeAttribute(PageController.CSRF_SESSION_KEY);
        }

        chain.doFilter(req, res);   // LoginController 실행

        // 로그인 후: 성공/실패 감사 로그
        HttpSession session = req.getSession(false);
        Object customerId = (session == null) ? null : session.getAttribute(LoginController.SESSION_KEY);
        if (res.getStatus() == HttpServletResponse.SC_OK && customerId != null) {
            audit.info("LOGIN_SUCCESS customerId={} sessionRotated={}", customerId, existing != null);
        } else {
            audit.warn("LOGIN_FAILURE status={} requestedId={}", res.getStatus(), sanitize(req.getParameter("customerId")));
        }
    }

    /** 로그 위조 방지: 영문/숫자 외 문자는 '_' 로 바꾸고 20자로 자른다. */
    static String sanitize(String value) {
        if (value == null) {
            return "-";
        }
        String s = value.replaceAll("[^A-Za-z0-9]", "_");
        return s.length() > 20 ? s.substring(0, 20) + "..." : s;
    }
}
