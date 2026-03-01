# Outbox Pattern Example

트랜잭션 안에서 비즈니스 데이터(`Post`)와 이벤트(`OutboxEvent`)를 함께 저장하고, 커밋 후 워커가 파일 작업을 처리하는 예제 프로젝트다.

## 핵심 동작

1. API 요청으로 게시글 + 파일 업로드
2. `Post` 저장과 `OutboxEvent` 저장을 한 트랜잭션으로 처리
3. 커밋 후 `OutboxWorker`가 이벤트를 읽어 파일 이동/검증/정리 수행
4. 성공 시 `COMPLETED`, 실패 시 재시도 후 `FAILED` 전이

## 기술 스택

- Java 25
- Spring Boot 4.0.0
- Spring Data JPA, Spring Web MVC
- H2 (기본), MySQL 드라이버 포함
- AWS SDK v2 S3 (로컬 실행은 LocalStack 기준)

## 패키지 구조

```text
src/main/java/com/dochiri/outboxpattern
├── presentation     # REST API 컨트롤러
├── application      # 유스케이스/파사드
├── domain           # 도메인 엔티티/리포지토리
├── infrastructure   # outbox, scheduler, s3 adapter, 설정
└── common           # 공통 payload/DTO
```

## 빠른 실행

### 1) 사전 준비

- JDK 25
- Docker (LocalStack 사용 시)
- AWS CLI (버킷 생성용, 선택)

### 2) LocalStack 실행 및 버킷 생성

```bash
docker run --rm -it -p 4566:4566 localstack/localstack:latest
aws --endpoint-url=http://localhost:4566 s3 mb s3://local-test-bucket
```

기본 설정(`src/main/resources/application.yml`)은 아래 값을 사용한다.

- endpoint: `http://localhost:4566`
- bucket: `local-test-bucket`
- region/access-key/secret-key: `us-east-1` / `test` / `test`

### 3) 애플리케이션 실행

```bash
./gradlew bootRun
```

- API base URL: `http://localhost:8080`
- H2 Console: `http://localhost:8080/h2-console`

## API 예시

`POST /api/v1/posts` (`multipart/form-data`)

```bash
curl -X POST http://localhost:8080/api/v1/posts \
  -F 'input={"title":"hello","content":"outbox","temporaryFilePath":"ignore","originalFileName":"ignore.txt","fileSize":0,"contentType":"application/octet-stream"};type=application/json' \
  -F 'file=@./sample.txt'
```

예상 응답:

```json
{"postId":1}
```

## 테스트

```bash
./gradlew test
```

테스트에서는 `InMemoryFileStoragePort`를 사용하므로 LocalStack 없이 실행할 수 있다.

## 도입 배경과 설계 기록

- https://velog.io/@dochiri0916/Outbox-Pattern%EC%9C%BC%EB%A1%9C-%ED%8A%B8%EB%9E%9C%EC%9E%AD%EC%85%98-%EC%9D%B4%ED%9B%84-%EC%9E%91%EC%97%85%EC%9D%84-%EB%B3%B4%EC%9E%A5%ED%95%98%EA%B2%8C-%EB%90%9C-%EC%9D%B4%EC%9C%A0
