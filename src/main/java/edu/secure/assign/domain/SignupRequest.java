package edu.secure.assign.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 회원가입 요청 본문. 필드별 입력 규칙의 근거는 REPORT.md [5] 표 참고. */
public record SignupRequest(

        /* 영문/숫자 4~10자: SQL·스크립트 특수문자 차단, 컬럼 길이(10) 초과 방지 */
        @NotBlank(message = "고객 ID는 필수입니다.")
        @Pattern(regexp = "^[A-Za-z0-9]{4,10}$", message = "고객 ID는 영문/숫자 4~10자입니다.")
        String customerId,

        /* 한글/영문 2~16자(단어 사이 공백 1칸 허용): 태그·스크립트 문자 차단, 컬럼 50바이트 이내 */
        @NotBlank(message = "이름은 필수입니다.")
        @Size(min = 2, max = 16, message = "이름은 2~16자입니다.")
        @Pattern(regexp = "^[가-힣A-Za-z]+( [가-힣A-Za-z]+)*$", message = "이름은 한글/영문만 사용할 수 있습니다.")
        String customerName,

        /* YYMMDD-성별자리(1~4)+6자리: 형식과 생년월일 범위 강제 */
        @NotBlank(message = "주민등록번호는 필수입니다.")
        @Pattern(regexp = "^\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])-[1-4]\\d{6}$",
                message = "주민등록번호 형식이 올바르지 않습니다.")
        String rrn,

        /* 휴대전화 010/011/016/017/018/019 형식, '-' 3분할(마스킹 유틸 전제) */
        @NotBlank(message = "전화번호는 필수입니다.")
        @Pattern(regexp = "^01[016789]-\\d{3,4}-\\d{4}$", message = "휴대전화 번호 형식(010-1234-5678)이 아닙니다.")
        String phone,

        /* 8~64자, 영문·숫자·특수문자 각 1자 이상, 공백 제외 출력 가능 ASCII 만 */
        @NotNull(message = "비밀번호는 필수입니다.")
        @Size(min = 8, max = 64, message = "비밀번호는 8~64자입니다.")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!-/:-@\\[-`{-~])[!-~]+$",
                message = "비밀번호는 영문, 숫자, 특수문자를 모두 포함해야 합니다.")
        String password) {

    /** 로그에 주민번호·비밀번호가 찍히지 않도록 고객 ID 만 출력 */
    @Override
    public String toString() {
        return "SignupRequest{customerId='" + customerId + "'}";
    }
}
