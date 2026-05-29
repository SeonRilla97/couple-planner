package com.seon.api.schedule.domain

import java.time.Instant

enum class Visibility { PRIVATE, SHARED, COUPLE }

/** 캘린더 통합 조회 시 각 일정의 분류(FR-5.4). 막대 색은 이 category 기준(FR-7.4). */
enum class Category { MINE, COUPLE, PARTNER }

/**
 * 일정 도메인. 시각은 Instant(UTC 절대시각)로만 보관하고 비교하므로(FR-4.5)
 * 입력 오프셋이 달라도 UTC 기준으로 end>=start를 판정한다. 인프라는 모른다.
 */
class Schedule(
    val id: Long? = null,
    val ownerId: Long,
    val coupleId: Long? = null,
    val visibility: Visibility,
    val title: String,
    val content: String? = null,
    val startAt: Instant,
    val endAt: Instant,
    val createdAt: Instant,
) {
    init {
        // FR-4.1 end_at >= start_at (== 허용, UTC 절대시각 기준)
        require(!endAt.isBefore(startAt)) { "end_at must be >= start_at" }
        // FR-4.3 COUPLE 일정은 couple_id 필수
        if (visibility == Visibility.COUPLE) {
            requireNotNull(coupleId) { "COUPLE schedule requires couple_id" }
        }
    }

    /**
     * FR-5.4/5.3 캘린더 category 판정. 매칭되는 분류가 없으면(파트너 PRIVATE 등) null → 조회에서 제외.
     * - 내 PRIVATE/SHARED → MINE (내 COUPLE은 COUPLE로 분류해 중복 방지)
     * - COUPLE && 내 커플 → COUPLE
     * - 파트너 SHARED → PARTNER (파트너 PRIVATE는 제외)
     */
    fun categoryFor(viewer: CalendarViewer): Category? = when {
        ownerId == viewer.myId && visibility != Visibility.COUPLE -> Category.MINE
        visibility == Visibility.COUPLE && coupleId != null && coupleId == viewer.myCoupleId -> Category.COUPLE
        ownerId == viewer.partnerId && visibility == Visibility.SHARED -> Category.PARTNER
        else -> null
    }
}

/** 캘린더 조회 주체 컨텍스트. 신원은 토큰에서 도출한 값을 주입받는다(요청 본문 불신). */
data class CalendarViewer(
    val myId: Long,
    val partnerId: Long? = null,
    val myCoupleId: Long? = null,
)
