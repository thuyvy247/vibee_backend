package com.vibee.security;

import com.vibee.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Cố tình KHÔNG gắn @Component: nếu là bean, Spring Boot sẽ tự đăng ký thêm vào servlet container
 * ngoài Security filter chain. Filter này được tạo trực tiếp trong SecurityConfig.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";
    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith(BEARER)) {
            try {
                Claims claims = jwtService.parse(header.substring(BEARER.length()));
                UserPrincipal principal = UserPrincipal.fromToken(
                        Long.valueOf(claims.getSubject()),
                        claims.get("email", String.class),
                        Role.valueOf(claims.get("role", String.class)));

                var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException | IllegalArgumentException e) {
                // Token lỗi/hết hạn: không đặt Authentication. Request vào route cần đăng nhập sẽ nhận 401
                // từ AuthenticationEntryPoint; route public vẫn đi bình thường.
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
