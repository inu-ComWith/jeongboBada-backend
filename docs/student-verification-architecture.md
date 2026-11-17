# 재학 확인(학생 인증) 아키텍처

재학생 인증의 설계와 진행 상태. GitHub #142.
파일 저장(S3) 공용 컴포넌트는 [file-upload-architecture.md](file-upload-architecture.md)에 적는다.

## 배경

- 가입한 사람이 실제 인천대 재학생인지 확인해야 한다
- 가입할 때 신분 확인 서류를 함께 올림 → 운영진이 검토 → 승인/반려 → 결과를 메일로 안내 ([wireframe-spec.md](wireframe-spec.md) 1번)
- 서류는 S3(비공개 버킷)의 `verification/`에 저장하고, 승인·반려하면 바로 삭제한다

## 결정된 것

| 항목 | 결정 | 근거 |
|---|---|---|
| 업로드 방식 | 서버 경유(multipart). 가입 요청에 서류를 함께 보낸다 | #98, 2026-09-26 |
| 서류 조건 | pdf·jpg, 최대 10MB, 1개 (`FileType.PDF`, `FileType.JPG`) | [wireframe-spec.md](wireframe-spec.md) 1번 |
| 인증 상태 | `User`에 `VerificationStatus`(`PENDING`/`APPROVED`/`REJECTED`). 가입하면 `PENDING` | |
| 기존 가입자 | `APPROVED`로 둔다. 서류가 없는 계정을 `PENDING`으로 두면 로그인이 막히고 풀 방법이 없다. 계정은 지우지 않는다 | 2026-11-15 |
| 승인 대기·반려 로그인 | 토큰을 주지 않고 거절한다. 상태별 code로 응답하고, 승인 대기는 신청일·반려는 반려 사유를 함께 준다 (화면이 상태별 팝업을 띄움). 상태 검사는 **비밀번호 확인 뒤**에 한다 (학번 가입 여부 노출 방지) | [wireframe-spec.md](wireframe-spec.md) 2번, #142, 2026-11-15 |
| 반려 후 재제출 | 허용. 반려 사용자는 토큰이 없으므로 재제출 API가 학번 + 비밀번호 + 새 서류를 함께 받아 본인을 확인한다. 성공하면 다시 `PENDING` | [wireframe-spec.md](wireframe-spec.md) 1·2번, 2026-11-15 |
| 결과 안내 | 승인·반려하면 메일을 보낸다 (기존 메일 발송 재사용) | [wireframe-spec.md](wireframe-spec.md) 1번 |
| 관리자 화면 | 프론트 안의 관리자 페이지 4장: 대기 목록 / 서류 검토 / 승인 목록 / 거절 목록. 검색 없음, 대기는 먼저 가입한 순. 반려 사유는 선택 입력 | #142 (피그마, 2026-09-30) |
| 관리자 서류 열람 | 관리자가 열 때마다 presigned GET URL을 새로 만든다. 만료 5분 | 2026-11-15 |
| 서류 보관 | 승인·반려하면 바로 삭제. 처리되지 않은 서류도 S3 수명주기 규칙으로 업로드 30일 뒤 자동 삭제 | 와이어프레임 안내 문구, 2026-11-15 |
| 버킷 접근 | 퍼블릭 액세스 차단. 서버(자격 증명)와 presigned URL로만 접근 | #98 |
| 관리자 API 보호 | `SecurityConfig`에서 `/api/admin/**`를 `hasRole("ADMIN")`로 막는다 (2단계에서 `@PreAuthorize` 대신 URL 규칙으로 정함. 요청 검증보다 먼저 걸려 토큰 없는 요청이 401이 된다) | [auth-architecture.md](auth-architecture.md) 6번 |
| 별도 업로드 API | 두지 않는다. 서류는 가입·재제출 요청 안에서만 받는다 (로그인 없이 계정과 무관한 파일이 쌓이는 것 방지) | #142 |
| 성적 인증 기능 | **범위에서 제외**. 가입 서류 중 "성적증명서"는 본인 확인 서류일 뿐 이 기능과 무관 | 2026-11-15 |

