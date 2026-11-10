package edu.secure.assign.web;

import edu.secure.assign.domain.Customer;
import edu.secure.assign.domain.SignupRequest;
import edu.secure.assign.mapper.CustomerMapper;
import edu.secure.assign.security.AesCrypto;
import edu.secure.assign.security.PasswordHasher;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final CustomerMapper customerMapper;
    private final AesCrypto crypto;

    public AuthController(CustomerMapper customerMapper, AesCrypto crypto) {
        this.customerMapper = customerMapper;
        this.crypto = crypto;
    }

    @PostMapping("/signup")
    public Map<String, String> signup(@Valid @RequestBody SignupRequest req) {
        // @Valid 통과 후라 customerId 는 영문/숫자뿐 (로그 위조 불가)
        log.info("가입 요청: customerId={}", req.customerId());
        // 주민번호는 고객 ID 를 AAD 로 묶어 암호화, 비밀번호는 해시로 저장
        Customer c = new Customer(req.customerId(), req.customerName(),
                crypto.encrypt(req.rrn(), req.customerId()), req.phone(), PasswordHasher.hash(req.password()));
        customerMapper.insert(c);
        return Map.of("customerId", req.customerId());
    }
}
