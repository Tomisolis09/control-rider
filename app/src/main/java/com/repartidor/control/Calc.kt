package com.repartidor.control

import java.time.LocalDate

data class Totales(
    val base: Double, val adic: Double, val grupo: Double, val bono: Double, val propinas: Double,
    val efectivo: Double, val pedidos: Int, val gastos: Double
) {
    val bruto: Double get() = base + adic + grupo + bono
    val neto: Double get() = bruto - efectivo               // saldo: positivo = a cobrar, negativo = deuda con la app
    val promedio: Double get() = if (pedidos > 0) bruto / pedidos else 0.0
}

data class DiaRow(
    val fecha: LocalDate, val n: Int, val b: Double, val a: Double, val g: Double,
    val bn: Double, val p: Double, val e: Double
) {
    val saldo: Double get() = b + a + g + bn - e
}

data class Equilibrio(
    val objetivo: Double, val fijosDia: Double, val gastosDia: Double, val fondoDia: Double,
    val ingresoPedido: Double, val diasTrab: Int, val descDia: Double = 0.0
)

data class Recordatorio(
    val id: Long, val vehiculo: String, val item: String, val cadaDias: Int, val cadaPedidos: Int, val cadaKm: Int,
    val ultimo: LocalDate, val conRegistro: Boolean, val dias: Int, val pedidos: Int, val kmRec: Int?,
    val prog: Double, val estado: String
)

data class Vencimiento(val id: Long, val concepto: String, val fecha: LocalDate, val dias: Int, val estado: String)
data class Cobro(
    val cobro: LocalDate, val ini: LocalDate, val fin: LocalDate, val estado: String,
    val neto: Double, val propinas: Double, val descuentos: Double = 0.0, val arrastre: Double = 0.0
)

class DescSemana(val seguro: Double, val credito: Double) {
    val total: Double get() = seguro + credito
}

/**
 * Liquidación de una semana. El seguro y el crédito de la app se cargan por día y se descuentan del saldo de la
 * semana ANTERIOR: [seguro] y [credito] son los cargados durante la semana siguiente a [fin].
 * total = deuda arrastrada (negativa) + saldo de los turnos − seguro − crédito.
 * Si el total es positivo se cobra y la semana siguiente arranca en cero; si es negativo es deuda con la app
 * y pasa a la semana siguiente.
 */
class CuentaSemana(
    val ini: LocalDate, val fin: LocalDate, val turnos: Double, val seguro: Double, val credito: Double,
    val arrastre: Double
) {
    val total: Double get() = arrastre + turnos - seguro - credito
    val aCobrar: Double get() = maxOf(0.0, total)
    val deuda: Double get() = maxOf(0.0, -total)
}

/** Consumo de combustible entre recargas (se asume que cada recarga llena el tanque). */
class Consumo(val km: Int, val litros: Double, val gasto: Double) {
    val kmPorLitro: Double get() = if (litros > 0) km / litros else 0.0
    val costoKm: Double get() = if (km > 0) gasto / km else 0.0
}

data class FondoEstado(val apartado: Double, val usado: Double) {
    val disponible: Double get() = apartado - usado
}
data class AhorroHoy(
    val estado: String, val objetivo: Double, val hecho: Double, val falta: Double, val meta: String, val modo: String
)

data class Plan(
    val estado: String,                       // sin_meta | cumplida | sin_datos | sin_margen | ok
    val nombre: String = "", val objetivo: Double = 0.0, val inicial: Double = 0.0,
    val limite: LocalDate? = null, val inicio: LocalDate? = null, val diasSemana: Int = 6,
    val total: Double = 0.0, val falta: Double = 0.0, val pct: Double = 0.0,
    val diasTrab: Int = 0, val pedidosDia: Double = 0.0, val porDia: Double = 0.0, val porSemBruta: Double = 0.0,
    val fijosMes: Double = 0.0, val fijosSem: Double = 0.0, val porSem: Double = 0.0, val porDiaLibre: Double = 0.0,
    val prom: Double = 0.0, val semNec: Double? = null, val diasRest: Int? = null,
    val necSem: Double? = null, val necDia: Double? = null, val factible: Boolean? = null,
    val objDia: Double = 0.0, val modo: String = "", val descSem: Double = 0.0
)

/** Toda la lógica de cálculo de la app (portada de la versión de escritorio). */
class Calc(val db: Db) {

