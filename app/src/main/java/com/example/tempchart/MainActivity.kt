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
   private fun handleSms(body: String) {
    val text = body.trim()
    if (!text.startsWith("S")) return

    // جدا کردن خط هدر از خطوط داده
    val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.isEmpty()) return

    val header = lines[0]
    val dataStr = lines.drop(1).joinToString(",")

    // استخراج شماره سنسور
    val sensorMatch = Regex("^S(\\d+)").find(header)
    val sensor = sensorMatch?.groupValues?.get(1) ?: "?"

    // استخراج ساعت شروع
    val startMatch = Regex("start=(\\d{1,2}):(\\d{2})").find(header)
    if (startMatch == null) {
        runOnUiThread { txtInfo.text = "خطا: ساعت شروع پیدا نشد" }
        return
    }
    val startHour = startMatch.groupValues[1].toInt()
    val startMin = startMatch.groupValues[2].toInt()

    // خواندن دماها
    val temps = dataStr.split(",")
        .mapNotNull { it.trim().toFloatOrNull() }

    if (temps.isEmpty()) {
        runOnUiThread { txtInfo.text = "خطا: دمای پیدا نشد" }
        return
    }

    // گام ثابت ۳۰ دقیقه
    val stepMinutes = 30

    runOnUiThread {
        txtInfo.text = "سنسور $sensor - شروع %02d:%02d - %d مقدار"
            .format(startHour, startMin, temps.size)
        drawChart(temps, startHour, startMin, stepMinutes)
    }
}


    private fun drawChart(temps: List<Float>, startHour: Int, startMin: Int, stepMinutes: Int) {
    val entries = temps.mapIndexed { i, t -> Entry(i.toFloat(), t) }

    val dataSet = LineDataSet(entries, "دما")
    dataSet.setDrawCircles(false)
    dataSet.lineWidth = 2f

    chart.data = LineData(dataSet)

    // برچسب زمان روی محور X
    chart.xAxis.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
        override fun getFormattedValue(value: Float): String {
            val totalMin = startMin + value.toInt() * stepMinutes
            val h = (startHour + totalMin / 60) % 24
            val m = totalMin % 60
            return "%02d:%02d".format(h, m)
        }
    }

    chart.xAxis.position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
    chart.description.isEnabled = false
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
