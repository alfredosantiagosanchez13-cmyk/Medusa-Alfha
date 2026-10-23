# Plan de Implementación: Escáner CameraX de Pases QR y Verificación en Firestore

## 1. Resumen Ejecutivo
Implementar un sistema de escaneo y verificación de códigos QR de alta velocidad utilizando **CameraX** (`Preview` + `ImageAnalysis`) y decodificación ZXing, integrado con **Cloud Firestore** para el control de accesos de visitantes. El flujo cumplirá con las preferencias seleccionadas:
1. **Confirmación táctica del guardia:** Visualización de tarjeta de previsualización con datos del pase antes de guardar en la base de datos.
2. **Alternancia automática de entrada/salida:** Si el folio escaneado ya se encuentra registrado con estado `DENTRO`, la interfaz conmuta automáticamente para ofrecer la opción de **«Marcar Salida»** (`SALIDA`). En caso contrario, ofrece **«Confirmar Ingreso»** (`DENTRO`).
3. **Notificación en tiempo real al residente:** Al confirmar el ingreso, se genera y persiste automáticamente una alerta en Firestore para los condóminos de la casa de destino.

---

## 2. Arquitectura de Componentes

### 2.1 Módulo de Cámara (CameraX)
- **`QrCodeImageAnalyzer` (`ImageAnalysis.Analyzer`):**
  - Procesamiento eficiente de frames con `PlanarYUVLuminanceSource` y `MultiFormatReader` configurado exclusivamente para `BarcodeFormat.QR_CODE`.
  - Throttle de captura (1.2s) para evitar lecturas duplicadas en ráfaga.
  - Cierre oportuno de `image.close()` para evitar memory leaks o congelamiento del pipeline de CameraX.
- **`QrScannerBottomSheet` (Jetpack Compose + `AndroidView`):**
  - Integración de `PreviewView` con ciclo de vida vinculado a `LocalLifecycleOwner`.
  - Visor táctico con marco ciberseguridad, animación de escaneo láser y control de linterna / flash.
  - Manejo de permisos en tiempo de ejecución (`Manifest.permission.CAMERA`) mediante `rememberLauncherForActivityResult`.

### 2.2 Capa de Datos y Modelos
- **`ParsedVisitorPass`:**
  - Decodificación y parsing estructurado de códigos QR en formato JSON o URL con campos: `folio`, `visitorName`, `destinationHouse`, `vehiclePlate`, `visitorType`, `timestamp`.
- **`VisitorRepository` & `VisitorViewModel`:**
  - Consulta en Firestore o caché en vivo para verificar el estado actual del folio (`DENTRO` vs `SALIDA` o nuevo).
  - Método `verifyAndResolvePassStatus(pass: ParsedVisitorPass)`: Detecta si ya está en sitio.
  - Método `confirmVisitorEntry(entry: VisitorEntry, isCheckOut: Boolean)`: Guarda en Firestore con `checkInTimestamp` o `checkOutTimestamp`.
  - Método `dispatchArrivalAlert(entry: VisitorEntry)`: Registra la alerta en `/condominiums/{condominiumId}/resident_alerts`.

### 2.3 Tarjeta de Previsualización y Confirmación
- **Tarjeta Táctica en Compose:**
  - Encabezado: Nombre del visitante y folio.
  - Destino: Casa / Calle del condominio (ej. *Casa 54 · Circuito Los Álamos*).
  - Estado dinámico:
    - Si está fuera: Botón verde **«Confirmar Ingreso»** con badge *Ingreso Autorizado*.
    - Si ya está dentro: Botón ámbar **«Marcar Salida»** con badge *Visita en Sitio detectada*.
  - Botón de cancelación / rechazar acceso.
  - Feedback háptico (`performHapticFeedback`) al detectar y al confirmar.

---

## 3. Plan de Pasos para la Construcción

1. **Revisión y Enriquecimiento de `ParsedVisitorPass.kt`:**
   - Asegurar compatibilidad para parsear tanto JSON como cadenas formateadas tipo `MEDUSA|FOL-1041|Sofia Villaseñor|Casa 54|UMC-341-K`.
2. **Actualización de `VisitorRepository.kt`:**
   - Añadir método de consulta rápida de folio: `checkVisitorCurrentStatus(condominiumId: String, folio: String): VisitorStatus?`.
   - Método atómico para registrar entrada o salida según corresponda.
3. **Evolución de `VisitorViewModel.kt`:**
   - Manejo del estado `isPassAlreadyInside: Boolean` al detectar el QR.
   - Acciones `confirmScannedEntry()` y `confirmScannedExit()`.
   - Integración automática con `ResidentAlertRepository` para disparar alerta en tiempo real al confirmar ingreso.
4. **Perfeccionamiento de `QrScannerBottomSheet.kt`:**
   - Conexión de la vista previa de CameraX con la tarjeta de confirmación de entrada/salida.
   - Indicador visual claro del tipo de acción a ejecutar (Entrada vs Salida).
5. **Pruebas Unitarias y de Integración:**
   - `QrPassVerificationUnitTest.kt`: Validación de parsing de QR, detección de estado previo (entrada vs salida) y generación de alerta.
   - Verificación de compilación limpia con `compile_applet` y ejecución de tests con `gradle testDebugUnitTest`.

---

## 4. Criterios de Aceptación
- La cámara inicializa fluidamente sin bloqueos de UI.
- Al enfocar un QR de visita, se extrae la información y se despliega la tarjeta de confirmación del guardia sin registrar en Firestore de forma apresurada.
- Si el visitante ya estaba marcado como `DENTRO`, la interfaz muestra la opción de registrar su salida.
- Al confirmar el ingreso, los datos se almacenan en Firestore y se crea la alerta para el residente.
- Compilación `BUILD SUCCESSFUL` en Gradle y AI Studio.
