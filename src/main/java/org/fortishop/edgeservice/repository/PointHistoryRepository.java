package org.fortishop.edgeservice.repository;

import java.util.List;
import org.fortishop.edgeservice.domain.Member;
import org.fortishop.edgeservice.domain.PointHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PointHistoryRepository extends JpaRepository<PointHistory, Long> {
    List<PointHistory> findAllByMemberOrderByCreatedAtDesc(Member member);

    boolean existsByTransactionId(String transactionId);
}

