# 기능 구현 체크리스트 — 커플 공유 일정관리 (TDD 진행 순서판)

> 기준: [`requirements.md`](./requirements.md) · 테스트: [`test-cases.md`](./test-cases.md) · 전략: [`study/test.md`](../../study/test.md) / 작성일: 2026-05-29 · 개정: 2026-05-29
>
> **이 문서는 "무엇을 만드나(FR)"가 아니라 "어떤 순서로 짜나(TDD 진행)"로 정렬한다.**
> [`test.md`](../../study/test.md)의 피라미드 원칙대로 **아래(싸고 좁은 단위)부터 위(비싸고 넓은 통합)로** 올라간다.
> 각 단계는 **red→green 루프**다: 해당 `(Tn)` 테스트를 **먼저 쓰고(red)**, 통과시키는 **최소 구현(green)** 을 한다.
> 인프라·의존성·`(—)` 수동확인 구현은 *그게 필요해지는 단계로 당겨* 배치했다.
> 체크박스는 **구현 완료 + 해당 테스트 통과**(또는 `(—)`는 Swagger/Postman 1회 확인) 기준으로 켠다.
>
> 표기: `(Tn)`=test-cases.md 케이스(전체 12개), `(FR-x)`=근거 요구사항, `(—)`=단위테스트 없이 수동 확인(부록 B).
> 식별자 정책(§1): 외부=`public_id`(UUID)·JWT `sub`, 내부 FK/Join=auto-increment `BIGINT` PK.
> **헤어짐/삭제(FR-6)는 이번 스프린트 범위 외** — 관련 항목은 모두 제외했다(README '안 하는 것').

---

## Stage 1 — 도메인 단위 [단위] ⭐⭐⭐ *(의존성 추가 0 · 지금 바로 시작)*

> 순수 Kotlin 객체 + enum만. Spring·DB·프레임워크 없음. 가장 싸고, "도메인 순수성"(CLAUDE.md) 주장을 그대로 증명.
> 먼저 `src/test/.../fixture/`에 fixture 함수(기본값 인자)를 깔고 시작한다([`test.md` §8](../../study/test.md)).
> 산출물: enum(`CoupleStatus`(PENDING/CONNECTED), `Visibility`, `Category`) + 순수 도메인 객체(`Couple`, `Schedule`, 색상/비밀번호 규칙).

- [x] **1.1** `Couple.connect()`: `PENDING`→`CONNECTED`, `connected_at` 기록 / 이미 `CONNECTED`면 거절(불변식) **(T1, FR-2.3)**
- [x] **1.2** `Couple` 생성 불변식: `requester_id == target_id` 금지(자기 연결 금지) **(T1, FR-2.2)**
- [x] **1.3** PENDING 만료 판정: `created_at + 10분` 경계 — **미만=유효 / 정확히 10분·초과=만료** **(T2, FR-2.7)**
- [x] **1.4** 색상 매핑: `MINE`→본인 `personal_color`, `PARTNER`→파트너 `personal_color`, `COUPLE`→`couple_color`, 미설정 시 서버 기본색 **(T3, FR-7.4·7.1·7.2)**
- [x] **1.5** `Schedule` 시간 불변식: `end < start` 거절 / `end == start` 허용(경계) / 서로 다른 오프셋도 **UTC 절대시각 기준** 판정. 더불어 `visibility=COUPLE`이면 `couple_id` 필수 **(T4, FR-4.1·4.5·4.3)**
- [x] **1.6** 캘린더 `category` 판정 + 파트너 PRIVATE 제외: `owner==me`→`MINE`, `couple_id==내커플 && COUPLE`→`COUPLE`, `owner==파트너 && SHARED`→`PARTNER` / 파트너 `PRIVATE`는 어떤 category도 아님 → 제외 **(T5, FR-5.4·5.3)**
- [x] **1.7** 비밀번호 정책: **9자 이상 + 영문·숫자·특수문자 3종 모두**. 하나라도 빠지면 거절 **(T6, FR-1.10)**

