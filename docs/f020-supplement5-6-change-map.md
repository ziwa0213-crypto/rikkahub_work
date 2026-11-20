# F020 Supplement 5/6 Change Map

Baseline: F020 supplement 4 in the existing worktree. Version stays
`3.3.0-work.1 / 207`, DB34. Delivery is the requested signed ARM64 Release APK
plus compact source ZIP with root `DELIVERY.md`; no Debug APK or remote write.

## Supplement 5: UI notice and inline thinking

| Area | Implementation |
|---|---|
| Runtime state | `LocalPromptNoticeStore` keeps per-conversation drops in process memory. Empty entries record dismissal, so later generation steps cannot reopen the notice. |
| Registration | `ChatRequestFactory.prepareStep` emits the actual local-model drop decision to `ChatService`; API providers and compression diagnostics do not add messages or persistent fields. |
| Presentation | `ChatVM` exposes the service flow; `ChatPage` places one theme-colored, dismissible row above the capsule/input region. Both notices are joined on one line. |
| Model output | `LlamaCppProvider` no longer turns budget notices into assistant text; it retains a diagnostic log. Existing media notices remain unchanged. |
| Thinking | `InlineThinking` wraps the existing authoritative template parse. It preserves native reasoning and unrelated JSON fields, ignores Markdown code spans/fences, and suppresses incomplete delimiter suffixes during streaming. |

## Supplement 6: Vulkan

| Area | Implementation |
|---|---|
| Build | `CMakeLists.txt` enables `GGML_VULKAN` and configures the host shader compiler through `vulkan-host-toolchain.cmake.in`. `vulkan-android.cmake` generates the source with instance dispatch for the API 26 loader. |
| CPU/GPU selection | Persistent `enableLocalGpu=false`. CPU load uses 0 layers and an explicit empty device list. JNI registers Vulkan on an explicit GPU load, selects available GPU/iGPU devices and requests 99 layers. |
| Recovery | GPU model/context failure releases partial native state and tries CPU once. Cancellation is rethrown; preference is never changed. Ready state reports the fallback. |
| Diagnostics | Native model-load logs system/backend information; generation usage logs elapsed uptime and token/s. |

## Verification boundaries

- JVM tests cover notice dismissal/session isolation, native-reasoning preservation,
  code examples, partial streaming delimiters, and success/failure/cancel/retry
  runtime transitions.
- Release build checks ABI packaging, shader backend and artifact size. It does
  not establish device Vulkan support, successful real GGUF inference, speedup,
  peak memory, or behavior on Vulkan-less devices.
- Restore/install, chat UI screenshots and GPU speed for LFM3/Qwen3.5 remain
  maintainer device checks. Never report estimated speedup as measured.
- The adjacent `SUPPLEMENT5-6-DELTA.json` lists all 8 added and 27 changed files
  against the supplement 4 source archive. `DELIVERY.md` records the tested build
  command, final APK sizes and the remaining Android checks.

## Rollback boundary

Revert only this batch's hunks in the listed source, resource, test and ledger
files. Preserve the earlier F020 model, media, workspace, compression and prompt
budget work. Do not restore whole-file snapshots for ChatService, ChatPage,
Settings, Runtime, CMake, or `llama_jni.cpp`.
