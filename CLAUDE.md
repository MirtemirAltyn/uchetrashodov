# Мои финансы — правила проекта

Полное ТЗ: `SPEC.md`. Работаем строго по этапам из раздела 7, по одному этапу за раз.
Каждый этап: зелёная сборка, зелёные тесты, отдельный коммит. Следующий этап — только после проверки на телефоне.

## Стек
Kotlin · Jetpack Compose + Material 3 · MVVM + Clean Architecture · Room · Hilt (KSP) ·
Coroutines + Flow (StateFlow) · Navigation Compose · DataStore · WorkManager · Vico.
Gradle Kotlin DSL, версии в `gradle/libs.versions.toml`. minSdk 26, targetSdk/compileSdk 36, JVM target 17.

## Пакеты (`app/src/main/java/com/moifinansy`)
- `data` — Room, DAO, репозитории;
- `domain` — модели и use cases (без зависимостей от Android);
- `ui` — экраны и ViewModel, по папке на раздел (`home`, `operations`, `debts`, `reports`, `more`), навигация в `ui/navigation`;
- `core` — тема (`core/ui/theme`), форматирование денег и дат (`core/format`), общие компоненты (`core/ui`).
- Настройки (основная валюта, онбординг) — `SettingsRepository` на DataStore.

## Обязательные правила
- Деньги — только `Long` в минимальных единицах (тыйыны/копейки). Никогда не `Double`/`Float`.
- Даты — `Long` (epoch millis). id — `Long` autoincrement. У каждой таблицы `createdAt`, `updatedAt`.
- Балансы не храним, а считаем из операций SQL-запросами (SUM / GROUP BY), не в памяти.
- Связанные записи пишем в одной `@Transaction`.
- Расход и доход без категории сохранить нельзя.
- Бизнес-логика — в use cases/ViewModel, не в Composable-функциях.
- Все строки интерфейса — в `res/values/strings.xml` (русский в v1).
- Цвета и размеры — из темы (`MaterialTheme`, `LocalFinanceColors`, `Spacing`), без хардкода в экранах.
- Схема Room экспортируется (`exportSchema = true`, файлы в `app/schemas/`), изменения — через миграции с тестами.
- Знак суммы операции: EXPENSE/INCOME/TRANSFER — `amount` > 0, направление задаёт тип;
  ADJUSTMENT и DEBT — `amount` со знаком (влияние на баланс счёта). Формула баланса — `AccountDao.BALANCE_SQL`.
- Движения по долгам создаются только через `DebtRepository`/`DebtDao` (движение + операция по счёту + статус — одна транзакция БД).
- В отчёты по категориям входят только EXPENSE и INCOME; комиссия перевода — расход в служебной категории «Комиссии» (`systemKey = COMMISSIONS`).

## Команды
```
./gradlew assembleDebug   # сборка debug APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew test            # unit-тесты
```
Нужен Android SDK: путь в `local.properties` (`sdk.dir=...`) или переменная `ANDROID_HOME`.

## Тесты
- Unit-тесты на JVM; тесты Room — через Robolectric на базе в памяти, наследуются от `app/src/test/.../data/DbTest.kt`.
- `ui/AppSmokeTest` — сквозной прогон настоящего `MainActivity` (онбординг → счета → категории) через Compose UI test.
  Скриншоты экранов: `./gradlew testDebugUnitTest -Pscreenshots` → `app/build/screenshots/`.
- Поля ввода внутри `AlertDialog` в Robolectric «не дают уснуть» тестовым часам — ввод суммы делаем на экране, не в диалоге.
- Robolectric работает офлайн: его Android-рантайм (`android-all-instrumented`) скачивает Gradle как зависимость
  `robolectricRuntime`. Версия рантайма в `libs.versions.toml` должна подходить к версии Robolectric.
