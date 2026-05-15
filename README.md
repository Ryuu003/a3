# FocusLock Android

App de bloqueo de apps para Android. Elige qué apps se bloquean (lista negra) 
o cuáles son las únicas que puedes abrir (lista blanca).

## ¿Cómo funciona?

Usa el **Servicio de Accesibilidad** de Android para detectar qué app se abre
en tiempo real. Cuando detecta una app bloqueada, muestra una pantalla de bloqueo
de pantalla completa en lugar de dejar entrar.

**Modos:**
- **Lista Negra**: marcas las apps que quieres bloquear (Instagram, TikTok, etc.)
- **Lista Blanca**: solo puedes abrir las apps que marcas (ej: Teléfono, Mensajes, WhatsApp)

## Cómo abrir y compilar con Android Studio

### Requisitos
- Android Studio (Hedgehog 2023.1.1 o más reciente)
- Java 11 o superior
- SDK Android API 23+

### Pasos

1. Abre Android Studio
2. File → Open → selecciona la carpeta `FocusLock-Android`
3. Espera a que Gradle sincronice (primera vez tarda ~2-3 min)
4. Conecta tu celular con USB y activa "Depuración USB" en las opciones de desarrollador
5. Run → Run 'app' (botón ▶ verde)

### Primera vez en el celular

1. Se instala la app FocusLock
2. Al abrirla, toca **"ACTIVAR SERVICIO DE ACCESIBILIDAD"**
3. En Configuración de Android → busca "FocusLock" → actívalo
4. Vuelve a FocusLock
5. Elige las apps a bloquear o las que quieres permitir
6. Activa el switch principal

## Permisos requeridos

- `QUERY_ALL_PACKAGES` — para ver las apps instaladas
- `BIND_ACCESSIBILITY_SERVICE` — para detectar qué app está en primer plano
- `RECEIVE_BOOT_COMPLETED` — para recordar el estado después de reiniciar

## Notas

- El Servicio de Accesibilidad debe estar activo para que funcione
- Algunas marcas (Xiaomi, Samsung) pueden desactivar servicios en segundo plano
  — ve a Batería → sin restricciones para FocusLock
- Las apps del sistema (launcher, configuración, llamadas) nunca se bloquean
- El bloqueo persiste hasta que lo desactives desde FocusLock

## Estructura del proyecto

```
app/src/main/java/com/focuslock/
├── MainActivity.kt       — UI principal, lista de apps
├── AppBlockerService.kt  — Servicio de Accesibilidad (el núcleo)
├── BlockedActivity.kt    — Pantalla que se muestra al bloquear
├── AppAdapter.kt         — Adaptador RecyclerView
├── AppItem.kt            — Modelo de datos
├── PrefsManager.kt       — Gestión de preferencias
└── BootReceiver.kt       — Receptor de arranque
```
