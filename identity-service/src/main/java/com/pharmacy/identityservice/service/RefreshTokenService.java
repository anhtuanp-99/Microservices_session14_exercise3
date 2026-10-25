package com.pharmacy.identityservice.service;

import com.pharmacy.identityservice.entity.RefreshToken;
import com.pharmacy.identityservice.entity.User;
import com.pharmacy.identityservice.exception.BadCredentialsException;
import com.pharmacy.identityservice.repository.RefreshTokenRepository;
import com.pharmacy.identityservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    @Value("${app.jwt.refresh-token-expiration}")
    private long refreshTokenExpirationMs;

    @Transactional
    public RefreshToken createRefreshToken(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String tokenValue = UUID.randomUUID().toString();
        Instant expiryDate = Instant.now().plusMillis(refreshTokenExpirationMs);

        RefreshToken refreshToken = RefreshToken.builder()
                .token(tokenValue)
                .expiryDate(expiryDate)
                .user(user)
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    public RefreshToken verifyExpiration(String tokenValue) {
        RefreshToken refreshToken = refreshTokenRepository.findByTokenWithUser(tokenValue)
                .orElseThrow(BadCredentialsException::new);

        if (refreshToken.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(refreshToken);
            log.warn("Refresh token đã hết hạn!");
            throw new BadCredentialsException();
        }

        return refreshToken;
    }

    /**
     * Xóa 1 refresh token cụ thể khỏi DB (dùng cho Token Rotation hoặc Logout)
     */
    @Transactional
    public void deleteToken(RefreshToken refreshToken) {
        refreshTokenRepository.delete(refreshToken);
        log.info("Đã xóa refresh token cũ: {}", refreshToken.getToken());
    }
}