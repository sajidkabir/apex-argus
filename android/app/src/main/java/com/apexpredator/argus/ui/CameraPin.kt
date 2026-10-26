package com.apexpredator.argus.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

// A camera pin drawn in code: blue disc, white lens ring. Shared by
// the Cams map markers and the radar camera layer, so both maps read
// the same. No asset files, no icon-font dependency that could break
// the build.
fun cameraMarkerBitmap(): Bitmap {
    val size = 96
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1976D2")
        style = Paint.Style.FILL
    }
    val lens = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2f, disc)
    canvas.drawCircle(size / 2f, size / 2f, size / 4f, ring)
    canvas.drawCircle(size / 2f, size / 2f, size / 8f, lens)
    return bitmap
}
