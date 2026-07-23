package com.example.data.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import java.net.URLEncoder

sealed class ToolResult {
    data class Success(val message: String) : ToolResult()
    data class MissingPermission(val permission: String, val promptMessage: String) : ToolResult()
    data class Error(val message: String) : ToolResult()
}

class ToolExecutionEngine(private val context: Context) {

    fun checkPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Launch any app by package name or common app alias
     */
    fun openApp(appNameOrPackage: String): ToolResult {
        val pm = context.packageManager
        val targetPackage = resolvePackageName(appNameOrPackage) ?: appNameOrPackage

        val intent = pm.getLaunchIntentForPackage(targetPackage)
            ?: pm.getLaunchIntentForPackage(appNameOrPackage)

        return if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ToolResult.Success("Opened $appNameOrPackage successfully!")
        } else {
            // Try searching web/store or general intent if specific app package wasn't installed
            val storeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$appNameOrPackage")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(storeIntent)
                ToolResult.Success("Searching $appNameOrPackage on Play Store!")
            } catch (e: Exception) {
                ToolResult.Error("Could not find or launch application for '$appNameOrPackage'.")
            }
        }
    }

    /**
     * Query Contacts Provider for contact name and place phone call
     */
    fun searchAndCallContact(contactName: String): ToolResult {
        if (!checkPermission(Manifest.permission.READ_CONTACTS)) {
            return ToolResult.MissingPermission(
                permission = Manifest.permission.READ_CONTACTS,
                promptMessage = "I'd love to call $contactName for you, darling, but you haven't given me contacts permission yet! Enable it in settings, hotstuff."
            )
        }

        val phoneNumber = lookupPhoneNumber(contactName)
            ?: return ToolResult.Error("I couldn't find anyone named '$contactName' in your contacts list.")

        return if (checkPermission(Manifest.permission.CALL_PHONE)) {
            try {
                val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(callIntent)
                ToolResult.Success("Calling $contactName at $phoneNumber...")
            } catch (e: Exception) {
                // Safe fallback to dialer
                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(dialIntent)
                ToolResult.Success("Opening dialer for $contactName ($phoneNumber).")
            }
        } else {
            // Fallback to dialer if direct CALL_PHONE is not granted
            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(dialIntent)
            ToolResult.Success("Opening phone dialer for $contactName ($phoneNumber).")
        }
    }

    /**
     * Send WhatsApp message to contact via deep-link
     */
    fun sendWhatsAppMessage(contactName: String, message: String): ToolResult {
        val phoneNumber = if (checkPermission(Manifest.permission.READ_CONTACTS)) {
            lookupPhoneNumber(contactName) ?: contactName.filter { it.isDigit() || it == '+' }
        } else {
            contactName.filter { it.isDigit() || it == '+' }
        }

        val cleanPhone = phoneNumber.filter { it.isDigit() }
        val encodedMsg = URLEncoder.encode(message, "UTF-8")

        return try {
            val uri = if (cleanPhone.isNotEmpty()) {
                Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedMsg")
            } else {
                Uri.parse("whatsapp://send?text=$encodedMsg")
            }
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolResult.Success("WhatsApp deep link triggered for $contactName!")
        } catch (e: Exception) {
            // General share intent
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(sendIntent)
                ToolResult.Success("Opening WhatsApp to message $contactName.")
            } catch (ex: Exception) {
                ToolResult.Error("WhatsApp doesn't seem to be installed on this device, cutie!")
            }
        }
    }

    /**
     * Compose/Send email via Gmail Intent
     */
    fun sendGmail(recipientEmail: String, subject: String, body: String): ToolResult {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$recipientEmail")
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            setPackage("com.google.android.gm")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ToolResult.Success("Opening Gmail draft for $recipientEmail with subject: '$subject'.")
        } catch (e: Exception) {
            // General mail intent fallback
            val mailIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$recipientEmail")).apply {
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(mailIntent)
                ToolResult.Success("Opening email app for $recipientEmail.")
            } catch (ex: Exception) {
                ToolResult.Error("No email app available to send message to $recipientEmail.")
            }
        }
    }

    private fun lookupPhoneNumber(contactName: String): String? {
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            ),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$contactName%"),
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val numIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (numIndex >= 0) {
                    return it.getString(numIndex)
                }
            }
        }
        return null
    }

    private fun resolvePackageName(appName: String): String? {
        val lower = appName.lowercase().trim()
        return when {
            lower.contains("youtube") -> "com.google.android.youtube"
            lower.contains("instagram") -> "com.instagram.android"
            lower.contains("whatsapp") -> "com.whatsapp"
            lower.contains("gmail") -> "com.google.android.gm"
            lower.contains("calculator") -> "com.google.android.calculator"
            lower.contains("chrome") -> "com.android.chrome"
            lower.contains("camera") -> "com.android.camera"
            lower.contains("spotify") -> "com.spotify.music"
            lower.contains("map") || lower.contains("maps") -> "com.google.android.apps.maps"
            lower.contains("clock") || lower.contains("alarm") -> "com.google.android.deskclock"
            else -> null
        }
    }
}
