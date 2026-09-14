package net.enthusia.itemsignature.domain

data class ItemFacts(val isDiary: Boolean, val amount: Int)
enum class MutationRejection { DIARY, STACK }
