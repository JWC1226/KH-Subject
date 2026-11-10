package edu.secure.assign.domain;

import edu.secure.assign.security.MaskingUtil;

/** 고객 조회 응답 전용 DTO: 비밀번호 해시는 없고, 주민번호·전화번호는 마스킹된 값만 담는다. */
public record CustomerResponse(
        String customerId,
        String customerName,
        String rrn,
        String phone) {

    /** DB 원본 → 응답 변환. plainRrn 이 null 이면(복호화 실패) 주민번호를 전체 마스킹한다. */
    public static CustomerResponse of(Customer c, String plainRrn) {
        return new CustomerResponse(
                c.getCustomerId(),
                c.getCustomerName(),
                MaskingUtil.maskRrn(plainRrn),
                MaskingUtil.maskPhone(c.getPhone()));
    }
}
