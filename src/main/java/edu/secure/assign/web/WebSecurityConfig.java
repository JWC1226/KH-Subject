package edu.secure.assign.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** /api/** 에 로그인 필수 인터셉터를 등록한다. */
@Configuration
public class WebSecurityConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(new LoginRequiredInterceptor())
                .addPathPatterns("/api/**")
                // 로그인 없이 쓰는 공개 API (이자 계산기, 회원가입)
                .excludePathPatterns("/api/interest", "/api/signup");
    }
}