    // ---------------------------------------------------------------- cuenta semanal (deuda que pasa a la semana siguiente)
    private val serie = HashMap<LocalDate, CuentaSemana>()
    private var ultimoFin: LocalDate? = null
    private var arrastreSiguiente = 0.0

    /** Descarta lo calculado: se llama cada vez que cambian los datos. */
    fun invalidar() {
        serie.clear()
        ultimoFin = null
        arrastreSiguiente = 0.0
    }

    private fun primeraSemana(): LocalDate? {
        val fechas = ArrayList<LocalDate>()
        db.one("SELECT MIN(fecha) f FROM diario")?.sn("f")?.let { fechas.add(LocalDate.parse(it)) }
        // un descuento cargado en una semana liquida la semana anterior
        db.one("SELECT MIN(fecha) f FROM cargos_app")?.sn("f")?.let { fechas.add(LocalDate.parse(it).minusDays(7)) }
        if (fechas.isEmpty()) return null
        val primera = fechas.reduce { a, b -> if (a.isBefore(b)) a else b }
        return periodoSemana(primera, db.diaCierre).second
    }

    private fun semanaSola(fin: LocalDate): CuentaSemana {
        val ini = fin.minusDays(6)
        val d = descuentoSemana(fin)
        return CuentaSemana(ini, fin, totales(ini, fin).neto, d.seguro, d.credito, 0.0)
    }

    /**
     * Cuenta de la semana que termina el día [fin]. La deuda con la app de las semanas anteriores
     * se arrastra: se va descontando con lo que se hace en las semanas siguientes.
     */
    fun cuentaSemana(fin: LocalDate): CuentaSemana {
        serie[fin]?.let { return it }
        val primero = primeraSemana()
        if (primero == null || fin.isBefore(primero)) return semanaSola(fin)
        if (ultimoFin == null) {
            ultimoFin = primero.minusDays(7)
            arrastreSiguiente = 0.0
        }
        var f = ultimoFin!!
        while (f.isBefore(fin)) {
            f = f.plusDays(7)
            val ini = f.minusDays(6)
            val d = descuentoSemana(f)
            val cs = CuentaSemana(ini, f, totales(ini, f).neto, d.seguro, d.credito, arrastreSiguiente)
            serie[f] = cs
            arrastreSiguiente = minOf(0.0, cs.total)
            ultimoFin = f
        }
        return serie[fin] ?: semanaSola(fin)
    }

    // ---------------------------------------------------------------- totales
    fun totales(desde: LocalDate, hasta: LocalDate): Totales {
        val r = db.one(
            """SELECT COALESCE(SUM(base),0) b, COALESCE(SUM(adicionales),0) a, COALESCE(SUM(grupo),0) g,
               COALESCE(SUM(bono),0) bn, COALESCE(SUM(propinas),0) p, COALESCE(SUM(efectivo),0) e,
               COALESCE(SUM(pedidos),0) n FROM diario WHERE fecha BETWEEN ? AND ?""",
            desde.toString(), hasta.toString()
        )!!
        val gastos = db.one(
            "SELECT COALESCE(SUM(monto),0) m FROM gastos WHERE fecha BETWEEN ? AND ?",
            desde.toString(), hasta.toString()
        )!!.d("m")
        return Totales(r.d("b"), r.d("a"), r.d("g"), r.d("bn"), r.d("p"), r.d("e"), r.i("n"), gastos)
    }

    fun porDia(desde: LocalDate, hasta: LocalDate): List<DiaRow> = db.rows(
        """SELECT fecha, SUM(pedidos) n, SUM(base) b, SUM(adicionales) a, SUM(grupo) g,
           SUM(COALESCE(bono,0)) bn, SUM(propinas) p, SUM(efectivo) e FROM diario
           WHERE fecha BETWEEN ? AND ? GROUP BY fecha ORDER BY fecha""",
        desde.toString(), hasta.toString()
    ).map { DiaRow(LocalDate.parse(it.s("fecha")), it.i("n"), it.d("b"), it.d("a"), it.d("g"), it.d("bn"), it.d("p"), it.d("e")) }

    fun diasTrabajados(desde: LocalDate, hasta: LocalDate): Int = db.one(
        "SELECT COUNT(DISTINCT fecha) c FROM diario WHERE fecha BETWEEN ? AND ?", desde.toString(), hasta.toString()
    )!!.i("c")

