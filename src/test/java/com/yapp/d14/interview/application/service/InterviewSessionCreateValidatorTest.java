package com.yapp.d14.interview.application.service;

import com.yapp.d14.consent.application.port.in.ConsentUpToDateCheckUseCase;
import com.yapp.d14.consent.exception.ConsentErrorCode;
import com.yapp.d14.consent.exception.ConsentException;
import com.yapp.d14.interview.application.command.InterviewSessionCreateCommand;
import com.yapp.d14.interview.domain.JobType;
import com.yapp.d14.interview.exception.InterviewErrorCode;
import com.yapp.d14.interview.exception.InterviewException;
import com.yapp.d14.jd.application.port.in.JdValidationCheckUseCase;
import com.yapp.d14.portfolio.application.port.in.PortfolioSimilarityCheckUseCase;
import com.yapp.d14.portfolio.application.port.in.PortfolioStatusUseCase;
import com.yapp.d14.portfolio.application.port.in.result.PortfolioStatusResult;
import com.yapp.d14.portfolio.domain.PortfolioStatus;
import com.yapp.d14.portfolio.exception.PortfolioErrorCode;
import com.yapp.d14.portfolio.exception.PortfolioException;
import com.yapp.d14.user.application.port.in.AccountStatusCheckUseCase;
import com.yapp.d14.user.application.port.in.FindUserUseCase;
import com.yapp.d14.user.domain.JobRole;
import com.yapp.d14.user.domain.Provider;
import com.yapp.d14.user.domain.User;
import com.yapp.d14.user.exception.UserErrorCode;
import com.yapp.d14.user.exception.UserException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class InterviewSessionCreateValidatorTest {

    @Mock
    private PortfolioStatusUseCase portfolioStatusUseCase;

    @Mock
    private JdValidationCheckUseCase jdValidationCheckUseCase;

    @Mock
    private PortfolioSimilarityCheckUseCase portfolioSimilarityCheckUseCase;

    @Mock
    private FindUserUseCase findUserUseCase;

    @Mock
    private AccountStatusCheckUseCase accountStatusCheckUseCase;

    @Mock
    private ConsentUpToDateCheckUseCase consentUpToDateCheckUseCase;

    @InjectMocks
    private InterviewSessionCreateValidator validator;

    private final UUID userId = UUID.randomUUID();
    private final UUID portfolioId = UUID.randomUUID();

    private InterviewSessionCreateCommand command(String jdUrl, String jdText, String freeText) {
        return new InterviewSessionCreateCommand(userId, portfolioId, jdUrl, jdText, freeText);
    }

    private void givenPortfolioStatus(PortfolioStatus status) {
        given(portfolioStatusUseCase.getStatus(userId, portfolioId))
                .willReturn(new PortfolioStatusResult(portfolioId, status, "메시지", "resume.pdf", List.of()));
    }

    private void givenRegisteredProfile(JobRole jobRole, Integer careerYears) {
        givenRegisteredProfile("이름", jobRole, careerYears);
    }

    private void givenRegisteredProfile(String name, JobRole jobRole, Integer careerYears) {
        given(findUserUseCase.findById(userId)).willReturn(
                User.of(userId, "a@a.com", name, true, Provider.KAKAO, "pid", jobRole, careerYears, null, null, null)
        );
    }

    @Test
    void 포트폴리오가_없거나_본인_소유가_아니면_PORTFOLIO_NOT_FOUND() {
        given(portfolioStatusUseCase.getStatus(userId, portfolioId))
                .willThrow(new PortfolioException(PortfolioErrorCode.PORTFOLIO_NOT_FOUND));

        assertThatThrownBy(() -> validator.validate(command(null, null, null)))
                .isInstanceOf(PortfolioException.class)
                .extracting(e -> ((PortfolioException) e).getErrorCode())
                .isEqualTo(PortfolioErrorCode.PORTFOLIO_NOT_FOUND);
    }

    @Test
    void 포트폴리오가_PROCESSING이면_PORTFOLIO_PROCESSING() {
        givenPortfolioStatus(PortfolioStatus.PROCESSING);

        assertThatThrownBy(() -> validator.validate(command(null, null, null)))
                .isInstanceOf(PortfolioException.class)
                .extracting(e -> ((PortfolioException) e).getErrorCode())
                .isEqualTo(PortfolioErrorCode.PORTFOLIO_PROCESSING);
    }

    @Test
    void 포트폴리오가_FAILED_FILE이면_PORTFOLIO_UPLOAD_FAILED() {
        givenPortfolioStatus(PortfolioStatus.FAILED_FILE);

        assertThatThrownBy(() -> validator.validate(command(null, null, null)))
                .isInstanceOf(PortfolioException.class)
                .extracting(e -> ((PortfolioException) e).getErrorCode())
                .isEqualTo(PortfolioErrorCode.PORTFOLIO_UPLOAD_FAILED);
    }

    @Test
    void 포트폴리오가_FAILED_SYSTEM이면_PORTFOLIO_UPLOAD_FAILED() {
        givenPortfolioStatus(PortfolioStatus.FAILED_SYSTEM);

        assertThatThrownBy(() -> validator.validate(command(null, null, null)))
                .isInstanceOf(PortfolioException.class)
                .extracting(e -> ((PortfolioException) e).getErrorCode())
                .isEqualTo(PortfolioErrorCode.PORTFOLIO_UPLOAD_FAILED);
    }

    @Test
    void 포트폴리오가_CANCELLED이면_PORTFOLIO_UPLOAD_FAILED() {
        givenPortfolioStatus(PortfolioStatus.CANCELLED);

        assertThatThrownBy(() -> validator.validate(command(null, null, null)))
                .isInstanceOf(PortfolioException.class)
                .extracting(e -> ((PortfolioException) e).getErrorCode())
                .isEqualTo(PortfolioErrorCode.PORTFOLIO_UPLOAD_FAILED);
    }

    @Test
    void 포트폴리오가_READY이고_JD_freeText_모두_없으면_직무_연차_스냅샷을_담아_통과한다() {
        givenPortfolioStatus(PortfolioStatus.READY);
        givenRegisteredProfile(JobRole.BACKEND, 3);

        InterviewSessionCreateContext context = validator.validate(command(null, null, null));

        assertThat(context.portfolioFileName()).isEqualTo("resume.pdf");
        assertThat(context.jobRole()).isEqualTo(JobType.BACKEND);
        assertThat(context.careerYears()).isEqualTo(3);
    }

    @Test
    void 이름이_등록되어_있지_않으면_USER_PROFILE_NOT_REGISTERED() {
        givenPortfolioStatus(PortfolioStatus.READY);
        givenRegisteredProfile(null, JobRole.BACKEND, 3);

        assertThatThrownBy(() -> validator.validate(command(null, null, null)))
                .isInstanceOf(InterviewException.class)
                .extracting(e -> ((InterviewException) e).getErrorCode())
                .isEqualTo(InterviewErrorCode.USER_PROFILE_NOT_REGISTERED);
    }

    @Test
    void 직무가_등록되어_있지_않으면_USER_PROFILE_NOT_REGISTERED() {
        givenPortfolioStatus(PortfolioStatus.READY);
        givenRegisteredProfile(null, 3);

        assertThatThrownBy(() -> validator.validate(command(null, null, null)))
                .isInstanceOf(InterviewException.class)
                .extracting(e -> ((InterviewException) e).getErrorCode())
                .isEqualTo(InterviewErrorCode.USER_PROFILE_NOT_REGISTERED);
    }

    @Test
    void 연차가_등록되어_있지_않으면_USER_PROFILE_NOT_REGISTERED() {
        givenPortfolioStatus(PortfolioStatus.READY);
        givenRegisteredProfile(JobRole.BACKEND, null);

        assertThatThrownBy(() -> validator.validate(command(null, null, null)))
                .isInstanceOf(InterviewException.class)
                .extracting(e -> ((InterviewException) e).getErrorCode())
                .isEqualTo(InterviewErrorCode.USER_PROFILE_NOT_REGISTERED);
    }

    @Test
    void jdUrl이_있고_캐시가_존재하면_통과한다() {
        givenPortfolioStatus(PortfolioStatus.READY);
        givenRegisteredProfile(JobRole.BACKEND, 3);
        given(jdValidationCheckUseCase.isValidated("https://example.com/jd")).willReturn(true);

        InterviewSessionCreateContext context = validator.validate(command("https://example.com/jd", null, null));

        assertThat(context.jobRole()).isEqualTo(JobType.BACKEND);
    }

    @Test
    void jdUrl이_있고_캐시가_없으면_JD_NOT_VALIDATED() {
        givenPortfolioStatus(PortfolioStatus.READY);
        given(jdValidationCheckUseCase.isValidated("https://example.com/jd")).willReturn(false);

        assertThatThrownBy(() -> validator.validate(command("https://example.com/jd", null, null)))
                .isInstanceOf(InterviewException.class)
                .extracting(e -> ((InterviewException) e).getErrorCode())
                .isEqualTo(InterviewErrorCode.JD_NOT_VALIDATED);
    }

    @Test
    void jdUrl과_jdText가_함께_있으면_JD_URL_AND_TEXT_BOTH_PROVIDED() {
        givenPortfolioStatus(PortfolioStatus.READY);

        assertThatThrownBy(() -> validator.validate(command("https://example.com/jd", "가".repeat(200), null)))
                .isInstanceOf(InterviewException.class)
                .extracting(e -> ((InterviewException) e).getErrorCode())
                .isEqualTo(InterviewErrorCode.JD_URL_AND_TEXT_BOTH_PROVIDED);
    }

    @Test
    void jdText가_200자_미만이면_INVALID_JD_LENGTH() {
        givenPortfolioStatus(PortfolioStatus.READY);

        assertThatThrownBy(() -> validator.validate(command(null, "가".repeat(199), null)))
                .isInstanceOf(InterviewException.class)
                .extracting(e -> ((InterviewException) e).getErrorCode())
                .isEqualTo(InterviewErrorCode.INVALID_JD_LENGTH);
    }

    @Test
    void jdText가_3000자_초과면_INVALID_JD_LENGTH() {
        givenPortfolioStatus(PortfolioStatus.READY);

        assertThatThrownBy(() -> validator.validate(command(null, "가".repeat(3001), null)))
                .isInstanceOf(InterviewException.class)
                .extracting(e -> ((InterviewException) e).getErrorCode())
                .isEqualTo(InterviewErrorCode.INVALID_JD_LENGTH);
    }

    @Test
    void jdText가_200_3000자_경계값이면_통과한다() {
        givenPortfolioStatus(PortfolioStatus.READY);
        givenRegisteredProfile(JobRole.BACKEND, 3);

        assertThat(validator.validate(command(null, "가".repeat(200), null))).isNotNull();
        assertThat(validator.validate(command(null, "가".repeat(3000), null))).isNotNull();
    }

    @Test
    void freeText가_10자_미만이면_INVALID_FREETEXT_LENGTH() {
        givenPortfolioStatus(PortfolioStatus.READY);

        assertThatThrownBy(() -> validator.validate(command(null, null, "가".repeat(9))))
                .isInstanceOf(InterviewException.class)
                .extracting(e -> ((InterviewException) e).getErrorCode())
                .isEqualTo(InterviewErrorCode.INVALID_FREETEXT_LENGTH);
    }

    @Test
    void freeText가_300자_초과면_INVALID_FREETEXT_LENGTH() {
        givenPortfolioStatus(PortfolioStatus.READY);

        assertThatThrownBy(() -> validator.validate(command(null, null, "가".repeat(301))))
                .isInstanceOf(InterviewException.class)
                .extracting(e -> ((InterviewException) e).getErrorCode())
                .isEqualTo(InterviewErrorCode.INVALID_FREETEXT_LENGTH);
    }

    @Test
    void freeText가_유효길이여도_연관성이_0_4미만이면_FREETEXT_NOT_RELEVANT() {
        givenPortfolioStatus(PortfolioStatus.READY);
        given(portfolioSimilarityCheckUseCase.checkSimilarity(any(), any())).willReturn(Optional.of(0.39));

        assertThatThrownBy(() -> validator.validate(command(null, null, "가".repeat(20))))
                .isInstanceOf(InterviewException.class)
                .extracting(e -> ((InterviewException) e).getErrorCode())
                .isEqualTo(InterviewErrorCode.FREETEXT_NOT_RELEVANT);
    }

    @Test
    void freeText_연관성_점수가_없으면_0점으로_취급해_FREETEXT_NOT_RELEVANT() {
        givenPortfolioStatus(PortfolioStatus.READY);
        given(portfolioSimilarityCheckUseCase.checkSimilarity(any(), any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> validator.validate(command(null, null, "가".repeat(20))))
                .isInstanceOf(InterviewException.class)
                .extracting(e -> ((InterviewException) e).getErrorCode())
                .isEqualTo(InterviewErrorCode.FREETEXT_NOT_RELEVANT);
    }

    @Test
    void freeText가_유효길이이고_연관성이_0_4이상이면_통과한다() {
        givenPortfolioStatus(PortfolioStatus.READY);
        givenRegisteredProfile(JobRole.BACKEND, 3);
        given(portfolioSimilarityCheckUseCase.checkSimilarity(any(), any())).willReturn(Optional.of(0.4));

        assertThat(validator.validate(command(null, null, "가".repeat(20)))).isNotNull();
    }

    @Test
    void 포트폴리오_검증이_JD_검증보다_먼저_실행된다() {
        given(portfolioStatusUseCase.getStatus(userId, portfolioId))
                .willThrow(new PortfolioException(PortfolioErrorCode.PORTFOLIO_NOT_FOUND));

        assertThatThrownBy(() -> validator.validate(command(null, "가".repeat(1), null)))
                .isInstanceOf(PortfolioException.class)
                .extracting(e -> ((PortfolioException) e).getErrorCode())
                .isEqualTo(PortfolioErrorCode.PORTFOLIO_NOT_FOUND);
    }

    @Test
    void 정지된_계정이면_ACCOUNT_SUSPENDED로_면접_시작이_차단된다() {
        givenSuspendedAccount();

        assertThatThrownBy(() -> validator.validate(command(null, null, null)))
                .isInstanceOf(UserException.class)
                .extracting(e -> ((UserException) e).getErrorCode())
                .isEqualTo(UserErrorCode.ACCOUNT_SUSPENDED);
    }

    @Test
    void 게이트1_정지_검증이_나머지_모든_검증보다_먼저_실행된다() {
        // 포트폴리오도 없고 동의도 구버전인 계정이라도 정지가 먼저 걸려 A4를 띄운다(PRD Part7 5장).
        givenSuspendedAccount();

        assertThatThrownBy(() -> validator.validate(command("https://example.com/jd", "가".repeat(1), null)))
                .isInstanceOf(UserException.class)
                .extracting(e -> ((UserException) e).getErrorCode())
                .isEqualTo(UserErrorCode.ACCOUNT_SUSPENDED);

        verifyNoInteractions(consentUpToDateCheckUseCase, portfolioStatusUseCase, jdValidationCheckUseCase, findUserUseCase);
    }

    @Test
    void 동의가_구버전이면_CONSENT_VERSION_STALE로_면접_시작이_차단된다() {
        givenStaleConsent();

        assertThatThrownBy(() -> validator.validate(command(null, null, null)))
                .isInstanceOf(ConsentException.class)
                .extracting(e -> ((ConsentException) e).getErrorCode())
                .isEqualTo(ConsentErrorCode.CONSENT_VERSION_STALE);
    }

    @Test
    void 게이트3_동의_검증이_입력_검증보다_먼저_실행된다() {
        givenStaleConsent();

        assertThatThrownBy(() -> validator.validate(command(null, "가".repeat(1), null)))
                .isInstanceOf(ConsentException.class)
                .extracting(e -> ((ConsentException) e).getErrorCode())
                .isEqualTo(ConsentErrorCode.CONSENT_VERSION_STALE);

        verifyNoInteractions(portfolioStatusUseCase, jdValidationCheckUseCase, findUserUseCase);
    }

    private void givenSuspendedAccount() {
        willThrow(new UserException(UserErrorCode.ACCOUNT_SUSPENDED))
                .given(accountStatusCheckUseCase).checkNotSuspended(userId);
    }

    private void givenStaleConsent() {
        willThrow(new ConsentException(ConsentErrorCode.CONSENT_VERSION_STALE))
                .given(consentUpToDateCheckUseCase).checkUpToDate(userId);
    }
}
