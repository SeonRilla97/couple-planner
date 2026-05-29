# 기능 구현 체크리스트 — 커플 공유 일정관리

> 기준: [`requirements.md`](./requirements.md) · 테스트: [`test-cases.md`](./test-cases.md) / 작성일: 2026-05-29
>
> 각 항목은 `(FR-x)` 근거와 대응 테스트 `(Tn)`를 단다. 테스트가 `(—)`인 항목은 부록 B 기준
> 단위테스트 대신 Swagger/Postman 수동 확인 대상이다. 체크박스는 **구현 완료 + 해당 테스트 통과** 기준으로 켠다.

---

## 0. 사전 준비 (인프라/공통)

- [ ] `build.gradle`에 **Spring Security + JWT**(예: jjwt) 의존성 추가 (FR-1 전제, 현재 미포함)
- [ ] PII 결정적 암호화 컴포넌트 + **키 분리**: 암호화 키를 DB 밖 설정/시크릿으로 관리 (FR-1.2)
- [ ] BCrypt 인코더 빈 등록 (FR-1.1)
- [ ] 표준 에러 응답 `{ code, message }` + `@RestControllerAdvice` 전역 핸들러, HTTP status 매핑 400/401/403/404/409 (§1 에러 응답)
- [ ] 시각 정책: 저장 `Instant`(UTC), API 입출력 `OffsetDateTime`(ISO-8601) (FR-4.5)

---

## 1. 도메인 모델 / 엔티티

> 식별자 정책: 외부=`public_id`(UUID)·JWT `sub`, 내부 FK/Join=auto-increment `BIGINT` PK (§1)

- [ ] `User` 엔티티: `id`(PK) / `public_id`(UUID) / `email_enc`(암호문, unique) / `password`(BCrypt) / `nickname` / `couple_code`(unique) / `personal_color` / `created_at`
- [ ] `Couple` 엔티티: `requester_id` / `target_id` / `status(PENDING/CONNECTED)` / `connected_at` / 제약 `requester_id <> target_id`
- [ ] `CoupleProfile` 엔티티: `couple_id`(PK 겸 FK, 1:1) / `couple_color` / `created_at`
- [ ] `Schedule` 엔티티: `owner_id` / `couple_id`(COUPLE만) / `visibility(PRIVATE/SHARED/COUPLE)` / `title` / `content` / `start_at` / `end_at`, 제약 `end_at >= start_at`, `COUPLE이면 couple_id 필수`

### 도메인 순수 메서드 (DB/프레임워크 모름 → 단위 테스트 대상)
- [ ] `Couple.connect()`: PENDING→CONNECTED, `connected_at` 기록. 이미 CONNECTED면 거절 **(T1, FR-2.3)**
- [ ] `Couple` 생성 시 `requester == target` 금지 **(T1, FR-2.2)**
- [ ] PENDING 만료 판정: `created_at + 10분` 경계 (미만=유효 / 이상=만료) **(T2, FR-2.7)**
- [ ] `Schedule` 시간 불변식: `end < start` 거절, `end == start` 허용, UTC 절대시각 기준 **(T6, FR-4.1·4.5)**
- [ ] `Schedule` 등록 불변식: `visibility=COUPLE`이면 `couple_id` 필수 **(— · FR-4.3)** ※ T6에 케이스 추가 권장
- [ ] 캘린더 category 판정 + 파트너 PRIVATE 제외: MINE/COUPLE/PARTNER **(T7, FR-5.4·5.3)**
- [ ] 색상 매핑: MINE→`personal_color`, PARTNER→파트너 `personal_color`, COUPLE→`couple_color`, 미설정 시 기본색 **(T5, FR-7.4·7.1·7.2)**
- [ ] 비밀번호 정책: 9자 이상 + 영문·숫자·특수문자 3종 모두 **(T8, FR-1.10)**

---

## 2. FR-1 회원/인증 (JWT)

- [ ] **FR-1.1** 회원가입: email+password+nickname 받아 `public_id`/`couple_code`(영대문자+숫자 8자, 충돌 재생성) 발급, BCrypt 저장, `personal_color` 기본색 **(—)**
- [ ] **FR-1.2** email 결정적 암호화 저장 + `WHERE email_enc=?` 조회, 평문 컬럼 없음 **(T9)**
- [ ] **FR-1.3** 로그인: 검증 성공 시 Access+Refresh Token 발급 **(—)**
- [ ] **FR-1.4** JWT claim: `sub=public_id(UUID)`, `role`, `coupled` **(T16)**
- [ ] **FR-1.5** 토큰 없음/무효 → 401 (보안 필터) **(—, T11/T16 간접)**
- [ ] **FR-1.6** Access Token TTL 15분 + **로그인·커플연결 시 재발급** (coupled 최신화) **(T12)**
- [ ] **FR-1.7** Refresh(TTL 2주)로 Access 재발급, 발급 시점 `coupled` 반영. 만료/무효 시 재로그인 **(T16)**
- [ ] **FR-1.8** 내 정보 조회: `public_id/nickname/email/couple_code/personal_color/커플상태` 반환. 내부 PK·couple_id 미노출 **(—)**
- [ ] **FR-1.9** 커플 코드 재발급: 새 랜덤 코드 교체. 진행 중 커플(만료 PENDING 제외) 있으면 **409 거절** **(—)**
- [ ] **FR-1.10** 입력 검증 → 400: email(형식·≤255) / password(T8) / nickname(1~8자, 공백만 불가) / couple_code(트림 후 형식) / color(`#RRGGBB`) **(password=T8, 나머지 —)**

