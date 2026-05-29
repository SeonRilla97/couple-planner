package com.seon.api.user.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PasswordPolicyTest {

    // 1.7 (T6, FR-1.10) — 9자 이상 + 3종 모두 포함해야 통과
    @Test
    fun `valid password has 9+ chars with letter digit and special`() {
        assertTrue(PasswordPolicy.isValid("abcdefg1!")) // 9자, 영문+숫자+특수
        assertTrue(PasswordPolicy.isValid("Passw0rd!@#"))
    }

    // 1.7 (T6, FR-1.10) — 하나라도 빠지면 거절
    @Test
    fun `invalid when any rule is missing`() {
        assertFalse(PasswordPolicy.isValid("abc1!de"), "8자 — 길이 미달")
        assertFalse(PasswordPolicy.isValid("abcdefg12"), "특수문자 없음")
        assertFalse(PasswordPolicy.isValid("abcdefg!@"), "숫자 없음")
        assertFalse(PasswordPolicy.isValid("12345678!"), "영문자 없음")
    }
}
