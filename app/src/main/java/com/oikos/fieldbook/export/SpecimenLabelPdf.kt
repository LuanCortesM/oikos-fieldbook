package com.oikos.fieldbook.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.StyleSpan
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File

/**
 * PDF A4 com 8 etiquetas por folha (2 x 4, 100 x 70 mm), tamanho comum de
 * etiqueta de exsicata. Cada etiqueta tem moldura para o recorte; o texto
 * diminui ate caber. No limite, a descricao e encurtada com reticencias; se
 * ainda nao couber, o fim e cortado com reticencias. [write] devolve quantas
 * etiquetas foram encurtadas, para o app avisar.
 */
object SpecimenLabelPdf {
    private const val MM = 72f / 25.4f
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private val LABEL_WIDTH = 100 * MM
    private val LABEL_HEIGHT = 70 * MM
    private val PADDING = 4 * MM
    private val QR_SIZE = 18 * MM

    fun write(labels: List<LabelContent>, target: File): Int {
        val document = PdfDocument()
        var shortened = 0
        try {
            val marginX = (PAGE_WIDTH - 2 * LABEL_WIDTH) / 2
            val marginY = (PAGE_HEIGHT - 4 * LABEL_HEIGHT) / 2
            labels.chunked(8).forEachIndexed { pageIndex, pageLabels ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex + 1).create())
                pageLabels.forEachIndexed { index, label ->
                    val x = marginX + (index % 2) * LABEL_WIDTH
                    val y = marginY + (index / 2) * LABEL_HEIGHT
                    if (drawLabel(page.canvas, label, x, y)) shortened++
                }
                document.finishPage(page)
            }
            target.outputStream().use { document.writeTo(it) }
        } finally {
            document.close()
        }
        return shortened
    }

    /** Desenha a etiqueta; verdadeiro se o texto precisou ser encurtado. */
    private fun drawLabel(canvas: Canvas, label: LabelContent, x: Float, y: Float): Boolean {
        val border = Paint().apply {
            style = Paint.Style.STROKE
            color = Color.GRAY
            strokeWidth = 0.5f
        }
        canvas.drawRect(x, y, x + LABEL_WIDTH, y + LABEL_HEIGHT, border)
        if (label.code.isNotBlank()) drawQr(canvas, label.code, x + LABEL_WIDTH - PADDING - QR_SIZE, y + LABEL_HEIGHT - PADDING - QR_SIZE)

        // A coluna da direita fica para o QR: o texto nunca passa por cima dele.
        val textWidth = (LABEL_WIDTH - 2 * PADDING - QR_SIZE - 2 * MM).toInt()
        val bodyHeight = LABEL_HEIGHT - 2 * PADDING - 7f
        // Diminui a letra ate o conteudo caber; 5,5 pt e o limite de leitura.
        var size = 8.5f
        var layout: StaticLayout
        while (true) {
            layout = layoutFor(label, textWidth, size)
            if (layout.height <= bodyHeight || size <= MIN_SIZE) break
            size -= 0.5f
        }
        var shortened = false
        if (layout.height > bodyHeight) {
            shortened = true
            // Antes o clipRect cortava as ultimas linhas (coletor, numero,
            // data, determinador) sem sinal nenhum. Agora a descricao encolhe
            // primeiro, com reticencias.
            val index = label.shrinkableLine
            if (index in label.lines.indices) {
                val full = label.lines[index]
                var low = 0
                var high = full.length
                var best: StaticLayout? = null
                while (low <= high) {
                    val keep = (low + high) / 2
                    val candidate = layoutFor(label.withLine(index, full.take(keep).trimEnd() + "…"), textWidth, size)
                    if (candidate.height <= bodyHeight) {
                        best = candidate
                        low = keep + 1
                    } else {
                        high = keep - 1
                    }
                }
                best?.let { layout = it }
            }
            if (layout.height > bodyHeight) {
                // Nem sem a descricao coube: o fim vai com reticencias.
                val fitting = (0 until layout.lineCount).count { layout.getLineBottom(it) <= bodyHeight }.coerceAtLeast(1)
                layout = layoutFor(label, textWidth, size, maxLines = fitting)
            }
        }
        canvas.save()
        canvas.translate(x + PADDING, y + PADDING)
        canvas.clipRect(0f, 0f, textWidth.toFloat(), bodyHeight)
        layout.draw(canvas)
        canvas.restore()

        val codePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 5.5f
            typeface = Typeface.MONOSPACE
            color = Color.DKGRAY
        }
        canvas.drawText(label.code, x + PADDING, y + LABEL_HEIGHT - PADDING, codePaint)
        return shortened
    }

    private fun LabelContent.withLine(index: Int, text: String): LabelContent =
        copy(lines = lines.toMutableList().also { it[index] = text })

    private fun layoutFor(label: LabelContent, width: Int, size: Float, maxLines: Int = Int.MAX_VALUE): StaticLayout {
        val text = SpannableStringBuilder()
        if (label.header.isNotBlank()) text.append(label.header.uppercase()).append("\n")
        if (label.family.isNotBlank()) {
            val start = text.length
            text.append(label.family).append("\n")
            text.setSpan(StyleSpan(Typeface.BOLD), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (label.nameItalic.isNotBlank()) {
            fun italic(part: String) {
                val start = text.length
                text.append(part)
                text.setSpan(StyleSpan(Typeface.BOLD_ITALIC), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            fun roman(part: String) {
                val start = text.length
                text.append(part)
                text.setSpan(StyleSpan(Typeface.BOLD), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val words = label.nameItalic.split(' ').filter { it.isNotBlank() }
            when {
                // Convencao: "cf." e "aff." antes do epiteto ("Boana cf. faber").
                label.qualifier in setOf("cf.", "aff.") && words.size >= 2 -> {
                    italic(words.first())
                    roman(" ${label.qualifier} ")
                    italic(words.drop(1).joinToString(" "))
                }
                label.qualifier.isNotBlank() -> {
                    italic(label.nameItalic)
                    roman(" ${label.qualifier}")
                }
                else -> italic(label.nameItalic)
            }
            if (label.authorship.isNotBlank()) text.append(" ").append(label.authorship)
            text.append("\n")
        }
        label.lines.forEach { text.append(it).append("\n") }
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            color = Color.BLACK
            typeface = Typeface.SERIF
        }
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.05f)
            .setIncludePad(false)
            .setMaxLines(maxLines)
            .setEllipsize(if (maxLines == Int.MAX_VALUE) null else TextUtils.TruncateAt.END)
            .build()
    }

    private const val MIN_SIZE = 5.5f

    private fun drawQr(canvas: Canvas, code: String, x: Float, y: Float) {
        val matrix = QRCodeWriter().encode(
            code, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M)
        )
        val cell = QR_SIZE / matrix.width
        val paint = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
        for (row in 0 until matrix.height) {
            for (column in 0 until matrix.width) {
                if (matrix.get(column, row)) {
                    canvas.drawRect(x + column * cell, y + row * cell, x + (column + 1) * cell, y + (row + 1) * cell, paint)
                }
            }
        }
    }
}
