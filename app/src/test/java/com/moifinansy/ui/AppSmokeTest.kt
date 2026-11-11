package com.moifinansy.ui

import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.moifinansy.MainActivity
import com.moifinansy.core.format.MoneyFormat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Сквозная проверка настоящего приложения на Robolectric: онбординг → счета → карточка счёта → категории.
 * С `-Pscreenshots` сохраняет снимки экранов в app/build/screenshots/.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class AppSmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val dir = System.getProperty("screenshots.dir") ?: "build/screenshots"

    private fun shot(name: String) = compose.onRoot().captureRoboImage("$dir/$name.png")

    private fun waitForText(text: String, substring: Boolean = false) {
        try {
            compose.waitUntilAtLeastOneExists(hasText(text, substring = substring), timeoutMillis = 10_000)
        } catch (e: ComposeTimeoutException) {
            throw AssertionError("На экране нет текста «$text»", e)
        }
    }

    /** Ввод в поле с подписью [label]; ждёт, пока экран загрузит поля. */
    private fun input(label: String, text: String) {
        val field = hasSetTextAction() and hasText(label, substring = true)
        compose.waitUntilAtLeastOneExists(field, 10_000)
        compose.onNode(field).performTextReplacement(text)
    }

    /** Вкладка нижней панели (у неё роль Tab — не путать с одноимёнными заголовками). */
    private fun tab(label: String) = compose.onNode(
        hasText(label) and androidx.compose.ui.test.SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.Role,
            androidx.compose.ui.semantics.Role.Tab,
        ),
    ).performClick()

    private fun click(text: String) = compose.onNodeWithText(text).performClick()

    @Test
    fun onboardingAccountsAndCategories() {
        org.robolectric.shadows.ShadowLog.stream = System.out
        // Онбординг: валюта по умолчанию — сом.
        waitForText("Добро пожаловать")
        shot("01_onboarding_currency")
        click("Далее")
        waitForText("Первый счёт")
        input("Начальный баланс", "12500")
        shot("02_onboarding_account")
        click("Начать")

        // Главный экран: добавляем расход за три нажатия — «+», категория, «Сохранить».
        waitForText("Общий баланс")
        compose.onNode(hasContentDescription("Добавить")).performClick()
        waitForText("Новая операция")
        input("Сумма", "120+35")
        waitForText("= " + MoneyFormat.format(15_500, "KGS"))
        click("Продукты")
        shot("02b_operation_edit")
        compose.onNodeWithText("Сохранить").performScrollTo().performClick()
        waitForText("Куда ушли деньги")
        waitForText(MoneyFormat.format(-15_500, "KGS", signed = true))
        waitForText(MoneyFormat.format(1_234_500, "KGS"))
        shot("02c_home")

        // Список операций, удаление с отменой.
        click("Операции")
        waitForText("Поиск по комментарию и категории")
        waitForText("Продукты")
        shot("02d_operations")
        click("Продукты")
        waitForText("Изменить операцию")
        compose.onNode(hasContentDescription("Удалить")).performClick()
        waitForText("Операция удалена")
        click("Отменить")
        waitForText("Продукты")
        click("Главная")
        waitForText(MoneyFormat.format(1_234_500, "KGS"))

        // Отчёты: кольцо расходов и динамика.
        click("Отчёты")
        waitForText("Всего")
        waitForText("100 %")
        shot("02e_reports")
        click("Динамика")
        waitForText("По месяцам")
        shot("02f_dynamics")
        compose.onNode(hasText("Расходы") and hasClickAction()).performClick()
        waitForText("100 %")
        compose.onNode(hasText("Продукты") and hasClickAction()).performClick()
        waitForText("Сумма по месяцам")
        waitForText("100 % от всех", substring = true)
        waitForText(MoneyFormat.format(15_500, "KGS"))
        shot("02g_category_report")
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitForText("Итоги периода")

        waitForText("Ещё")
        click("Ещё")
        waitForText("Счета")
        shot("03_more")
        click("Счета")

        val total = MoneyFormat.format(1_234_500, "KGS")
        waitForText("Наличные")
        waitForText(total)
        shot("04_accounts")

        // Карточка счёта и корректировка баланса.
        click("Наличные")
        waitForText("Корректировка баланса")
        click("Корректировка баланса")
        waitForText("Фактический баланс")
        input("Фактический баланс", "10000")
        shot("05a_account_adjust")
        click("Применить")
        waitForText(MoneyFormat.format(-234_500, "KGS", signed = true))
        waitForText(MoneyFormat.format(1_000_000, "KGS"))
        shot("05_account_detail")

        // Новый счёт.
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitForText("Всего на счетах")
        compose.onNode(hasContentDescription("Добавить счёт")).performClick()
        waitForText("Новый счёт")
        input("Название", "Карта Мбанк")
        input("Начальный баланс", "5000,50")
        shot("06_account_edit")
        compose.onNodeWithText("Сохранить").performScrollTo().performClick()
        waitForText("Всего на счетах")
        waitForText("Карта Мбанк")
        waitForText(MoneyFormat.format(1_500_050, "KGS"))
        shot("07_accounts_two")

        // Настройки: выключаем типы счетов — подписи «Наличные»/«Банковская карта» пропадают.
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitForText("Настройки")
        click("Настройки")
        waitForText("Типы счетов")
        compose.onNode(hasText("Типы счетов")).performScrollTo().performClick()
        // PIN-код: придумать и повторить.
        compose.onNode(hasText("Вход по PIN-коду")).performScrollTo().performClick()
        waitForText("Придумайте PIN из 4 цифр")
        shot("07a_pin")
        "1234".forEach { click(it.toString()) }
        waitForText("Повторите PIN")
        "1234".forEach { click(it.toString()) }
        waitForText("Отпечаток или лицо")
        shot("07b_settings")
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        // Бюджет на все расходы 100 сом при расходе 155 — перерасход.
        waitForText("Бюджеты")
        click("Бюджеты")
        compose.onNode(hasContentDescription("Добавить бюджет")).performClick()
        input("Лимит на месяц", "100")
        click("Сохранить")
        waitForText("155 %")
        waitForText("Перерасход " + MoneyFormat.format(5_500, "KGS"))
        shot("07c_budgets")
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitForText("Счета")
        click("Счета")
        waitForText("Карта Мбанк")
        compose.onAllNodes(hasText("Банковская карта")).assertCountEquals(0)
        compose.onAllNodes(hasText("Наличные")).assertCountEquals(1) // только название счёта
        compose.onNode(hasContentDescription("Добавить счёт")).performClick()
        waitForText("Новый счёт")
        compose.onAllNodes(hasText("Тип")).assertCountEquals(0)
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitForText("Всего на счетах")

        // Категории: расходы и доходы.
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitForText("Категории")
        click("Категории")
        waitForText("Продукты")
        waitForText("Такси, Общественный транспорт")
        shot("08_categories_expenses")
        click("Доходы")
        waitForText("Зарплата")
        shot("09_categories_incomes")

        // Редактор категории с подкатегориями.
        click("Расходы")
        waitForText("Транспорт")
        click("Транспорт")
        waitForText("Изменить категорию")
        shot("10_category_edit")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Добавить подкатегорию"))
        waitForText("Общественный транспорт")
        shot("11_category_subcategories")

        // Долги: новый долг «Мне должны» 14 000, частичное погашение 4 000.
        compose.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
            it.onBackPressedDispatcher.onBackPressed()
        }
        waitForText("Долги")
        click("Долги")
        waitForText("Записать долг")
        click("Записать долг")
        waitForText("Новый долг")
        input("Имя", "Азамат Токтогулов")
        input("Сумма", "14000")
        shot("12_debt_edit")
        compose.onNodeWithText("Сохранить").performScrollTo().performClick()
        waitForText("Осталось".let { "История движений" })
        click("Погасить")
        input("Сумма", "4000")
        click("Провести")
        waitForText("Возвращено " + MoneyFormat.formatNumber(400_000) + "\u00A0сом из " + MoneyFormat.formatNumber(1_400_000) + "\u00A0сом")
        shot("13_debt_detail")
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitForText("Сальдо по долгам")
        waitForText(MoneyFormat.format(1_000_000, "KGS", signed = true))
        shot("14_debts")

        // Тёмная тема.
        click("Ещё")
        waitForText("Настройки")
        click("Настройки")
        waitForText("Тёмная")
        click("Тёмная")
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitForText("Главная")
        click("Главная")
        waitForText("Общий баланс")
        // Главная обновилась после долга: «Наличные» 10 000 − 14 000 + 4 000 = 0, всего 5 000,50; «Мне должны» 10 000.
        waitForText("Долг")
        shot("15_home_dark")
        tab("Долги")
        waitForText("Сальдо по долгам")
        shot("16_debts_dark")
    }
}
