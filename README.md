<!-- TODO: 배너 이미지 확정 후 아래 주석을 해제하고 docs/images/banner.png 를 추가하세요.
<div align="center">
  <img src="docs/images/banner.png" alt="hilit" width="100%" />
</div>
-->

<div align="center">

# hilit

**개발자에 최적화된 AI 면접대비 서비스**


![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.4.5-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![Spring AI](https://img.shields.io/badge/Spring_AI-1.1.8-6DB33F?style=flat-square&logo=spring&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL_+_pgvector-4169E1?style=flat-square&logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-FF4438?style=flat-square&logo=redis&logoColor=white)
![AWS](https://img.shields.io/badge/AWS-232F3E?style=flat-square&logo=amazonwebservices&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=docker&logoColor=white)
![GitHub Actions](https://img.shields.io/badge/GitHub_Actions-2088FF?style=flat-square&logo=githubactions&logoColor=white)

</div>

---

## 서비스 소개

준비 없이 보는 모의 면접은 "예상 질문 리스트"에서 끝납니다. hilit 은 사용자가 올린 **포트폴리오(PDF)** 와 **채용 공고(JD)** 를 직접 읽어, 그 사람의 경험에서 실제로 캐물을 만한 지점을 찾아 질문을 만듭니다.

면접은 음성으로 약 10분간 진행되며, 답변에 따라 꼬리 질문이 이어집니다. 끝나면 리포트와 면접 영상을 받고, 링크를 공유해 지인에게 피드백을 받을 수도 있습니다.

---

## 기술 스택

| 구분 | 사용 기술 |
|---|---|
| **언어 · 프레임워크** | Java 21, Spring Boot 3.4.5, Spring Security, Spring Data JPA |
| **데이터** | PostgreSQL 16 + pgvector (포트폴리오 청크 임베딩), Redis |
| **AI** | Spring AI — OpenAI(임베딩 · JD 추출 · 키워드 · TTS), Anthropic(면접 질문 생성 · 채점) |
| **인증** | JWT(jjwt), 카카오 · 애플 소셜 로그인 |
| **파일 처리** | PDFBox · Tika(PDF 텍스트 추출), Jsoup(JD 크롤링), ffmpeg(면접 영상 합성) |
| **인프라** | AWS S3 · ECR · CloudWatch, k3s + ArgoCD(GitOps), Caddy(TLS · 리버스 프록시) |
| **CI/CD** | GitHub Actions — 릴리즈 태그 발행 시 ECR 푸시 → GitOps(Hilit-GitOps) 이미지 태그 write-back → ArgoCD 배포 |

---

## 아키텍처

**헥사고날 아키텍처(Ports and Adapters)** 를 적용해 도메인을 프레임워크·인프라로부터 분리합니다.
의존 방향은 `Adapter → Application → Domain` 을 절대 준수하며, 상세 규칙은 [ARCHITECTURE.md](./ARCHITECTURE.md) 를 참고하세요.

<!-- TODO: 헥사고날 레이어 다이어그램 추가
<div align="center">
  <img src="docs/images/architecture-hexagonal.png" alt="헥사고날 아키텍처" width="800" />
</div>
-->

<!-- TODO: 인프라 구성도 추가 (EC2 · Caddy · 블루-그린 · RDS/pgvector · S3 · ECR · CloudWatch)
<div align="center">
  <img src="docs/images/architecture-infra.png" alt="인프라 구성도" width="800" />
</div>
-->

---

## 주요 기능

| 모듈 | 책임 |
|---|---|
| `auth` | 카카오 · 애플 소셜 로그인, JWT 발급 · 재발급 |
| `user` | 사용자 도메인, 회원 탈퇴 |
| `consent` | 약관 동의 문서 · 동의 이력 관리 |
| `portfolio` | 포트폴리오 PDF 업로드 → 텍스트 추출 → 청크 임베딩(pgvector) 저장 |
| `jd` | 채용 공고 URL 크롤링 → 본문 추출 → 키워드 분석 |
| `job` | 직무 · 직군 참조 데이터 |
| `interview` | 면접 세션 진행, 질문 preload, 답변 채점(axes · rubric), TTS, 리포트, 면접 영상 수명주기 |
| `ticket` | 이용권 예약 · 확정 · 반환(hold / commit / release) |
| `feedback` | 지인 피드백 공유 링크 발급, 게스트(무인증) 평가 제출 |
| `appversion` | 앱 최소 지원 버전 · 강제 업데이트 응답 |
| `common` | 공통 설정, 응답 포맷, 예외 기반 클래스, JWT 필터, `@CurrentUser` 리졸버 |

---

## 로컬 실행

### 요구 사항

- JDK 21
- Docker / Docker Compose
- ffmpeg, ffprobe *(면접 영상 합성 기능을 실행할 때만 필요)*

### 1. 환경 변수 설정

프로젝트 루트에 `.env` 파일을 만듭니다. `spring-dotenv` 가 실행 시 자동으로 로드합니다.

```dotenv
JWT_SECRET_KEY=
JWT_ACCESS_TOKEN_EXPIRY_MS=10800000
JWT_REFRESH_TOKEN_EXPIRY_MS=604800000

POSTGRES_PORT=5432
POSTGRES_DB=
POSTGRES_USER=
POSTGRES_PASSWORD=

APPLE_CLIENT_ID=
APPLE_TEAM_ID=
APPLE_KEY_ID=
APPLE_PRIVATE_KEY=

AWS_ACCESS_KEY_ID=
AWS_SECRET_ACCESS_KEY=
AWS_REGION=ap-northeast-2
AWS_S3_BUCKET=

OPENAI_API_KEY=
ANTHROPIC_API_KEY=
```

<!-- TODO: 실제 값 예시가 담긴 .env.example 을 커밋할지 팀과 논의 (.gitignore 에 이미 예외 처리되어 있음) -->

### 2. 로컬 인프라 기동

```bash
docker compose up -d postgres redis
```

### 3. 애플리케이션 실행

```bash
./gradlew bootRun
```

`dev` 프로파일로 실행하면 Swagger UI(`/swagger-ui/index.html`)가 활성화됩니다. 기본 프로파일에서는 비활성화되어 있습니다.

---

## 테스트

| 명령어 | 대상 | 전제 조건 |
|---|---|---|
| `./gradlew test` | 단위 테스트 (integration · llm-e2e · rag-eval · composite-smoke 제외) | 없음 |
| `./gradlew integrationTest` | DB · Redis 연동 통합 테스트 | 로컬 Postgres · Redis |
| `./gradlew llmE2eTest` | 리포트 파이프라인 e2e | `ANTHROPIC_API_KEY` **(API 비용 발생)** |
| `./gradlew ragEvalTest` | 포트폴리오 RAG 검색 품질 평가 (Recall@K, MRR) | 로컬 Postgres **(임베딩 API 비용 발생)** |
| `./gradlew compositeSmokeTest` | ffmpeg 영상 합성 스모크 | ffmpeg · ffprobe |

단일 테스트 클래스 실행:

```bash
./gradlew test --tests "com.yapp.d14.feedback.application.service.GuestFeedbackSubmitServiceTest"
```

### 개발용 CLI 태스크

```bash
# 테스트용 JWT 액세스 토큰 발급
./gradlew issueTestToken -PuserId=1 -Pprovider=KAKAO

# 지인 피드백 API 테스트용 완료 세션·영상 픽스처 시딩
./gradlew seedFeedbackFixture -PuserId=1 -PuserName=테스터

# 리포트 조회 API의 모든 응답 케이스 시딩
./gradlew seedReportShowcase -PuserId=1
```

---

## 프로젝트 구조

```
src/main/java/com/yapp/d14/{module}/
├── domain/                 # 순수 비즈니스 모델 (JPA·Spring 어노테이션 금지)
├── application/
│   ├── command/            # UseCase 진입 Command 객체
│   ├── port/
│   │   ├── in/             # 외부 → 헥사곤 진입점 인터페이스
│   │   └── out/            # 헥사곤 → 외부 요청 인터페이스
│   └── service/            # Port(in) 구현체 — 유스케이스 단위로 분리
├── exception/              # 도메인 전용 예외
└── adapter/
    ├── in/web/             # HTTP Controller + request/response DTO
    └── out/
        ├── persistence/    # JPA Repository + entity/
        ├── integration/    # S3, LLM 등 외부 시스템
        └── security/       # Security Adapter
```

면접 평가 축 · 루브릭 정의는 코드가 아니라 [`src/main/resources/interview-rubric/`](./src/main/resources/interview-rubric) 에서 관리합니다.

---

## 문서

| 문서 | 내용 |
|---|---|
| [ARCHITECTURE.md](./ARCHITECTURE.md) | 헥사고날 아키텍처 규칙, 패키지 구조, 네이밍, 금지 패턴 |
| [CONTRIBUTING.md](./CONTRIBUTING.md) | 브랜치 전략, 커밋 컨벤션, PR 규칙 |
| [docs/policy/s3-policy.md](./docs/policy/s3-policy.md) | S3 저장 경로 규칙 · 만료 · 삭제 정책 |

---

## 팀

<div align="center">

| <img src="https://github.com/nohy6630.png" width="120" /> | <img src="https://github.com/HamJina.png" width="120" /> |
|:---:|:--------------------------------------------------------:|
| **노영진** |                         **함지나**                          |
| [@nohy6630](https://github.com/nohy6630) |          [@HamJina](https://github.com/HamJina)          |
| 인프라 관리<br/>지인 피드백<br/>리포트 생성 |               면접 온보딩<br/>면접 진행<br/>auth 구현               |

</div>
