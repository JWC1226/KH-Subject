package edu.secure.assign.web;

import edu.secure.assign.domain.Loan;
import edu.secure.assign.mapper.LoanMapper;
import edu.secure.assign.service.LoanService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class LoanController {

    private final LoanMapper loanMapper;
    private final LoanService loanService;

    public LoanController(LoanMapper loanMapper, LoanService loanService) {
        this.loanMapper = loanMapper;
        this.loanService = loanService;
    }

    @GetMapping("/loans")
    public List<Loan> loans(@RequestParam String customerId, HttpSession session) {
        String me = AccessGuard.requireSelf(session, customerId);
        return loanMapper.findByCustomer(me);
    }

    @GetMapping("/loans/sorted")
    public List<Loan> sorted(@RequestParam String customerId, @RequestParam String sortBy, HttpSession session) {
        String me = AccessGuard.requireSelf(session, customerId);
        return loanService.findSorted(me, sortBy);
    }

    @GetMapping("/loans/{loanId}")
    public Loan loan(@PathVariable long loanId, HttpSession session) {
        String me = LoginController.requireCustomer(session);
        Loan loan = loanMapper.findById(loanId, me);
        if (loan == null) {
            // 타인 소유도 404: 다른 사람 대출의 존재 여부를 숨김
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return loan;
    }

    @PostMapping("/loans/{loanId}/repay")
    public Map<String, Object> repay(@PathVariable long loanId, @RequestParam long amount, HttpSession session) {
        String me = LoginController.requireCustomer(session);
        return Map.of("success", loanService.repay(loanId, me, amount));
    }

    @GetMapping("/interest")
    public Map<String, Object> interest(@RequestParam long principal, @RequestParam String rate) {
        return Map.of("monthlyInterest", loanService.monthlyInterest(principal, rate));
    }
}
