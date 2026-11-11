package com.moifinansy.ui.navigation

import com.moifinansy.domain.model.CategoryKind

/** Маршруты внутренних экранов (без нижней панели). Отсутствующий id передаётся как -1. */
object Routes {
    const val ACCOUNTS = "accounts"
    const val ACCOUNT = "account/{id}"
    const val ACCOUNT_EDIT = "account_edit?id={id}"
    const val CATEGORIES = "categories"
    const val SETTINGS = "settings"
    const val OPERATION_EDIT = "operation_edit?id={id}&type={type}&copyFrom={copyFrom}"
    const val DEBT = "debt/{id}"
    const val DEBT_EDIT = "debt_edit?contactId={contactId}"
    const val CONTACT = "contact/{id}"
    const val CATEGORY_REPORT = "report_category/{id}?kind={kind}&period={period}&anchor={anchor}&from={from}&to={to}"
    fun categoryReport(id: Long, kind: com.moifinansy.domain.model.CategoryKind, p: com.moifinansy.domain.model.ReportPeriod) =
        "report_category/$id?kind=${kind.name}&period=${p.kind.name}&anchor=${p.anchor.toEpochDay()}" +
            "&from=${p.customFrom?.toEpochDay() ?: Long.MIN_VALUE}&to=${p.customTo?.toEpochDay() ?: Long.MIN_VALUE}"
    fun contact(id: Long) = "contact/$id"
    fun debtEdit(contactId: Long? = null) = "debt_edit?contactId=${contactId ?: NONE}"
    const val CATEGORY_EDIT = "category_edit?id={id}&kind={kind}&parentId={parentId}"

    const val NONE = -1L

    fun account(id: Long) = "account/$id"
    fun debt(id: Long) = "debt/$id"
    fun operationEdit(id: Long? = null, type: com.moifinansy.domain.model.TransactionType = com.moifinansy.domain.model.TransactionType.EXPENSE, copyFrom: Long? = null) =
        "operation_edit?id=${id ?: NONE}&type=${type.name}&copyFrom=${copyFrom ?: NONE}"
    fun accountEdit(id: Long? = null) = "account_edit?id=${id ?: NONE}"
    fun categoryEdit(id: Long? = null, kind: CategoryKind = CategoryKind.EXPENSE, parentId: Long? = null) =
        "category_edit?id=${id ?: NONE}&kind=${kind.name}&parentId=${parentId ?: NONE}"
}
