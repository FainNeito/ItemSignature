package net.enthusia.itemsignature.domain

data class ItemFacts(val isDiary: Boolean, val amount: Int, val isLocked: Boolean = false)
enum class MutationRejection { DIARY, LOCKED, STACK }
