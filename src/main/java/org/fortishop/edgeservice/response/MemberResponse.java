package org.fortishop.edgeservice.response;

import lombok.Getter;
import org.fortishop.edgeservice.domain.Member;

@Getter
public class MemberResponse {
    private final Long id;
    private final String email;
    private final String nickname;
    private final String role;

    public MemberResponse(Member member) {
        this.id = member.getId();
        this.email = member.getEmail();
        this.nickname = member.getNickname();
        this.role = member.getRole().name();
    }
}
