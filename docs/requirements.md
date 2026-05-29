# 요구사항 정의서 — 커플 공유 일정관리

> 작성일: 2026-05-29 / 상태: 초안

## 1. 개요

두 사람(커플)이 일정을 공유·관리하는 서비스. **커플 연결이 앱 사용의 전제**다.
연결되지 않은 유저는 "커플 연결" 외 기능을 쓸 수 없다(FR-3 접근 게이트).

- 스택: Kotlin + Spring Boot + Spring Data JPA(H2)
- 인증: JWT 방식. Access Token에 `sub(UUID) + role + coupled` claim을 담아 인가·접근 게이트를 보안 필터 단에서 DB 없이 판정한다(FR-1). *현재 `build.gradle`에 Spring Security/JWT 의존성 미포함 → 구현 시 추가 필요.*

### 아키텍처 원칙
- **레이어드**: Controller → Service → Repository.
- **도메인 순수성**: 도메인 규칙(상태 전이·검증)은 프레임워크/DB를 모르는 순수 메서드로 둔다(예: `couple.connect()`, `schedule` 시간 검증). → DB 없이 단위 테스트 가능.
- **인증 주체**: 권한 판단의 주체는 항상 JWT에서 도출하며, 요청 본문의 user id는 신뢰하지 않는다.

### 식별자 정책
- **외부(클라이언트/JWT) 노출 = UUID.** API 요청/응답과 JWT `sub`는 모두 UUID를 사용한다. 내부 auto-increment PK는 클라이언트에 노출하지 않는다.
- **내부(DB) = auto-increment `BIGINT` PK.** 모든 FK/Join은 이 내부 PK로 처리한다.
- JWT는 UUID만 담으므로, 인증된 요청에서 실제 데이터 쿼리는 `UUID → 내부 PK` 변환(DB 조회)을 거친다. JWT가 DB 없이 처리하는 범위는 **인가(Role)와 접근 게이트(`coupled`) 판정까지**다.

### 에러 응답 형식
- 표준 바디 `{ "code": "<ERROR_CODE>", "message": "<설명>" }` + HTTP status 매핑: 400(입력 검증), 401(미인증/토큰 무효), 403(접근 게이트 차단), 404(대상 없음), 409(충돌 — 코드 재발급 거절 등). *일단 이 형태로 두고, 써보고 불편하면 조정한다.*

## 2. 용어

| 용어 | 의미 |
|---|---|
| couple_code | 가입 시 발급되는 유저별 고유 코드. 상대가 입력하면 연결 시작 |
| PENDING | 한쪽만 코드를 입력한 상태(상호 확인 대기) |
| CONNECTED | 양쪽이 서로 코드를 입력한 활성 커플 |
| DISCONNECTED | 헤어진 커플. `delete_scheduled_at`까지 보존 후 물리 삭제 |
| visibility | 일정 공개 범위: `PRIVATE`(비공개) / `SHARED`(상대 공개) / `COUPLE`(커플 공동) |

## 3. 도메인 모델

> 스키마(DDL)는 별도 문서 없이 구현 시 엔티티/마이그레이션으로 작성한다.

### User (`users`)
- `id`(내부 PK, BIGINT auto-increment), `public_id`(UUID, unique, 외부 노출용), `email_enc`(email 결정적 암호화, unique — 조회·유니크 동시 처리), `password`(BCrypt 해시), `nickname`(1~8자, FR-1.10), `couple_code`(unique), `personal_color`(HEX, 본인 일정 표시색), `created_at`
- 식별자: 외부=`public_id`(UUID), 내부 Join/FK=`id`(BIGINT). §1 식별자 정책 참조.

### Couple (`couple`)
- `id`, `requester_id`(먼저 코드 입력), `target_id`(코드 주인), `status`, `connected_at`, `disconnected_at`, `disconnected_by`, `delete_scheduled_at`, `created_at`
- 제약: `requester_id <> target_id`, `status IN (PENDING, CONNECTED, DISCONNECTED)`
- 유저당 커플 1개(상태 무관, 만료 PENDING 제외)는 **서비스 트랜잭션에서 보장**한다 — DB UNIQUE/부분 인덱스는 DISCONNECTED 보존·H2 한계로 미적용(FR-2.5/FR-2.6).
- 재결합은 **새 행 생성**으로 처리한다(기존 `DISCONNECTED` 행 재사용 안 함).

### CoupleProfile (`couple_profile`)
- `couple_id`(PK 겸 FK, couple 1:1), `couple_color`(HEX, 커플 공동 일정 표시색 — 양쪽 공유), `created_at`
- `CONNECTED` 전이 시 생성, 물리 삭제(FR-6.3) 시 커플과 함께 삭제. (색상 모델 = 유저 개인색 + 공유 커플색, FR-7 참조.)

