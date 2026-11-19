package com.squamed.core.laboratory.service

import java.io.ByteArrayOutputStream

/** One test row of the report table. */
data class LabReportRow(
  val test: String,
  val result: String,
  val unit: String,
  val reference: String,
  val flag: String,
)

/**
 * Minimal hand-rolled PDF 1.4 writer (02.04) — no PDF dependency. Uncompressed content stream so
 * integration tests can assert raw bytes; Helvetica core fonts, ASCII-only (non-ASCII → '?').
 */
object LabReportPdf {
  private const val PAGE_W = 595
  private const val MARGIN = 40

  fun render(
    hospitalName: String,
    orderNumber: String,
    reportVersion: Int,
    patientLines: List<String>,
    rows: List<LabReportRow>,
    amendmentLines: List<String>,
    footer: String,
  ): ByteArray {
    val text = StringBuilder()
    var y = 800

    fun line(
      font: String,
      size: Int,
      value: String,
      x: Int = MARGIN,
      step: Int = 18,
    ) {
      text.append("BT /$font $size Tf $x $y Td (${esc(value)}) Tj ET\n")
      y -= step
    }

    fun rule() {
      text.append("0.5 w $MARGIN $y m ${PAGE_W - MARGIN} $y l S\n")
      y -= 12
    }

    line("F1", 16, hospitalName, step = 22)
    line("F1", 11, "LABORATORY REPORT")
    rule()
    line("F2", 10, "Order: $orderNumber  (version $reportVersion)", step = 16)
    patientLines.forEach { line("F2", 10, it, step = 16) }
    y -= 8
    // Table header + rows: column x positions, cell text truncated to fit (~5.5px/char at 10pt).
    y -= 16

    fun row(cells: List<Pair<Int, String>>) {
      cells.forEach { (x, value) -> text.append("BT /F2 10 Tf $x $y Td (${esc(value)}) Tj ET\n") }
      y -= 16
    }
    row(listOf(MARGIN to fit("TEST", 26), 190 to fit("RESULT", 15), 290 to fit("UNIT", 10), 350 to fit("REFERENCE", 22), 470 to fit("FLAG", 14)))
    rows.take(24).forEach { r ->
      row(
        listOf(
          MARGIN to fit(r.test, 26),
          190 to fit(r.result, 15),
          290 to fit(r.unit, 10),
          350 to fit(r.reference, 22),
          470 to fit(r.flag, 14),
        ),
      )
    }
    y -= 10
    if (amendmentLines.isNotEmpty()) {
      line("F1", 10, "AMENDMENTS", step = 16)
      amendmentLines.take(8).forEach { line("F2", 10, it, step = 14) }
    }
    text.append("BT /F2 8 Tf $MARGIN 30 Td (${esc(footer)}) Tj ET\n")

    val content = text.toString()
    val buf = ByteArrayOutputStream()

    fun write(s: String) = buf.write(s.toByteArray(Charsets.US_ASCII))
    val bodies =
      listOf(
        "<< /Type /Catalog /Pages 2 0 R >>",
        "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R /F2 5 0 R >> >> /Contents 6 0 R >>",
        "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>",
        "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
        "<< /Length ${content.length} >>\nstream\n$content\nendstream",
      )
    write("%PDF-1.4\n")
    val offsets = IntArray(bodies.size)
    bodies.forEachIndexed { i, body ->
      offsets[i] = buf.size()
      write("${i + 1} 0 obj\n$body\nendobj\n")
    }
    val xrefStart = buf.size()
    write("xref\n0 ${bodies.size + 1}\n0000000000 65535 f \n")
    offsets.forEach { write("%010d 00000 n \n".format(it)) }
    write("trailer\n<< /Size ${bodies.size + 1} /Root 1 0 R >>\nstartxref\n$xrefStart\n%%EOF")
    return buf.toByteArray()
  }

  private fun esc(s: String) = ascii(s).replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")

  private fun ascii(s: String): String = buildString { s.forEach { append(if (it.code in 32..126) it else '?') } }

  private fun fit(
    s: String,
    max: Int,
  ): String = if (s.length <= max) s else s.take(max - 1) + "?"
}
