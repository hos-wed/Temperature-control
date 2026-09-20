package com.example.flydigicooler

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#F4F7FB"))
        }

        val titleView = TextView(this).apply {
            text = "Temperature control"
            textSize = 22f
            setTextColor(Color.parseColor("#0288D1"))
            gravity = Gravity.CENTER
        }

        rootLayout.addView(titleView)
        setContentView(rootLayout)
    }
}
