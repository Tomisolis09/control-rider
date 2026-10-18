package com.repartidor.control.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.repartidor.control.Plan
import com.repartidor.control.fmtCorto
import com.repartidor.control.fmtFecha
import com.repartidor.control.fmtMonto
import com.repartidor.control.money
import com.repartidor.control.money0
import com.repartidor.control.num
import com.repartidor.control.parseFecha
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Una línea del plan: texto + tipo (0 normal, 1 título, 2 bien, 3 mal, 4 apagado). */
private class Linea(val texto: String, val tipo: Int)

private fun lineasPlan(p: Plan): List<Linea> {
    if (p.estado == "cumplida") return listOf(Linea("¡Meta cumplida! 🎉 Definí una nueva meta cuando quieras.", 2))
    if (p.estado == "sin_datos") return listOf(
        Linea("Todavía no hay turnos en los últimos 30 días.", 1),
        Linea("Cargá tus turnos en el Diario y volvé para ver el plan.", 4)
    )
    val l = ArrayList<Linea>()
    l.add(Linea("Tu ritmo (últimos 30 días)", 1))
    l.add(Linea("• ${p.diasTrab} días trabajados · ${String.format(java.util.Locale.US, "%.1f", p.pedidosDia)} pedidos por día", 0))
    l.add(Linea("• Ganancia por día: ${money(p.porDia)} (descontados efectivo y gastos del turno)", 0))
    l.add(Linea("• Estimado por semana (${p.diasSemana} días): ${money(p.porSemBruta)}", 0))
    if (p.fijosMes > 0) l.add(Linea("• Gastos fijos del hogar: ${money(p.fijosMes)} por mes (~${money(p.fijosSem)} por semana)", 0))
    l.add(Linea("• Te queda para ahorrar por semana: ${money(p.porSem)}", 0))
    if (p.estado == "sin_margen") {
        l.add(Linea("Sin margen para ahorrar", 1))
        l.add(Linea("Tus gastos (del turno y del hogar) igualan o superan tus ingresos. Revisá los más grandes y qué app te paga mejor por pedido.", 3))
        return l
    }
    val semNec = p.semNec ?: 0.0
    l.add(Linea("A tu ritmo actual", 1))
    l.add(Linea("• Ahorrando el 100% de lo que te queda llegás en ~${String.format(java.util.Locale.US, "%.1f", semNec)} semanas " +
        "(~${String.format(java.util.Locale.US, "%.1f", semNec / 4.345)} meses).", 0))
    val necSem = p.necSem
    val necDia = p.necDia
    if (necSem == null || necDia == null) {
        for (pc in listOf(30, 50)) {
            val aporte = p.porSem * pc / 100
            l.add(Linea("• Ahorrando el $pc%: ${money(aporte)} por semana → ~${Math.round(p.falta / aporte)} semanas.", 0))
        }
        l.add(Linea(if (p.limite != null) "La fecha límite ya pasó: definí una nueva." else "Tip: poné una fecha límite y te calculo cuánto apartar por día.", 4))
        return l
    }
    val pct = necSem / p.porSem * 100
    l.add(Linea("Para llegar el ${fmtFecha(p.limite!!)}", 1))
    l.add(Linea("• Tenés que apartar ${money(necSem)} por semana (${money(necDia)} por día trabajado).", 0))
    l.add(Linea("• Recomendación: ahorrá el ${minOf(pct, 100.0).toInt()}% de lo que te queda libre.", 0))
    if (p.factible == true) {
        l.add(Linea("✔ Es alcanzable: equivale al ${pct.toInt()}% de lo que te queda libre por semana.", 2))
        l.add(Linea("• Apartá ese monto apenas cobrás, antes de gastar el resto.", 0))
    } else {
        val deficit = necDia - p.porDiaLibre
        l.add(Linea("✘ Con tu ritmo actual te faltan ${money(deficit)} por día trabajado.", 3))
        if (p.prom > 0) l.add(Linea("• Equivale a ~${String.format(java.util.Locale.US, "%.1f", deficit / p.prom)} pedidos más por día (a ${money(p.prom)} promedio).", 0))
        if (p.diasSemana < 7) l.add(Linea("• Otra opción: sumar un día más de trabajo por semana.", 0))
        l.add(Linea("• O mover la fecha: a tu ritmo llegarías cerca del ${fmtFecha(LocalDate.now().plusDays((semNec * 7).toLong()))}.", 0))
    }
    return l
}

