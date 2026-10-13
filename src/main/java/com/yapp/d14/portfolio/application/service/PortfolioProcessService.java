package com.yapp.d14.portfolio.application.service;

import com.yapp.d14.portfolio.application.port.in.PortfolioProcessUseCase;
import com.yapp.d14.portfolio.application.port.out.PdfTextExtractor;
import com.yapp.d14.portfolio.application.port.out.PortfolioContentSummarizer;
import com.yapp.d14.portfolio.application.port.out.PortfolioEmbeddingStore;
import com.yapp.d14.portfolio.application.port.out.PortfolioFileUploader;
import com.yapp.d14.portfolio.application.port.out.PortfolioRepository;
import com.yapp.d14.portfolio.domain.Portfolio;
import com.yapp.d14.portfolio.domain.PortfolioPiiMasker;
import com.yapp.d14.portfolio.domain.PortfolioStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
class PortfolioProcessService implements PortfolioProcessUseCase {

    private static final String CONTENT_TYPE = "application/pdf";
    private static final int MAX_PROCESS_RETRIES = 2;

    private final PortfolioRepository portfolioRepository;
    private final PortfolioFileUploader portfolioFileUploader;
    private final PdfTextExtractor pdfTextExtractor;
    private final PortfolioEmbeddingStore portfolioEmbeddingStore;
    private final PortfolioContentSummarizer portfolioContentSummarizer;
    private final PortfolioCompletionPersister portfolioCompletionPersister;

    @Override
    @Async("portfolioTaskExecutor")
    public void process(UUID userId, UUID portfolioId, byte[] fileContent) {
        log.info("portfolio async processing triggered: portfolioId={}", portfolioId);

        Portfolio portfolio = PortfolioAccessSupport.requireOwned(portfolioRepository, portfolioId, userId);

        if (!uploadToS3(portfolio, fileContent)) {
            return;
        }

        String extractedText = extractText(portfolio, fileContent);
        if (extractedText == null) {
            return;
        }

        String maskedText = maskPii(portfolio, extractedText);

        if (!embed(portfolio, maskedText)) {
            return;
        }

        List<String> summaryBullets = generateSummary(portfolio, maskedText);

        if (!portfolioCompletionPersister.completeIfStillProcessing(portfolio.getId(), summaryBullets)) {
            log.warn("[PORTFOLIO PROCESS] 처리 시간 초과로 이미 종료됨, 완료 처리를 생략하고 리소스를 정리함: portfolioId={}", portfolio.getId());
            portfolioEmbeddingStore.deleteByPortfolioId(portfolio.getId());
            portfolioFileUploader.delete(portfolio.getS3Key());
        }
    }

    private boolean uploadToS3(Portfolio portfolio, byte[] fileContent) {
        for (int attempt = 1; attempt <= MAX_PROCESS_RETRIES + 1; attempt++) {
            if (attempt > 1 && !isStillProcessing(portfolio.getId())) {
                log.warn("[PORTFOLIO PROCESS] 처리 시간 초과로 이미 종료됨, S3 업로드 재시도를 중단함: portfolioId={}", portfolio.getId());
                break;
            }
            try {
                portfolioFileUploader.upload(portfolio.getS3Key(), fileContent, CONTENT_TYPE);
                return true;
            } catch (Exception e) {
                log.warn("[PORTFOLIO PROCESS] S3 업로드 실패 ({}/{}): portfolioId={}",
                        attempt, MAX_PROCESS_RETRIES + 1, portfolio.getId(), e);
            }
        }

        log.error("[PORTFOLIO PROCESS] S3 업로드 처리 실패: portfolioId={}", portfolio.getId());
        if (isStillProcessing(portfolio.getId())) {
            portfolio.failSystem("파일 업로드에 실패했어요. 잠시 후 다시 시도해 주세요.");
            portfolioRepository.save(portfolio);
        }
        return false;
    }

