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
import com.repartidor.control.DIAS
import com.repartidor.control.DIAS_CORTOS
import com.repartidor.control.MESES
import com.repartidor.control.diaSemana
import com.repartidor.control.fechaCobro
import com.repartidor.control.finDeMes
import com.repartidor.control.fmtCorto
import com.repartidor.control.money
import com.repartidor.control.periodoSemana
import com.repartidor.control.sumarMeses
import java.time.LocalDate

/** Resumen semanal y mensual en una sola pantalla. */
@Composable
fun ResumenScreen(app: AppState) {
    val c = app.calc
    val db = app.db
    val v = app.version

    var modo by remember { mutableStateOf("Semana") }
    var ref by remember { mutableStateOf(LocalDate.now()) }
    val semanal = modo == "Semana"

    val rango = remember(v, modo, ref) {
        if (semanal) periodoSemana(ref, db.diaCierre) else Pair(ref.withDayOfMonth(1), finDeMes(ref))
    }
    val d = rango.first
    val h = rango.second
    val t = remember(v, modo, ref) { c.totales(d, h) }
    val dias = remember(v, modo, ref) { c.porDia(d, h).associateBy { it.fecha } }
    val desc = remember(v, modo, ref) { if (semanal) c.descuentoSemana(h) else c.descuentosPeriodo(d, h) }
    val aCobrar = t.neto - desc.total

    fun mover(n: Int) {
        ref = if (semanal) ref.plusDays(7L * n) else sumarMeses(ref, n)
    }

    Pantalla {
        Titulo(if (semanal) "Resumen semanal" else "Resumen mensual")
        Selector("Ver", listOf("Semana", "Mes"), modo, { modo = it })

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { mover(-1) }) { Text("‹") }
            Text(
                if (semanal) "${fmtCorto(d)} al ${fmtCorto(h)}/${h.year}" else "${MESES[d.monthValue - 1]} ${d.year}",
                fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = { mover(1) }) { Text("›") }
            TextButton(onClick = { ref = LocalDate.now() }) { Text("Hoy") }
        }

        if (semanal) {
            Panel("Cierre y cobro") {
                Dos {
                    Selector("Cierra", DIAS, DIAS[db.diaCierre], {
                        db.setCfg("dia_cierre", DIAS.indexOf(it))
                        app.cambio("Configuración de cobro actualizada")
                    }, peso())
                    Selector("Cobra", DIAS, DIAS[db.diaCobro], {
                        db.setCfg("dia_cobro", DIAS.indexOf(it))
                        app.cambio("Configuración de cobro actualizada")
                    }, peso())
                }
                val cobro = fechaCobro(h, db.diaCobro)
                Subtexto("Esta semana se cobra el ${DIAS[diaSemana(cobro)].lowercase()} ${fmtCorto(cobro)}.")
            }
        }

        FilaKpis {
            SaldoKpi(if (semanal) "Semana" else "Mes", aCobrar, peso())
            KpiMonto("Efectivo recaudado", t.efectivo, Paleta.Violeta, peso())
        }
        FilaKpis {
            Kpi("Pedidos", t.pedidos.toString(), Paleta.Ambar, peso())
            KpiMonto("Propinas (aparte)", t.propinas, Paleta.Verde, peso())
            KpiMonto("Gastos", t.gastos, Paleta.Rojo, peso())
        }

        Panel("Detalle por día") {
            var dia = d
            var hayFilas = false
            while (!dia.isAfter(h)) {
                val r = dias[dia]
                if (r != null) {
                    hayFilas = true
                    Column(Modifier.fillMaxWidth()) {
                        FilaDato(
                            "${DIAS_CORTOS[diaSemana(dia)]} ${fmtCorto(dia)} · ${r.n} pedidos",
                            money(r.saldo), if (r.saldo < 0) Paleta.Rojo else Paleta.Verde, true
                        )
                        Subtexto("Base ${money(r.b)} · Adic. ${money(r.a)} · Grupo ${money(r.g)} · Bono ${money(r.bn)}")
                        Subtexto("Efectivo ${money(r.e)} · Propinas ${money(r.p)}")
                    }
                } else if (semanal) {
                    FilaDato("${DIAS_CORTOS[diaSemana(dia)]} ${fmtCorto(dia)}", "—")
                }
                dia = dia.plusDays(1)
            }
            if (!hayFilas && !semanal) Subtexto("No hay turnos cargados en este mes.")
            FilaDato("Saldo de los turnos (base + adic. + grupo + bono − efectivo)", money(t.neto), if (t.neto < 0) Paleta.Rojo else Paleta.Verde, true)
            if (desc.seguro > 0) FilaDato("− Seguro", money(desc.seguro), Paleta.Ambar)
            if (desc.credito > 0) FilaDato("− Cuota del crédito", money(desc.credito), Paleta.Violeta)
            FilaDato(if (aCobrar < 0) "LE DEBÉS A LA APP" else "A COBRAR", money(kotlin.math.abs(aCobrar)), if (aCobrar < 0) Paleta.Rojo else Paleta.Verde, true)
            Subtexto("Si es negativo, le debés esa diferencia a la app. Las propinas no se suman ni se restan.")
        }

        Panel("Saldo por día") {
            val datos = ArrayList<Pair<String, Double>>()
            var dia = d
            while (!dia.isAfter(h)) {
                val r = dias[dia]
                datos.add((if (semanal) DIAS_CORTOS[diaSemana(dia)] else dia.dayOfMonth.toString()) to (r?.saldo ?: 0.0))
                dia = dia.plusDays(1)
            }
            GraficoBarras(datos, Paleta.Acento)
        }
    }
}
