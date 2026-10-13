package com.yapp.d14.portfolio.application.service;

import com.yapp.d14.portfolio.application.port.out.PdfTextExtractor;
import com.yapp.d14.portfolio.application.port.out.PortfolioContentSummarizer;
import com.yapp.d14.portfolio.application.port.out.PortfolioEmbeddingStore;
import com.yapp.d14.portfolio.application.port.out.PortfolioFileUploader;
import com.yapp.d14.portfolio.application.port.out.PortfolioRepository;
import com.yapp.d14.portfolio.domain.Portfolio;
import com.yapp.d14.portfolio.domain.PortfolioStatus;
import com.yapp.d14.portfolio.exception.PortfolioErrorCode;
import com.yapp.d14.portfolio.exception.PortfolioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PortfolioProcessServiceTest {

    private static final String VALID_EXTRACTED_TEXT = "포트폴리오 텍스트 추출 결과 예시 문장입니다. ".repeat(15);

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private PortfolioFileUploader portfolioFileUploader;

    @Mock
    private PdfTextExtractor pdfTextExtractor;

    @Mock
    private PortfolioEmbeddingStore portfolioEmbeddingStore;

    @Mock
    private PortfolioContentSummarizer portfolioContentSummarizer;

    @Mock
    private PortfolioCompletionPersister portfolioCompletionPersister;

    @InjectMocks
    private PortfolioProcessService portfolioProcessService;

    private UUID userId;
    private Portfolio portfolio;
    private byte[] fileContent;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        fileContent = "fake-pdf-bytes".getBytes();
        portfolio = Portfolio.create(
                UUID.randomUUID(), userId, "resume.pdf", fileContent.length, 5,
                "users/%s/portfolios/%s/test.pdf".formatted(userId, UUID.randomUUID()), false
        );
        // 완료 전환은 락을 잡는 PortfolioCompletionPersister가 전담한다. 기본은 "아직 PROCESSING이라 완료 성공".
        lenient().when(portfolioCompletionPersister.completeIfStillProcessing(any(), any())).thenReturn(true);
        // 요약 생성은 기본적으로 빈 리스트를 반환한다고 가정 — 요약 자체를 검증하는 테스트에서만 재정의한다.
        lenient().when(portfolioContentSummarizer.summarize(any(), any())).thenReturn(List.of());
    }

    @Test
    void 소유자가_아니면_예외를_던지고_후속처리를_하지_않는다() {
        given(portfolioRepository.findById(portfolio.getId())).willReturn(Optional.of(portfolio));
        UUID otherUserId = UUID.randomUUID();

        assertThatThrownBy(() -> portfolioProcessService.process(otherUserId, portfolio.getId(), fileContent))
                .isInstanceOf(PortfolioException.class);

        verify(portfolioFileUploader, never()).upload(any(), any(), any());
    }

    @Test
    void S3_업로드가_첫_시도에서_실패해도_재시도로_성공하면_계속_진행한다() {
        given(portfolioRepository.findById(portfolio.getId())).willReturn(Optional.of(portfolio));
        doThrow(new RuntimeException("일시적 S3 장애"))
                .doNothing()
                .when(portfolioFileUploader).upload(any(), any(), any());
        given(pdfTextExtractor.extractText(fileContent)).willReturn(VALID_EXTRACTED_TEXT);

        portfolioProcessService.process(userId, portfolio.getId(), fileContent);

        verify(portfolioCompletionPersister).completeIfStillProcessing(portfolio.getId(), List.of());
        verify(portfolioFileUploader, times(2)).upload(any(), any(), any());
    }

    @Test
    void S3_업로드가_재시도까지_모두_실패하면_FAILED_SYSTEM으로_전환하고_이후_단계를_진행하지_않는다() {
        given(portfolioRepository.findById(portfolio.getId())).willReturn(Optional.of(portfolio));
        doThrow(new RuntimeException("S3 장애")).when(portfolioFileUploader).upload(any(), any(), any());

        portfolioProcessService.process(userId, portfolio.getId(), fileContent);

        assertThat(portfolio.getStatus()).isEqualTo(PortfolioStatus.FAILED_SYSTEM);
        verify(portfolioFileUploader, times(3)).upload(any(), any(), any());
        verify(portfolioRepository).save(portfolio);
        verify(pdfTextExtractor, never()).extractText(any());
        verify(portfolioFileUploader, never()).delete(any());
    }

    @Test
    void S3_업로드_재시도_중_처리시간_초과로_이미_종료됐으면_재시도를_중단하고_상태를_덮어쓰지_않는다() {
        Portfolio timedOut = failedSystemCopyOf(portfolio);
        given(portfolioRepository.findById(portfolio.getId()))
                .willReturn(Optional.of(portfolio))
                .willReturn(Optional.of(timedOut));
        doThrow(new RuntimeException("S3 장애")).when(portfolioFileUploader).upload(any(), any(), any());

        portfolioProcessService.process(userId, portfolio.getId(), fileContent);

        verify(portfolioFileUploader, times(1)).upload(any(), any(), any());
        verify(portfolioRepository, never()).save(any());
    }

    @Test
    void 텍스트_추출에_실패하면_FAILED_FILE로_전환하고_S3_파일을_롤백_삭제한다() {
        given(portfolioRepository.findById(portfolio.getId())).willReturn(Optional.of(portfolio));
        given(pdfTextExtractor.extractText(fileContent))
                .willThrow(new PortfolioException(PortfolioErrorCode.INVALID_PDF_FILE));

        portfolioProcessService.process(userId, portfolio.getId(), fileContent);

        assertThat(portfolio.getStatus()).isEqualTo(PortfolioStatus.FAILED_FILE);
        verify(portfolioRepository).save(portfolio);
        verify(portfolioFileUploader).delete(portfolio.getS3Key());
    }

    @Test
    void 텍스트_추출_중_PortfolioException이_아닌_예외가_발생해도_FAILED_FILE로_전환하고_S3_파일을_롤백_삭제한다() {
        given(portfolioRepository.findById(portfolio.getId())).willReturn(Optional.of(portfolio));
        given(pdfTextExtractor.extractText(fileContent))
                .willThrow(new RuntimeException("Tika 파서 내부 오류"));

        portfolioProcessService.process(userId, portfolio.getId(), fileContent);

        assertThat(portfolio.getStatus()).isEqualTo(PortfolioStatus.FAILED_FILE);
        verify(portfolioRepository).save(portfolio);
        verify(portfolioFileUploader).delete(portfolio.getS3Key());
        verify(portfolioEmbeddingStore, never()).save(any(), any(), any(), any());
    }

    @Test
    void 추출된_텍스트가_300자_미만이면_FAILED_FILE로_전환하고_S3_파일을_롤백_삭제한다() {
        given(portfolioRepository.findById(portfolio.getId())).willReturn(Optional.of(portfolio));
        given(pdfTextExtractor.extractText(fileContent)).willReturn("너무 짧은 텍스트");

        portfolioProcessService.process(userId, portfolio.getId(), fileContent);

        assertThat(portfolio.getStatus()).isEqualTo(PortfolioStatus.FAILED_FILE);
        verify(portfolioRepository).save(portfolio);
        verify(portfolioFileUploader).delete(portfolio.getS3Key());
    }

    @Test
    void 임베딩이_첫_시도에서_실패해도_재시도로_성공하면_READY로_전환한다() {
        given(portfolioRepository.findById(portfolio.getId())).willReturn(Optional.of(portfolio));
        given(pdfTextExtractor.extractText(fileContent)).willReturn(VALID_EXTRACTED_TEXT);
        doThrow(new RuntimeException("일시적 임베딩 장애"))
                .doNothing()
                .when(portfolioEmbeddingStore).save(any(), any(), any(), any());

        portfolioProcessService.process(userId, portfolio.getId(), fileContent);

        verify(portfolioCompletionPersister).completeIfStillProcessing(portfolio.getId(), List.of());
        verify(portfolioEmbeddingStore, times(2)).save(any(), any(), any(), any());
    }

    @Test
    void 임베딩이_재시도까지_모두_실패하면_FAILED_SYSTEM으로_전환하고_벡터와_S3_파일을_롤백_삭제한다() {
        String extractedText = VALID_EXTRACTED_TEXT;
        given(portfolioRepository.findById(portfolio.getId())).willReturn(Optional.of(portfolio));
        given(pdfTextExtractor.extractText(fileContent)).willReturn(extractedText);
        doThrow(new RuntimeException("임베딩 API 장애"))
                .when(portfolioEmbeddingStore)
                .save(portfolio.getId(), userId, portfolio.getFileName(), extractedText);

        portfolioProcessService.process(userId, portfolio.getId(), fileContent);

        assertThat(portfolio.getStatus()).isEqualTo(PortfolioStatus.FAILED_SYSTEM);
        verify(portfolioEmbeddingStore, times(3)).save(any(), any(), any(), any());
        verify(portfolioEmbeddingStore).deleteByPortfolioId(portfolio.getId());
        verify(portfolioFileUploader).delete(portfolio.getS3Key());
    }

    @Test
    void 임베딩_재시도_중_처리시간_초과로_이미_종료됐으면_재시도를_중단하고_상태를_덮어쓰지_않는다() {
        Portfolio timedOut = failedSystemCopyOf(portfolio);
        given(portfolioRepository.findById(portfolio.getId()))
                .willReturn(Optional.of(portfolio))
                .willReturn(Optional.of(timedOut));
        given(pdfTextExtractor.extractText(fileContent)).willReturn(VALID_EXTRACTED_TEXT);
        doThrow(new RuntimeException("임베딩 장애")).when(portfolioEmbeddingStore).save(any(), any(), any(), any());

        portfolioProcessService.process(userId, portfolio.getId(), fileContent);

        verify(portfolioEmbeddingStore, times(1)).save(any(), any(), any(), any());
        verify(portfolioRepository, never()).save(any());
        verify(portfolioEmbeddingStore).deleteByPortfolioId(portfolio.getId());
        verify(portfolioFileUploader).delete(portfolio.getS3Key());
    }

    @Test
    void 임베딩까지_성공하면_완료_전환을_위임한다() {
        String extractedText = VALID_EXTRACTED_TEXT;
        given(portfolioRepository.findById(portfolio.getId())).willReturn(Optional.of(portfolio));
        given(pdfTextExtractor.extractText(fileContent)).willReturn(extractedText);

        portfolioProcessService.process(userId, portfolio.getId(), fileContent);

        verify(portfolioEmbeddingStore).save(portfolio.getId(), userId, portfolio.getFileName(), extractedText);
        verify(portfolioCompletionPersister).completeIfStillProcessing(portfolio.getId(), List.of());
        verify(portfolioFileUploader, never()).delete(any());
        verify(portfolioEmbeddingStore, never()).deleteByPortfolioId(any());
    }

    @Test
    void 추출된_텍스트에_PII가_있으면_마스킹된_텍스트로_임베딩한다() {
        String textWithPii = "연락처 hong@gmail.com / 010-1234-5678 " + VALID_EXTRACTED_TEXT;
        given(portfolioRepository.findById(portfolio.getId())).willReturn(Optional.of(portfolio));
        given(pdfTextExtractor.extractText(fileContent)).willReturn(textWithPii);

        portfolioProcessService.process(userId, portfolio.getId(), fileContent);

        ArgumentCaptor<String> embeddedText = ArgumentCaptor.forClass(String.class);
        verify(portfolioEmbeddingStore).save(any(), any(), any(), embeddedText.capture());
        assertThat(embeddedText.getValue())
                .doesNotContain("hong@gmail.com")
                .doesNotContain("010-1234-5678")
                .contains("[이메일]")
                .contains("[전화번호]");
    }

    @Test
    void 완료_전환이_거부되면_READY로_덮어쓰지_않고_S3와_임베딩을_정리한다() {
        given(portfolioRepository.findById(portfolio.getId())).willReturn(Optional.of(portfolio));
        given(pdfTextExtractor.extractText(fileContent)).willReturn(VALID_EXTRACTED_TEXT);
        given(portfolioCompletionPersister.completeIfStillProcessing(portfolio.getId(), List.of())).willReturn(false);

        portfolioProcessService.process(userId, portfolio.getId(), fileContent);

        assertThat(portfolio.getStatus()).isEqualTo(PortfolioStatus.PROCESSING);
        verify(portfolioRepository, never()).save(any());
        verify(portfolioEmbeddingStore).deleteByPortfolioId(portfolio.getId());
        verify(portfolioFileUploader).delete(portfolio.getS3Key());
    }

    @Test
    void 콘텐츠_요약_생성에_성공하면_생성된_불릿과_함께_완료_전환을_요청한다() {
        given(portfolioRepository.findById(portfolio.getId())).willReturn(Optional.of(portfolio));
        given(pdfTextExtractor.extractText(fileContent)).willReturn(VALID_EXTRACTED_TEXT);
        given(portfolioContentSummarizer.summarize(any(), any())).willReturn(List.of("불릿1"));

        portfolioProcessService.process(userId, portfolio.getId(), fileContent);

        verify(portfolioCompletionPersister).completeIfStillProcessing(portfolio.getId(), List.of("불릿1"));
    }

    @Test
    void 콘텐츠_요약_생성이_예외를_던져도_READY_전환은_그대로_진행된다() {
        given(portfolioRepository.findById(portfolio.getId())).willReturn(Optional.of(portfolio));
        given(pdfTextExtractor.extractText(fileContent)).willReturn(VALID_EXTRACTED_TEXT);
        given(portfolioContentSummarizer.summarize(any(), any())).willThrow(new RuntimeException("OpenAI 장애"));

        assertThatCode(() -> portfolioProcessService.process(userId, portfolio.getId(), fileContent))
                .doesNotThrowAnyException();

        verify(portfolioCompletionPersister).completeIfStillProcessing(portfolio.getId(), List.of());
    }

    @Test
    void 콘텐츠_요약_결과가_null이면_빈_리스트로_완료_전환을_요청한다() {
        given(portfolioRepository.findById(portfolio.getId())).willReturn(Optional.of(portfolio));
        given(pdfTextExtractor.extractText(fileContent)).willReturn(VALID_EXTRACTED_TEXT);
        given(portfolioContentSummarizer.summarize(any(), any())).willReturn(null);

        portfolioProcessService.process(userId, portfolio.getId(), fileContent);

        verify(portfolioCompletionPersister).completeIfStillProcessing(portfolio.getId(), List.of());
    }

    private Portfolio failedSystemCopyOf(Portfolio original) {
        return Portfolio.of(
                original.getId(), original.getUserId(), original.getFileName(), original.getFileSize(),
                original.getPageCount(), original.getS3Key(), PortfolioStatus.FAILED_SYSTEM,
                "처리 시간이 초과되었어요. 다시 시도해 주세요.", original.getCreatedAt(), null,
                original.isReplacement(), false, null, original.getSummaryBullets()
        );
    }

}
