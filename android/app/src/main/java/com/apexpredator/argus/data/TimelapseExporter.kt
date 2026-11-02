package com.apexpredator.argus.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Renders recorded radar snapshots to an MP4 video (H.264 via
// MediaCodec, input Surface) that the user can share from the
// playback bar. One frame per snapshot at 10 fps, so a 60-frame
// recording becomes a 6-second video. The frames are drawn on a
// canvas from snapshot data, not captured from the map view, so the
// export works even when the radar tab is not on screen. Returns the
// file on success, or null with the reason in the out parameter.
object TimelapseExporter {

    const val FRAME_SIZE = 720
    const val FPS = 10
    const val MAX_FRAMES = 120

    fun export(
        context: Context,
        frames: List<Snapshot>,
        onError: (String) -> Unit = {}
    ): File? {
        if (frames.isEmpty()) {
            onError("No recorded frames to export.")
            return null
        }
        val chosen = selectTimelapseFrames(frames, MAX_FRAMES)
        val bounds = timelapseBounds(chosen)
        val dir = File(context.cacheDir, "timelapse").apply { mkdirs() }
        val out = File(dir, "argus-timelapse-${System.currentTimeMillis()}.mp4")
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var surface: Surface? = null
        try {
            val format = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC, FRAME_SIZE, FRAME_SIZE
            ).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                )
                setInteger(MediaFormat.KEY_BIT_RATE, 2_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            surface = codec.createInputSurface()
            codec.start()
            muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)
            var trackIndex = -1
            var muxerStarted = false
            var eos = false
            var frameIndex = 0
            val bufferInfo = MediaCodec.BufferInfo()
            while (!eos) {
                if (frameIndex < chosen.size) {
                    val canvas = surface.lockCanvas(null)
                    try {
                        drawFrame(canvas, chosen[frameIndex], bounds, timeFmt)
                    } finally {
                        surface.unlockCanvasAndPost(canvas)
                    }
                    frameIndex++
                    if (frameIndex >= chosen.size) {
                        codec.signalEndOfInputStream()
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (muxerStarted) {
                            onError("Encoder changed format mid-stream.")
                            return null
                        }
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    outIndex >= 0 -> {
                        val buffer = codec.getOutputBuffer(outIndex)
                        if (buffer != null && bufferInfo.size > 0 && muxerStarted) {
                            buffer.position(bufferInfo.offset)
                            buffer.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackIndex, buffer, bufferInfo)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            eos = true
                        }
                    }
                }
            }
            if (!muxerStarted) {
                onError("Encoder produced no video track.")
                return null
            }
            return out
        } catch (e: Exception) {
            out.delete()
            onError(e.message ?: "Video encoding failed.")
            return null
        } finally {
            try { surface?.release() } catch (_: Exception) {}
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            try { muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }

    private val bgPaint = Paint().apply { color = Color.rgb(0x0B, 0x10, 0x20) }
    private val gridPaint = Paint().apply {
        color = Color.rgb(0x2A, 0x35, 0x55)
        strokeWidth = 1f
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 28f
        isAntiAlias = true
    }
    private val smallTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 20f
        isAntiAlias = true
    }
    private val satPaint = Paint().apply {
        color = Color.rgb(0xFF, 0xD7, 0x00)
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val localPaint = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    private fun drawFrame(
        canvas: Canvas,
        frame: Snapshot,
        bounds: TimelapseBounds,
        timeFmt: SimpleDateFormat
    ) {
        canvas.drawRect(0f, 0f, FRAME_SIZE.toFloat(), FRAME_SIZE.toFloat(), bgPaint)
        for (i in 1 until 6) {
            val p = i * FRAME_SIZE / 6f
            canvas.drawLine(p, 0f, p, FRAME_SIZE.toFloat(), gridPaint)
            canvas.drawLine(0f, p, FRAME_SIZE.toFloat(), p, gridPaint)
        }
        for (sat in frame.satellites) {
            val (x, y) = projectToFrame(sat.lat, sat.lon, bounds, FRAME_SIZE)
            drawStar(canvas, x, y, 12f)
        }
        val showLabels = frame.aircraft.size <= 30
        for (ac in frame.aircraft) {
            val (x, y) = projectToFrame(ac.latitude, ac.longitude, bounds, FRAME_SIZE)
            drawAircraft(canvas, x, y, ac.trueTrackDeg)
            if (ac.isLocal) canvas.drawCircle(x, y, 18f, localPaint)
            if (showLabels && !ac.callsign.isNullOrBlank()) {
                canvas.drawText(ac.callsign.trim(), x + 16f, y - 10f, smallTextPaint)
            }
        }
        canvas.drawText(
            "Apex Argus timelapse · ${timeFmt.format(Date(frame.epochMs))}",
            16f, FRAME_SIZE - 20f, textPaint
        )
    }

    private fun drawStar(canvas: Canvas, x: Float, y: Float, r: Float) {
        val path = Path()
        for (i in 0 until 8) {
            val angle = i * PI / 4 - PI / 2
            val radius = if (i % 2 == 0) r else r * 0.45f
            val px = (x + cos(angle) * radius).toFloat()
            val py = (y + sin(angle) * radius).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        canvas.drawPath(path, satPaint)
    }

    private fun drawAircraft(canvas: Canvas, x: Float, y: Float, headingDeg: Double?) {
        val paint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val heading = Math.toRadians(headingDeg ?: 0.0)
        val nose = 14f
        val wing = 10f
        val back = 8f
        fun point(angleOffset: Double, dist: Float): Pair<Float, Float> {
            val a = heading + angleOffset
            return (x + sin(a) * dist).toFloat() to (y - cos(a) * dist).toFloat()
        }
        val path = Path()
        val (nx, ny) = point(0.0, nose)
        val (lx, ly) = point(2.5, wing)
        val (bx, by) = point(PI, back)
        val (rx, ry) = point(-2.5, wing)
        path.moveTo(nx, ny)
        path.lineTo(lx, ly)
        path.lineTo(bx, by)
        path.lineTo(rx, ry)
        path.close()
        canvas.drawPath(path, paint)
    }

    // Kept for callers that render a single preview bitmap.
    fun renderBitmap(frame: Snapshot, bounds: TimelapseBounds): Bitmap {
        val bitmap = Bitmap.createBitmap(FRAME_SIZE, FRAME_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawFrame(canvas, frame, bounds, SimpleDateFormat("HH:mm:ss", Locale.US))
        return bitmap
    }
}
