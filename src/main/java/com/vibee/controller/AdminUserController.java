package com.vibee.controller;

import com.vibee.dto.CreateUserRequest;
import com.vibee.dto.UpdateStatusRequest;
import com.vibee.dto.UserResponse;
import com.vibee.security.UserPrincipal;
import com.vibee.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Hai lớp bảo vệ: URL rule trong SecurityConfig (/api/v1/admin/**) và @PreAuthorize ở đây.
 * Lớp thứ hai là phòng thủ chiều sâu, đề phòng ai đó sửa nhầm URL rule.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;

    /** ADMIN tạo tài khoản STAFF / ORGANIZER / ADMIN. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest req) {
        return userService.createByAdmin(req);
    }

    /** Khóa / mở khóa tài khoản. */
    @PatchMapping("/{id}/status")
    public UserResponse updateStatus(@PathVariable Long id,
                                     @Valid @RequestBody UpdateStatusRequest req,
                                     @AuthenticationPrincipal UserPrincipal admin) {
        return userService.changeStatus(admin.getId(), id, req.status());
    }
}
