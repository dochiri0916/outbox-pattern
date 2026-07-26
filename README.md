# Outbox Pattern

## 멀티 모듈 구성

```text
core          공통 Domain, Application UseCase, Outbound Port, Outbox 메시지 계약
polling-worker 기존 DB Polling + SKIP LOCKED 처리 방식
cdc-consumer  Debezium CDC 이벤트를 Kafka에서 소비하는 처리 진입점
```

두 실행 모듈은 `core`의 계약을 공유하고 이벤트 획득 방식만 분리합니다. 따라서 멀티 인스턴스 환경에서
Polling Worker의 DB lock 경쟁과 CDC Consumer의 Kafka consumer group 분산 처리를 같은 테스트 대상에서 비교할 수 있습니다.

```bash
./gradlew :polling-worker:check
./gradlew :cdc-consumer:check
./gradlew check
```

CDC Consumer는 Debezium Outbox SMT가 발행하는 `payload.op`/`payload.after` 형식을 입력으로 받으며,
`outbox.cdc.topic`과 `outbox.cdc.group-id`로 토픽과 consumer group을 설정합니다.

## 로컬 Debezium 실행

```bash
docker compose up -d mysql localstack kafka connect connector-init
./gradlew :cdc-consumer:bootRun
```

`connector-init`이 Kafka Connect REST API에 `outbox-mysql-connector`를 등록하고,
MySQL의 `outbox_pattern.outbox_event` 변경을 다음 토픽으로 발행합니다.

```text
outbox-pattern.outbox_pattern.outbox_event
```

Kafka는 호스트 애플리케이션에서 `localhost:29092`, Docker 내부 서비스에서 `kafka:9092`로 접근합니다.
Kafka Connect REST API는 `http://localhost:8083`에서 확인할 수 있습니다.

## 로컬 Worker 복제 실행

Polling Worker 이미지는 `docker/polling-worker.Dockerfile`로 만들며, Compose의 `polling` profile에서 실행합니다.
컨테이너 내부 포트는 모두 8080을 사용하고, 호스트 포트는 Compose가 replica별로 동적으로 할당합니다.

```bash
docker compose --profile polling up -d --build --scale polling-worker=10 polling-worker
docker compose ps polling-worker
docker compose port --index=1 polling-worker 8080
docker compose logs -f polling-worker
```

CDC Consumer 이미지도 같은 방식으로 만들 수 있습니다. Kafka consumer group 분산을 확인할 때 사용합니다.

```bash
docker compose --profile cdc up -d --build --scale cdc-consumer=3 cdc-consumer
docker compose ps cdc-consumer
docker compose logs -f cdc-consumer
```

Polling과 CDC profile을 동시에 실행하면 동일한 Outbox 이벤트를 두 처리 경로가 각각 소비할 수 있으므로,
비교 실험에서는 한 번에 하나의 profile만 실행합니다.

## k6 부하 테스트와 Grafana

Prometheus가 Polling Worker의 `/actuator/prometheus`와 MySQL exporter를 수집하고,
Grafana는 Prometheus를 기본 데이터 소스로 사용합니다. k6 결과도 Prometheus Remote Write로 전송됩니다.

```bash
docker compose --profile polling --profile observability up -d --build \
  --scale polling-worker=10 polling-worker prometheus mysql-exporter grafana

docker compose --profile load-test run --rm k6
```

기본 k6 시나리오는 초당 1,000건을 60초 동안 `POST /api/v1/posts`로 발생시킵니다.
로컬 환경에서 먼저 낮은 부하로 확인하려면 다음처럼 환경 변수를 덮어씁니다.

```bash
docker compose --profile load-test run --rm \
  -e RATE=100 \
  -e DURATION=30s \
  k6
```

- Grafana: `http://localhost:3000` (`admin` / `admin`)
- Prometheus: `http://localhost:9090`
- 기본 대시보드: `Outbox / Outbox Polling Load Test`
- k6 스크립트: `load-test/k6/outbox-insert.js`

대시보드는 HTTP/k6 p99, Outbox pending·failed queue, Worker별 HikariCP connection,
claim·processing p99, 처리량, MySQL connection·query 지표를 표시합니다.

기존 MySQL volume을 이미 생성한 상태라면 초기화 SQL이 다시 실행되지 않으므로,
처음 Debezium을 붙일 때는 기존 volume을 보존할지 확인한 뒤 재생성해야 합니다.

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
