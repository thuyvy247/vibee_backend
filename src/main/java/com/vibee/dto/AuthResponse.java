package com.vibee.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,      // giây
        UserResponse user
) {}
