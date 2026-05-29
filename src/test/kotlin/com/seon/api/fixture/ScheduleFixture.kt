package com.seon.api.fixture

import com.seon.api.schedule.domain.Schedule
import com.seon.api.schedule.domain.Visibility
import java.time.Instant

/** 일정 도메인 fixture. 관심 필드만 덮어쓴다. */
object ScheduleFixture {
    val START: Instant = Instant.parse("2026-01-01T10:00:00Z")
    val END: Instant = Instant.parse("2026-01-01T11:00:00Z")
    val CREATED_AT: Instant = Instant.parse("2026-01-01T00:00:00Z")

    fun schedule(
        id: Long? = 1L,
        ownerId: Long = 10L,
        coupleId: Long? = null,
        visibility: Visibility = Visibility.PRIVATE,
        title: String = "title",
        content: String? = null,
        startAt: Instant = START,
        endAt: Instant = END,
        createdAt: Instant = CREATED_AT,
    ): Schedule = Schedule(id, ownerId, coupleId, visibility, title, content, startAt, endAt, createdAt)
}
