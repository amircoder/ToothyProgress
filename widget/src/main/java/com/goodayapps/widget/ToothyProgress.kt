package com.goodayapps.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.RangeInfo
import androidx.annotation.ColorInt
import androidx.annotation.Dimension
import androidx.annotation.FloatRange
import androidx.annotation.RequiresApi
import com.goodayapps.widget.utils.convertDpToPixel
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random.Default.nextFloat

class ToothyProgress : View {

	private val debugPaint: Paint = getDebugPaint()
	private val trackPaint: Paint = getMarkerPaint()
	private val progressPaint: Paint = getProgressPaint()
	private val progressBackgroundPaint: Paint = getProgressBackgroundPaint()

	var isBuilderMode = false

	private var progress: Float = .0f
		set(value) {
			field = value
			listener?.onProgressChanged(value, isTouching)
			invalidate()
		}

	var progressStrokeCap = Paint.Cap.ROUND
		set(value) {
			field = value
			progressPaint.strokeCap = value

			invalidate()
		}

	var progressBackgroundStrokeCap = Paint.Cap.ROUND
		set(value) {
			field = value
			progressBackgroundPaint.strokeCap = value

			invalidate()
		}

	var strokeLineCapTrack = Paint.Cap.ROUND
		set(value) {
			field = value
			trackPaint.strokeCap = value

			invalidate()
		}

	@ColorInt
	var progressColor = Color.parseColor("#ffffff")
		set(value) {
			field = value
			progressPaint.color = value

			invalidate()
		}

	@ColorInt
	var progressBackgroundColor = Color.parseColor("#959595")
		set(value) {
			field = value
			progressBackgroundPaint.color = value

			invalidate()
		}

	@ColorInt
	var trackColor = Color.parseColor("#959595")
		set(value) {
			field = value
			trackPaint.color = value

			invalidate()
		}

	@Dimension(unit = Dimension.PX)
	var progressWidth = context.convertDpToPixel(3).toFloat()
		set(value) {
			field = value
			progressPaint.strokeWidth = value

			invalidate()
		}

	@Dimension(unit = Dimension.PX)
	var trackWidth = context.convertDpToPixel(3).toFloat()
		set(value) {
			field = value
			trackPaint.strokeWidth = value

			invalidate()
		}

	@Dimension(unit = Dimension.PX)
	var progressBackgroundWidth = context.convertDpToPixel(3).toFloat()
		set(value) {
			field = value
			progressBackgroundPaint.strokeWidth = value

			invalidate()
		}

	private val canvasWidth
		get() = width - paddingStart - paddingEnd

	private val canvasHeight
		get() = height - paddingTop - paddingBottom

	private val canvasHalfHeight
		get() = canvasHeight / 2f

	private val data: MutableList<PointF> = mutableListOf()
	private val fractureData: MutableList<PointF> = mutableListOf()

	private var progressAnimator: ValueAnimator? = null

	private var pointerPosition = -1f
	private var isTouching = false

	private var listener: Listener? = null

	private val nearestApex
		get() = data.getOrNull(nearestApexIndex)

	private var nearestApexIndex: Int = -1

