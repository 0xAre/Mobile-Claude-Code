# Claude Terminal — Mobile

A mobile terminal emulator for Android with Claude AI integration. Built with Kotlin + Jetpack Compose.

## Features

- **Terminal Emulator** — run shell commands (ls, cat, grep, find, mkdir, etc.)
- **ANSI Color Support** — full color terminal output with 16/256 color palette
- **Claude AI Panel** — slide-in chat panel, Claude gets your recent terminal output as context
- **Command History** — navigate previous commands with ↑/↓ buttons
- **Settings** — configure API key, model selection, and font size

## Setup

1. Get an Anthropic API key from [console.anthropic.com](https://console.anthropic.com)
2. Open the app → tap ⚙️ Settings → paste your API key
3. Tap `✦` to open the Claude panel

## Build

```bash
cd claude-code-mobile
./gradlew assembleDebug
```

Requires Android Studio Hedgehog or later, API 26+.

## Architecture

```
MainActivity → NavGraph
  ├── MainScreen
  │   ├── TerminalOutput (LazyColumn + AnsiParser)
  │   ├── ClaudePanel (slide-in chat with context injection)
  │   └── CommandInputBar (BasicTextField + history)
  └── SettingsScreen
      └── DataStore (API key, model, font size)

TerminalManager   → ProcessBuilder-based command execution
ClaudeApiService  → Anthropic Messages API (OkHttp)
AnsiParser        → VT100 color code → AnnotatedString
```

## Built by [0xAre](https://github.com/0xAre)
