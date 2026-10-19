<div align="center">
  <img src="docs/icon.png" alt="App Icon" width="100" />
  <h1>RikkaHub Work</h1>

A fork of RikkaHub — a native Android LLM chat client that supports switching between different providers for conversations. 🤖💬

[简体中文](README.md) | English
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

## ✨ What This Fork Adds

### 🗂 Memory

- **Memory groups** — share a single set of memories across several assistants while keeping private and built-in global memory. Deleting a group moves its memories back to private scope instead of deleting them.
- **Pick-and-choose migration** — when copying an assistant or switching its memory scope, select individual memories and choose copy or move. Moving shared memories warns about the impact first.
- **Chat ⇄ group import** — a group conversation can import memories from any assistant's private scope, global memory or a memory group; an assistant can import from a group by "group name · conversation title". Copy is the default, and the source list is de-duplicated by real scope.

### 🛠 Tools & Approval

- **Three approval modes** — per assistant: **Ask every time**, **Ask for risky calls** (default), and **Allow all**. Risk is tiered L0–L3, and unregistered tools default to the highest tier.
- **Calendar delete** — `calendar_delete` removes events created by mistake; deletion always asks for confirmation.
- **Credential-isolated built-in browser** — when a page needs a login, a CAPTCHA or JavaScript rendering, the AI can request the browser, **you sign in yourself**, and the AI only reads content filtered on-device. See below.
- **Chart tool** — `chart_display` renders line, bar and scatter charts; read-only, no extra approval.

### 🌐 Built-in Browser (F013)

- **Eight fixed tools**: open URL, current URL, get text, list actions, click, type, scroll, and done. **No** arbitrary JavaScript, raw DOM, cookies, headless mode, screenshots or OCR.
- **Three layers of protection**: page text is filtered on-device before it reaches the AI (likely API keys, JWTs, long tokens, and Luhn-valid card numbers); payment, deletion, authorization, publishing, transfer, password and file controls are **hard-refused** and cannot be approved around; filtering is on by default and turning it off requires confirmation.
- Click and type use single-use element IDs; after the page changes, stale IDs are rejected.
- A collapsible **mini chat panel** at the bottom is bound to the originating conversation and supports streaming replies, tool approval, stop and error cards.
- **Mobile / desktop display modes**; switching reloads the same WebView without clearing login state.
- Group mode does not expose the browser.

### 👥 Group Mode (F012)

- Switch between **Chat / Group** at the top of the drawer; each mode keeps its own lists, drafts and settings.
- **Standalone member library**: name, avatar, model, prompt, streaming, tags and numeric parameters (temperature / top-p / context messages / max tokens), with long-press drag reordering.
- **Group configuration**: name, avatar, tags, member add/remove and priority, MCP, local tools, extensions (prompt injection / lorebooks / skills), group memory, custom requests, background (image / gradient / opacity), compression settings and a group-wide reasoning level.
- **Group generation**: sequential speakers; multiple conversations, full group duplication, deputy dispatcher, @mentions and two-way message quotes.
- **Per-group nicknames and avatars** — isolated per group, resolved live for existing bubbles without rewriting history.
- **Group chat compression** — manual compression, layered summaries, editable prompts and an automatic reminder that only spends tokens after you confirm.
- **Workspace ownership** — workspaces can be filtered by Chat / Group / Unassigned and assigned when created; groups use their own workspace.

### 🔌 Providers & Search

- **DeepSeek Web (free)** — drives conversations from a browser login session instead of an API key. Ships with a read-only tool allowlist, a serial request gate and configurable throttling; file writes, shell, code execution, skills and MCP are cut off to reduce account risk.
- **Search providers** added on top of upstream:
  - **SuiXiang** (`/v1/web_search`, flat ¥0.06 per search, failures are not charged)
  - **DeepSeek official search & fetch** (`/v1/tools/search` + `/v1/tools/fetch`, ¥0.005 per request, with long summaries written for models)
  - **AnySearch** (anonymous use supported; 1,000 free requests/day with a key, plus article extraction)
- **Thinking-mode reasoning fix** — when DeepSeek thinking mode continues after a tool call, historical `reasoning_content` is passed back automatically, fixing HTTP 400 interruptions.

### 🎨 UI

