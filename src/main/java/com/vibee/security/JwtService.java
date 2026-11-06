package com.vibee.security;

import com.vibee.config.JwtProperties;
import com.vibee.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final Duration accessTtl;

    public JwtService(JwtProperties props) {
        // Secret quá ngắn (< 256 bit) sẽ ném WeakKeyException ngay lúc khởi động
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(props.secret()));
        this.accessTtl = props.accessTtl();
    }

    public String generateAccessToken(Long userId, String email, Role role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(key)
                .compact();
    }

    /** Ném JwtException nếu token sai chữ ký, hết hạn hoặc sai định dạng. */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
