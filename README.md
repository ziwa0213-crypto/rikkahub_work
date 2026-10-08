<div align="center">
  <img src="docs/icon.png" alt="App Icon" width="100" />
  <h1>RikkaHub Work</h1>

A fork of RikkaHub — a native Android LLM chat client that supports switching between different providers for conversations. 🤖💬

[简体中文](README_ZH_CN.md) | English
</div>

<div align="center">
  <img src="docs/img/chat.png" alt="Chat Interface" width="150" />
  <img src="docs/img/desktop.png" alt="Models Picker" width="450" />
</div>

## 📌 About This Fork

RikkaHub Work is a **personal fork** of [RikkaHub](https://github.com/rikkahub/rikkahub), kept mainly for my own use and shared as is. It follows upstream releases and adds a set of features and fixes on top of them.

- **Not an official RikkaHub release.** Everything here is built on upstream work. Issues with this fork should be reported **in this repository**, not upstream.
- **Versioning**: `{upstream version}-work.{n}`, e.g. `2.5.6-work.3`. `n` resets to `1` whenever a new upstream version is followed.
- **Languages**: English, 简体中文, 繁體中文 only.
- Builds are distributed through this repository's GitHub Releases.

## 📥 Install & Upgrade

Download the APK from this repository's **Releases** page.

- **Package name**: `me.rerere.rikkahub.plus` — different from upstream's `me.rerere.rikkahub`, so **this fork and the official RikkaHub can be installed side by side**.
- **Upgrading this fork**: just install over the existing app. The signature must match and the version code must not go backwards, or Android will refuse the install.
- **Coming from another fork, or from the official build**: uninstall first. The signing key differs, so the install would fail otherwise.
- Three builds are attached: `arm64-v8a`, `x86_64`, and `universal`. Most phones want `arm64-v8a`.

> [!WARNING]
> Back up your app data before upgrading. Settings, conversations and API keys live in the app's private storage and are gone if you uninstall.

## 🔀 What's Different from Upstream

### 🗂 Memory

- **Memory groups** — share a single set of memories across several assistants, while keeping private and built-in global memory.
- **Pick-and-choose memory migration** — when copying an assistant or switching memory scope, select individual memories and copy or move them.

### 🛠 Tools & Approval

- **Tool approval modes** — per-assistant control over whether tool calls always ask, only ask for risky ones, or run freely.
- **Calendar delete tool** — delete calendar events created by mistake.

### 🤖 Providers

- **DeepSeek Web provider (free)** — a built-in provider driven by a browser login session instead of an API key, with read-only tools and three layers of guardrails.
- **SuiXiang search provider** — an additional built-in web search provider.
- **DeepSeek thinking-mode fix** — always pass historical `reasoning_content` back when tools are used, fixing HTTP 400 errors.

### 🎨 UI

- **Per-provider loading animations** — save a loading animation per provider, auto-matched by brand, with support for custom GIF / image.
- **Liquid glass effect** — refraction and dispersion on the chat input bar (Android 13+), with an automatic fallback on older versions.
- **Group mode** *(in development)* — switch between chat and group mode from the drawer, with a standalone member library and group configuration.

### ⚙️ Miscellaneous

- **Fork-aware update check** — update checks read this fork's GitHub Releases only; you download and install the APK yourself.
- **About page & branding cleanup** — the app name is `RikkaHub Work`, sponsor / promo entries are removed, and exported content is watermark-free.

## ✨ Features

> Inherited from upstream. See [What's Different from Upstream](#-whats-different-from-upstream) for what this fork adds.

- 🎨 Material You Design and 🌙 Dark mode
- 📦 Workspace: a proot-based Linux agent environment
- 🔄 Multiple AI Provider Support: custom API / URL / models (all OpenAI, Google, Anthropic compatible api)
- 🖼️ Multimodal input support (Image, Text Documentation, PDF, Docx)
- 🖥️ Web access for multi-platform use
- 🛠️ MCP support
- 📝 Markdown Rendering (with code highlighting, Latex formulas, tables, Mermaid)
- 🪾 Message Branching
- 🔍 Search capabilities (Exa, Tavily, Zhipu, LinkUp, Brave, Perplexity, etc.)
- 🧩 Prompt variables (model name, time, etc.)
- 🤳 QR code export and import for providers
- 🤖 Agent customization
- 🧠 ChatGPT-like memory feature
- 📝 AI Translation
- 🌐 Custom HTTP request headers and request bodies
- 💌 Silly Tavern character card import

## 🛠 Contributing

This project is developed using [Android Studio](https://developer.android.com/studio).

Technology stack documentation:

- [Kotlin](https://kotlinlang.org/) (Development language)
- [Koin](https://insert-koin.io/) (Dependency Injection)
- [Jetpack Compose](https://developer.android.com/jetpack/compose) (UI framework)
- [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) (Preference data
  storage)
- [Room](https://developer.android.com/training/data-storage/room) (Database)
- [Coil](https://coil-kt.github.io/coil/) (Image loading)
- [Material You](https://m3.material.io/) (UI design)
- [Navigation 3](https://developer.android.com/guide/navigation/navigation-3) (Navigation)
- [Okhttp](https://square.github.io/okhttp/) (HTTP client)
- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) (JSON serialization)

> [!TIP]
> You need a `google-services.json` file at `app` folder to build the app.

> [!IMPORTANT]
> The following PRs will be rejected:
> 1. Translation related changes, such as adding new languages or updating existing translations
> 2. Large-scale refactoring and changes generated by AI

## 🙏 Credits

All credit for the foundation of this project goes to [RikkaHub](https://github.com/rikkahub/rikkahub) and its contributors. This fork would not exist without their work.

If you like this project, please give **upstream** a star too ⭐

## 📄 License

This project is licensed under the [GNU Affero General Public License v3.0](LICENSE) (AGPL-3.0), inherited from RikkaHub.