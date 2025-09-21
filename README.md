# Block Rise (Zen)

## How to run
1. Open the project folder in Android Studio Hedgehog or newer.
2. Allow Gradle to sync and ensure the Android SDK 34 platform is installed.
3. Connect an emulator or device running Android 7.0 (API 24) or higher and press **Run**.

## Tuning
Key gameplay constants live in `Utils.kt`:
- `BOARD_COLS = 24`
- `BOARD_ROWS = 80`
- `RISE_INTERVAL_SEC = 6.0f`
- `GRAVITY_MS = 1200f`
- `LOCK_DELAY_MS = 500f`

Adjusting these values lets you iterate on board scale, rise cadence, and fall timing without touching the renderer.

## Future art pass
Placeholder assets ship under `app/src/main/assets/` (e.g. `textures/wood_cell.png`). Swap them with final wood textures and typography during the dedicated art polish phase to complete the intended warm, zen presentation.
