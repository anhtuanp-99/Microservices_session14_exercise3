package com.pharmacy.identityservice.service;

import com.pharmacy.identityservice.dto.request.LoginRequest;
import com.pharmacy.identityservice.dto.request.RegisterRequest;
import com.pharmacy.identityservice.dto.response.TokenResponse;
import com.pharmacy.identityservice.entity.RefreshToken;
import com.pharmacy.identityservice.entity.User;
import com.pharmacy.identityservice.exception.BadCredentialsException;
import com.pharmacy.identityservice.exception.DuplicateResourceException;
import com.pharmacy.identityservice.repository.UserRepository;
import com.pharmacy.identityservice.util.JwtUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;

    @Value("${app.jwt.access-token-expiration}")
    private long accessTokenExpirationMs;

    public void register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username đã tồn tại!");
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role("ROLE_USER")
                .active(true)
                .build();

        userRepository.save(user);
        log.info("Đã đăng ký user: {}", user.getUsername());
    }

    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(BadCredentialsException::new);

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException();
        }

        if (!user.getActive()) {
            throw new BadCredentialsException();
        }

        // 1. Tạo Access Token (JWT)
        String accessToken = jwtUtils.generateAccessToken(user);

        // 2. Tạo Refresh Token (UUID + Lưu DB)
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getId());

        log.info("Login OK: {}", user.getUsername());

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .tokenType("Bearer")
                .accessExpiresIn(accessTokenExpirationMs / 1000)
                .userId(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .build();
    }

    @Transactional
    public TokenResponse refreshToken(String requestToken) {
        log.info("Bắt đầu xử lý Refresh Token Rotation");

        // 1 & 2. Tìm trong DB và kiểm tra hạn sử dụng
        // Nếu không thấy hoặc hết hạn -> verifyExpiration sẽ ném BadCredentialsException
        RefreshToken oldRefreshToken = refreshTokenService.verifyExpiration(requestToken);
        User user = oldRefreshToken.getUser();

        // 3. (QUAN TRỌNG) Xóa Refresh Token cũ khỏi DB
        refreshTokenService.deleteToken(oldRefreshToken);

        // 4. Tạo Access Token mới
        String newAccessToken = jwtUtils.generateAccessToken(user);

        // 5. Tạo Refresh Token hoàn toàn mới
        RefreshToken newRefreshToken = refreshTokenService.createRefreshToken(user.getId());

        log.info("Refresh Token Rotation thành công cho user: {}", user.getUsername());

        // 6. Trả về cặp Token mới
        return TokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken.getToken())
                .tokenType("Bearer")
                .accessExpiresIn(accessTokenExpirationMs / 1000)
                .userId(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .build();
    }
}