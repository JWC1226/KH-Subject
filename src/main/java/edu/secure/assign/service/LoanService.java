package edu.secure.assign.service;

import edu.secure.assign.domain.Loan;
import edu.secure.assign.mapper.LoanMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Service
public class LoanService {

    /** 정렬 허용 목록(입력값 → 실제 컬럼명). SQL 에는 입력값이 아니라 이 상수만 들어간다. */
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "loanId", "loan_id",
            "loan_id", "loan_id",
            "balance", "balance",
            "status", "status");

    private static final Logger audit = LoggerFactory.getLogger("AUDIT");

    private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);
    private static final BigDecimal MAX_RATE = BigDecimal.ONE;
    /** 금리 문자열 길이·소수 자릿수 상한 ("1E-999999999" 같은 과부하 입력 차단) */
    private static final int MAX_RATE_LENGTH = 20;
    private static final int MAX_RATE_SCALE = 10;

    private final LoanMapper loanMapper;

    public LoanService(LoanMapper loanMapper) {
        this.loanMapper = loanMapper;
    }

    /** 정렬 기준(sortBy)으로 대출 목록 조회. 허용 목록 검증은 DB 호출 전에 수행한다. */
    public List<Loan> findSorted(String customerId, String sortBy) {
        String column = (sortBy == null) ? null : SORT_COLUMNS.get(sortBy);
        if (column == null) {
            throw new IllegalArgumentException("허용되지 않은 정렬 기준입니다.");
        }
        return loanMapper.findSorted(customerId, column);
    }

    /** 대출 상환. 조건부 UPDATE 한 문장이라 동시 요청에도 잔액이 음수가 되지 않으며, 실패 사유는 구분하지 않고 false. */
    public boolean repay(long loanId, String customerId, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("상환 금액은 0보다 커야 합니다.");
        }
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("고객 정보가 없습니다.");
        }
        boolean ok = loanMapper.repay(loanId, customerId, amount) == 1;
        // 감사 로그: 누가 / 어느 대출을 / 얼마 / 결과
        audit.info("REPAY customerId={} loanId={} amount={} result={}",
                customerId, loanId, amount, ok ? "SUCCESS" : "REJECTED");
        return ok;
    }

    /** 월 이자 = 원금 × 연이율 ÷ 12 (BigDecimal, 원 단위 반올림) */
    public long monthlyInterest(long principal, String annualRate) {
        if (principal <= 0) {
            throw new IllegalArgumentException("원금은 0보다 커야 합니다.");
        }
        BigDecimal rate = parseRate(annualRate);
        return new BigDecimal(principal)
                .multiply(rate)
                .divide(MONTHS_PER_YEAR, 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    /** 연이율 문자열 → BigDecimal. 숫자가 아니거나 0 미만 / 1(100%) 초과면 거부 */
    private static BigDecimal parseRate(String annualRate) {
        if (annualRate == null || annualRate.isBlank() || annualRate.length() > MAX_RATE_LENGTH) {
            throw new IllegalArgumentException("금리 형식이 올바르지 않습니다.");
        }
        BigDecimal rate;
        try {
            rate = new BigDecimal(annualRate.trim());   // 문자열 생성자라 0.035 를 오차 없이 표현
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("금리 형식이 올바르지 않습니다.");
        }
        if (rate.scale() > MAX_RATE_SCALE) {
            throw new IllegalArgumentException("금리 형식이 올바르지 않습니다.");
        }
        if (rate.signum() < 0 || rate.compareTo(MAX_RATE) > 0) {
            throw new IllegalArgumentException("금리는 0 이상 1 이하여야 합니다.");
        }
        return rate;
    }
}
