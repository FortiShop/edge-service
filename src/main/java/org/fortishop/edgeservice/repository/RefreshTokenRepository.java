package org.fortishop.edgeservice.repository;

import java.util.Optional;
import org.fortishop.edgeservice.domain.Member;
import org.fortishop.edgeservice.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByMember(Member member);

    void deleteByMember(Member member);
}
