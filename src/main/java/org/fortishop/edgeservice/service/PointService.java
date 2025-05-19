package org.fortishop.edgeservice.service;

import java.math.BigDecimal;
import java.util.List;
import org.fortishop.edgeservice.domain.PointSourceService;
import org.fortishop.edgeservice.dto.request.PointAdjustRequest;
import org.fortishop.edgeservice.dto.request.PointTransferRequest;
import org.fortishop.edgeservice.dto.response.PointHistoryResponse;
import org.fortishop.edgeservice.dto.response.PointResponse;

public interface PointService {

    PointResponse getMyPoint(String email);

    List<PointHistoryResponse> getMyHistory(String email);

    void savePoint(String email, BigDecimal amount, String reason,
                   String transactionId, String traceId, PointSourceService sourceService);

    void usePoint(String email, BigDecimal amount, String reason,
                  String transactionId, String traceId, PointSourceService sourceService);

    void transferPoint(String senderEmail, PointTransferRequest request,
                       PointSourceService sourceService);

    void adjustPoint(PointAdjustRequest request, String adminEmail, PointSourceService sourceService);

    void savePoint(Long memberId, BigDecimal amount, String reason,
                   String transactionId, String traceId, PointSourceService sourceService);

    void usePoint(Long memberId, BigDecimal amount, String reason,
                  String transactionId, String traceId, PointSourceService sourceService);
}
