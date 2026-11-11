package com.moifinansy.data.local

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.moifinansy.domain.model.CategoryKind
import com.moifinansy.domain.model.SystemCategory

/** Предустановленные категории из SPEC.md (раздел 3.3). Иконки — имена Material Symbols Rounded. */
object DefaultCategories {

    data class Seed(
        val name: String,
        val icon: String,
        val color: Long,
        val systemKey: String? = null,
        val children: List<Seed> = emptyList(),
    )

    val expenses = listOf(
        Seed("Продукты", "shopping_cart", 0xFF4CAF50),
        Seed("Кафе и рестораны", "restaurant", 0xFFFF7043),
        Seed(
            "Транспорт", "directions_bus", 0xFF42A5F5,
            children = listOf(
                Seed("Такси", "local_taxi", 0xFF42A5F5),
                Seed("Общественный транспорт", "directions_bus", 0xFF42A5F5),
            ),
        ),
        Seed("Авто", "directions_car", 0xFF5C6BC0),
        Seed("Жильё и коммуналка", "home", 0xFF8D6E63),
        Seed("Связь и интернет", "wifi", 0xFF26C6DA),
        Seed("Здоровье", "medical_services", 0xFFEF5350),
        Seed("Одежда", "checkroom", 0xFFAB47BC),
        Seed("Дом", "chair", 0xFFA1887F),
        Seed("Дети", "child_care", 0xFFFFCA28),
        Seed("Образование", "school", 0xFF7E57C2),
        Seed("Развлечения", "sports_esports", 0xFFEC407A),
        Seed("Подписки", "subscriptions", 0xFF29B6F6),
        Seed("Подарки", "redeem", 0xFFF06292),
        Seed("Путешествия", "flight", 0xFF26A69A),
        Seed("Комиссии", "percent", 0xFF78909C, systemKey = SystemCategory.COMMISSIONS),
        Seed("Прочее", "more_horiz", 0xFF9E9E9E),
    )

    val incomes = listOf(
        Seed("Зарплата", "payments", 0xFF1F9D6B),
        Seed("Бизнес", "storefront", 0xFF43A047),
        Seed("Подработка", "work", 0xFF7CB342),
        Seed("Подарки", "redeem", 0xFFF06292),
        Seed("Проценты", "savings", 0xFF26A69A),
        Seed("Прочее", "more_horiz", 0xFF9E9E9E),
    )

    /** Заполняет пустую базу при первом создании. */
    fun callback(clock: () -> Long): RoomDatabase.Callback = object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            seed(db, clock())
        }
    }

    fun seed(db: SupportSQLiteDatabase, now: Long) {
        insertTree(db, expenses, CategoryKind.EXPENSE, parentId = null, now)
        insertTree(db, incomes, CategoryKind.INCOME, parentId = null, now)
    }

    private fun insertTree(db: SupportSQLiteDatabase, seeds: List<Seed>, kind: CategoryKind, parentId: Long?, now: Long) {
        seeds.forEachIndexed { index, seed ->
            val values = ContentValues().apply {
                put("name", seed.name)
                put("kind", kind.name)
                if (parentId == null) putNull("parentId") else put("parentId", parentId)
                put("icon", seed.icon)
                put("color", seed.color)
                put("sortOrder", index)
                put("isArchived", 0)
                if (seed.systemKey == null) putNull("systemKey") else put("systemKey", seed.systemKey)
                put("createdAt", now)
                put("updatedAt", now)
            }
            val id = db.insert("categories", SQLiteDatabase.CONFLICT_ABORT, values)
            if (seed.children.isNotEmpty()) insertTree(db, seed.children, kind, id, now)
        }
    }
}
