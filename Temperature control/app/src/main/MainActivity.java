package com.example.flydigicooler;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {

    private DashboardView dashboardView;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;
    private BluetoothGatt connectedGatt;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isScanning = false;

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                int tempRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
                if (tempRaw > 0 && dashboardView != null) {
                    dashboardView.phoneTemp = tempRaw / 10.0f;
                    dashboardView.invalidate();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dashboardView = new DashboardView(this);
        setContentView(dashboardView);

        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        initBleAndRequestPermissions();
    }

    private void initBleAndRequestPermissions() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            dashboardView.scanStatus = "设备不支持低功耗蓝牙";
            dashboardView.invalidate();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.ACCESS_FINE_LOCATION
                }, 101);
                return;
            }
        }
        startCoolerScan();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCoolerScan();
        } else {
            dashboardView.scanStatus = "请授予蓝牙权限以继续";
            dashboardView.invalidate();
        }
    }

    public void startCoolerScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            dashboardView.scanStatus = "请开启系统蓝牙后重试";
            dashboardView.invalidate();
            return;
        }

        bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bleScanner == null) {
            dashboardView.scanStatus = "蓝牙扫描服务启动失败";
            dashboardView.invalidate();
            return;
        }

        isScanning = true;
        dashboardView.isSearchingCooler = true;
        dashboardView.scanStatus = "正在连接设备.";
        dashboardView.invalidate();

        try {
            bleScanner.startScan(scanCallback);
        } catch (SecurityException ignored) {}

        handler.postDelayed(() -> {
            if (isScanning) {
                stopCoolerScan();
                dashboardView.scanStatus = "未检测到可用设备，请确认设备已通电";
                dashboardView.invalidate();
            }
        }, 15000);
    }

    private void stopCoolerScan() {
        if (isScanning && bleScanner != null) {
            try {
                bleScanner.stopScan(scanCallback);
            } catch (SecurityException ignored) {}
            isScanning = false;
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            String name = null;
            try {
                name = device.getName();
            } catch (SecurityException ignored) {}

            if (name == null || name.trim().isEmpty()) return;

            String upper = name.toUpperCase(Locale.ROOT);
            // 后台静默匹配目标散热器硬件标识，前端不展示敏感品牌词
            if (upper.contains("B6X") || upper.contains("B7X") || upper.contains("B8X") || upper.contains("B9X")
                    || upper.contains("FLYDIGI") || upper.contains("B6") || upper.contains("B7")) {
                stopCoolerScan();
                connectToCooler(device);
            }
        }
    };

    private void connectToCooler(BluetoothDevice device) {
        dashboardView.scanStatus = "正在建立通信连接...";
        dashboardView.invalidate();

        try {
            connectedGatt = device.connectGatt(this, false, new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        runOnUiThread(() -> {
                            dashboardView.connectedDeviceName = "COOLER UNIT";
                            dashboardView.isSearchingCooler = false; // 连通放行进入主界面
                            dashboardView.triggerHaptic(true);
                            dashboardView.invalidate();
                            Toast.makeText(MainActivity.this, "设备已连接", Toast.LENGTH_SHORT).show();
                        });
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        runOnUiThread(() -> {
                            dashboardView.scanStatus = "连接中断，点击重新连接";
                            dashboardView.invalidate();
                        });
                    }
                }
            });
        } catch (SecurityException ignored) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopCoolerScan();
        if (connectedGatt != null) {
            try {
                connectedGatt.close();
            } catch (SecurityException ignored) {}
        }
        unregisterReceiver(batteryReceiver);
    }

    public static class DashboardView extends View {
        public float phoneTemp = 34.0f;
        public int fanRpm = 5400;
        public int currentLevel = 3;
        public boolean isAmbientOn = true;

        // 蓝牙扫描与放行状态
        public boolean isSearchingCooler = true;
        public String scanStatus = "正在连接设备.";
        public String connectedDeviceName = "未连接";

        // 动画流转帧计数器
        private float animTick = 0f;
        private int dotCount = 1;
        private long lastDotTime = 0;

        // 震动档位：0=关, 1=轻柔, 2=标准, 3=强劲
        public int hapticStrength = 2;
        public boolean isHapticDialogVisible = false;

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Vibrator vibrator;

        public DashboardView(Context context) {
            super(context);
            setClickable(true);
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();

            // 1. 底层冷感流体背景
            Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            Shader bgShader = new LinearGradient(
                    0, 0, w, h,
                    new int[]{Color.parseColor("#EBF3FA"), Color.parseColor("#F4F8FC"), Color.parseColor("#E5EEF8")},
                    new float[]{0f, 0.5f, 1f},
                    Shader.TileMode.CLAMP
            );
            bgPaint.setShader(bgShader);
            canvas.drawRect(0, 0, w, h, bgPaint);

            // 背景液态浅蓝光晕
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor("#2538BDF8"));
            canvas.drawCircle(w * 0.2f, dp(150), dp(130), paint);
            paint.setColor(Color.parseColor("#180288D1"));
            canvas.drawCircle(w * 0.85f, dp(460), dp(160), paint);

            // 2. 顶部状态栏标题
            textPaint.setColor(Color.parseColor("#0F2840"));
            textPaint.setTextSize(sp(18));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("CLIMATE CONTROL", dp(24), dp(48), textPaint);

            textPaint.setColor(Color.parseColor("#5A7B9A"));
            textPaint.setTextSize(sp(11));
            textPaint.setTypeface(Typeface.DEFAULT);
            canvas.drawText("DEVICE: " + connectedDeviceName + " · 极冷座舱", dp(24), dp(68), textPaint);

            // 3. 双仪表盘液态玻璃卡片
            RectF card = new RectF(dp(20), dp(84), w - dp(20), dp(284));
            drawGlassPanel(canvas, card, dp(22));

            paint.setShader(null);
            paint.setColor(Color.parseColor("#30FFFFFF"));
            paint.setStrokeWidth(dp(2f));
            canvas.drawLine(w / 2.0f, dp(100), w / 2.0f, dp(268), paint);
            paint.setColor(Color.parseColor("#1864748B"));
            paint.setStrokeWidth(dp(1f));
            canvas.drawLine(w / 2.0f + dp(1), dp(100), w / 2.0f + dp(1), dp(268), paint);

            // 3.1 左仪表：手机核心温度
            float leftCx = w * 0.26f;
            float gaugeCy = dp(180);
            float gaugeR = dp(46);
            drawGauge(canvas, leftCx, gaugeCy, gaugeR, 135, 220, Math.min(1.0f, phoneTemp / 60.0f));

            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(Color.parseColor("#0A192F"));
            textPaint.setTextSize(sp(26));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText(String.valueOf((int) phoneTemp), leftCx - dp(6), gaugeCy + dp(6), textPaint);

            textPaint.setColor(Color.parseColor("#0284C7"));
            textPaint.setTextSize(sp(11));
            canvas.drawText("°C", leftCx + dp(16), gaugeCy - dp(4), textPaint);

            textPaint.setColor(Color.parseColor("#64748B"));
            textPaint.setTextSize(sp(9));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("PHONE TEMP", leftCx, gaugeCy + dp(22), textPaint);

            // 3.2 右仪表：散热风扇转速
            float rightCx = w * 0.74f;
            drawGauge(canvas, rightCx, gaugeCy, gaugeR, 45, -220, Math.min(1.0f, fanRpm / 7500.0f));

            textPaint.setColor(Color.parseColor("#0A192F"));
            textPaint.setTextSize(sp(26));
            textPaint.setTypeface(Typeface.DEFAULT);
            canvas.drawText(String.format("%.1f", fanRpm / 1000.0f), rightCx - dp(6), gaugeCy + dp(6), textPaint);

            textPaint.setColor(Color.parseColor("#0284C7"));
            textPaint.setTextSize(sp(11));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("k", rightCx + dp(16), gaugeCy - dp(4), textPaint);

            textPaint.setColor(Color.parseColor("#64748B"));
            textPaint.setTextSize(sp(9));
            canvas.drawText("FAN SPEED", rightCx, gaugeCy + dp(22), textPaint);

            // 4. 水滴拟态液态玻璃旋钮系统
            float knobCx = w / 2.0f;
            float knobCy = dp(455);
            float knobR = dp(50);

            RectF knobBase = new RectF(knobCx - dp(120), knobCy - dp(120), knobCx + dp(120), knobCy + dp(120));
            drawGlassPanel(canvas, knobBase, dp(120));

            String[] titles = {"OFF", "1 挡", "2 挡", "3 挡", "MAX", "AUTO"};
            String[] descs = {"关闭", "轻音", "日常", "电竞", "超频", "智冷"};
            float[] angles = {140f, 180f, 220f, 270f, 320f, 40f};

            for (int i = 0; i < 6; i++) {
                double rad = Math.toRadians(angles[i]);
                float lx = knobCx + (float) (dp(88) * Math.cos(rad));
                float ly = knobCy + (float) (dp(88) * Math.sin(rad));

                boolean isSel = (i == currentLevel);

                if (isSel) {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(Color.parseColor("#4000A0E9"));
                    canvas.drawCircle(lx, ly - dp(16), dp(8), paint);
                    paint.setColor(Color.parseColor("#00A0E9"));
                    canvas.drawCircle(lx, ly - dp(16), dp(4.5f), paint);
                    paint.setColor(Color.WHITE);
                    canvas.drawCircle(lx - dp(1), ly - dp(17), dp(1.5f), paint);
                } else {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(Color.parseColor("#B0CBD5E1"));
                    canvas.drawCircle(lx, ly - dp(16), dp(2.8f), paint);
                }

                textPaint.setColor(isSel ? Color.parseColor("#0284C7") : Color.parseColor("#64748B"));
                textPaint.setTextSize(isSel ? sp(12f) : sp(10.5f));
                textPaint.setTypeface(isSel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                canvas.drawText(titles[i], lx, ly - dp(2), textPaint);

                textPaint.setColor(isSel ? Color.parseColor("#0369A1") : Color.parseColor("#94A3B8"));
                textPaint.setTextSize(sp(8));
                textPaint.setTypeface(Typeface.DEFAULT);
                canvas.drawText(descs[i], lx, ly + dp(10), textPaint);
            }

            drawGlassOrbKnob(canvas, knobCx, knobCy, knobR, angles[currentLevel]);

            textPaint.setColor(Color.parseColor("#0F172A"));
            textPaint.setTextSize(sp(19));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText(titles[currentLevel].replace(" 挡", ""), knobCx, knobCy - dp(2), textPaint);

            textPaint.setColor(Color.parseColor("#0284C7"));
            textPaint.setTextSize(sp(8.5f));
            textPaint.setTypeface(Typeface.DEFAULT);
            canvas.drawText("LEVEL", knobCx, knobCy + dp(12), textPaint);

            RectF capRect = new RectF(knobCx - dp(70), knobCy + dp(74), knobCx + dp(70), knobCy + dp(98));
            drawGlassPanel(canvas, capRect, dp(12));

            String[] hapticNames = {"静音", "轻柔", "标准", "强劲"};
            textPaint.setColor(Color.parseColor("#0284C7"));
            textPaint.setTextSize(sp(9));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("⚙ 阻尼震感 · " + hapticNames[hapticStrength], knobCx, knobCy + dp(89), textPaint);

            // 5. 底部 RGB 开关
            RectF btn = new RectF(dp(20), h - dp(90), w - dp(20), h - dp(40));
            drawGlassPanel(canvas, btn, dp(16));

            if (isAmbientOn) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.parseColor("#5038BDF8"));
                canvas.drawCircle(dp(44), h - dp(65), dp(10), paint);
                paint.setColor(Color.parseColor("#0284C7"));
                canvas.drawCircle(dp(44), h - dp(65), dp(6), paint);
                paint.setColor(Color.WHITE);
                canvas.drawCircle(dp(42.5f), h - dp(66.5f), dp(2), paint);
            } else {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.parseColor("#CBD5E1"));
                canvas.drawCircle(dp(44), h - dp(65), dp(6), paint);
            }

            textPaint.setTextAlign(Paint.Align.LEFT);
            textPaint.setColor(Color.parseColor("#0F172A"));
            textPaint.setTextSize(sp(12));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("AMBIENT LIGHT / RGB氛围灯效", dp(62), h - dp(68), textPaint);

            textPaint.setColor(Color.parseColor("#64748B"));
            textPaint.setTextSize(sp(9.5f));
            textPaint.setTypeface(Typeface.DEFAULT);
            canvas.drawText(isAmbientOn ? "极光冰蓝呼吸流光生效中" : "已关闭灯效进入静默节电", dp(62), h - dp(54), textPaint);

            // 6. 弹出的震感设置窗口
            if (isHapticDialogVisible) {
                drawHapticSettingDialog(canvas, w, h);
            }

            // 7. 【动态“正在连接设备.”无任何敏感提示词的雷达检索窗口】
            if (isSearchingCooler) {
                drawBleSearchOverlay(canvas, w, h);
            }
        }

        /**
         * 动态正在连接设备雷达动画窗口（无任何品牌或型号词）
         */
        private void drawBleSearchOverlay(Canvas canvas, float w, float h) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor("#550A192F"));
            canvas.drawRect(0, 0, w, h, paint);

            float dw = w - dp(48);
            float dh = dp(270);
            float dx = dp(24);
            float dy = (h - dh) / 2.0f;
            RectF dlgRect = new RectF(dx, dy, dx + dw, dy + dh);
            drawGlassPanel(canvas, dlgRect, dp(26));

            // 雷达脉冲扩散波纹动画计算
            animTick += 0.04f;
            float pulseR1 = dp(28) + (float)(Math.sin(animTick) * dp(6));
            float pulseR2 = dp(46) + (float)(Math.cos(animTick) * dp(8));

            float radarCx = dlgRect.centerX();
            float radarCy = dy + dp(78);

            // 扩散双层呼吸光环
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(Color.parseColor("#2000A0E9"));
            paint.setStrokeWidth(dp(2f));
            canvas.drawCircle(radarCx, radarCy, pulseR2, paint);

            paint.setColor(Color.parseColor("#4500A0E9"));
            paint.setStrokeWidth(dp(1.5f));
            canvas.drawCircle(radarCx, radarCy, pulseR1, paint);

            // 中心天青冰蓝晶莹光珠
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor("#00A0E9"));
            canvas.drawCircle(radarCx, radarCy, dp(13), paint);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(radarCx - dp(3.5f), radarCy - dp(3.5f), dp(4f), paint);

            // 动态点号流转动画（正在连接设备. -> 正在连接设备.. -> 正在连接设备...）
            long now = System.currentTimeMillis();
            if (now - lastDotTime > 450) {
                dotCount = (dotCount % 3) + 1;
                lastDotTime = now;
            }
            StringBuilder dots = new StringBuilder();
            for (int i = 0; i < dotCount; i++) dots.append(".");

            String displayText;
            if (scanStatus.startsWith("正在连接设备")) {
                displayText = "正在连接设备" + dots.toString();
            } else {
                displayText = scanStatus;
            }

            // 核心动画主文本
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(Color.parseColor("#0F2840"));
            textPaint.setTextSize(sp(17));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText(displayText, radarCx, dy + dp(148), textPaint);

            textPaint.setColor(Color.parseColor("#64748B"));
            textPaint.setTextSize(sp(11));
            textPaint.setTypeface(Typeface.DEFAULT);
            canvas.drawText("请保持设备处于开启状态并靠近手机", radarCx, dy + dp(174), textPaint);

            // 底部操作胶囊：【重新连接】与【进入中控】
            float btnW = (dw - dp(48)) / 2.0f;
            float btnY = dy + dh - dp(54);

            RectF retryBtn = new RectF(dx + dp(18), btnY, dx + dp(18) + btnW, btnY + dp(38));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor("#E0F2FE"));
            canvas.drawRoundRect(retryBtn, dp(14), dp(14), paint);
            textPaint.setColor(Color.parseColor("#0284C7"));
            textPaint.setTextSize(sp(11.5f));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("重新连接", retryBtn.centerX(), retryBtn.centerY() + dp(4), textPaint);

            RectF demoBtn = new RectF(dx + dp(30) + btnW, btnY, dx + dw - dp(18), btnY + dp(38));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor("#00A0E9"));
            canvas.drawRoundRect(demoBtn, dp(14), dp(14), paint);
            textPaint.setColor(Color.WHITE);
            canvas.drawText("直接进入", demoBtn.centerX(), demoBtn.centerY() + dp(4), textPaint);

            // 驱动逐帧雷达波纹与点号平滑刷新
            postInvalidateOnAnimation();
        }

        private void drawHapticSettingDialog(Canvas canvas, float w, float h) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor("#4D0A192F"));
            canvas.drawRect(0, 0, w, h, paint);

            float dw = w - dp(60);
            float dh = dp(230);
            float dx = dp(30);
            float dy = (h - dh) / 2.0f;
            RectF dlgRect = new RectF(dx, dy, dx + dw, dy + dh);

            drawGlassPanel(canvas, dlgRect, dp(24));

            textPaint.setTextAlign(Paint.Align.LEFT);
            textPaint.setColor(Color.parseColor("#0F2840"));
            textPaint.setTextSize(sp(15));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("HAPTIC INTENSITY / 阻尼震感调节", dx + dp(20), dy + dp(36), textPaint);

            textPaint.setColor(Color.parseColor("#64748B"));
            textPaint.setTextSize(sp(10));
            textPaint.setTypeface(Typeface.DEFAULT);
            canvas.drawText("切换旋钮档位时的触控马达震动反馈", dx + dp(20), dy + dp(54), textPaint);

            String[] levels = {"关断", "轻柔", "标准", "强劲"};
            String[] subTexts = {"0% 无震感", "30% 细微", "70% 咔哒", "100% 重度"};
            float itemW = (dw - dp(50)) / 4.0f;
            float itemH = dp(75);
            float itemY = dy + dp(76);

            for (int i = 0; i < 4; i++) {
                float itemX = dx + dp(20) + i * (itemW + dp(3.3f));
                RectF itemRect = new RectF(itemX, itemY, itemX + itemW, itemY + itemH);

                boolean isSelected = (hapticStrength == i);

                if (isSelected) {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(Color.parseColor("#DDF0F9FF"));
                    canvas.drawRoundRect(itemRect, dp(14), dp(14), paint);

                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(Color.parseColor("#0284C7"));
                    paint.setStrokeWidth(dp(2));
                    canvas.drawRoundRect(itemRect, dp(14), dp(14), paint);
                } else {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(Color.parseColor("#50FFFFFF"));
                    canvas.drawRoundRect(itemRect, dp(14), dp(14), paint);

                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(Color.parseColor("#40CBD5E1"));
                    paint.setStrokeWidth(dp(1));
                    canvas.drawRoundRect(itemRect, dp(14), dp(14), paint);
                }

                textPaint.setTextAlign(Paint.Align.CENTER);
                textPaint.setColor(isSelected ? Color.parseColor("#0284C7") : Color.parseColor("#0F172A"));
                textPaint.setTextSize(sp(13));
                textPaint.setTypeface(Typeface.DEFAULT_BOLD);
                canvas.drawText(levels[i], itemRect.centerX(), itemY + dp(30), textPaint);

                textPaint.setColor(isSelected ? Color.parseColor("#0369A1") : Color.parseColor("#94A3B8"));
                textPaint.setTextSize(sp(8));
                textPaint.setTypeface(Typeface.DEFAULT);
                canvas.drawText(subTexts[i], itemRect.centerX(), itemY + dp(50), textPaint);
            }

            RectF closeBtn = new RectF(dx + dp(20), dy + dh - dp(52), dx + dw - dp(20), dy + dh - dp(18));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor("#00A0E9"));
            canvas.drawRoundRect(closeBtn, dp(14), dp(14), paint);

            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(sp(12));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("确定并保存触感", closeBtn.centerX(), closeBtn.centerY() + dp(4), textPaint);
        }

        private void drawGlassPanel(Canvas canvas, RectF rect, float radius) {
            Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            Shader fillShader = new LinearGradient(
                    rect.left, rect.top, rect.right, rect.bottom,
                    new int[]{Color.parseColor("#E6FFFFFF"), Color.parseColor("#BAF0F9FF"), Color.parseColor("#CFFFFFFF")},
                    new float[]{0f, 0.55f, 1f},
                    Shader.TileMode.CLAMP
            );
            fillPaint.setShader(fillShader);
            fillPaint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(rect, radius, radius, fillPaint);

            Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            Shader sheenShader = new LinearGradient(
                    rect.left, rect.top, rect.left, rect.top + rect.height() * 0.45f,
                    new int[]{Color.parseColor("#90FFFFFF"), Color.parseColor("#00FFFFFF")},
                    null,
                    Shader.TileMode.CLAMP
            );
            sheenPaint.setShader(sheenShader);
            sheenPaint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(rect, radius, radius, sheenPaint);

            Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            Shader strokeShader = new LinearGradient(
                    rect.left, rect.top, rect.right, rect.bottom,
                    new int[]{Color.parseColor("#FFFFFFFF"), Color.parseColor("#60BAE6FD"), Color.parseColor("#20FFFFFF")},
                    new float[]{0f, 0.4f, 1f},
                    Shader.TileMode.CLAMP
            );
            strokePaint.setShader(strokeShader);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(1.2f));
            canvas.drawRoundRect(rect, radius, radius, strokePaint);
        }

        private void drawGlassOrbKnob(Canvas canvas, float cx, float cy, float r, float angle) {
            Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(dp(3f));
            Shader ringShader = new SweepGradient(
                    cx, cy,
                    new int[]{Color.WHITE, Color.parseColor("#BAE6FD"), Color.WHITE, Color.parseColor("#CBD5E1"), Color.WHITE},
                    null
            );
            ringPaint.setShader(ringShader);
            canvas.drawCircle(cx, cy, r, ringPaint);

            Paint orbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            Shader orbShader = new RadialGradient(
                    cx - r * 0.3f, cy - r * 0.35f, r * 1.2f,
                    new int[]{Color.WHITE, Color.parseColor("#E0F2FE"), Color.parseColor("#F8FAFC")},
                    new float[]{0f, 0.65f, 1f},
                    Shader.TileMode.CLAMP
            );
            orbPaint.setShader(orbShader);
            orbPaint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx, cy, r - dp(3.5f), orbPaint);

            double rad = Math.toRadians(angle);
            float startX = cx + (float) (dp(16) * Math.cos(rad));
            float startY = cy + (float) (dp(16) * Math.sin(rad));
            float endX = cx + (float) ((r - dp(8)) * Math.cos(rad));
            float endY = cy + (float) ((r - dp(8)) * Math.sin(rad));

            Paint ptrPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            ptrPaint.setStyle(Paint.Style.STROKE);
            ptrPaint.setColor(Color.parseColor("#00A0E9"));
            ptrPaint.setStrokeWidth(dp(4));
            ptrPaint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(startX, startY, endX, endY, ptrPaint);

            Paint tipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            tipPaint.setStyle(Paint.Style.FILL);
            tipPaint.setColor(Color.WHITE);
            canvas.drawCircle(endX, endY, dp(1.5f), tipPaint);
        }

        private void drawGauge(Canvas canvas, float cx, float cy, float r, float startAngle, float sweep, float progress) {
            RectF rect = new RectF(cx - r, cy - r, cx + r, cy + r);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setShader(null);

            paint.setColor(Color.parseColor("#30CBD5E1"));
            paint.setStrokeWidth(dp(6.5f));
            canvas.drawArc(rect, startAngle, sweep, false, paint);

            Paint progPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            progPaint.setStyle(Paint.Style.STROKE);
            progPaint.setStrokeCap(Paint.Cap.ROUND);
            progPaint.setStrokeWidth(dp(6.5f));
            Shader progShader = new LinearGradient(
                    rect.left, rect.top, rect.right, rect.bottom,
                    new int[]{Color.parseColor("#38BDF8"), Color.parseColor("#0284C7")},
                    null,
                    Shader.TileMode.CLAMP
            );
            progPaint.setShader(progShader);
            canvas.drawArc(rect, startAngle, sweep * progress, false, progPaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float w = getWidth();
            float h = getHeight();
            float knobCx = w / 2.0f;
            float knobCy = dp(455);

            // 1. 正在连接设备窗口交互
            if (isSearchingCooler) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    float dw = w - dp(48);
                    float dh = dp(270);
                    float dx = dp(24);
                    float dy = (h - dh) / 2.0f;
                    float btnW = (dw - dp(48)) / 2.0f;
                    float btnY = dy + dh - dp(54);

                    // 点击“重新连接”
                    RectF retryBtn = new RectF(dx + dp(18), btnY, dx + dp(18) + btnW, btnY + dp(38));
                    if (retryBtn.contains(event.getX(), event.getY())) {
                        triggerHaptic(false);
                        if (getContext() instanceof MainActivity) {
                            ((MainActivity) getContext()).startCoolerScan();
                        }
                        return true;
                    }

                    // 点击“直接进入”
                    RectF demoBtn = new RectF(dx + dp(30) + btnW, btnY, dx + dw - dp(18), btnY + dp(38));
                    if (demoBtn.contains(event.getX(), event.getY())) {
                        triggerHaptic(true);
                        connectedDeviceName = "COOLER UNIT";
                        isSearchingCooler = false;
                        invalidate();
                        return true;
                    }
                }
                return true;
            }

            // 2. 震感调节窗口交互
            if (isHapticDialogVisible) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    float dw = w - dp(60);
                    float dh = dp(230);
                    float dx = dp(30);
                    float dy = (h - dh) / 2.0f;

                    if (event.getX() < dx || event.getX() > dx + dw || event.getY() < dy || event.getY() > dy + dh) {
                        isHapticDialogVisible = false;
                        invalidate();
                        return true;
                    }

                    RectF closeBtn = new RectF(dx + dp(20), dy + dh - dp(52), dx + dw - dp(20), dy + dh - dp(18));
                    if (closeBtn.contains(event.getX(), event.getY())) {
                        isHapticDialogVisible = false;
                        triggerHaptic(false);
                        invalidate();
                        return true;
                    }

                    float itemW = (dw - dp(50)) / 4.0f;
                    float itemH = dp(75);
                    float itemY = dy + dp(76);

                    for (int i = 0; i < 4; i++) {
                        float itemX = dx + dp(20) + i * (itemW + dp(3.3f));
                        RectF itemRect = new RectF(itemX, itemY, itemX + itemW, itemY + itemH);
                        if (itemRect.contains(event.getX(), event.getY())) {
                            hapticStrength = i;
                            triggerHaptic(false);
                            invalidate();
                            return true;
                        }
                    }
                }
                return true;
            }

            // 3. 点击“触控阻尼胶囊”
            RectF capRect = new RectF(knobCx - dp(70), knobCy + dp(74), knobCx + dp(70), knobCy + dp(98));
            if (event.getAction() == MotionEvent.ACTION_DOWN && capRect.contains(event.getX(), event.getY())) {
                isHapticDialogVisible = true;
                triggerHaptic(false);
                invalidate();
                return true;
            }

            // 4. 点击底部 RGB 开关
            if (event.getAction() == MotionEvent.ACTION_DOWN && event.getY() > h - dp(90)) {
                isAmbientOn = !isAmbientOn;
                triggerHaptic(false);
                invalidate();
                return true;
            }

            // 5. 旋钮滑动选档
            float dx = event.getX() - knobCx;
            float dy = event.getY() - knobCy;
            if (Math.sqrt(dx * dx + dy * dy) <= dp(130)) {
                double deg = Math.toDegrees(Math.atan2(dy, dx));
                if (deg < 0) deg += 360;

                float[] angles = {140f, 180f, 220f, 270f, 320f, 40f};
                int best = currentLevel;
                double minDiff = Double.MAX_VALUE;
                for (int i = 0; i < 6; i++) {
                    double diff = Math.abs(angles[i] - deg);
                    diff = Math.min(diff, 360 - diff);
                    if (diff < minDiff) {
                        minDiff = diff;
                        best = i;
                    }
                }

                if (best != currentLevel) {
                    currentLevel = best;
                    triggerHaptic(currentLevel == 0 || currentLevel == 4);
                    int[] rpms = {0, 2500, 3800, 5400, 7200, 4200};
                    fanRpm = rpms[currentLevel];
                    invalidate();
                }
                return true;
            }
            return super.onTouchEvent(event);
        }

        public void triggerHaptic(boolean isHeavy) {
            if (hapticStrength == 0 || vibrator == null) return;

            int duration;
            int amplitude;

            switch (hapticStrength) {
                case 1:
                    duration = 8;
                    amplitude = 60;
                    break;
                case 3:
                    duration = 24;
                    amplitude = 255;
                    break;
                case 2:
                default:
                    duration = 14;
                    amplitude = 150;
                    break;
            }

            if (isHeavy) {
                duration = Math.min(35, duration + 10);
                amplitude = Math.min(255, amplitude + 40);
            }

            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude));
            } else {
                vibrator.vibrate(duration);
            }
        }

        private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
        private float sp(float v) { return v * getResources().getDisplayMetrics().scaledDensity; }
    }
}
