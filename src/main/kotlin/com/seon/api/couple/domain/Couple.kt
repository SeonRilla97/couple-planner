package com.seon.api.couple.domain

import java.time.Duration
import java.time.Instant

enum class CoupleStatus { PENDING, CONNECTED }

/**
 * 커플 연결 도메인. 인프라(JPA/DB)를 모르는 순수 객체로 둔다.
 * 상태 전이·불변식·만료 판정만 담당하며 Instant(UTC)를 외부에서 주입받는다(시계 비의존).
 */
class Couple(
    val id: Long? = null,
    val requesterId: Long,
    val targetId: Long,
    status: CoupleStatus = CoupleStatus.PENDING,
    connectedAt: Instant? = null,
    val createdAt: Instant,
) {
    var status: CoupleStatus = status
        private set
    var connectedAt: Instant? = connectedAt
        private set

    init {
        // FR-2.2 자기 자신과는 연결 불가
        require(requesterId != targetId) { "requester_id and target_id must differ" }
    }

    /** FR-2.3 상호 확인 시 PENDING→CONNECTED 전이. 이미 CONNECTED면 거절(불변식). */
    fun connect(now: Instant) {
        check(status == CoupleStatus.PENDING) { "already connected" }
        status = CoupleStatus.CONNECTED
        connectedAt = now
    }

    /**
     * FR-2.7 PENDING 만료 판정(lazy). created_at+10분 미만은 유효, 정확히 10분·초과는 만료.
     * CONNECTED는 만료 개념이 없다.
     */
    fun isExpiredPending(now: Instant): Boolean =
        status == CoupleStatus.PENDING && !now.isBefore(createdAt.plus(PENDING_TTL))

    companion object {
        val PENDING_TTL: Duration = Duration.ofMinutes(10)
    }
}
