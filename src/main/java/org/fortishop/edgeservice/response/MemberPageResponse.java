package org.fortishop.edgeservice.response;

import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED, force = true)
@AllArgsConstructor
public class MemberPageResponse {
    private List<MemberResponse> members;
    private int offset;
    private int limit;
    private long total;
}