	constructor(context: Context?) : super(context)
	constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
		inflateAttrs(attrs)
	}

	constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
		inflateAttrs(attrs)
	}

	@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
	constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
		inflateAttrs(attrs)
	}

	init {
		val padding = context.convertDpToPixel(12)
		setPadding(padding, padding, padding, padding)
	}


	override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
		super.onInitializeAccessibilityNodeInfo(info)
		info.className = "android.widget.ProgressBar"
		info.contentDescription = "ProgressBar"

		val rangeInfo = RangeInfo.obtain(
				RangeInfo.RANGE_TYPE_INT,
				0.0f,
				1f,
				progress
		)

		info.rangeInfo = rangeInfo
	}

	override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
		super.onSizeChanged(w, h, oldw, oldh)
		setFractureDataPairs(listOf(
				.5f to .5f,
				.5f to 0f,
				.5f to .5f,
				1f to -.5f,
				1f to .5f,
				.5f to .0f,
				1f to 1f,
				1f to .0f,
				1f to .0f
		))
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)

		//Draw background
		drawProgress(canvas, progressBackgroundPaint)

		if (isBuilderMode.not()) {
			//Draw foreground
			drawProgress(canvas, progressPaint, progress)
			//Draw pointer
			drawPointer(canvas)
		}

		//Draw debug
		builderDrawDebug(canvas)
	}

	override fun onTouchEvent(event: MotionEvent): Boolean {
		if (!isEnabled) {
			return false
		}

		when (event.actionMasked) {
			MotionEvent.ACTION_DOWN,
			MotionEvent.ACTION_MOVE,
			-> {
				if (nearestApex == null) {
					trackTouch(event)
				} else {
					builderMoveApex(event)
				}

				return true
			}
			MotionEvent.ACTION_UP,
			MotionEvent.ACTION_CANCEL,
			-> {
				stopTrackingTouch()
				return true
			}
		}

		return super.onTouchEvent(event)
	}

	fun setProgress(@FloatRange(from = 0.0, to = 1.0) progress: Float, animated: Boolean = true) {
		if (isTouching) return

		if (animated) {
			progressAnimator?.cancel()
			val currentProgress = this.progress
			progressAnimator = ValueAnimator.ofFloat(currentProgress, progress).apply {
				duration = 220
				addUpdateListener { this@ToothyProgress.progress = it.animatedValue as Float }
				start()
			}
		} else {
			this.progress = progress
		}
	}

	fun setListener(listener: Listener) {
		this.listener = listener
	}

	fun setFractureDataPairs(data: List<Pair<Float, Float>>) {
		setFractureData(data.map { PointF(it.first, it.second) })
	}

	fun setFractureData(data: List<PointF>) {
		this.data.clear()
		this.fractureData.clear()
		this.data.addAll(getStepsDataByFracture(data))
		this.fractureData.addAll(data)

		invalidate()
	}

	fun getFractureData() = fractureData

	fun setFractureY(index: Int, @FloatRange(from = -1.0, to = 1.0) scale: Float) {
		var dataItem = data.getOrNull(index) ?: return
		var fractureItem = fractureData.getOrNull(index) ?: return

		dataItem = PointF(dataItem.x, (canvasHalfHeight + (scale * canvasHalfHeight)))
		fractureItem = PointF(fractureItem.x, scale)

		data[index] = dataItem
		fractureData[index] = fractureItem

		invalidate()
	}

	fun getFractureY(index: Int): Float {
		return fractureData.getOrNull(index)?.y ?: 0f
	}

	private fun stopTrackingTouch() {
		isTouching = false
		pointerPosition = -1f
		nearestApexIndex = -1

		invalidate()

		listener?.onStopTrackingTouch(progress)
	}

	private fun trackTouch(event: MotionEvent) {
		if (isTouching.not()) {
			listener?.onStartTrackingTouch(progress)
		}

		isTouching = true
		pointerPosition = when {
			event.x >= (canvasWidth + paddingEnd) -> canvasWidth.toFloat() + paddingEnd
			event.x <= paddingStart -> paddingStart.toFloat()
			else -> event.x
		}

		progress = ((pointerPosition - paddingStart) / canvasWidth.toFloat()).coerceAtMost(1f)

		if (isBuilderMode) {
			builderFindNearestApexForPointer(pointerPosition, event.y)
		}
	}

	private fun getStepsDataByFracture(data: List<PointF>): List<PointF> {
		val size = data.size
		val stepX = canvasWidth / size

		var prevX = paddingStart.toFloat()

		val lastIndex = size - 1

		return data.mapIndexed { index, value ->
			val x = when (index) {
				0 -> {
					.0f
				}
				lastIndex -> {
					canvasWidth.toFloat()
				}
				else -> {
					(stepX * value.x) + prevX
				}
			}

			prevX = x

			val y = canvasHalfHeight + (value.y * canvasHalfHeight)

			PointF(x, y)
		}
	}

	private fun drawPointer(canvas: Canvas) {
		if (pointerPosition < 0f) return
		if (isBuilderMode) return

		canvas.save()
		canvas.translate(0f, paddingTop.toFloat())

		canvas.drawLine(pointerPosition, 0f, pointerPosition, canvasHeight.toFloat(), trackPaint)

		canvas.restore()
	}

	private fun drawProgress(canvas: Canvas, paint: Paint, progress: Float = 1f) {
		if (data.isEmpty() || progress == 0f) return

		canvas.save()

		canvas.translate(paddingStart.toFloat(), paddingTop.toFloat())

		val first = data.first()

		var startX = first.x
		var startY = first.y
		val maxValue = progress * canvasWidth

		val path = Path()
		path.moveTo(startX, startY)

		for (nextIndex in 1 until data.size) {
			val point = data[nextIndex]

			val nextX = point.x.coerceAtMost(maxValue)
			val nextY = getCoordinateY(PointF(startX, startY), point, maxValue)

			path.lineTo(nextX, nextY)

			startX = nextX
			startY = nextY

			if (point.x > maxValue) break
		}

		canvas.drawPath(path, paint)

		canvas.restore()
	}

	private fun getCoordinateY(start: PointF, end: PointF, maxX: Float): Float {
		if (maxX >= end.x) return end.y

		val lambda = abs((start.x - maxX) / (maxX - end.x))

		return (start.y + end.y * lambda) / (1 + lambda)
	}

	//region Builder
	private fun builderMoveApex(event: MotionEvent) {
		val apex = nearestApex ?: return
		val fracture = fractureData.getOrNull(nearestApexIndex) ?: return

		val prevApex = data.getOrNull(nearestApexIndex - 1)
		val nextApex = data.getOrNull(nearestApexIndex + 1)

		apex.apply {
			this.x = event.x.coerceIn(prevApex?.x ?: paddingStart.toFloat(),
					nextApex?.x ?: canvasWidth.toFloat())
			this.y = event.y.coerceIn(0f, canvasHeight.toFloat())
		}

		var stepX: Float
		val prevX: Float

		when {
			prevApex != null -> {
				stepX = apex.x - prevApex.x
				prevX = prevApex.x
			}
			nextApex != null -> {
				stepX = nextApex.x - apex.x
				prevX = 1f
			}
			else -> {
				stepX = 1f
				prevX = 0f
			}
		}

		if (stepX == 0f) stepX = 1f

		fracture.apply {
			this.y = (apex.y - canvasHalfHeight) / canvasHalfHeight
			this.x = if (stepX != 0f) {
				(apex.x - prevX) / stepX
			} else {
				0f
			}
		}

		data[nearestApexIndex] = apex
		fractureData[nearestApexIndex] = fracture

		postInvalidate()
	}

	private fun builderDrawDebug(canvas: Canvas) {
		if (isBuilderMode.not()) return

		canvas.save()

		canvas.translate(paddingStart.toFloat(), paddingTop.toFloat())

		debugPaint.style = Paint.Style.STROKE
		canvas.drawRect(Rect(0, 0, canvasWidth, canvasHeight), debugPaint)

		val apex = nearestApex
		if (apex != null) {
			debugPaint.style = Paint.Style.FILL
			canvas.drawCircle(apex.x, apex.y, context.convertDpToPixel(6).toFloat(), debugPaint)
			canvas.drawLine(0f, apex.y, canvasWidth.toFloat(), apex.y, debugPaint)
			canvas.drawLine(apex.x, 0f, apex.x, canvasHeight.toFloat(), debugPaint)
		} else {
			debugPaint.style = Paint.Style.FILL
			canvas.drawLine(0f, canvasHalfHeight, canvasWidth.toFloat(), canvasHalfHeight, debugPaint)

			for (nextIndex in 1 until data.size) {
				val point = data[nextIndex]
				canvas.drawLine(point.x, 0f, point.x, canvasHeight.toFloat(), debugPaint)
			}
		}

		canvas.restore()
	}

	private fun builderFindNearestApexForPointer(pointerX: Float, pointerY: Float) {
		var lastL: Float = Float.MAX_VALUE

		val closestRange = canvasWidth / data.size.toFloat()

		for (i in 0 until data.size) {
			val apex = data[i]

			if (apex.x !in pointerX - closestRange..pointerX + closestRange) continue

			val coordV = PointF(pointerX - apex.x, pointerY - apex.y)

			val l = abs(sqrt(coordV.x * coordV.x + coordV.y * coordV.y))

			if (l < lastL) {
				nearestApexIndex = i
				lastL = l
			}
		}
	}

	//endregion

	//region Attributes
	private fun inflateAttrs(attrs: AttributeSet?) {
		val resAttrs = context.theme.obtainStyledAttributes(
				attrs,
				R.styleable.ToothyProgress,
				0,
				0
		)

		with(resAttrs) {
			progressStrokeCap = getCapType(getInt(R.styleable.ToothyProgress_strokeLineCapProgress, 1))
			progressBackgroundStrokeCap = getCapType(getInt(R.styleable.ToothyProgress_strokeLineCapProgressBackground, 1))
			strokeLineCapTrack = getCapType(getInt(R.styleable.ToothyProgress_strokeLineCapTrack, 1))

			progressColor = getColor(R.styleable.ToothyProgress_progressColor, progressColor)
			progressBackgroundColor = getColor(R.styleable.ToothyProgress_progressBackgroundColor, progressBackgroundColor)
			trackColor = getColor(R.styleable.ToothyProgress_trackColor, trackColor)

			progressWidth = getDimension(R.styleable.ToothyProgress_progressWidth, progressWidth)
			trackWidth = getDimension(R.styleable.ToothyProgress_progressWidth, trackWidth)
			progressBackgroundWidth = getDimension(R.styleable.ToothyProgress_progressBackgroundWidth, progressBackgroundWidth)

			progress = getFloat(R.styleable.ToothyProgress_progress, progress).coerceIn(0f, 1f)

			isBuilderMode = getBoolean(R.styleable.ToothyProgress_isBuilderMode, false)

			recycle()
		}
	}

	private fun getCapType(strokeLineCap: Int): Paint.Cap {
		return when (strokeLineCap) {
			0 -> Paint.Cap.BUTT
			1 -> Paint.Cap.ROUND
			else -> Paint.Cap.SQUARE
		}
	}
	//endregion

	//region Paint
	private fun getDebugPaint(): Paint {
		return Paint().apply {
			strokeCap = Paint.Cap.ROUND
			strokeWidth = context.convertDpToPixel(1).toFloat()
			style = Paint.Style.FILL
			color = Color.MAGENTA
			isAntiAlias = true
		}
	}

	private fun getMarkerPaint(): Paint {
		return Paint().apply {
			strokeCap = Paint.Cap.ROUND
			strokeWidth = context.convertDpToPixel(3).toFloat()
			style = Paint.Style.FILL
			color = trackColor
			isAntiAlias = true
		}
	}

	private fun getProgressPaint(): Paint {
		return Paint().apply {
			strokeCap = Paint.Cap.ROUND
			strokeWidth = context.convertDpToPixel(3).toFloat()
			style = Paint.Style.STROKE
			color = progressColor
			isAntiAlias = true
		}
	}

	private fun getProgressBackgroundPaint(): Paint {
		return Paint().apply {
			strokeCap = Paint.Cap.ROUND
			strokeWidth = context.convertDpToPixel(3).toFloat()
			style = Paint.Style.STROKE
			color = progressBackgroundColor
			isAntiAlias = true
		}
	}

	fun newApex(position: Int = data.size) {
		if ((position in 0..data.size).not()) return

		val nextY = nextFloat() * 2f - 1f
		this.fractureData.add(position, PointF(1 - nextY, nextY))

		setFractureData(ArrayList(fractureData))
	}
	//endregion

	interface Listener {
		fun onProgressChanged(progress: Float, fromUser: Boolean) {}
		fun onStartTrackingTouch(progress: Float) {}
		fun onStopTrackingTouch(progress: Float) {}
	}
}