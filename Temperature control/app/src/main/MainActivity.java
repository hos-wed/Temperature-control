package com.example.flydigicooler;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.graphics.*;
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
import java.util.UUID;

public class MainActivity extends Activity {

    private DashboardView dashboardView;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;
    private BluetoothGatt connectedGatt;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isScanning = false;

    private static final UUID TARGET_SERVICE_UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    private static final UUID TARGET_CHAR_UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            throwable.printStackTrace();
            mainHandler.post(() -> {
                if (dashboardView != null) {
                    dashboardView.scanStatusText = "通信链路重连中...";
                    dashboardView.postInvalidate();
                }
            });
        });

        dashboardView = new DashboardView(this);
        setContentView(dashboardView);

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
            dashboardView.scanStatusText = "当前设备不支持蓝牙";
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
                dashboardView.scanStatusText = "请授予蓝牙权限以继续连接";
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
            dashboardView.scanStatusText = "请开启系统蓝牙后重试";
            dashboardView.postInvalidate();
            return;
        }

        try {
            bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        } catch (Throwable ignored) {
            bleScanner = null;
        }

        if (bleScanner == null) {
            dashboardView.scanStatusText = "蓝牙扫描服务不可用";
            dashboardView.postInvalidate();
            return;
        }

        isScanning = true;
        dashboardView.isSearchingCooler = true;
        dashboardView.scanStatusText = "正在连接设备.";
        dashboardView.postInvalidate();

        try {
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();
            bleScanner.startScan(null, settings, scanCallback);
        } catch (Throwable t) {
            isScanning = false;
            dashboardView.scanStatusText = "扫描受限，点击重试";
            dashboardView.postInvalidate();
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
            dashboardView.scanStatusText = "正在建立通信连接...";
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
                dashboardView.scanStatusText = "通信连接失败，点击重试";
                dashboardView.postInvalidate();
            }
        });
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, int status, int newState) {
            mainHandler.post(() -> {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    dashboardView.deviceNameStr = "极冷散热器";
                    dashboardView.isSearchingCooler = false;
                    dashboardView.triggerHaptic(true);
                    dashboardView.postInvalidate();
                    try {
                        Toast.makeText(getApplicationContext(), "硬件设备已成功连接", Toast.LENGTH_SHORT).show();
                    } catch (Throwable ignored) {}
                    gatt.discoverServices();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    disconnectAndCloseGatt();
                    dashboardView.isSearchingCooler = true;
                    dashboardView.scanStatusText = "设备断开，正在重新搜索...";
                    dashboardView.postInvalidate();
                    startCoolerScan();
                }
            });
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(TARGET_SERVICE_UUID);
                if (service != null) {
                    BluetoothGattCharacteristic characteristic = service.getCharacteristic(TARGET_CHAR_UUID);
                    if (characteristic != null) {
                        gatt.setCharacteristicNotification(characteristic, true);
                        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CCCD_UUID);
                        if (descriptor != null) {
                            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            gatt.writeDescriptor(descriptor);
                        }
                    }
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                int rawTemp = data[0] & 0xFF;
                float hardwareTemp = rawTemp * 0.5f + 5.0f;
                mainHandler.post(() -> {
                    if (dashboardView != null) {
                        dashboardView.actualColdPlateTemp = hardwareTemp;
                        dashboardView.postInvalidate();
                    }
                });
            }
        }
    };

    public void sendCommandToCooler(int level) {
        if (connectedGatt == null) return;
        try {
            BluetoothGattService service = connectedGatt.getService(TARGET_SERVICE_UUID);
            if (service != null) {
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(TARGET_CHAR_UUID);
                if (characteristic != null) {
                    byte[] payload;
                    if (level == 0) {
                        payload = new byte[]{(byte) 0xAA, (byte) 0x00, (byte) 0x00};
                    } else {
                        payload = new byte[]{(byte) 0xAA, (byte) 0x01, (byte) level};
                    }
                    characteristic.setValue(payload);
                    connectedGatt.writeCharacteristic(characteristic);
                }
            }
        } catch (Throwable ignored) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopCoolerScan();
        disconnectAndCloseGatt();
        mainHandler.removeCallbacksAndMessages(null);
    }

    public static class DashboardView extends View {
        public float actualColdPlateTemp = 24.5f;
        public int fanRpm = 5400;
        public int currentLevel = 3;
        public boolean isAmbientOn = true;

        public boolean isSearchingCooler = true;
        public String scanStatusText = "正在连接设备.";
        public String deviceNameStr = "未连接";

        public int hapticStrength = 2;
        public boolean isHapticDialogVisible = false;

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

            if (isSearchingCooler) {
                drawBleSearchOverlay(canvas, w, h);
                return;
            }

            Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            Shader bgShader = new LinearGradient(
                    0, 0, w, h,
                    new int[]{Color.parseColor("#F2F6FB"), Color.parseColor("#EAF2F9"), Color.parseColor("#DEEBF6")},
                    new float[]{0f, 0.5f, 1f},
                    Shader.TileMode.CLAMP
            );
            bgPaint.setShader(bgShader);
            canvas.drawRect(0, 0, w, h, bgPaint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(70, rgbRed, rgbGreen, rgbBlue));
            canvas.drawCircle(w * 0.25f, dp(140), dp(140), paint);
            paint.setColor(Color.argb(50, rgbRed, rgbGreen, rgbBlue));
            canvas.drawCircle(w * 0.8f, dp(420), dp(180), paint);

            textPaint.setColor(Color.parseColor("#0F172A"));
            textPaint.setTextSize(sp(18));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("智能温控舱", dp(24), dp(48), textPaint);

            textPaint.setColor(Color.parseColor("#64748B"));
            textPaint.setTextSize(sp(11));
            textPaint.setTypeface(Typeface.DEFAULT);
            canvas.drawText("状态: " + deviceNameStr + " · iOS 液态玻璃座舱", dp(24), dp(68), textPaint);

            RectF card = new RectF(dp(20), dp(84), w - dp(20), dp(284));
            drawGlassPanel(canvas, card, dp(24));

            paint.setShader(null);
            paint.setColor(Color.parseColor("#40FFFFFF"));
            paint.setStrokeWidth(dp(2f));
            canvas.drawLine(w / 2.0f, dp(100), w / 2.0f, dp(268), paint);

            float leftCx = w * 0.26f;
            float gaugeCy = dp(180);
            float gaugeR = dp(46);
            drawGauge(canvas, leftCx, gaugeCy, gaugeR, 135, 220, Math.min(1.0f, actualColdPlateTemp / 50.0f));

            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(Color.parseColor("#0F172A"));
            textPaint.setTextSize(sp(26));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText(String.format(Locale.ROOT, "%.1f", actualColdPlateTemp), leftCx - dp(6), gaugeCy + dp(6), textPaint);

            textPaint.setColor(Color.rgb(rgbRed, rgbGreen, rgbBlue));
            textPaint.setTextSize(sp(11));
            canvas.drawText("°C", leftCx + dp(22), gaugeCy - dp(4), textPaint);

            textPaint.setColor(Color.parseColor("#64748B"));
            textPaint.setTextSize(sp(9));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("冷面实时温度", leftCx, gaugeCy + dp(22), textPaint);

            float rightCx = w * 0.74f;
            drawGauge(canvas, rightCx, gaugeCy, gaugeR, 45, -220, Math.min(1.0f, fanRpm / 7500.0f));

            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(Color.parseColor("#0F172A"));
            textPaint.setTextSize(sp(26));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText(String.format(Locale.ROOT, "%.1f", fanRpm / 1000.0f), rightCx - dp(6), gaugeCy + dp(6), textPaint);

            textPaint.setColor(Color.rgb(rgbRed, rgbGreen, rgbBlue));
            textPaint.setTextSize(sp(11));
            canvas.drawText("k", rightCx + dp(16), gaugeCy - dp(4), textPaint);

            textPaint.setColor(Color.parseColor("#64748B"));
            textPaint.setTextSize(sp(9));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("风扇转速", rightCx, gaugeCy + dp(22), textPaint);

            float knobCx = w / 2.0f;
            float knobCy = dp(475);
            float knobR = dp(125);

            RectF bigDialRect = new RectF(knobCx - knobR, knobCy - knobR, knobCx + knobR, knobCy + knobR);
            drawGlassPanel(canvas, bigDialRect, knobR);

            String[] levelTitles = {"关闭", "1 挡", "2 挡", "3 挡", "极速", "智能"};
            String[] levelDescs = {"OFF", "静音", "日常", "电竞", "27W", "AI"};
            float[] angles = {140f, 180f, 220f, 270f, 320f, 40f};

            for (int i = 0; i < 6; i++) {
                double rad = Math.toRadians(angles[i]);
                float lx = knobCx + (float) ((knobR - dp(32)) * Math.cos(rad));
                float ly = knobCy + (float) ((knobR - dp(32)) * Math.sin(rad));

                boolean isSel = (i == currentLevel);
                if (isSel) {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(Color.argb(120, rgbRed, rgbGreen, rgbBlue));
                    canvas.drawCircle(lx, ly, dp(18), paint);
                    paint.setColor(Color.rgb(rgbRed, rgbGreen, rgbBlue));
                    canvas.drawCircle(lx, ly, dp(11), paint);
                    paint.setColor(Color.WHITE);
                    canvas.drawCircle(lx - dp(1.5f), ly - dp(1.5f), dp(3.5f), paint);
                } else {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(Color.parseColor("#CBD5E1"));
                    canvas.drawCircle(lx, ly, dp(5), paint);
                }

                textPaint.setTextAlign(Paint.Align.CENTER);
                textPaint.setColor(isSel ? Color.rgb(rgbRed, rgbGreen, rgbBlue) : Color.parseColor("#475569"));
                textPaint.setTextSize(isSel ? sp(12f) : sp(10.5f));
                textPaint.setTypeface(isSel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                canvas.drawText(levelTitles[i], lx, ly - dp(22), textPaint);

                textPaint.setColor(isSel ? Color.parseColor("#0369A1") : Color.parseColor("#94A3B8"));
                textPaint.setTextSize(sp(8.5f));
                canvas.drawText(levelDescs[i], lx, ly + dp(24), textPaint);
            }

            textPaint.setColor(Color.parseColor("#0F172A"));
            textPaint.setTextSize(sp(24));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText(levelTitles[currentLevel], knobCx, knobCy - dp(4), textPaint);

            textPaint.setColor(Color.rgb(rgbRed, rgbGreen, rgbBlue));
            textPaint.setTextSize(sp(9.5f));
            canvas.drawText(fanRpm + " RPM", knobCx, knobCy + dp(18), textPaint);

            RectF capRect = new RectF(knobCx - dp(75), knobCy + dp(145), knobCx + dp(75), knobCy + dp(175));
            drawGlassPanel(canvas, capRect, dp(14));

            String[] hapticNames = {"关闭", "轻柔", "标准", "强劲"};
            textPaint.setColor(Color.rgb(rgbRed, rgbGreen, rgbBlue));
            textPaint.setTextSize(sp(10));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("⚙ 触感反馈 · " + hapticNames[hapticStrength], knobCx, knobCy + dp(163), textPaint);

            RectF btn = new RectF(dp(20), h - dp(90), w - dp(20), h - dp(40));
            drawGlassPanel(canvas, btn, dp(18));

            if (isAmbientOn) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.argb(90, rgbRed, rgbGreen, rgbBlue));
                canvas.drawCircle(dp(46), h - dp(65), dp(11), paint);
                paint.setColor(Color.rgb(rgbRed, rgbGreen, rgbBlue));
                canvas.drawCircle(dp(46), h - dp(65), dp(6.5f), paint);
                paint.setColor(Color.WHITE);
                canvas.drawCircle(dp(44.5f), h - dp(67.5f), dp(2), paint);
            } else {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.parseColor("#CBD5E1"));
                canvas.drawCircle(dp(46), h - dp(65), dp(6.5f), paint);
            }

            textPaint.setTextAlign(Paint.Align.LEFT);
            textPaint.setColor(Color.parseColor("#0F172A"));
            textPaint.setTextSize(sp(12.5f));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("RGB 氛围灯效自定义（点击调色）", dp(68), h - dp(68), textPaint);

            textPaint.setColor(Color.parseColor("#64748B"));
            textPaint.setTextSize(sp(10));
            textPaint.setTypeface(Typeface.DEFAULT);
            canvas.drawText(isAmbientOn ? "当前流光色彩生效中" : "已关闭灯效节能", dp(68), h - dp(53), textPaint);

            if (isHapticDialogVisible) {
                drawHapticSettingDialog(canvas, w, h);
            }

            if (isRgbDialogVisible) {
                drawRgbSettingDialog(canvas, w, h);
            }
        }

        private void drawBleSearchOverlay(Canvas canvas, float w, float h) {
            canvas.drawColor(Color.WHITE);

            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(Color.parseColor("#0F172A"));
            textPaint.setTextSize(sp(22));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("智能温控舱", w / 2.0f, dp(120), textPaint);

            textPaint.setColor(Color.parseColor("#64748B"));
            textPaint.setTextSize(sp(11.5f));
            textPaint.setTypeface(Typeface.DEFAULT);
            canvas.drawText("液态玻璃座舱系统 · 硬件联机", w / 2.0f, dp(146), textPaint);

            float dw = w - dp(48);
            float dh = dp(290);
            float dx = dp(24);
            float dy = dp(190);
            RectF dlgRect = new RectF(dx, dy, dx + dw, dy + dh);
            drawGlassPanel(canvas, dlgRect, dp(28));

            float radarCx = dlgRect.centerX();
            float radarCy = dy + dp(82);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor("#00A0E9"));
            canvas.drawCircle(radarCx, radarCy, dp(14), paint);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(radarCx - dp(4), radarCy - dp(4), dp(4.5f), paint);

            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(Color.parseColor("#0F172A"));
            textPaint.setTextSize(sp(17.5f));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("正在连接设备...", radarCx, dy + dp(156), textPaint);

            textPaint.setColor(Color.parseColor("#64748B"));
            textPaint.setTextSize(sp(11.5f));
            textPaint.setTypeface(Typeface.DEFAULT);
            canvas.drawText("请保持散热硬件开启并靠近手机", radarCx, dy + dp(184), textPaint);

            float btnW = dw - dp(48);
            float btnY = dy + dh - dp(58);
            RectF retryBtn = new RectF(dx + dp(24), btnY, dx + dp(24) + btnW, btnY + dp(42));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor("#00A0E9"));
            canvas.drawRoundRect(retryBtn, dp(16), dp(16), paint);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(sp(13));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("重新扫描并连接", retryBtn.centerX(), retryBtn.centerY() + dp(4), textPaint);
        }

        private void drawHapticSettingDialog(Canvas canvas, float w, float h) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor("#600F172A"));
            canvas.drawRect(0, 0, w, h, paint);

            float dw = w - dp(60);
            float dh = dp(240);
            float dx = dp(30);
            float dy = (h - dh) / 2.0f;
            RectF dlgRect = new RectF(dx, dy, dx + dw, dy + dh);

            drawGlassPanel(canvas, dlgRect, dp(26));

            textPaint.setTextAlign(Paint.Align.LEFT);
            textPaint.setColor(Color.parseColor("#0F172A"));
            textPaint.setTextSize(sp(16));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("触控马达反馈强度调节", dx + dp(22), dy + dp(38), textPaint);

            String[] levels = {"关闭", "轻柔", "标准", "强劲"};
            String[] subTexts = {"无震感", "30% 细微", "70% 咔哒", "100% 重度"};
            float itemW = (dw - dp(50)) / 4.0f;
            float itemH = dp(78);
            float itemY = dy + dp(65);

            for (int i = 0; i < 4; i++) {
                float itemX = dx + dp(20) + i * (itemW + dp(3.3f));
                RectF itemRect = new RectF(itemX, itemY, itemX + itemW, itemY + itemH);
                boolean isSelected = (hapticStrength == i);

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(isSelected ? Color.parseColor("#E0F2FE") : Color.parseColor("#70FFFFFF"));
                canvas.drawRoundRect(itemRect, dp(14), dp(14), paint);

                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(isSelected ? Color.rgb(rgbRed, rgbGreen, rgbBlue) : Color.parseColor("#CBD5E1"));
                paint.setStrokeWidth(isSelected ? dp(2.5f) : dp(1));
                canvas.drawRoundRect(itemRect, dp(14), dp(14), paint);

                textPaint.setTextAlign(Paint.Align.CENTER);
                textPaint.setColor(isSelected ? Color.rgb(rgbRed, rgbGreen, rgbBlue) : Color.parseColor("#0F172A"));
                textPaint.setTextSize(sp(13.5f));
                textPaint.setTypeface(Typeface.DEFAULT_BOLD);
                canvas.drawText(levels[i], itemRect.centerX(), itemY + dp(32), textPaint);

                textPaint.setColor(isSelected ? Color.parseColor("#0369A1") : Color.parseColor("#94A3B8"));
                textPaint.setTextSize(sp(8.5f));
                canvas.drawText(subTexts[i], itemRect.centerX(), itemY + dp(54), textPaint);
            }

            RectF closeBtn = new RectF(dx + dp(20), dy + dh - dp(54), dx + dw - dp(20), dy + dh - dp(18));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(rgbRed, rgbGreen, rgbBlue));
            canvas.drawRoundRect(closeBtn, dp(16), dp(16), paint);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(sp(13));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("确定并保存", closeBtn.centerX(), closeBtn.centerY() + dp(4), textPaint);
        }

        private void drawRgbSettingDialog(Canvas canvas, float w, float h) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor("#600F172A"));
            canvas.drawRect(0, 0, w, h, paint);

            float dw = w - dp(60);
            float dh = dp(380);
            float dx = dp(30);
            float dy = (h - dh) / 2.0f;
            RectF dlgRect = new RectF(dx, dy, dx + dw, dy + dh);

            drawGlassPanel(canvas, dlgRect, dp(26));

            textPaint.setTextAlign(Paint.Align.LEFT);
            textPaint.setColor(Color.parseColor("#0F172A"));
            textPaint.setTextSize(sp(16));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("RGB 氛围灯效色彩自定义", dx + dp(22), dy + dp(38), textPaint);

            String[] presetNames = {"冰蓝座舱", "极光绿", "电竞烈红", "纯白流光"};
            int[][] presetColors = {
                    {0, 160, 233},
                    {16, 185, 129},
                    {239, 68, 68},
                    {255, 255, 255}
            };

            float pW = (dw - dp(50)) / 4.0f;
            float pY = dy + dp(60);

            for (int i = 0; i < 4; i++) {
                float pX = dx + dp(20) + i * (pW + dp(3.3f));
                RectF pRect = new RectF(pX, pY, pX + pW, pY + dp(42));
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.rgb(presetColors[i][0], presetColors[i][1], presetColors[i][2]));
                canvas.drawRoundRect(pRect, dp(10), dp(10), paint);

                textPaint.setTextAlign(Paint.Align.CENTER);
                textPaint.setColor(i == 3 ? Color.BLACK : Color.WHITE);
                textPaint.setTextSize(sp(10));
                canvas.drawText(presetNames[i], pRect.centerX(), pRect.centerY() + dp(3), textPaint);
            }

            float sliderY = dy + dp(120);
            float sliderW = dw - dp(40);
            
            drawColorSlider(canvas, dx + dp(20), sliderY, sliderW, "红 (R): " + rgbRed, rgbRed, Color.RED);
            drawColorSlider(canvas, dx + dp(20), sliderY + dp(45), sliderW, "绿 (G): " + rgbGreen, rgbGreen, Color.GREEN);
            drawColorSlider(canvas, dx + dp(20), sliderY + dp(90), sliderW, "蓝 (B): " + rgbBlue, rgbBlue, Color.BLUE);

            RectF previewRect = new RectF(dx + dp(20), dy + dp(270), dx + dw - dp(20), dy + dp(315));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(rgbRed, rgbGreen, rgbBlue));
            canvas.drawRoundRect(previewRect, dp(14), dp(14), paint);
            textPaint.setColor((rgbRed * 0.299f + rgbGreen * 0.587f + rgbBlue * 0.114f) > 150 ? Color.BLACK : Color.WHITE);
            canvas.drawText("实时配色预览: RGB (" + rgbRed + ", " + rgbGreen + ", " + rgbBlue + ")", previewRect.centerX(), previewRect.centerY() + dp(4), textPaint);

            RectF closeBtn = new RectF(dx + dp(20), dy + dh - dp(50), dx + dw - dp(20), dy + dh - dp(18));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(rgbRed, rgbGreen, rgbBlue));
            canvas.drawRoundRect(closeBtn, dp(14), dp(14), paint);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(sp(12.5f));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("确定并应用配色", closeBtn.centerX(), closeBtn.centerY() + dp(4), textPaint);
        }

        private void drawColorSlider(Canvas canvas, float x, float y, float w, String label, int val, int tintColor) {
            textPaint.setTextAlign(Paint.Align.LEFT);
            textPaint.setColor(Color.parseColor("#0F172A"));
            textPaint.setTextSize(sp(11));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText(label, x, y, textPaint);

            float trackY = y + dp(10);
            RectF track = new RectF(x, trackY, x + w, trackY + dp(8));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor("#CBD5E1"));
            canvas.drawRoundRect(track, dp(4), dp(4), paint);

            float progressW = w * (val / 255.0f);
            RectF progress = new RectF(x, trackY, x + progressW, trackY + dp(8));
            paint.setColor(tintColor);
            canvas.drawRoundRect(progress, dp(4), dp(4), paint);

            canvas.drawCircle(x + progressW, trackY + dp(4), dp(8), paint);
        }

        private void drawGlassPanel(Canvas canvas, RectF rect, float radius) {
            Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            Shader fillShader = new LinearGradient(
                    rect.left, rect.top, rect.right, rect.bottom,
                    new int[]{Color.parseColor("#EBFFFFFF"), Color.parseColor("#B0EAF4FF"), Color.parseColor("#D5FFFFFF")},
                    new float[]{0f, 0.55f, 1f},
                    Shader.TileMode.CLAMP
            );
            fillPaint.setShader(fillShader);
            fillPaint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(rect, radius, radius, fillPaint);

            Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            Shader sheenShader = new LinearGradient(
                    rect.left, rect.top, rect.left, rect.top + rect.height() * 0.4f,
                    new int[]{Color.parseColor("#A0FFFFFF"), Color.parseColor("#00FFFFFF")},
                    null,
                    Shader.TileMode.CLAMP
            );
            sheenPaint.setShader(sheenShader);
            sheenPaint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(rect, radius, radius, sheenPaint);

            Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            Shader strokeShader = new LinearGradient(
                    rect.left, rect.top, rect.right, rect.bottom,
                    new int[]{Color.parseColor("#FFFFFFFF"), Color.parseColor("#70BAE6FD"), Color.parseColor("#30FFFFFF")},
                    new float[]{0f, 0.4f, 1f},
                    Shader.TileMode.CLAMP
            );
            strokePaint.setShader(strokeShader);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(1.5f));
            canvas.drawRoundRect(rect, radius, radius, strokePaint);
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
            float knobCy = dp(475);
            float knobR = dp(125);

            if (isSearchingCooler) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    float dw = w - dp(48);
                    float dh = dp(290);
                    float dx = dp(24);
                    float dy = dp(190);
                    float btnW = dw - dp(48);
                    float btnY = dy + dh - dp(58);
                    RectF retryBtn = new RectF(dx + dp(24), btnY, dx + dp(24) + btnW, btnY + dp(42));

                    if (retryBtn.contains(event.getX(), event.getY())) {
                        triggerHaptic(false);
                        Context ctx = getContext();
                        if (ctx instanceof MainActivity) {
                            ((MainActivity) ctx).startCoolerScan();
                        }
                        return true;
                    }
                }
                return true;
            }

            if (isRgbDialogVisible) {
                if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                    float dw = w - dp(60);
                    float dh = dp(380);
                    float dx = dp(30);
                    float dy = (h - dh) / 2.0f;

                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        if (event.getX() < dx || event.getX() > dx + dw || event.getY() < dy || event.getY() > dy + dh) {
                            isRgbDialogVisible = false;
                            postInvalidate();
                            return true;
                        }

                        RectF closeBtn = new RectF(dx + dp(20), dy + dh - dp(50), dx + dw - dp(20), dy + dh - dp(18));
                        if (closeBtn.contains(event.getX(), event.getY())) {
                            isRgbDialogVisible = false;
                            triggerHaptic(false);
                            postInvalidate();
                            return true;
                        }

                        float pW = (dw - dp(50)) / 4.0f;
                        float pY = dy + dp(60);
                        int[][] presetColors = {
                                {0, 160, 233},
                                {16, 185, 129},
                                {239, 68, 68},
                                {255, 255, 255}
                        };
                        for (int i = 0; i < 4; i++) {
                            float pX = dx + dp(20) + i * (pW + dp(3.3f));
                            RectF pRect = new RectF(pX, pY, pX + pW, pY + dp(42));
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

                    float sliderY = dy + dp(120);
                    float sliderW = dw - dp(40);
                    float startX = dx + dp(20);

                    for (int i = 0; i < 3; i++) {
                        float sY = sliderY + (i * dp(45));
                        RectF touchZone = new RectF(startX - dp(10), sY, startX + sliderW + dp(10), sY + dp(30));
                        if (touchZone.contains(event.getX(), event.getY())) {
                            float ratio = (event.getX() - startX) / sliderW;
                            int val = Math.max(0, Math.min(255, (int)(ratio * 255)));
                            if (i == 0) rgbRed = val;
                            else if (i == 1) rgbGreen = val;
                            else if (i == 2) rgbBlue = val;
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
                    float dh = dp(240);
                    float dx = dp(30);
                    float dy = (h - dh) / 2.0f;

                    if (event.getX() < dx || event.getX() > dx + dw || event.getY() < dy || event.getY() > dy + dh) {
                        isHapticDialogVisible = false;
                        postInvalidate();
                        return true;
                    }

                    RectF closeBtn = new RectF(dx + dp(20), dy + dh - dp(54), dx + dw - dp(20), dy + dh - dp(18));
                    if (closeBtn.contains(event.getX(), event.getY())) {
                        isHapticDialogVisible = false;
                        triggerHaptic(false);
                        postInvalidate();
                        return true;
                    }

                    float itemW = (dw - dp(50)) / 4.0f;
                    float itemH = dp(78);
                    float itemY = dy + dp(65);

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

            RectF capRect = new RectF(knobCx - dp(75), knobCy + dp(145), knobCx + dp(75), knobCy + dp(175));
            if (event.getAction() == MotionEvent.ACTION_DOWN && capRect.contains(event.getX(), event.getY())) {
                isHapticDialogVisible = true;
                triggerHaptic(false);
                postInvalidate();
                return true;
            }

            if (event.getAction() == MotionEvent.ACTION_DOWN && event.getY() > h - dp(90)) {
                isRgbDialogVisible = true;
                triggerHaptic(false);
                postInvalidate();
                return true;
            }

            float dx = event.getX() - knobCx;
            float dy = event.getY() - knobCy;
            if (Math.sqrt(dx * dx + dy * dy) <= knobR) {
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

                    Context ctx = getContext();
                    if (ctx instanceof MainActivity) {
                        ((MainActivity) ctx).sendCommandToCooler(currentLevel);
                    }

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
