package com.example.esp32control

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class LogsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logs)

        val txtLogs = findViewById<android.widget.TextView>(R.id.txtLogs)
        txtLogs.movementMethod = ScrollingMovementMethod()

        val db = DbHelper(this)
        val cursor = db.lastLogs(200) // show last 200 commands

        val sb = StringBuilder()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        while (cursor.moveToNext()) {
            val ts = cursor.getLong(0)
            val cmd = cursor.getString(1)
            sb.append("${sdf.format(Date(ts))} - $cmd\n")
        }
        cursor.close()

        if (sb.isEmpty()) {
            sb.append("No logs recorded.")
        }

        txtLogs.text = sb.toString()
    }
}
