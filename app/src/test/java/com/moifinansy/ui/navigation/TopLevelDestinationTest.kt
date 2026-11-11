package com.moifinansy.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TopLevelDestinationTest {

    @Test
    fun `five tabs in spec order`() {
        assertEquals(
            listOf("home", "operations", "debts", "reports", "more"),
            TopLevelDestination.entries.map { it.route },
        )
    }

    @Test
    fun `fab only on home, operations and debts`() {
        assertEquals(
            setOf(TopLevelDestination.HOME, TopLevelDestination.OPERATIONS, TopLevelDestination.DEBTS),
            TopLevelDestination.entries.filter { it.hasFab }.toSet(),
        )
    }

    @Test
    fun `fromRoute resolves known routes and ignores unknown`() {
        assertEquals(TopLevelDestination.DEBTS, TopLevelDestination.fromRoute("debts"))
        assertNull(TopLevelDestination.fromRoute("nope"))
        assertNull(TopLevelDestination.fromRoute(null))
    }
}
