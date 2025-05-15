package org.fortishop.edgeservice.repository;

import java.util.List;
import java.util.Optional;
import org.fortishop.edgeservice.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByEmail(String email);

    Optional<Member> findByEmailAndDeletedTrue(String email);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    @Query("SELECT m FROM Member m ORDER BY m.id DESC")
    List<Member> findMembersWithPaging(@Param("offset") int offset, @Param("limit") int limit);

    @Query("SELECT COUNT(m) FROM Member m")
    long count();

}
