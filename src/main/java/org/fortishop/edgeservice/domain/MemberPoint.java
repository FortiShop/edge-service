package org.fortishop.edgeservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false, unique = true)
    private Member member;

    @Column(nullable = false)
    private BigDecimal amount = BigDecimal.ZERO;

    private LocalDateTime lastUpdated;

    public MemberPoint(Member member) {
        this.member = member;
        this.lastUpdated = LocalDateTime.now();
    }

    public void add(BigDecimal value) {
        this.amount = this.amount.add(value);
        this.lastUpdated = LocalDateTime.now();
    }

    public void subtract(BigDecimal value) {
        this.amount = this.amount.subtract(value);
        this.lastUpdated = LocalDateTime.now();
    }

    public void resetToZero() {
        this.amount = BigDecimal.ZERO;
        this.lastUpdated = LocalDateTime.now();
    }

}
