# Miku_AI - Traductor en Pantalla en Tiempo Real (Android 14+ / Kotlin)

<p align="center">
  <img src="icono_miku.jpg" width="120" height="120" alt="Miku_AI Icon" style="border-radius: 50%;" />
</p>

**Miku_AI** es una aplicación Android de alto rendimiento desarrollada en **Kotlin** para Android 14+ (Target SDK 34), diseñada específicamente para la traducción fluida y en tiempo real de mangas, manhwas, cómics y aplicaciones. Utiliza reconocimiento óptico de caracteres en el dispositivo con **Google ML Kit OCR** y traducción neuronal con la API de **DeepL**, superponiendo los bocadillos traducidos directamente sobre el texto original con coordenadas exactas 1:1.

---

## 🌟 Características Principales

### 1. Burbuja Flotante Animada de Miku & Menú Desacoplado
- **Burbuja Flotante Fija (52dp)**: Muestra un avatar GIF animado con recorte circular por aceleración de hardware (`ViewOutlineProvider`) y un indicador de estado con color activo (verde), en pausa (amarillo) o detenido (rojo).
- **Menú Emergente Anclado**: Al tocar la burbuja, se despliega un menú lateral con scroll horizontal sin alterar la posición ni causar parpadeo en la burbuja principal:
  - 🟣 **TRADUCIR**: Captura y traduce la pantalla actual en ese instante.
  - ✂️ **RECORTAR**: Activa la herramienta de recorte de pantalla (Snip Tool).
  - 🔍 **LUPA**: Despliega la burbuja de lupa para traducción por punto/arrastre.
  - ⚪ **LIMPIAR**: Oculta los bocadillos traducidos para interactuar con la pantalla limpia.
  - 🟢 **MANUAL / AUTO**: Alterna entre traducción bajo demanda o escaneo periódico continuo.
  - 🔴 **✕**: Detiene el servicio y cierra los overlays.

### 2. Herramienta de Selección de Área (Snip Tool)
- Permite arrastrar libremente sobre la pantalla para definir un área rectangular de recorte (`Rect`).
- Malla de 4 cuadrantes que oscurece el fondo mientras deja visible el área seleccionada con un borde cian neón y contador dinámico de resolución (`px`).
- Procesa únicamente el sub-bitmap recortado con ML Kit y DeepL, acelerando el tiempo de respuesta y ahorrando cuota de API.
- Al confirmar o cancelar, devuelve de inmediato el control táctil a la aplicación subyacente.

### 3. Traducción por Punto / Lupa (Magnifier Bubble)
- Burbuja secundaria arrastrable (48dp) con retícula de muestreo.
- Al soltarla (`ACTION_UP`), calcula con precisión matemática el área de **200x100 dp** convertida a píxeles físicos del frame (`DisplayMetrics.density`).
- Ejecuta OCR y traducción localizada instantánea para viñetas individuales o párrafos específicos.

### 4. Síntesis de Voz Nativa (TTS) & Copiado al Portapapeles
- **Pulsación Larga en Bocadillos**: Al mantener presionado cualquier bocadillo traducido en pantalla, se reproduce inmediatamente en voz alta con síntesis de voz (TTS).
- **Modal de Detalle al Tocar**: Abre una tarjeta modal con:
  - Texto traducido en tipografía grande y legible.
  - 🔊 Botón para escuchar la traducción en el idioma seleccionado.
  - 🔊 Botón para escuchar la pronunciación del texto original (ideal para aprender inglés, japonés o coreano).
  - 📋 Botones independientes para copiar la traducción o el texto original al portapapeles.
  - Detección de paquetes de voz faltantes (`LANG_MISSING_DATA` / `LANG_NOT_SUPPORTED`) con notificaciones al usuario.

### 5. Especialización para Manga & Manhwa (`MangaBubbleClusterer.kt`)
- Agrupa espacialmente las líneas verticales y fragmentadas de un mismo bocadillo en un solo bloque coherente.
- Repara automáticamente palabras cortadas con guiones de cambio de línea (`"exam-"` + `"ple"` $\rightarrow$ `"example"`).
- Superpone globos de cómic (`MangaBubbleView.kt`) con fondo blanco opaco, contorno entintado clásico y autoajuste tipográfico (`setAutoSizeTextTypeUniformWithConfiguration`).
- Flags `FLAG_LAYOUT_IN_SCREEN or FLAG_LAYOUT_NO_LIMITS` para alineación física 1:1 eliminando desfases de la barra de estado.

### 6. Caché Persistente en Disco & Optimización de Red
- `TranslationCache.kt`: Almacena en disco (`SharedPreferences`) todos los textos traducidos para no volver a consultar nunca la API ante textos ya vistos.
- Batching de textos con Retrofit y OkHttp con reintentos exponenciales ante código HTTP 429.

---

## 🛠️ Estructura del Proyecto

```
app/src/main/java/com/antigravity/translator/
├── data/
│   ├── api/
│   │   ├── DeepLApiService.kt
│   │   ├── DeepLModels.kt
│   │   └── RetrofitClient.kt
│   ├── pref/
│   │   └── AppPreferences.kt
│   └── repository/
│       ├── DeepLRepository.kt
│       └── TranslationCache.kt
├── domain/
│   └── model/
│       ├── DetectedTextBlock.kt
│       ├── ServiceState.kt
│       └── TranslatedBlock.kt
├── engine/
│   ├── capture/
│   │   └── ScreenCaptureEngine.kt
│   └── ocr/
│       ├── MangaBubbleClusterer.kt
│       └── OcrEngine.kt
├── overlay/
│   ├── FloatingBubbleView.kt
│   ├── FloatingMenuView.kt
│   ├── MagnifierBubbleView.kt
│   ├── MangaBubbleView.kt
│   ├── OverlayWindowManager.kt
│   ├── RegionSelectionOverlayView.kt
│   ├── TranslationCanvasView.kt
│   └── TranslationDetailDialog.kt
├── service/
│   └── ScreenCaptureService.kt
├── tts/
│   └── TtsManager.kt
├── ui/
│   ├── MainActivity.kt
│   ├── MainViewModel.kt
│   └── theme/
└── TranslatorApplication.kt
```

---

## 🚀 Compilación y Ejecución

1. Clonar el repositorio:
   ```bash
   git clone https://github.com/Diego-capu/Traductor.git
   cd Traductor
   ```
2. Compilar el APK debug:
   ```bash
   ./gradlew assembleDebug
   ```
3. El archivo APK resultante se generará en:
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```
