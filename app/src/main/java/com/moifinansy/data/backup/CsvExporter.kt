package com.moifinansy.data.backup

import com.moifinansy.domain.model.Account
import com.moifinansy.domain.model.Category
import com.moifinansy.domain.model.Transaction
import com.moifinansy.domain.model.TransactionType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** CSV операций: «;» как разделитель (Excel в русской локали), UTF-8 с BOM, суммы с запятой. */
object CsvExporter {
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val header = listOf("Дата", "Тип", "Сумма", "Валюта", "Счёт", "На счёт", "Сумма зачисления", "Комиссия", "Категория", "Подкатегория", "Комментарий", "Теги")

    fun build(
        transactions: List<Transaction>,
        accounts: Map<Long, Account>,
        categories: Map<Long, Category>,
        tagsByTransaction: Map<Long, List<String>>,
        zone: ZoneId,
    ): String {
        val sb = StringBuilder("﻿")
        sb.append(header.joinToString(";") { escape(it) }).append("\r\n")
        transactions.sortedBy { it.date }.forEach { t ->
            val account = accounts[t.accountId]
            val category = t.categoryId?.let { categories[it] }
            val parent = category?.parentId?.let { categories[it] }
            val row = listOf(
                Instant.ofEpochMilli(t.date).atZone(zone).format(dateFormat),
                typeName(t.type),
                money(t.amount),
                account?.currencyCode.orEmpty(),
                account?.name.orEmpty(),
                t.toAccountId?.let { accounts[it]?.name }.orEmpty(),
                t.toAmount?.let(::money).orEmpty(),
                if (t.fee != 0L) money(t.fee) else "",
                (parent ?: category)?.name.orEmpty(),
                if (parent != null) category?.name.orEmpty() else "",
                t.note.orEmpty(),
                tagsByTransaction[t.id].orEmpty().joinToString(", "),
            )
            sb.append(row.joinToString(";") { escape(it) }).append("\r\n")
        }
        return sb.toString()
    }

    private fun typeName(type: TransactionType) = when (type) {
        TransactionType.EXPENSE -> "Расход"
        TransactionType.INCOME -> "Доход"
        TransactionType.TRANSFER -> "Перевод"
        TransactionType.ADJUSTMENT -> "Корректировка"
        TransactionType.DEBT -> "Долг"
    }

    /** −12 500,50 без разделителя тысяч, чтобы Excel понял как число. */
    private fun money(minor: Long): String {
        val sign = if (minor < 0) "-" else ""
        val abs = kotlin.math.abs(minor)
        return "$sign${abs / 100},${(abs % 100).toString().padStart(2, '0')}"
    }

    private fun escape(value: String): String =
        if (value.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
}
