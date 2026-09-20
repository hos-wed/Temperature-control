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

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
        }

        val titleText = TextView(this).apply {
            text = "Temperature control"
            textSize = 24f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
        }

        layout.addView(titleText)
        setContentView(layout)
    }
}
