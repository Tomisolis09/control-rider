package com.repartidor.control.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repartidor.control.AppState
import com.repartidor.control.Producto
import com.repartidor.control.Tienda
import com.repartidor.control.money0

@Composable
private fun Chip(texto: String, activo: Boolean, onClick: () -> Unit) {
    val fondo = if (activo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val letra = if (activo) Color.White else MaterialTheme.colorScheme.onSurface
    Box(
        Modifier.clip(RoundedCornerShape(20.dp)).background(fondo).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) { Text(texto, color = letra, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
}

/** Botón redondo de + y − del carrito. */
@Composable
private fun Paso(texto: String, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(texto, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
}

@Composable
private fun TarjetaProducto(p: Producto, cant: Int, mas: () -> Unit, menos: () -> Unit) {
    val apagado = MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        modifier = Modifier.fillMaxWidth().aparecer().animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, if (cant > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(88.dp).clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(Paleta.Acento.copy(alpha = 0.28f), Paleta.Violeta.copy(alpha = 0.28f)))),
                contentAlignment = Alignment.Center
            ) { Text(p.emoji, fontSize = 42.sp) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (p.etiqueta.isNotEmpty()) {
                    Text(
                        p.etiqueta.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Paleta.Ambar,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Paleta.Ambar.copy(alpha = 0.14f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Text(p.nombre, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(p.descripcion, fontSize = 12.sp, color = apagado)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    Text(money0(p.precio), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    if (p.precioAnterior > p.precio) {
                        Text(money0(p.precioAnterior), fontSize = 12.sp, color = apagado, textDecoration = TextDecoration.LineThrough)
                    }
                }
                if (cant == 0) {
                    Boton("Agregar al carrito", mas, Modifier.fillMaxWidth())
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Paso("−", menos)
                        Text("$cant en el carrito", Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Paso("+", mas)
                    }
                }
            }
        }
    }
}

@Composable
fun TiendaScreen(app: AppState) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val carrito = app.carrito
    var cat by remember { mutableStateOf("Todos") }
    var busca by remember { mutableStateOf("") }
    var verCarrito by remember { mutableStateOf(false) }
    var nombre by remember { mutableStateOf(app.db.cfgS("tienda_nombre", "")) }
    var direccion by remember { mutableStateOf(app.db.cfgS("tienda_dir", "")) }

    val categorias = listOf("Todos") + Tienda.PRODUCTOS.map { it.categoria }.distinct()
    val q = busca.trim()
    val lista = Tienda.PRODUCTOS.filter {
        (cat == "Todos" || it.categoria == cat) &&
            (q.isEmpty() || it.nombre.contains(q, ignoreCase = true) || it.descripcion.contains(q, ignoreCase = true))
    }
    val unidades = carrito.values.sum()
    val total = Tienda.PRODUCTOS.sumOf { (carrito[it.id] ?: 0) * it.precio }

    fun agregar(id: String) {
        carrito[id] = minOf(20, (carrito[id] ?: 0) + 1)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    fun quitar(id: String) {
        val n = (carrito[id] ?: 0) - 1
        if (n <= 0) carrito.remove(id) else carrito[id] = n
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun enviar() {
        if (!Tienda.numeroConfigurado) {
            app.aviso = "Falta configurar el número de WhatsApp de la tienda (archivo Tienda.kt)"
            return
        }
        app.db.setCfg("tienda_nombre", nombre.trim())
        app.db.setCfg("tienda_dir", direccion.trim())
        val sb = StringBuilder("Hola! Quiero hacer este pedido desde Control Rider:\n")
        for (p in Tienda.PRODUCTOS) {
            val n = carrito[p.id] ?: 0
            if (n > 0) sb.append("• $n x ${p.nombre} (${money0(p.precio)} c/u)\n")
        }
        sb.append("Total: ${money0(total)}\n")
        if (nombre.isNotBlank()) sb.append("Nombre: ${nombre.trim()}\n")
        if (direccion.isNotBlank()) sb.append("Dirección / zona: ${direccion.trim()}\n")
        sb.append("¿Coordinamos el pago y la entrega?")
        val uri = Uri.parse("https://wa.me/${Tienda.WHATSAPP}?text=" + Uri.encode(sb.toString()))
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
            verCarrito = false
            app.aviso = "Se abrió WhatsApp con tu pedido. Tu carrito sigue acá hasta que lo vacíes."
        } catch (e: Exception) {
            app.aviso = "No se pudo abrir WhatsApp"
        }
    }

    Box(Modifier.fillMaxSize()) {
        Pantalla {
            Titulo("Tienda", "Equipá tu moto o bici y pedí por WhatsApp")
            CampoTexto("Buscar producto", busca, { busca = it })
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in categorias) Chip(c, c == cat) { cat = c }
            }
            Panel("Próximamente", acento = Paleta.Violeta) {
                Text("🎁 Descuentos y regalos para los que más usan la app.", fontSize = 14.sp)
            }
            if (lista.isEmpty()) Subtexto("No hay productos que coincidan con tu búsqueda.")
            for (p in lista) {
                TarjetaProducto(p, carrito[p.id] ?: 0, { agregar(p.id) }, { quitar(p.id) })
            }
            Spacer(Modifier.height(96.dp))
        }

        if (unidades > 0) {
            Card(
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth().aparecer(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Row(
                    Modifier.clickable { verCarrito = true }.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("🛒 $unidades ${if (unidades == 1) "producto" else "productos"}", color = Color.White, fontSize = 13.sp)
                        Text(money0(total), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Text("Ver carrito ›", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }

    if (verCarrito && unidades > 0) {
        AlertDialog(
            onDismissRequest = { verCarrito = false },
            title = { Text("Tu pedido") },
            text = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (p in Tienda.PRODUCTOS) {
                        val n = carrito[p.id] ?: 0
                        if (n > 0) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${p.emoji} ${p.nombre}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Subtexto("${money0(p.precio)} c/u · ${money0(p.precio * n)}")
                                }
                                Paso("−") { quitar(p.id) }
                                Text("$n", Modifier.padding(horizontal = 8.dp), fontWeight = FontWeight.Bold)
                                Paso("+") { agregar(p.id) }
                            }
                        }
                    }
                    FilaDato("TOTAL", money0(total), Paleta.Verde, true)
                    CampoTexto("Tu nombre", nombre, { nombre = it })
                    CampoTexto("Dirección o zona de entrega", direccion, { direccion = it })
                    Subtexto("El pedido se envía por WhatsApp y ahí coordinamos el pago y la entrega.")
                    TextButton(onClick = { carrito.clear(); verCarrito = false }) { Text("Vaciar carrito", color = Paleta.Rojo) }
                }
            },
            confirmButton = { TextButton(onClick = { enviar() }) { Text("Pedir por WhatsApp") } },
            dismissButton = { TextButton(onClick = { verCarrito = false }) { Text("Seguir comprando") } }
        )
    }
}