    // ---------------------------------------------------------------- gastos fijos y fondo de repuestos
    fun totalFijos(desde: LocalDate, hasta: LocalDate): Double = db.one(
        "SELECT COALESCE(SUM(monto),0) m FROM gastos_fijos WHERE fecha BETWEEN ? AND ?",
        desde.toString(), hasta.toString()
    )!!.d("m")

    /** Gastos fijos mensuales de referencia: el mes actual o, si todavía está vacío, el anterior. */
    fun fijosReferencia(hoy: LocalDate): Double {
        val mes = hoy.withDayOfMonth(1)
        var total = totalFijos(mes, finDeMes(mes))
        if (total <= 0) {
            val ant = sumarMeses(mes, -1)
            total = totalFijos(ant, finDeMes(ant))
        }
        return total
    }

    fun fondoAporte(pedidos: Int, bruto: Double): Double {
        val tipo = db.cfgI("fondo_tipo", 0)
        val valor = db.cfgD("fondo_valor", 0.0)
        return if (tipo == 1) pedidos * valor else if (tipo == 2) bruto * valor / 100 else 0.0
    }

    fun fondoEstado(hoy: LocalDate): FondoEstado? {
        val desde = parseFecha(db.cfgS("fondo_desde", ""))
        if (db.cfgI("fondo_tipo", 0) == 0 || desde == null) return null
        val t = totales(desde, hoy)
        val usado = db.one(
            "SELECT COALESCE(SUM(costo),0) c FROM mantenimiento WHERE fecha >= ?", desde.toString()
        )!!.d("c")
        return FondoEstado(fondoAporte(t.pedidos, t.bruto), usado)
    }

    /** Cuánto hay que facturar por día trabajado para cubrir hogar + gastos de trabajo + fondo de repuestos. */
    fun equilibrio(hoy: LocalDate): Equilibrio {
        val desde = hoy.minusDays(29)
        val t = totales(desde, hoy)
        val diasTrab = diasTrabajados(desde, hoy)
        val diasSem = db.cfgI("dias_semana", 6).coerceIn(1, 7)
        val fijosDia = fijosReferencia(hoy) * 12 / 52 / diasSem
        val gastosDia = if (diasTrab > 0) t.gastos / diasTrab else 0.0
        val fondoDia = if (diasTrab > 0) fondoAporte(t.pedidos, t.bruto) / diasTrab else 0.0
        val ingresoPedido = if (t.pedidos > 0) (t.bruto + t.propinas) / t.pedidos else 0.0
        val descDia = descuentoEstimado(hoy).total / diasSem
        return Equilibrio(fijosDia + gastosDia + fondoDia + descDia, fijosDia, gastosDia, fondoDia, ingresoPedido, diasTrab, descDia)
    }

    // ---------------------------------------------------------------- mantenimiento y recordatorios
    fun itemsVehiculo(vehiculo: String): List<String> {
        val extra = db.rows("SELECT item FROM items_custom WHERE vehiculo=? ORDER BY item", vehiculo).map { it.s("item") }
        return (MANTENIMIENTO_BASICO[vehiculo] ?: emptyList()) + extra
    }

    fun kmActual(vehiculo: String): Int = db.cfgI("km_$vehiculo", 0)

    fun ultimoKm(vehiculo: String): Int? {
        val r = db.one(
            """SELECT km FROM mantenimiento WHERE vehiculo=? AND km IS NOT NULL
               ORDER BY fecha DESC, id DESC LIMIT 1""", vehiculo
        )
        return r?.i("km")
    }

