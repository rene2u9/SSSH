# VibeSSH

A modern SSH client for Android with a full-featured terminal emulator.

## Features

- **SSH Connections** - Connect to remote servers with password or key-based authentication
- **Terminal Emulator** - Full VT100/ANSI escape sequence support with 256-color palette
- **Scrollback History** - Configurable scrollback buffer (up to 10,000 lines)
- **Multiple Sessions** - Run multiple SSH sessions simultaneously with background support
- **Themes** - 6 built-in terminal themes (Default, Dracula, Monokai, Solarized Dark, Nord, Gruvbox)
- **Extra Keys** - Convenient shortcut keys for TAB, CTRL, ESC, arrows, and more
- **Volume Key Bindings** - Customize volume buttons for terminal input

## Screenshots

*Coming soon*

## Requirements

- Android 8.0 (API 26) or higher

## Building

```bash
# Clone the repository
git clone https://github.com/yourusername/VibeSSH.git
cd VibeSSH

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease
```

## Configuration

### Terminal Settings

- **Theme** - Choose from 6 color schemes
- **Font Size** - Adjustable from 8sp to 32sp
- **Scrollback Lines** - 500, 1000, 2000, 5000, or 10000 lines
- **Extra Keys** - Toggle the shortcut key bar

### Volume Key Actions

Map volume buttons to:
- Arrow keys (Up/Down)
- Tab / Enter
- Ctrl / Alt / Escape
- None (default volume behavior)

## Architecture

- **Kotlin** - 100% Kotlin codebase
- **Material Design 3** - Modern Android UI components
- **JSch** - SSH protocol implementation
- **Custom Terminal Emulator** - Lightweight VT100/ANSI terminal with UTF-8 support

## License

MIT License

## Contributing

Contributions are welcome! Please open an issue or submit a pull request.
