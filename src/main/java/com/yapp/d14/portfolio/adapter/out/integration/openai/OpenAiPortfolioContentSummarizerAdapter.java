package com.yapp.d14.portfolio.adapter.out.integration.openai;

import com.yapp.d14.common.metrics.AiCallMetrics;
import com.yapp.d14.common.metrics.AiCallStage;
import com.yapp.d14.portfolio.application.port.out.PortfolioContentSummarizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
class OpenAiPortfolioContentSummarizerAdapter implements PortfolioContentSummarizer {

    private static final String SYSTEM_PROMPT = """
            당신은 지원자의 포트폴리오 원문에서 사실을 있는 그대로 추려 요약하는 역할입니다.
            목적은 "평가"가 아니라 "요약"입니다 — 좋다·나쁘다는 판단을 하지 않습니다.

            규칙:
            - 포트폴리오에 적힌 사실(수행한 업무, 사용한 기술, 만든 결과물 등)만 중립적으로 서술하세요.
            - "우수한", "뛰어난", "효율적으로 개선", "성공적으로" 같은 평가·감탄형 표현은 절대 쓰지 마세요.
            - "~경험", "~수행", "~구현", "~참여" 등 관찰·사실 서술형 어미만 사용하세요.
            - 포트폴리오 내용 분량에 맞춰 2개에서 6개 사이의 불릿만 만드세요. 내용이 부족하면 2개만 만들어도 되고, 억지로 6개를 채우지 마세요.
            - 각 불릿은 한 문장, 한국어로, 40자 이내로 작성하세요.
            - 프로젝트 카드 단위로 나누지 말고, 포트폴리오 전체를 관통하는 사실만 불릿으로 나열하세요.
            - 이름·이메일·전화번호 등 개인정보는 절대 포함하지 마세요.

            출력은 다른 설명 없이 JSON 배열 하나만 반환하세요.
            예: ["백엔드 API 서버 설계 및 구현 경험", "Kafka를 활용한 비동기 메시징 처리 수행"]
            """;

    // 불릿 개수 상한. 프롬프트로도 "2~6개"를 지시하지만, 모델 드리프트·비결정성에 대비해
    // production 경로에서도 상한을 코드로 강제한다(시스템 프롬프트 자연어 지시에만 의존하지 않는다).
    private static final int MAX_BULLETS = 6;
    // 마이페이지 요약 카드 폭(335px, 좌우 패딩 16px씩 제외 시 텍스트 영역 303px, 14px 폰트) 기준으로
    // 1~2줄 안에 들어오는 분량. Figma AI 초안(node 2798:293) 예시 불릿 최장 29자보다 여유를 둔 상한.
    private static final int MAX_BULLET_LENGTH = 40;
    private static final String TRUNCATION_SUFFIX = "…";

    private final ChatClient chatClient;
    private final AiCallMetrics aiCallMetrics;

    OpenAiPortfolioContentSummarizerAdapter(
            @Qualifier("openAiChatModel") ChatModel chatModel,
            AiCallMetrics aiCallMetrics
    ) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.aiCallMetrics = aiCallMetrics;
    }

    @Override
    public List<String> summarize(UUID portfolioId, String maskedText) {
        try {
            return aiCallMetrics.record(AiCallStage.PORTFOLIO_SUMMARY, () -> {
                List<String> bullets = chatClient.prompt()
                        .system(SYSTEM_PROMPT)
                        .user(maskedText)
                        .call()
                        .responseEntity(new ParameterizedTypeReference<List<String>>() {
                        })
                        .entity();
                if (bullets == null) {
                    throw new IllegalStateException("포트폴리오 요약 응답이 비어있어요.");
                }
                return sanitize(bullets);
            });
        } catch (Exception e) {
            log.error("[PORTFOLIO SUMMARY] OpenAI 호출/파싱 실패: portfolioId={}", portfolioId, e);
            throw new RuntimeException("포트폴리오 요약 생성에 실패했어요.", e);
        }
    }

    // 빈/공백 불릿을 걸러내고, 길이·개수 상한을 코드로 강제한다. 프롬프트 지시만 믿지 않는다.
    private List<String> sanitize(List<String> bullets) {
        return bullets.stream()
                .filter(bullet -> bullet != null && !bullet.isBlank())
                .map(String::strip)
                .map(this::truncate)
                .limit(MAX_BULLETS)
                .toList();
    }

    private String truncate(String bullet) {
        if (bullet.length() <= MAX_BULLET_LENGTH) {
            return bullet;
        }
        return bullet.substring(0, MAX_BULLET_LENGTH - TRUNCATION_SUFFIX.length()) + TRUNCATION_SUFFIX;
    }
}
