package com.moifinansy.domain.model

/** Фильтр списка операций. Период полуоткрытый [from, to); null — без ограничения. */
data class OperationFilter(
    val from: Long,
    val to: Long,
    val type: TransactionType? = null,
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val tagId: Long? = null,
    val minAmount: Long? = null,
    val maxAmount: Long? = null,
)
