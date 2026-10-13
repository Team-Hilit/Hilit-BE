package com.yapp.d14.portfolio.adapter.in.web;

import com.yapp.d14.portfolio.adapter.in.web.request.PortfolioRegisterHttpRequest;
import com.yapp.d14.portfolio.adapter.in.web.response.PortfolioDeleteHttpResponse;
import com.yapp.d14.portfolio.adapter.in.web.response.PortfolioFileUrlHttpResponse;
import com.yapp.d14.portfolio.adapter.in.web.response.PortfolioListHttpResponse;
import com.yapp.d14.portfolio.adapter.in.web.response.PortfolioRegisterHttpResponse;
import com.yapp.d14.portfolio.adapter.in.web.response.PortfolioStatusHttpResponse;
import com.yapp.d14.common.response.ApiResponse;
import com.yapp.d14.common.web.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Tag(name = "Portfolio", description = "포트폴리오 API")
public interface PortfolioControllerDocs {

    @Operation(
            summary = "포트폴리오 등록",
            description = "PDF 포트폴리오를 업로드합니다.\n\n" +
                    "**인증**: Access Token 필요 (Authorization: Bearer {accessToken})\n\n" +
                    "- 등록 즉시 `PROCESSING` 상태로 202를 반환하고, S3 업로드·파싱·임베딩은 비동기로 처리됩니다.\n" +
                    "- 계정당 포트폴리오는 1개만 등록할 수 있습니다(기존 포트폴리오가 있으면 먼저 삭제해야 함).\n" +
                    "- 등록이 막히는 경우는 두 가지이며 응답 코드가 다릅니다. " +
                    "분석 중인 포트폴리오가 있으면 `PORTFOLIO_UPLOAD_IN_PROGRESS`(잠시 후 재시도하면 됨), " +
                    "완료된(`READY`) 포트폴리오가 있으면 `PORTFOLIO_ALREADY_EXISTS`(먼저 삭제해야 함)입니다.\n" +
                    "- 처리에 실패한(`FAILED_FILE`·`FAILED_SYSTEM`) 포트폴리오는 등록을 막지 않습니다. 따로 삭제하지 않고 바로 다시 업로드할 수 있습니다.\n" +
                    "- 삭제 후 재업로드(교체)는 캘린더 월 1회로 제한됩니다(매월 1일 0시 서버 시간 리셋). 최초 업로드는 이 제한과 무관합니다.\n" +
                    "- `READY`까지 완료된 업로드만 이 제한에 집계됩니다. 업로드 진행 중 취소했거나 처리에 실패한 건은 기회를 소진하지 않으며, " +
                    "그런 이력만 있는 계정의 다음 업로드는 여전히 최초 업로드로 취급됩니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "202",
                    description = "등록 접수 성공 — PROCESSING 상태로 생성"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "파일 형식·용량·페이지 수 위반",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(name = "PDF 아님", value = """
                                            {
                                              "success": false,
                                              "code": "INVALID_FILE_TYPE",
                                              "message": "PDF 파일만 올릴 수 있어요"
                                            }
                                            """),
                                    @ExampleObject(name = "용량 초과", value = """
                                            {
                                              "success": false,
                                              "code": "FILE_TOO_LARGE",
                                              "message": "파일이 너무 커요. 20MB 이하 PDF로 올려주세요"
                                            }
                                            """),
                                    @ExampleObject(name = "페이지 초과", value = """
                                            {
                                              "success": false,
                                              "code": "PAGE_COUNT_EXCEEDED",
                                              "message": "페이지가 너무 많아요. 30페이지 이하 PDF로 올려주세요"
                                            }
                                            """),
                                    @ExampleObject(name = "손상된 PDF", value = """
                                            {
                                              "success": false,
                                              "code": "INVALID_PDF_FILE",
                                              "message": "파일이 손상된 것 같아요. 파일을 확인하고 다시 시도해 주세요"
                                            }
                                            """)
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "분석 중이거나 이미 등록된 포트폴리오가 있음, 또는 이번 달 재업로드 횟수를 이미 사용함",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(name = "분석 중인 포트폴리오 존재", value = """
                                            {
                                              "success": false,
                                              "code": "PORTFOLIO_UPLOAD_IN_PROGRESS",
                                              "message": "이미 업로드 중인 포트폴리오가 있어요."
                                            }
                                            """),
                                    @ExampleObject(name = "등록된 포트폴리오 존재", value = """
                                            {
                                              "success": false,
                                              "code": "PORTFOLIO_ALREADY_EXISTS",
                                              "message": "이미 등록된 포트폴리오가 있어요. 기존 포트폴리오를 삭제한 뒤 새로 올려주세요."
                                            }
                                            """),
                                    @ExampleObject(name = "이번 달 재업로드 횟수 소진", value = """
                                            {
                                              "success": false,
                                              "code": "REPLACEMENT_LIMIT_EXCEEDED",
                                              "message": "포트폴리오 재업로드는 한 달에 한 번만 가능해요. 다음 달 1일부터 다시 시도해 주세요."
                                            }
                                            """)
                            }
                    )
            )
    })
    ResponseEntity<ApiResponse<PortfolioRegisterHttpResponse>> register(
            @Parameter(hidden = true) @CurrentUser UUID userId,
            @Parameter(description = "업로드할 PDF 파일") MultipartFile file,
            @Valid @ParameterObject PortfolioRegisterHttpRequest request
    );

    @Operation(
            summary = "포트폴리오 처리 상태 조회",
            description = "포트폴리오 등록 후 처리 상태를 폴링으로 조회합니다.\n\n" +
                    "**인증**: Access Token 필요 (Authorization: Bearer {accessToken})\n\n" +
                    "- 3~5초 간격으로 폴링하며 `PROCESSING`(계속) / `READY`(완료) / `FAILED_FILE`·`FAILED_SYSTEM`(실패)을 응답합니다.\n\n" +
                    "- `contentSummaryBullets`는 AI가 생성한 포트폴리오 콘텐츠 요약 불릿 목록(중립적 사실 서술형, 가변 개수)입니다. " +
                    "요약 생성이 실패해도 `READY` 전환에는 영향을 주지 않으며, 이 경우 빈 배열로 내려갑니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PortfolioStatusHttpResponse.class),
                            examples = {
                                    @ExampleObject(name = "PROCESSING — 분석 중", value = """
                                            {
                                              "success": true,
                                              "data": {
                                                "portfolioId": "b1f2c3d4-0000-0000-0000-000000000001",
                                                "status": "PROCESSING",
                                                "message": "포트폴리오를 분석하고 있어요.",
                                                "contentSummaryBullets": []
                                              }
                                            }
                                            """),
                                    @ExampleObject(name = "READY — 처리 완료", value = """
                                            {
                                              "success": true,
                                              "data": {
                                                "portfolioId": "b1f2c3d4-0000-0000-0000-000000000001",
                                                "status": "READY",
                                                "message": "포트폴리오 처리가 완료되었습니다.",
                                                "contentSummaryBullets": [
                                                  "백엔드 API 서버 설계 및 구현 경험",
                                                  "Kafka를 활용한 비동기 메시징 처리 수행",
                                                  "AWS 인프라 환경에서 서비스 배포 및 운영 참여"
                                                ]
                                              }
                                            }
                                            """),
                                    @ExampleObject(name = "FAILED_FILE — 파일 손상·암호 보호", value = """
                                            {
                                              "success": true,
                                              "data": {
                                                "portfolioId": "b1f2c3d4-0000-0000-0000-000000000001",
                                                "status": "FAILED_FILE",
                                                "message": "파일이 손상되었거나 암호로 보호되어 있어요. 다시 업로드해 주세요.",
                                                "contentSummaryBullets": []
                                              }
                                            }
                                            """),
                                    @ExampleObject(name = "FAILED_FILE — 텍스트 인식 불가(스캔본 등)", value = """
                                            {
                                              "success": true,
                                              "data": {
                                                "portfolioId": "b1f2c3d4-0000-0000-0000-000000000001",
                                                "status": "FAILED_FILE",
                                                "message": "텍스트를 인식할 수 없어요. 스캔본이 아닌 PDF 파일로 다시 업로드해 주세요.",
                                                "contentSummaryBullets": []
                                              }
                                            }
                                            """),
                                    @ExampleObject(name = "FAILED_SYSTEM — S3 업로드 실패", value = """
                                            {
                                              "success": true,
                                              "data": {
                                                "portfolioId": "b1f2c3d4-0000-0000-0000-000000000001",
                                                "status": "FAILED_SYSTEM",
                                                "message": "파일 업로드에 실패했어요. 잠시 후 다시 시도해 주세요.",
                                                "contentSummaryBullets": []
                                              }
                                            }
                                            """),
                                    @ExampleObject(name = "FAILED_SYSTEM — 분석(임베딩) 실패", value = """
                                            {
                                              "success": true,
                                              "data": {
                                                "portfolioId": "b1f2c3d4-0000-0000-0000-000000000001",
                                                "status": "FAILED_SYSTEM",
                                                "message": "포트폴리오 분석에 실패했어요. 잠시 후 다시 시도해 주세요.",
                                                "contentSummaryBullets": []
                                              }
                                            }
                                            """),
                                    @ExampleObject(name = "FAILED_SYSTEM — 처리 시간 초과", value = """
                                            {
                                              "success": true,
                                              "data": {
                                                "portfolioId": "b1f2c3d4-0000-0000-0000-000000000001",
                                                "status": "FAILED_SYSTEM",
                                                "message": "처리 시간이 초과되었어요. 다시 시도해 주세요.",
                                                "contentSummaryBullets": []
                                              }
                                            }
                                            """),
                                    @ExampleObject(name = "FAILED_SYSTEM — 비동기 처리 큐 포화(등록 직후 즉시 실패)", value = """
                                            {
                                              "success": true,
                                              "data": {
                                                "portfolioId": "b1f2c3d4-0000-0000-0000-000000000001",
                                                "status": "FAILED_SYSTEM",
                                                "message": "현재 요청이 많아 처리가 지연되고 있어요. 잠시 후 다시 시도해 주세요.",
                                                "contentSummaryBullets": []
                                              }
                                            }
                                            """)
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "포트폴리오가 존재하지 않거나 본인 소유가 아님",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "PORTFOLIO_NOT_FOUND",
                                      "message": "포트폴리오를 찾을 수 없어요."
                                    }
                                    """)
                    )
            )
    })
    ResponseEntity<ApiResponse<PortfolioStatusHttpResponse>> getStatus(
            @Parameter(hidden = true) @CurrentUser UUID userId,
            @Parameter(description = "포트폴리오 ID") UUID portfolioId
    );

    @Operation(
            summary = "내 포트폴리오 목록 조회",
            description = "로그인한 사용자가 등록한 포트폴리오 목록을 조회합니다.\n\n" +
                    "**인증**: Access Token 필요 (Authorization: Bearer {accessToken})\n\n" +
                    "- MVP는 계정당 1개로 제한되지만, `portfolios`는 향후 다건 확장을 고려해 배열로 내려갑니다.\n" +
                    "- 소프트 삭제된 포트폴리오는 `portfolios`에서 제외됩니다.\n" +
                    "- `portfolios`에 담기는 기준은 다음과 같습니다.\n" +
                    "  1. `PROCESSING`·`READY` 상태의 포트폴리오가 있으면 그것만 내려갑니다(둘은 동시에 존재할 수 없어 최대 1건).\n" +
                    "  2. 그런 포트폴리오가 없을 때는, 계정의 **가장 마지막 업로드 시도** 1건만 확인해 그것이 " +
                    "`FAILED_FILE`·`FAILED_SYSTEM`이고 아직 삭제되지 않았다면 그 1건을 내려보냅니다. " +
                    "업로드 실패를 알릴 수 있는 곳이 목록뿐이기 때문입니다.\n" +
                    "  3. 마지막 시도가 실패가 아니거나(취소·삭제된 완료 건 등) 이미 삭제한 실패 건이면 빈 배열입니다. " +
                    "지난 실패 건이 뒤늦게 다시 노출되지 않습니다.\n" +
                    "- 따라서 `portfolios[].status`로 `FAILED_FILE`·`FAILED_SYSTEM`이 내려올 수 있습니다. " +
                    "이 경우 해당 포트폴리오로는 면접을 시작할 수 없고 파일 조회도 불가하며, 재업로드를 유도해야 합니다. " +
                    "연속 실패해도 목록에는 항상 최근 1건만 나오므로 실패 건이 쌓여 보이지 않습니다.\n" +
                    "- `replaceAvailable`·`deleteAvailable`·`nextAvailableAt`·`nextDeleteAvailableAt`는 계정 단위로 결정되는 값이라 " +
                    "응답 최상위에 위치하며, `portfolios`가 빈 배열이어도(포트폴리오가 없어도) 항상 포함됩니다.\n" +
                    "- `replaceAvailable`: 이번 달 남은 재업로드(교체) 기회입니다. `true`면 1회 업로드 가능, " +
                    "`false`면 이미 이번 달 재업로드를 마쳐 업로드 시도 시 `REPLACEMENT_LIMIT_EXCEEDED`로 거부됩니다. " +
                    "`nextAvailableAt`: `false`일 때만 값이 채워지며 다시 가능해지는 시각(다음 달 1일 0시)을 나타냅니다.\n" +
                    "- `deleteAvailable`: 이번 달 남은 삭제 기회입니다. `replaceAvailable`과 독립적으로 집계되므로, " +
                    "재업로드 기회를 이미 썼어도 삭제 기회가 남아있으면 `true`이고, 반대로 삭제 기회를 이미 썼어도 재업로드 기회는 남아있을 수 있습니다. " +
                    "`false`면 `READY` 상태 포트폴리오의 삭제 시도가 `DELETE_LIMIT_EXCEEDED`로 거부됩니다. " +
                    "단 `PROCESSING`(업로드 중 취소)·`FAILED_FILE`·`FAILED_SYSTEM` 건의 삭제는 `deleteAvailable`이 `false`여도 항상 허용되며 기회를 소진하지 않습니다.\n" +
                    "`nextDeleteAvailableAt`: `false`일 때만 값이 채워지며 다시 가능해지는 시각(다음 달 1일 0시)을 나타냅니다.\n" +
                    "- `interviewInProgress`: 이 포트폴리오로 진행 중인 면접이 있으면 `true`이며, 이 경우 `deleteAvailable`과 무관하게 삭제 API 호출 시 " +
                    "`PORTFOLIO_DELETE_BLOCKED_BY_INTERVIEW`로 거부됩니다. `interviewInProgress=true`면 `deleteAvailable`이 `true`여도 " +
                    "실제로는 삭제가 불가능하므로, 삭제 버튼 활성화 여부는 반드시 `interviewInProgress`를 먼저 확인한 뒤 `deleteAvailable`을 봐야 합니다. " +
                    "`interviewInProgress`와 `deleteAvailable`을 함께 보면 " +
                    "삭제 불가 사유(면접 진행 중 vs 이번 달 삭제 기회 소진)를 구분해 안내 메시지를 다르게 보여줄 수 있습니다.\n\n" +
                    "- `portfolios[].contentSummaryBullets`는 AI가 생성한 포트폴리오 콘텐츠 요약 불릿 목록(중립적 사실 서술형, 가변 개수)입니다. " +
                    "요약 생성이 실패했거나 아직 `READY`가 아니면 빈 배열입니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PortfolioListHttpResponse.class),
                            examples = {
                                    @ExampleObject(name = "등록된 포트폴리오 없음(재업로드·삭제 가능 여부는 계정 단위로 여전히 내려감)", value = """
                                            {
                                              "success": true,
                                              "data": {
                                                "replaceAvailable": true,
                                                "nextAvailableAt": null,
                                                "deleteAvailable": false,
                                                "nextDeleteAvailableAt": "2026-09-01T00:00:00",
                                                "portfolios": []
                                              }
                                            }
                                            """),
                                    @ExampleObject(name = "처리 중(PROCESSING)", value = """
                                            {
                                              "success": true,
                                              "data": {
                                                "replaceAvailable": true,
                                                "nextAvailableAt": null,
                                                "deleteAvailable": true,
                                                "nextDeleteAvailableAt": null,
                                                "portfolios": [{
                                                  "portfolioId": "b1f2c3d4-0000-0000-0000-000000000001",
                                                  "fileName": "portfolio.pdf",
                                                  "fileSize": 1048576,
                                                  "pageCount": 12,
                                                  "status": "PROCESSING",
                                                  "uploadedAt": null,
                                                  "interviewInProgress": false,
                                                  "contentSummaryBullets": []
                                                }]
                                              }
                                            }
                                            """),
                                    @ExampleObject(name = "완료(READY) — 삭제·재업로드 기회 모두 있음", value = """
                                            {
                                              "success": true,
                                              "data": {
                                                "replaceAvailable": true,
                                                "nextAvailableAt": null,
                                                "deleteAvailable": true,
                                                "nextDeleteAvailableAt": null,
                                                "portfolios": [{
                                                  "portfolioId": "b1f2c3d4-0000-0000-0000-000000000002",
                                                  "fileName": "portfolio.pdf",
                                                  "fileSize": 1048576,
                                                  "pageCount": 12,
                                                  "status": "READY",
                                                  "uploadedAt": "2026-07-01T10:00:00",
                                                  "interviewInProgress": false,
                                                  "contentSummaryBullets": [
                                                    "백엔드 API 서버 설계 및 구현 경험",
                                                    "Kafka를 활용한 비동기 메시징 처리 수행",
                                                    "AWS 인프라 환경에서 서비스 배포 및 운영 참여"
                                                  ]
                                                }]
                                              }
                                            }
                                            """),
                                    @ExampleObject(name = "완료(READY) — 이번 달 삭제·재업로드 기회 모두 소진", value = """
                                            {
                                              "success": true,
                                              "data": {
                                                "replaceAvailable": false,
                                                "nextAvailableAt": "2026-08-01T00:00:00",
                                                "deleteAvailable": false,
                                                "nextDeleteAvailableAt": "2026-08-01T00:00:00",
                                                "portfolios": [{
                                                  "portfolioId": "b1f2c3d4-0000-0000-0000-000000000003",
                                                  "fileName": "portfolio.pdf",
                                                  "fileSize": 1048576,
                                                  "pageCount": 12,
                                                  "status": "READY",
                                                  "uploadedAt": "2026-07-15T10:00:00",
                                                  "interviewInProgress": false,
                                                  "contentSummaryBullets": []
                                                }]
                                              }
                                            }
                                            """),
                                    @ExampleObject(name = "완료(READY) — 삭제 기회만 소진(재업로드는 아직 가능, 서로 독립)", value = """
                                            {
                                              "success": true,
                                              "data": {
                                                "replaceAvailable": true,
                                                "nextAvailableAt": null,
                                                "deleteAvailable": false,
                                                "nextDeleteAvailableAt": "2026-08-01T00:00:00",
                                                "portfolios": [{
                                                  "portfolioId": "b1f2c3d4-0000-0000-0000-000000000007",
                                                  "fileName": "portfolio.pdf",
                                                  "fileSize": 1048576,
                                                  "pageCount": 12,
                                                  "status": "READY",
                                                  "uploadedAt": "2026-07-22T10:00:00",
                                                  "interviewInProgress": false,
                                                  "contentSummaryBullets": []
                                                }]
                                              }
                                            }
                                            """),
                                    @ExampleObject(name = "완료(READY) — deleteAvailable=true지만 진행 중인 면접이 있어 삭제 불가(interviewInProgress 우선 확인)", value = """
                                            {
                                              "success": true,
                                              "data": {
                                                "replaceAvailable": true,
                                                "nextAvailableAt": null,
                                                "deleteAvailable": true,
                                                "nextDeleteAvailableAt": null,
                                                "portfolios": [{
                                                  "portfolioId": "b1f2c3d4-0000-0000-0000-000000000004",
                                                  "fileName": "portfolio.pdf",
                                                  "fileSize": 1048576,
                                                  "pageCount": 12,
                                                  "status": "READY",
                                                  "uploadedAt": "2026-07-20T10:00:00",
                                                  "interviewInProgress": true,
                                                  "contentSummaryBullets": []
                                                }]
                                              }
                                            }
                                            """),
                                    @ExampleObject(name = "처리 실패(FAILED_FILE·FAILED_SYSTEM)", value = """
                                            {
                                              "success": true,
                                              "data": {
                                                "replaceAvailable": true,
                                                "nextAvailableAt": null,
                                                "deleteAvailable": true,
                                                "nextDeleteAvailableAt": null,
                                                "portfolios": [{
                                                  "portfolioId": "b1f2c3d4-0000-0000-0000-000000000005",
                                                  "fileName": "portfolio.pdf",
                                                  "fileSize": 1048576,
                                                  "pageCount": 12,
                                                  "status": "FAILED_FILE",
                                                  "uploadedAt": null,
                                                  "interviewInProgress": false,
                                                  "contentSummaryBullets": []
                                                }]
                                              }
                                            }
                                            """)
                            }
                    )
            )
    })
    ResponseEntity<ApiResponse<PortfolioListHttpResponse>> getList(
            @Parameter(hidden = true) @CurrentUser UUID userId
    );

    @Operation(
            summary = "포트폴리오 파일 열람 URL 발급",
            description = "포트폴리오 목록에서 항목을 클릭했을 때 PDF 원본을 열람할 수 있는 URL을 발급합니다.\n\n" +
                    "**인증**: Access Token 필요 (Authorization: Bearer {accessToken})\n\n" +
                    "- 응답의 `fileUrl`은 S3 presigned GET URL이며 발급 후 10분간만 유효합니다. 매번 새로 호출해 발급받아야 합니다.\n" +
                    "- `portfolioId`로 지정한 포트폴리오는 반드시 `READY` 상태여야 합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "발급 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "아직 처리 중이거나 처리에 실패한 포트폴리오",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(name = "포트폴리오 처리 중", value = """
                                            {
                                              "success": false,
                                              "code": "PORTFOLIO_PROCESSING",
                                              "message": "포트폴리오를 아직 분석하고 있어요. 잠시 후 다시 시도해 주세요."
                                            }
                                            """),
                                    @ExampleObject(name = "포트폴리오 처리 실패", value = """
                                            {
                                              "success": false,
                                              "code": "PORTFOLIO_UPLOAD_FAILED",
                                              "message": "포트폴리오 처리에 실패했어요. 다시 업로드해 주세요."
                                            }
                                            """)
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "포트폴리오가 존재하지 않거나 본인 소유가 아님",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "PORTFOLIO_NOT_FOUND",
                                      "message": "포트폴리오를 찾을 수 없어요."
                                    }
                                    """)
                    )
            )
    })
    ResponseEntity<ApiResponse<PortfolioFileUrlHttpResponse>> getFileUrl(
            @Parameter(hidden = true) @CurrentUser UUID userId,
            @Parameter(description = "포트폴리오 ID") UUID portfolioId
    );

    @Operation(
            summary = "포트폴리오 삭제",
            description = "포트폴리오를 삭제합니다.\n\n" +
                    "**인증**: Access Token 필요 (Authorization: Bearer {accessToken})\n\n" +
                    "- 진행 중인 면접이 이 포트폴리오를 사용 중이면 삭제할 수 없습니다.\n" +
                    "- 이번 달 삭제 기회를 이미 사용했다면(목록 조회 응답의 `deleteAvailable=false`) 삭제할 수 없습니다. " +
                    "재업로드 기회(`replaceAvailable`)와는 독립적으로 집계되므로, 재업로드 기회 소진 여부와 무관하게 삭제 기회만으로 판단합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "삭제 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "포트폴리오가 존재하지 않거나 본인 소유가 아님",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "PORTFOLIO_NOT_FOUND",
                                      "message": "포트폴리오를 찾을 수 없어요."
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "진행 중인 면접이 사용 중이거나, 이번 달 삭제 횟수를 이미 사용해 삭제할 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(name = "진행 중인 면접이 사용 중", value = """
                                            {
                                              "success": false,
                                              "code": "PORTFOLIO_DELETE_BLOCKED_BY_INTERVIEW",
                                              "message": "진행 중인 면접이 있어 포트폴리오를 삭제할 수 없어요. 면접 종료 후 다시 시도해 주세요."
                                            }
                                            """),
                                    @ExampleObject(name = "이번 달 삭제 횟수 소진", value = """
                                            {
                                              "success": false,
                                              "code": "DELETE_LIMIT_EXCEEDED",
                                              "message": "포트폴리오 삭제는 한 달에 한 번만 가능해요. 다음 달 1일부터 다시 시도해 주세요."
                                            }
                                            """)
                            }
                    )
            )
    })
    ResponseEntity<ApiResponse<PortfolioDeleteHttpResponse>> delete(
            @Parameter(hidden = true) @CurrentUser UUID userId,
            @Parameter(description = "포트폴리오 ID") UUID portfolioId
    );
}
