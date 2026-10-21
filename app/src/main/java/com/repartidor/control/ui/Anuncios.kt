package com.repartidor.control.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds

object ConfigAnuncios {
    /**
     * ID del bloque de anuncios. Este es el de PRUEBA de Google (muestra anuncios de ejemplo).
     * Antes de publicar, creá tu cuenta de AdMob y reemplazalo por tu ID real
     * (también el ID de aplicación en AndroidManifest.xml).
     * No toques tus propios anuncios reales mientras probás: AdMob puede suspender la cuenta.
     */
    const val BANNER = "ca-app-pub-3940256099942544/6300978111"
}

/** Banner de anuncios (AdMob). */
@Composable
fun BannerAnuncio(modifier: Modifier = Modifier) {
    val contexto = LocalContext.current
    LaunchedEffect(Unit) { MobileAds.initialize(contexto) }
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            val vista = AdView(ctx)
            vista.setAdSize(AdSize.BANNER)
            vista.adUnitId = ConfigAnuncios.BANNER
            vista.loadAd(AdRequest.Builder().build())
            vista
        }
    )
}
