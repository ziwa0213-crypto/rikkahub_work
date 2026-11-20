<div align="center">
  <img src="docs/icon.png" alt="App icon" width="100" />
  <h1>RikkaHub Work</h1>

An AI platform built on RikkaHub: getting work done, role-play, multi-agent collaboration and automation — four modes, one job each.

English | [简体中文](README.md)
</div>

> ✅ **Current release: [`3.2.0-work.3`](https://github.com/ziwa0213-crypto/rikkahub_work/releases/latest)** (versionCode 206, 2026-11-13) — silent-heartbeat detection, video stripped from compression, localized error messages.
> 🔧 **Ready for release: `3.3.0-work.1` / 207** — upstream 3.3.0, workspace previews, DeepSeek server-side tools and client context are implemented; three Release variants are built. Progress and pending checks: [DEVELOPMENT.md](./DEVELOPMENT.md).
> ⚠️ **Back up before upgrading.** The published release uses database **33**; the local `3.3.0-work.1` build uses **34**. Downgrades are unsupported. See "Install & Upgrade" below.

<div align="center">
  <img src="docs/img/chat.png" alt="Chat" width="150" />
  <img src="docs/img/desktop.png" alt="Model picker" width="450" />
</div>

## 📌 What this is

Upstream RikkaHub is a carefully built chat client. This fork arranges it differently: work with AI is split into four modes, each with its own lists, settings, memory and workspace.

| Mode | In one line |
|---|---|
| **Work** | Get things done with AI: tools, files, workspaces, search |
| **Characters** | Spend time with AI characters: cards, personas, long-term memory (planned) |
| **Groups** | Several AIs working together: members, speaking order, a deputy |
| **Pulse** | AI running on its own: heartbeats, scheduled tasks, watchers |

"Chat" became "Work" because it stopped being just chat a while ago: it reads and writes files, runs commands, browses the web, calls tools, talks to MCP servers, and opens workspaces. Keeping the name "chat" understates what it does.

**Nothing from upstream was removed.** Multi-provider support and MCP are still there, and so are skills and workspaces; the full list is below. All of it is available inside the four modes, just placed where it belongs. Each upstream release is merged in rather than reimplemented.

**This is not an official RikkaHub build.** Everything here rests on upstream work; please file issues about this fork on this repository, not upstream.

- **Versioning**: `{upstream}-work.{n}`, e.g. `3.2.0-work.3`; `n` resets to 1 whenever a new upstream is merged
- **Languages**: English, Simplified Chinese, Traditional Chinese only
- **Distribution**: builds ship through this repository's GitHub Releases

---

## 🧭 The four modes

### 01 Work — getting things done

One-to-one. Call tools, read and write files, run commands, search the web — or just talk.

- **Three approval levels** — saved per assistant: **ask every time**, **ask only for risk** (the default), **allow everything**. Tools are graded L0–L3, and unregistered tools default to the highest risk.
- **Workspaces** — a local Linux environment via proot, or a remote machine over SSH; files live in `/workspace`. Markdown switches between rendered and source views. HTML / SVG resources stay within the document directory with external network access blocked; HTML scripts are off by default. SSH does not support local linked-resource previews.
- **Memory** — per-item memory and file-based memory coexist; several assistants can share one memory group while keeping private and global memory. Copying an assistant or moving memory between scopes lets you tick exactly which entries travel, and choose copy or move.
- **Built-in browser (credential isolation)** — when a page needs a login, a CAPTCHA or JavaScript, the AI asks for the browser, **you sign in**, and the AI only reads content filtered on-device. Eight fixed tools; no arbitrary JavaScript, raw DOM, cookies, headless mode, screenshots or OCR. Payment, deletion, authorization, publishing, transfer, password and file controls are **hard-refused** and cannot be approved around.
- **Search providers** — a dozen from upstream, plus **Suixiang** (¥0.06/request, no charge on failure), **DeepSeek official search & fetch** (¥0.005/request, with long summaries meant for models), and **AnySearch** (anonymous available; 1,000 free requests a day with a key).
- **DeepSeek web (free)** — drives conversations with a browser login instead of an API key, using a read-only tool whitelist and request pacing by default. Custom capabilities require explicit opt-in and risk confirmation. Login credentials can be imported by pasting a cURL, so you no longer have to separate token from cookie.

### 02 Characters — spending time with AI characters (planned)

This mode is not implemented yet; below is the direction.

Role-play currently means building characters as assistants and mixing them into the work list alongside assistants that do actual work. This mode separates the two.

- **Characters are their own entity** — they do not occupy the assistant list and do not reuse assistant settings; the two can call the same tools, but configuration, memory and conversations stay apart
- **Their own conversation list**; existing assistant conversations can be migrated by ticking them, choosing copy or move per item
- **SillyTavern card import** — PNG / JSON with field mapping, example dialogue, lorebooks, regex scripts and a macro engine
- **Memory becomes file-based**, with optional read-only reference to selected memory from Work
- Against the usual "assistant voice" in role-play — lines like *"I've got you"* or *"Let me be clear first"* that perform a helpful assistant instead of a character — several layers are applied: prompt, example dialogue, depth injection, and regex cleanup

### 03 Groups — several AIs working together

The second mode in the drawer. A few members at one table, speaking in order.

- **A separate member library**: name, avatar, model, prompt, streaming, tags, numeric parameters (temperature / top-p / context limit / max tokens), with long-press reordering
- **Group settings**: members and priority, MCP, local tools, extensions (prompt injection / lorebooks / skills), group memory, custom headers and bodies, background, compression, group-level reasoning level
- **Generation**: strictly sequential turns, each member seeing what came before; multiple conversations per group, full group duplication, a deputy that dispatches, @ mentions, two-way message quoting
- **Per-group nicknames and avatars**, resolved live in history without rewriting it
- **Group compression**: manual compression, layered summaries and editable prompts remain available; automatic compression uses the global switch and threshold.

### 04 Pulse — AI running on its own

The third mode in the drawer. Unlike the other two, it does not wait for you: it runs on a clock and only shows up when something happened.

- **Heartbeat** — a self-check every 30 minutes by default: memory, to-dos, watchers. Quiet when nothing changed.
- **Scheduled tasks** — run something at a time, either once, on a cron schedule (five fields), or with each heartbeat. Tasks are created by the AI: just say "remind me to hand in my homework at 9 every morning".
- **Watchers** — local polling (battery, URL, command) that **only calls the model when something changed**, instead of reporting "all normal" every round.
- **Perception** — optional reading of notifications and SMS. Existing content is skipped on first enable; message bodies never reach the model, only counts and senders; OTP-like messages are masked before truncation.
- **Its own memory** — a separate bucket shared with no other mode; the top bar opens a memory page with two tabs, one for memories and one that consolidates scattered notes into files.
- **Its own tools and workspace** — time, memory, calendar, alarms, file access, shell, SMS, each toggled individually with the dangerous ones off; a workspace template is laid down on first entry.
- **Keep-alive and cost** — foreground notification, exact alarms, a WorkManager fallback, battery-optimization and autostart guidance, self-healing on return; the drawer shows today's tokens and estimated cost.

Memory is **read-only across modes**: nobody writes into another mode's bucket.

---

## 📦 Upstream capabilities (kept, and kept current)

> These come from upstream. Nothing was removed, and each upstream release is merged in.

- 🎨 Material You and 🌙 dark mode
- 🧠 Two memory systems: per-item and file-based
- 🎬 Media generation: images and video
- 📦 Workspaces: proot Linux, SSH remote machines, built-in terminal
- 🪟 Background generation overlay — leave mid-generation and come back to it
- 🔄 Any OpenAI / Google / Anthropic-compatible endpoint, with custom API, URL and model
- 🖼️ Multimodal input (images, text documents, PDF, Docx)
- 🖥️ Web access from other platforms
- 🛠️ MCP support
- 📝 Markdown rendering (code highlighting, LaTeX, tables, Mermaid)
- 📊 `chart_display` tool (line / bar / scatter, read-only)
- 🪾 Message branching
- 🔍 Search providers (Exa, Tavily, Zhipu, LinkUp, Brave, Perplexity and more)
- 🧩 Prompt variables (model name, time)
- 🤳 Provider QR export and import
- 🤖 Custom agents
- 📝 AI translation
- 🌐 Custom HTTP headers and bodies
- 💌 SillyTavern character-card import
- ✂️ Image cropping, context-compression stepper, tool-call timings

---

## 🔧 Changes that apply everywhere

Not tied to one mode, but used by all of them:

- **Memory** — groups, per-item migration, two-way import between chat and group chat; copy by default, with a warning before moving shared memory
- **Tool approval** — four tiers: L0 read-only, L1 self-management, L2 create, L3 rewrite. The mode layer can only raise risk, never lower it, so a tool's own safety declarations are never weakened
- **Video and workspace uploads** — Google retains native video input. For other providers, historical videos become text placeholders only in the request copy; original messages remain intact. Videos, archives and other non-chat attachments can be uploaded to the bound workspace, up to 2 GiB per file; if no workspace is bound, the draft is kept and the user is prompted to select one
- **Calendar deletion** — `calendar_delete` added; deletion always asks
- **DeepSeek reasoning replay** — in thinking mode after a tool call, historical `reasoning_content` is replayed in full, fixing HTTP 400 interruptions
- **Per-provider loading animations** — brand-matched automatically, a built-in preset, or your own GIF; OpenAI spin, Claude star, Gemini sparkle, DeepSeek pulse and a generic breathe
- **Liquid glass** — real refraction and dispersion in the input bar on Android 13+; older devices or a rendering failure fall back to blurred glass instead of crashing
- **Fork-aware update check** — reads this repository's GitHub Releases only and opens the release page for you to download
- **Stability** — generation holds and renews a `PARTIAL_WAKE_LOCK`, with no application-level generation deadline or cancellation on device sleep; request logs use active time and report sleep separately; a startup race that reverted group mode to chat on relaunch is fixed
- **Interface and branding** — the app is `RikkaHub Work`, the About page links to this repository only, and sponsorship entries and the export watermark are gone

---

## 🔧 In Development

`3.3.0-work.1 / 207` is implemented and three signed Release variants have been built. It is not published yet; real-service and device interaction checks remain pending.

- **Upstream 3.3.0** — DeepSeek speech recognition and synthesis, DeepSeek history import, paragraph-split assistant bubbles, shared-storage workspaces, Vertex Interactions, a safe mode that trims oversized settings, and a connection-test dialog
- **Workspace file previews (F021)** — Markdown renders by default with a source-view toggle; HTML / SVG use local resources within the document directory, with traversal and external network access blocked and HTML scripts off by default. FILES saving and existing read-only LINUX limits remain
- **DeepSeek server-side tools (F022)** — a separate Read URL switch; Chat Completions supports `web_search` / `read_url` declarations, streaming status, sources, errors, and stored tool signatures replayed on subsequent turns
- **Client context (F022)** — optional date, weekday, language and platform metadata; DeepSeek uses each user message's own date, while other protocols use system context. New assistants and Pulse enable it by default; existing settings are not silently enabled

### On-device AI models (F020) — on indefinite hold

Local-model entry points are hidden in this version and are not offered as an available feature. Source code, configuration compatibility and native modules remain; downloaded weights are kept. There is no date for resuming development. Source ZIPs exclude weights and the actual llama.cpp submodule contents; see [llama-cpp/README.md](./llama-cpp/README.md) for the pinned revision and restoration instructions.

Progress and verification scope: [DEVELOPMENT.md](./DEVELOPMENT.md). Delivery records: [DELIVERY.md](./DELIVERY.md).

## ⚠️ Known Limitations

| Item | Notes |
|---|---|
| DeepSeek web login may show a blank page | The in-app WebView can come up empty (UA / fingerprint level, not diagnosed). The screen offers a diagnostic hint: sign in with a system browser or Via, then import via "paste cURL" |
| Desktop mode is display only | Same engine underneath; `navigator.platform` and similar are still detectable, and a page that already redirected to its mobile site cannot be pulled back |
| Liquid glass needs Android 13+ | Older devices get blurred glass silently; the minimum SDK is not raised |
| Characters mode is not implemented | Design only, no code, no date |
| The database only moves forward | 33 in the current release, 34 in the dev build; installing an older version cannot read it — clearing data is the only way back |

## 📥 Install & Upgrade

Download the APK from **Releases**.

- **Package name**: `me.rerere.rikkahub.plus` — different from upstream's `me.rerere.rikkahub`, so **this fork and official RikkaHub can coexist**
- **Upgrading this fork**: install over it. The signature must match and versionCode must not go backwards, or Android refuses the install
- **Coming from another fork or the official app**: uninstall first; signatures differ
- Three builds: `arm64-v8a`, `x86_64` and `universal`; most phones want `arm64-v8a`. Refer to the corresponding Release attachments for actual sizes

> [!WARNING]
> **The database only moves forward.** The published release uses **33**; the local `3.3.0-work.1` build already uses **34**. Direct downgrades to DB33 or earlier are unsupported; plan for data and backups before reverting.
> **Make a full backup first** in Settings → Backup (tick "include database") and keep a copy of the current APK. Settings, conversations and API keys live in the app's private directory and are lost on uninstall.

## 🧭 Versions and packages

| Item | Value |
|---|---|
| Release package | `me.rerere.rikkahub.plus` |
| Debug package | `me.rerere.rikkahub.debug` |
| Version scheme | `{upstream}-work.{n}` |
| Current release | `3.2.0-work.3` (versionCode 206, 2026-11-13) |
| Pending release | `3.3.0-work.1` (versionCode 207, three Release variants built) |
| Database | 33 (34 in the dev build, forward only) |
| License | AGPL-3.0 |

> Development history, current stage and defect analyses: [DEVELOPMENT.md](./DEVELOPMENT.md).

## 🛠 Building

Open in [Android Studio](https://developer.android.com/studio). Stack:

- [Kotlin](https://kotlinlang.org/) (language)
- [Koin](https://insert-koin.io/) (dependency injection)
- [Jetpack Compose](https://developer.android.com/jetpack/compose) (UI)
- [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) (preferences)
- [Room](https://developer.android.com/training/data-storage/room) (database)
- [Coil](https://coil-kt.github.io/coil/) (images)
- [Material You](https://m3.material.io/) (design)
- [Navigation 3](https://developer.android.com/guide/navigation/navigation-3) (navigation)
- [OkHttp](https://square.github.io/okhttp/) (HTTP)
- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) (JSON)

Upstream split distribution into two flavors; the GitHub build is **`assembleGithubRelease`** (this fork only builds `github`).

> [!TIP]
> Place `google-services.json` in the `app` directory before building.

> [!IMPORTANT]
> Translation PRs from upstream are not accepted; only English, Simplified Chinese and Traditional Chinese are kept.

## 🙏 Credits

All of the foundation belongs to [RikkaHub](https://github.com/rikkahub/rikkahub) and its contributors. This fork would not exist without it.

Third-party components:

- [AndroidLiquidGlassView](https://github.com/QmDeve/AndroidLiquidGlassView) (MIT, © Donny Yale / QmDeve)
- [Mozilla Readability](https://github.com/mozilla/readability) (Apache-2.0, article extraction)
- [Haze](https://github.com/chrisbanes/haze) (Apache-2.0, blurred-glass fallback)
- [Termux terminal-emulator](https://github.com/termux/termux-app) (Apache-2.0, terminal core)

If this project helps you, please star **upstream** ⭐

## 📄 License

This fork is licensed under **AGPL-3.0**; original copyright belongs to the RikkaHub authors.
See [LICENSE](./LICENSE) in the repository root.
