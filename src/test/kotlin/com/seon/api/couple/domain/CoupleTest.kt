package com.seon.api.couple.domain

import com.seon.api.fixture.CoupleFixture
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoupleTest {

    // 1.1 (T1, FR-2.3)
    @Test
    fun `connect transitions PENDING to CONNECTED and records connectedAt`() {
        val now = Instant.parse("2026-01-01T00:05:00Z")
        val couple = CoupleFixture.couple(status = CoupleStatus.PENDING)

        couple.connect(now)

        assertEquals(CoupleStatus.CONNECTED, couple.status)
        assertEquals(now, couple.connectedAt)
    }

    // 1.1 (T1, FR-2.3) — 이미 CONNECTED면 거절(불변식)
    @Test
    fun `connect on already CONNECTED is rejected`() {
        val couple = CoupleFixture.couple(
            status = CoupleStatus.CONNECTED,
            connectedAt = Instant.parse("2026-01-01T00:01:00Z"),
        )

        assertFailsWith<IllegalStateException> {
            couple.connect(Instant.parse("2026-01-01T00:05:00Z"))
        }
    }

    // 1.2 (T1, FR-2.2) — 자기 연결 금지
    @Test
    fun `requester and target must differ`() {
        assertFailsWith<IllegalArgumentException> {
            CoupleFixture.couple(requesterId = 10L, targetId = 10L)
        }
    }

    // 1.3 (T2, FR-2.7) — PENDING 만료 경계값: 미만=유효 / 정확히 10분·초과=만료
    @Test
    fun `PENDING expiry boundary at 10 minutes`() {
        val created = Instant.parse("2026-01-01T00:00:00Z")
        val pending = CoupleFixture.couple(status = CoupleStatus.PENDING, createdAt = created)

        val tenMin = Duration.ofMinutes(10)
        assertFalse(pending.isExpiredPending(created.plus(tenMin).minusMillis(1)), "10분 미만은 유효")
        assertTrue(pending.isExpiredPending(created.plus(tenMin)), "정확히 10분은 만료")
        assertTrue(pending.isExpiredPending(created.plus(Duration.ofMinutes(11))), "초과는 만료")
    }

    // 1.3 (T2, FR-2.7) — CONNECTED는 만료 개념 없음
    @Test
    fun `CONNECTED is never expired`() {
        val created = Instant.parse("2026-01-01T00:00:00Z")
        val connected = CoupleFixture.couple(
            status = CoupleStatus.CONNECTED,
            connectedAt = created,
            createdAt = created,
        )

        assertFalse(connected.isExpiredPending(created.plus(Duration.ofDays(365))))
    }
}
