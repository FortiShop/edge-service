package org.fortishop.edgeservice.service;

import static java.util.stream.Collectors.toList;

import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fortishop.edgeservice.domain.Member;
import org.fortishop.edgeservice.domain.MemberPoint;
import org.fortishop.edgeservice.domain.PointChangeType;
import org.fortishop.edgeservice.domain.PointHistory;
import org.fortishop.edgeservice.domain.PointSourceService;
import org.fortishop.edgeservice.dto.request.PointAdjustRequest;
import org.fortishop.edgeservice.dto.request.PointTransferRequest;
import org.fortishop.edgeservice.dto.response.PointHistoryResponse;
import org.fortishop.edgeservice.dto.response.PointResponse;
import org.fortishop.edgeservice.exception.Member.MemberException;
import org.fortishop.edgeservice.exception.Member.MemberExceptionType;
import org.fortishop.edgeservice.repository.MemberPointRepository;
import org.fortishop.edgeservice.repository.MemberRepository;
import org.fortishop.edgeservice.repository.PointHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PointServiceImpl implements PointService {
    private final MemberRepository memberRepository;
    private final MemberPointRepository memberPointRepository;
    private final PointHistoryRepository pointHistoryRepository;

    private boolean isDuplicateTransaction(String transactionId) {
        boolean exists = pointHistoryRepository.existsByTransactionId(transactionId);
        if (exists) {
            log.warn("중복된 transactionId 요청입니다: {}", transactionId);
        }
        return exists;
    }

    @Override
    @Transactional(readOnly = true)
    public PointResponse getMyPoint(String email) {
        Member member = getMemberByEmail(email);
        BigDecimal amount = memberPointRepository.findByMember(member)
                .map(MemberPoint::getAmount)
                .orElse(BigDecimal.ZERO);
        return new PointResponse(amount);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PointHistoryResponse> getMyHistory(String email) {
        Member member = getMemberByEmail(email);
        return pointHistoryRepository.findAllByMemberOrderByCreatedAtDesc(member).stream()
                .map(ph -> new PointHistoryResponse(
                        ph.getChangeType(),
                        ph.getAmount(),
                        ph.getDescription(),
                        ph.getCreatedAt()
                ))
                .collect(toList());
    }

    @Override
    @Transactional
    public void savePoint(String email, BigDecimal amount, String reason,
                          String transactionId, String traceId, PointSourceService sourceService) {
        // Kafka 이벤트 소비자 전용 메서드
        if (isDuplicateTransaction(transactionId)) {
            return;
        }

        Member member = getMemberByEmail(email);
        MemberPoint point = memberPointRepository.findByMember(member)
                .orElseGet(() -> memberPointRepository.save(new MemberPoint(member)));

        point.add(amount);

        pointHistoryRepository.save(PointHistory.builder()
                .member(member)
                .changeType(PointChangeType.SAVE)
                .amount(amount)
                .description(reason)
                .transactionId(transactionId)
                .traceId(traceId)
                .sourceService(sourceService)
                .build());
    }

    @Override
    @Transactional
    public void usePoint(String email, BigDecimal amount, String reason,
                         String transactionId, String traceId, PointSourceService sourceService) {
        // Kafka 이벤트 소비자 전용 메서드
        if (isDuplicateTransaction(transactionId)) {
            return;
        }

        Member member = getMemberByEmail(email);
        MemberPoint point = memberPointRepository.findByMember(member)
                .orElseThrow(() -> new IllegalStateException("포인트 정보가 없습니다."));

        if (point.getAmount().compareTo(amount) < 0) {
            throw new IllegalArgumentException("잔액이 부족합니다.");
        }

        point.subtract(amount);

        pointHistoryRepository.save(PointHistory.builder()
                .member(member)
                .changeType(PointChangeType.USE)
                .amount(amount)
                .description(reason)
                .transactionId(transactionId)
                .traceId(traceId)
                .sourceService(sourceService)
                .build());
    }

    @Override
    @Transactional
    public void transferPoint(String senderEmail, PointTransferRequest request,
                              PointSourceService sourceService) {
        if (isDuplicateTransaction(request.getSenderTransactionId())) {
            return;
        }

        Member sender = getMemberByEmail(senderEmail);
        Member receiver = memberRepository.findById(request.getReceiverId())
                .orElseThrow(() -> new MemberException(MemberExceptionType.MEMBER_NOT_FOUND));

        BigDecimal amount = request.getAmount();

        MemberPoint senderPoint = memberPointRepository.findByMember(sender)
                .orElseThrow(() -> new IllegalStateException("보낸 사람의 포인트 정보가 없습니다."));
        if (senderPoint.getAmount().compareTo(amount) < 0) {
            throw new IllegalArgumentException("잔액이 부족합니다.");
        }
        senderPoint.subtract(amount);

        MemberPoint receiverPoint = memberPointRepository.findByMember(receiver)
                .orElseGet(() -> memberPointRepository.save(new MemberPoint(receiver)));
        receiverPoint.add(amount);

        pointHistoryRepository.save(PointHistory.builder()
                .member(sender)
                .changeType(PointChangeType.TRANSFER)
                .amount(amount.negate())
                .description("→ " + receiver.getNickname() + ": " + request.getReason())
                .transactionId(request.getSenderTransactionId())
                .traceId(request.getSenderTraceId())
                .sourceService(sourceService)
                .build());

        pointHistoryRepository.save(PointHistory.builder()
                .member(receiver)
                .changeType(PointChangeType.SAVE)
                .amount(amount)
                .description("← " + sender.getNickname() + ": " + request.getReason())
                .transactionId(request.getReceiverTransactionId())
                .traceId(request.getSenderTraceId())
                .sourceService(sourceService)
                .build());
    }

    @Override
    @Transactional
    public void adjustPoint(PointAdjustRequest request, String adminEmail, PointSourceService sourceService) {
        if (isDuplicateTransaction(request.getTransactionId())) {
            return;
        }

        Member receiver = memberRepository.findById(request.getMemberId())
                .orElseThrow(() -> new MemberException(MemberExceptionType.MEMBER_NOT_FOUND));
        BigDecimal amount = request.getAmount();
        PointChangeType changeType = request.getChangeType();

        MemberPoint point = memberPointRepository.findByMember(receiver)
                .orElseGet(() -> memberPointRepository.save(new MemberPoint(receiver)));

        if (changeType == PointChangeType.SAVE) {
            point.add(amount);
        } else if (changeType == PointChangeType.USE) {
            if (point.getAmount().compareTo(amount) < 0) {
                throw new IllegalArgumentException("잔액이 부족합니다.");
            }
            point.subtract(amount);
        }

        pointHistoryRepository.save(PointHistory.builder()
                .member(receiver)
                .changeType(changeType)
                .amount(amount)
                .description("[조정] " + request.getDescription())
                .transactionId(request.getTransactionId())
                .traceId(request.getTraceId())
                .sourceService(sourceService)
                .build());
    }

    @Override
    @Transactional
    public void savePoint(Long memberId, BigDecimal amount, String reason,
                          String transactionId, String traceId, PointSourceService sourceService) {
        if (isDuplicateTransaction(transactionId)) {
            return;
        }

        Member member = getMemberById(memberId);
        MemberPoint point = memberPointRepository.findByMember(member)
                .orElseGet(() -> memberPointRepository.save(new MemberPoint(member)));

        point.add(amount);

        pointHistoryRepository.save(PointHistory.builder()
                .member(member)
                .changeType(PointChangeType.SAVE)
                .amount(amount)
                .description(reason)
                .transactionId(transactionId)
                .traceId(traceId)
                .sourceService(sourceService)
                .build());
    }

    @Override
    @Transactional
    public void usePoint(Long memberId, BigDecimal amount, String reason,
                         String transactionId, String traceId, PointSourceService sourceService) {
        if (isDuplicateTransaction(transactionId)) {
            return;
        }

        Member member = getMemberById(memberId);
        MemberPoint point = memberPointRepository.findByMember(member)
                .orElseThrow(() -> new IllegalStateException("포인트 정보가 없습니다."));

        if (point.getAmount().compareTo(amount) < 0) {
            throw new IllegalArgumentException("잔액이 부족합니다.");
        }

        point.subtract(amount);

        pointHistoryRepository.save(PointHistory.builder()
                .member(member)
                .changeType(PointChangeType.USE)
                .amount(amount)
                .description(reason)
                .transactionId(transactionId)
                .traceId(traceId)
                .sourceService(sourceService)
                .build());
    }

    private Member getMemberById(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberExceptionType.MEMBER_NOT_FOUND));
    }

    private Member getMemberByEmail(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(() -> new MemberException(MemberExceptionType.MEMBER_NOT_FOUND));
    }
}
