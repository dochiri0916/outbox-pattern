# Outbox Pattern 부하·장애 조치 테스트 보고서

## 1. 보고서 요약

이 보고서는 단일 애플리케이션을 여러 컨테이너로 확장했을 때의 기존 DB Polling 방식과,
Debezium + Kafka CDC 방식의 처리량·지연 시간·장애 조치 결과를 정리한다.

결론은 다음과 같다.

- 기존 DB Polling 방식은 1,000 req/s를 처리하지 못했다.
- Worker 간 staging 파일을 공유하도록 수정한 뒤 `NoSuchFileException`은 제거됐지만, DB connection pool과 외부 I/O timeout이 병목으로 남았다.
- CDC 방식은 1,000 req/s 부하에서 HTTP 실패 없이 56,837건을 수락하고, Kafka lag 0과 effect 처리 완료를 확인했다.
- Consumer 강제 종료 후 Kafka rebalance가 정상 동작했고, 처리 완료 데이터의 유실은 없었다.
- 로컬 10 Consumer 테스트에서는 Kafka·Connect가 Docker 메모리 부족으로 OOM 종료됐다. 이후 Compose에 JVM heap 제한을 추가하고 Connector를 복구했다.

따라서 이번 실험은 절대적인 운영 용량을 증명하기보다, Polling에서 발생하는 병목과 멀티 인스턴스 장애 지점을 확인하고 CDC 전환의 효과를 비교한 실험으로 해석해야 한다.

## 2. 테스트 범위와 구성

### 2.1 비교 대상

```text
HTTP API
  └─ polling-worker × N
       └─ MySQL Outbox table polling + SKIP LOCKED

HTTP API
  └─ Outbox INSERT
       └─ MySQL binlog
            └─ Debezium Connector
                 └─ Kafka topic
                      └─ cdc-consumer × N
                           └─ effect DB update + file storage processing
```

### 2.2 공통 구성

- MySQL 8.4
- Debezium Kafka Connect 3.6
- Kafka 단일 브로커
- LocalStack S3
- k6 부하 생성기
- Prometheus + Grafana
- Docker Compose 기반 로컬 실행
- Outbox 이벤트와 처리 효과를 DB에서 검증

Polling 비교에서는 `polling-worker`를 10개 실행했고, CDC 비교에서는 `cdc-consumer`의 Consumer Group 분산과 Kafka partition rebalance를 검증했다.

단일 Docker 호스트에서 모든 컨테이너를 실행했으므로, 아래 수치는 운영 환경의 절대 처리량이 아니라 동일한 로컬 환경에서의 상대 비교 결과다.

## 3. 테스트 결과 요약

| 구분 | 부하 | 처리 결과 | 지연 시간 | 장애·관찰 결과 |
| --- | --- | --- | --- | --- |
| 1차 Polling | 1,000 req/s × 60초 | 실제 1,579건, 약 17.9 req/s | P95 약 44초, P99 약 50.5초 | staging 파일 공유 실패, DB connection pool 고갈 |
| Fixed Polling | 1,000 req/s × 60초 | 약 47.6 req/s, HTTP 성공률 26.13% | P95 약 31.5초, P99 약 70초 | `NoSuchFileException` 0건, timeout·connection 병목 지속 |
| CDC 기본 | 1,000 req/s × 60초 | 성공 56,837건, 실패 0건 | P95 1.365초, P99 1.834초 | Kafka lag 0, effect pending 0 |
| CDC 2 Consumer fail-over | 100 req/s × 30초 | 성공 3,001건, API 실패 0건 | P95 5.98ms, P99 11.66ms | Consumer 강제 종료 후 partition rebalance 성공 |
| CDC 10 partition·10 Consumer | 1,000 req/s 부하 | 성공 6,128건, 실제 약 127 req/s | P95 24.63초, P99 25.22초 | fail-over 성공, 로컬 메모리 부족으로 Kafka·Connect OOM |

`dropped iterations`는 k6의 constant-arrival-rate 시나리오가 목표 도착률을 유지하지 못해 실행하지 못한 iteration이다. HTTP 요청이 서버에서 명시적으로 실패한 건수와는 구분해야 한다.

## 4. 1차 테스트: 기존 DB Polling 한계 확인

### 4.1 목적

Worker 10개가 Outbox 테이블을 `SKIP LOCKED`로 polling하는 기존 구조에서,
1,000 req/s 입력이 발생할 때 DB connection, queue, latency가 어떻게 변하는지 확인했다.

