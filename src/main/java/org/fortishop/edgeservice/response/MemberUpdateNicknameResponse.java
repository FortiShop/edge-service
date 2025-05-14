package org.fortishop.edgeservice.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.fortishop.edgeservice.domain.Member;

@Getter
@AllArgsConstructor
public class MemberUpdateNicknameResponse {
    private String email;
    private String nickname;

    public static MemberUpdateNicknameResponse of(Member member) {
        return new MemberUpdateNicknameResponse(member.getEmail(), member.getNickname());
    }
}
