package com.moifinansy.data.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class PhoneContact(val name: String, val phone: String)

/**
 * Контакты телефона — только чтение и только на устройстве. Поиск работает, если пользователь дал
 * разрешение READ_CONTACTS; выбор одного контакта через системный экран разрешения не требует.
 */
class PhoneContacts @Inject constructor(@param:ApplicationContext private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Совпадения по имени или номеру (не больше [limit]). */
    suspend fun search(query: String, limit: Int = 5): List<PhoneContact> = withContext(Dispatchers.IO) {
        if (!hasPermission() || query.isBlank()) return@withContext emptyList()
        val uri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(query.trim()))
        val result = mutableListOf<PhoneContact>()
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC",
            )?.use { c ->
                while (c.moveToNext() && result.size < limit * 3) {
                    val name = c.getString(0).orEmpty()
                    val phone = c.getString(1).orEmpty()
                    if (name.isNotBlank() && phone.isNotBlank()) result += PhoneContact(name, phone)
                }
            }
        }
        result.distinctBy { it.name + "|" + it.phone.filter(Char::isDigit) }.take(limit)
    }

    /** Есть ли номер в контактах телефона (null — не знаем: нет разрешения). */
    suspend fun exists(phone: String?): Boolean? = withContext(Dispatchers.IO) {
        if (phone.isNullOrBlank()) return@withContext false
        if (!hasPermission()) return@withContext null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phone))
        runCatching { context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)?.use { it.count > 0 } }
            .getOrNull()
    }

    /** Разобрать результат системного выбора контакта (Phone.CONTENT_URI). */
    fun read(uri: Uri): PhoneContact? = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null,
        )?.use { c -> if (c.moveToFirst()) PhoneContact(c.getString(0).orEmpty(), c.getString(1).orEmpty()) else null }
    }.getOrNull()
}
