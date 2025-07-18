package org.fortishop.edgeservice.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.fortishop.edgeservice.auth.PrincipalDetails;
import org.fortishop.edgeservice.domain.Member;
import org.fortishop.edgeservice.domain.MemberPoint;
import org.fortishop.edgeservice.domain.Role;
import org.fortishop.edgeservice.dto.request.MemberUpdateNicknameRequest;
import org.fortishop.edgeservice.dto.request.PasswordUpdateRequest;
import org.fortishop.edgeservice.dto.request.SignupRequest;
import org.fortishop.edgeservice.dto.response.MemberPageResponse;
import org.fortishop.edgeservice.dto.response.MemberResponse;
import org.fortishop.edgeservice.dto.response.MemberUpdateNicknameResponse;
import org.fortishop.edgeservice.exception.Member.MemberException;
import org.fortishop.edgeservice.exception.Member.MemberExceptionType;
import org.fortishop.edgeservice.repository.MemberPointRepository;
import org.fortishop.edgeservice.repository.MemberRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {

    private final MemberRepository memberRepository;
    private final MemberPointRepository memberPointRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public MemberResponse signup(SignupRequest signupRequest) {
        Optional<Member> deletedMemberOpt = memberRepository.findByEmailAndDeletedTrue(signupRequest.getEmail());

        if (deletedMemberOpt.isPresent()) {
            Member deletedMember = deletedMemberOpt.get();
            deletedMember.restore(
                    passwordEncoder.encode(signupRequest.getPassword()),
                    signupRequest.getNickname()
            );

            memberPointRepository.findByMember(deletedMember)
                    .ifPresentOrElse(
                            MemberPoint::resetToZero,
                            () -> memberPointRepository.save(new MemberPoint(deletedMember))
                    );

            return MemberResponse.of(deletedMember);
        }

        validateDuplicate(signupRequest.getEmail(), signupRequest.getNickname());

        Member member = Member.builder()
                .email(signupRequest.getEmail())
                .password(passwordEncoder.encode(signupRequest.getPassword()))
                .nickname(signupRequest.getNickname())
                .role(Role.ROLE_USER)
                .createdAt(LocalDateTime.now())
                .deleted(false)
                .build();

        memberRepository.save(member);
        memberPointRepository.save(new MemberPoint(member));

        return MemberResponse.of(member);
    }

    @Override
    public MemberResponse getMyInfo(PrincipalDetails principal) {
        Member member = getMember(principal.getUsername());
        return MemberResponse.of(member);
    }

    @Override
    @Transactional
    public MemberUpdateNicknameResponse updateNickname(PrincipalDetails principalDetails,
                                                       MemberUpdateNicknameRequest request) {
        Member member = getMember(principalDetails.getUsername());

        if (memberRepository.existsByNickname(request.getNickname())) {
            throw new MemberException(MemberExceptionType.ALREADY_EXIST_NICKNAME);
        }

        member.updateNickname(request.getNickname());

        return MemberUpdateNicknameResponse.of(member);
    }

    @Override
    @Transactional
    public void updatePassword(PrincipalDetails principal, PasswordUpdateRequest request) {
        Member member = getMember(principal.getUsername());

        if (!passwordEncoder.matches(request.getCurrentPassword(), member.getPassword())) {
            throw new MemberException(MemberExceptionType.INVALID_PASSWORD);
        }

        member.updatePassword(passwordEncoder.encode(request.getNewPassword()));
    }

    @Override
    @Transactional
    public void withdraw(PrincipalDetails principal) {
        Member member = getMember(principal.getUsername());
        member.markDeleted();
    }

    @Override
    public void checkEmailDuplicate(String email) {
        if (memberRepository.existsByEmail(email)) {
            throw new MemberException(MemberExceptionType.ALREADY_EXIST_EMAIL);
        }
    }

    @Override
    public void checkNicknameDuplicate(String nickname) {
        if (memberRepository.existsByNickname(nickname)) {
            throw new MemberException(MemberExceptionType.ALREADY_EXIST_NICKNAME);
        }
    }

    /**
     * 전체 회원 목록 조회 (관리자용)
     */
    @Override
    public MemberPageResponse getMembers(int offset, int limit) {
        List<Member> members = memberRepository.findMembersWithPaging(offset, limit);
        long total = memberRepository.count();

        List<MemberResponse> content = members.stream()
                .map(MemberResponse::of)
                .toList();

        return new MemberPageResponse(content, offset, limit, total);
    }

    /**
     * 권한 변경 (관리자용)
     */
    @Override
    @Transactional
    public void updateRole(Long memberId, Role newRole) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberExceptionType.MEMBER_NOT_FOUND));

        member.updateRole(newRole);
    }

    private void validateDuplicate(String email, String nickname) {
        if (memberRepository.existsByEmail(email)) {
            throw new MemberException(MemberExceptionType.ALREADY_EXIST_EMAIL);
        }
        if (memberRepository.existsByNickname(nickname)) {
            throw new MemberException(MemberExceptionType.ALREADY_EXIST_NICKNAME);
        }
    }

    private Member getMember(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(() -> new MemberException(MemberExceptionType.MEMBER_NOT_FOUND));
    }
}
