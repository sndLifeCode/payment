# 정산 시스템 구현 계획 (TDD + Swagger)

> 이 문서는 PRD 기반 구현 계획서입니다. 구현은 **TDD(테스트 우선)** 로 진행하며, API 확인 편의를 위해 **Swagger(OpenAPI)** 를 포함합니다.

## 1) 목표

- 수수료 정책을 하드코딩이 아닌 DB 기반으로 관리
- Spring Batch로 정산 예정 금액 계산(멱등성 포함)
- 대량 정산 조회 API 성능 확보(인덱스 + 페이징)

## 2) 아키텍처/모듈

- 언어/프레임워크: Java 21 (records, sealed interface, pattern matching 적극 활용)
- 빌드: Gradle 8.x + Kotlin DSL (멀티모듈)
- DB: H2 file mode

모듈 구성:

- `core`: 도메인 모델/계산 로직/공용 enum/JPA Entity + Repository
- `api`: 정책 관리 API + 정산 조회 API + Swagger
- `batch`: 정산 예정 금액 계산 Job

공통 원칙:

- 비즈니스 로직은 `core`에 집중 (API/Batch에서 재사용)
- 정책 변경 이력 보존(유효기간 기반)
- 금액 계산은 `BigDecimal` + `RoundingMode.DOWN` + 원단위 절사

빌드/실행:

```bash
./gradlew build                        # 전체 빌드 + 테스트
./gradlew :api:bootRun                 # API 서버 기동 (http://localhost:8080/swagger-ui.html)
./gradlew :batch:bootRun --args='--baseDate=2026-03-10 --pgCompany=PG1'  # 배치 실행
./gradlew test                         # 전체 테스트
```

## 3) 핵심 설계 결정

### 3.1 결제수단 모델

PRD 요구를 반영해 단일 enum으로 평탄화:

- `CARD`
- `BANK_TRANSFER`
- `VIRTUAL_ACCOUNT`
- `MOBILE`
- `EASY_PAY_CARD`
- `EASY_PAY_ACCOUNT`

### 3.2 정책 변경 방식

- 정책은 기간 이력형 (`start_date`, `end_date`)
- 동일 `pg_company + merchant_id + payment_method`에서 기간 중복 금지
- 수수료율/수수료타입 실질 변경은 신규 정책 등록 + 기존 정책 종료일 조정

### 3.3 취소/부분취소 규칙

- 취소 거래는 **원거래(승인) 시점 정책** 사용
- 정액 수수료:
  - 부분취소 시 수수료 환급 없음
  - 누적 부분취소가 전액취소에 도달하는 시점에만 수수료/부가세 환급

### 3.4 배치 멱등성

- Job 파라미터: `baseDate`, `pgCompany`
- 동일 파라미터 재실행 시 동일 결과 보장
- 전략: 대상 키(`baseDate`, `pgCompany`)의 기존 산출 결과 정리 후 재산출 저장

## 4) 데이터 모델 + DB 설정

### 4.0 DB 설정 전략

H2 file mode 설정 (`application.yml`):

```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/settlement;AUTO_SERVER=TRUE;MODE=PostgreSQL
    driver-class-name: org.h2.Driver
    username: sa
    password:
  sql:
    init:
      mode: always          # 매 시작 시 schema.sql 실행
  jpa:
    hibernate:
      ddl-auto: validate    # 스키마는 schema.sql이 책임, JPA는 검증만
    defer-datasource-initialization: true   # data.sql을 JPA 초기화 후 실행
```

스키마/초기데이터 적용 순서:
1. `schema.sql` 실행 (테이블·인덱스 생성)
2. JPA Entity 스캔 + 검증 (`validate`)
3. `data.sql` 실행 (seed 데이터 삽입)

seed 데이터 규모:

| 대상 | 기본 seed(`data.sql`) | perf seed(`application-perf`) | 비고 |
|------|------------------------|-------------------------------|------|
| `fee_policy` | 5건 | 동일 | 정책 샘플 데이터 |
| `payment_transaction` | 6건 | 동일 | 승인/취소 규칙 검증 샘플 |
| `settlement_expected` | 0건 (배치 후 생성) | 최대 500,000건 자동 생성 | 조회 성능 측정용 대량 데이터 |

seed 생성 전략:

- 기본 실행: `data.sql` 기반 소량 seed
- 성능 측정: `perf` 프로파일에서 `SettlementPerfSeeder`로 `settlement_expected` 대량 생성