> ✅ 단계 완료 기준: 위 단위 테스트(T1~T6 묶음)가 Spring/DB 없이 green.

---

## Stage 2 — 영속 계층 [@DataJpaTest] ⭐⭐⭐ *(mock으로 못 잡는 영역)*

> Stage 1의 순수 도메인에 **JPA 매핑을 입혀 엔티티화**한다(순수 메서드는 그대로 유지 — 인프라가 도메인에 새지 않게).
> H2로 실제 SQL·제약·매핑을 검증. 신규 의존성 없음(data-jpa 기보유).

### 2.A 엔티티 / 인프라 (테스트의 전제)
- [ ] **2.1** 시각 정책: 저장 `Instant`(UTC), API 입출력 `OffsetDateTime`(ISO-8601) — 엔티티 시각 컬럼 매핑 **(FR-4.5)**
- [ ] **2.2** PII 결정적 암호화 컴포넌트 + **키 분리**: 암호화 키를 DB 밖 설정/시크릿으로 관리(BCrypt와 별개) **(FR-1.2)**
- [ ] **2.3** `User` 엔티티: `id`(PK) / `public_id`(UUID) / `email_enc`(암호문, unique) / `password`(BCrypt) / `nickname` / `couple_code`(unique) / `personal_color` / `created_at`
- [ ] **2.4** `Couple` 엔티티: `requester_id` / `target_id` / `status(PENDING/CONNECTED)` / `connected_at` / `created_at`, 제약 `requester_id <> target_id`
- [ ] **2.5** `CoupleProfile` 엔티티: `couple_id`(PK 겸 FK, 1:1) / `couple_color` / `created_at`
- [ ] **2.6** `Schedule` 엔티티: `owner_id` / `couple_id`(COUPLE만) / `visibility(PRIVATE/SHARED/COUPLE)` / `title` / `content` / `start_at` / `end_at`, 제약 `end_at >= start_at`, `COUPLE이면 couple_id 필수`

### 2.B 테스트 (red→green)
- [ ] **2.7** 이메일 결정적 암호화 조회: 같은 평문→항상 같은 암호문 → `WHERE email_enc = ?` 조회 가능 / DB에 email **평문 컬럼 없음**(보안 불변식) **(T7, FR-1.2)**

> ✅ 단계 완료 기준: @DataJpaTest(T7) green.

---

## Stage 3 — 웹 계층 / 접근 게이트 [@WebMvcTest] ⭐⭐ *(보안 게이트 주력)*

> 보안 필터 단에서 JWT `coupled` claim으로 DB 없이 판정. `spring-security-test`의 `jwt()`로 claim을 흉내낸다.
> Service는 mock — 여기선 **웹 입출력 계약(상태코드·필터)** 만 검증한다.

### 3.A 의존성 / 인프라 (테스트의 전제)
- [ ] **3.1** `build.gradle` 의존성 추가: **Spring Security + JWT(jjwt 등) + spring-security-test** **(FR-1 전제, 현재 미포함)**
- [ ] **3.2** BCrypt 인코더 빈 등록 **(FR-1.1)**
- [ ] **3.3** JWT 발급/검증 유틸: claim `sub=public_id(UUID)`, `role`, `coupled` 구조 정의(여기선 검증·필터가 주, 발급 흐름은 Stage 4에서 통합 검증) **(FR-1.4)**
- [ ] **3.4** 보안 필터: `Authorization: Bearer <token>` 파싱 → `coupled` claim으로 앱 기능 진입 판정(DB 조회 없음). 토큰 없음/무효 → **401** **(FR-3.1, FR-1.5)**
- [ ] **3.5** 표준 에러 응답 `{ code, message }` + `@RestControllerAdvice` 전역 핸들러, status 매핑 400/401/403/404/409 **(§1 에러 응답)**
- [ ] **3.6** 최소 컨트롤러 골격: 일정 API(게이트 보호 대상) + 커플 연결 API(게이트 예외). Service는 mock

