package com.repartidor.control

val APPS = listOf("PedidosYa", "Rappi", "Rapiboy", "Mercado Extra", "Otra")
val CATEGORIAS_GASTO = listOf("Nafta", "Comida", "Peaje", "Parches", "Estacionamiento", "Otro")
val CATEGORIAS_FIJOS = listOf(
    "Alquiler", "Comida", "Servicios (luz, gas, agua)", "Internet / celular", "Expensas",
    "Transporte", "Salud", "Educación", "Otro"
)
val VEHICULOS = listOf("Moto", "Bici", "Auto")
val CONCEPTOS_VENC = listOf("Seguro", "Patente", "Licencia de conducir", "Revisión técnica (VTV)", "Otro")
val MODOS_FONDO = listOf("Desactivado", "Monto fijo por pedido", "% de lo generado")

val MANTENIMIENTO_BASICO: Map<String, List<String>> = mapOf(
    "Moto" to listOf(
        "Cambio de aceite", "Filtro de aceite", "Filtro de aire", "Bujía", "Cadena / kit de transmisión",
        "Pastillas de freno", "Cubierta delantera", "Cubierta trasera", "Cámara / parche", "Batería",
        "Lámparas / luces", "Cables (embrague / acelerador)", "Service general"
    ),
    "Bici" to listOf(
        "Parche / cámara", "Cubierta", "Cadena", "Pastillas / zapatas de freno", "Cables y fundas",
        "Lubricación", "Cambio de piñón / plato", "Luces", "Service general"
    ),
    "Auto" to listOf(
        "Cambio de aceite", "Filtro de aceite", "Filtro de aire", "Filtro de nafta", "Bujías",
        "Pastillas de freno", "Neumáticos", "Alineación y balanceo", "Batería", "Correa de distribución",
        "Líquido refrigerante", "Lámparas / luces", "Service general"
    )
)

class Sugerido(val item: String, val dias: Int, val pedidos: Int, val km: Int)

/** Valores orientativos: ajustalos con el manual de tu vehículo. */
val SUGERIDOS: Map<String, List<Sugerido>> = mapOf(
    "Moto" to listOf(
        Sugerido("Cambio de aceite", 60, 600, 3000), Sugerido("Cadena / kit de transmisión", 90, 900, 5000),
        Sugerido("Pastillas de freno", 120, 1200, 8000), Sugerido("Cubierta trasera", 240, 2500, 12000),
        Sugerido("Service general", 180, 1800, 6000)
    ),
    "Bici" to listOf(
        Sugerido("Lubricación", 14, 150, 200), Sugerido("Cadena", 180, 1800, 2500),
        Sugerido("Pastillas / zapatas de freno", 120, 1200, 2000), Sugerido("Service general", 90, 900, 1500)
    ),
    "Auto" to listOf(
        Sugerido("Cambio de aceite", 90, 700, 8000), Sugerido("Filtro de aire", 180, 1400, 15000),
        Sugerido("Pastillas de freno", 150, 1200, 20000), Sugerido("Neumáticos", 365, 3000, 40000),
        Sugerido("Service general", 180, 1500, 10000)
    )
)
