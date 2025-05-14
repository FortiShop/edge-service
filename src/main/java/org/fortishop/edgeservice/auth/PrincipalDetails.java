package org.fortishop.edgeservice.auth;

import java.util.Collection;
import java.util.Collections;
import org.fortishop.edgeservice.domain.Member;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class PrincipalDetails extends Member implements UserDetails {
    private final Long id;
    private final String email;
    private String password;
    private final String role;

    public PrincipalDetails(Member member) {
        this.id = member.getId();
        this.email = member.getEmail();
        this.password = member.getPassword();
        this.role = member.getRole().toString();
    }

    private PrincipalDetails(Long memberId, String email, String role) {
        this.id = memberId;
        this.email = email;
        this.role = role;
    }

    public static PrincipalDetails of(Member member) {
        return new PrincipalDetails(member);
    }

    public static PrincipalDetails of(Long memberId, String email, String role) {
        return new PrincipalDetails(memberId, email, role);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority(role));
    }

    @Override
    public String getUsername() {
        return this.email;
    }

    @Override
    public String getPassword() {
        return this.password;
    }

    @Override
    public Long getId() {
        return this.id;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
