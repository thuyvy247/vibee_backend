package com.vibee.dto;

import com.vibee.enums.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(@NotNull UserStatus status) {}
