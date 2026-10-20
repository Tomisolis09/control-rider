package com.repartidor.control.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.repartidor.control.APPS
import com.repartidor.control.AppState
import com.repartidor.control.CATEGORIAS_GASTO
import com.repartidor.control.Row as FilaDb
import com.repartidor.control.fmtHora
import com.repartidor.control.fmtMonto
import com.repartidor.control.money
import com.repartidor.control.money0
import com.repartidor.control.num
import com.repartidor.control.parseHora
import java.time.LocalDate

@Composable
fun DiarioScreen(app: AppState) {
    val c = app.calc
    val db = app.db
    val v = app.version

    var fecha by remember { mutableStateOf(LocalDate.now()) }
    var appSel by remember { mutableStateOf(APPS[0]) }
    var pedidos by remember { mutableStateOf("") }
    var base by remember { mutableStateOf("") }
    var adic by remember { mutableStateOf("") }
    var grupo by remember { mutableStateOf("") }
    var bono by remember { mutableStateOf("") }
    var prop by remember { mutableStateOf("") }
    var efec by remember { mutableStateOf("") }
    var hIni by remember { mutableStateOf("") }
    var hFin by remember { mutableStateOf("") }
    var editando by remember { mutableStateOf<Long?>(null) }
    var gCat by remember { mutableStateOf(CATEGORIAS_GASTO[0]) }
    var gMonto by remember { mutableStateOf("") }
    var gNota by remember { mutableStateOf("") }
    var borrarTurno by remember { mutableStateOf<Long?>(null) }
    var borrarGasto by remember { mutableStateOf<Long?>(null) }

    val turnos = remember(v, fecha) { db.rows("SELECT * FROM diario WHERE fecha=? ORDER BY id", fecha.toString()) }
    val gastos = remember(v, fecha) { db.rows("SELECT * FROM gastos WHERE fecha=? ORDER BY id", fecha.toString()) }
    val t = remember(v, fecha) { c.totales(fecha, fecha) }
    val eq = remember(v, fecha) { c.equilibrio(fecha) }
    val ea = remember(v) { c.estadoAhorro(LocalDate.now()) }
    val guardado = remember(v, fecha, editando) {
        db.one(
            """SELECT COALESCE(SUM(base+adicionales+grupo+COALESCE(bono,0)),0) g, COALESCE(SUM(efectivo),0) e,
               COALESCE(SUM(propinas),0) p FROM diario WHERE fecha=? AND id != ?""",
            fecha.toString(), editando ?: -1L
        )!!
    }

    fun nv(s: String): Double = num(s) ?: 0.0

    // valores "en vivo": lo guardado del día + lo que estás tipeando
    val gen = guardado.d("g") + nv(base) + nv(adic) + nv(grupo) + nv(bono)
    val efecVivo = guardado.d("e") + nv(efec)
    val llevo = gen + guardado.d("p") + nv(prop)

    fun limpiar() {
        pedidos = ""; base = ""; adic = ""; grupo = ""; bono = ""; prop = ""; efec = ""; hIni = ""; hFin = ""
        editando = null
    }

    fun guardarTurno() {
        val montos = listOf(base, adic, grupo, bono, prop, efec).map { num(it) }
        val ped = num(pedidos)
        if (montos.any { it == null } || ped == null) {
            app.aviso = "Revisá los montos: deben ser números positivos"
            return
        }
        val a = parseHora(hIni)
        val z = parseHora(hFin)
        if ((hIni.isNotBlank() && a == null) || (hFin.isNotBlank() && z == null)) {
            app.aviso = "Hora inválida: escribila así 18:30 (o solo 18)"
            return
        }
        if ((a == null) != (z == null)) {
            app.aviso = "Completá hora de inicio y de fin, o dejá ambas vacías"
            return
        }
        val b = montos[0]!!
        val ad = montos[1]!!
        val g = montos[2]!!
        val bn = montos[3]!!
        val p = montos[4]!!
        val e = montos[5]!!
        if (b + ad + g + bn + p + e + ped == 0.0) {
            app.aviso = "Cargá al menos un dato antes de guardar"
            return
        }
        val hi = if (a != null) fmtHora(a) else ""
        val hf = if (z != null) fmtHora(z) else ""
        val id = editando
        if (id != null) {
            db.exec(
                """UPDATE diario SET fecha=?, app=?, base=?, adicionales=?, grupo=?, propinas=?, efectivo=?,
                   pedidos=?, hora_inicio=?, hora_fin=?, bono=? WHERE id=?""",
                fecha.toString(), appSel, b, ad, g, p, e, ped.toInt(), hi, hf, bn, id
            )
        } else {
            db.exec(
                """INSERT INTO diario(fecha,app,base,adicionales,grupo,propinas,efectivo,pedidos,hora_inicio,hora_fin,bono)
                   VALUES(?,?,?,?,?,?,?,?,?,?,?)""",
                fecha.toString(), appSel, b, ad, g, p, e, ped.toInt(), hi, hf, bn
            )
        }
        val saldo = b + ad + g + bn - e
        var msg = "Turno guardado"
        if (saldo < -0.005) msg += " · le debés ${money(-saldo)} a la app por este turno"
        val ea2 = c.estadoAhorro(LocalDate.now())
        if (ea2 != null && ea2.falta > 0.005 && ea2.estado != "cumplido") msg += " · apartá ${money0(ea2.falta)} para tu meta"
        limpiar()
        app.cambio(msg)
    }

    fun editar(r: FilaDb) {
        editando = r.l("id")
        appSel = r.s("app")
        pedidos = r.i("pedidos").toString()
        base = fmtMonto(r.d("base")); adic = fmtMonto(r.d("adicionales")); grupo = fmtMonto(r.d("grupo"))
        bono = fmtMonto(r.d("bono")); prop = fmtMonto(r.d("propinas")); efec = fmtMonto(r.d("efectivo"))
        hIni = r.sn("hora_inicio") ?: ""
        hFin = r.sn("hora_fin") ?: ""
    }

    fun guardarGasto() {
        val m = num(gMonto)
        if (m == null) { app.aviso = "El monto debe ser un número positivo"; return }
        if (m <= 0) return
        db.exec("INSERT INTO gastos(fecha,categoria,monto,nota) VALUES(?,?,?,?)", fecha.toString(), gCat, m, gNota.trim())
        gMonto = ""; gNota = ""
        app.cambio("Gasto agregado")
    }

    val apagado = MaterialTheme.colorScheme.onSurfaceVariant

    Pantalla {
        Titulo("Registro diario")
        CampoFecha(fecha, { fecha = it; limpiar() })

        FilaKpis {
            SaldoKpi("Del día", t.neto, peso())
            KpiMonto("Efectivo recaudado", t.efectivo, Paleta.Violeta, peso())
        }
        FilaKpis {
            Kpi("Pedidos", t.pedidos.toString(), Paleta.Ambar, peso())
            KpiMonto("Gastos", t.gastos, Paleta.Rojo, peso())
            KpiMonto("Propinas", t.propinas, Paleta.Verde, peso())
        }

        // ---- punto de equilibrio
        Panel("Punto de equilibrio del día") {
            if (eq.objetivo <= 0) {
                Text("Todavía no hay datos para calcularlo.", fontSize = 14.sp)
                Subtexto("Cargá gastos fijos del hogar y algunos turnos con sus gastos.")
            } else {
                val partes = ArrayList<String>()
                partes.add("hogar ${money0(eq.fijosDia)}")
                partes.add("gastos de trabajo ${money0(eq.gastosDia)}")
                if (eq.fondoDia > 0.5) partes.add("fondo de repuestos ${money0(eq.fondoDia)}")
                if (eq.descDia > 0.5) partes.add("seguro y crédito de la app ${money0(eq.descDia)}")
                Text("Para cubrir todo necesitás facturar ${money(eq.objetivo)} por día (${partes.joinToString(" + ")}).", fontSize = 14.sp)
                val pct = llevo / eq.objetivo
                Barra(pct.toFloat(), if (pct >= 1) Paleta.Verde else Paleta.Ambar)
                if (llevo >= eq.objetivo) {
                    Text("✔ Cubriste tus gastos con ${money(llevo)} (incluye propinas). Desde acá es ganancia limpia: ${money(llevo - eq.objetivo)} de excedente.",
                        fontSize = 13.sp, color = Paleta.Verde)
                } else {
                    val falta = eq.objetivo - llevo
                    val extra = if (eq.ingresoPedido > 0) " (~${Math.ceil(falta / eq.ingresoPedido).toInt()} pedidos más)" else ""
                    Subtexto("Llevás ${money(llevo)} (${(pct * 100).toInt()}%, incluye propinas). Te faltan ${money(falta)}$extra.")
                }
            }
        }

        // ---- caja de efectivo en vivo
        Panel("Caja de efectivo en vivo") {
            if (gen <= 0 && efecVivo <= 0) {
                Text("Sin movimientos todavía.", fontSize = 14.sp)
                Subtexto("Se actualiza mientras escribís base, adicionales, bono y efectivo.")
            } else if (efecVivo > gen) {
                Text("Estás debiendo ${money(efecVivo - gen)} a la app", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Paleta.Rojo)
                Barra(1f, Paleta.Rojo)
                Subtexto("Juntaste ${money(efecVivo)} en efectivo y generaste ${money(gen)}. Te faltan ${money(efecVivo - gen)} de ganancia para no quedar en deuda.")
            } else {
                val pct = efecVivo / gen
                Text("Margen disponible: ${money(gen - efecVivo)}", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Barra(pct.toFloat(), if (pct >= 0.8) Paleta.Ambar else Paleta.Verde)
                Subtexto("Efectivo encima ${money(efecVivo)} = ${(pct * 100).toInt()}% de lo generado (${money(gen)})." +
                    (if (pct >= 0.8) " Cuidado: estás cerca del límite." else ""))
            }
        }

        // ---- aviso de ahorro para la meta
        Panel("Ahorro para tu meta") {
            val (txt, color, boton) = textoAhorro(ea, apagado)
            Text(txt, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (boton != null) Boton(boton, { app.aporteDiario() })
        }

        // ---- formulario del turno
        Panel(if (editando != null) "Editando turno" else "Turno / ingresos") {
            Selector("App", APPS, appSel, { appSel = it })
            Dos {
                CampoNum("Pedidos", pedidos, { pedidos = it }, peso())
                CampoMonto("Pago base", base, { base = it }, peso())
            }
            Dos {
                CampoMonto("Adicionales", adic, { adic = it }, peso())
                CampoMonto("Adic. por grupo", grupo, { grupo = it }, peso())
            }
            Dos {
                CampoMonto("Bono", bono, { bono = it }, peso())
                CampoMonto("Propinas", prop, { prop = it }, peso())
            }
            CampoMonto("Efectivo juntado", efec, { efec = it })
            Dos {
                CampoTexto("Hora inicio", hIni, { hIni = it }, peso())
                CampoTexto("Hora fin", hFin, { hFin = it }, peso())
            }
            Subtexto("Horario opcional (ej. 18:00 a 23:30): habilita el rendimiento por hora.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Boton(if (editando != null) "Guardar cambios" else "Guardar turno", { guardarTurno() })
                if (editando != null) Boton("Cancelar", { limpiar() }, tipo = 1)
            }
        }

        // ---- turnos del día
        Panel("Turnos del día") {
            if (turnos.isEmpty()) Subtexto("No hay turnos cargados en esta fecha.")
            for (r in turnos) {
                Column(Modifier.fillMaxWidth()) {
                    val horario = if (r.tiene("hora_inicio") && r.s("hora_inicio").isNotEmpty()) " · ${r.s("hora_inicio")}–${r.s("hora_fin")}" else ""
                    Text("${r.s("app")}$horario · ${r.i("pedidos")} pedidos", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Subtexto("Base ${money(r.d("base"))} · Adic. ${money(r.d("adicionales"))} · Grupo ${money(r.d("grupo"))} · Bono ${money(r.d("bono"))}")
                    Subtexto("Propinas ${money(r.d("propinas"))} · Efectivo ${money(r.d("efectivo"))}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Boton("Editar", { editar(r) }, tipo = 1)
                        Boton("Borrar", { borrarTurno = r.l("id") }, tipo = 2)
                    }
                }
            }
        }

        // ---- gastos del día
        Panel("Gastos del día") {
            Selector("Categoría", CATEGORIAS_GASTO, gCat, { gCat = it })
            Dos {
                CampoMonto("Monto", gMonto, { gMonto = it }, peso())
                CampoTexto("Nota", gNota, { gNota = it }, peso())
            }
            Boton("Agregar gasto", { guardarGasto() })
            for (r in gastos) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("${r.s("categoria")} · ${money(r.d("monto"))}", fontSize = 14.sp)
                        if (r.s("nota").isNotEmpty()) Subtexto(r.s("nota"))
                    }
                    Boton("Borrar", { borrarGasto = r.l("id") }, tipo = 2)
                }
            }
        }
    }

    Confirmar(borrarTurno != null, "Borrar turno", "¿Borrar el turno seleccionado?",
        onOk = {
            borrarTurno?.let { db.exec("DELETE FROM diario WHERE id=?", it) }
            borrarTurno = null
            app.cambio("Turno borrado")
        },
        onCancel = { borrarTurno = null })
    Confirmar(borrarGasto != null, "Borrar gasto", "¿Borrar el gasto seleccionado?",
        onOk = {
            borrarGasto?.let { db.exec("DELETE FROM gastos WHERE id=?", it) }
            borrarGasto = null
            app.cambio("Gasto borrado")
        },
        onCancel = { borrarGasto = null })
}
