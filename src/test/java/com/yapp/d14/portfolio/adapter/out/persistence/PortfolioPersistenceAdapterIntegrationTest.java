package com.yapp.d14.portfolio.adapter.out.persistence;

import com.yapp.d14.portfolio.application.port.out.PortfolioRepository;
import com.yapp.d14.portfolio.domain.Portfolio;
import com.yapp.d14.portfolio.domain.PortfolioStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

// acquireRegistrationLock의 pg_advisory_xact_lock 네이티브 쿼리가 실제 Postgres 드라이버·캐스팅에서
// 문법 오류 없이 동작하는지 검증한다(네이티브 쿼리는 Hibernate 부트스트랩 시점에 검증되지 않는다).
@Tag("integration")
@SpringBootTest
class PortfolioPersistenceAdapterIntegrationTest {

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;

    @AfterEach
    void cleanUp() {
        if (userId == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM portfolios WHERE user_id = ?", userId);
    }

    @Test
    void 등록_락을_정상적으로_획득한다() {
        assertThatCode(() -> portfolioRepository.acquireRegistrationLock(UUID.randomUUID()))
                .doesNotThrowAnyException();
    }

    @Test
    void 등록은_월_경계_이전이고_완료가_월_경계_이후인_교체건도_해당_달_이력으로_잡힌다() {
        userId = UUID.randomUUID();
        LocalDateTime monthStart = LocalDateTime.of(2024, 2, 1, 0, 0);
        Portfolio monthBoundaryReplacement = Portfolio.of(
                UUID.randomUUID(), userId, "resume.pdf", 100L, 5, "s3-key",
                PortfolioStatus.READY, "완료",
                monthStart.minusHours(1), monthStart.plusMinutes(30),
                true, false, null, List.of()
        );
        portfolioRepository.save(monthBoundaryReplacement);

        boolean result = portfolioRepository.existsReplacementSince(userId, monthStart);

        assertThat(result).isTrue();
    }

    @Test
    void 삭제는_월_경계_이후에_삭제된_건만_해당_달_이력으로_잡힌다() {
        userId = UUID.randomUUID();
        LocalDateTime monthStart = LocalDateTime.of(2024, 2, 1, 0, 0);
        Portfolio deletedBeforeMonth = Portfolio.of(
                UUID.randomUUID(), userId, "resume.pdf", 100L, 5, "s3-key",
                PortfolioStatus.READY, "완료",
                monthStart.minusDays(10), monthStart.minusDays(10),
                false, true, monthStart.minusHours(1), List.of()
        );
        portfolioRepository.save(deletedBeforeMonth);

        assertThat(portfolioRepository.existsDeletionSince(userId, monthStart)).isFalse();

        Portfolio deletedInMonth = Portfolio.of(
                UUID.randomUUID(), userId, "resume2.pdf", 100L, 5, "s3-key-2",
                PortfolioStatus.READY, "완료",
                monthStart.plusDays(1), monthStart.plusDays(1),
                false, true, monthStart.plusDays(2), List.of()
        );
        portfolioRepository.save(deletedInMonth);

        assertThat(portfolioRepository.existsDeletionSince(userId, monthStart)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = PortfolioStatus.class, names = {"CANCELLED", "FAILED_FILE", "FAILED_SYSTEM"})
    void 완료되지_않은_건의_삭제는_해당_달_삭제_이력으로_잡히지_않는다(PortfolioStatus status) {
        userId = UUID.randomUUID();
        LocalDateTime monthStart = LocalDateTime.of(2024, 2, 1, 0, 0);
        Portfolio notCompleted = Portfolio.of(
                UUID.randomUUID(), userId, "resume.pdf", 100L, 5, "s3-key",
                status, "미완료",
                monthStart.plusDays(1), null,
                false, true, monthStart.plusDays(1), List.of()
        );
        portfolioRepository.save(notCompleted);

        assertThat(portfolioRepository.existsDeletionSince(userId, monthStart)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = PortfolioStatus.class, names = {"PROCESSING", "CANCELLED", "FAILED_FILE", "FAILED_SYSTEM"})
    void 완료된_적_없는_유저의_다음_업로드는_재업로드로_잡히지_않는다(PortfolioStatus status) {
        userId = UUID.randomUUID();
        Portfolio notCompleted = Portfolio.of(
                UUID.randomUUID(), userId, "resume.pdf", 100L, 5, "s3-key",
                status, "미완료",
                LocalDateTime.now(), null,
                false, status != PortfolioStatus.PROCESSING, LocalDateTime.now(), List.of()
        );
        portfolioRepository.save(notCompleted);

        assertThat(portfolioRepository.existsCompletedByUserId(userId)).isFalse();
    }

    @Test
    void 완료됐다가_삭제된_포트폴리오가_있으면_다음_업로드는_재업로드로_잡힌다() {
        userId = UUID.randomUUID();
        Portfolio deletedReady = Portfolio.of(
                UUID.randomUUID(), userId, "resume.pdf", 100L, 5, "s3-key",
                PortfolioStatus.READY, "완료",
                LocalDateTime.now(), LocalDateTime.now(),
                false, true, LocalDateTime.now(), List.of()
        );
        portfolioRepository.save(deletedReady);

        assertThat(portfolioRepository.existsCompletedByUserId(userId)).isTrue();
    }

    @Test
    void 요약_불릿을_저장하고_조회하면_순서와_개수가_보존된다() {
        userId = UUID.randomUUID();
        Portfolio ready = Portfolio.of(
                UUID.randomUUID(), userId, "resume.pdf", 100L, 5, "s3-key",
                PortfolioStatus.READY, "완료",
                LocalDateTime.now(), LocalDateTime.now(),
                false, false, null, List.of("첫째", "둘째", "셋째")
        );
        portfolioRepository.save(ready);

        Portfolio found = portfolioRepository.findById(ready.getId()).orElseThrow();

        assertThat(found.getSummaryBullets()).containsExactly("첫째", "둘째", "셋째");
    }
}