### 열람 URL과 서류 보관을 따로 정한 이유
- URL은 "서류 보기"를 누를 때마다 새로 만들어지므로, 짧게 만료돼도 관리자가 확인하는 데 문제가 없다. 밖으로 새도 금방 못 쓰게 되니 짧을수록 안전하다
- 서류는 30일 뒤 자동 삭제된다. 검토는 보통 1~2일이라 충분하고, 삭제에 실패했거나 처리되지 않은 개인정보 서류가 계속 남지 않게 한다
  - 30일 안에 처리하지 못해 서류가 지워졌으면, 관리자가 반려하고 사용자가 다시 제출하게 한다

## 흐름

1. 가입: `POST /api/auth/signup` (multipart: 기존 입력값 + 서류) → S3 업로드 → `PENDING`으로 저장
   - DB 저장이 실패하면 방금 올린 서류를 S3에서 지운다
2. 로그인: 비밀번호 확인 뒤 `PENDING`/`REJECTED`면 토큰 없이 상태별 code로 거절
3. 관리자: 대기 목록 조회 → 서류 열람(presigned URL 5분) → 승인 또는 반려(사유 선택)
   - 처리하면 S3 서류 삭제 + 결과 메일. S3 삭제가 실패해도 처리는 유지하고 key를 로그로 남긴다
4. 반려 후 재제출: 학번 + 비밀번호 + 새 서류 → S3 업로드 → `PENDING`

구현 순서는 흐름 순서와 다르다. 로그인 차단(2)은 관리자 승인(3)이 동작한 뒤 마지막에 켠다 ("진행 상태" 4단계).

## 진행 상태

