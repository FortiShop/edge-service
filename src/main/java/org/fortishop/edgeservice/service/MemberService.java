package org.fortishop.edgeservice.service;

import org.fortishop.edgeservice.auth.PrincipalDetails;
import org.fortishop.edgeservice.domain.Role;
import org.fortishop.edgeservice.dto.request.MemberUpdateNicknameRequest;
import org.fortishop.edgeservice.dto.request.PasswordUpdateRequest;
import org.fortishop.edgeservice.dto.request.SignupRequest;
import org.fortishop.edgeservice.dto.response.MemberPageResponse;
import org.fortishop.edgeservice.dto.response.MemberResponse;
import org.fortishop.edgeservice.dto.response.MemberUpdateNicknameResponse;

public interface MemberService {
    MemberResponse signup(SignupRequest request);

    MemberResponse getMyInfo(PrincipalDetails principal);

    MemberUpdateNicknameResponse updateNickname(PrincipalDetails principalDetails,
                                                MemberUpdateNicknameRequest request);

    void updatePassword(PrincipalDetails principal, PasswordUpdateRequest request);

    void withdraw(PrincipalDetails principal);

    void checkEmailDuplicate(String email);

    void checkNicknameDuplicate(String nickname);

    MemberPageResponse getMembers(int offset, int limit);

    void updateRole(Long memberId, Role newRole);
}