### 4.2 결과

- 목표: 1,000 req/s × 60초
- 실제 처리: 1,579건
- 실제 처리량: 약 17.9 req/s
- dropped iterations: 58,463건
- 성공률: 68.77%
- 실패율: 31.22%
- P95: 약 44초
- P99: 약 50.5초
- Hikari active: 최대 100/100
- Hikari pending: 최대 324
- MySQL connections: 최대 102

테스트 종료 시점의 Outbox 상태는 다음과 같았다.

- `COMPLETED`: 약 90건
- `PENDING`: 약 673건
- `FAILED - staging 파일 없음`: 약 789건
- `FAILED - DB 연결 timeout`: 약 5건

### 4.3 핵심 원인: Worker 간 staging 파일 불일치

요청을 수신한 Worker A가 `/tmp/outbox-staging`에 파일을 저장하고,
Outbox 이벤트를 처리하도록 claim한 Worker B가 해당 파일을 읽으려 했다.
각 컨테이너의 파일 시스템이 분리되어 있었기 때문에 Worker B에는 파일이 존재하지 않았고,
`NoSuchFileException`이 발생했다.

이 문제는 `SKIP LOCKED`의 동시성 제어 문제가 아니라, 이벤트가 참조하는 파일 데이터와
이벤트 처리 Worker의 저장 공간이 공유되지 않은 배포 구성 문제다.

### 4.4 DB 병목

Hikari active가 100/100까지 차고 pending이 최대 324까지 증가했다.
이는 요청 처리와 Outbox polling, 파일 처리 작업이 같은 로컬 MySQL connection 자원을 경쟁했다는 의미다.
Queue가 증가하면서 polling Worker를 늘리는 것만으로는 처리량이 선형적으로 증가하지 않고,
오히려 DB lock·connection 경쟁과 timeout을 키울 수 있다.

## 5. Fixed Polling 테스트: staging 공유 후 재검증

### 5.1 변경 사항

- 모든 Worker가 `outbox-staging-data` Docker volume을 공유
- Grafana queue 지표의 중복 집계를 `sum`에서 `max`로 변경
- 기존 DB volume은 보존
- `outbox-pattern-fixed`라는 별도 Compose project로 실행
- Worker 10개 구성 유지

### 5.2 결과

- 목표: 1,000 req/s × 60초
- 실제 처리량: 약 47.6 req/s
- HTTP 성공률: 26.13%
- dropped iterations: 17,916건
- P95: 약 31.5초
- P99: 약 70초
- Hikari pending: 최대 9
- MySQL connections: 최대 100
- `NoSuchFileException`: 0건

### 5.3 해석

staging volume 공유로 멀티 인스턴스 간 파일 불일치 문제는 해결됐다.
하지만 HTTP 성공률과 지연 시간은 여전히 목표 수준에 도달하지 못했다.
남은 병목은 다음과 같다.

- MySQL connection 상한 도달
- Hikari connection 대기
- 애플리케이션 처리 timeout
- LocalStack/S3 파일 처리 timeout
- Outbox polling과 API 쓰기 작업 간 DB 자원 경쟁

따라서 Fixed Polling 결과는 “파일 공유 문제를 해결한 뒤에도 Polling 구조가 1,000 req/s를 감당하지 못한다”는 것을 보여준다.

또한 Docker volume 공유는 동일한 Docker 호스트에서만 유효한 실습용 해결책이다.
실제 여러 서버로 배포하면 로컬 volume이 공유되지 않으므로, 파일 자체가 아니라 S3 object key처럼
공유 가능한 durable storage reference를 Outbox 이벤트에 기록하는 방향이 적절하다.

## 6. CDC 기본 테스트

### 6.1 구성

Polling scheduler를 비활성화하고 다음 경로만 사용했다.

```text
MySQL INSERT
  → binlog
  → Debezium MySQL Connector
  → Kafka topic
  → cdc-consumer
```

### 6.2 결과

- 부하: 1,000 req/s × 60초
- 성공: 56,837건
- 실패: 0건
- dropped iterations: 3,164건
- P95: 1.365초
- P99: 1.834초
- Debezium connector/task: `RUNNING`
- Kafka lag: 0
- `outbox_event`: 56,837건
- `outbox_event_effects`: 56,837건
- 완료 effect: 56,837건
- `post_files`: 56,837건
- effect pending: 0건
- `NoSuchFileException`: 0건

