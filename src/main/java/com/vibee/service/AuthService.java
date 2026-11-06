package com.vibee.service;

import com.vibee.config.JwtProperties;
import com.vibee.dto.*;
import com.vibee.entity.RefreshToken;
import com.vibee.entity.User;
import com.vibee.enums.Role;
import com.vibee.enums.UserStatus;
import com.vibee.exception.ApiException;
import com.vibee.repository.RefreshTokenRepository;
import com.vibee.repository.UserRepository;
import com.vibee.security.JwtService;
import com.vibee.security.TokenUtils;
import com.vibee.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        Role role = req.role() == null ? Role.ATTENDEE : req.role();
        // STAFF và ADMIN không được tự đăng ký, chỉ ADMIN tạo qua /admin/users
        if (role != Role.ATTENDEE && role != Role.ORGANIZER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ROLE_NOT_ALLOWED",
                    "Only ATTENDEE or ORGANIZER accounts can self-register");
        }
        User user = userService.createUser(req.email(), req.password(), req.fullName(), req.phone(), role);
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        // Ném BadCredentialsException (sai email/mật khẩu) hoặc LockedException (tài khoản bị khóa)
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.email(), req.password()));
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        User user = userRepository.findById(principal.getId()).orElseThrow();
        return issueTokens(user);
    }

    /**
     * Refresh token rotation: mỗi lần refresh sẽ thu hồi token cũ và cấp token mới.
     * Nếu token đã bị thu hồi mà vẫn được đem ra dùng, coi như bị đánh cắp: thu hồi toàn bộ phiên của user.
     * noRollbackFor: việc thu hồi hàng loạt phải được commit dù sau đó ném lỗi 401.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse refresh(String rawRefreshToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(TokenUtils.sha256(rawRefreshToken))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN",
                        "Refresh token is invalid"));

        User user = stored.getUser();

        if (stored.isRevoked()) {
            throw reuseDetected(user.getId());
        }
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_EXPIRED",
                    "Refresh token has expired, please log in again");
        }
        if (user.getStatus() == UserStatus.LOCKED) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_LOCKED", "This account has been locked");
        }
        // Hai request cùng lúc với cùng một token: UPDATE có điều kiện đảm bảo chỉ một request thắng
        if (refreshTokenRepository.revokeIfActive(stored.getId()) == 0) {
            throw reuseDetected(user.getId());
        }
        return issueTokens(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        // Idempotent: token không tồn tại thì coi như đã đăng xuất
        refreshTokenRepository.findByTokenHash(TokenUtils.sha256(rawRefreshToken))
                .ifPresent(t -> t.setRevoked(true));
    }

    @Transactional
    public void logoutAll(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
    }

    private ApiException reuseDetected(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
        return new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_REUSED",
                "Refresh token was already used. All sessions have been revoked, please log in again");
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole());

        String rawRefresh = TokenUtils.generateOpaqueToken();
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(TokenUtils.sha256(rawRefresh));   // DB chỉ lưu hash, không lưu token gốc
        token.setExpiresAt(Instant.now().plus(jwtProperties.refreshTtl()));
        refreshTokenRepository.save(token);

        return new AuthResponse(accessToken, rawRefresh, "Bearer",
                jwtProperties.accessTtl().toSeconds(), UserResponse.from(user));
    }
}
