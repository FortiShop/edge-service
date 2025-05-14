package org.fortishop.edgeservice.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class MemberUpdateNicknameRequest {
    @Size(min = 2, message = "닉네임은 최소 2자 이상이어야 합니다.")
    @NotBlank
    private String nickname;
}
