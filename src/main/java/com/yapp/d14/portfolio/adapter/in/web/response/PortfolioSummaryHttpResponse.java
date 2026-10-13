package com.yapp.d14.portfolio.adapter.in.web.response;

import com.yapp.d14.portfolio.application.port.in.result.PortfolioSummary;
import com.yapp.d14.portfolio.domain.PortfolioStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PortfolioSummaryHttpResponse(
        @Schema(description = "포트폴리오 ID")
        UUID portfolioId,

        @Schema(description = "업로드 파일명", example = "portfolio.pdf")
        String fileName,

        @Schema(description = "파일 크기(byte)", example = "1048576")
        long fileSize,

        @Schema(description = "PDF 페이지 수", example = "12")
        Integer pageCount,

        @Schema(description = "처리 상태 — PROCESSING(진행 중) / READY(완료) / FAILED_FILE·FAILED_SYSTEM(실패)")
        PortfolioStatus status,

        @Schema(description = "업로드 시각")
        LocalDateTime uploadedAt,

        @Schema(description = "이 포트폴리오를 사용 중인 진행 중(IN_PROGRESS) 면접 세션이 있는지 여부. true면 deleteAvailable 값과 무관하게 삭제 시도 시 PORTFOLIO_DELETE_BLOCKED_BY_INTERVIEW로 거부됨 — deleteAvailable보다 우선 확인해야 함")
        boolean interviewInProgress,

        @Schema(description = "AI가 생성한 포트폴리오 콘텐츠 요약 불릿 목록(중립적 사실 서술형, 가변 개수). 요약 생성이 실패했거나 아직 READY가 아니면 빈 배열")
        List<String> contentSummaryBullets
) {

    public static PortfolioSummaryHttpResponse from(PortfolioSummary summary) {
        return new PortfolioSummaryHttpResponse(
                summary.portfolioId(),
                summary.fileName(),
                summary.fileSize(),
                summary.pageCount(),
                summary.status(),
                summary.uploadedAt(),
                summary.interviewInProgress(),
                summary.contentSummaryBullets()
        );
    }
}
