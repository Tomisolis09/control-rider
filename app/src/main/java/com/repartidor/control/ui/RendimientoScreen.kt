package com.repartidor.control.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.repartidor.control.AppState
import com.repartidor.control.DIAS
import com.repartidor.control.DIAS_CORTOS
import com.repartidor.control.duracionHoras
import com.repartidor.control.franja
import com.repartidor.control.money0
import java.time.LocalDate

private class G(var h: Double = 0.0, var ing: Double = 0.0, var ped: Int = 0)

private class Rend(
    val n: Int, val h: Double, val ing: Double, val ped: Int, val gastos: Double,
    val app: Map<String, G>, val dia: Map<String, G>, val fra: Map<String, G>
)

private val PERIODOS = listOf("Últimos 30 días", "Últimos 90 días", "Todo el historial")

@Composable
fun RendimientoScreen(app: AppState) {
    val db = app.db
    val v = app.version
    var periodo by remember { mutableStateOf(PERIODOS[0]) }

    val r = remember(v, periodo) {
        val desde = when (periodo) {
            PERIODOS[0] -> LocalDate.now().minusDays(29)
            PERIODOS[1] -> LocalDate.now().minusDays(89)
            else -> LocalDate.of(2000, 1, 1)
        }
        val filas = db.rows(
            """SELECT fecha, app, base, adicionales, grupo, COALESCE(bono,0) bono, propinas, pedidos, hora_inicio, hora_fin
               FROM diario WHERE fecha >= ? AND hora_inicio IS NOT NULL AND hora_inicio != ''""", desde.toString()
        )
        val mApp = LinkedHashMap<String, G>()
        val mDia = LinkedHashMap<String, G>()
        val mFra = LinkedHashMap<String, G>()
        var hT = 0.0
        var iT = 0.0
        var pT = 0
        var n = 0
        val fechas = HashSet<String>()
        for (f in filas) {
            val h = duracionHoras(f.s("hora_inicio"), f.s("hora_fin"))
            if (h <= 0) continue
            val ing = f.d("base") + f.d("adicionales") + f.d("grupo") + f.d("bono") + f.d("propinas")
            hT += h; iT += ing; pT += f.i("pedidos"); n++
            fechas.add(f.s("fecha"))
            val claves = listOf(
                mApp to (f.s("app").ifEmpty { "—" }),
                mDia to DIAS[LocalDate.parse(f.s("fecha")).dayOfWeek.value - 1],
                mFra to franja(f.s("hora_inicio"))
            )
            for ((mapa, clave) in claves) {
                val g = mapa.getOrPut(clave) { G() }
                g.h += h; g.ing += ing; g.ped += f.i("pedidos")
            }
        }
        val gastos = db.rows("SELECT fecha, SUM(monto) m FROM gastos WHERE fecha >= ? GROUP BY fecha", desde.toString())
            .filter { it.s("fecha") in fechas }.sumOf { it.d("m") }
        Rend(n, hT, iT, pT, gastos, mApp, mDia, mFra)
    }

    Pantalla {
        Titulo("Rendimiento por hora", "Ingreso = base + adicionales + grupo + bono + propinas. La franja se toma de la hora de inicio.")
        Selector("Período", PERIODOS, periodo, { periodo = it })

        if (r.h <= 0) {
            Panel("") { Subtexto("Todavía no hay turnos con horario. Cargá hora de inicio y fin en el Diario.") }
        } else {
            val real = (r.ing - r.gastos) / r.h
            FilaKpis {
                KpiMonto("Ganancia real por hora", real, if (real > 0) Paleta.Verde else Paleta.Rojo, peso())
                Kpi("Pedidos por hora", String.format(java.util.Locale.US, "%.1f", r.ped / r.h), Paleta.Ambar, peso())
            }
            FilaKpis {
                Kpi("Ingreso por pedido", if (r.ped > 0) money0(r.ing / r.ped) else "—", Paleta.Acento, peso())
                Kpi("Horas trabajadas", String.format(java.util.Locale.US, "%.1f h", r.h), Paleta.Violeta, peso())
            }

            fun mejor(m: Map<String, G>): String {
                val e = m.entries.maxByOrNull { it.value.ing / it.value.h } ?: return "—"
                return e.key.substringBefore(" (") + " · " + money0(e.value.ing / e.value.h) + "/h"
            }
            FilaKpis {
                Kpi("Mejor app", mejor(r.app), Paleta.Acento, peso())
                Kpi("Mejor día", mejor(r.dia), Paleta.Verde, peso())
                Kpi("Mejor franja", mejor(r.fra), Paleta.Ambar, peso())
            }

            for ((titulo, mapa) in listOf("Por app" to r.app, "Por día de la semana" to r.dia, "Por franja horaria" to r.fra)) {
                Panel(titulo) {
                    val orden = mapa.entries.sortedByDescending { it.value.ing / it.value.h }
                    orden.forEachIndexed { i, e ->
                        val g = e.value
                        FilaDato(
                            "${e.key}  ·  ${String.format(java.util.Locale.US, "%.1f", g.h)} h",
                            money0(g.ing / g.h) + "/h" + (if (g.ped > 0) "  ·  " + money0(g.ing / g.ped) + "/ped" else ""),
                            if (i == 0) Paleta.Verde else androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Panel("Ingreso por hora según el día de la semana") {
                GraficoBarras((0..6).map { i ->
                    val g = r.dia[DIAS[i]]
                    DIAS_CORTOS[i] to (if (g != null && g.h > 0) g.ing / g.h else 0.0)
                }, Paleta.Verde)
            }
        }
    }
}
