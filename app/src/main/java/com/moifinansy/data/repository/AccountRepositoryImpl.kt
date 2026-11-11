package com.moifinansy.data.repository

import com.moifinansy.data.local.dao.AccountDao
import com.moifinansy.domain.model.Account
import com.moifinansy.domain.model.AccountWithBalance
import com.moifinansy.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject

class AccountRepositoryImpl @Inject constructor(
    private val dao: AccountDao,
    private val clock: Clock,
) : AccountRepository {

    override fun observeAccounts(includeArchived: Boolean): Flow<List<AccountWithBalance>> =
        dao.observeWithBalance(includeArchived).map { rows -> rows.map { it.toModel() } }

    override fun observeTotal(currencyCode: String): Flow<Long> = dao.observeTotal(currencyCode)

    override suspend fun get(id: Long): Account? = dao.getById(id)?.toModel()

    override suspend fun balanceOf(accountId: Long): Long =
        requireNotNull(dao.balanceOf(accountId)) { "Счёт $accountId не найден" }

    override suspend fun create(account: Account): Long {
        validate(account)
        require(account.id == 0L) { "Новый счёт не должен иметь id" }
        val now = clock.millis()
        return dao.insert(account.toEntity(now, now))
    }

    override suspend fun update(account: Account) {
        validate(account)
        val old = requireNotNull(dao.getById(account.id)) { "Счёт ${account.id} не найден" }
        dao.update(account.toEntity(old.createdAt, clock.millis()))
    }

    override suspend fun setArchived(id: Long, archived: Boolean) = dao.setArchived(id, archived, clock.millis())

    private fun validate(account: Account) {
        require(account.name.isNotBlank()) { "Введите название счёта" }
        require(account.currencyCode.length == 3) { "Код валюты — три буквы" }
    }
}
