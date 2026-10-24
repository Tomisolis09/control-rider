package com.repartidor.control

/** Un producto de la tienda. Para sumar descuentos más adelante alcanza con poner [precioAnterior] (precio sin descuento). */
data class Producto(
    val id: String,
    val nombre: String,
    val descripcion: String,
    val precio: Double,
    val emoji: String,
    val categoria: String,
    val precioAnterior: Double = 0.0,   // si es mayor que precio, se muestra tachado
    val etiqueta: String = ""           // ej. "Nuevo", "Oferta", "Más vendido"
)

/**
 * Catálogo de la tienda. EDITÁ ESTE ARCHIVO para poner tus productos y precios reales.
 * Los de abajo son solo EJEMPLOS.
 */
object Tienda {
    /** Tu número de WhatsApp con código de país y sin + ni espacios. Argentina (celular): 549 + código de área + número. */
    const val WHATSAPP = "5491100000000"

    /** Mientras el número sea el de ejemplo, la app avisa que falta configurarlo. */
    val numeroConfigurado: Boolean get() = WHATSAPP != "5491100000000"

    val PRODUCTOS = listOf(
        Producto("soporte", "Soporte de celular para moto", "Antivibración, se ajusta a cualquier manubrio. Ideal para el GPS.", 14900.0, "📱", "Accesorios", etiqueta = "Más vendido"),
        Producto("cargador", "Cargador USB para moto", "Doble salida, resistente al agua. Cargá mientras repartís.", 9900.0, "🔌", "Accesorios"),
        Producto("funda", "Funda impermeable para celular", "Protege del agua y del polvo y deja usar la pantalla.", 6500.0, "💧", "Accesorios", precioAnterior = 8000.0, etiqueta = "Oferta"),
        Producto("mochila", "Mochila térmica de reparto", "Mantiene la comida caliente. Reforzada y fácil de limpiar.", 38500.0, "🎒", "Equipamiento"),
        Producto("guantes", "Guantes para moto", "Con protecciones y dedos táctiles para usar el celular.", 11900.0, "🧤", "Protección"),
        Producto("luz", "Luz LED trasera recargable", "Más visibilidad de noche. Carga por USB.", 5900.0, "🔦", "Protección", etiqueta = "Nuevo")
    )

    fun producto(id: String): Producto? = PRODUCTOS.firstOrNull { it.id == id }
}