### Schedule (`schedule`)
- `id`, `owner_id`, `couple_id`(COUPLE일 때만), `visibility`, `title`, `content`, `start_at`, `end_at`, `created_at`
- 제약: `end_at >= start_at`, `visibility=COUPLE`이면 `couple_id` 필수

## 4. 기능 요구사항

### FR-1 회원/인증 (JWT)
- **FR-1.1** 회원가입: email + password + nickname. 가입 시 `public_id`(UUID)와 `couple_code`(랜덤 영문 대문자+숫자 8자, unique — 충돌 시 재생성) 자동 발급. password는 BCrypt 해시 저장(평문 저장 금지). `personal_color`는 미설정(기본색)으로 시작.
- **FR-1.2** email은 중복 불가. email 등 개인정보(PII)는 **결정적 암호화**로 저장하며(평문 컬럼 없이 암호문 컬럼 1개), 그 암호문에 직접 unique 제약과 로그인 조회(`WHERE email_enc = ?`)를 건다. 목적은 DB만 탈취당해도 평문을 복원하지 못하게 하는 것 — email은 본래 유니크하므로 결정적 암호화의 equality 노출 약점은 무력화된다.
  - **키 분리**: PII 암호화 키는 DB 밖(설정/시크릿 매니저)에서 관리하고 비밀번호 BCrypt와 별개로 둔다. DB만 유출돼도 키가 없어 복호화 불가 — 한쪽 유출이 다른 쪽으로 전파되지 않게 키 출처/수명주기를 분리한다.
  - **검색 제약**: 결정적 암호화는 `WHERE email_enc = ?` 완전 일치 조회만 가능 → email 부분/전방 일치(LIKE) 검색은 제공하지 않는다(현 기획은 로그인 일치 확인만 필요. 추후 관리자 검색 등이 추가되면 구조 변경 필요).
- **FR-1.3** 로그인: email + password 검증 성공 시 **Access Token + Refresh Token** 발급. Access Token은 짧은 TTL(FR-1.6), Refresh Token으로 재발급한다(FR-1.7).
- **FR-1.4** JWT claim: `sub=public_id(UUID)`, `role`, `coupled`(CONNECTED 커플 보유 여부). 인가(Role)와 접근 게이트(`coupled`)는 이 claim으로 DB 없이 판정. 보호 API는 `Authorization: Bearer <token>` 헤더로 인증하고, 주체는 토큰에서 도출한다(클라이언트가 보낸 id 불신).
- **FR-1.5** 토큰이 없거나 유효하지 않으면 401.
- **FR-1.6** Access Token TTL은 짧게(기준값 15분). **로그인·커플 연결(FR-2.3)·헤어짐(FR-6.1) 시 Access Token을 재발급**하여 `coupled` claim을 최신화한다. → 연결 직후 잠금/헤어짐 후 잔여 접근 윈도우를 최소화.
- **FR-1.7** 토큰 재발급: 유효한 Refresh Token으로 새 Access Token을 발급하며, 발급 시점의 최신 `coupled` 상태를 반영한다. Refresh Token TTL은 길게(기준값 2주). Refresh Token이 만료·무효면 재로그인.
- **FR-1.8** 내 정보 조회: 인증된 유저의 `public_id`, `nickname`, `email`, `couple_code`, `personal_color`, 현재 커플 상태(`NONE`/`PENDING`/`CONNECTED`/`DISCONNECTED`)를 반환한다. **`couple_code`는 여기서 노출**하여 상대에게 공유한다(FR-2 연결의 전제). 내부 PK·`couple_id`는 응답에 포함하지 않는다(§1 식별자 정책).
- **FR-1.9** 커플 코드 재발급: 내 `couple_code`를 새 랜덤 코드로 교체한다(unique 충돌 시 재생성 재시도). 단, 진행 중인 커플 row(`PENDING`/`CONNECTED`/`DISCONNECTED`, 만료 PENDING 제외)가 있으면 **409로 거절** — 코드 교체로 인한 연결 혼선 방지.
- **FR-1.10** 입력 검증(위반 시 400):
  - `email`: 비어있지 않음, 이메일 형식, ≤255자
  - `password`: **9자 이상**, **영문자·숫자·특수문자 3종 모두 포함**
  - `nickname`: 1~8자, 공백만은 불가
  - `couple_code`(입력): 트림 후 형식(영문 대문자+숫자) 일치
  - `personal_color`/`couple_color`: `#RRGGBB` HEX 포맷

