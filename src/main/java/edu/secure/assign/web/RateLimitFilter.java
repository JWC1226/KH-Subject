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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** 로그인/회원가입 요청 횟수 제한(IP 당 1분 20회, 초과 시 429). ID 대입·대량 가입을 늦춘다. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RateLimitFilter extends OncePerRequestFilter {

    static final long WINDOW_MILLIS = 60_000;
    /** POST 경로별 1분당 허용 횟수 */
    static final Map<String, Integer> LIMITS = Map.of("/login", 20, "/api/signup", 20);
    private static final int MAX_TRACKED_CLIENTS = 10_000;   // 이 수를 넘으면 만료된 기록 정리(메모리 보호)

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    private static final class Window {
        final long start;
        final AtomicInteger count = new AtomicInteger();

        Window(long start) {
            this.start = start;
        }
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest req) {
        return !"POST".equals(req.getMethod()) || !LIMITS.containsKey(req.getServletPath());
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest req, @NonNull HttpServletResponse res,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String path = req.getServletPath();
        long now = System.currentTimeMillis();
        // 경로+IP 별 1분 창 (위조 가능한 X-Forwarded-For 는 쓰지 않음)
        Window w = windows.compute(path + "|" + req.getRemoteAddr(),
                (k, old) -> (old == null || now - old.start >= WINDOW_MILLIS) ? new Window(now) : old);
        if (windows.size() > MAX_TRACKED_CLIENTS) {
            windows.values().removeIf(v -> now - v.start >= WINDOW_MILLIS);
        }
        if (w.count.incrementAndGet() > LIMITS.get(path)) {
            long retryAfter = Math.max(1, (WINDOW_MILLIS - (now - w.start) + 999) / 1000);
            res.setHeader("Retry-After", String.valueOf(retryAfter));
            FilterErrors.reject(res, HttpStatus.TOO_MANY_REQUESTS, "RateLimitExceeded");
            return;
        }
        chain.doFilter(req, res);
    }
}