@Composable
fun MetaScreen(app: AppState) {
    val db = app.db
    val c = app.calc
    val v = app.version
    val hoy = LocalDate.now()

    val actual = remember { db.one("SELECT * FROM meta WHERE id=1") }
    var nombre by remember { mutableStateOf(actual?.s("nombre") ?: "") }
    var objetivo by remember { mutableStateOf(if (actual != null) fmtMonto(actual.d("objetivo")) else "") }
    var inicial by remember { mutableStateOf(if (actual != null) fmtMonto(actual.d("ahorrado")) else "") }
    var limite by remember {
        mutableStateOf(parseFecha(actual?.sn("fecha_limite") ?: "")?.let { fmtFecha(it) } ?: "")
    }
    var diasSem by remember { mutableStateOf(actual?.i("dias_semana")?.takeIf { it > 0 }?.toString() ?: "6") }
    var incProp by remember { mutableStateOf((actual?.i("incluir_propinas") ?: 1) != 0) }
    var apFecha by remember { mutableStateOf(hoy) }
    var apMonto by remember { mutableStateOf("") }
    var apNota by remember { mutableStateOf("") }
    var simPed by remember { mutableStateOf("0") }
    var simDias by remember { mutableStateOf("0") }
    var simTxt by remember { mutableStateOf("") }
    var borrar by remember { mutableStateOf<Long?>(null) }

    val p = remember(v) { c.plan(hoy) }
    val ea = remember(v) { c.estadoAhorro(hoy) }
    val aportes = remember(v) { db.rows("SELECT * FROM aportes ORDER BY fecha DESC, id DESC") }
    val apagado = MaterialTheme.colorScheme.onSurfaceVariant

    fun guardar() {
        val obj = num(objetivo)
        val aho = num(inicial)
        val dias = num(diasSem)?.toInt()
        if (obj == null || aho == null || dias == null) { app.aviso = "Revisá los montos: deben ser números positivos"; return }
        var lim = ""
        if (limite.isNotBlank()) {
            val f = parseFecha(limite)
            if (f == null) { app.aviso = "La fecha límite debe ser DD/MM/AAAA"; return }
            lim = f.toString()
        }
        if (obj <= 0) { app.aviso = "Ingresá cuánta plata querés juntar"; return }
        val previo = db.one("SELECT fecha_inicio FROM meta WHERE id=1")?.sn("fecha_inicio")
        val inicio = if (!previo.isNullOrEmpty()) previo else hoy.toString()
        val d = dias.coerceIn(1, 7)
        db.exec(
            """INSERT OR REPLACE INTO meta(id,nombre,objetivo,ahorrado,fecha_limite,dias_semana,incluir_propinas,fecha_inicio)
               VALUES(1,?,?,?,?,?,?,?)""",
            nombre.trim().ifEmpty { "Mi meta" }, obj, aho, lim, d, if (incProp) 1 else 0, inicio
        )
        db.setCfg("dias_semana", d)
        app.cambio("Meta guardada")
    }

    fun aportar() {
        val m = num(apMonto)
        if (m == null) { app.aviso = "El monto debe ser un número positivo"; return }
        if (m <= 0) return
        if (db.one("SELECT 1 x FROM meta WHERE id=1") == null) { app.aviso = "Primero guardá tu meta"; return }
        val hitos = c.registrarAporte(apFecha, m, apNota.trim())
        apMonto = ""; apNota = ""
        val extra = if (hitos.isNotEmpty()) " · ¡Llegaste al ${hitos.max()}% de tu meta! 🎉" else ""
        app.cambio("Aporte de ${money(m)} registrado$extra")
    }

    fun simular() {
        if (p.estado != "ok") { app.aviso = "Primero guardá tu meta y cargá turnos para conocer tu ritmo"; return }
        val ped = num(simPed)
        val dd = num(simDias)?.toInt()
        if (ped == null || dd == null) { app.aviso = "Usá números positivos"; return }
        val dias = minOf(dd, 7 - p.diasSemana)
        val nueva = (p.diasSemana + dias) * (p.porDia + ped * p.prom) - p.fijosSem
        val antes = p.falta / p.porSem
        if (nueva <= 0) { simTxt = "Aun así no te quedaría margen para ahorrar: revisá tus gastos."; return }
        val despues = p.falta / nueva
        val dif = nueva - p.porSem
        var txt = "Ahorrarías ${money(nueva)} por semana (${if (dif >= 0) "+" else ""}${money(dif)}). " +
            "Llegás en ~${String.format(java.util.Locale.US, "%.1f", despues)} semanas en lugar de ~${String.format(java.util.Locale.US, "%.1f", antes)}"
        txt += if (antes - despues > 0.05) {
            ": te adelantás ~${String.format(java.util.Locale.US, "%.1f", antes - despues)} semanas (cerca del ${fmtFecha(hoy.plusDays((despues * 7).toLong()))})."
        } else "."
        simTxt = txt
    }

    Pantalla {
        Titulo("Mi meta")

        if (p.estado == "sin_meta") {
            Panel("") { Text("Definí tu meta para empezar a ahorrar.", color = apagado) }
        } else {
            // indicadores
            FilaKpis {
                KpiMonto("Ahorrado", p.total, Paleta.Verde, peso())
                KpiMonto("Falta", p.falta, Paleta.Rojo, peso())
            }
            val inicio = p.inicio ?: hoy
            val lim = p.limite
            var ritmoTxt = "Sin fecha límite"
            var ritmoColor: Color = Paleta.Violeta
            if (lim != null && lim.isAfter(inicio)) {
                val fr = (ChronoUnit.DAYS.between(inicio, hoy).toDouble() / ChronoUnit.DAYS.between(inicio, lim)).coerceIn(0.0, 1.0)
                val idealHoy = p.inicial + (p.objetivo - p.inicial) * fr
                val dif = p.total - idealHoy
                if (Math.abs(dif) < p.objetivo * 0.005) { ritmoTxt = "En ritmo"; ritmoColor = Paleta.Verde }
                else if (dif > 0) { ritmoTxt = "Adelantado ${money0(dif)}"; ritmoColor = Paleta.Verde }
                else { ritmoTxt = "Atrasado ${money0(-dif)}"; ritmoColor = Paleta.Rojo }
            }
            FilaKpis {
                Kpi("Progreso", "${p.pct.toInt()}%", Paleta.Acento, peso())
                Kpi("Ritmo", ritmoTxt, ritmoColor, peso())
                Kpi("Días restantes", if (lim != null) "${maxOf(0L, ChronoUnit.DAYS.between(hoy, lim))} días" else "Sin fecha", Paleta.Ambar, peso())
            }
            Barra((p.pct / 100).toFloat(), Paleta.Verde)

            // aviso de hoy
            Panel("Hoy · ¿apartaste la plata de tu meta?") {
                if (p.estado == "cumplida") {
                    Text("🏁 ¡Meta cumplida!", color = Paleta.Verde, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                } else {
                    val (txt, color, boton) = textoAhorro(ea, apagado)
                    Text(txt, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (ea != null) {
                        Subtexto(
                            if (ea.modo == "fecha") "Calculado para llegar el ${fmtFecha(p.limite!!)} (≈ ${money(p.necSem ?: 0.0)} por semana)."
                            else "Sugerencia: el 30% de lo que te queda libre (poné una fecha límite para ajustarlo)."
                        )
                    }
                    if (boton != null) Boton(boton, { app.aporteDiario() })
                }
            }
        }

        // registrar aporte
        Panel("Registrar aporte") {
            CampoFecha(apFecha, { apFecha = it })
            Dos {
                CampoMonto("Monto", apMonto, { apMonto = it }, peso())
                CampoTexto("Nota", apNota, { apNota = it }, peso())
            }
            Boton("Apartar", { aportar() })
        }

        // evolución
        if (p.estado != "sin_meta") {
            Panel("Evolución del ahorro · real vs. ideal") {
                val inicio = p.inicio ?: hoy
                val real = ArrayList<Pair<LocalDate, Double>>()
                var acum = p.inicial
                real.add(inicio to acum)
                for (a in aportes.sortedWith(compareBy({ it.s("fecha") }, { it.l("id") }))) {
                    acum += a.d("monto")
                    val f = LocalDate.parse(a.s("fecha"))
                    real.add((if (f.isBefore(inicio)) inicio else f) to acum)
                }
                if (real.last().first.isBefore(hoy)) real.add(hoy to acum)
                val lim = p.limite
                val ideal = if (lim != null && lim.isAfter(inicio)) listOf(inicio to p.inicial, lim to p.objetivo) else emptyList()
                GraficoLinea(real, ideal, p.objetivo)
                Subtexto(if (ideal.isNotEmpty()) "— real    - - ideal    - - objetivo" else "— real    - - objetivo")
            }

            Panel("Plan para llegar") {
                for (ln in lineasPlan(p)) {
                    val color = when (ln.tipo) {
                        2 -> Paleta.Verde
                        3 -> Paleta.Rojo
                        4 -> apagado
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Text(ln.texto, color = color, fontSize = 14.sp, fontWeight = if (ln.tipo == 0 || ln.tipo == 4) FontWeight.Normal else FontWeight.Bold)
                }
            }

            Panel("Simulador ¿y si…?") {
                Dos {
                    CampoNum("Pedidos extra por día", simPed, { simPed = it }, peso())
                    CampoNum("Días extra por semana", simDias, { simDias = it }, peso())
                }
                Boton("Simular", { simular() })
                if (simTxt.isNotEmpty()) Text(simTxt, fontSize = 14.sp)
            }
        }

        // formulario de la meta
        Panel("Definí tu objetivo") {
            CampoTexto("Meta (ej. Comprar una moto)", nombre, { nombre = it })
            Dos {
                CampoMonto("Monto objetivo", objetivo, { objetivo = it }, peso())
                CampoMonto("Ahorro inicial", inicial, { inicial = it }, peso())
            }
            Dos {
                CampoTexto("Fecha límite (DD/MM/AAAA)", limite, { limite = it }, peso())
                CampoNum("Días que trabajo por semana", diasSem, { diasSem = it }, peso())
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = incProp, onCheckedChange = { incProp = it })
                Text("Contar propinas como ingreso", fontSize = 14.sp)
            }
            Boton("Guardar y calcular", { guardar() })
        }

        // historial
        Panel("Historial de aportes") {
            if (aportes.isEmpty()) Subtexto("Todavía no registraste aportes.")
            for (a in aportes) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(money(a.d("monto")), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Subtexto(fmtCorto(LocalDate.parse(a.s("fecha"))) + "/" + LocalDate.parse(a.s("fecha")).year +
                            (if (a.s("nota").isNotEmpty()) " · ${a.s("nota")}" else ""))
                    }
                    Boton("Borrar", { borrar = a.l("id") }, tipo = 2)
                }
            }
        }
    }

    Confirmar(borrar != null, "Borrar aporte", "¿Borrar el aporte seleccionado?",
        onOk = {
            borrar?.let { db.exec("DELETE FROM aportes WHERE id=?", it) }
            borrar = null
            app.cambio("Aporte borrado")
        },
        onCancel = { borrar = null })
}
