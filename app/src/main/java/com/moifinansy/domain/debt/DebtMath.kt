package com.moifinansy.domain.debt

import com.moifinansy.domain.model.DebtDirection
import com.moifinansy.domain.model.DebtPaymentKind
import com.moifinansy.domain.model.DebtStatus

/** Чистые правила долгов без Android и базы данных. */
object DebtMath {

    /**
     * Как движение по долгу меняет баланс счёта: > 0 — деньги пришли, < 0 — ушли, 0 — не меняет.
     * «Я должен»: занял/дозанял → +, вернул → −. «Мне должны»: дал/додал → −, мне вернули → +.
     * Прощение долга деньги не двигает.
     */
    fun accountEffect(direction: DebtDirection, kind: DebtPaymentKind, amount: Long): Long {
        val incoming = when (kind) {
            DebtPaymentKind.INITIAL, DebtPaymentKind.INCREASE -> direction == DebtDirection.I_OWE
            DebtPaymentKind.REPAY -> direction == DebtDirection.OWED_TO_ME
            DebtPaymentKind.FORGIVE -> return 0
        }
        return if (incoming) amount else -amount
    }

    /** Как движение меняет остаток долга. */
    fun remainingDelta(kind: DebtPaymentKind, amount: Long): Long = when (kind) {
        DebtPaymentKind.INITIAL, DebtPaymentKind.INCREASE -> amount
        DebtPaymentKind.REPAY, DebtPaymentKind.FORGIVE -> -amount
    }

    /** Статус после движения: остаток 0 → закрыт (или списан, если закрыли прощением). */
    fun statusAfter(remaining: Long, lastKind: DebtPaymentKind): DebtStatus = when {
        remaining > 0 -> DebtStatus.ACTIVE
        lastKind == DebtPaymentKind.FORGIVE -> DebtStatus.FORGIVEN
        else -> DebtStatus.CLOSED
    }

    fun isOverdue(status: DebtStatus, dueDate: Long?, now: Long): Boolean =
        status == DebtStatus.ACTIVE && dueDate != null && dueDate < now
}
