# Outbox Pattern

파일 I/O가 포함된 쓰기 흐름에서, 트랜잭션은 짧게 유지하면서 커밋 이후 작업 유실을 방지하기 위해 Transactional Outbox를 검증한 프로젝트입니다.
여기에는 실행 가능한 코드와 핵심 요약만 두고, 설계 배경과 선택 이유는 블로그에 정리했습니다.

## 실험 범위

- `Post` 저장과 `OutboxEvent` 저장을 하나의 트랜잭션으로 처리합니다.
- 커밋 후 `OutboxWorker`가 이벤트를 비동기로 처리합니다.
- 실패 시 상태 전이(`PENDING -> PROCESSING -> FAILED/COMPLETED`)와 재시도로 복구합니다.
- 즉시 힌트 발행(`afterCommit`) + 폴링 스케줄러를 함께 사용해 유실 위험을 완화합니다.

## Outbox Worker 운영

- 이벤트 claim은 기본적으로 `SKIP_LOCKED` 전략을 사용해 잠긴 이벤트를 건너뛰며, 데이터베이스가 해당 lock hint를 지원하지 않으면 `PESSIMISTIC_WRITE`로 fallback합니다. 운영 환경에서 `outbox.worker.claim-strategy`로 fallback 전략을 명시할 수 있고, fallback lock 대기는 `OUTBOX_LOCK_WAIT_TIMEOUT_MS`로 조정합니다.
- claim은 이벤트 하나만 `PROCESSING`으로 전이하는 짧은 트랜잭션으로 끝나고, handler 실행과 외부 I/O는 트랜잭션 커밋 이후에 수행합니다. `batch-size`는 한 번의 polling에서 claim할 최대 이벤트 수입니다.
- 영구 오류(`INVALID_PAYLOAD`, `UNSUPPORTED_EVENT_TYPE`, `INVALID_STAGED_FILE`, `AGGREGATE_STATE_CONFLICT`, `UNKNOWN_FAILURE`)는 즉시 Dead Letter(`FAILED`)로 보내고, 네트워크·스토리지 오류는 지수 백오프 후 재시도합니다. 실패 code, 마지막 예외 타입, 최초 실패 시각은 이벤트에 기록됩니다.
- 일반 처리 결과와 queue 상태는 Micrometer로 기록합니다. `/actuator/metrics`에서 pending/oldest age, 처리 시간, retry·terminal failure, timeout recovery, claim 실패 지표를 확인할 수 있습니다.
- 운영 재처리는 `POST /admin/outbox-events/{id}/retry`, retry count 초기화는 별도 `POST /admin/outbox-events/{id}/retry/reset` 경로를 사용합니다.

## 자세한 내용

[Outbox Pattern으로 트랜잭션 이후 작업을 보장하게 된 이유](https://velog.io/@dochiri0916/Outbox-Pattern%EC%9C%BC%EB%A1%9C-%ED%8A%B8%EB%9E%9C%EC%9E%AD%EC%85%98-%EC%9D%B4%ED%9B%84-%EC%9E%91%EC%97%85%EC%9D%84-%EB%B3%B4%EC%9E%A5%ED%95%98%EA%B2%8C-%EB%90%9C-%EC%9D%B4%EC%9C%A0)
<br>
[Outbox Worker가 처리 중 죽으면 PROCESSING 이벤트는 어떻게 복구할까](https://velog.io/@dochiri0916/Outbox-Worker%EA%B0%80-%EC%B2%98%EB%A6%AC-%EC%A4%91-%EC%A3%BD%EC%9C%BC%EB%A9%B4-PROCESSING-%EC%9D%B4%EB%B2%A4%ED%8A%B8%EB%8A%94-%EC%96%B4%EB%96%BB%EA%B2%8C-%EB%B3%B5%EA%B5%AC%ED%95%A0%EA%B9%8C)
<br>
[Outbox는 exactly-once를 보장할까](https://velog.io/@dochiri0916/Outbox%EB%8A%94-exactly-once%EB%A5%BC-%EB%B3%B4%EC%9E%A5%ED%95%A0%EA%B9%8C)
