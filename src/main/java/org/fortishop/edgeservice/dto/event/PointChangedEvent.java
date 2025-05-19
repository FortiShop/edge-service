package org.fortishop.edgeservice.dto.event;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PointChangedEvent {
    private Long memberId;
    private Long orderId;         // optional (null 가능)
    private String changeType;    // SAVE, USE, CANCEL
    private BigDecimal amount;
    private String reason;
    private String transactionId;
    private String timestamp;
    private String traceId;
    private String sourceService;
}

