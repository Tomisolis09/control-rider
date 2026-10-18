package com.repartidor.control.ui

import android.app.DatePickerDialog
import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repartidor.control.DIAS_CORTOS
import com.repartidor.control.diaSemana
import com.repartidor.control.fmtFecha
import com.repartidor.control.fmtMonto
import com.repartidor.control.money0
import com.repartidor.control.num
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max

// ------------------------------------------------------------------ estructura

@Composable
fun Pantalla(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

@Composable
fun Titulo(texto: String, sub: String? = null) {
    Column {
        Text(texto, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (sub != null) Text(sub, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Panel(titulo: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (titulo.isNotEmpty()) Text(titulo, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            content()
        }
    }
}

@Composable
fun Subtexto(texto: String, modifier: Modifier = Modifier) {
    Text(texto, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

@Composable
fun FilaDato(izq: String, der: String, colorDer: Color = MaterialTheme.colorScheme.onSurface, negrita: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(izq, fontSize = 14.sp, fontWeight = if (negrita) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
        Text(der, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colorDer)
    }
}

@Composable
fun Aviso(nivel: String, texto: String) {
    val c = if (nivel == "rojo") Paleta.Rojo else Paleta.Ambar
    Row(verticalAlignment = Alignment.Top) {
        Text("●  ", color = c, fontSize = 13.sp)
        Text(texto, color = c, fontSize = 13.sp)
    }
}

// ------------------------------------------------------------------ indicadores

@Composable
fun Kpi(titulo: String, valor: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(3.dp).background(color))
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(titulo.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                Text(valor, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Indicador de monto que "cuenta" hasta el valor nuevo. */
@Composable
fun KpiMonto(titulo: String, valor: Double, color: Color, modifier: Modifier = Modifier, decimales: Int = 0) {
    val animado by animateFloatAsState(targetValue = valor.toFloat(), animationSpec = tween(600), label = "kpi")
    Kpi(titulo, com.repartidor.control.money(animado.toDouble(), decimales), color, modifier)
}

/** Dos o más campos lado a lado (cada uno con peso()). */
@Composable
fun Dos(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
fun FilaKpis(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
fun Barra(fraccion: Float, color: Color, modifier: Modifier = Modifier) {
    val f by animateFloatAsState(targetValue = fraccion.coerceIn(0f, 1f), animationSpec = tween(500), label = "barra")
    Box(
        modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.outline)
    ) {
        Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(color))
    }
}

// ------------------------------------------------------------------ campos

@Composable
fun CampoTexto(label: String, valor: String, onValor: (String) -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedTextField(
        value = valor, onValueChange = onValor, label = { Text(label) }, singleLine = true,
        modifier = modifier
    )
}

@Composable
fun CampoNum(label: String, valor: String, onValor: (String) -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedTextField(
        value = valor, onValueChange = onValor, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

/** Campo de dinero con el signo $ siempre visible; al salir del campo da formato (88.897,62). */
@Composable
fun CampoMonto(label: String, valor: String, onValor: (String) -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedTextField(
        value = valor, onValueChange = onValor, label = { Text(label) }, singleLine = true,
        prefix = { Text("\$ ") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier
            .onFocusChanged { estado ->
                if (!estado.isFocused && valor.isNotBlank()) {
                    val v = num(valor)
                    if (v != null) {
                        val f = fmtMonto(v)
                        if (f != valor) onValor(f)
                    }
                }
            }
    )
}

@Composable
fun Selector(label: String, opciones: List<String>, seleccion: String, onSel: (String) -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
    var abierto by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { abierto = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (label.isEmpty()) seleccion else "$label: $seleccion",
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            for (o in opciones) {
                DropdownMenuItem(text = { Text(o) }, onClick = { onSel(o); abierto = false })
            }
        }
    }
}

/** Campo de texto libre con lista de sugerencias desplegable. */
@Composable
fun ComboLibre(label: String, opciones: List<String>, valor: String, onValor: (String) -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
    var abierto by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = valor, onValueChange = onValor, label = { Text(label) }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = { TextButton(onClick = { abierto = true }) { Text("▾") } }
        )
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            for (o in opciones) {
                DropdownMenuItem(text = { Text(o) }, onClick = { onValor(o); abierto = false })
            }
        }
    }
}

@Composable
fun CampoFecha(fecha: LocalDate, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick = { onChange(fecha.minusDays(1)) }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("‹") }
        OutlinedButton(
            onClick = {
                DatePickerDialog(ctx, { _, y, m, d -> onChange(LocalDate.of(y, m + 1, d)) },
                    fecha.year, fecha.monthValue - 1, fecha.dayOfMonth).show()
            },
            modifier = Modifier.weight(1f)
        ) { Text("${DIAS_CORTOS[diaSemana(fecha)]} ${fmtFecha(fecha)}") }
        OutlinedButton(onClick = { onChange(fecha.plusDays(1)) }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("›") }
        TextButton(onClick = { onChange(LocalDate.now()) }) { Text("Hoy") }
    }
}

/** tipo: 0 = principal, 1 = secundario, 2 = peligro */
@Composable
fun Boton(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier, tipo: Int = 0) {
    when (tipo) {
        1 -> OutlinedButton(onClick = onClick, modifier = modifier) { Text(texto) }
        2 -> Button(
            onClick = onClick, modifier = modifier,
            colors = ButtonDefaults.buttonColors(containerColor = Paleta.Rojo)
        ) { Text(texto) }
        else -> Button(onClick = onClick, modifier = modifier) { Text(texto) }
    }
}

@Composable
fun Confirmar(visible: Boolean, titulo: String, texto: String, onOk: () -> Unit, onCancel: () -> Unit) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(titulo) },
        text = { Text(texto) },
        confirmButton = { TextButton(onClick = onOk) { Text("Sí") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("No") } }
    )
}

// ------------------------------------------------------------------ gráficos

private fun DrawScope.texto(t: String, x: Float, y: Float, color: Color, sizeSp: Float = 10f, alin: Paint.Align = Paint.Align.CENTER) {
    val p = Paint()
    p.isAntiAlias = true
    p.color = color.toArgb()
    p.textSize = sizeSp * density
    p.textAlign = alin
    drawContext.canvas.nativeCanvas.drawText(t, x, y, p)
}

private fun compacto(v: Double): String {
    val a = abs(v)
    val s = if (a >= 1_000_000) String.format(java.util.Locale.US, "%.1fM", a / 1_000_000)
    else if (a >= 1000) String.format(java.util.Locale.US, "%.0fk", a / 1000)
    else String.format(java.util.Locale.US, "%.0f", a)
    return if (v < 0) "-$s" else s
}

@Composable
fun GraficoBarras(datos: List<Pair<String, Double>>, color: Color, modifier: Modifier = Modifier, alto: Int = 150) {
    val apagado = MaterialTheme.colorScheme.onSurfaceVariant
    val linea = MaterialTheme.colorScheme.outline
    val crecer = remember { Animatable(0f) }
    LaunchedEffect(datos) {
        crecer.snapTo(0f)
        crecer.animateTo(1f, tween(600))
    }
    Canvas(modifier.fillMaxWidth().height(alto.dp)) {
        if (datos.isEmpty() || datos.all { it.second == 0.0 }) {
            texto("Sin datos para mostrar", size.width / 2, size.height / 2, apagado, 12f)
            return@Canvas
        }
        val padTop = 20f * density
        val padBottom = 22f * density
        val vmax = max(0.0, datos.maxOf { it.second })
        val vmin = minOf(0.0, datos.minOf { it.second })
        val span = if (vmax - vmin == 0.0) 1.0 else vmax - vmin
        val area = size.height - padTop - padBottom
        fun y(v: Double): Float = padTop + ((vmax - v) / span * area).toFloat()
        val slot = size.width / datos.size
        val bw = minOf(slot * 0.62f, 40f * density)
        val base = y(0.0)
        drawLine(linea, Offset(0f, base), Offset(size.width, base), 1f * density)
        datos.forEachIndexed { i, (etiqueta, v) ->
            val cx = slot * (i + 0.5f)
            if (v != 0.0) {
                val y1 = base + (y(v) - base) * crecer.value
                drawRect(
                    color = if (v > 0) color else Paleta.Rojo,
                    topLeft = Offset(cx - bw / 2, minOf(y1, base)),
                    size = Size(bw, abs(y1 - base))
                )
                if (slot > 34f * density) {
                    texto(compacto(v), cx, if (v > 0) y1 - 4f * density else y1 + 12f * density, apagado, 9f)
                }
            }
            if (datos.size <= 16 || i % 2 == 0) texto(etiqueta, cx, size.height - 6f * density, apagado, 9f)
        }
    }
}

@Composable
fun GraficoLinea(
    real: List<Pair<LocalDate, Double>>, ideal: List<Pair<LocalDate, Double>>, objetivo: Double,
    modifier: Modifier = Modifier, alto: Int = 160
) {
    val apagado = MaterialTheme.colorScheme.onSurfaceVariant
    val linea = MaterialTheme.colorScheme.outline
    Canvas(modifier.fillMaxWidth().height(alto.dp)) {
        val pts = real + ideal
        if (pts.isEmpty() || objetivo <= 0) {
            texto("Guardá tu meta para ver la evolución", size.width / 2, size.height / 2, apagado, 12f)
            return@Canvas
        }
        val hoy = LocalDate.now()
        val x0 = pts.map { it.first }.reduce { a, b -> if (a.isBefore(b)) a else b }
        var x1 = (pts.map { it.first } + hoy).reduce { a, b -> if (a.isAfter(b)) a else b }
        if (!x1.isAfter(x0)) x1 = x0.plusDays(1)
        val ymax = max(objetivo, pts.maxOf { it.second }) * 1.05
        val pl = 58f * density
        val pr = 10f * density
        val pt = 16f * density
        val pb = 22f * density
        val dias = ChronoUnit.DAYS.between(x0, x1).toFloat()
        fun px(d: LocalDate): Float = pl + ChronoUnit.DAYS.between(x0, d) / dias * (size.width - pl - pr)
        fun py(v: Double): Float = pt + ((1 - v / ymax) * (size.height - pt - pb)).toFloat()
        for (frac in listOf(0.0, 0.5, 1.0)) {
            val v = ymax / 1.05 * frac
            drawLine(linea, Offset(pl, py(v)), Offset(size.width - pr, py(v)), 1f * density)
            texto(compacto(v), pl - 6f * density, py(v) + 3f * density, apagado, 9f, Paint.Align.RIGHT)
        }
        val trazo = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
        drawLine(Paleta.Verde, Offset(pl, py(objetivo)), Offset(size.width - pr, py(objetivo)), 2f * density, pathEffect = trazo)
        if (!hoy.isBefore(x0) && !hoy.isAfter(x1)) {
            drawLine(linea, Offset(px(hoy), pt), Offset(px(hoy), size.height - pb), 1f * density)
        }
        for (i in 1 until ideal.size) {
            drawLine(apagado, Offset(px(ideal[i - 1].first), py(ideal[i - 1].second)),
                Offset(px(ideal[i].first), py(ideal[i].second)), 2f * density, pathEffect = trazo)
        }
        for (i in 1 until real.size) {
            drawLine(Paleta.Acento, Offset(px(real[i - 1].first), py(real[i - 1].second)),
                Offset(px(real[i].first), py(real[i].second)), 3f * density)
        }
        if (real.isNotEmpty()) {
            val u = real.last()
            drawCircle(Paleta.Acento, 5f * density, Offset(px(u.first), py(u.second)))
        }
        texto(fmtFecha(x0), pl, size.height - 4f * density, apagado, 9f, Paint.Align.LEFT)
        texto(fmtFecha(x1), size.width - pr, size.height - 4f * density, apagado, 9f, Paint.Align.RIGHT)
    }
}

/** Anillo con leyenda debajo. */
@Composable
fun GraficoAnillo(datos: List<Pair<String, Double>>, modifier: Modifier = Modifier) {
    val total = datos.sumOf { it.second }
    val apagado = MaterialTheme.colorScheme.onSurfaceVariant
    val barrido = remember { Animatable(0f) }
    LaunchedEffect(datos) {
        barrido.snapTo(0f)
        barrido.animateTo(1f, tween(700))
    }
    if (total <= 0) {
        Text("Sin gastos este mes", color = apagado, fontSize = 13.sp)
        return
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(130.dp)) {
            val grosor = 26f * density
            val lado = size.minDimension - grosor
            var ini = -90f
            datos.forEachIndexed { i, (_, v) ->
                val ext = (v / total * 360.0).toFloat() * barrido.value
                drawArc(
                    color = Paleta.serie[i % Paleta.serie.size], startAngle = ini, sweepAngle = ext, useCenter = false,
                    topLeft = Offset(grosor / 2, grosor / 2), size = Size(lado, lado), style = Stroke(width = grosor)
                )
                ini += ext
            }
            texto(compacto(total), size.width / 2, size.height / 2 + 4f * density, apagado, 13f)
        }
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            datos.forEachIndexed { i, (nombre, v) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(Paleta.serie[i % Paleta.serie.size]))
                    Spacer(Modifier.width(6.dp))
                    Text("${nombre.substringBefore(" (").take(16)}  ${(v / total * 100).toInt()}%", fontSize = 12.sp)
                }
            }
        }
    }
}

fun montoCorto(v: Double): String = money0(v)

/** Peso 1 para repartir indicadores en una fila (se usa dentro de FilaKpis). */
fun RowScope.peso(): Modifier = Modifier.weight(1f)

/** Indicador de saldo: "A COBRAR" en verde o "DEBÉS A LA APP" en rojo. */
@Composable
fun SaldoKpi(base: String, saldo: Double, modifier: Modifier = Modifier) {
    val titulo = if (saldo < -0.005) "$base: debés a la app" else "$base: a cobrar"
    val color = if (saldo < -0.005) Paleta.Rojo else Paleta.Verde
    KpiMonto(titulo, kotlin.math.abs(saldo), color, modifier)
}
