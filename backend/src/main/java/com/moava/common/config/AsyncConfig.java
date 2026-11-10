package com.moava.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * @Async 사용 설정 (알림 SMS 리스너 등).
 * 스레드 풀 크기는 application.properties 의 spring.task.execution.pool.* 값을 따른다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}