### 4.1 `fee_policy`

- `id`
- `pg_company`
- `merchant_id`
- `payment_method`
- `fee_type` (`RATE`/`FIXED`)
- `fee_value` (정률: 비율값, 정액: 원금액)
- `settlement_cycle_days` (D+N)
- `start_date`
- `end_date`
- `created_at`, `updated_at`

인덱스:

- `(pg_company, merchant_id, payment_method, start_date, end_date)`

### 4.2 `payment_transaction`

- `id`
- `transaction_id` (외부 거래 식별자, unique)
- `pg_company`
- `merchant_id`
- `payment_method`
- `transaction_type` (`APPROVAL`/`CANCEL`)
- `amount`
- `transaction_date`
- `original_transaction_id` (취소 시 원거래 연결)
- `created_at`

인덱스:

- `(pg_company, transaction_date)`
- `(merchant_id, transaction_date)`
- `(original_transaction_id)`

### 4.3 `settlement_expected`

- `id`
- `base_date`
- `pg_company`
- `transaction_id`
- `merchant_id`
- `transaction_date`
- `transaction_type`
- `amount`
- `fee_amount`
- `vat_amount`
- `expected_settlement_amount`
- `expected_settlement_date`
- `created_at`

인덱스:

- `(merchant_id, transaction_date)`  // 조회 핵심
- `(transaction_date)`
- `(base_date, pg_company)`  // 배치 재실행 대상 정리용

## 5) API 설계(요구사항 충족)

### 5.1 수수료 정책 관리 API

- `POST /api/v1/policies` : 정책 등록
- `GET /api/v1/policies` : 정책 조회(조건 검색)
- `PUT /api/v1/policies/{policyId}` : 정책 변경

검증:

- 기간 중복 검증
- 수수료 타입/값 형식 검증(정률/정액)
- D+N 유효성 검증(N >= 0)
- 존재하지 않는 정책 변경 요청은 404 반환
- 정책 변경 API(`PUT`)는 `endDate` 조정 중심이며, 수수료 값/타입 변경은 신규 정책 등록 + 기존 정책 종료일 조정 방식

### 5.2 정산 내역 조회 API

- `GET /api/v1/settlements`
- 필수 조건: `merchantId`, `fromDate`, `toDate`
- 선택 조건: `transactionType`, `amount`
- 페이징: `page`, `size`, `sort`

필수 응답 항목 포함:

- `merchantId`
- `transactionDate`
- `transactionType`
- `amount`
- `expectedSettlementAmount`
- `expectedSettlementDate`

## 6) Batch Job 설계

Job: `settlementExpectedJob`

입력 파라미터:

- `baseDate` (예: 2026-03-10)
- `pgCompany` (예: PG1)

Step 흐름:

1. 대상 거래 조회 (`pgCompany`, `baseDate`)
2. 거래별 적용 정책 결정
   - 승인: 거래일 기준 유효 정책
   - 취소: 원거래 승인 시점 정책
3. 수수료/부가세/정산예정금액 계산
4. 정산 예정일 계산 (`transaction_date + D+N`)
5. 결과 저장(멱등성 보장)

Processor 예외 처리 정책:

- `PolicyNotFoundException`, `InvalidTransactionException`: skip + 경고 로그
- 그 외 런타임 예외: 전파하여 Job 실패 (silent data loss 방지)

## 7) TDD 실행 계획 (핵심)

모든 기능은 아래 루프 반복:

1. 실패 테스트 작성 (RED)
2. 최소 구현으로 통과 (GREEN)
3. 리팩터링 (REFACTOR)

### Phase A: 계산 도메인 (core)

- 수수료 계산기 테스트 우선 작성
  - 정률 계산
  - 정액 계산
  - 부가세 10%
  - 부분취소/전액취소(정액 환급 규칙)
- 정책 매칭 로직 테스트
  - 기간 내 정책 선택
  - 중복 기간 거부

### Phase B: 정책 API (api)

- Controller/Service 통합 테스트 우선
- 정책 등록/조회/변경 + 유효성 실패 케이스
- 존재하지 않는 정책 변경 요청 404 테스트

### Phase C: 배치 (batch)

- Job 파라미터 기반 실행 테스트
- 같은 파라미터 재실행 멱등성 테스트
- 취소 시 원거래 정책 적용 테스트
- 정책 미존재/잘못된 취소 참조 skip 테스트
- 예상치 못한 런타임 예외 전파(Job 실패) 테스트

