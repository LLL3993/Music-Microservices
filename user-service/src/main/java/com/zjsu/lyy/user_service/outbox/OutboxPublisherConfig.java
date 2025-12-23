package com.zjsu.lyy.user_service.outbox;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@Profile("docker")
@EnableScheduling
public class OutboxPublisherConfig {
}

