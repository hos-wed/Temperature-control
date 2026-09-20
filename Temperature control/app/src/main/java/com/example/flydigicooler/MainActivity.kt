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

        dashboardView = CoolerDashboardView(this)
        setContentView(dashboardView)

        dashboardView.onGearChangedListener = { gear ->
            val msg = "已切换至 ${gear.title}（${gear.desc}）"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(batteryReceiver)
    }
}
