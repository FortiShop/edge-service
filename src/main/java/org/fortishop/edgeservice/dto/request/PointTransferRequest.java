package org.fortishop.edgeservice.dto.request;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PointTransferRequest {
    private Long receiverId;
    private BigDecimal amount;
    private String reason;
    private String senderTransactionId;
    private String senderTraceId;
    private String receiverTransactionId;
    private String receiverTraceId;
}
