package net.enthusia.itemsignature.application

import net.enthusia.itemsignature.domain.ItemFacts
import net.enthusia.itemsignature.domain.MutationRejection

/** Framework-free rules used by the actual command and tracking adapters. */
object CustomizationPolicy {
    fun rejection(facts: ItemFacts): MutationRejection? = when {
        facts.isDiary -> MutationRejection.DIARY
        facts.amount != 1 -> MutationRejection.STACK
        else -> null
    }

    fun nextCounter(value: Long, amount: Long = 1): Long {
        require(amount >= 0)
        return if (value > Long.MAX_VALUE - amount) Long.MAX_VALUE else value + amount
    }
}
