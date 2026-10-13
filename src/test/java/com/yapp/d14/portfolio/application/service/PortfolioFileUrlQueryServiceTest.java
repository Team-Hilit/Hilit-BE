package com.yapp.d14.portfolio.application.service;

import com.yapp.d14.portfolio.application.port.in.result.PortfolioFileUrlResult;
import com.yapp.d14.portfolio.application.port.out.PortfolioFileUploader;
import com.yapp.d14.portfolio.application.port.out.PortfolioRepository;
import com.yapp.d14.portfolio.domain.Portfolio;
import com.yapp.d14.portfolio.domain.PortfolioStatus;
import com.yapp.d14.portfolio.exception.PortfolioErrorCode;
import com.yapp.d14.portfolio.exception.PortfolioException;
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
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PortfolioFileUrlQueryServiceTest {

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private PortfolioFileUploader portfolioFileUploader;

    @InjectMocks
    private PortfolioFileUrlQueryService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID portfolioId = UUID.randomUUID();
    private final String s3Key = "users/%s/portfolios/%s.pdf".formatted(userId, portfolioId);

    private Portfolio portfolioWithStatus(PortfolioStatus status) {
        return Portfolio.of(
                portfolioId, userId, "resume.pdf", 1024, 5, s3Key,
                status, null, LocalDateTime.now(), LocalDateTime.now(), false, false, null, List.of()
        );
    }

    @Test
    void READY_상태면_presigned_URL을_발급한다() {
        given(portfolioRepository.findById(portfolioId)).willReturn(Optional.of(portfolioWithStatus(PortfolioStatus.READY)));
        given(portfolioFileUploader.presignDownload(s3Key)).willReturn("https://example.com/presigned");

        PortfolioFileUrlResult result = service.getFileUrl(userId, portfolioId);

        assertThat(result.portfolioId()).isEqualTo(portfolioId);
        assertThat(result.fileUrl()).isEqualTo("https://example.com/presigned");
    }

    @Test
    void PROCESSING_상태면_PORTFOLIO_PROCESSING() {
        given(portfolioRepository.findById(portfolioId)).willReturn(Optional.of(portfolioWithStatus(PortfolioStatus.PROCESSING)));

        assertThatThrownBy(() -> service.getFileUrl(userId, portfolioId))
                .isInstanceOf(PortfolioException.class)
                .extracting(e -> ((PortfolioException) e).getErrorCode())
                .isEqualTo(PortfolioErrorCode.PORTFOLIO_PROCESSING);
    }

    @ParameterizedTest
    @EnumSource(value = PortfolioStatus.class, names = {"FAILED_FILE", "FAILED_SYSTEM", "CANCELLED"})
    void 실패_상태면_PORTFOLIO_UPLOAD_FAILED(PortfolioStatus status) {
        given(portfolioRepository.findById(portfolioId)).willReturn(Optional.of(portfolioWithStatus(status)));

        assertThatThrownBy(() -> service.getFileUrl(userId, portfolioId))
                .isInstanceOf(PortfolioException.class)
                .extracting(e -> ((PortfolioException) e).getErrorCode())
                .isEqualTo(PortfolioErrorCode.PORTFOLIO_UPLOAD_FAILED);
    }

    @Test
    void 존재하지_않거나_본인_소유가_아니면_PORTFOLIO_NOT_FOUND() {
        given(portfolioRepository.findById(portfolioId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getFileUrl(userId, portfolioId))
                .isInstanceOf(PortfolioException.class)
                .extracting(e -> ((PortfolioException) e).getErrorCode())
                .isEqualTo(PortfolioErrorCode.PORTFOLIO_NOT_FOUND);
    }
}
