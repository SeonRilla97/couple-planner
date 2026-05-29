package com.seon.api.user.domain

/**
 * FR-1.10 비밀번호 정책: 9자 이상 + 영문자·숫자·특수문자 3종 모두 포함.
 * @Valid 한 줄이 아닌 조합 규칙이라 깨지기 쉬워 순수 단위로 둔다(test-cases T6).
 */
object PasswordPolicy {
    const val MIN_LENGTH = 9

    fun isValid(password: String): Boolean {
        if (password.length < MIN_LENGTH) return false
        val hasLetter = password.any { it in 'a'..'z' || it in 'A'..'Z' }
        val hasDigit = password.any { it in '0'..'9' }
        val hasSpecial = password.any { !it.isLetterOrDigit() && !it.isWhitespace() }
        return hasLetter && hasDigit && hasSpecial
    }
}
