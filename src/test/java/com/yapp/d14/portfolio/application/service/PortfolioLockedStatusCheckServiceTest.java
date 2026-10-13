package com.yapp.d14.portfolio.application.service;

import com.yapp.d14.portfolio.application.port.out.PortfolioRepository;
import com.yapp.d14.portfolio.domain.Portfolio;
import com.yapp.d14.portfolio.domain.PortfolioStatus;
import com.yapp.d14.portfolio.exception.PortfolioErrorCode;
import com.yapp.d14.portfolio.exception.PortfolioException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PortfolioLockedStatusCheckServiceTest {

    @Mock
    private PortfolioRepository portfolioRepository;

    @InjectMocks
    private PortfolioLockedStatusCheckService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID portfolioId = UUID.randomUUID();

    private Portfolio portfolioWithStatus(PortfolioStatus status) {
        return Portfolio.of(
                portfolioId, userId, "resume.pdf", 1024, 5, "users/x/portfolios/x.pdf",
                status, null, LocalDateTime.now(), LocalDateTime.now(), false, false, null, List.of()
        );
    }

    @Test
    void 락을_먼저_잡은_뒤_소유권과_상태를_검증한다() {
        given(portfolioRepository.findById(portfolioId)).willReturn(Optional.of(portfolioWithStatus(PortfolioStatus.READY)));

        service.requireReadyWithLock(userId, portfolioId);

        InOrder order = inOrder(portfolioRepository);
        order.verify(portfolioRepository).acquirePortfolioLock(portfolioId);
        order.verify(portfolioRepository).findById(portfolioId);
    }

    @Test
    void READY_상태면_예외를_던지지_않는다() {
        given(portfolioRepository.findById(portfolioId)).willReturn(Optional.of(portfolioWithStatus(PortfolioStatus.READY)));

        assertThatCode(() -> service.requireReadyWithLock(userId, portfolioId)).doesNotThrowAnyException();
    }

    @Test
    void 락을_잡은_사이_삭제됐으면_PORTFOLIO_NOT_FOUND() {
        given(portfolioRepository.findById(portfolioId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireReadyWithLock(userId, portfolioId))
                .isInstanceOf(PortfolioException.class)
                .extracting(e -> ((PortfolioException) e).getErrorCode())
                .isEqualTo(PortfolioErrorCode.PORTFOLIO_NOT_FOUND);
        verify(portfolioRepository).acquirePortfolioLock(portfolioId);
    }

    @Test
    void PROCESSING_상태면_PORTFOLIO_PROCESSING() {
        given(portfolioRepository.findById(portfolioId)).willReturn(Optional.of(portfolioWithStatus(PortfolioStatus.PROCESSING)));

        assertThatThrownBy(() -> service.requireReadyWithLock(userId, portfolioId))
                .isInstanceOf(PortfolioException.class)
                .extracting(e -> ((PortfolioException) e).getErrorCode())
                .isEqualTo(PortfolioErrorCode.PORTFOLIO_PROCESSING);
    }

    @ParameterizedTest
    @EnumSource(value = PortfolioStatus.class, names = {"FAILED_FILE", "FAILED_SYSTEM", "CANCELLED"})
    void 실패_상태면_PORTFOLIO_UPLOAD_FAILED(PortfolioStatus status) {
        given(portfolioRepository.findById(portfolioId)).willReturn(Optional.of(portfolioWithStatus(status)));

        assertThatThrownBy(() -> service.requireReadyWithLock(userId, portfolioId))
                .isInstanceOf(PortfolioException.class)
                .extracting(e -> ((PortfolioException) e).getErrorCode())
                .isEqualTo(PortfolioErrorCode.PORTFOLIO_UPLOAD_FAILED);
    }
}
