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

            // 背景液态浅蓝微光光晕（散焦光斑）
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor("#2538BDF8"));
            canvas.drawCircle(w * 0.2f, dp(150), dp(130), paint);
            paint.setColor(Color.parseColor("#180288D1"));
            canvas.drawCircle(w * 0.85f, dp(460), dp(160), paint);

            // 2. 顶部车机状态栏标题
            textPaint.setColor(Color.parseColor("#0F2840"));
            textPaint.setTextSize(sp(18));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("CLIMATE CONTROL", dp(24), dp(48), textPaint);

            textPaint.setColor(Color.parseColor("#5A7B9A"));
            textPaint.setTextSize(sp(11));
            textPaint.setTypeface(Typeface.DEFAULT);
            canvas.drawText("Temperature control · 液态玻璃极冷座舱", dp(24), dp(68), textPaint);

            // 3. 绘制双仪表盘——【液态玻璃悬浮卡片】
            RectF card = new RectF(dp(20), dp(84), w - dp(20), dp(284));
            drawGlassPanel(canvas, card, dp(22));

            // 中间半透明玻璃折射刻痕线
            paint.setShader(null);
            paint.setColor(Color.parseColor("#30FFFFFF"));
            paint.setStrokeWidth(dp(2f));
            canvas.drawLine(w / 2.0f, dp(100), w / 2.0f, dp(268), paint);
            paint.setColor(Color.parseColor("#1864748B"));
            paint.setStrokeWidth(dp(1f));
            canvas.drawLine(w / 2.0f + dp(1), dp(100), w / 2.0f + dp(1), dp(268), paint);

            // 3.1 左仪表：手机实时温度
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

            // 4. 【水滴拟态液态玻璃旋钮系统】
            float knobCx = w / 2.0f;
            float knobCy = dp(455);
            float knobR = dp(50);

            // 旋钮大底座——折射玻璃圆盘
            RectF knobBase = new RectF(knobCx - dp(120), knobCy - dp(120), knobCx + dp(120), knobCy + dp(120));
            drawGlassPanel(canvas, knobBase, dp(120));

            String[] titles = {"OFF", "1 挡", "2 挡", "3 挡", "MAX", "AUTO"};
            String[] descs = {"关闭", "轻音", "日常", "电竞", "超频", "智冷"};
            float[] angles = {140f, 180f, 220f, 270f, 320f, 40f};

            // 绘制刻度与发光液体小气泡
            for (int i = 0; i < 6; i++) {
                double rad = Math.toRadians(angles[i]);
                float lx = knobCx + (float) (dp(88) * Math.cos(rad));
                float ly = knobCy + (float) (dp(88) * Math.sin(rad));

                boolean isSel = (i == currentLevel);

                if (isSel) {
                    // 当前档位：液态发光凝珠
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

            // 旋钮核心水晶球体与金属棱镜环
            drawGlassOrbKnob(canvas, knobCx, knobCy, knobR, angles[currentLevel]);

            // 中心大字数值显示
            textPaint.setColor(Color.parseColor("#0F172A"));
            textPaint.setTextSize(sp(19));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText(titles[currentLevel].replace(" 挡", ""), knobCx, knobCy - dp(2), textPaint);

            textPaint.setColor(Color.parseColor("#0284C7"));
            textPaint.setTextSize(sp(8.5f));
            textPaint.setTypeface(Typeface.DEFAULT);
            canvas.drawText("LEVEL", knobCx, knobCy + dp(12), textPaint);

            // 阻尼触感玻璃胶囊标签
            RectF capRect = new RectF(knobCx - dp(60), knobCy + dp(74), knobCx + dp(60), knobCy + dp(96));
            drawGlassPanel(canvas, capRect, dp(11));

            textPaint.setColor(Color.parseColor("#0284C7"));
            textPaint.setTextSize(sp(9));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("触控阻尼段落感", knobCx, knobCy + dp(88), textPaint);

            // 5. 底部 RGB 氛围灯【晶莹剔透玻璃开关】
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
        }

        /**
         * 绘制液态玻璃卡片（双层反光、边缘菲涅尔高光、折射渐变）
         */
        private void drawGlassPanel(Canvas canvas, RectF rect, float radius) {
            // A. 底层液态漫反射填充（天青浅蓝微光至高透白）
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

            // B. 顶面折射光泽弧面（模拟厚玻璃反光）
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

            // C. 玻璃物理边缘高光外圈（上亮下透）
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

        /**
         * 绘制液态水晶与合金旋钮
         */
        private void drawGlassOrbKnob(Canvas canvas, float cx, float cy, float r, float angle) {
            // 外圈磨砂金属与玻璃倒角
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

            // 球体水晶盘面（向心液态渐变）
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

            // 动态发光液滴指针
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

            // 指针尖端折射光点
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

            // 晶莹浅灰底环
            paint.setColor(Color.parseColor("#30CBD5E1"));
            paint.setStrokeWidth(dp(6.5f));
            canvas.drawArc(rect, startAngle, sweep, false, paint);

            // 液体光泽天青色进度环
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
            float knobCx = getWidth() / 2.0f;
            float knobCy = dp(455);

            if (event.getAction() == MotionEvent.ACTION_DOWN && event.getY() > getHeight() - dp(90)) {
                isAmbientOn = !isAmbientOn;
                triggerHaptic();
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
