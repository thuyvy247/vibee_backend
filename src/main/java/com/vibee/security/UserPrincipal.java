package com.vibee.security;

import com.vibee.entity.User;
import com.vibee.enums.Role;
import com.vibee.enums.UserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String email;
    private final String passwordHash;   // null khi principal dựng từ JWT
    private final Role role;
    private final boolean locked;

    private UserPrincipal(Long id, String email, String passwordHash, Role role, boolean locked) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.locked = locked;
    }

    /** Dùng khi đăng nhập (có password hash để so khớp). */
    public static UserPrincipal from(User u) {
        return new UserPrincipal(u.getId(), u.getEmail(), u.getPasswordHash(), u.getRole(),
                u.getStatus() == UserStatus.LOCKED);
    }

    /** Dùng trong filter: dựng từ claims của JWT, không cần truy vấn DB cho mỗi request. */
    public static UserPrincipal fromToken(Long id, String email, Role role) {
        return new UserPrincipal(id, email, null, role, false);
    }

    public Long getId() { return id; }

    public Role getRole() { return role; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override public String getPassword() { return passwordHash; }

    @Override public String getUsername() { return email; }

    @Override public boolean isAccountNonLocked() { return !locked; }
}
