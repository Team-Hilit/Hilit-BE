package com.yapp.d14.portfolio.application.port.in.result;

import com.yapp.d14.portfolio.domain.PortfolioStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PortfolioSummary(
        UUID portfolioId,
        String fileName,
        long fileSize,
        Integer pageCount,
        PortfolioStatus status,
        LocalDateTime uploadedAt,
        boolean interviewInProgress,
        List<String> contentSummaryBullets
) {
}
