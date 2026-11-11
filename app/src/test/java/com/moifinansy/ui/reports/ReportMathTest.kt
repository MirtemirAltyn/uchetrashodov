package com.moifinansy.ui.reports

import com.moifinansy.domain.model.Category
import com.moifinansy.domain.model.CategoryKind
import com.moifinansy.domain.model.CategoryTotal
import com.moifinansy.domain.model.MonthTotals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

class ReportMathTest {
    private fun cat(id: Long, parent: Long? = null) = Category(id = id, name = "c$id", kind = CategoryKind.EXPENSE, parentId = parent, icon = "x", color = 0)

    @Test
    fun `subcategories roll up into parent and shares are computed`() {
        val cats = listOf(cat(1), cat(2, parent = 1), cat(3)).associateBy { it.id }
        val slices = rollUp(listOf(CategoryTotal(2, 300), CategoryTotal(1, 100), CategoryTotal(3, 600)), cats)
        assertEquals(listOf(3L to 600L, 1L to 400L), slices.map { it.category.id to it.total })
        assertEquals(listOf(60, 40), slices.map { it.percent })
        assertEquals(1_000L, slices.sumOf { it.total })
    }

    @Test
    fun `balance line goes backwards from current total`() {
        val sep = YearMonth.of(2026, 9)
        val oct = YearMonth.of(2026, 10)
        val nov = YearMonth.of(2026, 11)
        val totals = listOf(MonthTotals(oct, 1_000, 400, 600), MonthTotals(nov, 0, 100, -100))
        val points = monthPoints(listOf(sep, oct, nov), totals, totalNow = 5_000)
        assertEquals(listOf(4_500L, 5_100L, 5_000L), points.map { it.balanceEnd })
        assertEquals(listOf(0L, 1_000L, 0L), points.map { it.income })
    }
}
