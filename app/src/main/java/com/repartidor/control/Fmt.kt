package com.repartidor.control

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

val DIAS = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
val DIAS_CORTOS = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
val MESES = listOf(
    "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto",
    "Septiembre", "Octubre", "Noviembre", "Diciembre"
)

private val REGEX_MILES = Regex("""\d{1,3}(\.\d{3})+""")
private val FMT_UI = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val FMT_CORTO = DateTimeFormatter.ofPattern("dd/MM")

/** Texto -> monto. Acepta "1.234,50", "1500", "88897.62", "$ 2,5". Vacío = 0. Inválido o negativo = null. */
fun num(texto: String): Double? {
    var t = texto.trim().replace("$", "").replace(" ", "")
    if (t.isEmpty()) return 0.0
    if (t.contains(",")) {
        t = t.replace(".", "").replace(",", ".")
    } else if (REGEX_MILES.matches(t)) {
        t = t.replace(".", "")
    }
    val v = t.toDoubleOrNull() ?: return null
    if (v < 0 || v.isNaN() || v.isInfinite()) return null
    return v
}

/** Número sin símbolo, estilo argentino: 88.897,62 */
fun fmtMonto(x: Double): String {
    val s = String.format(Locale.US, "%,.2f", x)
    return s.replace(",", "X").replace(".", ",").replace("X", ".")
}

fun money(x: Double, decimales: Int = 2): String {
    val s = String.format(Locale.US, "%,." + decimales + "f", abs(x))
        .replace(",", "X").replace(".", ",").replace("X", ".")
    return if (x < -0.004) "-\$ $s" else "\$ $s"
}

fun money0(x: Double): String = money(x, 0)

fun fmtFecha(d: LocalDate): String = d.format(FMT_UI)
fun fmtCorto(d: LocalDate): String = d.format(FMT_CORTO)

fun parseFecha(s: String): LocalDate? {
    val t = s.trim()
    for (p in listOf("dd/MM/yyyy", "dd-MM-yyyy", "yyyy-MM-dd", "dd/MM/yy")) {
        try {
            return LocalDate.parse(t, DateTimeFormatter.ofPattern(p))
        } catch (e: Exception) {
            // probar el siguiente formato
        }
    }
    return null
}

fun diaSemana(d: LocalDate): Int = d.dayOfWeek.value - 1   // 0 = lunes ... 6 = domingo

fun finDeMes(d: LocalDate): LocalDate = d.withDayOfMonth(d.lengthOfMonth())

fun sumarMeses(d: LocalDate, n: Int): LocalDate = d.withDayOfMonth(1).plusMonths(n.toLong())

/** Semana de 7 días que termina en el día de cierre (0 = lunes ... 6 = domingo). */
fun periodoSemana(ref: LocalDate, cierre: Int): Pair<LocalDate, LocalDate> {
    val delta = (((cierre - diaSemana(ref)) % 7) + 7) % 7
    val fin = ref.plusDays(delta.toLong())
    return Pair(fin.minusDays(6), fin)
}

/** Primer día de cobro posterior al cierre de la semana. */
fun fechaCobro(finSemana: LocalDate, diaCobro: Int): LocalDate {
    var delta = (((diaCobro - diaSemana(finSemana)) % 7) + 7) % 7
    if (delta == 0) delta = 7
    return finSemana.plusDays(delta.toLong())
}

/** "18", "18:30", "18.30", "18h30" -> minutos desde medianoche. Vacío o inválido -> null. */
fun parseHora(s: String): Int? {
    val t = s.trim().lowercase().replace("hs", "").replace("h", ":").replace(".", ":")
    if (t.isEmpty()) return null
    val p = t.split(":")
    val h = p[0].toIntOrNull() ?: return null
    val m = if (p.size > 1 && p[1].isNotEmpty()) (p[1].toIntOrNull() ?: return null) else 0
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

fun fmtHora(minutos: Int): String = String.format(Locale.US, "%02d:%02d", minutos / 60, minutos % 60)

/** Horas entre dos "HH:MM" (soporta cruzar la medianoche). 0 si falta alguno. */
fun duracionHoras(ini: String?, fin: String?): Double {
    val a = parseHora(ini ?: "") ?: return 0.0
    val b = parseHora(fin ?: "") ?: return 0.0
    return (((b - a) % 1440) + 1440) % 1440 / 60.0
}

val FRANJAS = listOf("Madrugada (0–6)", "Mañana (6–12)", "Mediodía (12–15)", "Tarde (15–19)", "Noche (19–24)")

fun franja(horaInicio: String): String {
    val h = (parseHora(horaInicio) ?: 0) / 60
    return FRANJAS[if (h < 6) 0 else if (h < 12) 1 else if (h < 15) 2 else if (h < 19) 3 else 4]
}