### FR-2 커플 연결
- **FR-2.1** 유저 X가 코드를 입력하면 그 코드의 주인 Y를 찾는다. 없는 코드면 실패.
- **FR-2.2** 자기 자신의 코드 입력 금지.
- **FR-2.3** 이미 Y가 X의 코드를 넣어 둔 **만료되지 않은**(FR-2.7) `PENDING`(requester=Y, target=X)이 있으면 → **새 행을 만들지 않고 그 행을** `CONNECTED`로 전이(`connected_at` 기록, couple_profile 생성 FR-7.3). (상호 확인) 즉 새 `PENDING` 생성(FR-2.4)보다 역방향 `PENDING` 조회를 **먼저** 수행한다.
- **FR-2.4** 없으면 새 `PENDING`(requester=X, target=Y) 생성.
- **FR-2.5** 유저는 커플을 동시에 **하나만** 가진다(상태 무관 — `PENDING`/`CONNECTED`/`DISCONNECTED` 통틀어, **단 만료 PENDING 제외**). 연결 시 **입력자(X)와 코드 주인(Y) 둘 다** 어떤 커플 row도 없어야 하며, 둘 중 한 명이라도 row가 있으면 연결 실패(이미 커플이면 애초에 튕긴다). → **서비스 단에서 보장**(DB UNIQUE로는 DISCONNECTED 보존 때문에 강제 못 함).
- **FR-2.6** (동시성 방어) FR-2.5 검증은 `if(exists) → insert` 사이에 경쟁 조건이 있어, 양쪽이 동시에 서로의 코드를 입력하거나 한쪽이 광클하면 한 유저에게 커플 row가 2개 생길 수 있다. 이를 막기 위해 커플 연결 트랜잭션은 **관련 두 유저 row에 JPA 비관적 락(`PESSIMISTIC_WRITE`)을 건 뒤** FR-2.3~2.5 검증·생성을 진행한다. 데드락 방지를 위해 **두 유저의 내부 PK를 오름차순으로 정렬해 락을 획득**한다. (단일 인스턴스 + H2 전제이므로 분산 락은 도입하지 않는다.)
- **FR-2.7** (PENDING 만료) `PENDING`은 생성(`created_at`) 후 **10분**이 지나면 만료된 것으로 본다. 만료된 PENDING은 (1) 커플 1개 제한(FR-2.5)·역방향 확인(FR-2.3) 판정에서 **없는 것으로 취급**하고(read 시점 lazy 판정), (2) row의 물리 정리는 스케줄러(FR-6.3, TODO)가 담당한다. → 잘못된 코드 입력·상대 무응답으로 인한 영구 잠금 방지. (lazy 판정 덕에 스케줄러가 아직 없어도 새 연결은 가능하다.)

### FR-3 접근 게이트
- **FR-3.1** 일정 등록/조회 등 앱 기능 진입 전, JWT의 `coupled` claim으로 활성 커플 여부를 판정한다(보안 필터 단, DB 조회 없음).
- **FR-3.2** `coupled=false`(=`PENDING`만 있거나 커플 없음/헤어짐)이면 앱 기능 차단(403), 커플 연결만 허용.
- **FR-3.3** `coupled` claim은 토큰 발급 시점의 스냅샷이다. 상태 변화는 FR-1.6의 재발급 + TTL(≤15분)로 수렴한다. 즉 차단은 토큰 만료 내에 보장된다(FR-6.6).

### FR-4 일정 관리
- **FR-4.1** 일정 등록: owner=본인, `title`/`start_at`/`end_at` 필수, `end_at >= start_at`.
- **FR-4.2** visibility 지정: `PRIVATE` / `SHARED` / `COUPLE`.
- **FR-4.3** `COUPLE` 일정은 본인이 속한 `CONNECTED` 커플의 `couple_id`로 등록. 그 외 visibility는 `couple_id`=null.
- **FR-4.4** 일정 수정/삭제는 **owner 본인만** 가능.
- **FR-4.5** (시간대) 일정 시각은 **`Instant`(UTC)로 저장**하고 API 입출력은 타임존 오프셋을 포함한다(`OffsetDateTime`/ISO-8601). `end_at >= start_at` 검증과 캘린더 기간 조회는 이 절대시각(UTC) 기준으로 수행하여 서버-클라이언트 시간대 불일치로 날짜가 밀리는 문제를 막는다. 클라이언트 표시는 클라이언트 타임존(예: `Asia/Seoul`)에서 변환한다.

