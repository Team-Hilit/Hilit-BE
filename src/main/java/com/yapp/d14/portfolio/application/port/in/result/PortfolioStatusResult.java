package com.yapp.d14.portfolio.application.port.in.result;

import com.yapp.d14.portfolio.domain.PortfolioStatus;

import java.util.List;
import java.util.UUID;

public record PortfolioStatusResult(
        UUID portfolioId,
        PortfolioStatus status,
        String message,
        String fileName,
        List<String> contentSummaryBullets
) {
}
