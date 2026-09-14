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

    fun nextCounter(value: Long): Long = if (value == Long.MAX_VALUE) value else value + 1
}