### Phase D: 정산 조회 API (api)

- 필수 조건 누락 실패 테스트
- 조건 조합 + 페이징 테스트
- 응답 필수 필드 검증
- 잘못된 enum 파라미터(`transactionType`) 400 + Problem Details 테스트

## 8) Swagger/OpenAPI 추가 계획

- 라이브러리: `springdoc-openapi-starter-webmvc-ui`
- 노출 경로:
  - `/swagger-ui.html`
  - `/v3/api-docs`
- 각 API 요청/응답 예시 및 필수 필드 명시

운영 편의:

- 평가자가 브라우저에서 즉시 API 시나리오 확인 가능
- 테스트 데이터 기반 샘플 요청 제공

## 9) 성능 계획 (README 반영)

### 9.1 인덱스-쿼리 매핑

| 조회 시나리오 | 사용 인덱스 | 이유 |
|---|---|---|
| merchantId + 거래일자 범위 (필수) | `(merchant_id, transaction_date)` | equality 먼저, range 뒤 → B-Tree 최적 |
| 거래일자 범위만 (단독) | `(transaction_date)` | 보조 인덱스 |
| 배치 재실행 대상 정리 | `(base_date, pg_company)` | DELETE 대상 특정 |

> **PRD "1위 거래일자, 2위 가맹점ID" 해석**: 이는 조회 *빈도* 순위이지 인덱스 컬럼 순서가 아님.  
> `merchant_id`가 equality, `transaction_date`가 range이므로 카디널리티·선택도 기준으로 `(merchant_id, transaction_date)` 순이 옳음. README에 이 결정 근거를 명시.

### 9.2 페이징 전략

- 기본: Spring Data `Pageable` (offset 기반), 기본 page size = 20, 최대 100
- 50만 건 + deep page 문제 대응: `fromDate`/`toDate` 범위를 **최대 31일**로 제한
  - 이로써 단일 쿼리 대상이 가맹점당 최대 수만 건으로 제한됨
  - 범위 초과 시 `400 Bad Request` 반환
- 추후 keyset pagination 전환 시 `lastId` 파라미터 추가로 무중단 전환 가능한 구조로 설계

### 9.3 측정 도구 및 방법

도구: **Spring Boot Actuator** + `StopWatch` 로깅 (외부 도구 없이 README에 결과 기록)  
측정 방법:
1. 서버 기동 후 warm-up 10회 요청
2. 각 시나리오 50회 연속 요청 → 평균/p95 측정

시나리오:

| # | 시나리오 | 조건 |
|---|---|---|
| S1 | 기본 (필수 조건만) | merchantId + 30일 범위 |
| S2 | 선택 조건 추가 | + transactionType=APPROVAL |
| S3 | 금액 필터 추가 | + amount=100000 |
| S4 | 딥 페이지 | page=50, size=20 |
| S5 | Cold cache | H2 재시작 후 첫 요청 |

목표 SLO: **S1~S4 시나리오 p95 < 300ms** (H2 file mode 기준)

- S5(cold cache)는 참고 지표로 별도 기록

### 9.4 JVM 옵션 (측정 환경 README 기재)

```
-Xms512m -Xmx1g -XX:+UseG1GC
```

실행 환경도 함께 기재: CPU 모델, 코어 수, 메모리, OS

## 10) 자동 테스트 전략 결론 (요청사항 조사 반영)

이 과제의 자동 테스트 주력은 브라우저 도구가 아니라 백엔드 테스트 체계로 설정:

- 사용: JUnit 5, Mockito, MockMvc, SpringBootTest, SpringBatchTest
- 보조: Swagger UI는 수동 검증/데모 용도
- 비권장: Playwright/Agent Browser를 API 주 테스트로 사용

사유:

- 본 과제는 UI가 아닌 백엔드 도메인/배치/조회 성능 검증이 핵심
- 브라우저 자동화는 느리고 유지보수 비용이 높음
- 채용 과제 평가 포인트와 직접 연결되는 것은 서버 테스트 코드

## 11) 구현 순서 체크리스트

- [x] 멀티모듈 Gradle 뼈대 구성 (`core`, `api`, `batch`)
- [x] H2 file mode 설정 + `schema.sql`/`data.sql`
- [x] `core` 계산 로직 TDD 완료
- [x] 정책 API TDD 완료
- [x] 배치 Job TDD 완료 (멱등성/취소규칙)
- [x] 정산 조회 API TDD 완료 (조건조합/페이징)
- [x] Swagger 문서화 완료
- [x] 성능 측정 및 README 기록 (50만 건 seed, S1~S5 측정값/환경 반영 완료)
- [x] 미정의 비즈니스 룰 가정사항 README 명시

