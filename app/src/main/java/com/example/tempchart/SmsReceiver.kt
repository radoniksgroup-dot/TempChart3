package com.example.tempchart

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            val body = StringBuilder()
            for (msg in messages) {
                body.append(msg.messageBody)
            }
            val localIntent = Intent("TEMP_SMS_RECEIVED")
            localIntent.putExtra("body", body.toString())
            localIntent.setPackage(context.packageName)
            context.sendBroadcast(localIntent)
        }
    }
}

