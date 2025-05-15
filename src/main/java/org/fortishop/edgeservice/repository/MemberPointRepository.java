package org.fortishop.edgeservice.repository;

import java.util.Optional;
import org.fortishop.edgeservice.domain.Member;
import org.fortishop.edgeservice.domain.MemberPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MemberPointRepository extends JpaRepository<MemberPoint, Long> {
    Optional<MemberPoint> findByMember(Member member);
}

