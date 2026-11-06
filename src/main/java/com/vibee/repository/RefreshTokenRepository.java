package com.vibee.repository;

import com.vibee.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Thu hồi nguyên tử: chỉ một request thắng khi hai request cùng dùng một refresh token.
     * Trả về 0 nghĩa là token đã bị thu hồi trước đó.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.id = :id AND r.revoked = false")
    int revokeIfActive(@Param("id") Long id);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.user.id = :userId AND r.revoked = false")
    int revokeAllByUserId(@Param("userId") Long userId);
}
