package edu.secure.assign.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 요청 대상 고객 ID 가 로그인한 본인인지 확인하는 접근 통제 헬퍼 */
final class AccessGuard {

    private AccessGuard() {
    }

    /** 비로그인 401, 다른 고객 ID 요청 403 (DB 조회 전에 판단). 통과하면 세션의 고객 ID 를 돌려준다. */
    static String requireSelf(HttpSession session, String requestedCustomerId) {
        String me = LoginController.requireCustomer(session);
        if (!me.equals(requestedCustomerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return me;
    }
}