성공한 요청 기준으로는 HTTP 오류 없이 처리됐고, CDC downstream도 Kafka lag 0까지 따라잡았다.
다만 3,164건의 dropped iteration이 있었으므로 “로컬 환경에서 60,000건을 모두 수락했다”고 해석해서는 안 된다.
실제 성공 수 기준 처리량은 약 947 req/s다.

CDC는 DB polling query와 `SKIP LOCKED` lock 경쟁을 제거하지만, DB INSERT/binlog 기록,
effect update, S3 처리까지 모든 DB·외부 저장소 부하가 사라지는 것은 아니다.
이번 결과는 “Outbox를 읽기 위해 DB를 반복 조회하는 부하를 제거했을 때 입력 경로와 처리 지연이 크게 개선됐다”고 해석하는 것이 정확하다.

## 7. Kafka Consumer fail-over 테스트

### 7.1 2 Consumer 구성

- Consumer: 2개
- Kafka topic partition: 1개
- 부하: 100 req/s × 30초
- 약 12초 시점에 `cdc-consumer-1` 강제 종료

### 7.2 결과

- 성공: 3,001건
- API 실패: 0건
- P95: 5.98ms
- P99: 11.66ms
- Kafka lag: 0
- `outbox_event`: 59,838건
- `outbox_event_effects`: 59,838건
- `post_files`: 59,838건
- effect pending: 0건
- `NoSuchFileException`: 0건

기존 56,837건에 신규 이벤트 3,001건이 정확히 추가됐다.
Consumer 하나가 종료된 뒤 다른 Consumer가 partition을 재할당받았고,
처리 완료 데이터 기준 유실은 확인되지 않았다.

단, partition이 1개였기 때문에 두 Consumer가 동시에 이벤트를 처리한 것은 아니다.
이 테스트는 Consumer Group fail-over와 재할당 동작만 검증했다.

### 7.3 10 partition·10 Consumer 구성

실제 병렬 처리와 fail-over를 확인하기 위해 topic partition을 10개로 늘리고,
`cdc-consumer`를 10개로 확장했다. 부하 중 Consumer 하나를 강제 종료해 partition 재할당을 확인했다.

결과는 다음과 같다.

- 성공: 6,128건
- API 실패: 0건
- 실제 처리량: 약 127 req/s
- dropped iterations: 23,884건
- P95: 24.63초
- P99: 25.22초
- 최종 Kafka lag: 0
- `outbox_event`, effect, completed effect, `post_files`: 각각 65,966건
- effect pending: 0건
- 복구 후 신규 이벤트 1건도 정상 처리

Fail-over와 데이터 정합성은 통과했지만, 10개 Consumer를 띄운 상태에서 로컬 API/MySQL이 먼저 병목이 됐다.
따라서 이 결과를 Kafka가 1,000 req/s를 처리하지 못한 결과로 단정하면 안 된다.
입력 API와 로컬 DB가 목표 부하를 수용하지 못한 결과가 함께 포함돼 있다.

## 8. CDC 처리량 매트릭스

API와 k6 도착률의 영향을 분리하기 위해, 유효한 `posts`와 `outbox_event`를 DB에 직접 생성하고
각 이벤트에 대응하는 zero-byte staging 파일을 공유 volume에 준비했다.
Debezium이 해당 이벤트를 Kafka에 발행한 뒤 Connect를 중지하고,
Consumer를 시작해 Kafka backlog를 모두 처리하는 catch-up 시간을 측정했다.

각 시나리오는 고유 이벤트 2,000건을 사용했다. 측정 시간에는 Consumer 컨테이너 기동과
Kafka partition assignment 시간이 포함되어 있으므로 steady-state 처리량이 아니라 로컬 catch-up 지표로 해석해야 한다.

| Consumer 수 | Kafka 처리 레코드 | catch-up 시간 | 최종 lag | effect 결과 |
| ---: | ---: | ---: | ---: | --- |
| 1 | 2,000 | 약 22초 | 0 | 2,000 완료, pending 0 |
| 2 | 2,000 | 약 9초 | 0 | 2,000 완료, pending 0 |
| 5 | 2,000 | 약 18초 | 0 | 2,000 완료, pending 0 |
| 10 | 4,000 | 약 21초 | 0 | 신규 2,000 완료, 직전 2,000은 중복 replay |