---

## 3. FR-2 커플 연결

> 권한 주체는 JWT에서 도출, 요청 본문 id 불신 (FR-1.4)

- [ ] **FR-2.1** 입력 코드의 주인 Y 조회, 없는 코드면 실패 **(—)**
- [ ] **FR-2.2** 자기 코드 입력 금지 **(T1)**
- [ ] **FR-2.6** 트랜잭션 시작 시 **두 유저 row를 PK 오름차순으로 `PESSIMISTIC_WRITE` 락** 획득 (데드락 방지) **(T15)**
- [ ] **FR-2.3** 역방향 만료 안 된 PENDING(req=Y,target=X) **먼저** 조회 → 있으면 그 행을 CONNECTED 전이 + `couple_profile` 생성 (새 행 X) **(T14)**
- [ ] **FR-2.5** 둘 다 어떤 커플 row(PENDING/CONNECTED, 만료 PENDING 제외)도 없어야 연결. 하나라도 있으면 실패 — **서비스 단 보장** **(T15)**
- [ ] **FR-2.4** 없으면 새 PENDING(req=X, target=Y) 생성 **(—)**
- [ ] **FR-2.7** 만료 PENDING은 read 시점 lazy로 "없는 것" 취급(FR-2.3·2.5 판정에서 제외) **(T2)**

---

## 4. FR-3 접근 게이트

- [ ] **FR-3.1** 앱 기능 진입 전 JWT `coupled` claim으로 판정 (보안 필터, DB 조회 없음) **(T11)**
- [ ] **FR-3.2** `coupled=false` → 앱 기능 403, 커플 연결 API만 허용 **(T11)**
- [ ] **FR-3.3** claim은 발급 시점 스냅샷 → 구 토큰 차단, 재발급/TTL로 수렴 **(T12)**

---

## 5. FR-4 일정 관리

- [ ] **FR-4.1** 등록: owner=본인(토큰), `title/start_at/end_at` 필수, `end>=start` **(T6)**
- [ ] **FR-4.2** visibility 지정 PRIVATE/SHARED/COUPLE **(—)**
- [ ] **FR-4.3** COUPLE은 본인 CONNECTED 커플의 `couple_id`로 등록, 그 외 `couple_id=null` **(— · 불변식은 T6 권장)**
- [ ] **FR-4.4** 수정/삭제는 **owner 본인만** (게이트 외 추가 인가) **(— ※ 테스트 보강 검토)**
- [ ] **FR-4.5** `Instant`(UTC) 저장, API 입출력 오프셋 포함, 검증·기간조회는 UTC 기준 **(T6)**

---

## 6. FR-5 일정 조회 (FR-3 게이트 통과 전제)

- [ ] **FR-5.1** 내 일정: `owner_id=본인` (PRIVATE+SHARED) **(—)**
- [ ] **FR-5.2** 커플 일정: `couple_id=내 커플 AND visibility=COUPLE` **(—)**
- [ ] **FR-5.3** 상대방 일정: `owner_id=파트너 AND visibility=SHARED` (PRIVATE 제외) **(T7)**
- [ ] **FR-5.4** 캘린더 통합: 셋 합쳐 `category/visibility/color` 동봉, 파트너 PRIVATE 제외 **(T7)**
- [ ] **FR-5.5** 통합 조회 기간 **최대 3개월**, 초과 시 **400 거절** **(— ※ 테스트 누락, 추가 권장)**

---

## 7. FR-7 커플 프로필 / 색상

- [ ] **FR-7.1** 개인색 `users.personal_color` 설정·변경, 미설정 시 기본색 **(T5)**
- [ ] **FR-7.2** 커플색 `couple_profile.couple_color`: 누구나 변경, **양쪽 동일 반영**, 미설정 시 기본색 **(매핑=T5, 공유반영=—)**
- [ ] **FR-7.3** `couple_profile`은 CONNECTED 전이 시 생성 **(T14)**
- [ ] **FR-7.4** 색상 매핑 MINE/PARTNER→개인색, COUPLE→커플색 **(T5)**

---

## 부록: 테스트 미커버 → 결정 필요 항목

> test-cases.md를 그대로 둔 상태에서 구현 시 짚고 갈 갭. (리뷰 발견 사항)

| 항목 | FR | 현황 | 권장 |
|---|---|---|---|
| 조회 기간 3개월 제한 | FR-5.5 | 테스트·부록 모두 없음 (누락) | 경계값 단위/슬라이스 테스트 추가 |
| COUPLE이면 couple_id 필수 | FR-4.3 | 불변식 테스트 불명확 | T6에 케이스 한 줄 추가 |
| 수정/삭제 owner 인가 | FR-4.4 | 명시적 테스트 없음 | 슬라이스 테스트 보강 or 부록 B에 제외 근거 명시 |
| 커플색 양쪽 반영 | FR-7.2 | 매핑만 커버 | 통합 1개 검토(선택) |
