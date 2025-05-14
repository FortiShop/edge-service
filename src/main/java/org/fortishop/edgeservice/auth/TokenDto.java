package org.fortishop.edgeservice.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenDto {
    private String grantType;
    private String authorizationType;
    private String accessToken;
    private String refreshToken;
    private Long accessTokenExpiresIn;
}
