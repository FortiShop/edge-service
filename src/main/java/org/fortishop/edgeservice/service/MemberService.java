package org.fortishop.edgeservice.service;

import org.fortishop.edgeservice.auth.PrincipalDetails;
import org.fortishop.edgeservice.domain.Role;
import org.fortishop.edgeservice.request.MemberUpdateNicknameRequest;
import org.fortishop.edgeservice.request.PasswordUpdateRequest;
import org.fortishop.edgeservice.request.SignupRequest;
import org.fortishop.edgeservice.response.MemberPageResponse;
import org.fortishop.edgeservice.response.MemberResponse;
import org.fortishop.edgeservice.response.MemberUpdateNicknameResponse;

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
