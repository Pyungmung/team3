package com.customhouse.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * [담당: 허겸] 공통 인프라 - @Scheduled 사용 활성화 (payment/scheduler/SubscriptionScheduler 등에서 사용)
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
