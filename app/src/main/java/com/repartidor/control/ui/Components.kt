package com.repartidor.control.ui

import android.app.DatePickerDialog
import android.graphics.Paint
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.graphics.nativeCanvas
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

/** Entrada suave: el elemento aparece subiendo y con fundido la primera vez que se muestra. */
@Composable
fun Modifier.aparecer(): Modifier {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }
    return this.graphicsLayer {
        alpha = a.value
        translationY = (1f - a.value) * 24.dp.toPx()
    }
}

@Composable
fun Pantalla(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
fun Titulo(texto: String, sub: String? = null) {
    Column(Modifier.aparecer()) {
        Text(texto, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        if (sub != null) Text(sub, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Panel(
    titulo: String, modifier: Modifier = Modifier, acento: Color = MaterialTheme.colorScheme.primary,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth().aparecer(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (titulo.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(4.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(acento))
                    Spacer(Modifier.width(8.dp))
                    Text(titulo, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            content()
        }
    }
}

/** Panel que se pliega y se despliega al tocar el título. Plegado muestra un [resumen] debajo del título. */
@Composable
fun PanelPlegable(
    titulo: String, modifier: Modifier = Modifier, acento: Color = MaterialTheme.colorScheme.primary,
    abierto0: Boolean = true, resumen: String = "", content: @Composable ColumnScope.() -> Unit
) {
    var abierto by remember { mutableStateOf(abierto0) }
    val giro by animateFloatAsState(if (abierto) 90f else 0f, spring(dampingRatio = 0.7f), label = "giro")
    val haptic = LocalHapticFeedback.current
    Card(
        modifier = modifier.fillMaxWidth().aparecer(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.animateContentSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    abierto = !abierto
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(4.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(acento))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(titulo, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (!abierto && resumen.isNotEmpty()) {
                        Text(resumen, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("›", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer { rotationZ = giro })
            }
            if (abierto) content()
        }
    }
}

/** Anillo de progreso animado con un texto en el centro. */
@Composable
fun Anillo(fraccion: Float, color: Color, centro: String, modifier: Modifier = Modifier, tam: Int = 104) {
    val f by animateFloatAsState(targetValue = fraccion.coerceIn(0f, 1f), animationSpec = tween(900), label = "anillo")
    val pista = MaterialTheme.colorScheme.outline
    val letra = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.size(tam.dp)) {
        val g = 12f * density
        val lado = size.minDimension - g
        val arriba = Offset(g / 2, g / 2)
        drawArc(pista, -90f, 360f, false, topLeft = arriba, size = Size(lado, lado), style = Stroke(width = g, cap = StrokeCap.Round))
        if (f > 0.005f) {
            drawArc(color, -90f, 360f * f, false, topLeft = arriba, size = Size(lado, lado), style = Stroke(width = g, cap = StrokeCap.Round))
        }
        texto(centro, size.width / 2, size.height / 2 + 6f * density, letra, 17f)
    }
}

/** Atajo grande y tocable (ícono + texto) que rebota al apretarlo. */
@Composable
fun Accion(icono: String, texto: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val fuente = remember { MutableInteractionSource() }
    val presionado by fuente.collectIsPressedAsState()
    val esc by animateFloatAsState(if (presionado) 0.92f else 1f, spring(dampingRatio = 0.55f), label = "accion")
    val haptic = LocalHapticFeedback.current
    Column(
        modifier.scale(esc).clip(RoundedCornerShape(18.dp)).background(color.copy(alpha = 0.14f))
            .clickable(interactionSource = fuente, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(icono, fontSize = 24.sp)
        Text(texto, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun Subtexto(texto: String, modifier: Modifier = Modifier) {
    Text(texto, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

@Composable
fun FilaDato(izq: String, der: String, colorDer: Color = MaterialTheme.colorScheme.onSurface, negrita: Boolean = false) {
    if (negrita) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(colorDer.copy(alpha = 0.10f)).padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Text(izq, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(der, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = colorDer)
        }
    } else {
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(izq, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(der, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colorDer)
        }
    }
}

@Composable
fun Aviso(nivel: String, texto: String) {
    val c = if (nivel == "rojo") Paleta.Rojo else Paleta.Ambar
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.copy(alpha = 0.10f)).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(Modifier.padding(top = 5.dp).size(8.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(10.dp))
        Text(texto, color = c, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

// ------------------------------------------------------------------ indicadores

@Composable
fun Kpi(titulo: String, valor: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.aparecer(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.16f), Color.Transparent)))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(6.dp))
                Text(titulo.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Text(valor, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Tarjeta grande con degradado para el número más importante de la pantalla. */
@Composable
fun Hero(titulo: String, saldo: Double, detalle: String, modifier: Modifier = Modifier) {
    val deuda = saldo < -0.005
    val animado by animateFloatAsState(targetValue = abs(saldo).toFloat(), animationSpec = tween(900), label = "hero")
    val fin = if (deuda) Paleta.Rojo else Paleta.Verde
    Box(
        modifier.fillMaxWidth().aparecer().clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(Paleta.Marino, fin)))
    ) {
        Box(
            Modifier.align(Alignment.TopEnd).offset(x = 36.dp, y = (-36).dp).size(130.dp)
                .clip(CircleShape).background(Color.White.copy(alpha = 0.09f))
        )
        Box(
            Modifier.align(Alignment.BottomEnd).offset(x = (-30).dp, y = 40.dp).size(90.dp)
                .clip(CircleShape).background(Color.White.copy(alpha = 0.07f))
        )
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(titulo.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.75f), letterSpacing = 1.2.sp)
            Text(com.repartidor.control.money(animado.toDouble()), fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text(detalle, fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f))
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
    val f by animateFloatAsState(targetValue = fraccion.coerceIn(0f, 1f), animationSpec = tween(700), label = "barra")
    Box(
        modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.outline)
    ) {
        Box(
            Modifier.fillMaxWidth(f).fillMaxHeight().clip(RoundedCornerShape(6.dp))
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.65f), color)))
        )
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

/** Número con decimales (litros, etc.), sin signo de moneda. */
@Composable
fun CampoDec(label: String, valor: String, onValor: (String) -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedTextField(
        value = valor, onValueChange = onValor, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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

/** tipo: 0 = principal, 1 = secundario, 2 = peligro. Se achica un poco al tocarlo. */
@Composable
fun Boton(texto: String, onClick0: () -> Unit, modifier: Modifier = Modifier, tipo: Int = 0) {
    val fuente = remember { MutableInteractionSource() }
    val presionado by fuente.collectIsPressedAsState()
    val esc by animateFloatAsState(if (presionado) 0.95f else 1f, spring(dampingRatio = 0.6f), label = "boton")
    val m = modifier.scale(esc).heightIn(min = 46.dp)
    val forma = RoundedCornerShape(14.dp)
    val haptic = LocalHapticFeedback.current
    val onClick: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onClick0()
    }
    when (tipo) {
        1 -> OutlinedButton(onClick = onClick, modifier = m, shape = forma, interactionSource = fuente) {
            Text(texto, fontWeight = FontWeight.SemiBold)
        }
        2 -> Button(
            onClick = onClick, modifier = m, shape = forma, interactionSource = fuente,
            colors = ButtonDefaults.buttonColors(containerColor = Paleta.Rojo)
        ) { Text(texto, fontWeight = FontWeight.SemiBold) }
        else -> Button(onClick = onClick, modifier = m, shape = forma, interactionSource = fuente) {
            Text(texto, fontWeight = FontWeight.SemiBold)
        }
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
    val fuerte = MaterialTheme.colorScheme.onSurface
    val crecer = remember { Animatable(0f) }
    var sel by remember { mutableStateOf(-1) }
    LaunchedEffect(datos) {
        sel = -1
        crecer.snapTo(0f)
        crecer.animateTo(1f, tween(600))
    }
    Canvas(
        modifier.fillMaxWidth().height(alto.dp).pointerInput(datos) {
            detectTapGestures { o ->
                if (datos.isNotEmpty()) {
                    val i = (o.x / (size.width.toFloat() / datos.size)).toInt().coerceIn(0, datos.size - 1)
                    sel = if (sel == i) -1 else i
                }
            }
        }
    ) {
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
                drawRoundRect(
                    color = (if (v > 0) color else Paleta.Rojo).copy(alpha = if (sel < 0 || sel == i) 1f else 0.4f),
                    topLeft = Offset(cx - bw / 2, minOf(y1, base)),
                    size = Size(bw, maxOf(abs(y1 - base), 1f)),
                    cornerRadius = CornerRadius(6f * density, 6f * density)
                )
                if (i == sel) {
                    texto(money0(v), cx.coerceIn(34f * density, size.width - 34f * density),
                        if (v > 0) y1 - 4f * density else y1 + 14f * density, fuerte, 11f)
                } else if (sel < 0 && slot > 34f * density) {
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
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Paleta.serie[i % Paleta.serie.size]))
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
