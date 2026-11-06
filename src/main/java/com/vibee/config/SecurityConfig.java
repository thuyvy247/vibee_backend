package com.vibee.config;

import com.vibee.security.JwtAuthenticationFilter;
import com.vibee.security.JwtService;
import com.vibee.security.RestSecurityHandlers;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // bật @PreAuthorize
@RequiredArgsConstructor
public class SecurityConfig {

    private final RestSecurityHandlers securityHandlers;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)          // API stateless dùng Bearer token, không dùng cookie session
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(securityHandlers)
                        .accessDeniedHandler(securityHandlers))
                .authorizeHttpRequests(auth -> auth
                        // Public
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/register", "/api/v1/auth/login",
                                "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/**").permitAll()
                        .requestMatchers("/api/v1/payments/vnpay/ipn", "/api/v1/payments/vnpay/return").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        // Theo role
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/organizer/**").hasRole("ORGANIZER")
                        .requestMatchers("/api/v1/staff/**").hasAnyRole("STAFF", "ADMIN")
                        // Còn lại: chỉ cần đăng nhập
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // @Bean
    // CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") String[] origins) {
    //     CorsConfiguration cfg = new CorsConfiguration();
    //     cfg.setAllowedOrigins(List.of(origins));
    //     cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    //     cfg.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    //     cfg.setMaxAge(3600L);
    //     UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    //     source.registerCorsConfiguration("/**", cfg);
    //     return source;
    // }
    
    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") String[] origins) {

        CorsConfiguration cfg = new CorsConfiguration();

        cfg.setAllowedOrigins(List.of(origins));

        cfg.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "PATCH",
                "DELETE", "OPTIONS"
        ));

        cfg.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type"
        ));

        cfg.setAllowCredentials(false);
        cfg.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", cfg);

        return source;
    }

}
