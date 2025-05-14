package org.fortishop.edgeservice.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MemberPageResponse {
    private List<MemberResponse> members;
    private int offset;
    private int limit;
    private long total;
}

