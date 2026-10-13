package com.yapp.d14.portfolio.application.service;

import com.yapp.d14.portfolio.application.port.out.PortfolioEmbeddingStore;
import com.yapp.d14.portfolio.application.port.out.PortfolioFileUploader;
import com.yapp.d14.portfolio.application.port.out.PortfolioRepository;
import com.yapp.d14.portfolio.domain.Portfolio;
import com.yapp.d14.portfolio.domain.PortfolioStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PortfolioProcessingTimeoutHandlerTest {

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private PortfolioEmbeddingStore portfolioEmbeddingStore;

    @Mock
    private PortfolioFileUploader portfolioFileUploader;

    @InjectMocks
    private PortfolioProcessingTimeoutHandler portfolioProcessingTimeoutHandler;

    @Test
    void 처리_시간을_넘겼으면_FAILED_SYSTEM으로_전환하고_저장한다() {
        Portfolio stale = processingPortfolioCreatedAt(LocalDateTime.now().minusSeconds(21));
        given(portfolioRepository.findByIdForUpdate(stale.getId())).willReturn(Optional.of(stale));

        Portfolio result = portfolioProcessingTimeoutHandler.failAndCleanup(stale);

        assertThat(result.getStatus()).isEqualTo(PortfolioStatus.FAILED_SYSTEM);
        verify(portfolioRepository).save(stale);
    }

    @Test
    void 처리_시간을_넘겼으면_임베딩과_S3_원본을_함께_지운다() {
        Portfolio stale = processingPortfolioCreatedAt(LocalDateTime.now().minusSeconds(21));
        given(portfolioRepository.findByIdForUpdate(stale.getId())).willReturn(Optional.of(stale));

        portfolioProcessingTimeoutHandler.failAndCleanup(stale);

        verify(portfolioEmbeddingStore).deleteByPortfolioId(stale.getId());
        verify(portfolioFileUploader).delete(stale.getS3Key());
    }

    @Test
    void 소프트_삭제하지_않아_실패_사유가_계속_보인다() {
        Portfolio stale = processingPortfolioCreatedAt(LocalDateTime.now().minusSeconds(21));
        given(portfolioRepository.findByIdForUpdate(stale.getId())).willReturn(Optional.of(stale));

        Portfolio result = portfolioProcessingTimeoutHandler.failAndCleanup(stale);

        assertThat(result.isDeleted()).isFalse();
        assertThat(result.getDeletedAt()).isNull();
    }

    @Test
    void 아직_처리_시간_안이면_행을_잠그지_않는다() {
        Portfolio fresh = processingPortfolioCreatedAt(LocalDateTime.now().minusSeconds(5));

        Portfolio result = portfolioProcessingTimeoutHandler.failAndCleanup(fresh);

        assertThat(result).isSameAs(fresh);
        assertThat(result.getStatus()).isEqualTo(PortfolioStatus.PROCESSING);
        verify(portfolioRepository, never()).findByIdForUpdate(any());
        verify(portfolioRepository, never()).save(any());
        verifyNoInteractions(portfolioEmbeddingStore, portfolioFileUploader);
    }

    @Test
    void 락을_잡는_사이_처리가_완료됐으면_덮어쓰지_않고_최신_상태를_돌려준다() {
        Portfolio stale = processingPortfolioCreatedAt(LocalDateTime.now().minusSeconds(21));
        Portfolio completed = readyPortfolio(stale.getId());
        given(portfolioRepository.findByIdForUpdate(stale.getId())).willReturn(Optional.of(completed));

        Portfolio result = portfolioProcessingTimeoutHandler.failAndCleanup(stale);

        assertThat(result.getStatus()).isEqualTo(PortfolioStatus.READY);
        verify(portfolioRepository, never()).save(any());
        verifyNoInteractions(portfolioEmbeddingStore, portfolioFileUploader);
    }

    @Test
    void PROCESSING이_아니면_아무것도_하지_않는다() {
        Portfolio ready = readyPortfolio(UUID.randomUUID());

        Portfolio result = portfolioProcessingTimeoutHandler.failAndCleanup(ready);

        assertThat(result).isSameAs(ready);
        verify(portfolioRepository, never()).findByIdForUpdate(any());
        verify(portfolioRepository, never()).save(any());
        verifyNoInteractions(portfolioEmbeddingStore, portfolioFileUploader);
    }

    private Portfolio processingPortfolioCreatedAt(LocalDateTime createdAt) {
        return Portfolio.of(
                UUID.randomUUID(), UUID.randomUUID(), "resume.pdf", 1024, 5, "users/x/portfolios/x.pdf",
                PortfolioStatus.PROCESSING, "포트폴리오를 분석하고 있어요.", createdAt, null,
                false, false, null, List.of()
        );
    }

    private Portfolio readyPortfolio(UUID portfolioId) {
        return Portfolio.of(
                portfolioId, UUID.randomUUID(), "resume.pdf", 1024, 5, "users/x/portfolios/x.pdf",
                PortfolioStatus.READY, "포트폴리오 처리가 완료되었습니다.", LocalDateTime.now().minusSeconds(20),
                LocalDateTime.now(), false, false, null, List.of()
        );
    }
}
