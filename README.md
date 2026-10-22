# Control Rider

App de Android (Kotlin + Jetpack Compose) para llevar el control de ganancias de un repartidor:
saldo diario, semanal y mensual, efectivo recaudado, bono, propinas, gastos, gastos fijos del hogar,
mantenimiento del vehículo (con km), recordatorios, rendimiento por hora y metas de ahorro.

## Descargar el APK
Cada vez que se sube código a GitHub, **GitHub compila el APK solo** (tarda unos 5 a 10 minutos).

- Link directo, siempre a la última versión:
  `https://github.com/TU_USUARIO/TU_REPOSITORIO/releases/latest/download/ControlRider.apk`
- O entrá a la pestaña **Releases** del repositorio y bajá `ControlRider.apk`.

### Instalarlo en el teléfono
1. Abrí el link desde el teléfono y descargá `ControlRider.apk`.
2. Android te va a pedir permiso para *instalar apps desconocidas* con el navegador que usaste: aceptalo.
3. Si aparece el aviso de Google Play Protect ("app no verificada"), elegí *Instalar de todos modos*:
   la app no está en Play Store, la firmó este repositorio.
4. Para actualizar, bajá el APK nuevo e instalalo encima: se conservan tus datos.

## Compilar a mano
- En GitHub: pestaña **Actions** > *Compilar APK de Control Rider* > **Run workflow**.
- En tu compu: abrí la carpeta con Android Studio y tocá Run ▶ (necesita Android 8.0 o superior, JDK 17).

## Si la compilación falla
Entrá a **Actions**, abrí la corrida con la cruz roja y copiá el error del paso *Compilar el APK*
(o bajá el archivo `registro-de-compilacion`). Con ese texto se corrige el código.

## Seguro, crédito y combustible
- **Seguro y crédito de la app** (Más > Seguro de la app y crédito): lo que la plataforma de reparto descuenta cada semana.
  El seguro es el de la app, **no** el seguro del vehículo (ese va en Recordatorios > Vencimientos).
  Se resta de lo que te toca cobrar (Inicio, Resumen, calendario de cobros), del plan de la meta y del punto de equilibrio.
  El crédito se descuenta en cuotas semanales hasta terminar de pagarlo.
- En la solapa **Diario**, el panel *Cuenta de la semana* muestra con qué empezó la semana (deuda anterior o cero), el saldo de los turnos,
  el seguro y la cuota, y el total. Desde ahí también se cargan el seguro y el crédito.
- **Combustible y costo por km** (Más > Combustible): cargá cada recarga con litros, monto y km del odómetro.
  Calcula el consumo (km/L) y cuánto te cuesta cada km, y suma la recarga a los gastos de nafta.

## Anuncios
- La app muestra un **banner de anuncios** (Google AdMob). Por ahora usa los **IDs de prueba** de Google:
  antes de publicar hay que reemplazarlos por los de tu cuenta de AdMob (`AndroidManifest.xml` y `ui/Anuncios.kt`).
- Hay un borrador de política de privacidad en `docs/politica-de-privacidad.md` (Google Play lo exige).

## Cálculo
`Saldo = base + adicionales + adicional por grupo + bono − efectivo recaudado`.
Positivo = a cobrar; negativo = se lo debés a la app. Las propinas van aparte (no se suman ni se restan).
**Total de la semana = deuda arrastrada + saldo de los turnos − seguro − cuota del crédito.**
- Si el total es positivo, es lo que cobrás y la semana siguiente **arranca en cero**.
- Si es negativo, es deuda con la app: **la semana siguiente arranca con esa deuda** y se va descontando con lo que hagas.

## Datos y copia de seguridad
**Para no perder nada al reinstalar:** activá la copia automática eligiendo una carpeta (mejor de Google Drive).
Al reinstalar, en Inicio tocá *Recuperar mis datos*, elegí la misma carpeta y vuelve todo en un paso.
Además la app guarda un archivo interno (`respaldo-interno.json`) que la copia de seguridad de Google de Android
recupera sola al reinstalar, si está activada en el teléfono (ver `res/xml/backup_rules.xml`).

Los datos viven en una base SQLite dentro del teléfono. Si se desinstala la app, Android borra esa base, así que
la app guarda **una copia en una carpeta que elegís** (en el teléfono o en Google Drive):
- *Más > Ajustes y datos > Activar copia automática*: elegís la carpeta una vez y la copia
  (`ControlRider-respaldo.json`) se actualiza sola unos segundos después de cada cambio y al salir de la app.
- *Guardar una copia en otro lugar*: copia manual con fecha.
- *Restaurar una copia*: al reinstalar, elegís el archivo y se recuperan todos los datos.
- Desde *Más > Ajustes y datos* también se exporta todo a CSV (para Excel; el CSV no se puede restaurar en la app).

## Nota sobre la firma
El APK se firma con la clave `app/controlrider.keystore` (incluida en el repositorio) para que las
actualizaciones se instalen sobre la versión anterior. Es una clave pensada para uso personal:
si el repositorio es público, cualquiera puede ver esa clave. Si querés más seguridad, hacé el repositorio
privado o generá tu propia clave.