## 13) 가정사항 (Assumptions) — README에도 명시

| # | 항목 | 결정 |
|---|------|------|
| A1 | 정률 수수료 부분취소 | 취소 금액 비례로 수수료/부가세 환급 (정액과 달리 부분취소도 환급) |
| A2 | 환급 금액 표현 | `fee_amount`, `vat_amount`, `expected_settlement_amount` 모두 **음수**로 저장 |
| A3 | 전액취소 도달 시 정액 환급 row | 마지막 취소 거래의 정산 row에 음수 수수료/부가세를 attach (별도 row 없음) |
| A4 | 취소 거래의 `original_transaction_id` | 외부에서 제공 (seed 데이터에 포함). 없으면 배치 처리 건너뜀 + 경고 로그 |
| A5 | 정책 미존재 시 | 배치에서 해당 거래 skip + 경고 로그. 정산 row 미생성 |
| A6 | `baseDate` 정의 | 배치 처리 기준일 = 거래가 발생한 날 (= `transaction_date`). 해당 날의 거래를 일괄 처리 |
| A7 | 정산 주기 D+0 | 즉시 정산. `expected_settlement_date = transaction_date` |
| A8 | 조회 날짜 범위 최대 | 31일 초과 시 400 반환 |
| A9 | 정책 `end_date` | 포함(inclusive). 거래일이 `end_date`와 같아도 해당 정책 적용 |
| A10 | 중복 기간 검증 동시성 | DB unique 제약 없음(H2 한계) → 서비스단 비관적 락 또는 SERIALIZABLE 트랜잭션으로 방어 |
| A11 | 배치의 예상치 못한 예외 | skip하지 않고 예외 전파로 Job 실패 처리 |

## 14) 기술 세부 결정

### 14.1 에러 응답 포맷

RFC 7807 Problem Details 통일:

```json
{
  "type": "https://settlement.example.com/errors/policy-overlap",
  "title": "수수료 정책 기간 중복",
  "status": 409,
  "detail": "merchant1 + CARD 조합에 2026-01-01~2026-12-31 구간이 이미 존재합니다."
}
```

주요 매핑:

- 정책 기간 중복: `409 Conflict`
- 정책 변경 대상 없음: `404 Not Found`
- 요청 검증 실패/필수 파라미터 누락/타입 불일치: `400 Bad Request`

### 14.2 트랜잭션 경계

- 정책 등록/변경: 중복 검증 + INSERT/UPDATE 단일 트랜잭션
- 배치 Step: chunk 단위 트랜잭션 (실패 시 해당 chunk만 롤백)
- 조회 API: `@Transactional(readOnly = true)`

### 14.3 배치 상세

- Reader: `JpaCursorItemReader` (대용량 scroll, 메모리 효율)
- Processor: 정책 조회 + 수수료 계산 + 정산예정일 산출
  - known 예외(`PolicyNotFoundException`, `InvalidTransactionException`)는 skip
  - unknown 런타임 예외는 전파하여 Job 실패
- Writer: `JpaItemWriter` (chunk size = 500)
- 멱등성 Tasklet: Step 0에서 `DELETE FROM settlement_expected WHERE base_date=? AND pg_company=?` 실행 후 본 Step 진행
- 배치 트리거: `ApplicationRunner` (배치 모듈 부트 앱 실행 시 파라미터 수신)

### 14.4 정책 중복 검증 쿼리

```sql
SELECT COUNT(*) FROM fee_policy
WHERE pg_company = ? AND merchant_id = ? AND payment_method = ?
  AND NOT (end_date < :newStart OR start_date > :newEnd)
```

결과 > 0이면 409 반환.

## 15) 완료 기준 (Definition of Done)

- 필수 API/배치 요구사항 100% 충족
- 테스트 자동 실행 성공 (`./gradlew test`)
- 동일 배치 파라미터 재실행 시 동일 결과
- 정산 조회 API 페이징 + 필수 응답 필드 충족
- S1~S4 시나리오 p95 < 300ms 달성
- S5(cold cache) 측정값은 참고 지표로 별도 기록
- README에 테이블 설계 의도/성능 결과/가정사항/빌드방법 문서화
