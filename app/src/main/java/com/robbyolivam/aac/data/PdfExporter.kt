package com.robbyolivam.aac.data

import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.text.TextUtils
import com.robbyolivam.aac.core.Board
import com.robbyolivam.aac.core.Cell
import com.robbyolivam.aac.core.Hotspot
import com.robbyolivam.aac.core.Vocabulary
import com.robbyolivam.aac.core.fitzgeraldRgb
import java.io.OutputStream

/**
 * Prints every page of the vocabulary as a paper communication board (a no-tech backup for
 * dead batteries, the pool, or the beach), one board per landscape US-letter page.
 */
class PdfExporter(private val store: VocabularyStore) {

    fun export(vocabulary: Vocabulary, level: Int, output: OutputStream) {
        val doc = PdfDocument()
        vocabulary.boards.forEachIndexed { i, board ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, i + 1).create())
            drawBoard(page.canvas, board, if (board.id == vocabulary.homeBoardId) vocabulary.quickStrip else emptyList(), level)
            doc.finishPage(page)
        }
        doc.writeTo(output)
        doc.close()
    }

    private fun drawBoard(canvas: Canvas, board: Board, strip: List<Cell>, level: Int) {
        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 20f; typeface = Typeface.DEFAULT_BOLD }
        canvas.drawText(board.name, MARGIN, MARGIN + 16f, title)
        val top = MARGIN + 30f
        val bottom = PAGE_H - MARGIN - if (strip.isNotEmpty()) 64f else 0f
        val area = RectF(MARGIN, top, PAGE_W - MARGIN, bottom)

        val scene = board.scene
        if (scene != null) {
            drawScene(canvas, scene.imagePath, scene.hotspots, area)
            return
        }
        val cellW = area.width() / board.cols
        val cellH = area.height() / board.rows
        board.cells.forEachIndexed { index, cell ->
            if (cell == null || !cell.visibleAt(level)) return@forEachIndexed
            val r = index / board.cols
            val c = index % board.cols
            drawCell(canvas, cell, RectF(area.left + c * cellW, area.top + r * cellH, area.left + (c + 1) * cellW, area.top + (r + 1) * cellH))
        }
        if (strip.isNotEmpty()) {
            val w = area.width() / strip.size
            strip.forEachIndexed { i, cell ->
                drawCell(canvas, cell, RectF(area.left + i * w, bottom + 8f, area.left + (i + 1) * w, PAGE_H - MARGIN))
            }
        }
    }

    private fun drawCell(canvas: Canvas, cell: Cell, bounds: RectF) {
        val r = RectF(bounds.left + 2f, bounds.top + 2f, bounds.right - 2f, bounds.bottom - 2f)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF000000.toInt() or fitzgeraldRgb(cell.type) }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = 0xFF555555.toInt() }
        canvas.drawRoundRect(r, 8f, 8f, fill)
        canvas.drawRoundRect(r, 8f, 8f, stroke)

        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = (r.height() * 0.16f).coerceIn(7f, 14f)
            typeface = Typeface.DEFAULT_BOLD
            color = 0xFF1A1A1A.toInt()
            textAlign = Paint.Align.CENTER
        }
        val label = TextUtils.ellipsize(cell.label, labelPaint, r.width() - 6f, TextUtils.TruncateAt.END).toString()
        canvas.drawText(label, r.centerX(), r.bottom - 6f, labelPaint)

        val symbolBox = RectF(r.left + 4f, r.top + 4f, r.right - 4f, r.bottom - labelPaint.textSize - 10f)
        val imagePath = cell.symbol.imagePath
        val bitmap = imagePath?.let { BitmapFactory.decodeFile(store.resolve(it).absolutePath) }
        if (bitmap != null) {
            canvas.drawBitmap(bitmap, null, fit(bitmap.width.toFloat(), bitmap.height.toFloat(), symbolBox), null)
        } else if (!cell.symbol.emoji.isNullOrBlank()) {
            val emojiPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = minOf(symbolBox.height(), symbolBox.width()) * 0.75f
                textAlign = Paint.Align.CENTER
            }
            val y = symbolBox.centerY() - (emojiPaint.descent() + emojiPaint.ascent()) / 2
            canvas.drawText(cell.symbol.emoji!!, symbolBox.centerX(), y, emojiPaint)
        }
    }

    private fun drawScene(canvas: Canvas, imagePath: String, spots: List<Hotspot>, area: RectF) {
        val bitmap = BitmapFactory.decodeFile(store.resolve(imagePath).absolutePath) ?: return
        val dest = fit(bitmap.width.toFloat(), bitmap.height.toFloat(), area)
        canvas.drawBitmap(bitmap, null, dest, null)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f }
        val label = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f; typeface = Typeface.DEFAULT_BOLD }
        val labelBg = Paint().apply { color = 0xDDFFFFFF.toInt() }
        for (spot in spots) {
            val cell = spot.cell
            val rect = RectF(
                dest.left + spot.x * dest.width(),
                dest.top + spot.y * dest.height(),
                dest.left + (spot.x + spot.width) * dest.width(),
                dest.top + (spot.y + spot.height) * dest.height(),
            )
            stroke.color = 0xFF000000.toInt() or fitzgeraldRgb(cell.type)
            canvas.drawRoundRect(rect, 8f, 8f, stroke)
            val w = label.measureText(cell.label)
            canvas.drawRect(rect.left, rect.bottom - 18f, rect.left + w + 8f, rect.bottom, labelBg)
            canvas.drawText(cell.label, rect.left + 4f, rect.bottom - 5f, label)
        }
    }

    private fun fit(w: Float, h: Float, box: RectF): RectF {
        val scale = minOf(box.width() / w, box.height() / h)
        val dw = w * scale
        val dh = h * scale
        return RectF(box.centerX() - dw / 2, box.centerY() - dh / 2, box.centerX() + dw / 2, box.centerY() + dh / 2)
    }

    private companion object {
        const val PAGE_W = 792 // US letter, landscape, in points
        const val PAGE_H = 612
        const val MARGIN = 28f
    }
}