Consumer 수를 2개에서 5개, 10개로 늘려도 처리 시간이 선형으로 줄지 않았다.
현재 처리 경로에는 effect DB update와 S3(LocalStack) 호출이 포함되어 있으므로,
Kafka partition 병렬성만으로 전체 처리량이 결정되지 않는다.
DB connection, S3 latency, Consumer startup/rebalance 비용을 분리한 추가 측정이 필요하다.

## 9. Connector 재시작 중복 replay와 멱등성

10 Consumer 시나리오를 준비하는 동안 Debezium Connect를 재기동했고,
offset flush 전에 처리된 binlog 구간이 Kafka에 다시 발행됐다.
새로운 DB Outbox 이벤트는 2,000건이었지만 Kafka log end는 직전 2,000건의 replay까지 포함해 4,000건 증가했다.

중복 replay 처리 후 결과는 다음과 같다.

- Kafka consumer lag: 0
- DB의 고유 `outbox_event`: 74,967건
- `outbox_event_effects`: 74,967건
- 완료 effect: 74,967건
- effect pending: 0건
- `post_files`: 74,967건
- CDC matrix storage key: 9,000개
- S3 object: 9,000개

Kafka 레코드는 중복으로 처리됐지만 DB effect와 `post_files`, S3 object는 중복 생성되지 않았다.
따라서 현재 effect 상태와 storage key를 이용한 멱등성 처리가 실제 Connector 재시작에 따른 at-least-once replay에서도 동작함을 확인했다.

이 결과는 의도적으로 Consumer offset을 rewind한 테스트가 아니라 Connect 재시작 중 자연스럽게 발생한 replay 결과다.
별도의 offset rewind 테스트는 이후 재현성을 높이기 위해 추가할 수 있다.

## 10. 로컬 리소스 한계와 보완

10 Consumer 부하 중 Kafka와 Debezium Connect가 Docker 메모리 부족으로 `exit 137 (OOMKilled)` 됐다.
확인된 주요 원인은 Connect 기본 JVM heap이 `-Xmx2G`였고,
Consumer 10개가 각각 수백 MiB의 메모리를 사용한 상태에서 모든 서비스를 단일 Docker 호스트에 올렸기 때문이다.

복구 과정에서 Kafka/Connect/Consumer의 데이터 volume은 삭제하지 않았다.
Compose에 다음 JVM heap 제한을 추가하고 컨테이너를 재생성했다.

| 서비스 | JVM 설정 |
| --- | --- |
| Kafka | `-Xms256M -Xmx512M` |
| Debezium Connect | `-Xms256M -Xmx768M` |
| Polling Worker | `-Xms64m -Xmx384m` |
| CDC Consumer | `-Xms64m -Xmx384m` |

설정은 [`compose.yml`](compose.yml)에 반영되어 있다.
복구 후 Debezium connector/task는 `RUNNING`이었고, 신규 이벤트 1건이 effect와 `post_files`까지 정상 반영됐다.

이 설정은 로컬 실습의 재현성을 높이기 위한 상한이다.
운영 배포에서는 JVM heap, container memory limit, Kafka broker/Connect worker 수,
MySQL 자원을 별도로 산정해야 한다.

## 11. 멱등성과 데이터 정합성

현재 CDC Consumer의 중복 처리 방지는 Redis가 아니라 DB의 Outbox effect 상태와 이벤트 식별자를 기준으로 구현되어 있다.
따라서 Kafka가 at-least-once로 동일 이벤트를 재전달하더라도 이미 완료된 effect를 다시 수행하지 않도록 검증한다.

이번 테스트에서 확인한 정합성 기준은 다음과 같다.

- Kafka consumer lag이 0으로 수렴
- `outbox_event_effects`와 `post_files`의 처리 건수 일치
- effect pending 0
- Consumer 강제 종료 후 최종 처리 건수 증가분이 입력 성공 건수와 일치
- `NoSuchFileException` 0건

다만 외부 파일 저장소 작업과 DB 상태 업데이트 사이의 장애 조합까지 완전히 exactly-once로 보장하는 것은 아니다.
현재 구조의 보장은 “중복 실행을 감지·스킵할 수 있는 멱등성”과 “Kafka 재처리를 통한 eventual completion”에 가깝다.

## 12. Grafana 관찰 결과

Grafana 대시보드에서는 다음 지표를 비교했다.

- Create Post HTTP P95/P99
- Outbox pending/failed queue
- Worker별 HikariCP active/pending
- Outbox claim/processing P95/P99
- Outbox claimed/completed throughput
- MySQL connections, running queries, queries/sec

