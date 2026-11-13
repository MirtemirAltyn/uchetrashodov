package com.moifinansy.ui.debts

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Выбор одного контакта в системной телефонной книге. Разрешение READ_CONTACTS не нужно:
 * система даёт доступ только к выбранной записи. Возвращает функцию «открыть выбор».
 */
@Composable
fun rememberPhonebookPicker(onPicked: (name: String, phone: String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            com.moifinansy.data.contacts.PhoneContacts(context.applicationContext).read(uri)?.let { onPicked(it.name, it.phone) }
        }
    }
    return { runCatching { launcher.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) } }
}

/** Системный экран «Новый контакт» с уже вставленными именем и номером; сохраняет сам пользователь. */
fun saveToPhoneContacts(context: Context, name: String, phone: String?) {
    val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
        type = ContactsContract.RawContacts.CONTENT_TYPE
        putExtra(ContactsContract.Intents.Insert.NAME, name)
        if (!phone.isNullOrBlank()) putExtra(ContactsContract.Intents.Insert.PHONE, phone)
    }
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
