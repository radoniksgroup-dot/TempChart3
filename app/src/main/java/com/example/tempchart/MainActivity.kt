package com.example.tempchart

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
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
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter

class MainActivity : AppCompatActivity() {

    private lateinit var txtInfo: TextView
    private lateinit var chart: LineChart

    // گیرنده‌ی داخلی برای پیامک‌های زنده که از SmsReceiver می‌آد
    private val smsBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val body = intent?.getStringExtra("body") ?: return
            handleSms(body)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtInfo = findViewById(R.id.txtInfo)
        chart = findViewById(R.id.chart)

        findViewById<Button>(R.id.btnReadLast).setOnClickListener {
            readLastSms()
        }

        // درخواست مجوزها در شروع
        requestSmsPermissions()
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            smsBroadcastReceiver,
            IntentFilter("TEMP_SMS_RECEIVED")
        )
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(smsBroadcastReceiver)
    }

    private fun requestSmsPermissions() {
        val needed = arrayOf(
            android.Manifest.permission.RECEIVE_SMS,
            android.Manifest.permission.READ_SMS
        ).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 100)
        }
    }

    // خواندن آخرین پیامک مناسب از صندوق ورودی
    private fun readLastSms() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.READ_SMS),
                101
            )
            return
        }

        val uri = android.net.Uri.parse("content://sms/inbox")
        val cursor = contentResolver.query(
            uri,
            arrayOf("body"),
            null, null,
            "date DESC"
        )

        cursor?.use {
            val bodyIndex = it.getColumnIndex("body")
            while (it.moveToNext()) {
                val body = it.getString(bodyIndex) ?: continue
                if (body.trim().startsWith("S")) {
                    handleSms(body)
                    return
                }
            }
        }

        txtInfo.text = "پیامک مناسبی پیدا نشد"
    }

    // پردازش متن پیامک و رسم نمودار
    // فرمت: S<سنسور> start=<HH:mm> step=<گام>\n<عدد,عدد,عدد>
    private fun handleSms(raw: String) {
        try {
            val body = raw.trim()
            if (!body.startsWith("S")) {
                txtInfo.text = "فرمت پیامک نامعتبر است"
                return
            }

            // جدا کردن هدر و خط داده
            val lines = body.split("\n", "\r\n", "\r").filter { it.isNotBlank() }
            if (lines.size < 2) {
                txtInfo.text = "داده‌ی دما در پیامک نیست"
                return
            }

            val header = lines[0].trim()
            val dataLine = lines[1].trim()

            // استخراج شماره سنسور
            val sensor = Regex("^S(\\d+)").find(header)?.groupValues?.get(1) ?: "?"

            // استخراج زمان شروع
            val start = Regex("start=([0-9]{1,2}:[0-9]{2})").find(header)?.groupValues?.get(1) ?: "00:00"

            // استخراج گام زمانی
            val step = Regex("step=(\\S+)").find(header)?.groupValues?.get(1) ?: "1h"

            // مقادیر دما
            val values = dataLine.split(",")
                .mapNotNull { it.trim().toFloatOrNull() }

            if (values.isEmpty()) {
                txtInfo.text = "هیچ مقدار دمایی خوانده نشد"
                return
            }

            txtInfo.text = "سنسور $sensor | شروع $start | گام $step | تعداد نقاط ${values.size}"

            drawChart(values, start, step)

        } catch (e: Exception) {
            txtInfo.text = "خطا در پردازش: ${e.message}"
            Toast.makeText(this, "خطا در پردازش پیامک", Toast.LENGTH_SHORT).show()
        }
    }

    private fun drawChart(values: List<Float>, start: String, step: String) {
        val entries = values.mapIndexed { i, v -> Entry(i.toFloat(), v) }

        val dataSet = LineDataSet(entries, "دما (°C)").apply {
            setDrawCircles(true)
            circleRadius = 3f
            lineWidth = 2f
            setDrawValues(false)
        }

        chart.data = LineData(dataSet)

        // برچسب‌های محور افقی بر اساس زمان شروع و گام
        val labels = buildTimeLabels(start, step, values.size)
        chart.xAxis.apply {
            valueFormatter = IndexAxisValueFormatter(labels)
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            labelRotationAngle = -45f
        }

        chart.description.text = ""
        chart.axisRight.isEnabled = false
        chart.invalidate()
    }

    // ساخت برچسب‌های زمانی محور افقی
    private fun buildTimeLabels(start: String, step: String, count: Int): List<String> {
        val parts = start.split(":")
        var hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
        var minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        // تبدیل گام به دقیقه
        val stepMinutes = parseStepMinutes(step)

        val labels = ArrayList<String>()
        for (i in 0 until count) {
            labels.add(String.format("%02d:%02d", hour, minute))
            minute += stepMinutes
            hour += minute / 60
            minute %= 60
            hour %= 24
        }
        return labels
    }

    // تبدیل رشته‌ی گام (مثل 1h، 30m، 2h) به دقیقه
    private fun parseStepMinutes(step: String): Int {
        val s = step.trim().lowercase()
        val num = Regex("(\\d+)").find(s)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        return when {
            s.endsWith("h") -> num * 60
            s.endsWith("m") -> num
            else -> num * 60
        }
    }
}
