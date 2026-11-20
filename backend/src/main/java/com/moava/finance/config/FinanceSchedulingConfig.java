package com.moava.finance.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** @Scheduled 를 켠다 (KOSIS 월간 갱신용). 레포에 다른 @Scheduled 가 없어 다른 기능에는 영향 없음. */
@Configuration
@EnableScheduling
public class FinanceSchedulingConfig {
}
