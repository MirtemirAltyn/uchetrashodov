package com.moifinansy.ui.navigation

import com.moifinansy.domain.model.CategoryKind

/** Маршруты внутренних экранов (без нижней панели). Отсутствующий id передаётся как -1. */
object Routes {
    const val ACCOUNTS = "accounts"
    const val ACCOUNT = "account/{id}"
    const val ACCOUNT_EDIT = "account_edit?id={id}"
    const val CATEGORIES = "categories"
    const val SETTINGS = "settings"
    const val CATEGORY_EDIT = "category_edit?id={id}&kind={kind}&parentId={parentId}"

    const val NONE = -1L

    fun account(id: Long) = "account/$id"
    fun accountEdit(id: Long? = null) = "account_edit?id=${id ?: NONE}"
    fun categoryEdit(id: Long? = null, kind: CategoryKind = CategoryKind.EXPENSE, parentId: Long? = null) =
        "category_edit?id=${id ?: NONE}&kind=${kind.name}&parentId=${parentId ?: NONE}"
}
