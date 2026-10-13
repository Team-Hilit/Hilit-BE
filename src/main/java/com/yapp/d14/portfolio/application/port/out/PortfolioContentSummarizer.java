package com.yapp.d14.portfolio.application.port.out;

import java.util.List;
import java.util.UUID;

public interface PortfolioContentSummarizer {

    List<String> summarize(UUID portfolioId, String maskedText);
}
