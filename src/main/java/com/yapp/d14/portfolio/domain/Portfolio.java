package com.yapp.d14.portfolio.domain;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
public class Portfolio {

    private static final int MIN_EXTRACTED_TEXT_LENGTH = 300;
    private static final Duration PROCESSING_TIMEOUT = Duration.ofSeconds(20);

    private final UUID id;
    private final UUID userId;
    private final String fileName;
    private final long fileSize;
    private Integer pageCount;
    private final String s3Key;
    private PortfolioStatus status;
    private String message;
    private final LocalDateTime createdAt;
    private LocalDateTime uploadedAt;
    private final boolean replacement;
    private boolean deleted;
    private LocalDateTime deletedAt;
    private List<String> summaryBullets;

    @Builder(access = AccessLevel.PRIVATE)
    private Portfolio(
            UUID id,
            UUID userId,
            String fileName,
            long fileSize,
            Integer pageCount,
            String s3Key,
            PortfolioStatus status,
            String message,
            LocalDateTime createdAt,
            LocalDateTime uploadedAt,
            boolean replacement,
            boolean deleted,
            LocalDateTime deletedAt,
            List<String> summaryBullets
    ) {
        this.id = id;
        this.userId = userId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.pageCount = pageCount;
        this.s3Key = s3Key;
        this.status = status;
        this.message = message;
        this.createdAt = createdAt;
        this.uploadedAt = uploadedAt;
        this.replacement = replacement;
        this.deleted = deleted;
        this.deletedAt = deletedAt;
        this.summaryBullets = copyOrEmpty(summaryBullets);
    }

    // null·외부 참조 노출 방지를 한 곳에서 보장한다 — 생성자와 updateSummaryBullets가 함께 쓴다.
    private static List<String> copyOrEmpty(List<String> summaryBullets) {
        return summaryBullets == null ? List.of() : List.copyOf(summaryBullets);
    }

    public static Portfolio create(UUID id, UUID userId, String fileName, long fileSize, int pageCount, String s3Key, boolean replacement) {
        return Portfolio.builder()
                .id(id)
                .userId(userId)
                .fileName(fileName)
                .fileSize(fileSize)
                .pageCount(pageCount)
                .s3Key(s3Key)
                .status(PortfolioStatus.PROCESSING)
                .message("포트폴리오를 분석하고 있어요.")
                .createdAt(LocalDateTime.now())
                .replacement(replacement)
                .deleted(false)
                .summaryBullets(List.of())
                .build();
    }

    public static Portfolio of(
            UUID id,
            UUID userId,
            String fileName,
            long fileSize,
            Integer pageCount,
            String s3Key,
            PortfolioStatus status,
            String message,
            LocalDateTime createdAt,
            LocalDateTime uploadedAt,
            boolean replacement,
            boolean deleted,
            LocalDateTime deletedAt,
            List<String> summaryBullets
    ) {
        return Portfolio.builder()
                .id(id)
                .userId(userId)
                .fileName(fileName)
                .fileSize(fileSize)
                .pageCount(pageCount)
                .s3Key(s3Key)
                .status(status)
                .message(message)
                .createdAt(createdAt)
                .uploadedAt(uploadedAt)
                .replacement(replacement)
                .deleted(deleted)
                .deletedAt(deletedAt)
                .summaryBullets(summaryBullets)
                .build();
    }

    public boolean hasEnoughExtractedText(String extractedText) {
        return extractedText.trim().length() >= MIN_EXTRACTED_TEXT_LENGTH;
    }

    public void ready() {
        this.status = PortfolioStatus.READY;
        this.uploadedAt = LocalDateTime.now();
        this.message = "포트폴리오 처리가 완료되었습니다.";
    }

    public void failFile(String message) {
        this.status = PortfolioStatus.FAILED_FILE;
        this.message = message;
    }

    public void failSystem(String message) {
        this.status = PortfolioStatus.FAILED_SYSTEM;
        this.message = message;
    }

    // 상태 전환과 독립적으로 호출 가능하다 — 요약 생성 실패가 READY 전환을 막지 않고,
    // 요약 성공도 status를 바꾸지 않는다(HLT-11).
    public void updateSummaryBullets(List<String> summaryBullets) {
        this.summaryBullets = copyOrEmpty(summaryBullets);
    }

    public boolean isFailed() {
        return status == PortfolioStatus.FAILED_FILE || status == PortfolioStatus.FAILED_SYSTEM;
    }

    // InterviewSession.isPreloadTimedOut()과 동일한 패턴 — 상태를 바꾸지 않는 순수 판별만 한다.
    // 락 없이 미리 걸러내는 용도이고, 실제 전환은 행을 잠근 뒤 failIfProcessingTimedOut()으로 다시 판정한다.
    public boolean isProcessingTimedOut() {
        return status == PortfolioStatus.PROCESSING
                && !createdAt.plus(PROCESSING_TIMEOUT).isAfter(LocalDateTime.now());
    }

    public boolean failIfProcessingTimedOut() {
        if (!isProcessingTimedOut()) {
            return false;
        }
        failSystem("처리 시간이 초과되었어요. 다시 시도해 주세요.");
        return true;
    }

    public boolean isProcessing() {
        return status == PortfolioStatus.PROCESSING;
    }

    // 완료(READY)까지 간 포트폴리오를 지울 때만 월 1회 삭제 기회를 소진한다.
    // 취소·실패 건은 재업로드 집계에서도 제외되므로, 삭제 집계에서도 같은 기준을 적용한다.
    public boolean countsTowardDeletionLimit() {
        return status == PortfolioStatus.READY;
    }

    public void softDelete() {
        if (status == PortfolioStatus.PROCESSING) {
            this.status = PortfolioStatus.CANCELLED;
            this.message = "사용자 요청으로 취소되었어요.";
        }
        this.deleted = true;
        this.deletedAt = LocalDateTime.now();
    }
}
