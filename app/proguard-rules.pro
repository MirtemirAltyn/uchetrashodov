# Room, Hilt, WorkManager, Compose приносят свои правила (consumer rules).
# Перечисления хранятся в базе по имени — имена констант не должны меняться.
-keepclassmembers enum com.moifinansy.domain.model.** { *; }
-keepclassmembers enum com.moifinansy.domain.settings.** { *; }
