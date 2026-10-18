package com.repartidor.control.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repartidor.control.AppState
import com.repartidor.control.CONCEPTOS_VENC
import com.repartidor.control.MODOS_FONDO
import com.repartidor.control.Recordatorio
import com.repartidor.control.SUGERIDOS
import com.repartidor.control.VEHICULOS
import com.repartidor.control.fmtFecha
import com.repartidor.control.money0
import com.repartidor.control.num
import java.time.LocalDate
import java.util.Locale

private fun miles(n: Int): String = String.format(Locale.US, "%,d", n).replace(",", ".")

private fun colorEstado(e: String): Color =
    if (e == "VENCIDO") Paleta.Rojo else if (e == "Próximo" || e == "Pronto") Paleta.Ambar else Paleta.Verde

@Composable
fun RecordatoriosScreen(app: AppState) {
    val db = app.db
    val c = app.calc
    val v = app.version
    val hoy = LocalDate.now()

    var veh by remember { mutableStateOf(VEHICULOS[0]) }
    var item by remember { mutableStateOf("") }
    var cadaDias by remember { mutableStateOf("") }
    var cadaPed by remember { mutableStateOf("") }
    var cadaKm by remember { mutableStateOf("") }
    var hechoDe by remember { mutableStateOf<Recordatorio?>(null) }
    var hCosto by remember { mutableStateOf("") }
    var hKm by remember { mutableStateOf("") }
    var borrarRec by remember { mutableStateOf<Long?>(null) }
    var vConcepto by remember { mutableStateOf(CONCEPTOS_VENC[0]) }
    var vFecha by remember { mutableStateOf(hoy) }
    var borrarVenc by remember { mutableStateOf<Long?>(null) }
    var modo by remember { mutableStateOf(MODOS_FONDO[db.cfgI("fondo_tipo", 0).coerceIn(0, 2)]) }
    var fValor by remember { mutableStateOf(db.cfgD("fondo_valor", 0.0).let { if (it == 0.0) "" else it.toString().removeSuffix(".0") }) }

    val items = remember(v, veh) { c.itemsVehiculo(veh) }
    val recs = remember(v) { c.recordatorios(hoy) }
    val vencs = remember(v) { c.vencimientos(hoy) }
    val fondo = remember(v) { c.fondoEstado(hoy) }

    fun crear() {
        if (item.isBlank()) { app.aviso = "Elegí o escribí el trabajo a recordar"; return }
        val d = num(cadaDias)
        val p = num(cadaPed)
        val k = num(cadaKm)
        if (d == null || p == null || k == null) { app.aviso = "Los intervalos deben ser números positivos"; return }
        if (d <= 0 && p <= 0 && k <= 0) { app.aviso = "Poné un intervalo en días, en pedidos, en km, o en varios"; return }
        db.exec("INSERT OR REPLACE INTO recordatorios(vehiculo,item,cada_dias,cada_pedidos,cada_km,desde) VALUES(?,?,?,?,?,?)",
            veh, item.trim(), d.toInt(), p.toInt(), k.toInt(), hoy.toString())
        cadaDias = ""; cadaPed = ""; cadaKm = ""
        app.cambio("Recordatorio creado")
    }

    fun sugeridos() {
        var n = 0
        for (s in SUGERIDOS[veh] ?: emptyList()) {
            if (db.one("SELECT 1 x FROM recordatorios WHERE vehiculo=? AND item=?", veh, s.item) == null) {
                db.exec("INSERT INTO recordatorios(vehiculo,item,cada_dias,cada_pedidos,cada_km,desde) VALUES(?,?,?,?,?,?)",
                    veh, s.item, s.dias, s.pedidos, s.km, hoy.toString())
                n++
            }
        }
        app.cambio("$n recordatorio(s) sugerido(s) cargado(s) para $veh")
    }

    fun registrarHecho(r: Recordatorio) {
        val costo = num(hCosto)
        if (costo == null) { app.aviso = "El costo debe ser un número positivo"; return }
        var km: Int? = null
        if (hKm.isNotBlank()) {
            val k = num(hKm)
            if (k == null) { app.aviso = "Los km deben ser un número positivo"; return }
            km = k.toInt()
        }
        db.exec("INSERT INTO mantenimiento(fecha,vehiculo,item,costo,nota,km) VALUES(?,?,?,?,?,?)",
            hoy.toString(), r.vehiculo, r.item, costo, "Registrado desde Recordatorios", km)
        if (km != null && km > c.kmActual(r.vehiculo)) db.setCfg("km_${r.vehiculo}", km)
        hechoDe = null; hCosto = ""; hKm = ""
        app.cambio("Mantenimiento registrado: el contador volvió a cero")
    }

    fun guardarFondo() {
        val tipo = MODOS_FONDO.indexOf(modo)
        val valor = num(fValor.replace(",", "."))
        if (valor == null) { app.aviso = "El valor debe ser un número positivo"; return }
        if (tipo > 0 && valor <= 0) { app.aviso = "Ingresá cuánto apartar por pedido o qué porcentaje"; return }
        if (tipo == 2 && valor > 100) { app.aviso = "El porcentaje no puede superar 100"; return }
        val anterior = db.cfgI("fondo_tipo", 0)
        db.setCfg("fondo_tipo", tipo)
        db.setCfg("fondo_valor", valor)
        if (tipo > 0 && (anterior == 0 || db.cfgS("fondo_desde", "").isEmpty())) db.setCfg("fondo_desde", hoy.toString())
        app.cambio("Fondo de repuestos actualizado")
    }

    Pantalla {
        Titulo("Recordatorios del vehículo", "Avisan por días, pedidos y km desde el último trabajo")

        Panel("Mantenimiento por uso") {
            Selector("Vehículo", VEHICULOS, veh, { veh = it })
            ComboLibre("Trabajo", items, item, { item = it })
            Dos {
                CampoNum("Cada (días)", cadaDias, { cadaDias = it }, peso())
                CampoNum("Cada (pedidos)", cadaPed, { cadaPed = it }, peso())
                CampoNum("Cada (km)", cadaKm, { cadaKm = it }, peso())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Boton("Crear recordatorio", { crear() })
                Boton("Cargar sugeridos", { sugeridos() }, tipo = 1)
            }
            Subtexto("Los km salen del odómetro (solapa Mantenimiento) y del km registrado en el último trabajo. Los sugeridos son valores orientativos: ajustalos con el manual de tu vehículo.")
        }

        Panel("Estado") {
            if (recs.isEmpty()) Subtexto("Todavía no hay recordatorios. Creá uno o cargá los sugeridos.")
            for (r in recs) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${r.vehiculo} · ${r.item}", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text(r.estado, color = colorEstado(r.estado), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Barra(r.prog.toFloat(), colorEstado(r.estado))
                    val partes = ArrayList<String>()
                    partes.add("Días ${r.dias}/${if (r.cadaDias > 0) r.cadaDias.toString() else "—"}")
                    partes.add("Pedidos ${r.pedidos}/${if (r.cadaPedidos > 0) r.cadaPedidos.toString() else "—"}")
                    if (r.cadaKm > 0) partes.add("Km ${if (r.kmRec != null) miles(r.kmRec) else "?"}/${miles(r.cadaKm)}")
                    Subtexto("Último: ${fmtFecha(r.ultimo)}${if (r.conRegistro) "" else "*"} · ${partes.joinToString(" · ")}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Boton("Registrar como hecho", { hechoDe = r }, tipo = 1)
                        Boton("Borrar", { borrarRec = r.id }, tipo = 2)
                    }
                }
            }
            if (recs.isNotEmpty()) Subtexto("* = sin trabajo registrado: cuenta desde que creaste el recordatorio.")
        }

        Panel("Vencimientos (seguro, patente, licencia…)") {
            ComboLibre("Concepto", CONCEPTOS_VENC, vConcepto, { vConcepto = it })
            CampoFecha(vFecha, { vFecha = it })
            Boton("Agregar vencimiento", {
                if (vConcepto.isNotBlank()) {
                    db.exec("INSERT INTO vencimientos(concepto,fecha,nota) VALUES(?,?,?)", vConcepto.trim(), vFecha.toString(), "")
                    app.cambio("Vencimiento agregado")
                }
            })
            for (x in vencs) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(x.concepto, fontSize = 14.sp)
                        Text("${fmtFecha(x.fecha)} · ${if (x.dias < 0) "venció hace ${-x.dias} día(s)" else "faltan ${x.dias} día(s)"} · ${x.estado}",
                            fontSize = 12.sp, color = colorEstado(x.estado))
                    }
                    Boton("Borrar", { borrarVenc = x.id }, tipo = 2)
                }
            }
        }

        Panel("Fondo de repuestos automático") {
            Selector("Modo", MODOS_FONDO, modo, { modo = it })
            CampoTexto("Valor (\$ por pedido o %)", fValor, { fValor = it })
            Boton("Guardar", { guardarFondo() })
            FilaKpis {
                KpiMonto("Apartado", fondo?.apartado ?: 0.0, Paleta.Verde, peso())
                KpiMonto("Usado", fondo?.usado ?: 0.0, Paleta.Rojo, peso())
                KpiMonto("Disponible", fondo?.disponible ?: 0.0, Paleta.Acento, peso())
            }
            if (fondo == null) Subtexto("Activá el fondo para ir apartando plata para repuestos con cada pedido.")
        }
    }

    val hecho = hechoDe
    if (hecho != null) {
        AlertDialog(
            onDismissRequest = { hechoDe = null },
            title = { Text("${hecho.vehiculo} · ${hecho.item}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CampoMonto("Costo (vacío = \$ 0)", hCosto, { hCosto = it })
                    CampoNum("Km del vehículo (opcional)", hKm, { hKm = it })
                }
            },
            confirmButton = { TextButton(onClick = { registrarHecho(hecho) }) { Text("Registrar") } },
            dismissButton = { TextButton(onClick = { hechoDe = null }) { Text("Cancelar") } }
        )
    }
    Confirmar(borrarRec != null, "Borrar", "¿Borrar el recordatorio seleccionado?",
        onOk = {
            borrarRec?.let { db.exec("DELETE FROM recordatorios WHERE id=?", it) }
            borrarRec = null
            app.cambio("Recordatorio borrado")
        },
        onCancel = { borrarRec = null })
    Confirmar(borrarVenc != null, "Borrar", "¿Borrar el vencimiento seleccionado?",
        onOk = {
            borrarVenc?.let { db.exec("DELETE FROM vencimientos WHERE id=?", it) }
            borrarVenc = null
            app.cambio("Vencimiento borrado")
        },
        onCancel = { borrarVenc = null })
}
