package com.pharmacy.identityservice.dto.response;

import lombok.*;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TokenResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long accessExpiresIn;
    private UUID userId;
    private String username;
    private String role;
}