package com.yapp.d14.portfolio.application.service;

import com.yapp.d14.interview.application.port.in.InterviewSessionInProgressCheckUseCase;
import com.yapp.d14.portfolio.application.port.in.result.PortfolioDeleteResult;
import com.yapp.d14.portfolio.application.port.out.PortfolioEmbeddingStore;
import com.yapp.d14.portfolio.application.port.out.PortfolioFileUploader;
import com.yapp.d14.portfolio.application.port.out.PortfolioRepository;
import com.yapp.d14.portfolio.domain.Portfolio;
import com.yapp.d14.portfolio.domain.PortfolioStatus;
import com.yapp.d14.portfolio.exception.PortfolioErrorCode;
import com.yapp.d14.portfolio.exception.PortfolioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PortfolioDeleteServiceTest {

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private PortfolioFileUploader portfolioFileUploader;

    @Mock
    private PortfolioEmbeddingStore portfolioEmbeddingStore;

    @Mock
    private InterviewSessionInProgressCheckUseCase interviewSessionInProgressCheckUseCase;

    @InjectMocks
    private PortfolioDeleteService portfolioDeleteService;

    private UUID userId;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        portfolio = Portfolio.create(
                UUID.randomUUID(), userId, "resume.pdf", 1024, 5,
                "users/%s/portfolios/%s/test.pdf".formatted(userId, UUID.randomUUID()), false
        );
    }

    private Portfolio portfolioWith(PortfolioStatus status) {
        LocalDateTime now = LocalDateTime.now();
        return Portfolio.of(
                UUID.randomUUID(), userId, "resume.pdf", 1024L, 5,
                "users/%s/portfolios/%s/test.pdf".formatted(userId, UUID.randomUUID()),
                status, "메시지", now,
                status == PortfolioStatus.READY ? now : null,
                false, false, null, List.of()
        );
    }

    @Test
    void 소유자가_아니면_예외를_던지고_삭제하지_않는다() {
        given(portfolioRepository.findById(portfolio.getId())).willReturn(Optional.of(portfolio));
        UUID otherUserId = UUID.randomUUID();

        assertThatThrownBy(() -> portfolioDeleteService.delete(otherUserId, portfolio.getId()))
                .isInstanceOf(PortfolioException.class);

        verify(portfolioRepository, never()).save(any());
    }

    @Test
    void 삭제하면_소프트_삭제로_기록하고_벡터_S3_파일은_실제로_정리한다() {
        given(portfolioRepository.findById(portfolio.getId())).willReturn(Optional.of(portfolio));
        given(portfolioRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        PortfolioDeleteResult result = portfolioDeleteService.delete(userId, portfolio.getId());

        assertThat(result.portfolioId()).isEqualTo(portfolio.getId());
        assertThat(portfolio.isDeleted()).isTrue();
        assertThat(portfolio.getStatus()).isEqualTo(PortfolioStatus.CANCELLED);
        verify(portfolioRepository).save(portfolio);
        verify(portfolioRepository).acquirePortfolioLock(portfolio.getId());
        verify(portfolioEmbeddingStore).deleteByPortfolioId(portfolio.getId());
        verify(portfolioFileUploader).delete(portfolio.getS3Key());
    }

    @Test
    void 진행중인_면접이_사용_중이면_삭제하지_않는다() {
        given(portfolioRepository.findById(portfolio.getId())).willReturn(Optional.of(portfolio));
        given(interviewSessionInProgressCheckUseCase.existsInProgress(portfolio.getId())).willReturn(true);

        assertThatThrownBy(() -> portfolioDeleteService.delete(userId, portfolio.getId()))
                .isInstanceOf(PortfolioException.class)
                .extracting(e -> ((PortfolioException) e).getErrorCode())
                .isEqualTo(PortfolioErrorCode.PORTFOLIO_DELETE_BLOCKED_BY_INTERVIEW);

        verify(portfolioRepository, never()).save(any());
        verify(portfolioEmbeddingStore, never()).deleteByPortfolioId(any());
    }

    @Test
    void 이번달_삭제_기회를_이미_썼으면_완료된_포트폴리오를_삭제하지_않는다() {
        Portfolio ready = portfolioWith(PortfolioStatus.READY);
        given(portfolioRepository.findById(ready.getId())).willReturn(Optional.of(ready));
        given(portfolioRepository.existsDeletionSince(any(), any())).willReturn(true);

        assertThatThrownBy(() -> portfolioDeleteService.delete(userId, ready.getId()))
                .isInstanceOf(PortfolioException.class)
                .extracting(e -> ((PortfolioException) e).getErrorCode())
                .isEqualTo(PortfolioErrorCode.DELETE_LIMIT_EXCEEDED);

        verify(portfolioRepository, never()).save(any());
        verify(portfolioEmbeddingStore, never()).deleteByPortfolioId(any());
    }

    @Test
    void 삭제_가능_여부는_재업로드_이력과_무관하게_삭제_이력만_본다() {
        Portfolio ready = portfolioWith(PortfolioStatus.READY);
        given(portfolioRepository.findById(ready.getId())).willReturn(Optional.of(ready));
        given(portfolioRepository.existsDeletionSince(any(), any())).willReturn(false);
        given(portfolioRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        PortfolioDeleteResult result = portfolioDeleteService.delete(userId, ready.getId());

        assertThat(result.portfolioId()).isEqualTo(ready.getId());
        assertThat(ready.isDeleted()).isTrue();
        verify(portfolioRepository).save(ready);
    }

    @Test
    void 업로드_중_취소는_삭제_기회를_이미_썼어도_허용하고_기회를_소진하지_않는다() {
        given(portfolioRepository.findById(portfolio.getId())).willReturn(Optional.of(portfolio));
        given(portfolioRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        PortfolioDeleteResult result = portfolioDeleteService.delete(userId, portfolio.getId());

        assertThat(result.portfolioId()).isEqualTo(portfolio.getId());
        assertThat(portfolio.getStatus()).isEqualTo(PortfolioStatus.CANCELLED);
        verify(portfolioRepository, never()).existsDeletionSince(any(), any());
        verify(portfolioRepository).save(portfolio);
    }

    @ParameterizedTest
    @EnumSource(value = PortfolioStatus.class, names = {"FAILED_FILE", "FAILED_SYSTEM"})
    void 실패한_포트폴리오_삭제는_삭제_기회를_이미_썼어도_허용하고_기회를_소진하지_않는다(PortfolioStatus status) {
        Portfolio failed = portfolioWith(status);
        given(portfolioRepository.findById(failed.getId())).willReturn(Optional.of(failed));
        given(portfolioRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        PortfolioDeleteResult result = portfolioDeleteService.delete(userId, failed.getId());

        assertThat(result.portfolioId()).isEqualTo(failed.getId());
        assertThat(failed.isDeleted()).isTrue();
        assertThat(failed.getStatus()).isEqualTo(status);
        verify(portfolioRepository, never()).existsDeletionSince(any(), any());
        verify(portfolioRepository).save(failed);
    }
}