    private String extractText(Portfolio portfolio, byte[] fileContent) {
        String extractedText;
        try {
            extractedText = pdfTextExtractor.extractText(fileContent);
        } catch (Exception e) {
            log.error("[PORTFOLIO PROCESS] 텍스트 추출 실패: portfolioId={}", portfolio.getId(), e);
            failFileAndCleanupS3(portfolio, "파일이 손상되었거나 암호로 보호되어 있어요. 다시 업로드해 주세요.");
            return null;
        }

        if (!portfolio.hasEnoughExtractedText(extractedText)) {
            log.warn("[PORTFOLIO PROCESS] 추출된 텍스트가 너무 짧음: portfolioId={}, length={}",
                    portfolio.getId(), extractedText.trim().length());
            failFileAndCleanupS3(portfolio, "텍스트를 인식할 수 없어요. 스캔본이 아닌 PDF 파일로 다시 업로드해 주세요.");
            return null;
        }

        return extractedText;
    }

    // 마스킹된 텍스트만 임베딩·LLM 입력으로 흘려보낸다. 로그에는 값이 아닌 유형별 건수만 남긴다.
    private String maskPii(Portfolio portfolio, String extractedText) {
        PortfolioPiiMasker.MaskingResult result = PortfolioPiiMasker.mask(extractedText);
        log.info("[PORTFOLIO PROCESS] PII 마스킹 완료: portfolioId={}, email={}, phone={}, rrn={}",
                portfolio.getId(), result.emailCount(), result.phoneCount(), result.rrnCount());
        return result.maskedText();
    }

    private boolean embed(Portfolio portfolio, String maskedText) {
        for (int attempt = 1; attempt <= MAX_PROCESS_RETRIES + 1; attempt++) {
            if (attempt > 1 && !isStillProcessing(portfolio.getId())) {
                log.warn("[PORTFOLIO PROCESS] 처리 시간 초과로 이미 종료됨, 임베딩 재시도를 중단함: portfolioId={}", portfolio.getId());
                break;
            }
            try {
                portfolioEmbeddingStore.save(portfolio.getId(), portfolio.getUserId(), portfolio.getFileName(), maskedText);
                return true;
            } catch (Exception e) {
                log.warn("[PORTFOLIO PROCESS] 임베딩 실패 ({}/{}): portfolioId={}",
                        attempt, MAX_PROCESS_RETRIES + 1, portfolio.getId(), e);
            }
        }

        log.error("[PORTFOLIO PROCESS] 임베딩 처리 실패: portfolioId={}", portfolio.getId());
        if (isStillProcessing(portfolio.getId())) {
            portfolio.failSystem("포트폴리오 분석에 실패했어요. 잠시 후 다시 시도해 주세요.");
            portfolioRepository.save(portfolio);
        }
        portfolioEmbeddingStore.deleteByPortfolioId(portfolio.getId());
        portfolioFileUploader.delete(portfolio.getS3Key());
        return false;
    }

    // 요약 생성은 best-effort다 — 실패해도 READY 전환을 막지 않고, 재시도도 하지 않는다(HLT-11).
    private List<String> generateSummary(Portfolio portfolio, String maskedText) {
        try {
            List<String> bullets = portfolioContentSummarizer.summarize(portfolio.getId(), maskedText);
            return bullets == null ? List.of() : bullets;
        } catch (Exception e) {
            log.warn("[PORTFOLIO PROCESS] 콘텐츠 요약 생성 실패, 요약 없이 완료 처리함: portfolioId={}", portfolio.getId(), e);
            return List.of();
        }
    }

    private void failFileAndCleanupS3(Portfolio portfolio, String message) {
        if (isStillProcessing(portfolio.getId())) {
            portfolio.failFile(message);
            portfolioRepository.save(portfolio);
        }
        portfolioFileUploader.delete(portfolio.getS3Key());
    }

    private boolean isStillProcessing(UUID portfolioId) {
        return portfolioRepository.findById(portfolioId)
                .map(p -> !p.isDeleted() && p.getStatus() == PortfolioStatus.PROCESSING)
                .orElse(false);
    }
}
