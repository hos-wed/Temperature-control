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
            setBackgroundColor(Color.parseColor("#F4F7FB")) // 纯白浅蓝主题底色
        }

        val titleView = TextView(this).apply {
            text = "Temperature control"
            textSize = 22f
            setTextColor(Color.parseColor("#0288D1")) // 冰蓝文字
            gravity = Gravity.CENTER
        }

        rootLayout.addView(titleView)
        setContentView(rootLayout)
    }
}
