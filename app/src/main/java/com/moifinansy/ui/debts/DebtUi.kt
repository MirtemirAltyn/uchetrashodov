package com.moifinansy.ui.debts

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moifinansy.R
import com.moifinansy.core.format.DateFormat
import com.moifinansy.core.format.MoneyFormat
import com.moifinansy.core.ui.theme.PickerColors
import com.moifinansy.domain.model.DebtDirection
import com.moifinansy.domain.model.DebtWithRemaining

/** Аватар с инициалами; цвет стабильно выводится из имени. */
@Composable
fun Avatar(name: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val color = Color(PickerColors[Math.floorMod(name.hashCode(), PickerColors.size)])
    val initials = name.split(' ', '-').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, color = color, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Готовый текст напоминания о долге. */
fun reminderText(context: Context, debt: DebtWithRemaining, contactName: String): String {
    val d = debt.debt
    val due = d.dueDate?.let { context.getString(R.string.debt_remind_due, DateFormat.day(it)) }.orEmpty()
    val res = if (d.direction == DebtDirection.OWED_TO_ME) R.string.debt_remind_owed_to_me else R.string.debt_remind_i_owe
    return context.getString(
        res,
        contactName,
        MoneyFormat.format(debt.remaining, d.currencyCode),
        MoneyFormat.format(debt.total, d.currencyCode),
        DateFormat.day(d.startDate),
        due,
    )
}

/** Системный Share: WhatsApp, Telegram, SMS и т. д. */
fun shareReminder(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.debt_remind_chooser)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
