# Settlement System (Java 21, Spring Boot 3)

정산 개발 과제 구현입니다. PRD 요구사항 기준으로 다음을 제공합니다.

- 수수료 정책 관리 API (`POST/GET/PUT /api/v1/policies`)
- 정산 예정금액 계산 Batch (`settlementExpectedJob`)
- 정산 내역 조회 API (`GET /api/v1/settlements`)
- Swagger (`/swagger-ui.html`)

## 모듈 구성

- `core`: 도메인 모델, JPA Entity/Repository, 수수료 계산/정산 계산 로직
- `api`: 정책 API, 정산 조회 API, 예외 응답(Problem Details), Swagger
- `batch`: Spring Batch Job/Step, 멱등성 cleanup, Job 실행기

### 모듈 상세 구조

#### `core` (비즈니스 규칙/영속성 공통 모듈)

- 역할: API와 Batch가 공통으로 사용하는 정산 도메인 규칙과 영속성 로직 제공
- 패키지 구조
  - `core.domain`
    - Enum: `PaymentMethod`, `FeeType`, `TransactionType`
    - 예외: `PolicyConflictException`, `PolicyNotFoundException`, `InvalidTransactionException`
    - 계산 로직
      - `SettlementCalculator`: 수수료/부가세/정산예정금액 계산(정률/정액, 취소 환급 규칙)
      - `SettlementCalculationService`: 정책 조회/원거래 조회/누적취소액 조회를 조합해 `SettlementExpected` 생성
  - `core.persistence.entity`
    - `FeePolicy`, `PaymentTransaction`, `SettlementExpected`
  - `core.persistence.repository`
    - `FeePolicyRepository`: 유효 정책 조회, 중복 기간 검증, 정책 검색
    - `PaymentTransactionRepository`: 원거래 조회, 이전 취소 누적액 조회
    - `SettlementExpectedRepository`: 배치 결과 저장/삭제/조회 검색

#### `api` (HTTP 진입점 + 응답 포맷)

- 역할: 외부 요청을 받아 검증/변환 후 `core`를 호출하고 표준 응답으로 반환
- 패키지 구조
  - `api.policy`
    - Controller: `FeePolicyController` (`POST/GET/PUT /api/v1/policies`)
    - Service: `FeePolicyService` (정책 등록/변경/조회)
    - DTO: `CreateFeePolicyRequest`, `UpdateFeePolicyRequest`, `FeePolicyResponse`
  - `api.settlement`
    - Controller: `SettlementController` (`GET /api/v1/settlements`)
    - Service: `SettlementQueryService` (조회 기간 검증 + 검색)
    - DTO: `SettlementSearchItem`
  - `api.common`
    - `GlobalExceptionHandler`: Problem Details 기반 예외 응답
    - `PageResponse`: 공통 페이징 응답 래퍼
  - `api.perf`
    - `SettlementPerfSeeder`: `perf` 프로파일 대량 데이터 생성 도우미

#### `batch` (정산 예정금액 산출 배치)

- 역할: 기준일/PG사 단위로 거래를 읽어 정산 예정 데이터를 생성
- 패키지 구조
  - `batch.job.SettlementExpectedJobConfig`
    - Job: `settlementExpectedJob`
    - Step 1 `settlementCleanupStep`: `(baseDate, pgCompany)` 대상 기존 결과 삭제(멱등성)
    - Step 2 `settlementExpectedStep`: 거래 읽기 -> 계산 -> 저장(Chunk 기반)
    - Reader: `JpaCursorItemReader` (기준일/PG사 거래를 `id asc`로 조회)
    - Processor: `SettlementCalculationService` 호출
      - `PolicyNotFoundException`, `InvalidTransactionException`은 skip + 경고 로그
      - 그 외 예외는 전파하여 Job 실패(데이터 누락 방지)
    - Writer: `JpaItemWriter`로 `SettlementExpected` 저장
  - `batch.job.BatchJobRunner`
    - 애플리케이션 실행 시 Job 파라미터(`baseDate`, `pgCompany`)를 전달해 배치 실행

### 모듈 의존 관계 / 호출 흐름

- 의존 방향: `api -> core`, `batch -> core` (`core`는 단독 재사용 가능)
- 정책 관리 API 흐름
  - `FeePolicyController` -> `FeePolicyService` -> `FeePolicyRepository`
- 정산 조회 API 흐름
  - `SettlementController` -> `SettlementQueryService` -> `SettlementExpectedRepository`
- 배치 계산 흐름
  - `BatchJobRunner` -> `settlementExpectedJob`
  - cleanup step: `SettlementExpectedRepository.deleteByBaseDateAndPgCompany`
  - processing step: Reader(`PaymentTransaction`) -> `SettlementCalculationService` -> Writer(`SettlementExpected`)

### 아키텍처 다이어그램 (간단)

```text
+--------+      +-----+      +------+      +----------------------+
| Client | ---> | api | ---> | core | ---> | H2 (policy/tx/result) |
+--------+      +-----+      +------+      +----------------------+
                     ^            ^
                     |            |
                     |     +------+------+
                     +-----| batch (Job) |
                           +-------------+
```

## 요구사항 대응 요약

### 1) 정책 데이터 기반 관리

