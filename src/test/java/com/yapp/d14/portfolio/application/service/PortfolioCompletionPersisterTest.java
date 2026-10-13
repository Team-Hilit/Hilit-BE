package com.yapp.d14.portfolio.application.service;

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

@ExtendWith(MockitoExtension.class)
class PortfolioCompletionPersisterTest {

    @Mock
    private PortfolioRepository portfolioRepository;

    @InjectMocks
    private PortfolioCompletionPersister portfolioCompletionPersister;

    @Test
    void 여전히_PROCESSING이면_READY로_전환하고_저장한다() {
        Portfolio processing = portfolio(PortfolioStatus.PROCESSING, false);
        given(portfolioRepository.findByIdForUpdate(processing.getId())).willReturn(Optional.of(processing));

        boolean completed = portfolioCompletionPersister.completeIfStillProcessing(processing.getId(), List.of());

        assertThat(completed).isTrue();
        assertThat(processing.getStatus()).isEqualTo(PortfolioStatus.READY);
        assertThat(processing.getUploadedAt()).isNotNull();
        verify(portfolioRepository).save(processing);
    }

    @Test
    void 여전히_PROCESSING이면_READY_전환과_함께_요약_불릿도_저장한다() {
        Portfolio processing = portfolio(PortfolioStatus.PROCESSING, false);
        given(portfolioRepository.findByIdForUpdate(processing.getId())).willReturn(Optional.of(processing));

        boolean completed = portfolioCompletionPersister.completeIfStillProcessing(
                processing.getId(), List.of("불릿1", "불릿2"));

        assertThat(completed).isTrue();
        assertThat(processing.getSummaryBullets()).containsExactly("불릿1", "불릿2");
        verify(portfolioRepository).save(processing);
    }

    @Test
    void 타임아웃으로_이미_실패_처리됐으면_덮어쓰지_않는다() {
        Portfolio failed = portfolio(PortfolioStatus.FAILED_SYSTEM, false);
        given(portfolioRepository.findByIdForUpdate(failed.getId())).willReturn(Optional.of(failed));

        boolean completed = portfolioCompletionPersister.completeIfStillProcessing(failed.getId(), List.of("불릿1"));

        assertThat(completed).isFalse();
        assertThat(failed.getStatus()).isEqualTo(PortfolioStatus.FAILED_SYSTEM);
        assertThat(failed.getSummaryBullets()).isEmpty();
        verify(portfolioRepository, never()).save(any());
    }

    @Test
    void 사용자가_취소해_삭제됐으면_완료_처리하지_않는다() {
        Portfolio cancelled = portfolio(PortfolioStatus.CANCELLED, true);
        given(portfolioRepository.findByIdForUpdate(cancelled.getId())).willReturn(Optional.of(cancelled));

        boolean completed = portfolioCompletionPersister.completeIfStillProcessing(cancelled.getId(), List.of());

        assertThat(completed).isFalse();
        verify(portfolioRepository, never()).save(any());
    }

    @Test
    void 포트폴리오가_없으면_완료_처리하지_않는다() {
        UUID portfolioId = UUID.randomUUID();
        given(portfolioRepository.findByIdForUpdate(portfolioId)).willReturn(Optional.empty());

        boolean completed = portfolioCompletionPersister.completeIfStillProcessing(portfolioId, List.of());

        assertThat(completed).isFalse();
        verify(portfolioRepository, never()).save(any());
    }

    private Portfolio portfolio(PortfolioStatus status, boolean deleted) {
        return Portfolio.of(
                UUID.randomUUID(), UUID.randomUUID(), "resume.pdf", 1024, 5, "users/x/portfolios/x.pdf",
                status, "message", LocalDateTime.now().minusSeconds(20), null,
                false, deleted, deleted ? LocalDateTime.now() : null, List.of()
        );
    }
}