### 3.B 테스트 (red→green)
- [ ] **3.7** 게이트 차단/허용: `coupled=false` 토큰 → 앱 기능(일정 API) **403**, 단 커플 연결 API는 허용 / `coupled=true`는 앱 기능 통과 **(T8, FR-3.1·3.2)**
- [ ] **3.8** 구 토큰 차단 — claim 스냅샷: 연결 직후라도 옛 토큰(`coupled=false`)이면 차단, 재발급 후 통과 — claim은 스냅샷, TTL로 수렴 **(T9, FR-3.3·1.6)**

> ✅ 단계 완료 기준: @WebMvcTest 2개(T8·T9) green.

---

## Stage 4 — 전 계층 통합 [@SpringBootTest] ⭐ *(대체 불가능한 것만)*

> 전체 컨텍스트로 Controller→Service→Repository→DB가 맞물려 도는지 검증. **동시성·트랜잭션 경계·전체 흐름**은 여기서만.
> 이 단계의 비즈니스 와이어링 구현이 곧 `(—)` 수동확인 항목(가입·로그인·CRUD·조회)의 실체다 — 구현 후 Swagger/Postman으로 1회 확인.

### 4.A 인증/회원 비즈니스 와이어링
- [ ] **4.1** 회원가입: email+password+nickname → `public_id`/`couple_code`(영대문자+숫자 8자, 충돌 재생성) 발급, BCrypt 저장, `personal_color` 기본색 **(— · FR-1.1)**
- [ ] **4.2** 로그인: 검증 성공 시 **Access(TTL 15분)+Refresh(TTL 2주)** 발급. 로그인·커플연결 시 Access 재발급으로 `coupled` 최신화 **(— · FR-1.3·1.6)**
- [ ] **4.3** 내 정보 조회: `public_id/nickname/email/couple_code/personal_color/커플상태` 반환, **`couple_code` 노출**, 내부 PK·`couple_id` 미노출 **(— · FR-1.8)**
- [ ] **4.4** 커플 코드 재발급: 새 랜덤 코드 교체(충돌 재시도). 진행 중 커플(만료 PENDING 제외) 있으면 **409 거절** **(— · FR-1.9)**

### 4.B 커플 연결 서비스 (FR-2 전체 — 순서 주의)
- [ ] **4.5** 입력 코드의 주인 Y 조회, 없는 코드면 실패 **(— · FR-2.1)** / 자기 코드 입력 금지(Stage 1.2 규칙 사용) **(FR-2.2)**
- [ ] **4.6** 트랜잭션 시작 시 **두 유저 row를 PK 오름차순으로 `PESSIMISTIC_WRITE` 락** 획득(데드락 방지) **(FR-2.6)**
- [ ] **4.7** 역방향 만료 안 된 PENDING(req=Y, target=X)을 **먼저** 조회 → 있으면 그 행을 `CONNECTED` 전이 + `couple_profile` 생성(새 행 X) **(FR-2.3·7.3)**
- [ ] **4.8** 커플 1개 제한: 둘 다 어떤 커플 row(PENDING/CONNECTED, 만료 PENDING 제외)도 없어야 연결 — **서비스 단 보장** / 없으면 새 `PENDING`(req=X) 생성 **(FR-2.5·2.4)**
- [ ] **4.9** 만료 PENDING은 read 시점 lazy로 "없는 것" 취급(FR-2.3·2.5 판정에서 제외) **(FR-2.7, Stage 1.3 규칙 사용)**

