package com.kh.mdgs.support;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.kh.mdgs.member.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@Controller
public class SupportController {

    private static final String ADMIN_ID = "MDGS1226";

    private final UserService userService;

    public SupportController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/support")
    public String support(HttpServletRequest request, Model model) {
        HttpSession session = request.getSession(false);
        String loginId = (session != null) ? (String) session.getAttribute("loginId") : null;
        boolean isAdmin = ADMIN_ID.equals(loginId);

        model.addAttribute("isAdmin", isAdmin);
        if (isAdmin) {
            // 일반 회원일 땐 호출되지 않으므로 불필요한 DB 조회를 막아둔 것
            model.addAttribute("allUsers", userService.getAllUsers());
        }
        return "support";
    }
}
