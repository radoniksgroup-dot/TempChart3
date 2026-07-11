package com.example.tempchart

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            val body = StringBuilder()
            for (msg in messages) {
                body.append(msg.messageBody)
            }
            val text = body.toString()

            if (text.trimStart().startsWith("S")) {
                val localIntent = Intent(MainActivity.ACTION_SMS)
                localIntent.putExtra("body", text)
                LocalBroadcastManager.getInstance(context).sendBroadcast(localIntent)
            }
        }
    }
}

