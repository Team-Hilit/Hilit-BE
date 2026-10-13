package com.yapp.d14.common.metrics;

// AI 호출 계측 단계(Hilit-BE#176). 값이 Prometheus 태그로 그대로 나가므로 바꾸면 기존 시계열과 끊긴다.
public enum AiCallStage {

    PROBE_CANDIDATE("anthropic", "probe_candidate"),
    QUESTION_TEXT("anthropic", "question_text"),
    QUESTION_OPENER("anthropic", "question_opener"),
    LIVE_TURN("anthropic", "live_turn"),
    AXIS_SCORE("anthropic", "axis_score"),
    RED_FLAG("anthropic", "red_flag"),
    REPORT_CARD("anthropic", "report_card"),
    REPORT_HEADLINE("anthropic", "report_headline"),
    JD_KEYWORD("openai", "jd_keyword"),
    JD_CONTENT("openai", "jd_content"),
    PORTFOLIO_SUMMARY("openai", "portfolio_summary"),
    STT("openai", "stt"),
    TTS("openai", "tts"),
    TTS_STREAM("openai", "tts_stream");

    private final String provider;
    private final String stage;

    AiCallStage(String provider, String stage) {
        this.provider = provider;
        this.stage = stage;
    }

    String provider() {
        return provider;
    }

    String stage() {
        return stage;
    }
}
