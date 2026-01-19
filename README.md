# Transactional Outbox Pattern with External File Storage

이 프로젝트는 **외부 스토리지(S3/MinIO) 파일 I/O로 인한 트랜잭션 지연 및 DB 커넥션 누수 문제**를 해결하기 위해 **Transactional Outbox Pattern**을 적용한 예제입니다.

특히 **DB 트랜잭션과 파일 I/O를 구조적으로 분리**하고, 파일 이동·삭제를 **커밋 이후(after commit)** 에 안전하게 처리하도록 설계되었습니다.

## 1. 문제 배경

### 기존 방식의 한계

- 파일 업로드/복사/삭제를 DB 트랜잭션 내부에서 수행
- 트랜잭션 커밋 지연
- HikariCP Connection Leak Detection 경고 발생
- DB는 커밋되었지만 파일 처리가 실패하는 정합성 문제 발생 가능

### 요구 사항

- DB 트랜잭션은 **최소 시간 유지**
- 파일 I/O는 **트랜잭션 커밋 이후에만 실행**
- 실패 시 **재시도 가능**
- 멀티 스레드/멀티 인스턴스 환경에서도 안전하게 동작

## 2. 해결 전략

### 핵심 아이디어

- **DB 트랜잭션 내부**
    - 도메인 엔티티 저장
    - OutboxEvent 기록
- **DB 트랜잭션 외부**
    - 파일 업로드/복사/삭제
- **afterCommit + 비동기 Worker**를 통한 후처리

> DB는 DB답게,  
> 파일 I/O는 커밋 이후에

## 3. 전체 처리 흐름

```text
Controller
  ↓
Facade
  ├─ MultipartFile 수신
  └─ 외부 스토리지에 임시 업로드 (tmp/)
        ↓
UseCase (@Transactional)
  ├─ Domain Entity 저장
  └─ OutboxEvent 저장
        ↓ (commit)
afterCommit
  ↓
OutboxWakeUpHintEvent
  ↓ (@Async)
OutboxWorker
  ├─ OutboxEvent 조회
  ├─ 상태 전이 (PENDING → PROCESSING)
  └─ Handler 실행
        ├─ copy (tmp → final)
        ├─ exists 확인
        └─ delete (tmp)
```

## 4. Outbox 처리 규칙

### 상태 전이

```text
PENDING
  ↓
PROCESSING
  ↓
COMPLETED / FAILED
```

### 재시도 정책

- 실패 시 retryCount 증가
- 최대 재시도 횟수 초과 시 FAILED 상태 전이
- 재시도 가능 이벤트는 다시 PENDING으로 복귀

## 5. 파일 처리 전략

### 처리순서

- copy(temporaryKey -> finalKey)
- exists (finalKey) 확인
- delete (temporaryKey)

### 이유

- 데이터 유실 방지
- S3/MinIO eventual consistency 대응
- Outbox 재시도 시 멱등성 확보

## 6. 패키지 구조

```text
application
 ├─ blog
 │   ├─ CreatePostUseCase
 │   └─ CreatePostFacade
 │
 └─ storage
     └─ port
         └─ FileStoragePort

infrastructure
 ├─ storage
 │   └─ s3
 │       └─ S3FileStorageAdapter
 │
 └─ outbox
     ├─ entity
     │   ├─ OutboxEvent
     │   ├─ OutboxEventStatus
     │   ├─ OutboxEventType
     │   └─ AggregateType
     │
     ├─ repository
     │   └─ OutboxEventRepository
     │
     ├─ serializer
     │   └─ OutboxPayloadSerializer
     │
     ├─ recorder
     │   └─ OutboxEventRecorder
     │
     ├─ publisher
     │   └─ OutboxWakeUpHintPublisher
     │
     ├─ event
     │   └─ OutboxWakeUpHintEvent
     │
     ├─ listener
     │   └─ OutboxWakeUpHintListener
     │
     ├─ handler
     │   ├─ OutboxEventHandler
     │   └─ post
     │       └─ PostFileUploadOutboxHandler
     │
     ├─ worker
     │   ├─ OutboxWorker
     │   └─ OutboxStatusService
     │
     └─ scheduler
         └─ OutboxPollingScheduler (fallback)
```

## 7. OutboxEvent 엔티티 설계 요약

- 상태 기반 처리 (PENDING / PROCESSING / COMPLETED / FAILED)
- 재시도 횟수 관리 (retryCount)
- 배치 처리를 위한 인덱스 (status, created_at)

## 8. 트리거 전략

### 기본 트리거 (메인)

- afterCommit 기반 즉시 실행
- OutboxEvent가 생성된 경우에만 Worker 실행
- 불필요한 DB 폴링 없음

### Scheduler (선택)

- 애플리케이션 재시작 / 예외 상황 대비용
- 상시 1초 폴링은 사용하지 않음
- 필요 시 주기를 크게 설정 (수십 초 ~ 수 분)

## 9. 이 구조의 장점

- DB 트랜잭션 커밋 시간 최소화
- 파일 I/O로 인한 커넥션 누수 방지
- 실패 시 안전한 재시도
- 멀티 스레드/멀티 인스턴스 환경에서도 안전

## 10. 정리

이 프로젝트는 **DB 트랜잭션과 외부 파일 I/O를 구조적으로 분리**하고, **Transactional Outbox Pattern을 통해 커밋 이후 비동기 처리를 보장**함으로써 실무 환경에서 발생하는 트랜잭션 지연과 커넥션 누수 문제를 해결합니다.