- `fee_policy` 테이블 기반 관리
- 기간 중복 정책 검증 (`NOT (end < newStart OR start > newEnd)`)
- `PaymentMethod`를 간편결제 세부수단까지 분리 (`EASY_PAY_CARD`, `EASY_PAY_ACCOUNT`)
- 정책 변경 시 대상 정책이 없으면 404 반환 (`PolicyNotFoundException`)
- 정책 변경 API(`PUT`)는 `endDate` 조정 용도이며, 수수료 값/타입 변경은 기존 정책 종료 + 신규 정책 등록으로 처리

### 2) 정산 예정금액 배치

- Job 파라미터: `baseDate`, `pgCompany`
- 멱등성: 실행 시작 시 `settlement_expected` 대상(baseDate, pgCompany) 삭제 후 재산출
- 취소 거래는 원거래 승인 시점 정책 사용
- 정액 부분취소 환급 규칙 반영
- 예외 처리 정책: 정책 미존재/잘못된 취소 참조만 skip, 그 외 런타임 예외는 Job 실패

### 3) 정산 조회 성능

- 필수 조건: `merchantId`, `fromDate`, `toDate`
- 선택 조건: `transactionType`, `amount`
- 인덱스:
  - `settlement_expected(merchant_id, transaction_date)`
  - `settlement_expected(transaction_date)`
  - `settlement_expected(base_date, pg_company)`

## 예외/오류 응답 정책 (Problem Details)

- `409 Conflict`: 수수료 정책 기간 중복
- `404 Not Found`: 정책 변경 대상이 존재하지 않음
- `400 Bad Request`: 요청 검증 실패, 필수 파라미터 누락, enum/날짜 타입 불일치
- 위 케이스는 모두 RFC 7807 Problem Details 포맷으로 통일 응답

## DB 초기화 전략

- `schema.sql`: 테이블/인덱스 생성
- `data.sql`: 정책/거래 seed 데이터 삽입
- 설정: `ddl-auto=validate`, `spring.sql.init.mode=always`

### 테이블 설계 의도

- `fee_policy`: `pg_company + merchant_id + payment_method` 조합의 정책 이력을 유효기간(`start_date`, `end_date`)으로 관리
- `payment_transaction`: 승인/취소 원천 거래를 저장하고 `original_transaction_id`로 취소-원거래 관계를 추적
- `settlement_expected`: 배치 산출 결과를 조회용으로 저장하며, `(base_date, pg_company, transaction_id)` 고유키로 멱등성 보장

## 실행 방법

```bash
./gradlew build
./gradlew :api:bootRun
./gradlew :batch:bootRun --args='--baseDate=2026-03-10 --pgCompany=PG1'
```

Swagger:

- `http://localhost:8080/swagger-ui.html`
- `http://localhost:8080/v3/api-docs`

## 테스트

- `core`: 수수료 계산 도메인 테스트
- `api`: 정책 API/정산 조회 API 통합 테스트(MockMvc)
- `batch`: 멱등성 배치 테스트(Spring Batch Test)

실행:

```bash
./gradlew test
```

## 가정사항

- 정률 부분취소: 취소 금액 비례 환급
- 정액 부분취소: 전액취소 시점에만 환급
- 환급 수치(`fee_amount`, `vat_amount`, `expected_settlement_amount`)는 음수 저장
- `baseDate`는 거래일(`transaction_date`) 기준 배치 처리일
- 조회 기간은 최대 31일 제한
- 배치에서 예상치 못한 런타임 예외는 skip하지 않고 Job 실패 처리

## 성능 측정 결과 (50만 건)

측정 방법:

1. `perf` 프로파일로 API 실행 (`settlement_expected` 500,000건 seed 자동 생성)
2. S5(cold cache) 1회 측정
3. S1 warm-up 10회
4. S1~S4 각 50회 요청 후 avg/p95 계산

실행 명령:

```bash
./gradlew :api:bootRun --args='--spring.profiles.active=perf' -Dspring-boot.run.jvmArguments='-Xms512m -Xmx1g -XX:+UseG1GC'
python3 scripts/perf_measure.py
```

측정 시나리오:

- S1: 필수조건 조회 (`merchantId + fromDate + toDate`)
- S2: S1 + `transactionType=APPROVAL`
- S3: S1 + `amount=100000`
- S4: S1 + deep page (`page=50,size=20`)
- S5: cold cache first hit

측정 결과(ms):

| 시나리오 | avg | p95 | min | max |
|---|---:|---:|---:|---:|
| S1 | 2.30 | 4.22 | 1.58 | 6.77 |
| S2 | 2.71 | 2.72 | 1.32 | 49.90 |
| S3 | 1.32 | 1.71 | 0.90 | 5.87 |
| S4 | 1.45 | 1.91 | 1.01 | 11.39 |
| S5 (cold) | 329.11 | 329.11 | 329.11 | 329.11 |

해석:

- 반복 조회(S1~S4) p95는 모두 5ms 내외로 목표(300ms) 충족
- S5(cold)만 초기 기동/캐시 영향으로 329ms 발생

측정 환경:

- CPU: Apple M2 Pro
- 메모리: 32GB 
- OS: macOS 26.0.1 
- JVM 옵션: `-Xms512m -Xmx1g -XX:+UseG1GC`
