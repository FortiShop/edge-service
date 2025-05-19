package org.fortishop.edgeservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fortishop.edgeservice.dto.event.PointChangedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class PointKafkaProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void send(PointChangedEvent event) {
        kafkaTemplate.send("point.changed", event.getMemberId().toString(), event);
        log.info("[Kafka] Sent point.changed: {}", event);
    }
}
