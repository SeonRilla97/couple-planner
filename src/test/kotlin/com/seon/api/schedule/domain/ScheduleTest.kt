package com.seon.api.schedule.domain

import com.seon.api.fixture.ScheduleFixture
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ScheduleTest {

    // 1.5 (T4, FR-4.1) — end == start 허용(경계)
    @Test
    fun `end equal to start is allowed`() {
        val t = Instant.parse("2026-01-01T10:00:00Z")
        ScheduleFixture.schedule(startAt = t, endAt = t) // 예외 없으면 통과
    }

    // 1.5 (T4, FR-4.1) — end < start 거절
    @Test
    fun `end before start is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            ScheduleFixture.schedule(
                startAt = Instant.parse("2026-01-01T10:00:00Z"),
                endAt = Instant.parse("2026-01-01T09:59:59Z"),
            )
        }
    }

    // 1.5 (T4, FR-4.5) — 서로 다른 오프셋이라도 UTC 절대시각으로 판정
    @Test
    fun `different offsets are judged by UTC absolute time`() {
        // 벽시계로는 end(03:00) < start(10:00)처럼 보이지만 UTC로는 start=01:00Z < end=03:00Z → 허용
        val start = OffsetDateTime.parse("2026-01-01T10:00:00+09:00").toInstant() // 01:00Z
        val end = OffsetDateTime.parse("2026-01-01T03:00:00+00:00").toInstant()   // 03:00Z
        ScheduleFixture.schedule(startAt = start, endAt = end) // 예외 없으면 통과

        // 반대로 UTC상 end < start면 거절: start=10:00Z, end=09:00Z(+09:00의 18:00)
        assertFailsWith<IllegalArgumentException> {
            ScheduleFixture.schedule(
                startAt = OffsetDateTime.parse("2026-01-01T10:00:00+00:00").toInstant(), // 10:00Z
                endAt = OffsetDateTime.parse("2026-01-01T18:00:00+09:00").toInstant(),    // 09:00Z
            )
        }
    }

    // 1.5 (T4, FR-4.3) — COUPLE이면 couple_id 필수
    @Test
    fun `COUPLE schedule requires couple_id`() {
        assertFailsWith<IllegalArgumentException> {
            ScheduleFixture.schedule(visibility = Visibility.COUPLE, coupleId = null)
        }
        ScheduleFixture.schedule(visibility = Visibility.COUPLE, coupleId = 100L) // 있으면 통과
    }

    // 1.6 (T5, FR-5.4·5.3) — category 판정 + 파트너 PRIVATE 제외
    @Test
    fun `category determination and partner PRIVATE exclusion`() {
        val viewer = CalendarViewer(myId = 10L, partnerId = 20L, myCoupleId = 100L)

        // 내 일정(PRIVATE/SHARED) → MINE
        assertEquals(
            Category.MINE,
            ScheduleFixture.schedule(ownerId = 10L, visibility = Visibility.PRIVATE).categoryFor(viewer),
        )
        assertEquals(
            Category.MINE,
            ScheduleFixture.schedule(ownerId = 10L, visibility = Visibility.SHARED).categoryFor(viewer),
        )

        // 내 커플의 COUPLE 일정 → COUPLE (소유자 무관)
        assertEquals(
            Category.COUPLE,
            ScheduleFixture.schedule(ownerId = 10L, visibility = Visibility.COUPLE, coupleId = 100L).categoryFor(viewer),
        )
        assertEquals(
            Category.COUPLE,
            ScheduleFixture.schedule(ownerId = 20L, visibility = Visibility.COUPLE, coupleId = 100L).categoryFor(viewer),
        )

        // 파트너 SHARED → PARTNER
        assertEquals(
            Category.PARTNER,
            ScheduleFixture.schedule(ownerId = 20L, visibility = Visibility.SHARED).categoryFor(viewer),
        )

        // 파트너 PRIVATE → 제외(null)
        assertNull(
            ScheduleFixture.schedule(ownerId = 20L, visibility = Visibility.PRIVATE).categoryFor(viewer),
        )

        // 다른 커플의 COUPLE 일정 → 제외(null)
        assertNull(
            ScheduleFixture.schedule(ownerId = 30L, visibility = Visibility.COUPLE, coupleId = 999L).categoryFor(viewer),
        )
    }

    // 1.4 (T3, FR-7.4·7.1·7.2) — 색상 매핑 + 미설정 시 기본색
    @Test
    fun `color mapping by category with defaults`() {
        assertEquals("#111111", CalendarColor.of(Category.MINE, myColor = "#111111"))
        assertEquals("#222222", CalendarColor.of(Category.PARTNER, partnerColor = "#222222"))
        assertEquals("#333333", CalendarColor.of(Category.COUPLE, coupleColor = "#333333"))

        // 미설정 → 서버 기본색
        assertEquals(CalendarColor.DEFAULT_PERSONAL, CalendarColor.of(Category.MINE))
        assertEquals(CalendarColor.DEFAULT_PERSONAL, CalendarColor.of(Category.PARTNER))
        assertEquals(CalendarColor.DEFAULT_COUPLE, CalendarColor.of(Category.COUPLE))
    }
}
