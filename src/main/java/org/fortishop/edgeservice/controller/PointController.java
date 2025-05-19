package org.fortishop.edgeservice.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.fortishop.edgeservice.auth.PrincipalDetails;
import org.fortishop.edgeservice.domain.PointSourceService;
import org.fortishop.edgeservice.global.Responder;
import org.fortishop.edgeservice.dto.request.PointAdjustRequest;
import org.fortishop.edgeservice.dto.request.PointTransferRequest;
import org.fortishop.edgeservice.dto.response.PointHistoryResponse;
import org.fortishop.edgeservice.dto.response.PointResponse;
import org.fortishop.edgeservice.service.PointService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/points")
public class PointController {
    private final PointService pointService;

    @GetMapping
    public ResponseEntity<PointResponse> getMyPoint(@AuthenticationPrincipal PrincipalDetails principal) {
        String email = principal.getUsername();
        return Responder.success(pointService.getMyPoint(email));
    }

    @GetMapping("/history")
    public ResponseEntity<List<PointHistoryResponse>> getMyHistory(
            @AuthenticationPrincipal PrincipalDetails principal) {
        String email = principal.getUsername();
        return Responder.success(pointService.getMyHistory(email));
    }

    @PostMapping("/transfer")
    public ResponseEntity<Void> transferPoint(@AuthenticationPrincipal PrincipalDetails principal,
                                              @RequestBody PointTransferRequest request) {
        pointService.transferPoint(
                principal.getUsername(),
                request,
                PointSourceService.MEMBER_TRANSFER
        );
        return Responder.success(HttpStatus.OK);
    }

    @PostMapping("/adjust")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> adjustPoint(@AuthenticationPrincipal PrincipalDetails principal,
                                            @RequestBody PointAdjustRequest request) {
        pointService.adjustPoint(
                request,
                principal.getUsername(),
                PointSourceService.MEMBER_ADJUST
        );
        return Responder.success(HttpStatus.OK);
    }
}
