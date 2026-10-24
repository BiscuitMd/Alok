package com.suxanox.caller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("CallerPrefs", Context.MODE_PRIVATE)
            val url = prefs.getString("BASE_URL", "") ?: ""
            val key = prefs.getString("API_KEY", "") ?: ""
            
            if (url.isNotEmpty() && key.isNotEmpty()) {
                val serviceIntent = Intent(context, CallerService::class.java)
                serviceIntent.putExtra("BASE_URL", url)
                serviceIntent.putExtra("API_KEY", key)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
