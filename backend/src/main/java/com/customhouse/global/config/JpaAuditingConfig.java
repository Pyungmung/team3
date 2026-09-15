package com.customhouse.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * [담당: 허겸] 공통 인프라 - BaseTimeEntity의 @CreatedDate/@LastModifiedDate 자동 채움 활성화.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
