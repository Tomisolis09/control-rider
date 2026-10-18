package com.repartidor.control.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repartidor.control.AppState
import com.repartidor.control.VEHICULOS
import com.repartidor.control.fmtFecha
import com.repartidor.control.money
import com.repartidor.control.num
import java.time.LocalDate
import java.util.Locale

private fun fmtKm(km: Int): String = String.format(Locale.US, "%,d", km).replace(",", ".") + " km"

@Composable
fun MantenimientoScreen(app: AppState) {
    val db = app.db
    val c = app.calc
    val v = app.version

    var veh by remember { mutableStateOf(VEHICULOS[0]) }
    var fecha by remember { mutableStateOf(LocalDate.now()) }
    var item by remember { mutableStateOf("") }
    var costo by remember { mutableStateOf("") }
    var nota by remember { mutableStateOf("") }
    var km by remember { mutableStateOf("") }
    var kmAct by remember { mutableStateOf("") }
    var borrar by remember { mutableStateOf<Long?>(null) }

    val items = remember(v, veh) { c.itemsVehiculo(veh) }
    val filas = remember(v, veh) { db.rows("SELECT * FROM mantenimiento WHERE vehiculo=? ORDER BY fecha DESC, id DESC", veh) }
    val totalVeh = filas.sumOf { it.d("costo") }
    val totalTodos = remember(v) { db.one("SELECT COALESCE(SUM(costo),0) c FROM mantenimiento")!!.d("c") }
    val kmActual = remember(v, veh) { c.kmActual(veh) }
    val ultimoKm = remember(v, veh) { c.ultimoKm(veh) }

    fun guardar() {
        if (item.isBlank()) { app.aviso = "Elegí o escribí qué mantenimiento o repuesto fue"; return }
        val cs = num(costo)
        if (cs == null) { app.aviso = "El costo debe ser un número positivo"; return }
        var kmV: Int? = null
        if (km.isNotBlank()) {
            val k = num(km)
            if (k == null) { app.aviso = "Los km deben ser un número positivo (ej. 12.500)"; return }
            kmV = k.toInt()
        }
        db.exec("INSERT INTO mantenimiento(fecha,vehiculo,item,costo,nota,km) VALUES(?,?,?,?,?,?)",
            fecha.toString(), veh, item.trim(), cs, nota.trim(), kmV)
        if (kmV != null && kmV > c.kmActual(veh)) db.setCfg("km_$veh", kmV)   // el odómetro avanza con el último registro
        costo = ""; nota = ""; km = ""; item = ""
        app.cambio("Mantenimiento guardado")
    }

    fun agregarItem() {
        val n = item.trim()
        if (n.isEmpty()) return
        if (items.any { it.equals(n, ignoreCase = true) }) { app.aviso = "Ese ítem ya está en la lista"; return }
        db.exec("INSERT OR IGNORE INTO items_custom(vehiculo,item) VALUES(?,?)", veh, n)
        app.cambio("“$n” agregado a la lista de $veh")
    }

    fun actualizarKm() {
        val k = num(kmAct)
        if (k == null || k <= 0) { app.aviso = "Los km deben ser un número positivo (ej. 12.500)"; return }
        db.setCfg("km_$veh", k.toInt())
        kmAct = ""
        app.cambio("Km actual de la ${veh.lowercase()}: ${fmtKm(k.toInt())}")
    }

    Pantalla {
        Titulo("Mantenimiento del vehículo")
        Selector("Vehículo", VEHICULOS, veh, { veh = it })

        FilaKpis {
            KpiMonto("Gastado en este vehículo", totalVeh, Paleta.Rojo, peso())
            KpiMonto("Gastado en total", totalTodos, Paleta.Ambar, peso())
        }
        FilaKpis {
            Kpi("Km actual", if (kmActual > 0) fmtKm(kmActual) else "—", Paleta.Acento, peso())
            Kpi("Km desde el último trabajo",
                if (ultimoKm != null && kmActual >= ultimoKm && ultimoKm > 0) fmtKm(kmActual - ultimoKm) else "—",
                Paleta.Violeta, peso())
        }

        Panel("Registrar mantenimiento o repuesto") {
            CampoFecha(fecha, { fecha = it })
            ComboLibre("Trabajo / repuesto", items, item, { item = it })
            Boton("+ Agregar a la lista (si no está)", { agregarItem() }, tipo = 1)
            Dos {
                CampoMonto("Costo", costo, { costo = it }, peso())
                CampoNum("Km del vehículo", km, { km = it }, peso())
            }
            CampoTexto("Nota", nota, { nota = it })
            Subtexto("Los km son opcionales: el kilometraje cuando se hizo el trabajo.")
            Boton("Guardar gasto", { guardar() })
        }

        Panel("Odómetro") {
            CampoNum("Km actual de la ${veh.lowercase()}", kmAct, { kmAct = it })
            Boton("Actualizar km actual", { actualizarKm() }, tipo = 1)
        }

        Panel("Historial") {
            if (filas.isEmpty()) Subtexto("Todavía no hay registros para este vehículo.")
            for (r in filas) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("${r.s("item")} · ${money(r.d("costo"))}", fontSize = 14.sp)
                        val kmTxt = if (r.tiene("km")) " · ${fmtKm(r.i("km"))}" else ""
                        Subtexto(fmtFecha(LocalDate.parse(r.s("fecha"))) + kmTxt + (if (r.s("nota").isNotEmpty()) " · ${r.s("nota")}" else ""))
                    }
                    Boton("Borrar", { borrar = r.l("id") }, tipo = 2)
                }
            }
        }
    }

    Confirmar(borrar != null, "Borrar", "¿Borrar el registro seleccionado?",
        onOk = {
            borrar?.let { db.exec("DELETE FROM mantenimiento WHERE id=?", it) }
            borrar = null
            app.cambio("Registro borrado")
        },
        onCancel = { borrar = null })
}