    fun recordatorios(hoy: LocalDate): List<Recordatorio> {
        val out = ArrayList<Recordatorio>()
        for (r in db.rows("SELECT * FROM recordatorios ORDER BY vehiculo, item")) {
            val veh = r.s("vehiculo")
            val ult = db.one(
                "SELECT fecha, km FROM mantenimiento WHERE vehiculo=? AND item=? ORDER BY fecha DESC, id DESC LIMIT 1",
                veh, r.s("item")
            )
            val base = LocalDate.parse(ult?.s("fecha") ?: r.s("desde"))
            val dias = java.time.temporal.ChronoUnit.DAYS.between(base, hoy).toInt()
            val ped = db.one("SELECT COALESCE(SUM(pedidos),0) n FROM diario WHERE fecha > ?", base.toString())!!.i("n")
            var prog = 0.0
            if (r.i("cada_dias") > 0) prog = maxOf(prog, dias.toDouble() / r.i("cada_dias"))
            if (r.i("cada_pedidos") > 0) prog = maxOf(prog, ped.toDouble() / r.i("cada_pedidos"))
            var kmRec: Int? = null
            val kmAct = kmActual(veh)
            if (ult != null && ult.tiene("km") && kmAct > 0) {
                kmRec = maxOf(0, kmAct - ult.i("km"))
                if (r.i("cada_km") > 0) prog = maxOf(prog, kmRec.toDouble() / r.i("cada_km"))
            }
            val estado = if (prog >= 1) "VENCIDO" else if (prog >= 0.8) "Próximo" else "Al día"
            out.add(
                Recordatorio(
                    r.l("id"), veh, r.s("item"), r.i("cada_dias"), r.i("cada_pedidos"), r.i("cada_km"),
                    base, ult != null, dias, ped, kmRec, prog, estado
                )
            )
        }
        return out
    }

    fun vencimientos(hoy: LocalDate): List<Vencimiento> =
        db.rows("SELECT * FROM vencimientos ORDER BY fecha").map {
            val f = LocalDate.parse(it.s("fecha"))
            val dias = java.time.temporal.ChronoUnit.DAYS.between(hoy, f).toInt()
            Vencimiento(it.l("id"), it.s("concepto"), f, dias, if (dias < 0) "VENCIDO" else if (dias <= 30) "Pronto" else "Ok")
        }

    // ---------------------------------------------------------------- seguro y crédito de la app (cargados por día)
    /** Seguro y crédito cargados entre [desde] y [hasta] (por la fecha en que se cargaron). */
    fun descuentosPeriodo(desde: LocalDate, hasta: LocalDate): DescSemana {
        val r = db.one(
            """SELECT COALESCE(SUM(CASE WHEN tipo='seguro' THEN monto END),0) s,
               COALESCE(SUM(CASE WHEN tipo='credito' THEN monto END),0) c
               FROM cargos_app WHERE fecha BETWEEN ? AND ?""",
            desde.toString(), hasta.toString()
        )!!
        return DescSemana(r.d("s"), r.d("c"))
    }

    /** Lo que se descuenta de la semana que termina el día [fin]: lo cargado durante la semana siguiente. */
    fun descuentoSemana(fin: LocalDate): DescSemana = descuentosPeriodo(fin.plusDays(1), fin.plusDays(7))

    /** Descuento semanal típico (promedio de las últimas semanas con cargas), para proyecciones. */
    fun descuentoEstimado(hoy: LocalDate): DescSemana {
        val primero = db.one("SELECT MIN(fecha) f FROM cargos_app")?.sn("f")?.let { LocalDate.parse(it) }
            ?: return DescSemana(0.0, 0.0)
        val desde = if (primero.isAfter(hoy.minusDays(27))) primero else hoy.minusDays(27)
        val semanas = (java.time.temporal.ChronoUnit.DAYS.between(desde, hoy) / 7 + 1).toInt().coerceIn(1, 4)
        val d = descuentosPeriodo(desde, hoy)
        return DescSemana(d.seguro / semanas, d.credito / semanas)
    }

    /** Total pagado del crédito de la app según lo cargado. */
    fun creditoPagado(): Double =
        db.one("SELECT COALESCE(SUM(monto),0) m FROM cargos_app WHERE tipo='credito'")!!.d("m")

    // ---------------------------------------------------------------- combustible
    fun consumo(vehiculo: String): Consumo? {
        val todas = db.rows("SELECT * FROM combustible WHERE vehiculo=? AND km IS NOT NULL ORDER BY km, id", vehiculo)
        val r = todas.takeLast(8)
        if (r.size < 2) return null
        val km = r.last().i("km") - r.first().i("km")
        if (km <= 0) return null
        val resto = r.drop(1)
        return Consumo(km, resto.sumOf { it.d("litros") }, resto.sumOf { it.d("monto") })
    }

