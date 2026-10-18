package com.oikos.fieldbook.services

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.oikos.fieldbook.export.LabelContent
import com.oikos.fieldbook.export.SpecimenLabelPdf
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** O PDF abre, tem as folhas certas e o QR impresso devolve o occurrenceID. */
@RunWith(AndroidJUnit4::class)
class SpecimenLabelPdfTest {

    @Test
    fun noveEtiquetasOcupamDuasFolhasEOQrLeOCodigo() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val label = LabelContent(
            header = "JBRJ · Bromélias do Arboreto",
            family = "BROMELIACEAE",
            nameItalic = "Vriesea neoglutinosa",
            authorship = "Mez",
            lines = List(12) { "Linha $it de texto longo para forçar a redução da letra na etiqueta de exsicata." },
            code = "BI-20260314-0009-AB12CD34"
        )
        val file = File(context.cacheDir, "etiquetas-teste.pdf")
        SpecimenLabelPdf.write(List(9) { label }, file)

        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                assertEquals(2, renderer.pageCount)
                renderer.openPage(0).use { page ->
                    // 3x a resolucao do PDF (216 dpi): suficiente para o QR de 18 mm.
                    val bitmap = Bitmap.createBitmap(page.width * 3, page.height * 3, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    // Recorta a primeira etiqueta (canto superior esquerdo) para ler um QR so.
                    val cropWidth = (bitmap.width / 2)
                    val cropHeight = (bitmap.height / 4)
                    val crop = Bitmap.createBitmap(bitmap, 0, 0, cropWidth, cropHeight)
                    val pixels = IntArray(crop.width * crop.height)
                    crop.getPixels(pixels, 0, crop.width, 0, 0, crop.width, crop.height)
                    val result = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(crop.width, crop.height, pixels))))
                    assertEquals("BI-20260314-0009-AB12CD34", result.text)
                }
            }
        }
        file.delete()
    }
}
