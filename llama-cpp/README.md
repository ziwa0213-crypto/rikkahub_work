# Local llama.cpp provider (F020)

CPU Android inference with opt-in experimental Vulkan GPU offload, GGUF installation, template-driven streaming tools,
and Android-decoded image/audio/video input. Model weights live in the app's
private `filesDir/models/llamacpp` directory and are never bundled in the APK.

## Native source

`native/llama.cpp` is a Git submodule of `https://github.com/ggml-org/llama.cpp`,
fixed at **b1b770d90d94cad9e9ea5b993bdbc383a522fb28** (tag **v1.0.0**).
Do not update it to master without reviewing the JNI API and parser contract.

In a Git checkout:

```bash
git submodule update --init --recursive
git -C llama-cpp/native/llama.cpp rev-parse HEAD
```

The compact source ZIP intentionally excludes that submodule's contents and all
build products. Its exact revision is also recorded in `native/llama.cpp.commit`.
The existing tracked libsimple/proot binaries under the app/workspace jniLibs
directories are required source-tree dependencies and remain in the archive;
this exclusion applies to generated native outputs, not those dependencies.
From an extracted ZIP, restore the source before building:

```bash
git clone --no-checkout https://github.com/ggml-org/llama.cpp llama-cpp/native/llama.cpp
git -C llama-cpp/native/llama.cpp checkout --detach b1b770d90d94cad9e9ea5b993bdbc383a522fb28
```

The native project is MIT licensed; the unmodified license is included here.
The reference implementation was read from ExTV/rikkahub-agent at
`a88f5854b4d2e2706ea2ae8304a7d66a3f9f2bbc` (v2.5.1-agent.1), then adapted to this
fork's provider/settings and the pinned native version. The reference checkout is
kept separately and is not part of the product archive.

## Build and verification

Android NDK `27.2.12479018`, CMake `3.22.1`, JDK 21. Set `LLAMA_NDK_PATH` when
using an NDK outside the configured SDK. Building the opt-in Vulkan backend requires
host `glslc`, Vulkan headers and SPIRV-Headers; on Ubuntu install
`glslc glslang-tools libvulkan-dev spirv-headers`. The first native build
compiles the pinned shaders. OpenCL, ffmpeg, subprocesses and an external llama
server remain disabled. Native outputs use flexible 16 KB pages.

Set `LLAMA_GLSLC`, `LLAMA_VULKAN_INCLUDE_DIR` and `LLAMA_SPIRV_HEADERS_DIR` only
when CMake cannot find those host development tools in its normal search paths.
The app setting is off by default. CPU requests explicitly select zero GPU
layers. The explicit GPU load registers the Vulkan backend, requests 99 GPU
layers and retries on CPU once if loading fails. `vulkan-android.cmake` generates
an Android build copy that resolves device features through the instance
dispatcher, preserving Android API 26 and the pinned checkout. The backend
requires a Vulkan 1.2 loader and compatible device features. Device support,
inference speed and memory use are measured on Android hardware.

```bash
./gradlew :llama-cpp:assembleDebug
./gradlew test
./gradlew :app:assembleGithubRelease -Prikkahub.abi=arm64-v8a
```

The optional `rikkahub.abi` property limits a delivery to one architecture; normal
builds retain ARM64 and x86_64 support. Release signing remains the project's
existing local configuration, which is intentionally excluded from source ZIPs.

JVM protocol/download/planning/settings tests do not validate actual native model
inference, memory pressure, Android media codecs or device UI. See root
`DELIVERY.md` for the per-item verification status and remaining phone checks.

## Explicit loading and short-context compression

Load from the model detail sheet or the single-chat/Pulse capsule before
generation. `withReady` never loads or swaps a model. The inference mutex covers
the entire template/generation/usage lifecycle; a requested deferred unload is
consumed in `finally`, and returning to the foreground cancels it. Background
notifications are advisory and never automatically unload a model.

Physical memory only controls an optional warning. Context follows the model
training limit and the user's choice (floor 512, upper limit 16384, F16 KV),
without shrinking to fit available memory. Available memory and experimental
fusion (at most min(SwapTotal / 8, 1 GiB)) inform the estimated tight-memory
warning only. Reclaimable mmap weight pages are estimated at one quarter of the
file size. Output is capped at one quarter of the context window.
Changing context/fusion settings affects the next explicit load; unload/reload a
ready model to apply them. A very small allocated context can still reject an
oversized input/output request; successful loading is not a guarantee that every
prompt fits.

Compression uses the same app checkpoint path. Its local provider adapter
requires the complete selected prefix and checks the final templated token count;
it fails rather than letting normal chat trimming discard history being
summarized. Normal generation trimming is unchanged. Prompt and raw completion
counts are emitted as Usage before Finish and before releasing the applied
template. Character estimates are only used for local compression triggers and
optional prompt-layer decisions, never as displayed usage or text clipping.

Newly imported models default to 8192 context tokens. Existing saved choices are
not migrated. Before local request transformation, system text plus tool schemas
are estimated at three characters per token: above 55% of the input budget tools
and their system prompts (including the skills list) are omitted; if the remaining
prompt exceeds 75%, memory injection is omitted too. The chat page discloses both
omissions above the input area in a dismissible, process-local notice. API requests do not use
these rules. The agent preserves the local omission instead of reinserting tool
definitions. Per-request metadata is transient and never persisted or serialized
to an API. The loaded model's actual context is used when available.

TOO_LONG diagnostic component counts are computed only on failure; they are
approximate raw system/tool/history counts, not an additive template-token total.
Failed component counts display as `?`. CPU workers use 2–6 threads according to
available processors; batching remains 512. This is a prompt-fit repair, not a
measured performance claim. Real tok/s and Android memory pressure need device
testing with the maintainer's model.

Model configuration can select the embedded/automatic chat template, a bundled
ChatML/Llama 3/Gemma Jinja preset, or custom Jinja. Templates are applied on the
next request. A missing embedded template is a warning, not a load failure;
normal completion without text or a usable tool call reports EMPTY_OUTPUT.
Cancellation does not report empty output. There is no bundled LFM preset.
