package com.yapp.d14.portfolio.adapter.out.persistence.entity;

import com.yapp.d14.portfolio.domain.Portfolio;
import com.yapp.d14.portfolio.domain.PortfolioStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "portfolios")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortfolioJpaEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", columnDefinition = "uuid", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private long fileSize;

    private Integer pageCount;

    @Column(name = "s3_key", nullable = false)
    private String s3Key;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PortfolioStatus status;

    private String message;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime uploadedAt;

    @Column(name = "is_replacement", nullable = false, updatable = false)
    private boolean replacement;

    @Column(nullable = false)
    private boolean deleted;

    private LocalDateTime deletedAt;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "summary_bullets", columnDefinition = "text[]")
    private List<String> summaryBullets = new ArrayList<>();

    public static PortfolioJpaEntity from(Portfolio portfolio) {
        PortfolioJpaEntity entity = new PortfolioJpaEntity();
        entity.id = portfolio.getId();
        entity.userId = portfolio.getUserId();
        entity.fileName = portfolio.getFileName();
        entity.fileSize = portfolio.getFileSize();
        entity.pageCount = portfolio.getPageCount();
        entity.s3Key = portfolio.getS3Key();
        entity.status = portfolio.getStatus();
        entity.message = portfolio.getMessage();
        entity.createdAt = portfolio.getCreatedAt();
        entity.uploadedAt = portfolio.getUploadedAt();
        entity.replacement = portfolio.isReplacement();
        entity.deleted = portfolio.isDeleted();
        entity.deletedAt = portfolio.getDeletedAt();
        entity.summaryBullets = new ArrayList<>(portfolio.getSummaryBullets());
        return entity;
    }

    public Portfolio toDomain() {
        return Portfolio.of(
                id,
                userId,
                fileName,
                fileSize,
                pageCount,
                s3Key,
                status,
                message,
                createdAt,
                uploadedAt,
                replacement,
                deleted,
                deletedAt,
                summaryBullets
        );
    }
}
