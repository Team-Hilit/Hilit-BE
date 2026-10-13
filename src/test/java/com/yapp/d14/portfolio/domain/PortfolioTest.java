package com.yapp.d14.portfolio.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioTest {

    private final Portfolio portfolio = Portfolio.create(
            UUID.randomUUID(), UUID.randomUUID(), "resume.pdf", 1024, 5, "users/x/portfolios/x/x.pdf", false
    );

    @Test
    void 추출된_텍스트가_300자_이상이면_충분하다고_판단한다() {
        String text = "이 문장은 300자를 충분히 넘기는 추출된 텍스트인지 확인하기 위한 문장입니다. ".repeat(15);

        assertThat(portfolio.hasEnoughExtractedText(text)).isTrue();
    }

    @Test
    void 추출된_텍스트가_300자_미만이면_부족하다고_판단한다() {
        String text = "너무 짧은 텍스트";

        assertThat(portfolio.hasEnoughExtractedText(text)).isFalse();
    }

    @Test
    void 앞뒤_공백은_길이_판단에서_제외한다() {
        String text = "   짧음   ";

        assertThat(portfolio.hasEnoughExtractedText(text)).isFalse();
    }

    @Test
    void 재업로드로_생성되면_replacement가_true다() {
        Portfolio replacement = Portfolio.create(
                UUID.randomUUID(), UUID.randomUUID(), "resume.pdf", 1024, 5, "users/x/portfolios/x/x.pdf", true
        );

        assertThat(replacement.isReplacement()).isTrue();
    }

    @Test
    void softDelete하면_deleted_상태와_시각이_기록된다() {
        assertThat(portfolio.isDeleted()).isFalse();

        portfolio.softDelete();

        assertThat(portfolio.isDeleted()).isTrue();
        assertThat(portfolio.getDeletedAt()).isNotNull();
    }

    @Test
    void PROCESSING_중에_softDelete하면_CANCELLED로_전환된다() {
        portfolio.softDelete();

        assertThat(portfolio.getStatus()).isEqualTo(PortfolioStatus.CANCELLED);
    }

    @Test
    void 이미_종료된_상태에서_softDelete해도_상태는_유지된다() {
        Portfolio ready = Portfolio.of(
                UUID.randomUUID(), UUID.randomUUID(), "resume.pdf", 1024, 5, "users/x/portfolios/x/x.pdf",
                PortfolioStatus.READY, "완료", LocalDateTime.now(), LocalDateTime.now(),
                false, false, null, List.of()
        );

        ready.softDelete();

        assertThat(ready.getStatus()).isEqualTo(PortfolioStatus.READY);
        assertThat(ready.isDeleted()).isTrue();
    }

    @Test
    void 완료된_포트폴리오_삭제만_월_삭제_기회를_소진한다() {
        Portfolio ready = Portfolio.of(
                UUID.randomUUID(), UUID.randomUUID(), "resume.pdf", 1024, 5, "users/x/portfolios/x/x.pdf",
                PortfolioStatus.READY, "완료", LocalDateTime.now(), LocalDateTime.now(),
                false, false, null, List.of()
        );

        assertThat(ready.countsTowardDeletionLimit()).isTrue();
    }

    @Test
    void 완료되지_않은_포트폴리오_삭제는_월_삭제_기회를_소진하지_않는다() {
        assertThat(portfolio.countsTowardDeletionLimit()).isFalse();

        portfolio.softDelete();

        assertThat(portfolio.getStatus()).isEqualTo(PortfolioStatus.CANCELLED);
        assertThat(portfolio.countsTowardDeletionLimit()).isFalse();
    }

    @Test
    void PROCESSING_상태로_생성된지_20초가_지나면_FAILED_SYSTEM으로_전환한다() {
        Portfolio staleProcessing = processingPortfolioCreatedAt(LocalDateTime.now().minusSeconds(21));

        boolean timedOut = staleProcessing.failIfProcessingTimedOut();

        assertThat(timedOut).isTrue();
        assertThat(staleProcessing.getStatus()).isEqualTo(PortfolioStatus.FAILED_SYSTEM);
    }

    @Test
    void PROCESSING_상태로_생성된지_20초가_지나지_않았으면_상태를_유지한다() {
        Portfolio freshProcessing = processingPortfolioCreatedAt(LocalDateTime.now().minusSeconds(5));

        boolean timedOut = freshProcessing.failIfProcessingTimedOut();

        assertThat(timedOut).isFalse();
        assertThat(freshProcessing.getStatus()).isEqualTo(PortfolioStatus.PROCESSING);
    }

    @Test
    void PROCESSING_상태가_아니면_시간이_지나도_상태를_유지한다() {
        Portfolio ready = Portfolio.of(
                UUID.randomUUID(), UUID.randomUUID(), "resume.pdf", 1024, 5, "users/x/portfolios/x/x.pdf",
                PortfolioStatus.READY, "완료", LocalDateTime.now().minusSeconds(100), LocalDateTime.now(),
                false, false, null, List.of()
        );

        boolean timedOut = ready.failIfProcessingTimedOut();

        assertThat(timedOut).isFalse();
        assertThat(ready.getStatus()).isEqualTo(PortfolioStatus.READY);
    }

    @Test
    void 생성_직후에는_요약_불릿이_빈_리스트다() {
        assertThat(portfolio.getSummaryBullets()).isEmpty();
    }

    @Test
    void updateSummaryBullets로_상태와_무관하게_요약_불릿을_설정할_수_있다() {
        portfolio.updateSummaryBullets(List.of("불릿1", "불릿2"));

        assertThat(portfolio.getSummaryBullets()).containsExactly("불릿1", "불릿2");
        assertThat(portfolio.getStatus()).isEqualTo(PortfolioStatus.PROCESSING);
    }

    @Test
    void updateSummaryBullets에_null을_전달하면_빈_리스트가_된다() {
        portfolio.updateSummaryBullets(List.of("불릿1"));

        portfolio.updateSummaryBullets(null);

        assertThat(portfolio.getSummaryBullets()).isEmpty();
    }

    private Portfolio processingPortfolioCreatedAt(LocalDateTime createdAt) {
        return Portfolio.of(
                UUID.randomUUID(), UUID.randomUUID(), "resume.pdf", 1024, 5, "users/x/portfolios/x/x.pdf",
                PortfolioStatus.PROCESSING, "포트폴리오를 분석하고 있어요.", createdAt, null,
                false, false, null, List.of()
        );
    }
}
