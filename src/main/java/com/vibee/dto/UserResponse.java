package com.vibee.dto;

import com.vibee.entity.User;
import com.vibee.enums.Role;
import com.vibee.enums.UserStatus;

public record UserResponse(Long id, String email, String fullName, String phone, Role role, UserStatus status) {

    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getFullName(), u.getPhone(), u.getRole(), u.getStatus());
    }
}
