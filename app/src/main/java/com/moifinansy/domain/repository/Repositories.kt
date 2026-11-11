package com.moifinansy.domain.repository

import com.moifinansy.domain.model.Account
import com.moifinansy.domain.model.AccountWithBalance
import com.moifinansy.domain.model.Budget
import com.moifinansy.domain.model.Category
import com.moifinansy.domain.model.CategoryKind
import com.moifinansy.domain.model.CategoryTotal
import com.moifinansy.domain.model.Contact
import com.moifinansy.domain.model.ContactBalance
import com.moifinansy.domain.model.CurrencyRate
import com.moifinansy.domain.model.Debt
import com.moifinansy.domain.model.DebtPayment
import com.moifinansy.domain.model.DebtPaymentKind
import com.moifinansy.domain.model.DebtTotals
import com.moifinansy.domain.model.DebtWithRemaining
import com.moifinansy.domain.model.RecurringRule
import com.moifinansy.domain.model.Tag
import com.moifinansy.domain.model.Transaction
import kotlinx.coroutines.flow.Flow

// Периоды везде полуоткрытые: from включительно, to — нет.

interface AccountRepository {
    fun observeAccounts(includeArchived: Boolean = false): Flow<List<AccountWithBalance>>
    /** Общий баланс счетов с «учитывать в общем балансе» в одной валюте. */
    fun observeTotal(currencyCode: String): Flow<Long>
    suspend fun get(id: Long): Account?
    suspend fun balanceOf(accountId: Long): Long
    suspend fun create(account: Account): Long
    suspend fun update(account: Account)
    suspend fun setArchived(id: Long, archived: Boolean)
    /** Сколько операций, движений долгов и регулярных платежей ссылается на счёт. */
    suspend fun usageCount(id: Long): Int
    /** Удаляет счёт без операций. Счёт с операциями можно только архивировать. */
    suspend fun delete(id: Long)
}

interface CategoryRepository {
    fun observe(kind: CategoryKind, includeArchived: Boolean = false): Flow<List<Category>>
    suspend fun get(id: Long): Category?
    suspend fun create(category: Category): Long
    suspend fun update(category: Category)
    suspend fun setArchived(id: Long, archived: Boolean)
    /** Удаляет категорию, перенося её операции и регулярные платежи в [moveToId]. */
    suspend fun delete(id: Long, moveToId: Long?)
    /** Порядок соседних категорий (один уровень, один тип). */
    suspend fun reorder(ids: List<Long>)
    /** Сколько операций и регулярных платежей в категории. */
    suspend fun usageCount(id: Long): Int
    suspend fun childrenCount(id: Long): Int
}

interface TransactionRepository {
    fun observeRange(from: Long, to: Long): Flow<List<Transaction>>
    /** Операции, где счёт — источник или получатель, новые сверху. */
    fun observeForAccount(accountId: Long): Flow<List<Transaction>>
    fun observeFiltered(filter: com.moifinansy.domain.model.OperationFilter): Flow<List<Transaction>>
    fun observeLatest(limit: Int): Flow<List<Transaction>>
    fun observeTotalExpense(from: Long, to: Long): Flow<Long>
    fun observeTotalIncome(from: Long, to: Long): Flow<Long>
    /** Расходы по категориям (по самим категориям операций, без свёртки в родителя). */
    fun observeExpenseByCategory(from: Long, to: Long): Flow<List<CategoryTotal>>
    fun observeIncomeByCategory(from: Long, to: Long): Flow<List<CategoryTotal>>
    fun observeCategoryMonths(categoryId: Long, from: Long, to: Long): Flow<Map<java.time.YearMonth, Long>>
    fun observeMonthTotals(from: Long, to: Long): Flow<List<com.moifinansy.domain.model.MonthTotals>>
    suspend fun get(id: Long): Transaction?
    /** Создаёт (id == 0) или обновляет операцию вместе с тегами. Возвращает id. */
    suspend fun save(transaction: Transaction, tagIds: List<Long> = emptyList()): Long
    suspend fun delete(id: Long)
    /** Корректировка: создаёт ADJUSTMENT на разницу между фактическим и расчётным балансом. null — разницы нет. */
    suspend fun adjustBalance(accountId: Long, actualBalance: Long, date: Long): Long?
    suspend fun tagIdsOf(transactionId: Long): List<Long>

    suspend fun totalExpense(from: Long, to: Long): Long
    suspend fun totalIncome(from: Long, to: Long): Long
    suspend fun expenseByCategory(from: Long, to: Long): List<CategoryTotal>
    suspend fun incomeByCategory(from: Long, to: Long): List<CategoryTotal>
}

interface DebtRepository {
    fun observeContacts(): Flow<List<Contact>>
    suspend fun createContact(contact: Contact): Long
    suspend fun updateContact(contact: Contact)

    fun observeDebts(): Flow<List<DebtWithRemaining>>
    fun observePayments(debtId: Long): Flow<List<DebtPayment>>
    fun observeTotals(currencyCode: String): Flow<DebtTotals>
    fun observeContactBalances(): Flow<List<ContactBalance>>
    suspend fun get(id: Long): DebtWithRemaining?
    fun observe(id: Long): Flow<DebtWithRemaining?>
    suspend fun getContact(id: Long): Contact?
    fun observeContact(id: Long): Flow<Contact?>
    /** Удаляет долг вместе с движениями и связанными операциями по счетам. */
    suspend fun deleteDebt(id: Long)
    /** Удаляет контакт, если у него нет долгов. true — удалён. */
    suspend fun deleteContact(id: Long): Boolean

    /** Создаёт долг и первое движение INITIAL. [accountId] обязателен, если долг влияет на счета. */
    suspend fun createDebt(debt: Debt, accountId: Long?): Long
    /** Погашение, дозайм или прощение. Остаток и статус пересчитываются в той же транзакции БД. */
    suspend fun addPayment(debtId: Long, kind: DebtPaymentKind, amount: Long, accountId: Long?, date: Long, note: String? = null): Long
}

interface TagRepository {
    fun observeAll(): Flow<List<Tag>>
    suspend fun create(name: String): Long
}

interface BudgetRepository {
    fun observeAll(): Flow<List<Budget>>
    suspend fun save(budget: Budget): Long
    suspend fun delete(id: Long)
}

interface RecurringRepository {
    fun observeAll(): Flow<List<RecurringRule>>
    suspend fun dueBy(date: Long): List<RecurringRule>
    suspend fun save(rule: RecurringRule): Long
    suspend fun delete(id: Long)
}

interface CurrencyRateRepository {
    fun observeAll(): Flow<List<CurrencyRate>>
    suspend fun save(rate: CurrencyRate)
}
