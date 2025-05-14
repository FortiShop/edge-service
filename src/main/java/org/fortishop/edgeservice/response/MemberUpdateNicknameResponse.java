package org.fortishop.edgeservice.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.fortishop.edgeservice.domain.Member;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED, force = true)
@AllArgsConstructor
public class MemberUpdateNicknameResponse {
    private String email;
    private String nickname;

    public static MemberUpdateNicknameResponse of(Member member) {
        return new MemberUpdateNicknameResponse(member.getEmail(), member.getNickname());
    }
}