### FR-5 일정 조회 (모두 FR-3 접근 게이트 통과 전제)
- **FR-5.1** 내 일정: `owner_id = 본인` (PRIVATE + SHARED 전부).
- **FR-5.2** 커플 일정: `couple_id = 내 커플 AND visibility = COUPLE`.
- **FR-5.3** 상대방 일정: `owner_id = 파트너 AND visibility = SHARED`. (파트너의 PRIVATE는 제외)
- **FR-5.4** 캘린더 통합 조회: 기간(예: 한 달) 안에서 위 셋을 합쳐 반환하며, 각 항목에 `category`(`MINE`/`COUPLE`/`PARTNER`), `visibility`(`PRIVATE`/`SHARED`/`COUPLE`), `color`를 함께 내린다. 파트너 PRIVATE 제외. **막대 색은 `category` 기준(FR-7.4), 공개/비공개 구분은 `visibility` 필드 기준 — 두 축은 독립**이다(예: 내 일정은 PRIVATE·SHARED 모두 내 색이되 visibility로 구분 렌더). 실제 시각 표현은 클라이언트 몫.
- **FR-5.5** (조회 기간 제한) 통합 조회는 1회 요청당 조회 가능 기간을 **최대 3개월**로 제한한다. 초과 요청은 400으로 거절(대량 쿼리로 인한 성능 저하 방지).
- **FR-5.6** (헤어진 뒤 SHARED 노출) **별도 규칙 불필요 — 접근 게이트(FR-3)에 흡수됨.** 커플 연결이 앱 사용의 전제이므로 `DISCONNECTED`가 되면 `CONNECTED` 커플이 없어 게이트가 막고, 상대방 SHARED 조회(FR-5.3) 쿼리 자체가 실행되지 않는다. "헤어진 뒤 SHARED가 보이나"는 게이트에 의해 자동으로 "안 보임"으로 귀결된다.

### FR-6 헤어짐 / 삭제
- **FR-6.1** 헤어짐은 단방향(한쪽 요청으로 성립). `CONNECTED` → `DISCONNECTED`, `disconnected_at`/`disconnected_by` 기록.
- **FR-6.2** 삭제 시점은 `delete_scheduled_at`으로 통합: 즉시=now, 또는 **1주일(7일) 유예**=now+7일.
- **FR-6.3** 스케줄러가 `status=DISCONNECTED AND delete_scheduled_at <= now`인 커플을 물리 삭제하고, 만료 PENDING(FR-2.7)도 정리한다. **(스케줄러 구현은 후순위 — 현재는 TODO. 만료/삭제 판정은 read 시점 lazy로 이미 처리되므로 기능 정확성은 스케줄러 없이도 성립하며, 스케줄러는 잔여 row의 물리 정리만 담당한다.)**
- **FR-6.4** 물리 삭제 시 커플 공동 일정(`COUPLE`)은 함께 삭제. 개인 일정(`PRIVATE`/`SHARED`)은 각자 소유라 보존.
- **FR-6.5** `DISCONNECTED` row가 존재하는 동안 두 유저 모두 (1) 앱 기능 사용 불가(게이트 차단), (2) 새 커플 연결 불가(FR-2.5, 커플 1개 제한). **물리 삭제(FR-6.3) 후에야** 재연결·재사용 가능. → `delete_scheduled_at`까지의 유예 기간 = 두 유저의 잠금 기간(즉시 삭제면 즉시 해제, 1주일 유예면 7일 잠금).
- **FR-6.6** (헤어짐 후 차단 시점 / 리스크 수용) 헤어지면 DB 상태는 즉시 `DISCONNECTED`가 되지만, 접근 게이트는 JWT `coupled` claim 기반이라 기존 토큰이 만료될 때까지 **최대 TTL(≤15분)의 잔여 접근 윈도우**가 존재한다(수용 가능한 리스크로 결정). 헤어짐 요청자는 토큰 재발급(FR-1.6)으로 즉시 반영되고, 데이터는 `delete_scheduled_at`까지 보존만 한다.
  - **상대방 강제 만료 미도입**: 상대방(피요청자)의 기존 토큰을 강제 만료시키는 기전(Redis 블랙리스트 등)은 **도입하지 않는다.** 상대방은 토큰 자연 만료(≤15분) 후 차단된다. → 짧은 TTL로 윈도우가 이미 충분히 작고, 블랙리스트 인프라는 사이드 프로젝트에 오버킬이라 판단(추후 실시간 차단이 필요해지면 재검토).

### FR-7 커플 프로필 / 색상
> 색상 모델 = **유저 개인색 + 공유 커플색**. (내 색만 내가 정하고, 상대 색은 상대가 정한 걸 그대로 본다.)
- **FR-7.1** 개인 색: 각 유저는 `users.personal_color`(HEX) 1개를 설정·변경한다. 내 정보(FR-1.8)에 포함되며, 미설정 시 서버 기본색을 적용한다.
- **FR-7.2** 커플 색: `CONNECTED` 커플은 공유 `couple_profile.couple_color`(HEX) 1개를 가진다. 두 유저 중 누구나 변경 가능하며, 변경은 **양쪽에 동일하게 반영**된다. 미설정 시 기본색.
- **FR-7.3** `couple_profile`은 커플 `CONNECTED` 전이 시 생성, 물리 삭제(FR-6.3) 시 커플과 함께 삭제.
- **FR-7.4** 색상 매핑(FR-5.4 캘린더): `MINE`→내 `personal_color`, `PARTNER`→파트너 `personal_color`, `COUPLE`→`couple_color`.
