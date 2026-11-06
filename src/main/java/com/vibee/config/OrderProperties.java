package com.vibee.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.order")
public record OrderProperties(int holdMinutes, int maxPendingPerUser) {}
