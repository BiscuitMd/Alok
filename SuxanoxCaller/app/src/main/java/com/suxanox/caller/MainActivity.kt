package com.suxanox.caller

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    
    private lateinit var editUrl: EditText
    private lateinit var editKey: EditText
    private lateinit var btnStart: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        editUrl = findViewById(R.id.editUrl)
        editKey = findViewById(R.id.editKey)
        btnStart = findViewById(R.id.btnStart)

        // Minta izin CALL_PHONE biar bisa nelfon otomatis
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), 1)
        }

        // Ambil data lama (kalau udah pernah disimpen)
        val prefs = getSharedPreferences("CallerPrefs", MODE_PRIVATE)
        editUrl.setText(prefs.getString("BASE_URL", "http://ip-vps-lu:8081"))
        editKey.setText(prefs.getString("API_KEY", ""))

        btnStart.setOnClickListener {
            val url = editUrl.text.toString().trim()
            val key = editKey.text.toString().trim()
            
            if (url.isNotEmpty() && key.isNotEmpty()) {
                // 1. Simpan URL & API Key ke memori HP (Buat BootReceiver)
                val editor = getSharedPreferences("CallerPrefs", MODE_PRIVATE).edit()
                editor.putString("BASE_URL", url)
                editor.putString("API_KEY", key)
                editor.apply()

                // 2. Nyalain Service di Background
                val intent = Intent(this, CallerService::class.java)
                intent.putExtra("BASE_URL", url)
                intent.putExtra("API_KEY", key)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                
                Toast.makeText(this, "✅ Mesin Caller Aktif di Background!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "❌ URL dan API Key harus diisi!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
