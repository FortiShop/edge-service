package org.fortishop.edgeservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.fortishop.edgeservice.auth.PrincipalDetails;
import org.fortishop.edgeservice.domain.Member;
import org.fortishop.edgeservice.domain.MemberPoint;
import org.fortishop.edgeservice.domain.Role;
import org.fortishop.edgeservice.exception.Member.MemberException;
import org.fortishop.edgeservice.exception.Member.MemberExceptionType;
import org.fortishop.edgeservice.repository.MemberPointRepository;
import org.fortishop.edgeservice.repository.MemberRepository;
import org.fortishop.edgeservice.dto.request.MemberUpdateNicknameRequest;
import org.fortishop.edgeservice.dto.request.PasswordUpdateRequest;
import org.fortishop.edgeservice.dto.request.SignupRequest;
import org.fortishop.edgeservice.dto.response.MemberPageResponse;
import org.fortishop.edgeservice.dto.response.MemberResponse;
import org.fortishop.edgeservice.dto.response.MemberUpdateNicknameResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;

public class MemberServiceImplTest {

    @InjectMocks
    private MemberServiceImpl memberService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberPointRepository memberPointRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("신규 회원 가입에 성공하고 포인트가 생성된다")
    void signup_success() {
        SignupRequest request = new SignupRequest("a@a.com", "pw1234", "nick");

        given(memberRepository.findByEmailAndDeletedTrue(request.getEmail()))
                .willReturn(Optional.empty());

        given(memberRepository.existsByEmail(request.getEmail())).willReturn(false);
        given(memberRepository.existsByNickname(request.getNickname())).willReturn(false);

        given(passwordEncoder.encode("pw1234")).willReturn("encoded");

        Member savedMember = Member.builder()
                .id(1L)
                .email(request.getEmail())
                .nickname(request.getNickname())
                .password("encoded")
                .role(Role.ROLE_USER)
                .deleted(false)
                .createdAt(LocalDateTime.now())
                .build();
        given(memberRepository.save(any())).willReturn(savedMember);

        given(memberPointRepository.save(any())).willReturn(new MemberPoint(savedMember));

        // when
        MemberResponse res = memberService.signup(request);

        // then
        assertThat(res.getEmail()).isEqualTo("a@a.com");
        assertThat(res.getNickname()).isEqualTo("nick");
        verify(memberPointRepository).save(any(MemberPoint.class));
    }

    @Test
    void signup_fail_duplicate_email() {
        given(memberRepository.existsByEmail("a@a.com")).willReturn(true);
        SignupRequest req = new SignupRequest("a@a.com", "pw1234", "nick");

        assertThatThrownBy(() -> memberService.signup(req))
                .isInstanceOf(MemberException.class)
                .extracting("exceptionType")
                .isEqualTo(MemberExceptionType.ALREADY_EXIST_EMAIL);
    }

    @Test
    void getMyInfo_success() {
        PrincipalDetails principal = PrincipalDetails.of(1L, "user@a.com", "ROLE_USER");
        Member m = Member.builder().email("user@a.com").nickname("nick").password("pw1234").role(Role.ROLE_USER)
                .build();
        given(memberRepository.findByEmail("user@a.com")).willReturn(Optional.of(m));

        MemberResponse res = memberService.getMyInfo(principal);

        assertThat(res.getEmail()).isEqualTo("user@a.com");
    }

    @Test
    void updatePassword_success() {
        PrincipalDetails p = PrincipalDetails.of(1L, "user@a.com", "ROLE_USER");
        PasswordUpdateRequest req = new PasswordUpdateRequest("cur123", "new123");
        Member m = Member.builder().email("user@a.com").password("enc").build();
        given(memberRepository.findByEmail("user@a.com")).willReturn(Optional.of(m));
        given(passwordEncoder.matches("cur123", "enc")).willReturn(true);
        given(passwordEncoder.encode("new123")).willReturn("new-enc");

        memberService.updatePassword(p, req);

        assertThat(m.getPassword()).isEqualTo("new-enc");
    }

    @Test
    void updatePassword_fail_invalid_current() {
        PrincipalDetails p = PrincipalDetails.of(1L, "user@a.com", "ROLE_USER");
        PasswordUpdateRequest req = new PasswordUpdateRequest("wrongpw", "newpw1");
        Member m = Member.builder().email("user@a.com").password("enc").build();
        given(memberRepository.findByEmail("user@a.com")).willReturn(Optional.of(m));
        given(passwordEncoder.matches("wrongpw", "enc")).willReturn(false);

        assertThatThrownBy(() -> memberService.updatePassword(p, req))
                .isInstanceOf(MemberException.class)
                .extracting("exceptionType")
                .isEqualTo(MemberExceptionType.INVALID_PASSWORD);
    }

