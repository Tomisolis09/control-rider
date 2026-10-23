package com.repartidor.control

import java.util.Locale

private fun celda(v: String): String =
    if (v.contains(';') || v.contains('"') || v.contains('\n')) "\"" + v.replace("\"", "\"\"") + "\"" else v

private fun dec(x: Double): String = String.format(Locale.US, "%.2f", x).replace('.', ',')

/** Todos los datos en un CSV (separador ;) que se abre bien en Excel. */
fun exportarCsv(db: Db): String {
    val sb = StringBuilder()
    fun fila(vararg c: String) {
        sb.append(c.joinToString(";") { celda(it) }).append("\r\n")
    }
    fila("tipo", "fecha", "app/categoria", "hora_inicio", "hora_fin", "pedidos", "base", "adicionales", "grupo",
        "bono", "propinas", "efectivo", "monto", "km", "nota")
    for (r in db.rows("SELECT * FROM diario ORDER BY fecha, id")) {
        fila("turno", r.s("fecha"), r.s("app"), r.s("hora_inicio"), r.s("hora_fin"), r.i("pedidos").toString(),
            dec(r.d("base")), dec(r.d("adicionales")), dec(r.d("grupo")), dec(r.d("bono")), dec(r.d("propinas")),
            dec(r.d("efectivo")), "", "", "")
    }
    for ((tabla, tipo) in listOf("gastos" to "gasto", "gastos_fijos" to "gasto fijo")) {
        for (r in db.rows("SELECT * FROM $tabla ORDER BY fecha, id")) {
            fila(tipo, r.s("fecha"), r.s("categoria"), "", "", "", "", "", "", "", "", "", dec(r.d("monto")), "", r.s("nota"))
        }
    }
    for (r in db.rows("SELECT * FROM mantenimiento ORDER BY fecha, id")) {
        fila("mantenimiento", r.s("fecha"), r.s("vehiculo") + " - " + r.s("item"), "", "", "", "", "", "", "", "", "",
            dec(r.d("costo")), if (r.tiene("km")) r.i("km").toString() else "", r.s("nota"))
    }
    for (r in db.rows("SELECT * FROM cargos_app ORDER BY fecha, id")) {
        fila(if (r.s("tipo") == "credito") "crédito de la app" else "seguro de la app", r.s("fecha"), "", "", "", "", "", "", "", "", "", "",
            dec(r.d("monto")), "", r.s("nota"))
    }
    for (r in db.rows("SELECT * FROM aportes ORDER BY fecha, id")) {
        fila("aporte meta", r.s("fecha"), "", "", "", "", "", "", "", "", "", "", dec(r.d("monto")), "", r.s("nota"))
    }
    for (r in db.rows("SELECT * FROM vencimientos ORDER BY fecha")) {
        fila("vencimiento", r.s("fecha"), r.s("concepto"), "", "", "", "", "", "", "", "", "", "", "", r.s("nota"))
    }
    return sb.toString()
}
