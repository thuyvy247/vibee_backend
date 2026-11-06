package com.vibee.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.vnpay")
public record VnPayProperties(String tmnCode, String hashSecret, String payUrl, String returnUrl) {}
