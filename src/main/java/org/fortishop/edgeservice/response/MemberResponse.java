package org.fortishop.edgeservice.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.fortishop.edgeservice.domain.Member;

@Getter
@AllArgsConstructor
public class MemberResponse {
    private Long id;
    private String email;
    private String nickname;
    private String role;

    public static MemberResponse of(Member member) {
        return new MemberResponse(member.getId(), member.getEmail(), member.getNickname(), member.getRole().toString());
    }
}
