package com.parallelagent.inputhelper

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Base64

class ClipboardReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val encoded = intent.getStringExtra("text_base64") ?: return
        val text = String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("Parallel Agent task text", text))
    }
}
