package com.yapp.d14.portfolio.application.service;

import com.yapp.d14.interview.application.port.in.InterviewSessionInProgressCheckUseCase;
import com.yapp.d14.portfolio.application.port.in.result.PortfolioListResult;
import com.yapp.d14.portfolio.application.port.in.result.PortfolioSummary;
import com.yapp.d14.portfolio.application.port.out.PortfolioRepository;
import com.yapp.d14.portfolio.domain.Portfolio;
import com.yapp.d14.portfolio.domain.PortfolioStatus;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PortfolioQueryServiceTest {

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private PortfolioProcessingTimeoutHandler portfolioProcessingTimeoutHandler;

    @Mock
    private InterviewSessionInProgressCheckUseCase interviewSessionInProgressCheckUseCase;

    @InjectMocks
    private PortfolioQueryService portfolioQueryService;

    private UUID userId;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        portfolio = Portfolio.create(
                UUID.randomUUID(), userId, "resume.pdf", 1024, 5,
                "users/%s/portfolios/%s/test.pdf".formatted(userId, UUID.randomUUID()), false
        );
        // 타임아웃이 아닌 경우 핸들러는 넘겨받은 포트폴리오를 그대로 돌려준다.
        lenient().when(portfolioProcessingTimeoutHandler.failAndCleanup(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void 이번달_재업로드_이력이_없으면_replaceAvailable이_true이고_nextAvailableAt은_null이다() {
        given(portfolioRepository.findAllActiveByUserId(userId)).willReturn(List.of(portfolio));
        given(portfolioRepository.existsReplacementSince(any(), any())).willReturn(false);

        PortfolioListResult result = portfolioQueryService.getList(userId);

        assertThat(result.portfolios()).hasSize(1);
        assertThat(result.replaceAvailable()).isTrue();
        assertThat(result.nextAvailableAt()).isNull();
    }

    @Test
    void 이번달_재업로드_이력이_있으면_replaceAvailable이_false이고_다음달_1일_0시를_반환한다() {
        given(portfolioRepository.findAllActiveByUserId(userId)).willReturn(List.of(portfolio));
        given(portfolioRepository.existsReplacementSince(any(), any())).willReturn(true);

        PortfolioListResult result = portfolioQueryService.getList(userId);

        assertThat(result.replaceAvailable()).isFalse();
        assertThat(result.nextAvailableAt()).isEqualTo(PortfolioReplacementPolicy.nextMonthStart());
    }

    @Test
    void 재업로드_이력이_있어도_삭제_이력이_없으면_deleteAvailable은_true다() {
        given(portfolioRepository.findAllActiveByUserId(userId)).willReturn(List.of(portfolio));
        given(portfolioRepository.existsReplacementSince(any(), any())).willReturn(true);
        given(portfolioRepository.existsDeletionSince(any(), any())).willReturn(false);

        PortfolioListResult result = portfolioQueryService.getList(userId);

        assertThat(result.replaceAvailable()).isFalse();
        assertThat(result.deleteAvailable()).isTrue();
        assertThat(result.nextDeleteAvailableAt()).isNull();
    }

    @Test
    void 삭제_이력이_있어도_재업로드_이력이_없으면_replaceAvailable은_true다() {
        given(portfolioRepository.findAllActiveByUserId(userId)).willReturn(List.of(portfolio));
        given(portfolioRepository.existsReplacementSince(any(), any())).willReturn(false);
        given(portfolioRepository.existsDeletionSince(any(), any())).willReturn(true);

        PortfolioListResult result = portfolioQueryService.getList(userId);

        assertThat(result.deleteAvailable()).isFalse();
        assertThat(result.nextDeleteAvailableAt()).isEqualTo(PortfolioReplacementPolicy.nextMonthStart());
        assertThat(result.replaceAvailable()).isTrue();
        assertThat(result.nextAvailableAt()).isNull();
    }

    @Test
    void 포트폴리오가_없어도_재업로드_삭제_가능_여부는_계정_단위로_내려간다() {
        given(portfolioRepository.findAllActiveByUserId(userId)).willReturn(List.of());
        given(portfolioRepository.existsReplacementSince(any(), any())).willReturn(false);
        given(portfolioRepository.existsDeletionSince(any(), any())).willReturn(true);

        PortfolioListResult result = portfolioQueryService.getList(userId);

        assertThat(result.portfolios()).isEmpty();
        assertThat(result.replaceAvailable()).isTrue();
        assertThat(result.deleteAvailable()).isFalse();
        assertThat(result.nextDeleteAvailableAt()).isEqualTo(PortfolioReplacementPolicy.nextMonthStart());
    }

    @Test
    void 해당_포트폴리오로_진행중인_면접이_있으면_interviewInProgress가_true다() {
        given(portfolioRepository.findAllActiveByUserId(userId)).willReturn(List.of(portfolio));
        given(portfolioRepository.existsReplacementSince(any(), any())).willReturn(false);
        given(interviewSessionInProgressCheckUseCase.existsInProgress(portfolio.getId())).willReturn(true);

        PortfolioListResult result = portfolioQueryService.getList(userId);

        assertThat(result.portfolios().get(0).interviewInProgress()).isTrue();
    }

    @Test
    void 상태_조회_시_처리_시간_초과_여부를_확인한다() {
        Portfolio stale = processingPortfolioCreatedAt(LocalDateTime.now().minusSeconds(21));
        given(portfolioRepository.findById(stale.getId())).willReturn(Optional.of(stale));

        portfolioQueryService.getStatus(stale.getUserId(), stale.getId());

        verify(portfolioProcessingTimeoutHandler).failAndCleanup(stale);
    }

    @Test
    void 마지막_시도가_실패였으면_목록에_노출한다() {
        given(portfolioRepository.findAllActiveByUserId(userId)).willReturn(List.of());
        given(portfolioRepository.findLatestByUserId(userId)).willReturn(Optional.of(failedPortfolio()));
        given(portfolioRepository.existsReplacementSince(any(), any())).willReturn(false);

        PortfolioListResult result = portfolioQueryService.getList(userId);

        assertThat(result.portfolios()).hasSize(1);
        assertThat(result.portfolios().get(0).status()).isEqualTo(PortfolioStatus.FAILED_FILE);
    }

    @Test
    void 실재하는_포트폴리오가_있으면_지난_실패는_조회하지_않는다() {
        given(portfolioRepository.findAllActiveByUserId(userId)).willReturn(List.of(portfolio));
        given(portfolioRepository.existsReplacementSince(any(), any())).willReturn(false);

        PortfolioListResult result = portfolioQueryService.getList(userId);

        assertThat(result.portfolios()).hasSize(1);
        assertThat(result.portfolios().get(0).portfolioId()).isEqualTo(portfolio.getId());
        verify(portfolioRepository, never()).findLatestByUserId(any());
    }

    @Test
    void 마지막_시도가_삭제된_READY면_그_이전_실패는_되살아나지_않는다() {
        given(portfolioRepository.findAllActiveByUserId(userId)).willReturn(List.of());
        given(portfolioRepository.findLatestByUserId(userId)).willReturn(Optional.of(deletedReadyPortfolio()));
        given(portfolioRepository.existsReplacementSince(any(), any())).willReturn(false);

        PortfolioListResult result = portfolioQueryService.getList(userId);

        assertThat(result.portfolios()).isEmpty();
    }

    @Test
    void 마지막_시도가_실패였어도_이미_삭제됐으면_노출하지_않는다() {
        given(portfolioRepository.findAllActiveByUserId(userId)).willReturn(List.of());
        given(portfolioRepository.findLatestByUserId(userId)).willReturn(Optional.of(deletedFailedPortfolio()));
        given(portfolioRepository.existsReplacementSince(any(), any())).willReturn(false);

        PortfolioListResult result = portfolioQueryService.getList(userId);

        assertThat(result.portfolios()).isEmpty();
    }

    @Test
    void 포트폴리오_이력이_아예_없으면_빈_목록이다() {
        given(portfolioRepository.findAllActiveByUserId(userId)).willReturn(List.of());
        given(portfolioRepository.findLatestByUserId(userId)).willReturn(Optional.empty());
        given(portfolioRepository.existsReplacementSince(any(), any())).willReturn(false);

        PortfolioListResult result = portfolioQueryService.getList(userId);

        assertThat(result.portfolios()).isEmpty();
    }

    @Test
    void 목록_조회_시_항목마다_처리_시간_초과_여부를_확인한다() {
        Portfolio stale = processingPortfolioCreatedAt(LocalDateTime.now().minusSeconds(21));
        given(portfolioRepository.findAllActiveByUserId(stale.getUserId())).willReturn(List.of(stale));
        given(portfolioRepository.existsReplacementSince(any(), any())).willReturn(false);

        portfolioQueryService.getList(stale.getUserId());

        verify(portfolioProcessingTimeoutHandler).failAndCleanup(stale);
    }

    @Test
    void 목록_조회시_콘텐츠_요약_불릿을_함께_내려준다() {
        portfolio.updateSummaryBullets(List.of("불릿1", "불릿2"));
        given(portfolioRepository.findAllActiveByUserId(userId)).willReturn(List.of(portfolio));
        given(portfolioRepository.existsReplacementSince(any(), any())).willReturn(false);

        PortfolioListResult result = portfolioQueryService.getList(userId);

        assertThat(result.portfolios().get(0).contentSummaryBullets()).containsExactly("불릿1", "불릿2");
    }

    @Test
    void 상태_조회시_콘텐츠_요약_불릿을_함께_내려준다() {
        portfolio.updateSummaryBullets(List.of("불릿1", "불릿2"));
        given(portfolioRepository.findById(portfolio.getId())).willReturn(Optional.of(portfolio));

        var result = portfolioQueryService.getStatus(portfolio.getUserId(), portfolio.getId());

        assertThat(result.contentSummaryBullets()).containsExactly("불릿1", "불릿2");
    }

    private Portfolio failedPortfolio() {
        return Portfolio.of(
                UUID.randomUUID(), userId, "broken.pdf", 1024, 5, "users/x/portfolios/x/x.pdf",
                PortfolioStatus.FAILED_FILE, "파일이 손상되었어요.", LocalDateTime.now().minusMinutes(10), null,
                false, false, null, List.of()
        );
    }

    private Portfolio deletedFailedPortfolio() {
        return Portfolio.of(
                UUID.randomUUID(), userId, "broken.pdf", 1024, 5, "users/x/portfolios/x/x.pdf",
                PortfolioStatus.FAILED_FILE, "파일이 손상되었어요.", LocalDateTime.now().minusMinutes(10), null,
                false, true, LocalDateTime.now(), List.of()
        );
    }

    private Portfolio deletedReadyPortfolio() {
        return Portfolio.of(
                UUID.randomUUID(), userId, "resume.pdf", 1024, 5, "users/x/portfolios/x/x.pdf",
                PortfolioStatus.READY, "포트폴리오 처리가 완료되었습니다.", LocalDateTime.now().minusMinutes(5),
                LocalDateTime.now().minusMinutes(4), false, true, LocalDateTime.now(), List.of()
        );
    }

    private Portfolio processingPortfolioCreatedAt(LocalDateTime createdAt) {
        UUID ownerId = UUID.randomUUID();
        return Portfolio.of(
                UUID.randomUUID(), ownerId, "resume.pdf", 1024, 5, "users/x/portfolios/x/x.pdf",
                PortfolioStatus.PROCESSING, "포트폴리오를 분석하고 있어요.", createdAt, null,
                false, false, null, List.of()
        );
    }
}
