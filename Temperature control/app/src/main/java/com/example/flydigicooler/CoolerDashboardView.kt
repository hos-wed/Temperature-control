package com.example.flydigicooler

import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class CoolerDashboardView(context: Context) : View(context) {

    enum class Gear(val title: String, val desc: String, val angle: Float) {
        OFF("OFF", "关闭制冷", 140f),
        LEVEL_1("1 挡", "轻音阅读", 180f),
        LEVEL_2("2 挡", "日常影音", 220f),
        LEVEL_3("3 挡", "电竞强冷", 270f),
        MAX("MAX", "27W超频", 320f),
        AUTO("AUTO", "智能温控", 40f)
    }

    var currentGear = Gear.LEVEL_3
    var phoneTemp = 34f
    var fanRpm = 5400
    var isAmbientOn = true
    var onGearChangedListener: ((Gear) -> Unit)? = null

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        isClickable = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        // 1. 全局背景底色：纯白冷淡蓝基调
        canvas.drawColor(Color.parseColor("#F4F7FB"))

        // 2. 顶部车机状态栏标题
        textPaint.color = Color.parseColor("#102A43")
        textPaint.textSize = sp(18f)
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("CLIMATE CONTROL", dp(24f), dp(48f), textPaint)

        textPaint.color = Color.parseColor("#627D98")
        textPaint.textSize = sp(11f)
        textPaint.typeface = Typeface.DEFAULT
        canvas.drawText("Temperature control · 现代车载极冷座舱", dp(24f), dp(68f), textPaint)

        // 3. 绘制双仪表盘白色卡片底托
        val cardRect = RectF(dp(20f), dp(84f), w - dp(20f), dp(284f))
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(cardRect, dp(18f), dp(18f), paint)

        // 仪表盘中央分割线
        paint.color = Color.parseColor("#F0F4F8")
        paint.strokeWidth = dp(1.5f)
        canvas.drawLine(w / 2f, dp(100f), w / 2f, dp(268f), paint)

        // 3.1 左仪表：手机实时温度
        val leftCx = w * 0.26f
        val gaugeCy = dp(180f)
        val gaugeR = dp(46f)
        drawGauge(canvas, leftCx, gaugeCy, gaugeR, 135f, 220f, (phoneTemp / 60f).coerceIn(0f, 1f))

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = Color.parseColor("#0F172A")
        textPaint.textSize = sp(26f)
        textPaint.typeface = Typeface.DEFAULT
        canvas.drawText("${phoneTemp.toInt()}", leftCx - dp(6f), gaugeCy + dp(6f), textPaint)

        textPaint.color = Color.parseColor("#0288D1")
        textPaint.textSize = sp(11f)
        textPaint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("°C", leftCx + dp(16f), gaugeCy - dp(4f), textPaint)

        textPaint.color = Color.parseColor("#64748B")
        textPaint.textSize = sp(9f)
        textPaint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("PHONE TEMP", leftCx, gaugeCy + dp(22f), textPaint)

        textPaint.color = Color.parseColor("#0288D1")
        textPaint.textSize = sp(9f)
        canvas.drawText("手机核心温度", leftCx, gaugeCy + dp(35f), textPaint)

        // 3.2 右仪表：散热风扇转速
        val rightCx = w * 0.74f
        drawGauge(canvas, rightCx, gaugeCy, gaugeR, 45f, -220f, (fanRpm / 7500f).coerceIn(0f, 1f))

        textPaint.color = Color.parseColor("#0F172A")
        textPaint.textSize = sp(26f)
        textPaint.typeface = Typeface.DEFAULT
        val rpmK = String.format("%.1f", fanRpm / 1000f)
        canvas.drawText(rpmK, rightCx - dp(6f), gaugeCy + dp(6f), textPaint)

        textPaint.color = Color.parseColor("#0288D1")
        textPaint.textSize = sp(11f)
        textPaint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("k", rightCx + dp(16f), gaugeCy - dp(4f), textPaint)

        textPaint.color = Color.parseColor("#64748B")
        textPaint.textSize = sp(9f)
        textPaint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("FAN SPEED", rightCx, gaugeCy + dp(22f), textPaint)

        textPaint.color = Color.parseColor("#0288D1")
        textPaint.textSize = sp(9f)
        canvas.drawText("$fanRpm RPM", rightCx, gaugeCy + dp(35f), textPaint)

        // 4. 车规小旋钮控温系统
        val knobCx = w / 2f
        val knobCy = dp(455f)
        val knobR = dp(46f)

        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        canvas.drawCircle(knobCx, knobCy, dp(120f), paint)

        // 绘制 6 个挡位文字与功能标明
        val labelR = dp(88f)
        for (g in Gear.values()) {
            val rad = Math.toRadians(g.angle.toDouble())
            val lx = knobCx + (labelR * cos(rad)).toFloat()
            val ly = knobCy + (labelR * sin(rad)).toFloat()

            val isSelected = (g == currentGear)
            paint.color = if (isSelected) Color.parseColor("#00A0E9") else Color.parseColor("#CBD5E1")
            canvas.drawCircle(lx, ly - dp(16f), if (isSelected) dp(4f) else dp(2.5f), paint)

            textPaint.textAlign = Paint.Align.CENTER
            textPaint.color = if (isSelected) Color.parseColor("#00A0E9") else Color.parseColor("#64748B")
            textPaint.textSize = if (isSelected) sp(11.5f) else sp(10.5f)
            textPaint.typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            canvas.drawText(g.title, lx, ly - dp(2f), textPaint)

            textPaint.color = if (isSelected) Color.parseColor("#0288D1") else Color.parseColor("#94A3B8")
            textPaint.textSize = sp(8f)
            textPaint.typeface = Typeface.DEFAULT
            canvas.drawText(g.desc, lx, ly + dp(10f), textPaint)
        }

        paint.style = Paint.Style.STROKE
        paint.color = Color.parseColor("#E2E8F0")
        paint.strokeWidth = dp(2.5f)
        canvas.drawCircle(knobCx, knobCy, knobR, paint)

        paint.color = Color.parseColor("#CBD5E1")
        paint.strokeWidth = dp(1.2f)
        canvas.drawCircle(knobCx, knobCy, knobR - dp(4f), paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawCircle(knobCx, knobCy, knobR - dp(5f), paint)

        val curRad = Math.toRadians(currentGear.angle.toDouble())
        val pStartX = knobCx + (dp(14f) * cos(curRad)).toFloat()
        val pStartY = knobCy + (dp(14f) * sin(curRad)).toFloat()
        val pEndX = knobCx + ((knobR - dp(6f)) * cos(curRad)).toFloat()
        val pEndY = knobCy + ((knobR - dp(6f)) * sin(curRad)).toFloat()

        paint.style = Paint.Style.STROKE
        paint.color = Color.parseColor("#00A0E9")
        paint.strokeWidth = dp(4f)
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(pStartX, pStartY, pEndX, pEndY, paint)

        textPaint.color = Color.parseColor("#0F172A")
        textPaint.textSize = sp(18f)
        textPaint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText(currentGear.title.replace(" 挡", ""), knobCx, knobCy - dp(2f), textPaint)

        textPaint.color = Color.parseColor("#0288D1")
        textPaint.textSize = sp(8f)
        textPaint.typeface = Typeface.DEFAULT
        canvas.drawText("LEVEL", knobCx, knobCy + dp(11f), textPaint)

        val capRect = RectF(knobCx - dp(58f), knobCy + dp(68f), knobCx + dp(58f), knobCy + dp(88f))
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#E1F5FE")
        canvas.drawRoundRect(capRect, dp(10f), dp(10f), paint)

        textPaint.color = Color.parseColor("#0288D1")
        textPaint.textSize = sp(9f)
        textPaint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("马达机械阻尼震感", knobCx, knobCy + dp(81f), textPaint)

        // 5. 底部 RGB 开关
        val btnRect = RectF(dp(20f), h - dp(90f), w - dp(20f), h - dp(40f))
        paint.color = Color.WHITE
        canvas.drawRoundRect(btnRect, dp(14f), dp(14f), paint)

        paint.color = if (isAmbientOn) Color.parseColor("#00A0E9") else Color.parseColor("#CBD5E1")
        canvas.drawCircle(dp(44f), h - dp(65f), dp(6f), paint)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = Color.parseColor("#0F172A")
        textPaint.textSize = sp(12f)
        textPaint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("AMBIENT LIGHT / RGB氛围灯效", dp(58f), h - dp(68f), textPaint)

        textPaint.color = Color.parseColor("#64748B")
        textPaint.textSize = sp(9.5f)
        textPaint.typeface = Typeface.DEFAULT
        canvas.drawText(if (isAmbientOn) "流光炫彩联动已开启" else "已关闭灯效节电", dp(58f), h - dp(54f), textPaint)

        paint.color = if (isAmbientOn) Color.parseColor("#00A0E9") else Color.parseColor("#CBD5E1")
        canvas.drawCircle(w - dp(44f), h - dp(65f), dp(4f), paint)
    }

    private fun drawGauge(canvas: Canvas, cx: Float, cy: Float, r: Float, startAngle: Float, sweep: Float, progress: Float) {
        val rect = RectF(cx - r, cy - r, cx + r, cy + r)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.parseColor("#E2E8F0")
        paint.strokeWidth = dp(5.5f)
        canvas.drawArc(rect, startAngle, sweep, false, paint)

        paint.color = Color.parseColor("#00A0E9")
        paint.strokeWidth = dp(5.5f)
        canvas.drawArc(rect, startAngle, sweep * progress, false, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val knobCx = width / 2f
        val knobCy = dp(455f)

        if (event.action == MotionEvent.ACTION_DOWN && event.y > height - dp(90f)) {
            isAmbientOn = !isAmbientOn
            triggerHaptic(false)
            invalidate()
            return true
        }

        val dx = event.x - knobCx
        val dy = event.y - knobCy
        val dist = sqrt(dx * dx + dy * dy)

        if (dist <= dp(130f)) {
            var deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
            if (deg < 0) deg += 360f

            val nearestGear = Gear.values().minByOrNull { g ->
                val diff = abs(g.angle - deg)
                min(diff, 360f - diff)
            } ?: currentGear

            if (nearestGear != currentGear) {
                currentGear = nearestGear
                triggerHaptic(currentGear == Gear.MAX || currentGear == Gear.OFF)
                fanRpm = when (currentGear) {
                    Gear.OFF -> 0
                    Gear.LEVEL_1 -> 2500
                    Gear.LEVEL_2 -> 3800
                    Gear.LEVEL_3 -> 5400
                    Gear.MAX -> 7200
                    Gear.AUTO -> 4200
                }
                onGearChangedListener?.invoke(currentGear)
                invalidate()
            }
            return true
        }
        return super.onTouchEvent(event)
    }

    private fun triggerHaptic(isHeavy: Boolean) {
        performHapticFeedback(if (isHeavy) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CLOCK_TICK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = if (isHeavy) {
                VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE)
            } else {
                VibrationEffect.createOneShot(12, 120)
            }
            vibrator?.vibrate(effect)
        } else {
            vibrator?.vibrate(if (isHeavy) 35 else 12)
        }
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun sp(v: Float) = v * resources.displayMetrics.scaledDensity
}
