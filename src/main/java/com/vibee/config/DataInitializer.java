package com.vibee.config;

import com.vibee.enums.Role;
import com.vibee.repository.UserRepository;
import com.vibee.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Locale;

/** Tạo ADMIN đầu tiên từ biến môi trường ADMIN_EMAIL / ADMIN_PASSWORD (chỉ tạo khi chưa tồn tại). */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserService userService;
    private final UserRepository userRepository;

    @Value("${app.admin.email:}")
    private String adminEmail;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            return;
        }
        if (userRepository.existsByEmail(adminEmail.trim().toLowerCase(Locale.ROOT))) {
            return;
        }
        userService.createUser(adminEmail, adminPassword, "Administrator", null, Role.ADMIN);
        log.info("Created initial admin account: {}", adminEmail);
    }
}
