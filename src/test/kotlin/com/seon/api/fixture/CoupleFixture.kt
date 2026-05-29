package com.seon.api.fixture

import com.seon.api.couple.domain.Couple
import com.seon.api.couple.domain.CoupleStatus
import java.time.Instant

/**
 * 커플 도메인 fixture. 모든 인자에 기본값을 주고, 테스트는 관심 필드만 덮어쓴다.
 * (test.md §8 — given 비용을 낮춰 테스트가 굴러가게)
 */
object CoupleFixture {
    val CREATED_AT: Instant = Instant.parse("2026-01-01T00:00:00Z")

    fun couple(
        id: Long? = 1L,
        requesterId: Long = 10L,
        targetId: Long = 20L,
        status: CoupleStatus = CoupleStatus.PENDING,
        connectedAt: Instant? = null,
        createdAt: Instant = CREATED_AT,
    ): Couple = Couple(id, requesterId, targetId, status, connectedAt, createdAt)
}
