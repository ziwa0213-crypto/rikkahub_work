# F009 Supplement 4 Verification

Date: 2026-10-06. Version: `2.5.6-work.1`, versionCode `194`.

## Scope

This implements JSON text-tool protocol alignment for DeepSeek Web only. The 11-tool read-only policy,
clipboard read restriction, shared `GenerationLoop`, tool schemas, persistence and other providers are
unchanged. Existing DSML detection remains a fallback, not an execution protocol. No account-backed
requests, device/emulator operations, commits, tags, pushes or GitHub uploads were performed.

The task attachment's claim that there was no format hint does not exactly match the inspected source:
there was a short hint after the schemas, but no full examples or explicit fence rule. The actual parser
also required the entire trimmed response to be a single object. SSE only considered JSON at the start
of the response, so a call preceded by prose could miss both conversion and execution.

## Prompt Comparison

Before, after the tool schemas:

```text
工具调用格式：单独输出 JSON {"tool":"工具名","arguments":{...}}，不要加解释。
```

After, before `可用工具：`:

```text
调用格式（严格照此输出，不要加代码围栏、不要在调用前后添加解释性文字）：

{"tool": "<工具名>", "arguments": {<参数>}}

示例（仅可调用下方清单中已启用的工具）：
{"tool": "get_time_info", "arguments": {}}
{"tool": "calendar_query", "arguments": {"range": "today"}}
{"tool": "calendar_query", "arguments": {"range": "week", "limit": 5}}

系统收到上述格式后会执行该工具并把结果返回给你，然后你再继续回答用户。
```

The unchanged read-only policy still appears before and after the conversation. The format is not
offered when there are no enabled read-only tools. Examples do not authorize disabled tools.

## Key Changes

- `DeepSeekWebToolProtocol`: find outer JSON boundaries, parse with kotlinx.serialization, classify the
  first call, remove recognized calls and standalone fences, retain surrounding prose and log discarded
  additional calls. String escaping and nested objects are handled. Unknown and malformed calls stay
  visible for application feedback without execution. DSML-contained JSON is excluded.
- `DeepSeekWebSSE`: hold potential calls from the first brace, fence or DSML marker, including split
  markers. Convert an allowed, enabled first call into `ToolCallStart/Delta/End`; produce `tool_calls`
  finish reason. Forbidden or disabled calls produce no executable event. Preserve prose before closing
  the provider stream with `DeepSeekWebCapabilityRefusalException` for the existing error card.
- `DeepSeekWebOutputGuard`: reuse protocol classification for remaining malformed/unknown JSON. Keep
  uncertain examples and report the existing persistent format-error card. Keep DSML behavior.
- `DeepSeekWebTools` / `DeepSeekWebImages`: return read-only tool text and image results in the next
  request. Forbidden historical output is not returned. Tool invocation JSON is not echoed as history.

Brace/fence-containing answers may display their later text at completion instead of streaming it;
plain chat remains streamed. This avoids showing a possible tool JSON before classification.

## Acceptance Evidence

These results are offline tests, not captured DeepSeek responses or Android screenshots.

| Case | Offline evidence | Device/model status |
| --- | --- | --- |
| A1 read workspace | `decodedReadOnlyCallReachesToolExecutionAndResultIsReturnedInNextPrompt`: SSE -> actual shared `StreamChunkHandler` -> `UIMessagePart.Tool` -> test read executor exactly once -> next prompt contains returned data | Pending: read a specified existing file |
| A2 create calendar event | JSON classified blocked; no tool events; persistent link-free capability-card properties tested | Pending |
| A3 invoke Skill | Preflight regression plus parsed `use_skill` rejection/card tested | Pending |
| A4 time | Empty-argument call converted; first-call-only behavior tested | Pending |
| A5 calendar query | Bare/fenced calls, week/limit arguments and thinking/fast SSE tested | Pending |
| A6 fences | `json`, uppercase `JSON` and unlabeled fences supported | Passed offline |
| A7 prose | Prose before/after bare and fenced calls retained, JSON removed | Passed offline |
| A8 multiple JSON | Only first eligible call emitted; all later calls removed | Passed offline |
| A9 malformed JSON | No crash or execution; logs and application format feedback tested | Passed offline |
| A10 ordinary chat | Ordinary text/JSON/HTML/code and incomplete marker suffixes retained without duplicate text | Passed offline |
| A11 HTML knowledge | Existing preflight knowledge-question regressions and plain-code preservation remain passing | Passed offline |
| A12 API regression | Other provider implementations unchanged; shared tests run by full `test` | Device behavior not exercised |
| A13 changes log | Dated Supplement 4 entry in `CHANGES.md` and `FEATURES.md` | Recorded |

A1 in the attachment assumes `workspace_read_file` can list a directory. Current `WorkspaceTools.kt`
reads text/image files only. This task does not change that contract, add a directory-listing tool or
enable shell. The offline executor above uses fixture data, not the real Android workspace repository
or the entire `GenerationLoop`. Real filesystem execution, permissions, UI rendering and screenshots
still require maintainer testing. Approvals, including `ask_user` HITL, follow the existing chain.

## Commands And Artifacts

```bash
./gradlew :ai:test :app:testDebugUnitTest --console=plain
./gradlew test :app:assembleRelease --console=plain
git diff --check
```

Logs: `/tmp/rikkahub-f009-protocol-tests.log`, `/tmp/rikkahub-f009-protocol-release.log`.
Only signed Release APKs are delivered; Debug unit testing does not assemble a Debug APK.
This iteration retains English, Simplified Chinese and Traditional Chinese only.

APK delivery directory: `app/build/outputs/apk/f009-protocol-20261006/`.
Names: `RikkaHub.Work-2.5.6-work.1-{arm64-v8a,universal,x86_64}-release.apk`.
The previous image-upload delivery directory is preserved.

Final results: both commands passed; full-module tests and Release build finished with
`BUILD SUCCESSFUL in 2m 4s`. The nine DeepSeek Web-related JVM suites contain 75 tests, zero failures,
zero errors and zero skips. `git diff --check` passed. All three delivery copies were byte-compared
with the build outputs.

Package inspection for all three APKs:

- Package: `me.rerere.rikkahub.plus`.
- Version: `2.5.6-work.1` / `194`.
- Label: `RikkaHub Work`.
- Locales: default, `en`, `zh`, `zh-CN`, `zh-TW` only.
- APK signature v2 verified; certificate SHA-256:
  `671f265609eb23c6c84abbfc0342b4e11dd8ba20db9714bc475c536d8e2a6d6b`.
- Included `assets/deepseek_sha3.wasm` SHA-256:
  `b3fca8cc072c1defbd60c02266a8e48bd307a1804aaff4314900aea720e72f7d`.

Delivery APK SHA-256:

| ABI | SHA-256 |
| --- | --- |
| arm64-v8a | `4da64d5b6cb866cc91f22611bf43f78d41ff37b1c93feb0fb63c9d9991d9ffa9` |
| universal | `132196947aec716a4ff4b4a90d63f5f41db64defb51a9a3c5fe7ccb2f8e63b7a` |
| x86_64 | `6da5704f7ae3172b73e755185d27961e0457284a631974095163bd559f23efeb` |

## Rollback

No commit hash exists for this iteration. Reverse only the Supplement 4 incremental changes, preserving
earlier F009 image and refusal fixes. Do not restore whole modified files from HEAD. After a separately
authorized isolated commit, use `git revert <supplement4-commit>`.
