package com.vibee.service;

import com.vibee.dto.CreateUserRequest;
import com.vibee.dto.UserResponse;
import com.vibee.entity.User;
import com.vibee.enums.Role;
import com.vibee.enums.UserStatus;
import com.vibee.exception.ApiException;
import com.vibee.repository.RefreshTokenRepository;
import com.vibee.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User createUser(String email, String rawPassword, String fullName, String phone, Role role) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(normalized)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "Email is already registered");
        }
        User user = new User();
        user.setEmail(normalized);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setFullName(fullName.trim());
        user.setPhone(phone);
        user.setRole(role);
        return userRepository.save(user);
    }

    @Transactional
    public UserResponse createByAdmin(CreateUserRequest req) {
        return UserResponse.from(createUser(req.email(), req.password(), req.fullName(), req.phone(), req.role()));
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        return userRepository.findById(id)
                .map(UserResponse::from)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
    }

    @Transactional
    public UserResponse changeStatus(Long adminId, Long userId, UserStatus status) {
        if (adminId.equals(userId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_CHANGE_SELF", "You cannot change your own status");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
        user.setStatus(status);
        if (status == UserStatus.LOCKED) {
            // Khóa tài khoản thì thu hồi luôn mọi phiên đăng nhập (refresh token)
            refreshTokenRepository.revokeAllByUserId(userId);
        }
        return UserResponse.from(user);
    }
}
