package com.kh.mdgs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// 이 어노테이션이 Spring Boot의 자동 설정, 컴포넌트 스캔 등을 활성화합니다
@SpringBootApplication
public class MdgsApplication {

    public static void main(String[] args) {
        SpringApplication.run(MdgsApplication.class, args);
    }
}
