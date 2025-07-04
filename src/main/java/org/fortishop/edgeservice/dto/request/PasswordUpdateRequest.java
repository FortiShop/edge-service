package org.fortishop.edgeservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PasswordUpdateRequest {
    @NotBlank
    private String currentPassword;

    @Size(min = 6, message = "비밀번호는 최소 6자 이상이어야 합니다.")
    @NotBlank
    private String newPassword;
}

