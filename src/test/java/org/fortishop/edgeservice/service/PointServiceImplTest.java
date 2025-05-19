package org.fortishop.edgeservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import org.fortishop.edgeservice.domain.Member;
import org.fortishop.edgeservice.domain.MemberPoint;
import org.fortishop.edgeservice.domain.PointChangeType;
import org.fortishop.edgeservice.domain.PointHistory;
import org.fortishop.edgeservice.domain.PointSourceService;
import org.fortishop.edgeservice.exception.Member.MemberException;
import org.fortishop.edgeservice.repository.MemberPointRepository;
import org.fortishop.edgeservice.repository.MemberRepository;
import org.fortishop.edgeservice.repository.PointHistoryRepository;
import org.fortishop.edgeservice.dto.request.PointAdjustRequest;
import org.fortishop.edgeservice.dto.request.PointTransferRequest;
import org.fortishop.edgeservice.dto.response.PointResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class PointServiceImplTest {

    @InjectMocks
    private PointServiceImpl pointService;

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private MemberPointRepository memberPointRepository;
    @Mock
    private PointHistoryRepository pointHistoryRepository;

    private final String email = "test@fortishop.com";
    private Member member;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        member = Member.builder().id(1L).email(email).nickname("user").build();
    }

    @Test
    @DisplayName("회원의 잔여 포인트를 정상적으로 조회한다")
    void getMyPoint_success() {
        MemberPoint point = new MemberPoint(member);
        point.add(BigDecimal.valueOf(3000));

        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(memberPointRepository.findByMember(member)).thenReturn(Optional.of(point));

        PointResponse response = pointService.getMyPoint(email);

        assertThat(response.getPoint()).isEqualTo(BigDecimal.valueOf(3000));
    }

    @Test
    @DisplayName("존재하지 않는 이메일일 경우 예외를 반환한다")
    void getMyPoint_memberNotFound() {
        when(memberRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pointService.getMyPoint(email))
                .isInstanceOf(MemberException.class);
    }

    @Test
    @DisplayName("중복된 트랜잭션 ID일 경우 포인트 적립을 하지 않는다")
    void savePoint_duplicateTransaction() {
        when(pointHistoryRepository.existsByTransactionId("tx123")).thenReturn(true);

        pointService.savePoint(email, BigDecimal.valueOf(1000), "적립 사유", "tx123", "trace-1",
                PointSourceService.MEMBER_ADJUST);

        verify(memberRepository, never()).findByEmail(any());
        verify(memberPointRepository, never()).save(any());
        verify(pointHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("포인트 적립 시 기존 잔액에 금액이 추가된다")
    void savePoint_success() {
        MemberPoint point = new MemberPoint(member);
        when(pointHistoryRepository.existsByTransactionId(any())).thenReturn(false);
        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(memberPointRepository.findByMember(member)).thenReturn(Optional.of(point));

        pointService.savePoint(email, BigDecimal.valueOf(1000), "첫 적립", "tx-001", "trace-001",
                PointSourceService.MEMBER_ADJUST);

        assertThat(point.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        verify(pointHistoryRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("포인트 차감 시 잔액이 정상적으로 감소한다")
    void usePoint_success() {
        MemberPoint point = new MemberPoint(member);
        point.add(BigDecimal.valueOf(5000));

        when(pointHistoryRepository.existsByTransactionId(any())).thenReturn(false);
        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(memberPointRepository.findByMember(member)).thenReturn(Optional.of(point));

        pointService.usePoint(email, BigDecimal.valueOf(3000), "사용", "tx-use-1", "trace-use-1",
                PointSourceService.MEMBER_ADJUST);

        assertThat(point.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(2000));
        verify(pointHistoryRepository).save(any(PointHistory.class));
    }

    @Test
    @DisplayName("수신자가 존재하지 않으면 MemberException이 발생한다")
    void transferPoint_receiverNotFound() {
        PointTransferRequest request = new PointTransferRequest(999L, BigDecimal.valueOf(1000), "전송", "tx-tf-1",
                "trace-tf-1", "tx-tf-2", "trace-tf-2");

        when(pointHistoryRepository.existsByTransactionId(any())).thenReturn(false);
        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(memberRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pointService.transferPoint(email, request,
                PointSourceService.MEMBER_TRANSFER))
                .isInstanceOf(MemberException.class);
    }

    @Test
    @DisplayName("관리자가 차감 요청 시 잔액이 부족하면 예외가 발생한다")
    void adjustPoint_insufficientBalance() {
        MemberPoint point = new MemberPoint(member);
        point.add(BigDecimal.valueOf(1000));

        PointAdjustRequest request = new PointAdjustRequest(member.getId(), BigDecimal.valueOf(2000),
                PointChangeType.USE, "관리자 차감", "tx-aj-1", "trace-aj-1");

        when(pointHistoryRepository.existsByTransactionId(any())).thenReturn(false);
        when(memberRepository.findById(member.getId())).thenReturn(Optional.of(member));
        when(memberPointRepository.findByMember(member)).thenReturn(Optional.of(point));

        assertThatThrownBy(() ->
                pointService.adjustPoint(request, email, PointSourceService.MEMBER_ADJUST)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("잔액이 부족");
    }
}
