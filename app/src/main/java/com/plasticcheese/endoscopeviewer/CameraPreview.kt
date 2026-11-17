package com.plasticcheese.endoscopeviewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.view.TextureView
import android.view.ViewGroup
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip

internal class CameraPreview(
    context: Context,
    private val session: CameraSession,
) : ViewGroup(context), TextureView.SurfaceTextureListener {
    private val texture = TextureView(context)

    private var frame: Resolution? = null
    private var transform = PreviewTransform()
    private var viewportWidth = 0
    private var viewportHeight = 0

    init {
        texture.surfaceTextureListener = this
        addView(texture)
    }

    fun update(frame: Resolution?, transform: PreviewTransform) {
        if (this.frame == frame && this.transform == transform) return
        val layoutChanged = this.frame != frame || this.transform.sideways != transform.sideways
        this.frame = frame
        this.transform = transform

        texture.rotation = transform.degrees
        texture.scaleX = transform.zoom * if (transform.sideways) 1f else transform.horizontalSign
        texture.scaleY = transform.zoom * if (transform.sideways) transform.horizontalSign else 1f
        if (layoutChanged) requestLayout()
        invalidate()
    }

    fun snapshot(): Bitmap? {
        val frame = frame ?: return null
        if (session.status != CameraStatus.Streaming || !texture.isAvailable) return null
        val raw = texture.getBitmap(frame.width, frame.height) ?: return null
        if (transform == PreviewTransform()) return raw

        try {
            val output = if (transform.sideways) {
                createBitmap(frame.height, frame.width)
            } else {
                createBitmap(frame.width, frame.height)
            }
            Canvas(output).apply {
                translate(output.width / 2f, output.height / 2f)
                scale(transform.zoom * transform.horizontalSign, transform.zoom)
                rotate(transform.degrees)
                drawBitmap(raw, -raw.width / 2f, -raw.height / 2f, Paint(Paint.FILTER_BITMAP_FLAG))
            }
            return output
        } finally {
            raw.recycle()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)

        val viewport = frame?.let { transform.fit(it, width, height) }
        viewportWidth = viewport?.width ?: width
        viewportHeight = viewport?.height ?: height
        val textureWidth = if (transform.sideways) viewportHeight else viewportWidth
        val textureHeight = if (transform.sideways) viewportWidth else viewportHeight
        texture.measure(
            MeasureSpec.makeMeasureSpec(textureWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(textureHeight, MeasureSpec.EXACTLY),
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val x = (width - texture.measuredWidth) / 2
        val y = (height - texture.measuredHeight) / 2
        texture.layout(x, y, x + texture.measuredWidth, y + texture.measuredHeight)
    }

    override fun dispatchDraw(canvas: Canvas) {
        val left = (width - viewportWidth) / 2f
        val top = (height - viewportHeight) / 2f
        canvas.withClip(left, top, left + viewportWidth, top + viewportHeight) {
            super.dispatchDraw(canvas)
        }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        session.setSurface(surface)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        session.setSurface(null)
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        if (surface.timestamp != 0L) session.onFrame()
    }
}
