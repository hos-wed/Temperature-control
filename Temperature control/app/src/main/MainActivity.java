package com.example.flydigicooler;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private DashboardView dashboardView;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;
    private BluetoothGatt connectedGatt;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isScanning = false;

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                int tempRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
                if (tempRaw > 0 && dashboardView != null) {
                    dashboardView.phoneTemp = tempRaw / 10.0f;
                    dashboardView.postInvalidate();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            throwable.printStackTrace();
            mainHandler.post(() -> {
                if (dashboardView != null) {
                    dashboardView.scanStatus = "通信重连中...";
                    dashboardView.postInvalidate();
                }
            });
        });

        dashboardView = new DashboardView(this);
        setContentView(dashboardView);

        try {
            registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        } catch (Throwable ignored) {}

        initBleAndRequestPermissions();
    }

    private void initBleAndRequestPermissions() {
        try {
            BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            bluetoothAdapter = manager != null ? manager.getAdapter() : BluetoothAdapter.getDefaultAdapter();
        } catch (Throwable e) {
            bluetoothAdapter = null;
        }

        if (bluetoothAdapter == null) {
            dashboardView.scanStatus = "设备不支持蓝牙服务";
            dashboardView.postInvalidate();
            return;
        }

        if (!hasRequiredPermissions()) {
            requestRequiredPermissions();
            return;
        }

        startCoolerScan();
    }

    private boolean hasRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                   checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        } else {
            return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                   checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestRequiredPermissions() {
        List<String> list = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(Manifest.permission.BLUETOOTH_SCAN);
            list.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        list.add(Manifest.permission.ACCESS_FINE_LOCATION);
        requestPermissions(list.toArray(new String[0]), 101);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101) {
            if (hasRequiredPermissions()) {
                startCoolerScan();
            } else {
                dashboardView.scanStatus = "请授予蓝牙权限以连接设备";
                dashboardView.postInvalidate();
            }
        }
    }

    public synchronized void startCoolerScan() {
        stopCoolerScan();
        disconnectAndCloseGatt();

        if (!hasRequiredPermissions()) {
            requestRequiredPermissions();
            return;
        }

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            dashboardView.scanStatus = "请开启系统蓝牙后重试";
            dashboardView.postInvalidate();
            return;
        }

        try {
            bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        } catch (Throwable ignored) {
            bleScanner = null;
        }

        if (bleScanner == null) {
            dashboardView.scanStatus = "蓝牙扫描服务暂不可用";
            dashboardView.postInvalidate();
            return;
        }

        isScanning = true;
        dashboardView.isSearchingCooler = true; // 强制保持在搜索界面
        dashboardView.scanStatus = "正在连接设备.";
        dashboardView.postInvalidate();

        try {
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();
            bleScanner.startScan(null, settings, scanCallback);
        } catch (Throwable t) {
            isScanning = false;
            dashboardView.scanStatus = "扫描受限，请重试";
            dashboardView.postInvalidate();
            return;
        }
    }

    private synchronized void stopCoolerScan() {
        if (isScanning && bleScanner != null) {
            try {
                bleScanner.stopScan(scanCallback);
            } catch (Throwable ignored) {}
            isScanning = false;
        }
    }

    private synchronized void disconnectAndCloseGatt() {
        if (connectedGatt != null) {
            try {
                connectedGatt.disconnect();
                connectedGatt.close();
            } catch (Throwable ignored) {}
            connectedGatt = null;
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            if (result == null || result.getDevice() == null) return;

            final BluetoothDevice device = result.getDevice();
            String name = null;

            try {
                if (hasRequiredPermissions()) {
                    name = device.getName();
                }
            } catch (Throwable ignored) {}

            if (name == null && result.getScanRecord() != null) {
                try {
                    name = result.getScanRecord().getDeviceName();
                } catch (Throwable ignored) {}
            }

            if (name == null || name.trim().isEmpty()) return;

            String upper = name.toUpperCase(Locale.ROOT);
            if (upper.contains("B6X") || upper.contains("B7X") || upper.contains("B8X") || upper.contains("B9X")
                    || upper.contains("FLYDIGI") || upper.contains("B6") || upper.contains("B7")) {
                stopCoolerScan();
                connectToCooler(device);
            }
        }
    };

    private void connectToCooler(BluetoothDevice device) {
        mainHandler.post(() -> {
            dashboardView.scanStatus = "正在建立通信连接...";
            dashboardView.postInvalidate();
        });

        mainHandler.post(() -> {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    connectedGatt = device.connectGatt(getApplicationContext(), false, gattCallback, BluetoothDevice.TRANSPORT_LE);
                } else {
                    connectedGatt = device.connectGatt(getApplicationContext(), false, gattCallback);
                }
            } catch (Throwable t) {
                dashboardView.scanStatus = "通信连接失败，点击重试";
                dashboardView.postInvalidate();
            }
        });
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, int status, int newState) {
            mainHandler.post(() -> {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    dashboardView.connectedDeviceName = "COOLER UNIT";
                    dashboardView.isSearchingCooler = false; // 只有连上硬件才放行进入功能区
                    dashboardView.triggerHaptic(true);
                    dashboardView.postInvalidate();
                    try {
                        Toast.makeText(getApplicationContext(), "硬件设备已连接", Toast.LENGTH_SHORT).show();
                    } catch (Throwable ignored) {}
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    disconnectAndCloseGatt();
                    dashboardView.isSearchingCooler = true; // 断开后强制退回搜索界面
                    dashboardView.scanStatus = "设备断开，正在重新搜索...";
                    dashboardView.postInvalidate();
                    startCoolerScan();
                }
            });
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopCoolerScan();
        disconnectAndCloseGatt();
        mainHandler.removeCallbacksAndMessages(null);
        try {
            unregisterReceiver(batteryReceiver);
        } catch (Throwable ignored) {}
    }

    public static class DashboardView extends View {
        public float phoneTemp = 34.0f;
        public int fanRpm = 5400;
        public int currentLevel = 3;
        public boolean isAmbientOn = true;

        public boolean isSearchingCooler = true; // 默认启动处于搜索状态
        public String scanStatus = "正在连接设备.";
        public String connectedDeviceName = "未连接";

        private float animTick = 0f;
        private int dotCount = 1;
        private long lastDotTime = 0;

        public int hapticStrength = 2;
        public boolean isHapticDialogVisible = false;

        // RGB 自定义调节窗口
        public boolean isRgbDialogVisible = false;
        public int rgbRed = 0;
        public int rgbGreen = 160;
        public int rgbBlue = 233;

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

            // 如果未连接设备，锁死在纯白搜索页
            if (isSearchingCooler) {
                drawBleSearchOverlay(canvas, w, h);
                return;
            }

            // 温感系统实时波动渲染
            animTick += 0.05f;
            float tempFluctuation = (float) Math.sin(animTick) * 0.2f;

            Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            Shader bgShader = new LinearGradient(
                    0, 0, w, h,
                    new int[]{Color.parseColor("#EBF3FA"), Color.parseColor("#F4F8FC"), Color.parseColor("#E5EEF8")},
                    new float[]{0f, 0.5f, 1f},
                    Shader.TileMode.CLAMP
            );
            bgPaint.setShader(bgShader);
            canvas.drawRect(0, 0, w, h, bgPaint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(80, rgbRed, rgbGreen, rgbBlue));
            canvas.drawCircle(w * 0.2f, dp(150), dp(130), paint);
            paint.setColor(Color.argb(50, rgbRed, rgbGreen, rgbBlue));
            canvas.drawCircle(w * 0.85f, dp(460), dp(160), paint);

            textPaint.setColor(Color.parseColor("#0F2840"));
            textPaint.setTextSize(sp(18));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("CLIMATE CONTROL", dp(24), dp(48), textPaint);

            textPaint.setColor(Color.parseColor("#5A7B9A"));
            textPaint.setTextSize(sp(11));
            textPaint.setTypeface(Typeface.DEFAULT);
            canvas.drawText("DEVICE: " + connectedDeviceName + " · 极冷座舱", dp(24), dp(68), textPaint);

            RectF card = new RectF(dp(20), dp(84), w - dp(20), dp(284));
            drawGlassPanel(canvas, card, dp(22));

            paint.setShader(null);
            paint.setColor(Color.parseColor("#30FFFFFF"));
            paint.setStrokeWidth(dp(2f));
            canvas.drawLine(w / 2.0f, dp(100), w / 2.0f, dp(268), paint);

            float displayTemp = phoneTemp + tempFluctuation;
            float leftCx = w * 0.26f;
            float gaugeCy = dp(180);
            float gaugeR = dp(46);
            drawGauge(canvas, leftCx, gaugeCy, gaugeR, 135, 220, Math.min(1.0f, displayTemp / 60.0f));

            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(Color.parseColor("#0A192F"));
            textPaint.setTextSize(sp(26));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText(String.format(Locale.ROOT, "%.1f", displayTemp), leftCx - dp(6), gaugeCy + dp(6), textPaint);

            textPaint.setColor(Color.rgb(rgbRed, rgbGreen, rgbBlue));
            textPaint.setTextSize(sp(11));
            canvas.drawText("°C", leftCx + dp(18), gaugeCy - dp(4), textPaint);

            textPaint.setColor(Color.parseColor("#64748B"));
            textPaint.setTextSize(sp(9));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("PHONE TEMP", leftCx, gaugeCy + dp(22), textPaint);

            float rightCx = w * 0.74f;
            drawGauge(canvas, rightCx, gaugeCy, gaugeR, 45, -220, Math.min(1.0f, fanRpm / 7500.0f));

            textPaint.setColor(Color.parseColor("#0A192F"));
            textPaint.setTextSize(sp(26));
            textPaint.setTypeface(Typeface.DEFAULT);
            canvas.drawText(String.format(Locale.ROOT, "%.1f", fanRpm / 1000.0f), rightCx - dp(6), gaugeCy + dp(6), textPaint);

            textPaint.setColor(Color.rgb(rgbRed, rgbGreen, rgbBlue));
            textPaint.setTextSize(sp(11));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("k", rightCx + dp(16), gaugeCy - dp(4), textPaint);

            textPaint.setColor(Color.parseColor("#64748B"));
            textPaint.setTextSize(sp(9));
            canvas.drawText("FAN SPEED", rightCx, gaugeCy + dp(22), textPaint);

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
                    paint.setColor(Color.argb(100, rgbRed, rgbGreen, rgbBlue));
                    canvas.drawCircle(lx, ly - dp(16), dp(8), paint);
                    paint.setColor(Color.rgb(rgbRed, rgbGreen, rgbBlue));
                    canvas.drawCircle(lx, ly - dp(16), dp(4.5f), paint);
                    paint.setColor(Color.WHITE);
                    canvas.drawCircle(lx - dp(1), ly - dp(17), dp(1.5f), paint);
                } else {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(Color.parseColor("#B0CBD5E1"));
                    canvas.drawCircle(lx, ly - dp(16), dp(2.8f), paint);
                }

                textPaint.setColor(isSel ? Color.rgb(rgbRed, rgbGreen, rgbBlue) : Color.parseColor("#64748B"));
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

            textPaint.setColor(Color.rgb(rgbRed, rgbGreen, rgbBlue));
            textPaint.setTextSize(sp(8.5f));
            textPaint.setTypeface(Typeface.DEFAULT);
            canvas.drawText("LEVEL", knobCx, knobCy + dp(12), textPaint);

            RectF capRect = new RectF(knobCx - dp(70), knobCy + dp(74), knobCx + dp(70), knobCy + dp(98));
            drawGlassPanel(canvas, capRect, dp(12));

            String[] hapticNames = {"静音", "轻柔", "标准", "强劲"};
            textPaint.setColor(Color.rgb(rgbRed, rgbGreen, rgbBlue));
            textPaint.setTextSize(sp(9));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("⚙ 阻尼震感 · " + hapticNames[hapticStrength], knobCx, knobCy + dp(89), textPaint);

            RectF btn = new RectF(dp(20), h - dp(90), w - dp(20), h - dp(40));
            drawGlassPanel(canvas, btn, dp(16));

            if (isAmbientOn) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.argb(90, rgbRed, rgbGreen, rgbBlue));
                canvas.drawCircle(dp(44), h - dp(65), dp(10), paint);
                paint.setColor(Color.rgb(rgbRed, rgbGreen, rgbBlue));
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
            canvas.drawText("AMBIENT LIGHT / RGB氛围灯效 (点击调色)", dp(62), h - dp(68), textPaint);

            textPaint.setColor(Color.parseColor("#64748B"));
            textPaint.setTextSize(sp(9.5f));
            textPaint.setTypeface(Typeface.DEFAULT);
            canvas.drawText(isAmbientOn ? "自定义RGB流光色温生效中" : "已关闭灯效进入静默节电", dp(62), h - dp(54), textPaint);

            if (isHapticDialogVisible) {
                drawHapticSettingDialog(canvas, w, h);
            }

            if (isRgbDialogVisible) {
                drawRgbSettingDialog(canvas, w, h);
            }

            postInvalidateOnAnimation();
        }

        private void drawBleSearchOverlay(Canvas canvas, float w, float h) {
            canvas.drawColor(Color.WHITE);

            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(Color.parseColor("#0F2840"));
            textPaint.setTextSize(sp(20));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("TEMPERATURE CONTROL", w / 2.0f, dp(120), textPaint);

            textPaint.setColor(Color.parseColor("#64748B"));
            textPaint.setTextSize(sp(11));
            textPaint.setTypeface(Typeface.DEFAULT);
            canvas.drawText("极冷座舱 · 智能温控管理系统", w / 2.0f, dp(144), textPaint);

            float dw = w - dp(48);
            float dh = dp(280);
            float dx = dp(24);
            float dy = dp(190);
            RectF dlgRect = new RectF(dx, dy, dx + dw, dy + dh);
            drawGlassPanel(canvas, dlgRect, dp(26));

            animTick += 0.04f;
            float pulseR1 = dp(28) + (float)(Math.sin(animTick) * dp(6));
            float pulseR2 = dp(46) + (float)(Math.cos(animTick) * dp(8));

            float radarCx = dlgRect.centerX();
            float radarCy = dy + dp(78);

            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(Color.parseColor("#2000A0E9"));
            paint.setStrokeWidth(dp(2f));
            canvas.drawCircle(radarCx, radarCy, pulseR2, paint);

            paint.setColor(Color.parseColor("#4500A0E9"));
            paint.setStrokeWidth(dp(1.5f));
            canvas.drawCircle(radarCx, radarCy, pulseR1, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor("#00A0E9"));
            canvas.drawCircle(radarCx, radarCy, dp(13), paint);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(radarCx - dp(3.5f), radarCy - dp(3.5f), dp(4f), paint);

            long now = System.currentTimeMillis();
            if (now - lastDotTime > 450) {
                dotCount = (dotCount % 3) + 1;
                lastDotTime = now;
            }
            StringBuilder dots = new StringBuilder();
            for (int i = 0; i < dotCount; i++) dots.append(".");

            String displayText = "正在连接设备" + dots.toString();

            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(Color.parseColor("#0F2840"));
            textPaint.setTextSize(sp(17));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText(displayText, radarCx, dy + dp(148), textPaint);

            textPaint.setColor(Color.parseColor("#64748B"));
            textPaint.setTextSize(sp(11));
            textPaint.setTypeface(Typeface.DEFAULT);
            canvas.drawText("请保持硬件设备开启并靠近手机", radarCx, dy + dp(174), textPaint);

            float btnW = dw - dp(48);
            float btnY = dy + dh - dp(56);
            RectF retryBtn = new RectF(dx + dp(24), btnY, dx + dp(24) + btnW, btnY + dp(40));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor("#00A0E9"));
            canvas.drawRoundRect(retryBtn, dp(14), dp(14), paint);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(sp(12));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("重新扫描并连接", retryBtn.centerX(), retryBtn.centerY() + dp(4), textPaint);

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

            String[] levels = {"关断", "轻柔", "标准", "强劲"};
            String[] subTexts = {"0% 无", "30% 细微", "70% 咔哒", "100% 重度"};
            float itemW = (dw - dp(50)) / 4.0f;
            float itemH = dp(75);
            float itemY = dy + dp(76);

            for (int i = 0; i < 4; i++) {
                float itemX = dx + dp(20) + i * (itemW + dp(3.3f));
                RectF itemRect = new RectF(itemX, itemY, itemX + itemW, itemY + itemH);
                boolean isSelected = (hapticStrength == i);

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(isSelected ? Color.parseColor("#DDF0F9FF") : Color.parseColor("#50FFFFFF"));
                canvas.drawRoundRect(itemRect, dp(14), dp(14), paint);

                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(isSelected ? Color.rgb(rgbRed, rgbGreen, rgbBlue) : Color.parseColor("#40CBD5E1"));
                paint.setStrokeWidth(isSelected ? dp(2) : dp(1));
                canvas.drawRoundRect(itemRect, dp(14), dp(14), paint);

                textPaint.setTextAlign(Paint.Align.CENTER);
                textPaint.setColor(isSelected ? Color.rgb(rgbRed, rgbGreen, rgbBlue) : Color.parseColor("#0F172A"));
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
            paint.setColor(Color.rgb(rgbRed, rgbGreen, rgbBlue));
            canvas.drawRoundRect(closeBtn, dp(14), dp(14), paint);

            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(sp(12));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("确定并保存", closeBtn.centerX(), closeBtn.centerY() + dp(4), textPaint);
        }

        private void drawRgbSettingDialog(Canvas canvas, float w, float h) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor("#4D0A192F"));
            canvas.drawRect(0, 0, w, h, paint);

            float dw = w - dp(60);
            float dh = dp(270);
            float dx = dp(30);
            float dy = (h - dh) / 2.0f;
            RectF dlgRect = new RectF(dx, dy, dx + dw, dy + dh);

            drawGlassPanel(canvas, dlgRect, dp(24));

            textPaint.setTextAlign(Paint.Align.LEFT);
            textPaint.setColor(Color.parseColor("#0F2840"));
            textPaint.setTextSize(sp(15));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("RGB AMBIENT / 氛围灯色温自定义", dx + dp(20), dy + dp(36), textPaint);

            // 预设色彩选项卡
            String[] presetNames = {"冰蓝", "极光", "烈红", "纯白"};
            int[][] presetColors = {
                    {0, 160, 233},
                    {16, 185, 129},
                    {239, 68, 68},
                    {255, 255, 255}
            };

            float pW = (dw - dp(50)) / 4.0f;
            float pY = dy + dp(65);

            for (int i = 0; i < 4; i++) {
                float pX = dx + dp(20) + i * (pW + dp(3.3f));
                RectF pRect = new RectF(pX, pY, pX + pW, pY + dp(46));
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.rgb(presetColors[i][0], presetColors[i][1], presetColors[i][2]));
                canvas.drawRoundRect(pRect, dp(10), dp(10), paint);

                textPaint.setTextAlign(Paint.Align.CENTER);
                textPaint.setColor(i == 3 ? Color.BLACK : Color.WHITE);
                textPaint.setTextSize(sp(11));
                canvas.drawText(presetNames[i], pRect.centerX(), pRect.centerY() + dp(4), textPaint);
            }

            // 当前混合色预览条
            RectF previewRect = new RectF(dx + dp(20), dy + dp(130), dx + dw - dp(20), dy + dp(175));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(rgbRed, rgbGreen, rgbBlue));
            canvas.drawRoundRect(previewRect, dp(14), dp(14), paint);
            textPaint.setColor(Color.WHITE);
            canvas.drawText("RGB(" + rgbRed + ", " + rgbGreen + ", " + rgbBlue + ")", previewRect.centerX(), previewRect.centerY() + dp(4), textPaint);

            RectF closeBtn = new RectF(dx + dp(20), dy + dh - dp(52), dx + dw - dp(20), dy + dh - dp(18));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(rgbRed, rgbGreen, rgbBlue));
            canvas.drawRoundRect(closeBtn, dp(14), dp(14), paint);
            textPaint.setColor(Color.WHITE);
            canvas.drawText("应用配色方案", closeBtn.centerX(), closeBtn.centerY() + dp(4), textPaint);
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
                    new int[]{Color.WHITE, Color.rgb(rgbRed, rgbGreen, rgbBlue), Color.WHITE, Color.parseColor("#CBD5E1"), Color.WHITE},
                    null
            );
            ringPaint.setShader(ringShader);
            canvas.drawCircle(cx, cy, r, ringPaint);

            Paint orbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            Shader orbShader = new RadialGradient(
                    cx - r * 0.3f, cy - r * 0.35f, r * 1.2f,
                    new int[]{Color.WHITE, Color.argb(120, rgbRed, rgbGreen, rgbBlue), Color.parseColor("#F8FAFC")},
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
            ptrPaint.setColor(Color.rgb(rgbRed, rgbGreen, rgbBlue));
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
                    new int[]{Color.argb(200, rgbRed, rgbGreen, rgbBlue), Color.rgb(rgbRed, rgbGreen, rgbBlue)},
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

            if (isSearchingCooler) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    float dw = w - dp(48);
                    float dh = dp(280);
                    float dx = dp(24);
                    float dy = dp(190);
                    float btnW = dw - dp(48);
                    float btnY = dy + dh - dp(56);
                    RectF retryBtn = new RectF(dx + dp(24), btnY, dx + dp(24) + btnW, btnY + dp(40));

                    if (retryBtn.contains(event.getX(), event.getY())) {
                        triggerHaptic(false);
                        if (getContext() instanceof MainActivity) {
                            ((MainActivity) getContext()).startCoolerScan();
                        }
                        return true;
                    }
                }
                return true;
            }

            if (isRgbDialogVisible) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    float dw = w - dp(60);
                    float dh = dp(270);
                    float dx = dp(30);
                    float dy = (h - dh) / 2.0f;

                    if (event.getX() < dx || event.getX() > dx + dw || event.getY() < dy || event.getY() > dy + dh) {
                        isRgbDialogVisible = false;
                        postInvalidate();
                        return true;
                    }

                    RectF closeBtn = new RectF(dx + dp(20), dy + dh - dp(52), dx + dw - dp(20), dy + dh - dp(18));
                    if (closeBtn.contains(event.getX(), event.getY())) {
                        isRgbDialogVisible = false;
                        triggerHaptic(false);
                        postInvalidate();
                        return true;
                    }

                    float pW = (dw - dp(50)) / 4.0f;
                    float pY = dy + dp(65);
                    int[][] presetColors = {
                            {0, 160, 233},
                            {16, 185, 129},
                            {239, 68, 68},
                            {255, 255, 255}
                    };
                    for (int i = 0; i < 4; i++) {
                        float pX = dx + dp(20) + i * (pW + dp(3.3f));
                        RectF pRect = new RectF(pX, pY, pX + pW, pY + dp(46));
                        if (pRect.contains(event.getX(), event.getY())) {
                            rgbRed = presetColors[i][0];
                            rgbGreen = presetColors[i][1];
                            rgbBlue = presetColors[i][2];
                            triggerHaptic(false);
                            postInvalidate();
                            return true;
                        }
                    }
                }
                return true;
            }

            if (isHapticDialogVisible) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    float dw = w - dp(60);
                    float dh = dp(230);
                    float dx = dp(30);
                    float dy = (h - dh) / 2.0f;

                    if (event.getX() < dx || event.getX() > dx + dw || event.getY() < dy || event.getY() > dy + dh) {
                        isHapticDialogVisible = false;
                        postInvalidate();
                        return true;
                    }

                    RectF closeBtn = new RectF(dx + dp(20), dy + dh - dp(52), dx + dw - dp(20), dy + dh - dp(18));
                    if (closeBtn.contains(event.getX(), event.getY())) {
                        isHapticDialogVisible = false;
                        triggerHaptic(false);
                        postInvalidate();
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
                            postInvalidate();
                            return true;
                        }
                    }
                }
                return true;
            }

            RectF capRect = new RectF(knobCx - dp(70), knobCy + dp(74), knobCx + dp(70), knobCy + dp(98));
            if (event.getAction() == MotionEvent.ACTION_DOWN && capRect.contains(event.getX(), event.getY())) {
                isHapticDialogVisible = true;
                triggerHaptic(false);
                postInvalidate();
                return true;
            }

            // 点击底部 RGB 氛围灯区域打开调色窗
            if (event.getAction() == MotionEvent.ACTION_DOWN && event.getY() > h - dp(90)) {
                isRgbDialogVisible = true;
                triggerHaptic(false);
                postInvalidate();
                return true;
            }

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
                    postInvalidate();
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