- **Per-provider loading animations** — each provider can use a brand-matched animation, a built-in preset, or a custom GIF / image. Built-ins cover OpenAI rotation, Claude starburst, Gemini sparkle, DeepSeek pulse and a generic breathe animation.
- **Liquid glass** — real refraction and dispersion on the chat input bar on Android 13+, with an automatic fallback to blurred glass on older versions or render failures, so it never crashes.
- **Branding & About page** — the app name is `RikkaHub Work`; the About page links only to this repository; sponsor, QQ / Discord entries and export watermarks are removed.
- **Group UI** — transparent group chat top bar, date-grouped and pinned group drawer, drag ordering for members and groups.

### 🔄 Updates & Stability

- **Fork-aware update check** — update checks read this repository's GitHub Releases only; when a new version is found the app opens the release page and you download and install the APK yourself.
- **Background hang fix** — generation holds a `PARTIAL_WAKE_LOCK` and has a 20-minute hard deadline checked every 5 seconds against a clock that includes sleep. Request logs use active time and report suspended time separately, so frozen time is no longer counted as network time.
- Timeouts and "interrupted by device sleep" both surface a clear error card, which is recorded before the job is cancelled.

## 📦 Inherited from Upstream

> The following comes from upstream and is kept as is.

- 🎨 Material You Design and 🌙 Dark mode
- 📦 Workspace: a proot-based Linux agent environment
- 🔄 Multiple AI Provider Support: custom API / URL / models (all OpenAI, Google, Anthropic compatible APIs)
- 🖼️ Multimodal input support (Image, Text Documentation, PDF, Docx)
- 🖥️ Web access for multi-platform use
- 🛠️ MCP support
- 📝 Markdown Rendering (with code highlighting, LaTeX formulas, tables, Mermaid)
- 🪾 Message Branching
- 🔍 Search capabilities (Exa, Tavily, Zhipu, LinkUp, Brave, Perplexity, etc.)
- 🧩 Prompt variables (model name, time, etc.)
- 🤳 QR code export and import for providers
- 🤖 Agent customization
- 🧠 ChatGPT-like memory feature
- 📝 AI Translation
- 🌐 Custom HTTP request headers and request bodies
- 💌 Silly Tavern character card import

## 🧭 Versions & Package Names

| Item | Value |
|---|---|
| Release package | `me.rerere.rikkahub.plus` |
| Debug package | `me.rerere.rikkahub.debug` |
| Version format | `{upstream version}-work.{n}` |
| Current release | `2.5.6-work.3` (versionCode 196) |
| Next in the working tree | `2.5.6-work.4` (versionCode 197, not released yet) |
| License | AGPL-3.0 |

## 🛠 Build

Built with [Android Studio](https://developer.android.com/studio). Technology stack:

- [Kotlin](https://kotlinlang.org/) (Development language)
- [Koin](https://insert-koin.io/) (Dependency Injection)
- [Jetpack Compose](https://developer.android.com/jetpack/compose) (UI framework)
- [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) (Preference data storage)
- [Room](https://developer.android.com/training/data-storage/room) (Database)
- [Coil](https://coil-kt.github.io/coil/) (Image loading)
- [Material You](https://m3.material.io/) (UI design)
- [Navigation 3](https://developer.android.com/guide/navigation/navigation-3) (Navigation)
- [OkHttp](https://square.github.io/okhttp/) (HTTP client)
- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) (JSON serialization)

> [!TIP]
> You need a `google-services.json` file in the `app` folder to build the app.

> [!IMPORTANT]
> This fork does not accept upstream translation PRs; only English, 简体中文 and 繁體中文 are kept.

## 🙏 Credits

All credit for the foundation of this project goes to [RikkaHub](https://github.com/rikkahub/rikkahub) and its contributors. This fork would not exist without their work.

Third-party components:

- [AndroidLiquidGlassView](https://github.com/QmDeve/AndroidLiquidGlassView) (MIT, © Donny Yale / QmDeve)
- [Mozilla Readability](https://github.com/mozilla/readability) (Apache-2.0, article extraction)
- [Haze](https://github.com/chrisbanes/haze) (Apache-2.0, glass blur fallback)

If you like this project, please give **upstream** a star too ⭐

## 📄 License

This fork is licensed under **AGPL-3.0**. Original copyright belongs to the RikkaHub authors.
See [LICENSE](./LICENSE) for the full text.

