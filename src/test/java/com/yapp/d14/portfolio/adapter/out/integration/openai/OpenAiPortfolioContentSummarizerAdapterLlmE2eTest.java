package com.yapp.d14.portfolio.adapter.out.integration.openai;

import com.yapp.d14.portfolio.application.port.out.PortfolioContentSummarizer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 포트폴리오 콘텐츠 요약(HLT-11) 프롬프트가 실제 OpenAI 호출에서
 * "평가가 아니라 요약"이라는 핵심 규칙(평가성 표현 금지, 가변 불릿 개수)을 지키는지 확인하는 e2e 테스트.
 *
 * 유효한 OPENAI_API_KEY가 필요하며 실제 호출로 비용이 발생한다.
 * 비용·비결정성 때문에 기본 test/integrationTest에서 제외되며(@Tag("llm-e2e")), ./gradlew llmE2eTest로만 실행한다.
 */
@Tag("llm-e2e")
@SpringBootTest
class OpenAiPortfolioContentSummarizerAdapterLlmE2eTest {

    private static final Logger log = LoggerFactory.getLogger(OpenAiPortfolioContentSummarizerAdapterLlmE2eTest.class);

    // 평가·감탄형 표현은 절대 쓰지 말라는 시스템 프롬프트 규칙이 실제로 지켜지는지 확인하는 금지어 목록.
    private static final List<String> FORBIDDEN_EVALUATIVE_WORDS = List.of(
            "우수한", "뛰어난", "효율적으로", "성공적으로", "완벽하게", "탁월한", "훌륭한"
    );

    private static final String SAMPLE_PORTFOLIO_TEXT = """
            홍길동 백엔드 개발자 포트폴리오

            [프로젝트 1] 이커머스 결제 시스템 리팩토링 (2024.03 ~ 2024.09)
            - Spring Boot, PostgreSQL, Redis 기반으로 기존 모놀리식 결제 모듈을 분리했다.
            - Redis 캐시를 도입해 결제 조회 API의 평균 응답 시간을 320ms에서 80ms로 줄였다.
            - 결제 실패 시 보상 트랜잭션을 처리하는 사가 패턴을 적용했다.

            [프로젝트 2] 사내 인증 모듈 고도화 (2023.06 ~ 2023.12)
            - JWT와 OAuth2 기반으로 카카오·구글·애플 소셜 로그인 3종을 통합했다.
            - Spring Security 필터 체인을 커스터마이징해 다중 소셜 제공자를 하나의 인터페이스로 처리했다.
            - 월간 활성 사용자 15만 명 규모의 인증 트래픽을 처리했다.

            [기술 스택] Java, Spring Boot, Spring Security, PostgreSQL, Redis, AWS S3, Kafka, Docker
            """.repeat(2);

    @Autowired
    private PortfolioContentSummarizer portfolioContentSummarizer;

    @Test
    void 실제_LLM으로_중립적_사실_서술형_불릿을_생성한다() {
        List<String> bullets = portfolioContentSummarizer.summarize(UUID.randomUUID(), SAMPLE_PORTFOLIO_TEXT);

        log.info("========== [LLM E2E] 포트폴리오 콘텐츠 요약 불릿 ==========");
        bullets.forEach(bullet -> log.info("- {}", bullet));
        log.info("================================================================");

        assertThat(bullets).isNotEmpty();
        assertThat(bullets.size()).isBetween(2, 6);
        bullets.forEach(bullet -> {
            assertThat(bullet).isNotBlank();
            // 마이페이지 요약 카드(Figma node 2798:293, 텍스트 영역 303px) 1~2줄 안에 들어오는 분량.
            assertThat(bullet.length()).isLessThanOrEqualTo(40);
            FORBIDDEN_EVALUATIVE_WORDS.forEach(forbidden ->
                    assertThat(bullet).doesNotContain(forbidden));
        });
    }
}
