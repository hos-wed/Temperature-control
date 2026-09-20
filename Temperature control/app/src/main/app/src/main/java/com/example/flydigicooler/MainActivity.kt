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
        try {
            val rootLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(Color.parseColor("#0E1116")) // 采用现代汽车暗灰底色
            }

            val titleView = TextView(this).apply {
                text = "Temperature control"
                textSize = 22f
                setTextColor(Color.parseColor("#00E5FF")) // 采用冰蓝文字
                gravity = Gravity.CENTER
            }

            rootLayout.addView(titleView)
            setContentView(rootLayout)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
