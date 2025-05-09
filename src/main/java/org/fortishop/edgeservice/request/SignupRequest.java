package org.fortishop.edgeservice.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class SignupRequest {

    @Email
    @NotBlank
    private String email;

    @Size(min = 6, message = "비밀번호는 최소 6자 이상이어야 합니다.")
    @NotBlank
    private String password;

    @NotBlank
    private String nickname;
}