    // ---------------------------------------------------------------- calendario de cobros
    fun calendarioCobros(hoy: LocalDate, n: Int = 4): List<Cobro> {
        val finActual = periodoSemana(hoy, db.diaCierre).second
        val hist = ArrayList<Totales>()
        for (i in 1..4) {
            val fin = finActual.minusDays(7L * i)
            val t = totales(fin.minusDays(6), fin)
            if (t.pedidos > 0) hist.add(t)
        }
        val promNeto = if (hist.isEmpty()) 0.0 else hist.sumOf { it.neto } / hist.size
        val promProp = if (hist.isEmpty()) 0.0 else hist.sumOf { it.propinas } / hist.size
        val filas = ArrayList<Cobro>()
        var fin = finActual.minusDays(7)
        val est = descuentoEstimado(hoy)
        var arrastre = 0.0   // deuda con la app que pasa a la semana siguiente (para las proyecciones)
        while (filas.size < n) {
            val cobro = fechaCobro(fin, db.diaCobro)
            val ini = fin.minusDays(6)
            if (ini.isAfter(hoy)) {
                val d = est.total
                val total = arrastre + promNeto - d
                filas.add(Cobro(cobro, ini, fin, "Proyectada", total, promProp, d, arrastre))
                arrastre = minOf(0.0, total)
            } else {
                val cs = cuentaSemana(fin)
                val enCurso = !fin.isBefore(hoy)
                if (!cobro.isBefore(hoy)) {
                    filas.add(
                        Cobro(
                            cobro, ini, fin, if (enCurso) "En curso" else "Cerrada", cs.total,
                            totales(ini, fin).propinas, cs.seguro + cs.credito, cs.arrastre
                        )
                    )
                }
                // para la semana en curso se estima cómo va a cerrar, así la proyección siguiente no se distorsiona
                val estimado = if (enCurso) cs.arrastre + maxOf(cs.turnos, promNeto) - maxOf(cs.seguro + cs.credito, est.total) else cs.total
                arrastre = minOf(0.0, estimado)
            }
            fin = fin.plusDays(7)
        }
        return filas
    }

    // ---------------------------------------------------------------- meta de ahorro
    fun ahorroTotal(): Double? {
        val m = db.one("SELECT ahorrado FROM meta WHERE id=1") ?: return null
        return m.d("ahorrado") + db.one("SELECT COALESCE(SUM(monto),0) s FROM aportes")!!.d("s")
    }

    /** Guarda un aporte y devuelve los hitos (25/50/75/100 %) que cruzó. */
    fun registrarAporte(fecha: LocalDate, monto: Double, nota: String): List<Int> {
        val antes = ahorroTotal()
        db.exec("INSERT INTO aportes(fecha,monto,nota) VALUES(?,?,?)", fecha.toString(), monto, nota)
        val obj = db.one("SELECT objetivo FROM meta WHERE id=1")?.d("objetivo") ?: 0.0
        val despues = ahorroTotal()
        if (obj <= 0 || antes == null || despues == null) return emptyList()
        return listOf(25, 50, 75, 100).filter { antes / obj * 100 < it && it <= despues / obj * 100 }
    }

    fun plan(hoy: LocalDate): Plan {
        val m = db.one("SELECT * FROM meta WHERE id=1") ?: return Plan("sin_meta")
        val total = ahorroTotal() ?: 0.0
        val objetivo = m.d("objetivo")
        val falta = maxOf(0.0, objetivo - total)
        val limite = parseFecha(m.sn("fecha_limite") ?: "")
        val inicio = parseFecha(m.sn("fecha_inicio") ?: "") ?: hoy
        val diasSem = maxOf(1, m.i("dias_semana").let { if (it == 0) 6 else it })
        val base = Plan(
            estado = "cumplida", nombre = m.s("nombre"), objetivo = objetivo, inicial = m.d("ahorrado"),
            limite = limite, inicio = inicio, diasSemana = diasSem, total = total, falta = falta,
            pct = if (objetivo > 0) minOf(100.0, total / objetivo * 100) else 0.0
        )
        if (falta <= 0) return base
        val desde = hoy.minusDays(29)
        val t = totales(desde, hoy)
        val diasTrab = diasTrabajados(desde, hoy)
        if (diasTrab == 0) return base.copy(estado = "sin_datos")
        val incluirProp = m.i("incluir_propinas") != 0
        val ingreso = t.neto - t.gastos + (if (incluirProp) t.propinas else 0.0)
        val porDia = ingreso / diasTrab
        val fijosMes = fijosReferencia(hoy)
        val fijosSem = fijosMes * 12 / 52
        val descSem = descuentoEstimado(hoy).total
        val porSem = porDia * diasSem - fijosSem - descSem
        var p = base.copy(
            estado = "ok", diasTrab = diasTrab, porDia = porDia, porSemBruta = porDia * diasSem,
            fijosMes = fijosMes, fijosSem = fijosSem, descSem = descSem, porSem = porSem, porDiaLibre = porSem / diasSem,
            pedidosDia = t.pedidos.toDouble() / diasTrab,
            prom = if (t.pedidos > 0) (t.bruto + (if (incluirProp) t.propinas else 0.0)) / t.pedidos else 0.0
        )
        if (porSem <= 0) return p.copy(estado = "sin_margen")
        p = p.copy(semNec = falta / porSem)
        if (limite != null) {
            val diasRest = java.time.temporal.ChronoUnit.DAYS.between(hoy, limite).toInt()
            p = p.copy(diasRest = diasRest)
            if (diasRest > 0) {
                val necSem = falta / (diasRest / 7.0)
                p = p.copy(necSem = necSem, necDia = necSem / diasSem, factible = necSem <= porSem)
            }
        }
        return if (p.necDia != null) p.copy(objDia = p.necDia!!, modo = "fecha")
        else p.copy(objDia = porSem * 0.30 / diasSem, modo = "recomendado")
    }

