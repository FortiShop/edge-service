package org.fortishop.edgeservice.repository;

import java.util.Optional;
import org.fortishop.edgeservice.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean exitsByNickname(String nickname);
}