초기 Polling 테스트에서는 HTTP latency와 Outbox queue가 함께 증가했고,
Hikari active/pending 및 MySQL connection 지표가 상한에 도달했다.
Fixed Polling에서는 파일 오류가 사라졌지만 connection 및 외부 I/O 병목이 남았다.
CDC와 Consumer fail-over 화면에서는 queue와 Kafka lag이 처리 종료 후 0으로 수렴하는 것을 확인했다.

원본 스크린샷은 다음 경로에 보관되어 있다.

```text
/Users/seongbin/스크린샷/
```

주요 캡처 파일:

- `스크린샷 2026-07-25 오전 7.08.12.png`
- `스크린샷 2026-07-25 오전 7.23.31.png`
- `스크린샷 2026-07-25 오전 7.35.48.png`
- `스크린샷 2026-07-25 오후 6.01.52.png`
- `스크린샷 2026-07-25 오후 6.24.25.png`

## 13. 최종 결론

### Polling 방식

Polling + `SKIP LOCKED`는 단일 인스턴스 또는 낮은 처리량에서는 단순하고 이해하기 쉽다.
하지만 Worker가 여러 개가 되면 다음 문제가 동시에 나타난다.

- DB polling query와 lock 경쟁
- HikariCP connection 고갈
- Worker 간 local file storage 불일치
- S3/외부 I/O timeout이 DB 처리 지연으로 전파
- Worker 수를 늘려도 처리량이 선형 증가하지 않음

공유 Docker volume으로 staging 오류는 해결할 수 있었지만, 멀티 서버 환경의 근본적인 파일 공유 전략은 아니다.

### CDC 방식

Debezium + Kafka는 Outbox INSERT를 binlog에서 읽어 DB polling을 제거하고,
Kafka Consumer Group을 통해 처리 인스턴스를 확장한다.

이번 테스트에서는 다음을 확인했다.

- 1,000 req/s 입력에서 HTTP 실패 0건인 CDC 기본 경로
- Kafka lag 0 수렴
- Consumer 강제 종료 후 partition rebalance
- 2 Consumer와 10 Consumer 구성에서 처리 완료 데이터 유실 없음
- effect 기반 멱등성 처리

다만 CDC는 Kafka와 Connect 운영 복잡도, partition 설계, Consumer 메모리, 외부 저장소 처리,
DB effect update라는 새로운 운영 요소를 추가한다. 따라서 “복잡도가 사라진다”기보다
DB polling의 병목을 Kafka 기반의 명시적인 처리 파이프라인으로 이동시키는 선택이다.

## 14. 후속 작업

1. Consumer startup/rebalance 시간을 제외한 steady-state CDC 처리량을 별도로 측정한다.
2. k6의 `dropped iterations`, HTTP 실패, Kafka lag, effect 완료량을 별도 지표로 기록한다.
3. 영구 실패 메시지를 DLT로 격리하고 운영자가 재처리하는 흐름을 구현한다.
4. MySQL·Kafka·Connect·S3 장애를 주입해 복구 시간과 lag 회복을 측정한다.
5. Kafka broker와 Connect를 별도 리소스로 분리하고 replication factor를 운영 환경 기준으로 변경한다.
6. LocalStack 대신 실제와 유사한 object storage latency/error를 주입해 외부 I/O 병목을 재검증한다.
7. Polling을 유지해야 하는 경우 DB pool, claim batch, backoff, 외부 I/O worker를 분리해 별도 실험한다.

## 15. 검증 상태

- `./gradlew check`: 통과
- Debezium connector/task: `RUNNING`
- Kafka consumer lag: `0`
- 10개 CDC Consumer 처리량·fail-over 테스트: 완료
- CDC effect 기반 유효 상태 조회: 원본 `PENDING` → 관리자 조회 `COMPLETED` 회귀 테스트 통과
- 최종 로컬 실행 상태: Connect + CDC Consumer 2개
- 복구 후 신규 CDC 이벤트: 정상 처리
- 기존 DB/Kafka volume: 보존

## 16. 정합성·안정성 보장 범위

현재 구현을 운영 보장 수준과 혼동하지 않도록 항목별 상태를 구분한다.

