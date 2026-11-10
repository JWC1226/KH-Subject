package edu.secure.assign.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

/** /api/** 로그인 필수 검사(기본 거부). 새 API 에서 인증 확인을 빠뜨려도 무인증 노출을 막는다. */
final class LoginRequiredInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(@NonNull HttpServletRequest req, @NonNull HttpServletResponse res,
                             @NonNull Object handler) {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute(LoginController.SESSION_KEY) == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);   // 401
        }
        return true;
    }
}
