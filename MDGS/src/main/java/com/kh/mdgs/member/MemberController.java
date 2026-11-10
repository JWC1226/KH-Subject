package com.kh.mdgs.member;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@Controller
public class MemberController {

    // DB에 "권한" 컬럼이 따로 없어서, 이 아이디로 로그인했는지만으로 관리자 여부를 판단 (학습용 최소 구현)
    private static final String ADMIN_ID = "MDGS1226";

    private final UserService userService;

    public MemberController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/login")
    public String loginForm() {
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String id, @RequestParam String password,
            HttpSession session, Model model) {
        String userSid = userService.login(id, password);
        if (userSid != null) {
            // 스프링 컨테이너가 관리하는 세션에 로그인 정보를 저장
            session.setAttribute("userSid", userSid);
            session.setAttribute("loginId", id);
            return "redirect:/";
        }
        model.addAttribute("errorMsg", "아이디 또는 비밀번호가 올바르지 않습니다.");
        return "login";
    }

    @GetMapping("/logout")
    public String logout(HttpServletRequest request) {
        // 로그아웃 상태에서 굳이 새 세션을 만들 필요가 없으므로 getSession(false) 사용
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return "redirect:/";
    }

    @GetMapping("/register")
    public String registerForm() {
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String id, @RequestParam String password,
            @RequestParam String email, @RequestParam String age,
            @RequestParam(required = false) String address,
            @RequestParam(required = false) String cardNum,
            Model model) {
        if (userService.isDuplicateId(id)) {
            model.addAttribute("errorMsg", "중복된 ID입니다.");
            return "register";
        }

        User newUser = new User(generateUserSid(), id, password, email,
                Integer.parseInt(age), address, cardNum);

        if (userService.register(newUser)) {
            return "redirect:/login";
        }
        model.addAttribute("errorMsg", "회원가입에 실패했습니다. 잠시 후 다시 시도해주세요.");
        return "register";
    }

    private String generateUserSid() {
        return "U_" + new SimpleDateFormat("yyyyMMddHHmmssSSS").format(new Date());
    }

    @GetMapping("/mypage")
    public String myPage(HttpServletRequest request, Model model) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userSid") == null) {
            return "redirect:/login";
        }

        String userSid = (String) session.getAttribute("userSid");
        model.addAttribute("user", userService.getUserBySid(userSid));
        model.addAttribute("isAdmin", ADMIN_ID.equals(session.getAttribute("loginId")));
        return "mypage";
    }

    // 계정 정보 수정 / 회원 탈퇴 - 같은 URL로 POST하되, 숨김 필드 action 값으로 구분
    @PostMapping("/mypage")
    public String myPageSubmit(@RequestParam String action,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String age,
            @RequestParam(required = false) String address,
            @RequestParam(required = false) String cardNum,
            HttpServletRequest request, Model model) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userSid") == null) {
            return "redirect:/login";
        }

        String userSid = (String) session.getAttribute("userSid");
        boolean isAdmin = ADMIN_ID.equals(session.getAttribute("loginId"));

        if ("withdraw".equals(action)) {
            if (isAdmin) {
                model.addAttribute("errorMsg", "관리자 계정은 탈퇴할 수 없습니다.");
                model.addAttribute("user", userService.getUserBySid(userSid));
                model.addAttribute("isAdmin", true);
                return "mypage";
            }
            if (userService.deleteUser(userSid)) {
                session.invalidate();
                return "redirect:/";
            }
            model.addAttribute("errorMsg", "회원 탈퇴에 실패했습니다. 잠시 후 다시 시도해주세요.");
            model.addAttribute("user", userService.getUserBySid(userSid));
            model.addAttribute("isAdmin", isAdmin);
            return "mypage";
        }

        boolean success = userService.updateUser(userSid, email, Integer.parseInt(age), address, cardNum);
        if (success) {
            model.addAttribute("successMsg", "계정 정보가 저장되었습니다.");
        } else {
            model.addAttribute("errorMsg", "정보 저장에 실패했습니다. 잠시 후 다시 시도해주세요.");
        }
        model.addAttribute("user", userService.getUserBySid(userSid));
        model.addAttribute("isAdmin", isAdmin);
        return "mypage";
    }
}
