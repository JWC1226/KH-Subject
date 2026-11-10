package edu.secure.assign.web;

import edu.secure.assign.service.LoanService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** 화면(JSP) 컨트롤러 */
@Controller
public class PageController {

    /** 세션에 보관하는 CSRF 토큰(Synchronizer Token) 키 */
    static final String CSRF_SESSION_KEY = "CSRF_TOKEN";
    private static final int CSRF_TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Logger audit = LoggerFactory.getLogger("AUDIT");

    private final LoanService loanService;

    public PageController(LoanService loanService) {
        this.loanService = loanService;
    }

    @GetMapping("/login-page")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/search")
    public String search(@RequestParam(defaultValue = "") String q, Model model) {
        model.addAttribute("q", q);
        return "search";
    }

    /** 상환 폼: 세션의 CSRF 토큰(없으면 새로 생성)을 hidden 필드로 내려 준다. */
    @GetMapping("/repay-form")
    public String repayForm(HttpSession session, Model model) {
        model.addAttribute("csrfToken", csrfToken(session));
        return "repay-form";
    }

    /** 상환 처리: 비로그인 401, CSRF 토큰 불일치 403 (둘 다 DB 호출 전에 검사). */
    @PostMapping("/repay-form")
    @ResponseBody
    public String repay(@RequestParam long loanId, @RequestParam long amount,
                        @RequestParam(name = "_csrf", required = false) String csrf,
                        HttpSession session) {
        String me = LoginController.requireCustomer(session);
        verifyCsrf(session, csrf, me);
        return "상환 처리: " + loanService.repay(loanId, me, amount);
    }

    // ------------------------------------------------------------------ CSRF

    /** 세션 CSRF 토큰 조회, 없으면 32바이트 난수로 생성 */
    private static String csrfToken(HttpSession session) {
        Object token = session.getAttribute(CSRF_SESSION_KEY);
        if (token instanceof String s) {
            return s;
        }
        byte[] b = new byte[CSRF_TOKEN_BYTES];
        RANDOM.nextBytes(b);
        String created = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        session.setAttribute(CSRF_SESSION_KEY, created);
        return created;
    }

    /** 토큰이 없거나 다르면 403. 비교는 MessageDigest.isEqual 로 상수 시간에 한다. */
    private static void verifyCsrf(HttpSession session, String requestToken, String customerId) {
        Object sessionToken = session.getAttribute(CSRF_SESSION_KEY);
        if (!(sessionToken instanceof String expected) || requestToken == null
                || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                                          requestToken.getBytes(StandardCharsets.UTF_8))) {
            audit.warn("CSRF_REJECTED customerId={} tokenPresent={}", customerId, requestToken != null);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }
}
