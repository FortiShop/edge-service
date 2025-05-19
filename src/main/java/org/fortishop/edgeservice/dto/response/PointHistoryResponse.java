package org.fortishop.edgeservice.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.fortishop.edgeservice.domain.PointChangeType;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED, force = true)
@AllArgsConstructor
public class PointHistoryResponse {
    private PointChangeType type;
    private BigDecimal amount;
    private String description;
    private LocalDateTime createdAt;
}