### 4.C 일정 / 조회 / 색상 와이어링
- [ ] **4.10** 일정 등록(owner=토큰 주체)·수정·삭제. 수정/삭제는 **owner 본인만** / `COUPLE`은 본인 CONNECTED 커플 `couple_id`로 **(— · FR-4.1~4.5, 규칙은 Stage 1.5)**
- [ ] **4.11** 일정 조회: 내 일정(owner=본인) / 커플(couple_id=내커플 && COUPLE) / 상대 SHARED. 캘린더 통합에 `category/visibility/color` 동봉, 파트너 PRIVATE 제외 **(— · FR-5.1~5.4, 판정은 Stage 1.6)**
- [ ] **4.12** 통합 조회 기간 **최대 3개월**, 초과 시 **400 거절** **(— · FR-5.5 ※ 단위/슬라이스 경계 테스트 추가 권장 — 부록 갭 참조)**
- [ ] **4.13** 개인색 설정·변경(FR-7.1) / 커플색 설정·변경 — 누구나 변경, **양쪽 동일 반영** **(— · FR-7.1·7.2)**

### 4.D 테스트 (red→green) — T12를 먼저(나머지의 전제 메커니즘)
- [ ] **4.14** JWT claim 발급 + Refresh 갱신: Access claim에 `sub=public_id(UUID)`·`role`·`coupled` 스냅샷이 정확히 박힌다 / 유효한 Refresh로 재발급 시 `coupled`가 **현재 상태로 갱신** **(T12, FR-1.4·1.7)** → T9(구 토큰 차단)가 "재발급 후 통과"로 수렴하는 전제
- [ ] **4.15** 상호확인 연결 흐름: Y→X PENDING이 있을 때 X가 코드 입력 → **새 행 없이 그 행을** `CONNECTED` 전이 + `couple_profile` 생성 **(T10, FR-2.3·7.3)**
- [ ] **4.16** 동시성 락 — row 1개 보장: 두 유저가 동시에 서로 코드 입력해도 비관적 락(`PESSIMISTIC_WRITE`)으로 최종 커플 row **정확히 1개** **(T11, FR-2.6·2.5)**

> ✅ 단계 완료 기준: @SpringBootTest 3개(T10~T12) green + 4.A~4.C 흐름 Postman 1회 확인.

---

## 부록 A: 테스트가 아닌 검증 (코드리뷰)

- **데드락 방지(원 C-I10):** 두 유저 PK **오름차순 정렬 후 락 획득**으로 교차 요청 데드락을 구조적으로 차단(Stage 4.6). 정밀 재현 테스트는 flaky·고비용이라 짜지 않고 잠금 순서 고정을 코드리뷰로 검증한다([`concurrency.md`](../../study/concurrency.md)).

## 부록 B: 수동 확인(`(—)`) 항목 — Swagger/Postman

> "짜지 않는다"≠"검증 안 한다". Stage 4 와이어링 후 손으로 1회 찔러본다.
> 대상: 회원가입(4.1)·로그인/401(4.2)·내정보(4.3)·코드재발급 분기(4.4)·없는 코드/새 PENDING(4.5·4.8)·일정 CRUD/visibility별 조회(4.10·4.11)·커플색 양쪽 반영(4.13). 근거는 test-cases.md 부록 B.

## 부록 C: 테스트 미커버 → 결정 필요 항목 (리뷰 발견)

| 항목 | FR | 배치 | 현황 | 권장 |
|---|---|---|---|---|
| 조회 기간 3개월 제한 | FR-5.5 | 4.12 | 테스트·부록 모두 없음(누락) | 경계값 단위/슬라이스 테스트 추가 |
| COUPLE이면 couple_id 필수 | FR-4.3 | 1.5 | T4에 흡수(케이스 명시됨) | 유지 |
| 수정/삭제 owner 인가 | FR-4.4 | 4.10 | 명시적 테스트 없음 | 슬라이스 보강 or 부록 B 제외 근거 명시 |
| 커플색 양쪽 반영 | FR-7.2 | 4.13 | 매핑(T3)만 커버 | 통합 1개 검토(선택) |
