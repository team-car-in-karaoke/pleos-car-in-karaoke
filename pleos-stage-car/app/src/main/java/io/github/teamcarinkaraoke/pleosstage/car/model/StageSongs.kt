package io.github.teamcarinkaraoke.pleosstage.car.model

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlin.math.pow

data class LyricLine(
    val startSec: Float,
    val endSec: Float,
    val koreanText: String,
    val subText: String,
    val targetPitchHz: Float,
    val targetNote: String,
    val isHighlight: Boolean = false
)

data class StageSong(
    val id: String,
    val title: String,
    val artist: String,
    val genre: String,
    val bpm: Int,
    val durationSec: Int,
    val accentHex: Long,
    val lyrics: List<LyricLine>
) {
    fun shiftedPitch(baseHz: Float, keyShiftSemitones: Int): Float {
        return (baseHz * 2.0.pow(keyShiftSemitones / 12.0)).toFloat()
    }
}

object StageSongCatalog {
    val DEMO_SONGS = emptyList<StageSong>()
}

object QrCodeGenerator {
    fun generateQrBitmap(content: String, sizePx: Int = 360): Bitmap {
        return try {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to 1
            )
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    bmp.setPixel(
                        x,
                        y,
                        if (matrix.get(x, y)) Color.parseColor("#0B0E14") else Color.WHITE
                    )
                }
            }
            bmp
        } catch (e: Exception) {
            Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        }
    }
}
