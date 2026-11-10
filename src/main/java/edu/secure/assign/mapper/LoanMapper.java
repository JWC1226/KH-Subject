package edu.secure.assign.mapper;

import edu.secure.assign.domain.Loan;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface LoanMapper {

    List<Loan> findByCustomer(@Param("customerId") String customerId);

    /** sortBy 는 반드시 LoanService 허용 목록에서 매핑된 컬럼명이어야 한다 */
    List<Loan> findSorted(@Param("customerId") String customerId, @Param("sortBy") String sortBy);

    /** 소유자 조건 포함 단건 조회. 타인 소유이거나 없으면 null */
    Loan findById(@Param("loanId") long loanId, @Param("customerId") String customerId);

    /** 본인 소유이고 잔액이 충분할 때만 차감하는 조건부 UPDATE. 반환값 1 = 성공, 0 = 실패 */
    int repay(@Param("loanId") long loanId, @Param("customerId") String customerId, @Param("amount") long amount);
}
