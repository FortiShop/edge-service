package org.fortishop.edgeservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fortishop.edgeservice.domain.PointSourceService;
import org.fortishop.edgeservice.dto.event.PointChangedEvent;
import org.fortishop.edgeservice.service.PointService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PointKafkaConsumer {

    private final PointService pointService;

    @KafkaListener(topics = "point.changed", groupId = "point-group", containerFactory = "pointChangedKafkaListenerContainerFactory")
    public void consume(PointChangedEvent event, Acknowledgment ack) {
        log.info("[Kafka] Received point.changed: memberId={}, type={}, amount={}",
                event.getMemberId(), event.getChangeType(), event.getAmount());

        try {
            switch (event.getChangeType()) {
                case "SAVE" -> pointService.savePoint(
                        event.getMemberId(), event.getAmount(), event.getReason(),
                        event.getTransactionId(), event.getTraceId(),
                        PointSourceService.valueOf(event.getSourceService())
                );
                case "USE" -> pointService.usePoint(
                        event.getMemberId(), event.getAmount(), event.getReason(),
                        event.getTransactionId(), event.getTraceId(),
                        PointSourceService.valueOf(event.getSourceService())
                );
                case "CANCEL" -> pointService.savePoint( // 적립금 복구
                        event.getMemberId(), event.getAmount(), "[CANCEL] " + event.getReason(),
                        event.getTransactionId(), event.getTraceId(),
                        PointSourceService.valueOf(event.getSourceService())
                );
                default -> log.warn("Unsupported changeType: {}", event.getChangeType());
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to handle point.changed event", e);
            throw e;
        }
    }

    @KafkaListener(topics = "point.changed.dlq", groupId = "point-dlq-group")
    public void handleDlq(PointChangedEvent event) {
        log.error("[DLQ 메시지 확인] point.changed 처리 실패 : {}", event);
        // slack 또는 이메일로 개발자, 관리자에게 알림
    }
}
