package com.example.flydigicooler

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var dashboardView: CoolerDashboardView

    // 系统电池温度广播监听（精准获取硬件实际发热）
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val tempRaw = it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                if (tempRaw > 0) {
                    dashboardView.phoneTemp = tempRaw / 10.0f
                    dashboardView.invalidate()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 直接挂载纯白淡蓝自绘现代车机控件
        dashboardView = CoolerDashboardView(this)
        setContentView(dashboardView)

        // 阻尼旋钮档位切换回调
        dashboardView.onGearChangedListener = { gear ->
            val msg = "已切换至 ${gear.title}（${gear.desc}）"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // 注册系统温度监听
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(batteryReceiver)
    }
}