### 0. S3 연동
- [x] 공용 업로드 컴포넌트 `global/file/FileStorage` (#169)
- [x] Learner Lab 버킷에서 실제 업로드·presigned URL·삭제 확인 ([file-upload-architecture.md](file-upload-architecture.md) 3단계)

### 1. 가입 상태 + 서류 첨부 가입
- [x] `User`에 `VerificationStatus`(`VERIFICATION_STATUS`), 서류 S3 key(`VERIFICATION_DOCUMENT_KEY`) 추가
  - 기존 가입자는 컬럼 기본값 `APPROVED`로 처리한다 (`@ColumnDefault`). `ddl-auto: update`가 기존 행에 기본값을 채운다
  - 신청일은 지금 가입일(`CREATED_AT`)과 같아서 따로 두지 않는다. 재제출(3단계)에서 달라지면 그때 추가한다
  - 반려 사유는 반려 API(2단계)에서 추가한다
- [x] 로그인은 아직 막지 않는다 (4단계 참고)
- [x] `POST /api/auth/signup` multipart 전환, 서류 필수
  - 폼 필드와 서류(`file`)를 `SignupRequest` 하나로 받는다(`@ModelAttribute`). Swagger에서 칸이 펼쳐져 바로 시험할 수 있다
  - 서류가 없으면 Spring 기본 400 대신 `FILE_EMPTY`가 나가도록 `file`은 필수로 두지 않고 `FileStorage`가 검사한다
  - 중복 확인 → 업로드 → `saveAndFlush` 순서. 저장이 실패하면 올린 서류를 지운다 (삭제 실패는 로그만, 수명주기 규칙이 정리)
  - JSON으로 보내면 415이고 `message`에 `multipart/form-data`를 안내한다 (`GlobalExceptionHandler.handleHttpMediaTypeNotSupported`)
- [x] `api-spec.md`, `erd.md` 수정
- [x] 통합 테스트 `SignupVerificationDocumentIntegrationTest` (성공 / 서류 없음 / 형식 오류 / 기존 계정 기본값), 동시 가입 시 서류 정리는 `ConcurrentSignupIntegrationTest`
  - 가입을 쓰는 기존 통합 테스트는 `SignupRequests.signup(...)`으로 multipart를 보내고 `FileStorage`를 `@MockitoBean`으로 바꾼다
- [x] CI 스모크(`scripts/ci/compose-smoke.sh`)는 가입 API 대신 DB에 계정을 넣고 로그인을 확인한다 (CI에는 AWS 키가 없음)
- [x] Learner Lab 버킷으로 실제 가입 확인: 201, `verification/{UUID}.pdf` 저장, 상태 `PENDING` (2026-11-15)
- [x] 프론트 영향 확인: 프론트가 가입 API를 아직 연동하지 않아 깨지는 화면은 없다. 연동할 때 Swagger의 multipart 형식을 따른다 (2026-11-15)

### 2. 관리자 검토 API (`ROLE_ADMIN`)
API는 `/api/admin/verifications` (`AdminVerificationController`, `AdminVerificationService`). 명세는 `api-spec.md`.
- [x] 대기·승인·거절 목록 (닉네임, 학번, 이메일, 가입일 + 서류 형식 / 승인일 / 거절일·거절 사유)
  - `GET ?status=`. 대기는 먼저 가입한 순, 승인·거절은 최근 처리한 순
  - 검토 없이 승인 상태인 기존 계정(처리 시각 null)은 승인 목록에서 뺀다. 관리자가 처리한 건만 보여주기 위해서다
  - 승인일·거절일은 `VERIFICATION_PROCESSED_AT` 하나에 저장하고 상태로 구분한다
  - 페이지 나누기는 없다 (가입 신청 규모가 작고 화면에도 없음)
- [x] 서류 열람: 상세 조회(`GET /{userId}`) 때마다 presigned URL(5분)을 새로 만든다. 만들지 못하면 503 `FILE_URL_FAILED`
- [x] 승인 / 반려(사유 선택, 200자) → S3 서류 삭제 + 결과 메일
  - 행을 잠그고 `PENDING`인지 확인한다. 아니면 409 `VERIFICATION_ALREADY_PROCESSED` (두 관리자가 동시에 처리해도 한 번만 처리)
  - 서류 삭제와 메일은 **커밋 뒤**(`TransactionSynchronization.afterCommit`)에 한다. 커밋 전에 지우면 롤백됐을 때 서류만 사라진다
  - 둘 다 실패해도 처리는 유지하고 로그만 남긴다. 이메일이 없는 기존 계정은 메일을 건너뛴다
- [x] 권한: `SecurityConfig`에서 `/api/admin/**`를 `hasRole("ADMIN")` (토큰 없으면 401, 일반 사용자면 403). 컨트롤러가 아니라 URL로 막아 요청 검증보다 권한 검사가 먼저 걸린다
- [x] Swagger 자물쇠, 통합 테스트 `AdminVerificationIntegrationTest` (승인 / 거절 사유·메일 / 이미 처리 409 / 없는 사용자 404 / 일반 사용자 403·토큰 없음 401)
- [ ] 관리자 계정 만들기: 아직 API가 없어 DB에서 직접 바꾼다 (`docs/auth-architecture.md` 관리자 승격 SQL)

### 3. 반려 후 재제출
- [ ] 학번 + 비밀번호 + 새 서류 → `PENDING`, 이전 서류 key 교체
- [ ] 통합 테스트

### 4. 승인 전 로그인 차단 켜기
**1~3단계가 배포되고 관리자가 실제로 승인할 수 있는 걸 확인한 뒤에 켠다.** 먼저 켜면 새 가입자가 로그인도 못 하고 승인받을 방법도 없는 기간이 생긴다 (#142).
- [ ] 승인 대기 → 신청일, 반려 → 반려 사유를 담아 거절 (`UserErrorCode` + `api-spec.md` 에러 코드 표)
- [ ] 기존 통합 테스트 정리: 대부분 "가입 → 로그인" 순서라 테스트에서 승인 처리가 필요하다
- [ ] 통합 테스트 (승인 전 로그인 거부)

## 브랜치
- feat/s3-upload (공용 업로드 컴포넌트, #169 머지됨)
- 위 1~4는 각각 별도 PR로 올린다
