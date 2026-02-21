# Transactional Outbox Pattern with File Storage

이 프로젝트는 게시글 생성과 파일 처리에서 발생하는 정합성 문제를 해결하기 위해 Transactional Outbox Pattern을 적용한 예제다.
핵심 목표는 단순하다. 데이터베이스 트랜잭션은 짧게 유지하고, 파일 I/O는 커밋 이후 안전하게 처리한다.

---

## 왜 Outbox인가

파일 업로드/복사/삭제를 트랜잭션 내부에서 처리하면 다음 문제가 쉽게 발생한다.

- 트랜잭션이 길어져 커넥션 점유 시간이 길어진다.
- 외부 스토리지 지연이 DB 성능 저하로 이어진다.
- DB 커밋 이후 파일 처리 실패 시 복구가 어렵다.

Outbox는 "파일 작업을 직접 트랜잭션에 넣는 방식" 대신, "파일 작업이 필요하다는 사실을 트랜잭션으로 기록"하는 방식으로 이 문제를 분리한다.

---

## 처리 흐름

```text
POST /posts (multipart)
  ↓
CreatePostFacade
  ├─ temporary key 생성
  ├─ 파일을 temporary 경로에 선 업로드
  └─ UseCase 호출
        ↓
CreatePostUseCase (@Transactional)
  ├─ Post 저장
  └─ OutboxEvent 저장 (POST_FILE_UPLOAD)
        ↓ (commit)
OutboxWakeUpHintPublisher.afterCommit()
  ↓
OutboxWakeUpHintListener (@Async("outboxExecutor"))
  ↓
OutboxWorker
  ├─ PENDING 배치 조회
  ├─ markProcessing (비관적 락)
  └─ Handler 실행
       ├─ copy(temp -> final)
       ├─ exists(final) 검증
       ├─ delete(temp)
       └─ PostFile 저장
```

Scheduler 안전망도 활성화되어 있어 이벤트 힌트가 누락돼도 주기적으로 PENDING 이벤트를 재처리한다.

---

## 상태 머신과 재시도

OutboxEvent는 상태 전이를 엔티티 내부로 캡슐화한다.

```text
PENDING -> PROCESSING -> COMPLETED
                      -> PENDING (retry)
                      -> FAILED  (max retry 초과)
```

- 최대 재시도 횟수: 5
- `failed()`는 `PROCESSING` 상태에서만 호출 가능
- 조회 쿼리는 `status, created_at` 인덱스를 사용해 배치 처리

---

## 현재 리팩토링 상태

리뷰 문서 기준 핵심 수정 사항이 모두 반영됐다.

- Worker 이중 실행 버그 제거
- 임시 업로드 누락 수정
- `@Async("outboxExecutor")` 명시
- `markProcessing()` 반환을 DTO 컨텍스트로 변경
- payload DTO를 공용 패키지로 분리
- Worker 실패 로그 추가
- Handler `@Transactional` 적용
- Polling Scheduler 활성화 (`fixedDelay = 30_000`)
- `OutboxEvent.failed()` 상태 가드 추가
- H2 인메모리 설정 및 테스트 환경 정비

---

## 실행 방법

### 요구사항

- Java 25
- Gradle Wrapper

### 테스트 실행

```bash
./gradlew test
```

현재 기본 설정은 H2 인메모리 DB를 사용한다.

---

## 패키지 요약

```text
application
  blog
    CreatePostFacade
    CreatePostUseCase
  storage.port
    FileStoragePort

common.outbox
  PostFileUploadPayload

infrastructure.outbox
  entity / repository / serializer / recorder / publisher
  listener / handler / worker / poller

infrastructure.storage.s3
  S3FileStorageAdapter
```

---

## 문서

- 리팩토링 리뷰: `documents/REVIEW.md`
- 벨로그 초안: `documents/VELOG.md`
- 벨로그 레퍼런스: `documents/VELOG_REFERENCE.md`
