package com.example.tempchart

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telephony.SmsManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter

class MainActivity : AppCompatActivity() {

    companion object {
        const val DEVICE_NUMBER = "+98XXXXXXX" // شماره واقعی دستگاه را اینجا بگذار
        const val ACTION_SMS = "TEMP_SMS_RECEIVED"
        const val PERM_REQUEST = 100
    }

    private lateinit var chart: LineChart
    private lateinit var txtInfo: TextView

    // برچسب‌های زمانی محور X
    private var timeLabels: List<String> = emptyList()

    private val smsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val body = intent?.getStringExtra("body") ?: return
            handleSms(body)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        chart = findViewById(R.id.chart)
        txtInfo = findViewById(R.id.txtInfo)

        // درخواست مجوزهای لازم
        requestNededPermissions()

        // دکمه خواندن آخرین پیامک
        findViewById<Button>(R.id.btnReadLast).setOnClickListener { readLastSms() }

        // دکمه‌های درخواست سنسور
        findViewById<Button>(R.id.btnSensor1).setOnClickListener { requestSensor(1) }
        findViewById<Button>(R.id.btnSensor2).setOnClickListener { requestSensor(2) }
        findViewById<Button>(R.id.btnSensor3).setOnClickListener { requestSensor(3) }
        findViewById<Button>(R.id.btnSensor4).setOnClickListener { requestSensor(4) }
        findViewById<Button>(R.id.btnSensor5).setOnClickListener { requestSensor(5) }
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(smsReceiver, IntentFilter(ACTION_SMS))
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(smsReceiver)
    }

    private fun requestNeededPermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
            != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.RECEIVE_SMS)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.READ_SMS)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.SEND_SMS)
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), PERM_REQUEST)
        }
    }

    // ارسال درخواست داده به دستگاه: G1 تا G5
    private fun requestSensor(sensor: Int) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.SEND_SMS), PERM_REQUEST)
            Toast.makeText(this, "مجوز ارسال پیامک لازم است", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val sms = SmsManager.getDefault()
            sms.sendTextMessage(DEVICE_NUMBER, null, "G$sensor", null, null)
            Toast.makeText(this, "درخواست سنسور $sensor ارسال شد", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "خطا در ارسال: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // خواندن آخرین پیامک شروع‌شونده با S از صندوق ورودی
    private fun readLastSms() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.READ_SMS), PERM_REQUEST)
            Toast.makeText(this, "مجوز خواندن پیامک لازم است", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val cursor = contentResolver.query(
                Uri.parse("content://sms/inbox"),
                arayOf("body"),
                null, null,
                "date DESC"
            )
            cursor?.use {
                val idx = it.getColumnIndex("body")
                while (it.moveToNext()) {
                    val body = it.getString(idx) ?: continue
                    if (body.trimStart().startsWith("S") {
                        handleSms(body)
                        return
                    }
                }
            }
            Toast.makeText(this, "پیامک معتبری پیدا نشد", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "خطا در خواندن: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // پردازش پیامک با فرمت:
    // S<n> start=<H:m>\n<دما یا دما*تعداد , ...>
    private fun handleSms(raw: String) {
        try {
            val text = raw.trim()
            val newlineIdx = text.indexOf('\n')
            if (newlineIdx < 0) {
                Toast.makeText(this, "فرمت پیامک نامعتبر است", Toast.LENGTH_SHORT).show()
                return
            }
            val header = text.substring(0, newlineIdx).trim()
            val dataLine = text.substring(newlineIdx + 1).trim()

            // استخراج شماره سنسور و ساعت شروع
            val sensor = Regex("""S\s*(\d+)"").find(header)?.groupValues?.get(1) ?: "?"
            val startStr = Regex("""start\s*=\s*(\d{1,2}:\d{2})""")
                .find(header)?.groupValues?.get(1) ?: "0:00"

            val temps = expandTemps(dataLine)
            if (temps.isEmpty()) {
                Toast.makeText(this, "داده‌ای برای رسم وجود ندارد", Toast.LENGTH_SHORT).show()
                return
            }

            timeLabels = buildTimeLabels(startStr, temps.size)
            drawChart(temps)

            txtInfo.text = "سنسور $sensor | شروع $startStr | تعداد ${temps.size} نقطه"
        } catch (e: Exception) {
            Toast.makeText(this, "خطا در پردازش: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // باز کردن فرمت فشرده: "18*3,17*4,19" -> [18,18,18,17,17,17,19]
    private fun expandTemps(dataLine: String): List<Float> {
        val result = mutableListOf<Float>()
        for (token in dataLine.split(",")) {
            val t = token.trim()
            if (t.isEmpty()) continue
            if (t.contains("*")) {
                val parts = t.split("*")
                val value = parts[0].trim().toFloat()
                val count = parts[1].trim().toInt()
                repeat(count) { result.add(value) }
            } else {
                result.add(t.toFloat())
            }
        }
        return result
    }

    // ساخت برچسب زمانی با گام۳۰ دقیقه از ساعت شروع
    private fun buildTimeLabels(startStr: String, count: Int): List<String> {
        val parts = startStr.split(":")
        var hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
        var minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val labels = ArrayList<String>(count)
        for (i in 0 until count) {
            labels.add(String.format("%02d:%02d", hour, minute))
            minute += 30
            if (minute >= 60) {
                minute -= 60
                hour = (hour + 1) % 24
            }
        }
        return labels
    }

    private fun drawChart(temps: List<Float>) {
        val entries = temps.mapIndexed { i, v -> Entry(i.toFloat(), v) }
        val dataSet = LineDataSet(entries, "دما (°C)").apply {
            setDrawCircles(true)
            circleRadius = 2.5f
            lineWidth = 1.5f
            setDrawValues(false)
        }

        chart.data = LineData(dataSet)

        chart.xAxis.aply {
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            labelRotationAngle = -45f
            setLabelCount(6, false)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                val idx = value.toInt()
                    return timeLabels.getOrNull(idx) ?: ""
                }
            }
        }

        chart.axisRight.isEnabled = false
        chart.description.isEnabled = false
        chart.invalidate()
    }
}
