package com.moifinansy.domain.budget

import com.moifinansy.domain.model.Budget
import com.moifinansy.domain.model.Category
import com.moifinansy.domain.model.CategoryTotal

/** Состояние бюджета за месяц. [category] null — лимит на все расходы. */
data class BudgetStatus(val budget: Budget, val category: Category?, val spent: Long) {
    val limit: Long get() = budget.limitAmount
    val left: Long get() = limit - spent
    val percent: Int get() = if (limit > 0) (spent * 100 / limit).toInt() else 0
    val level: Level get() = when {
        spent >= limit -> Level.EXCEEDED
        percent >= 80 -> Level.WARNING
        else -> Level.OK
    }

    enum class Level { OK, WARNING, EXCEEDED }

    companion object {
        /** Траты по бюджетам из расходов месяца по категориям (подкатегории входят в бюджет родителя). */
        fun compute(budgets: List<Budget>, rows: List<CategoryTotal>, categories: Map<Long, Category>): List<BudgetStatus> {
            val total = rows.sumOf { it.total }
            return budgets.map { b ->
                val spent = if (b.categoryId == null) {
                    total
                } else {
                    rows.filter { r -> r.categoryId == b.categoryId || categories[r.categoryId]?.parentId == b.categoryId }.sumOf { it.total }
                }
                BudgetStatus(b, b.categoryId?.let { categories[it] }, spent)
            }
        }
    }
}
