package com.moifinansy.ui

import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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

    private fun click(text: String) = compose.onNodeWithText(text).performClick()

    @Test
    fun onboardingAccountsAndCategories() {
        // Онбординг: валюта по умолчанию — сом.
        waitForText("Добро пожаловать")
        shot("01_onboarding_currency")
        click("Далее")
        waitForText("Первый счёт")
        compose.onNode(hasSetTextAction() and hasText("Начальный баланс", substring = true)).performTextReplacement("12500")
        shot("02_onboarding_account")
        click("Начать")

        // Главный экран: добавляем расход за три нажатия — «+», категория, «Сохранить».
        waitForText("Общий баланс")
        compose.onNode(hasContentDescription("Добавить")).performClick()
        waitForText("Новая операция")
        compose.waitUntilAtLeastOneExists(hasSetTextAction() and hasText("Сумма", substring = true), 10_000)
        compose.onNode(hasSetTextAction() and hasText("Сумма", substring = true)).performTextReplacement("120+35")
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
        compose.onNode(hasSetTextAction() and hasText("Фактический баланс", substring = true)).performTextReplacement("10000")
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
        compose.onNode(hasSetTextAction() and hasText("Название", substring = true)).performTextReplacement("Карта Мбанк")
        compose.onNode(hasSetTextAction() and hasText("Начальный баланс", substring = true)).performTextReplacement("5000,50")
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
        click("Типы счетов")
        shot("07b_settings")
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
        compose.waitUntilAtLeastOneExists(hasSetTextAction() and hasText("Имя", substring = true), 10_000)
        compose.onNode(hasSetTextAction() and hasText("Имя", substring = true)).performTextReplacement("Азамат Токтогулов")
        compose.onNode(hasSetTextAction() and hasText("Сумма", substring = true)).performTextReplacement("14000")
        shot("12_debt_edit")
        compose.onNodeWithText("Сохранить").performScrollTo().performClick()
        waitForText("Осталось".let { "История движений" })
        click("Погасить")
        compose.onNode(hasSetTextAction() and hasText("Сумма", substring = true)).performTextReplacement("4000")
        click("Провести")
        waitForText("Возвращено " + MoneyFormat.formatNumber(400_000) + "\u00A0сом из " + MoneyFormat.formatNumber(1_400_000) + "\u00A0сом")
        shot("13_debt_detail")
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitForText("Сальдо по долгам")
        waitForText(MoneyFormat.format(1_000_000, "KGS", signed = true))
        shot("14_debts")
    }
}
