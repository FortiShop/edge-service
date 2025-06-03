package org.fortishop.edgeservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.fortishop.edgeservice.auth.PrincipalDetails;
import org.fortishop.edgeservice.domain.Role;
import org.fortishop.edgeservice.dto.request.MemberUpdateNicknameRequest;
import org.fortishop.edgeservice.dto.request.PasswordUpdateRequest;
import org.fortishop.edgeservice.dto.request.SignupRequest;
import org.fortishop.edgeservice.dto.response.MemberPageResponse;
import org.fortishop.edgeservice.dto.response.MemberResponse;
import org.fortishop.edgeservice.dto.response.MemberUpdateNicknameResponse;
import org.fortishop.edgeservice.global.Responder;
import org.fortishop.edgeservice.service.MemberService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @PostMapping("/signup")
    public ResponseEntity<MemberResponse> signup(@Valid @RequestBody SignupRequest request) {
        MemberResponse memberResponse = memberService.signup(request);
        return Responder.success(memberResponse, HttpStatus.CREATED);
    }

    @GetMapping("/me")
    public ResponseEntity<MemberResponse> getMyInfo(@AuthenticationPrincipal PrincipalDetails principal) {
        return Responder.success(memberService.getMyInfo(principal));
    }

    @PatchMapping("/nickname")
    public ResponseEntity<MemberUpdateNicknameResponse> updateNickname(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Valid @RequestBody MemberUpdateNicknameRequest request) {
        MemberUpdateNicknameResponse response = memberService.updateNickname(principal, request);
        return Responder.success(response, HttpStatus.OK);
    }

    @PatchMapping("/password")
    public ResponseEntity<Void> updatePassword(@AuthenticationPrincipal PrincipalDetails principal,
                                               @Valid @RequestBody PasswordUpdateRequest request) {
        memberService.updatePassword(principal, request);
        return Responder.success(HttpStatus.OK);
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal PrincipalDetails principal) {
        memberService.withdraw(principal);
        return Responder.success(HttpStatus.NO_CONTENT);
    }

    @GetMapping("/check-email")
    public ResponseEntity<Void> checkEmailDuplicate(@RequestParam(name = "email") String email) {
        memberService.checkEmailDuplicate(email);
        return Responder.success(HttpStatus.OK);
    }

    @GetMapping("/check-nickname")
    public ResponseEntity<Void> checkNicknameDuplicate(@RequestParam(name = "nickname") String nickname) {
        memberService.checkNicknameDuplicate(nickname);
        return Responder.success(HttpStatus.OK);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    public ResponseEntity<MemberPageResponse> getMembers(@RequestParam(defaultValue = "0", name = "offset") int offset,
                                                         @RequestParam(defaultValue = "20", name = "limit") int limit) {
        return Responder.success(memberService.getMembers(offset, limit));
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> updateRole(@PathVariable(name = "id") Long id,
                                           @RequestParam(name = "role") Role role) {
        memberService.updateRole(id, role);
        return Responder.success(HttpStatus.OK);
    }
}
