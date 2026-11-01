<div align="center">
  <img src="docs/icon.png" alt="App Icon" width="100" />
  <h1>RikkaHub Work</h1>

A fork of RikkaHub — a native Android LLM chat client that supports switching between different providers for conversations. 🤖💬

[简体中文](README.md) | English
</div>

> ✅ **Current release: [`3.1.1-work.1`](https://github.com/ziwa0213-crypto/rikkahub_work/releases/latest)** (versionCode 200, 2026-10-31) — upstream 3.1.1 has been merged.
> ⚠️ **Back up before upgrading.** The database goes from 32 to 33 in this build and cannot be rolled back. See "Install & Upgrade" below.

<div align="center">
  <img src="docs/img/chat.png" alt="Chat Interface" width="150" />
  <img src="docs/img/desktop.png" alt="Models Picker" width="450" />
</div>

## 📌 About This Fork

RikkaHub Work is a **personal fork** of [RikkaHub](https://github.com/rikkahub/rikkahub), kept mainly for my own use and shared as is. It follows upstream releases and adds a set of features and fixes on top of them.

- **Not an official RikkaHub release.** Everything here is built on upstream work. Issues with this fork should be reported **in this repository**, not upstream.
- **Versioning**: `{upstream version}-work.{n}`, e.g. `3.1.1-work.1`. `n` resets to `1` whenever a new upstream version is followed.
- **Languages**: English, 简体中文, 繁體中文 only.
- Builds are distributed through this repository's GitHub Releases.

## 📥 Install & Upgrade

Download the APK from this repository's **Releases** page.

- **Package name**: `me.rerere.rikkahub.plus` — different from upstream's `me.rerere.rikkahub`, so **this fork and the official RikkaHub can be installed side by side**.
- **Upgrading this fork**: just install over the existing app. The signature must match and the version code must not go backwards, or Android will refuse the install.
- **Coming from another fork, or from the official build**: uninstall first. The signing key differs, so the install would fail otherwise.
- Three builds are attached: `arm64-v8a`, `x86_64`, and `universal`. Most phones want `arm64-v8a`. The APK is about 16 MB (`universal` about 19 MB).

> [!WARNING]
> **The database only goes up.** This build moves the database from 32 to 33; installing any older version afterwards fails on the Room downgrade and the only way out is clearing app data.
> **Make a full backup first (Settings → Backup, include the database)** and keep a copy of the APK you are running now. Settings, conversations and API keys live in the app's private storage and are gone if you uninstall.

## ✨ What This Fork Adds

### 🗂 Memory

- **Memory groups** — share a single set of memories across several assistants while keeping private and built-in global memory. Deleting a group moves its memories back to private scope instead of deleting them.
- **Pick-and-choose migration** — when copying an assistant or switching its memory scope, select individual memories and choose copy or move. Moving shared memories warns about the impact first.
- **Chat ⇄ group import** — a group conversation can import memories from any assistant's private scope, global memory or a memory group; an assistant can import from a group by "group name · conversation title". Copy is the default, and the source list is de-duplicated by real scope.

### 🛠 Tools & Approval

- **Three approval modes** — per assistant: **Ask every time**, **Ask for risky calls** (default), and **Allow all**. Risk is tiered L0–L3, and unregistered tools default to the highest tier. In group mode the tier is set in the group configuration.
- **Calendar delete** — `calendar_delete` removes events created by mistake; deletion always asks for confirmation.
- **Credential-isolated built-in browser** — when a page needs a login, a CAPTCHA or JavaScript rendering, the AI can request the browser, **you sign in yourself**, and the AI only reads content filtered on-device. See below.

### 🌐 Built-in Browser (F013)

- **Eight fixed tools**: open URL, current URL, get text, list actions, click, type, scroll, and done. **No** arbitrary JavaScript, raw DOM, cookies, headless mode, screenshots or OCR.
- **Three layers of protection**: page text is filtered on-device before it reaches the AI (likely API keys, JWTs, long tokens, and Luhn-valid card numbers); payment, deletion, authorization, publishing, transfer, password and file controls are **hard-refused** and cannot be approved around; filtering is on by default and turning it off requires confirmation.
- Click and type use single-use element IDs; after the page changes, stale IDs are rejected.
- A collapsible **mini chat panel** at the bottom is bound to the originating conversation and supports streaming replies, tool approval, stop and error cards.
- **Mobile / desktop display modes**; switching reloads the same WebView without clearing login state.
- **Standalone entry** — Settings → Model & Services → Built-in browser opens at `about:blank` so you can sign in first; browsing data (cookies, local storage, cache) can be cleared in one tap.
- **Per-tool switches and timeouts** — each of the eight tools can be enabled or disabled; the sensitive-content filter is on by default (turning it off needs a second confirmation); mobile / desktop mode; per-tool timeout (10 s – 10 min) and per-task timeout (1 – 60 min).
- Group mode does not expose the browser.

### 👥 Group Mode (F012)

- Switch between **Chat / Group** at the top of the drawer; each mode keeps its own lists, drafts and settings, and **the last mode survives a restart**.
- **Standalone member library**: name, avatar, model, prompt, streaming, tags and numeric parameters (temperature / top-p / context messages / max tokens), with long-press drag reordering.
- **Group configuration**: name, avatar, tags, member add/remove and priority, MCP, local tools, extensions (prompt injection / lorebooks / skills), group memory, custom requests, background (image / gradient / opacity), compression settings and a group-wide reasoning level.
- **Group generation**: sequential speakers; multiple conversations, full group duplication, deputy dispatcher, @mentions and two-way message quotes.
- **Per-group nicknames and avatars** — isolated per group, resolved live for existing bubbles without rewriting history.
- **Group chat compression** — manual compression, layered summaries, editable prompts and an automatic reminder that only spends tokens after you confirm.
- **Workspace ownership** — workspaces are filtered by Chat / Group / Unassigned and created under the current mode; the same filter applies when opening workspace management from the file picker or the extensions page.

### 🔌 Providers & Search

- **DeepSeek Web (free)** — drives conversations from a browser login session instead of an API key. Ships with a read-only tool allowlist, a serial request gate and configurable throttling; file writes, shell, code execution, skills and MCP are cut off to reduce account risk.
  - **Manual login**: paste DevTools' `Copy as cURL` or a header block and `authorization`, `cookie` and fingerprint headers are parsed out automatically — no need to tell Token from Cookie. The original single-field Token / Cookie inputs remain under an "Advanced" section.
  - Pasted content stays in page memory only; it is never written to settings and credentials are never logged.
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
- **Consistent settings rows** — pages written by this fork (browser, DeepSeek Web, group config and others) now carry leading icons, drop duplicated section titles, and use the standard card switch row.

### 🔄 Updates & Stability

- **Fork-aware update check** — update checks read this repository's GitHub Releases only; when a new version is found the app opens the release page and you download and install the APK yourself.
- **Background hang fix** — generation holds a `PARTIAL_WAKE_LOCK` and has a 20-minute hard deadline checked every 5 seconds. Request logs use active time and report suspended time separately, so frozen time is no longer counted as network time.
- Timeouts and "interrupted by device sleep" both surface a clear error card, which is recorded before the job is cancelled.
- **Startup race fix** — fixes "I left the app in Group mode and it came back in Chat mode": settings and the placeholder conversation both load asynchronously, and the old code rendered from defaults on the first frame and wrote that back over your real choice.

## ⚠️ Known Limitations

| Item | Notes |
|---|---|
| DeepSeek Web login page may be blank | Opening the login page in the in-app WebView can render blank (user-agent / fingerprint level, not diagnosed). A diagnostic hint is shown; sign in with a system browser or Via and import via "paste cURL" instead |
| Browser desktop mode is display-only | Same engine, so `navigator.platform` and friends can still tell. A page that already redirected to its mobile site cannot be rescued |
| Liquid glass needs Android 13+ | Older versions fall back to blurred glass; no error, and the install floor is unchanged |
| Database only goes up | Currently 33; older builds cannot read the data, so clearing app data is the only way back |

## 📦 Inherited from Upstream

> The following comes from upstream and is kept as is.

- 🎨 Material You Design and 🌙 Dark mode
- 🧠 Two memory tracks: itemised memories plus file-based memory
- 🎬 Media generation: images and video
- 📦 Workspace: a proot-based local Linux agent environment, or a remote machine over **SSH**; built-in **terminal** page
- 🪟 Background generation floating window (switch away and pick the conversation back up)
- 🔄 Multiple AI Provider Support: custom API / URL / models (all OpenAI, Google, Anthropic compatible APIs)
- 🖼️ Multimodal input support (Image, Text Documentation, PDF, Docx)
- 🖥️ Web access for multi-platform use
- 🛠️ MCP support
- 📝 Markdown Rendering (with code highlighting, LaTeX formulas, tables, Mermaid)
- 📊 Chart tool `chart_display` (line / bar / scatter, read-only, no extra approval)
- 🪾 Message Branching
- 🔍 Search capabilities (Exa, Tavily, Zhipu, LinkUp, Brave, Perplexity, etc.)
- 🧩 Prompt variables (model name, time, etc.)
- 🤳 QR code export and import for providers
- 🤖 Agent customization
- 📝 AI Translation
- 🌐 Custom HTTP request headers and request bodies
- 💌 Silly Tavern character card import
- ✂️ Image cropping, a stepper for context compression, tool call duration display

## 🧭 Versions & Package Names

| Item | Value |
|---|---|
| Release package | `me.rerere.rikkahub.plus` |
| Debug package | `me.rerere.rikkahub.debug` |
| Version format | `{upstream version}-work.{n}` |
| Current release | `3.1.1-work.1` (versionCode 200, 2026-10-31) |
| Database version | 33 (only goes up) |
| License | AGPL-3.0 |

> Development progress, stage breakdown and root causes are in [DEVELOPMENT.md](./DEVELOPMENT.md).

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

Release task: upstream split distribution into two flavors, so the GitHub build is **`assembleGithubRelease`** (this fork only builds `github`).

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
- [Termux terminal-emulator](https://github.com/termux/termux-app) (Apache-2.0, terminal core)

If you like this project, please give **upstream** a star too ⭐

## 📄 License

This fork is licensed under **AGPL-3.0**. Original copyright belongs to the RikkaHub authors.
See [LICENSE](./LICENSE) for the full text.
