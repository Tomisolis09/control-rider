package com.repartidor.control.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repartidor.control.AppState
import com.repartidor.control.CATEGORIAS_FIJOS
import com.repartidor.control.MESES
import com.repartidor.control.finDeMes
import com.repartidor.control.fmtCorto
import com.repartidor.control.money
import com.repartidor.control.money0
import com.repartidor.control.num
import com.repartidor.control.sumarMeses
import java.time.LocalDate

@Composable
fun GastosFijosScreen(app: AppState) {
    val db = app.db
    val c = app.calc
    val v = app.version

    var mes by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var fecha by remember { mutableStateOf(LocalDate.now()) }
    var concepto by remember { mutableStateOf(CATEGORIAS_FIJOS[0]) }
    var monto by remember { mutableStateOf("") }
    var nota by remember { mutableStateOf("") }
    var borrar by remember { mutableStateOf<Long?>(null) }
    var copiar by remember { mutableStateOf(false) }

    val desde = mes
    val hasta = finDeMes(mes)
    val filas = remember(v, mes) {
        db.rows("SELECT * FROM gastos_fijos WHERE fecha BETWEEN ? AND ? ORDER BY fecha, id", desde.toString(), hasta.toString())
    }
    val total = filas.sumOf { it.d("monto") }
    val anterior = sumarMeses(mes, -1)
    val totalAnt = remember(v, mes) { c.totalFijos(anterior, finDeMes(anterior)) }
    val dif = total - totalAnt
    val porCat = filas.groupBy { it.s("categoria") }.map { it.key to it.value.sumOf { r -> r.d("monto") } }
        .sortedByDescending { it.second }
    val ultimos6 = remember(v, mes) {
        (-5..0).map { i ->
            val m = sumarMeses(mes, i)
            MESES[m.monthValue - 1].take(3) to c.totalFijos(m, finDeMes(m))
        }
    }

    fun irA(m: LocalDate) {
        mes = m.withDayOfMonth(1)
        fecha = if (m.year == LocalDate.now().year && m.monthValue == LocalDate.now().monthValue) LocalDate.now() else mes
    }

    fun guardar() {
        val m = num(monto)
        if (m == null) { app.aviso = "El monto debe ser un número positivo"; return }
        if (m <= 0) return
        db.exec("INSERT INTO gastos_fijos(fecha,categoria,monto,nota) VALUES(?,?,?,?)",
            fecha.toString(), concepto.trim().ifEmpty { "Otro" }, m, nota.trim())
        monto = ""; nota = ""
        mes = fecha.withDayOfMonth(1)
        app.cambio("Gasto fijo agregado")
    }

    fun copiarAnterior() {
        val prev = db.rows("SELECT * FROM gastos_fijos WHERE fecha BETWEEN ? AND ? ORDER BY fecha, id",
            anterior.toString(), finDeMes(anterior).toString())
        if (prev.isEmpty()) { app.aviso = "El mes anterior no tiene gastos fijos para copiar"; return }
        val ultimo = mes.lengthOfMonth()
        for (r in prev) {
            val dia = LocalDate.parse(r.s("fecha")).dayOfMonth
            val nuevo = mes.withDayOfMonth(minOf(dia, ultimo))
            db.exec("INSERT INTO gastos_fijos(fecha,categoria,monto,nota) VALUES(?,?,?,?)",
                nuevo.toString(), r.s("categoria"), r.d("monto"), r.s("nota"))
        }
        app.cambio("Se copiaron ${prev.size} gastos del mes anterior")
    }

    Pantalla {
        Titulo("Gastos fijos del hogar", "Alquiler, comida, servicios y demás gastos del mes")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { irA(sumarMeses(mes, -1)) }) { Text("‹") }
            Text("${MESES[mes.monthValue - 1]} ${mes.year}", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { irA(sumarMeses(mes, 1)) }) { Text("›") }
            TextButton(onClick = { irA(LocalDate.now()) }) { Text("Este mes") }
        }

        FilaKpis {
            KpiMonto("Total del mes", total, Paleta.Rojo, peso())
            KpiMonto("Mes anterior", totalAnt, Paleta.Ambar, peso())
        }
        FilaKpis {
            Kpi("Diferencia", (if (dif > 0) "+" else "") + money0(dif), if (dif > 0) Paleta.Rojo else Paleta.Verde, peso())
            Kpi("Mayor gasto", if (porCat.isEmpty()) "—" else porCat[0].first.substringBefore(" (") + " · " + money0(porCat[0].second), Paleta.Violeta, peso())
        }

        Panel("Agregar gasto fijo") {
            CampoFecha(fecha, { fecha = it })
            ComboLibre("Concepto", CATEGORIAS_FIJOS, concepto, { concepto = it })
            Dos {
                CampoMonto("Monto", monto, { monto = it }, peso())
                CampoTexto("Nota", nota, { nota = it }, peso())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Boton("Agregar gasto", { guardar() })
                Boton("Copiar del mes anterior", {
                    if (total > 0) copiar = true else copiarAnterior()
                }, tipo = 1)
            }
        }

        Panel("Gastos del mes") {
            if (filas.isEmpty()) Subtexto("No hay gastos fijos cargados en este mes.")
            for (r in filas) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("${r.s("categoria")} · ${money(r.d("monto"))}", fontSize = 14.sp)
                        Subtexto(fmtCorto(LocalDate.parse(r.s("fecha"))) + (if (r.s("nota").isNotEmpty()) " · ${r.s("nota")}" else ""))
                    }
                    Boton("Borrar", { borrar = r.l("id") }, tipo = 2)
                }
            }
        }

        if (porCat.isNotEmpty()) {
            Panel("Por concepto") {
                for ((cat, valor) in porCat) {
                    FilaDato("$cat  (${(valor / total * 100).toInt()}%)", money(valor))
                }
                FilaDato("TOTAL", money(total), negrita = true)
            }
        }

        Panel("Últimos 6 meses") { GraficoBarras(ultimos6, Paleta.Rojo) }
    }

    Confirmar(borrar != null, "Borrar gasto", "¿Borrar el gasto seleccionado?",
        onOk = {
            borrar?.let { db.exec("DELETE FROM gastos_fijos WHERE id=?", it) }
            borrar = null
            app.cambio("Gasto fijo borrado")
        },
        onCancel = { borrar = null })
    Confirmar(copiar, "Copiar gastos", "Este mes ya tiene gastos cargados. ¿Copiar igual los del mes anterior?",
        onOk = { copiar = false; copiarAnterior() },
        onCancel = { copiar = false })
}
