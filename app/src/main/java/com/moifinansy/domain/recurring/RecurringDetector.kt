package com.moifinansy.domain.recurring

import com.moifinansy.domain.model.RecurringRule
import com.moifinansy.domain.model.Transaction
import com.moifinansy.domain.model.TransactionType
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** Похоже на регулярный платёж: одна и та же категория, счёт и сумма каждый месяц примерно в одно число. */
data class RecurringSuggestion(
    val key: String,
    val type: TransactionType,
    val categoryId: Long,
    val accountId: Long,
    val amount: Long,
    val dayOfMonth: Int,
    val months: Int,
    val nextDate: Long,
)

object RecurringDetector {
    const val MIN_MONTHS = 3
    private const val MAX_DAY_SPREAD = 5
    private const val MAX_SINCE_LAST_DAYS = 45L

    fun key(type: TransactionType, categoryId: Long, accountId: Long, amount: Long) = "$type-$categoryId-$accountId-$amount"

    fun detect(
        transactions: List<Transaction>,
        rules: List<RecurringRule>,
        dismissed: Set<String>,
        now: Long,
        zone: ZoneId,
    ): List<RecurringSuggestion> {
        val existing = rules.map { key(it.type, it.categoryId ?: -1, it.accountId, it.amount) }.toSet()
        return transactions
            .filter { (it.type == TransactionType.EXPENSE || it.type == TransactionType.INCOME) && it.recurringId == null && it.categoryId != null }
            .groupBy { key(it.type, it.categoryId!!, it.accountId, it.amount) }
            .filterKeys { it !in existing && it !in dismissed }
            .mapNotNull { (key, list) -> suggestion(key, list, now, zone) }
            .sortedByDescending { it.amount }
    }

    private fun suggestion(key: String, list: List<Transaction>, now: Long, zone: ZoneId): RecurringSuggestion? {
        // По одной операции на месяц (самой ранней), затем — непрерывная цепочка месяцев до последнего.
        val byMonth = list.sortedBy { it.date }
            .groupBy { YearMonth.from(Instant.ofEpochMilli(it.date).atZone(zone)) }
            .mapValues { it.value.first() }
        val last = byMonth.keys.maxOrNull() ?: return null
        val chain = generateSequence(last) { it.minusMonths(1) }.takeWhile { it in byMonth }.toList()
        if (chain.size < MIN_MONTHS) return null
        val picked = chain.map { byMonth.getValue(it) }
        // Числа месяца близки (31-е и 28-е в феврале считаются одним «концом месяца»).
        val days = picked.map { minOf(Instant.ofEpochMilli(it.date).atZone(zone).dayOfMonth, 28) }
        if (days.max() - days.min() > MAX_DAY_SPREAD) return null
        val latest = picked.first()
        if (now - latest.date > MAX_SINCE_LAST_DAYS * 86_400_000L) return null
        val latestDate = Instant.ofEpochMilli(latest.date).atZone(zone)
        return RecurringSuggestion(
            key = key,
            type = latest.type,
            categoryId = latest.categoryId!!,
            accountId = latest.accountId,
            amount = latest.amount,
            dayOfMonth = latestDate.dayOfMonth,
            months = chain.size,
            nextDate = latestDate.plusMonths(1).toInstant().toEpochMilli(),
        )
    }
}
