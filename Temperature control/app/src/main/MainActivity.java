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

// 完美修复版：使用回调接口解决内部类编译报错
public class MainActivity extends Activity implements DashboardView.ControlListener {

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

        // 初始化 DashboardView 并设置 MainActivity 为监听器
        dashboardView = new DashboardView(this);
        dashboardView.setControlListener(this);
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
                        BluetoothGattDescriptor characteristicDescriptor = characteristic.getDescriptor(CCCD_UUID);
                        if (characteristicDescriptor != null) {
                            characteristicDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            gatt.writeDescriptor(characteristicDescriptor);
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

    // --- DashboardView.ControlListener 接口实现 ---

    @Override
    public void onSendCommandRequested(int level) {
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
    public void onScanRequested() {
        startCoolerScan();
    }

    // --- End of Interface Implementation ---

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopCoolerScan();
        disconnectAndCloseGatt();
        mainHandler.removeCallbacksAndMessages(null);
    }
}

// 终极修复版：将 DashboardView 改为标准的 Public Static Class
// 不再依赖 MainActivity 的非静态上下文，完美通过 Gradle 编译
class DashboardView extends View {

    // 定义回调接口
    public interface ControlListener {
        void onSendCommandRequested(int level);
        void onScanRequested();
    }

    private ControlListener controlListener;

    public void setControlListener(ControlListener listener) {
        this.controlListener = listener;
    }

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
        canvas