| 항목 | 상태 | 현재 구현·검증 범위 |
| --- | --- | --- |
| Kafka 중복 메시지 멱등성 | 구현·검증 완료 | `outbox_event_effects`의 이벤트 ID unique 제약과 완료 상태로 중복 effect를 건너뛴다. |
| `post_files` 중복 생성 방지 | 구현·검증 완료 | `storage_key` unique 제약과 기존 metadata 확인을 사용한다. |
| S3 object 중복 업로드 방지 | 구현·검증 완료 | storage key와 conditional `uploadIfAbsent`를 사용한다. |
| Consumer 강제 종료 fail-over | 구현·검증 완료 | Kafka Consumer Group rebalance 후 lag 0과 처리 완료 건수를 확인했다. |
| Debezium Connect 재시작 replay | 구현·검증 완료 | 중복 Kafka 레코드가 재처리돼도 effect·DB row·S3 object가 중복 생성되지 않았다. |
| Kafka offset 처리 | 구현 | auto commit을 끄고 Listener 처리 이후 offset이 커밋되도록 구성했다. |
| 외부 저장소 오류 재처리 | 부분 구현 | storage 오류를 retryable failure로 분류하고 offset 미커밋 재처리가 가능하지만, CDC 전용 retry count/backoff/DLQ는 별도 구현이 필요하다. |
| Poison Message 격리 | 미구현 | 영구 실패 메시지를 별도 DLT로 격리하고 운영자가 재처리하는 흐름은 아직 없다. |
| MySQL·Kafka·S3 장애 자동 복구 | 미검증 | Connect/Consumer 재시작은 확인했지만 각 인프라 장애 주입 테스트는 별도 수행해야 한다. |
| DB와 S3의 원자적 exactly-once | 구조적으로 미보장 | DB transaction과 외부 S3 작업을 하나의 원자적 transaction으로 묶을 수 없다. 현재 보장은 at-least-once + 멱등성이다. |
| Redis 기반 중복 기록 | 적용하지 않음 | DB effect table이 최종 정합성 저장소이므로 Redis를 추가하지 않았다. |
| CDC 유효 상태 조회 | 구현·검증 완료 | `outbox_event` 원본 상태를 변경하지 않고 effect를 조합해 관리자 API의 `status`를 계산한다. 원본 값은 `sourceStatus`로 함께 노출한다. |

CDC에서는 `OutboxProgressPort`를 no-op으로 두고 Kafka consumer group과 offset이 처리 소유권을 담당한다.
따라서 CDC 경로의 원본 `outbox_event.status`는 의도적으로 `PENDING`으로 남을 수 있지만,
처리 완료의 기준은 `outbox_event_effects.completed_at`, `post_files`, storage object다.
이 값을 원본 Outbox 행에 다시 기록하면 Debezium이 `UPDATE` 이벤트를 발행하고,
CDC Consumer가 다시 읽는 피드백 경로와 Polling/CDC 간 상태 소유권 충돌이 생길 수 있다.

이를 해결하기 위해 `polling-worker`의 `GET /admin/outbox-events` 조회를 effect 기반 유효 상태 조회로 변경했다.
조회 시 다음 우선순위를 적용한다.

1. 원본 상태가 `FAILED`면 `FAILED`
2. effect의 `completed_at`이 있으면 `COMPLETED`
3. effect 행이 있으면 `PROCESSING`
4. 그 외에는 원본 `outbox_event.status`

예를 들어 CDC 처리 전후의 원본 행이 다음처럼 남아 있어도,

```text
outbox_event.status = PENDING
outbox_event_effects.completed_at = 2026-07-25T...
```

관리자 API 응답은 `status=COMPLETED`, `sourceStatus=PENDING`으로 반환한다.
`status=COMPLETED` 같은 필터도 이 유효 상태를 기준으로 동작하며,
완료 시각 역시 effect의 `completed_at`을 사용한다. 원본 `outbox_event`를 수정하지 않으므로
Debezium `op=u` 재발행은 발생하지 않는다.

이 방식은 “DB의 원본 status 값을 COMPLETED로 바꾼다”가 아니라
“CDC 처리 상태의 단일 조회 모델을 effect 테이블로 만든다”는 해결책이다.
CDC 전용 DLT, retry count/backoff, 영구 실패 상태까지 관리자 화면에서 구분하려면
후속으로 별도 CDC lifecycle/DLT 저장 모델을 추가해야 한다.

따라서 현재 상태를 한 문장으로 요약하면 다음과 같다.

> 핵심 데이터 정합성과 중복 처리 안정성은 구현·검증됐지만, DLT, 인프라 장애 자동 복구, DB-S3 원자성까지 완료된 운영형 exactly-once 시스템은 아니다.
