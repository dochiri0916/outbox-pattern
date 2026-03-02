# Outbox Pattern 실험

파일 I/O가 포함된 쓰기 흐름에서, 트랜잭션은 짧게 유지하면서 커밋 이후 작업 유실을 방지하기 위해 Transactional Outbox를 검증한 프로젝트다.
여기에는 실행 가능한 코드와 핵심 요약만 두고, 설계 배경과 선택 이유는 블로그에 정리했다.

## 실험 범위

- `Post` 저장과 `OutboxEvent` 저장을 하나의 트랜잭션으로 처리
- 커밋 후 `OutboxWorker`가 이벤트를 비동기로 처리
- 실패 시 상태 전이(`PENDING -> PROCESSING -> FAILED/COMPLETED`)와 재시도로 복구
- 즉시 힌트 발행(`afterCommit`) + 폴링 스케줄러를 함께 사용해 유실 위험 완화

## 자세한 내용

[Outbox Pattern으로 트랜잭션 이후 작업을 보장하게 된 이유](https://velog.io/@dochiri0916/Outbox-Pattern%EC%9C%BC%EB%A1%9C-%ED%8A%B8%EB%9E%9C%EC%9E%AD%EC%85%98-%EC%9D%B4%ED%9B%84-%EC%9E%91%EC%97%85%EC%9D%84-%EB%B3%B4%EC%9E%A5%ED%95%98%EA%B2%8C-%EB%90%9C-%EC%9D%B4%EC%9C%A0)