package com.example.tempchart

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter

class MainActivity : AppCompatActivity() {

    private lateinit var chart: LineChart
    private lateinit var txtInfo: TextView

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

        requestSmsPermission()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter("TEMP_SMS_RECEIVED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(smsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(smsReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(smsReceiver)
        } catch (_: Exception) {
        }
    }

    private fun requestSmsPermission() {
        val perms = arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
        val need = perms.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (need) {
            ActivityCompat.requestPermissions(this, perms, 1)
        }
    }

    // فرمت پیامک:
    // خط اول: S3 start=00:00 step=1h
    // خط دوم: 24.5,25.1,25.8,...
    private fun handleSms(body: String) {
        val lines = body.trim().split("\n")
        if (lines.size < 2) return

        val header = lines[0].trim()
        // فقط پیامک‌های که با S شروع می‌شن پردازش می‌شن
        if (!header.startsWith("S")) return

        var sensor = "?"
        var start = "00:00"
        var step = "1h"

        val parts = header.split(" ")
        for (p in parts) {
            when {
                p.startsWith("S") && !p.contains("=") -> sensor = p.substring(1)
                p.startsWith("start=") -> start = p.substringAfter("=")
                p.startsWith("step=") -> step = p.substringAfter("=")
            }
        }

        val values = lines[1].split(",")
            .mapNotNull { it.trim().toFloatOrNull() }

        if (values.isEmpty()) return

        txtInfo.text = "سنسور $sensor | شروع $start | گام $step | تعداد ${values.size}"

        val labels = buildTimeLabels(start, step, values.size)
        drawChart(values, labels, sensor)
    }

    private fun buildTimeLabels(start: String, step: String, count: Int): List<String> {
        val startParts = start.split(":")
        var hour = startParts.getOrNull(0)?.toIntOrNull() ?: 0
        var minute = startParts.getOrNull(1)?.toIntOrNull() ?: 0

        val stepMinutes = when {
            step.endsWith("h") -> (step.dropLast(1).toIntOrNull() ?: 1) * 60
            step.endsWith("m") -> step.dropLast(1).toIntOrNull() ?: 60
            else -> 60
        }

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

    private fun drawChart(values: List<Float>, labels: List<String>, sensor: String) {
        val entries = ArrayList<Entry>()
        for (i in values.indices) {
            entries.add(Entry(i.toFloat(), values[i]))
        }

        val dataSet = LineDataSet(entries, "سنسور $sensor")
        dataSet.setDrawCircles(true)
        dataSet.circleRadius = 3f
        dataSet.lineWidth = 2f
        dataSet.setDrawValues(false)

        chart.data = LineData(dataSet)

        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        chart.xAxis.granularity = 1f
        chart.xAxis.labelRotationAngle = -45f

        chart.axisRight.isEnabled = false
        chart.description.isEnabled = false
        chart.animateX(500)
        chart.invalidate()
    }
}