    /** ¿Apartaste hoy la plata de tu meta? cumplido | parcial | pendiente | sin_turno (null si no hay plan). */
    fun estadoAhorro(hoy: LocalDate): AhorroHoy? {
        val p = plan(hoy)
        if (p.estado != "ok") return null
        val obj = p.objDia
        val hecho = db.one("SELECT COALESCE(SUM(monto),0) s FROM aportes WHERE fecha=?", hoy.toString())!!.d("s")
        val t = totales(hoy, hoy)
        val e = if (hecho >= obj * 0.999) "cumplido"
        else if (hecho > 0) "parcial"
        else if (t.pedidos > 0 || t.bruto > 0) "pendiente"
        else "sin_turno"
        return AhorroHoy(e, obj, hecho, maxOf(0.0, obj - hecho), p.nombre, p.modo)
    }

    // ---------------------------------------------------------------- alertas
    /** Lista de (nivel, texto). nivel: "rojo" | "ambar". */
    fun alertas(hoy: LocalDate): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        val ea = estadoAhorro(hoy)
        if (ea != null && ea.estado == "pendiente") {
            out.add("rojo" to "Todavía no apartaste la plata de hoy para «${ea.meta}»: ${money(ea.falta)}.")
        } else if (ea != null && ea.estado == "parcial") {
            out.add("ambar" to "Apartaste ${money(ea.hecho)} de ${money(ea.objetivo)} para tu meta; faltan ${money(ea.falta)}.")
        }
        val cs = cuentaSemana(periodoSemana(hoy, db.diaCierre).second)
        if (cs.arrastre < -0.005) {
            out.add("ambar" to "Arrastrás ${money(-cs.arrastre)} de deuda con la app de la semana anterior: se va descontando con lo que hagas esta semana.")
        }
        if (cs.total < -0.005) {
            out.add("rojo" to "Esta semana vas debiendo ${money(-cs.total)} a la app (contando la deuda anterior).")
        }
        val semHoy = periodoSemana(hoy, db.diaCierre)
        if (descuentoEstimado(hoy).total > 0.005 && descuentosPeriodo(semHoy.first, semHoy.second).total <= 0.005) {
            out.add("ambar" to "Todavía no cargaste el seguro y el crédito de esta semana (solapa Diario): se descuentan del saldo de la semana pasada.")
        }
        val t = totales(hoy, hoy)
        if (t.neto < -0.005) out.add("rojo" to "Hoy le debés ${money(-t.neto)} a la aplicación (el efectivo supera lo generado).")
        for (r in recordatorios(hoy)) {
            val det = "${r.vehiculo} · ${r.item}"
            if (r.estado == "VENCIDO") out.add("rojo" to "Mantenimiento vencido: $det (${r.dias} días y ${r.pedidos} pedidos desde el último).")
            else if (r.estado == "Próximo") out.add("ambar" to "Mantenimiento próximo: $det (${(r.prog * 100).toInt()}% del intervalo).")
        }
        for (v in vencimientos(hoy)) {
            if (v.dias < 0) out.add("rojo" to "${v.concepto}: venció hace ${-v.dias} día(s).")
            else if (v.dias <= 30) out.add("ambar" to "${v.concepto}: vence en ${v.dias} día(s) (${fmtFecha(v.fecha)}).")
        }
        return out
    }
}
