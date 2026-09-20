package com.example.flydigicooler;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.*;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

public class MainActivity extends Activity {

    private DashboardView dashboardView;

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
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(batteryReceiver);
    }

    public static class DashboardView extends View {
        public float phoneTemp = 34.0f;
        public int fanRpm = 5400;
        public int currentLevel = 3;
        public boolean isAmbientOn = true;

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

            // 背景底色
            canvas.drawColor(Color.parseColor("#F4F7FB"));

            // 标题
            textPaint.setColor(Color.parseColor("#102A43"));
            textPaint.setTextSize(sp(18));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("CLIMATE CONTROL", dp(24), dp(48), textPaint);

            textPaint.setColor(Color.parseColor("#627D98"));
            textPaint.setTextSize(sp(11));
            textPaint.setTypeface(Typeface.DEFAULT);
            canvas.drawText("Temperature control · 现代车载极冷座舱", dp(24), dp(68), textPaint);

            // 双仪表卡片
            RectF card = new RectF(dp(20), dp(84), w - dp(20), dp(284));
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(card, dp(18), dp(18), paint);

            paint.setColor(Color.parseColor("#F0F4F8"));
            paint.setStrokeWidth(dp(1.5f));
            canvas.drawLine(w / 2.0f, dp(100), w / 2.0f, dp(268), paint);

            // 左表：温度
            float leftCx = w * 0.26f;
            float gaugeCy = dp(180);
            float gaugeR = dp(46);
            drawGauge(canvas, leftCx, gaugeCy, gaugeR, 135, 220, Math.min(1.0f, phoneTemp / 60.0f));

            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(Color.parseColor("#0F172A"));
            textPaint.setTextSize(sp(26));
            canvas.drawText(String.valueOf((int) phoneTemp), leftCx - dp(6), gaugeCy + dp(6), textPaint);

            textPaint.setColor(Color.parseColor("#0288D1"));
            textPaint.setTextSize(sp(11));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("°C", leftCx + dp(16), gaugeCy - dp(4), textPaint);

            textPaint.setColor(Color.parseColor("#64748B"));
            textPaint.setTextSize(sp(9));
            canvas.drawText("PHONE TEMP", leftCx, gaugeCy + dp(22), textPaint);

            // 右表：转速
            float rightCx = w * 0.74f;
            drawGauge(canvas, rightCx, gaugeCy, gaugeR, 45, -220, Math.min(1.0f, fanRpm / 7500.0f));

            textPaint.setColor(Color.parseColor("#0F172A"));
            textPaint.setTextSize(sp(26));
            textPaint.setTypeface(Typeface.DEFAULT);
            canvas.drawText(String.format("%.1f", fanRpm / 1000.0f), rightCx - dp(6), gaugeCy + dp(6), textPaint);

            textPaint.setColor(Color.parseColor("#0288D1"));
            textPaint.setTextSize(sp(11));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("k", rightCx + dp(16), gaugeCy - dp(4), textPaint);

            textPaint.setColor(Color.parseColor("#64748B"));
            textPaint.setTextSize(sp(9));
            canvas.drawText("FAN SPEED", rightCx, gaugeCy + dp(22), textPaint);

            // 阻尼旋钮
            float knobCx = w / 2.0f;
            float knobCy = dp(455);
            float knobR = dp(46);

            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(knobCx, knobCy, dp(120), paint);

            String[] titles = {"OFF", "1 挡", "2 挡", "3 挡", "MAX", "AUTO"};
            String[] descs = {"关闭", "轻音", "日常", "电竞", "超频", "智冷"};
            float[] angles = {140f, 180f, 220f, 270f, 320f, 40f};

            for (int i = 0; i < 6; i++) {
                double rad = Math.toRadians(angles[i]);
                float lx = knobCx + (float) (dp(88) * Math.cos(rad));
                float ly = knobCy + (float) (dp(88) * Math.sin(rad));

                boolean isSel = (i == currentLevel);
                paint.setColor(isSel ? Color.parseColor("#00A0E9") : Color.parseColor("#CBD5E1"));
                canvas.drawCircle(lx, ly - dp(16), isSel ? dp(4) : dp(2.5f), paint);

                textPaint.setColor(isSel ? Color.parseColor("#00A0E9") : Color.parseColor("#64748B"));
                textPaint.setTextSize(isSel ? sp(11.5f) : sp(10.5f));
                textPaint.setTypeface(isSel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                canvas.drawText(titles[i], lx, ly - dp(2), textPaint);

                textPaint.setColor(isSel ? Color.parseColor("#0288D1") : Color.parseColor("#94A3B8"));
                textPaint.setTextSize(sp(8));
                textPaint.setTypeface(Typeface.DEFAULT);
                canvas.drawText(descs[i], lx, ly + dp(10), textPaint);
            }

            // 旋钮环与指针
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(Color.parseColor("#E2E8F0"));
            paint.setStrokeWidth(dp(2.5f));
            canvas.drawCircle(knobCx, knobCy, knobR, paint);

            double curRad = Math.toRadians(angles[currentLevel]);
            paint.setColor(Color.parseColor("#00A0E9"));
            paint.setStrokeWidth(dp(4));
            paint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(
                    knobCx + (float) (dp(14) * Math.cos(curRad)),
                    knobCy + (float) (dp(14) * Math.sin(curRad)),
                    knobCx + (float) ((knobR - dp(6)) * Math.cos(curRad)),
                    knobCy + (float) ((knobR - dp(6)) * Math.sin(curRad)),
                    paint
            );

            // 中心指示
            textPaint.setColor(Color.parseColor("#0F172A"));
            textPaint.setTextSize(sp(18));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText(titles[currentLevel].replace(" 挡", ""), knobCx, knobCy - dp(2), textPaint);

            // 底部氛围灯开关
            RectF btn = new RectF(dp(20), h - dp(90), w - dp(20), h - dp(40));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            canvas.drawRoundRect(btn, dp(14), dp(14), paint);

            paint.setColor(isAmbientOn ? Color.parseColor("#00A0E9") : Color.parseColor("#CBD5E1"));
            canvas.drawCircle(dp(44), h - dp(65), dp(6), paint);

            textPaint.setTextAlign(Paint.Align.LEFT);
            textPaint.setColor(Color.parseColor("#0F172A"));
            textPaint.setTextSize(sp(12));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("AMBIENT LIGHT / RGB氛围灯效", dp(58), h - dp(68), textPaint);
        }

        private void drawGauge(Canvas canvas, float cx, float cy, float r, float startAngle, float sweep, float progress) {
            RectF rect = new RectF(cx - r, cy - r, cx + r, cy + r);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);

            paint.setColor(Color.parseColor("#E2E8F0"));
            paint.setStrokeWidth(dp(5.5f));
            canvas.drawArc(rect, startAngle, sweep, false, paint);

            paint.setColor(Color.parseColor("#00A0E9"));
            paint.setStrokeWidth(dp(5.5f));
            canvas.drawArc(rect, startAngle, sweep * progress, false, paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float knobCx = getWidth() / 2.0f;
            float knobCy = dp(455);

            if (event.getAction() == MotionEvent.ACTION_DOWN && event.getY() > getHeight() - dp(90)) {
                isAmbientOn = !isAmbientOn;
                invalidate();
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
                    triggerHaptic();
                    int[] rpms = {0, 2500, 3800, 5400, 7200, 4200};
                    fanRpm = rpms[currentLevel];
                    invalidate();
                }
                return true;
            }
            return super.onTouchEvent(event);
        }

        private void triggerHaptic() {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            if (vibrator != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(15, 120));
                } else {
                    vibrator.vibrate(15);
                }
            }
        }

        private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
        private float sp(float v) { return v * getResources().getDisplayMetrics().scaledDensity; }
    }
}
