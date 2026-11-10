package edu.secure.assign.web;

import edu.secure.assign.domain.Customer;
import edu.secure.assign.domain.CustomerResponse;
import edu.secure.assign.mapper.CustomerMapper;
import edu.secure.assign.security.AesCrypto;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.regex.Pattern;

@RestController
@RequestMapping("/api")
public class CustomerController {

    private static final Logger log = LoggerFactory.getLogger(CustomerController.class);

    /** 초기 데이터의 평문 주민번호 형식. 암호문(Base64)에는 '-' 가 없어 둘을 구분할 수 있다. */
    private static final Pattern LEGACY_PLAIN_RRN = Pattern.compile("\\d{6}-\\d{7}");

    private final CustomerMapper customerMapper;
    private final AesCrypto crypto;

    public CustomerController(CustomerMapper customerMapper, AesCrypto crypto) {
        this.customerMapper = customerMapper;
        this.crypto = crypto;
    }

    @GetMapping("/customers/{customerId}")
    public CustomerResponse customer(@PathVariable String customerId, HttpSession session) {
        String me = AccessGuard.requireSelf(session, customerId);
        Customer c = customerMapper.findById(me);
        if (c == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        log.info("고객 조회: customerId={}", c.getCustomerId());
        return CustomerResponse.of(c, plainRrn(c));
    }

    /** 저장된 주민번호 → 평문(마스킹용). 실패하면 null 이라 응답에서는 전체 마스킹된다. */
    private String plainRrn(Customer c) {
        String stored = c.getRrn();
        if (stored == null) {
            return null;
        }
        if (LEGACY_PLAIN_RRN.matcher(stored).matches()) {
            return stored;   // 레거시 평문은 복호화 없이 마스킹
        }
        try {
            return crypto.decrypt(stored, c.getCustomerId());   // 가입 때 묶은 고객 ID(AAD)로 복호화
        } catch (RuntimeException e) {
            // 키 불일치·손상·바꿔치기된 암호문: 값은 남기지 않고 고객 ID 만 기록
            log.warn("주민번호 복호화 실패: customerId={}", c.getCustomerId());
            return null;
        }
    }
}
