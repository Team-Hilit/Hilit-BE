package com.yapp.d14.portfolio.application.service;

import com.yapp.d14.portfolio.application.port.out.PortfolioRepository;
import com.yapp.d14.portfolio.domain.Portfolio;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class PortfolioCompletionPersister {

    private final PortfolioRepository portfolioRepository;

    // PortfolioProcessingTimeoutHandler와 같은 락을 잡고 PROCESSING 여부를 다시 확인한다.
    // 락 없이 확인하면 그 사이 끼어든 타임아웃 처리를 덮어써, 임베딩·S3가 지워진 채 READY가 될 수 있다.
    // summaryBullets는 요약 생성이 실패하면 빈 리스트로 들어온다 — READY 전환 자체는 항상 그대로 진행한다(HLT-11).
    @Transactional
    boolean completeIfStillProcessing(UUID portfolioId, List<String> summaryBullets) {
        Portfolio current = portfolioRepository.findByIdForUpdate(portfolioId).orElse(null);
        if (current == null || current.isDeleted() || !current.isProcessing()) {
            return false;
        }
        current.ready();
        current.updateSummaryBullets(summaryBullets);
        portfolioRepository.save(current);
        return true;
    }
}
