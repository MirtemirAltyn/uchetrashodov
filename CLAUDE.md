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
- `core` — тема (`core/ui/theme`), форматирование денег и дат, общие компоненты.

## Обязательные правила
- Деньги — только `Long` в минимальных единицах (тыйыны/копейки). Никогда не `Double`/`Float`.
- Даты — `Long` (epoch millis). id — `Long` autoincrement. У каждой таблицы `createdAt`, `updatedAt`.
- Балансы не храним, а считаем из операций SQL-запросами (SUM / GROUP BY), не в памяти.
- Связанные записи пишем в одной `@Transaction`.
- Расход и доход без категории сохранить нельзя.
- Бизнес-логика — в use cases/ViewModel, не в Composable-функциях.
- Все строки интерфейса — в `res/values/strings.xml` (русский в v1).
- Цвета и размеры — из темы (`MaterialTheme`, `LocalFinanceColors`, `Spacing`), без хардкода в экранах.
- Схема Room экспортируется (`exportSchema = true`), изменения — через миграции с тестами.

## Команды
```
./gradlew assembleDebug   # сборка debug APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew test            # unit-тесты
```
Нужен Android SDK: путь в `local.properties` (`sdk.dir=...`) или переменная `ANDROID_HOME`.
