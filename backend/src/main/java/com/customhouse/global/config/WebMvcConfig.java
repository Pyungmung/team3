package com.customhouse.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * [담당: 송귀성] 매물 등록 사진 업로드 - ListingPhotoController가 upload.dir(로컬 디스크)에 저장한
 * 파일을 /uploads/** 로 정적 서빙한다. SecurityConfig의 anyRequest().permitAll()에 자연히 걸려
 * 별도 인증 규칙 추가 없이 공개된다(이미지는 누구나 볼 수 있어야 카드에 뜬다).
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${upload.dir}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = uploadDir.endsWith("/") ? uploadDir : uploadDir + "/";
        registry.addResourceHandler("/uploads/**").addResourceLocations("file:" + location);
    }
}
