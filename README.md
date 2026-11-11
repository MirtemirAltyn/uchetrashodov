# Мои финансы

Android-приложение для личного учёта денег: доходы, расходы, переводы, долги, бюджеты, регулярные платежи и отчёты.
Работает офлайн, все данные хранятся на телефоне. ТЗ — `SPEC.md`, правила разработки — `CLAUDE.md`.

## Сборка

Нужны JDK 17+ и Android SDK (путь в `local.properties`: `sdk.dir=…`).

```
./gradlew assembleDebug     # отладочный APK → app/build/outputs/apk/debug/
./gradlew test              # unit-тесты (Robolectric, без эмулятора)
./gradlew testDebugUnitTest -Pscreenshots   # + снимки экранов → app/build/screenshots/
```

### Релиз

Подпись берётся из `keystore.properties` в корне (файл в git не попадает):

```
storeFile=/путь/к/release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

```
./gradlew assembleRelease   # APK  → app/build/outputs/apk/release/
./gradlew bundleRelease     # AAB  → app/build/outputs/bundle/release/ (для Google Play)
```

Без `keystore.properties` релиз собирается неподписанным. Храните ключ подписи: без него не выпустить обновление,
которое встанет поверх уже установленного приложения.

## Сторонние ресурсы

- Шрифт Inter — SIL Open Font License, `third_party/inter/OFL.txt`.
