package com.repartidor.control.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Paleta {
    val Acento = Color(0xFF2563EB)
    val Verde = Color(0xFF16A34A)
    val Rojo = Color(0xFFDC2626)
    val Ambar = Color(0xFFD97706)
    val Violeta = Color(0xFF7C3AED)
    val Celeste = Color(0xFF0EA5E9)
    val Rosa = Color(0xFFEC4899)
    val Turquesa = Color(0xFF14B8A6)
    val Marino = Color(0xFF0F172A)
    val serie = listOf(Acento, Verde, Ambar, Rojo, Violeta, Celeste, Rosa, Turquesa)
}

private val Claro = lightColorScheme(
    primary = Paleta.Acento,
    background = Color(0xFFF1F4F9),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8EDF5),
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFE2E8F0)
)

private val Oscuro = darkColorScheme(
    primary = Color(0xFF60A5FA),
    background = Color(0xFF0B1220),
    surface = Color(0xFF131C31),
    surfaceVariant = Color(0xFF1E2B47),
    onBackground = Color(0xFFE2E8F0),
    onSurface = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF26324A)
)

/** tema: 0 = sistema, 1 = claro, 2 = oscuro */
@Composable
fun RepartidorTheme(tema: Int, content: @Composable () -> Unit) {
    val oscuro = if (tema == 0) isSystemInDarkTheme() else tema == 2
    MaterialTheme(colorScheme = if (oscuro) Oscuro else Claro, content = content)
}
