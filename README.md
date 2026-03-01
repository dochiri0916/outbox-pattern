# Transactional Outbox Pattern with File Storage

게시글 생성 시 DB 데이터와 파일 스토리지(S3) 사이 정합성 문제를 해결하기 위해 Transactional Outbox Pattern을 적용한 프로젝트다.

## 문제 정의

게시글 저장과 파일 처리를 한 트랜잭션/요청 흐름에 강하게 결합하면 다음 문제가 생긴다.

- 외부 스토리지 지연으로 DB 트랜잭션이 길어짐
- DB 커밋 성공 후 파일 처리 실패 시 데이터 불일치
- 실패 재처리/복구 흐름이 불명확함

이 프로젝트는 파일 I/O를 트랜잭션 밖으로 분리하고, Outbox 이벤트 기반으로 커밋 이후 처리한다.

## 아키텍처 요약

- API 계층: `POST /api/v1/posts` (multipart)
- Application 계층:
  - `CreatePostFacade`: 임시 경로 업로드
  - `CreatePostUseCase`: Post 저장 + Outbox 이벤트 기록
- Outbox 계층:
  - `OutboxEventRecorder`: 이벤트 저장 + after-commit wake-up
  - `OutboxWorker`: `PENDING` 배치 처리
  - `PostFileUploadOutboxHandler`: copy/검증/delete/PostFile 저장
  - `OutboxPollingScheduler`: 즉시 트리거 실패 시 주기 재처리
- Storage 계층:
  - `FileStoragePort`
  - `S3FileStorageAdapter`

## 처리 흐름

```text
POST /api/v1/posts (multipart)
  -> CreatePostFacade
     -> temporary/* 업로드
     -> CreatePostUseCase (@Transactional)
        -> Post 저장
        -> OutboxEvent(POST_FILE_UPLOAD, PENDING) 저장
        -> commit
  -> afterCommit wake-up event
  -> OutboxWorker.runOnce()
     -> PENDING 조회
     -> PROCESSING 전이(락)
     -> Handler 실행
        -> copy(temp -> final)
        -> exists(final) 검증
        -> delete(temp)
        -> PostFile 저장
     -> COMPLETED 또는 retry/FAILED
```

## Outbox 상태 전이

```text
PENDING -> PROCESSING -> COMPLETED
                      -> PENDING (retry)
                      -> FAILED  (max retry 초과)
```

- 최대 재시도: `5`
- 배치 크기: `10`
- Polling: `fixedDelay = 30_000`

## 패키지 구조

```text
application
  blog
    CreatePostFacade
    CreatePostUseCase
  storage.port
    FileStoragePort

common.outbox
  PostFileUploadPayload

domain.blog
  Post
  PostFile

infrastructure.outbox
  entity / repository / recorder / serializer
  publisher / listener / poller / worker / handler

infrastructure.storage.s3
  S3FileStorageAdapter

presentation.post
  PostController
```

## 실행

요구사항:

- Java 25
- Gradle Wrapper

실행:

```bash
./gradlew test
./gradlew bootRun
```

기본 설정:

- DB: H2 in-memory
- S3 endpoint: `http://localhost:4566` (LocalStack 기준)

## 상세 글

설계 배경, 트러블슈팅, 리팩토링 과정은 벨로그 글에 정리.

- [velog](https://velog.io/@dochiri0916/Outbox-Pattern%EC%9C%BC%EB%A1%9C-%ED%8A%B8%EB%9E%9C%EC%9E%AD%EC%85%98-%EC%9D%B4%ED%9B%84-%EC%9E%91%EC%97%85%EC%9D%84-%EB%B3%B4%EC%9E%A5%ED%95%98%EA%B2%8C-%EB%90%9C-%EC%9D%B4%EC%9C%A0)

## 커밋 메시지 규칙

- Conventional Commits 1.0.0 형식 사용
- 기본 형식: `<type>: <한국어 설명>`
- scope 미사용
- 설명은 한 줄 요약으로 대상과 변경 핵심만 작성
