package org.fortishop.edgeservice.dto.request;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.fortishop.edgeservice.domain.PointChangeType;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PointAdjustRequest {
    private Long memberId;
    private BigDecimal amount;
    private PointChangeType changeType;
    private String description;
    private String transactionId;
    private String traceId;
}