    @Test
    void updateNickname_success() {
        PrincipalDetails p = PrincipalDetails.of(1L, "user@a.com", "ROLE_USER");
        MemberUpdateNicknameRequest req = new MemberUpdateNicknameRequest("newnick");
        Member m = Member.builder().email("user@a.com").nickname("oldnick").build();
        given(memberRepository.findByEmail("user@a.com")).willReturn(Optional.of(m));
        given(memberRepository.existsByNickname("newnick")).willReturn(false);

        MemberUpdateNicknameResponse res = memberService.updateNickname(p, req);

        assertThat(res.getNickname()).isEqualTo("newnick");
    }

    @Test
    void updateNickname_fail_duplicate() {
        PrincipalDetails p = PrincipalDetails.of(1L, "user@a.com", "ROLE_USER");
        MemberUpdateNicknameRequest req = new MemberUpdateNicknameRequest("dup");
        Member m = Member.builder().email("user@a.com").nickname("old").build();
        given(memberRepository.findByEmail("user@a.com")).willReturn(Optional.of(m));
        given(memberRepository.existsByNickname("dup")).willReturn(true);

        assertThatThrownBy(() -> memberService.updateNickname(p, req))
                .isInstanceOf(MemberException.class)
                .extracting("exceptionType")
                .isEqualTo(MemberExceptionType.ALREADY_EXIST_NICKNAME);
    }

    @Test
    void withdraw_success() {
        PrincipalDetails p = PrincipalDetails.of(1L, "user@a.com", "ROLE_USER");
        Member m = Member.builder().email("user@a.com").deleted(false).build();
        given(memberRepository.findByEmail("user@a.com")).willReturn(Optional.of(m));

        memberService.withdraw(p);

        assertThat(m.isDeleted()).isTrue();
    }

    @Test
    void getMembers_success() {
        Member m1 = Member.builder().email("1@a.com").nickname("1").role(Role.ROLE_USER).build();
        Member m2 = Member.builder().email("2@a.com").nickname("2").role(Role.ROLE_USER).build();
        given(memberRepository.findMembersWithPaging(0, 2)).willReturn(List.of(m1, m2));
        given(memberRepository.count()).willReturn(2L);

        MemberPageResponse page = memberService.getMembers(0, 2);

        assertThat(page.getMembers()).hasSize(2);
        assertThat(page.getTotal()).isEqualTo(2);
    }

    @Test
    void updateRole_success() {
        Member m = Member.builder().email("a@a.com").role(Role.ROLE_USER).build();
        given(memberRepository.findById(1L)).willReturn(Optional.of(m));

        memberService.updateRole(1L, Role.ROLE_ADMIN);

        assertThat(m.getRole()).isEqualTo(Role.ROLE_ADMIN);
    }

    @Test
    void getMyInfo_fail_memberNotFound() {
        PrincipalDetails principal = PrincipalDetails.of(1L, "no-user@a.com", "ROLE_USER");
        given(memberRepository.findByEmail("no-user@a.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.getMyInfo(principal))
                .isInstanceOf(MemberException.class)
                .extracting("exceptionType")
                .isEqualTo(MemberExceptionType.MEMBER_NOT_FOUND);
    }

    @Test
    void updateRole_fail_memberNotFound() {
        given(memberRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.updateRole(999L, Role.ROLE_ADMIN))
                .isInstanceOf(MemberException.class)
                .extracting("exceptionType")
                .isEqualTo(MemberExceptionType.MEMBER_NOT_FOUND);
    }

    @Test
    void checkEmailDuplicate_success() {
        given(memberRepository.existsByEmail("free@a.com")).willReturn(false);

        memberService.checkEmailDuplicate("free@a.com");
    }

    @Test
    void checkEmailDuplicate_fail_alreadyExists() {
        given(memberRepository.existsByEmail("dup@a.com")).willReturn(true);

        assertThatThrownBy(() -> memberService.checkEmailDuplicate("dup@a.com"))
                .isInstanceOf(MemberException.class)
                .extracting("exceptionType")
                .isEqualTo(MemberExceptionType.ALREADY_EXIST_EMAIL);
    }

    @Test
    void checkNicknameDuplicate_success() {
        given(memberRepository.existsByNickname("freeNick")).willReturn(false);

        memberService.checkNicknameDuplicate("freeNick");
    }

    @Test
    void checkNicknameDuplicate_fail_alreadyExists() {
        given(memberRepository.existsByNickname("dupNick")).willReturn(true);

        assertThatThrownBy(() -> memberService.checkNicknameDuplicate("dupNick"))
                .isInstanceOf(MemberException.class)
                .extracting("exceptionType")
                .isEqualTo(MemberExceptionType.ALREADY_EXIST_NICKNAME);
    }
}
