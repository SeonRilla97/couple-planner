package com.seon.api.schedule.domain

/**
 * FR-7.4 색상 매핑: 유저 개인색 + 공유 커플색 모델.
 * 미설정(null)이면 서버 기본색을 적용한다(FR-7.1·7.2).
 */
object CalendarColor {
    const val DEFAULT_PERSONAL = "#9E9E9E"
    const val DEFAULT_COUPLE = "#FF8AAE"

    fun of(
        category: Category,
        myColor: String? = null,
        partnerColor: String? = null,
        coupleColor: String? = null,
    ): String = when (category) {
        Category.MINE -> myColor ?: DEFAULT_PERSONAL
        Category.PARTNER -> partnerColor ?: DEFAULT_PERSONAL
        Category.COUPLE -> coupleColor ?: DEFAULT_COUPLE
    }
}
