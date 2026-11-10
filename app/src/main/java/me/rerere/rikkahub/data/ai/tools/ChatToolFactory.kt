package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.jsonObject
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.providers.deepseekweb.DeepSeekWebCapabilityRefusalException
import me.rerere.ai.provider.providers.deepseekweb.DeepSeekWebToolPolicy
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.tools.local.LocalTools
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.data.repository.memoryId
import me.rerere.rikkahub.data.skills.SkillManager
import me.rerere.rikkahub.service.BackgroundShellManager
import me.rerere.workspace.WorkspaceShellStatus
import java.io.File
import kotlin.uuid.Uuid

private const val TAG = "ChatToolFactory"
private const val MAX_TOOL_OUTPUT_CHARS = 32 * 1024
private const val TOOL_OUTPUT_PREVIEW_CHARS = 4 * 1024

// 搜索结果的体积由用户设置的结果数决定，且结果列表 UI 与引用跳转都依赖完整的 JSON 结构，不参与截断
private val TOOLS_WITHOUT_OUTPUT_TRUNCATION = setOf("search_web", "read_notifications", "read_sms")

internal fun shouldUseExternalWebSearch(assistant: Assistant, model: Model): Boolean {
    return assistant.enableWebSearch && BuiltInTools.Search !in model.tools
}

class InvalidMcpServerNamesException(val names: List<String>) :
    IllegalStateException("Invalid MCP server names: ${names.joinToString(", ")}")

/** Creates the complete tool set for one generation run, including approval resumption. */
class ChatToolFactory(
    private val context: Context,
    private val memoryRepository: MemoryRepository,
    private val conversationRepository: ConversationRepository,
    private val localTools: LocalTools,
    private val mcpManager: McpManager,
    private val skillManager: SkillManager,
    private val workspaceRepository: WorkspaceRepository,
    private val pulseTasks: me.rerere.rikkahub.data.repository.PulseTaskRepository,
    private val backgroundShellManager: BackgroundShellManager,
    private val settingsStore: me.rerere.rikkahub.data.datastore.SettingsStore,
) {
    suspend fun createTools(
        settings: Settings,
        assistant: Assistant,
        model: Model,
        workspaceCwd: String? = null,
        memoryScope: String? = null,
        conversationScopeAssistantId: Uuid? = assistant.id,
        conversationId: Uuid? = null,
    ): List<Tool> {
        val workspaceTools = createWorkspaceToolsIfReady(
            assistant.workspaceId?.toString(),
            workspaceCwd,
            allowedOwners = when {
                assistant.id == me.rerere.rikkahub.data.model.PULSE_ASSISTANT_ID -> setOf("pulse")
                memoryScope != null -> setOf(null, "group")
                else -> setOf(null, "chat")
            },
            conversationId = conversationId,
        )
        val tools = buildList {
            if (assistant.id == me.rerere.rikkahub.data.model.PULSE_ASSISTANT_ID) {
                if (settings.pulse.enableNotificationReading) add(me.rerere.rikkahub.data.ai.tools.pulse.readNotificationsTool(context, settingsStore))
                if (settings.pulse.enableSmsReading) add(me.rerere.rikkahub.data.ai.tools.pulse.readSmsTool(context, settingsStore))
                if (settings.pulse.enableSmsSending) add(me.rerere.rikkahub.data.ai.tools.pulse.sendSmsTool(context, settingsStore))
            }
            if (assistant.id == me.rerere.rikkahub.data.model.PULSE_ASSISTANT_ID && settings.pulse.enableScheduledTasks) {
                add(me.rerere.rikkahub.data.ai.tools.pulse.pulseTaskTool(pulseTasks))
            }
            if (assistant.enableMemory) {
                addAll(buildMemoryTools(memoryRepository, memoryScope ?: assistant.memoryId))
                // Register even for an empty store, so the first explicit request can be saved.
                addAll(buildRowMemoryTools(memoryRepository, memoryScope ?: assistant.memoryId))
            }
            if (shouldUseExternalWebSearch(assistant, model)) {
                addAll(createSearchTools(settings))
            }
            addAll(localTools.getTools(assistant.localTools, conversationId))
            if (assistant.enableRecentChatsReference) {
                addAll(createConversationTools(conversationRepository, conversationScopeAssistantId,
                    pulse = assistant.id == me.rerere.rikkahub.data.model.PULSE_ASSISTANT_ID))
            }
            addAll(workspaceTools)
            if (assistant.enabledSkills.isNotEmpty()) {
                addAll(
                    createSkillTools(
                        enabledSkills = assistant.enabledSkills,
                        allSkills = skillManager.listSkills(),
                    )
                )
            }

            val mcpTools = mcpManager.getAllAvailableTools(assistant)
            val invalidNames = mcpTools
                .map { it.second }
                .distinct()
                .filter { name -> name.isEmpty() || !name.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' } }
            if (invalidNames.isNotEmpty()) {
                throw InvalidMcpServerNamesException(invalidNames)
            }
            mcpTools.forEach { (serverId, serverName, tool) ->
                add(
                    Tool(
                        name = "mcp__${serverName}__${tool.name}",
                        description = tool.description ?: "",
                        parameters = { tool.inputSchema },
                        needsApproval = { tool.needsApproval },
                        execute = { mcpManager.callTool(serverId, tool.name, it.jsonObject) },
                    )
                )
            }
        }
        // 过长的输出存成文件让模型用 shell 按需读取；没有 shell 时模型读不到文件，只能原样返回
        val scoped = if (memoryScope == null) tools else tools.filterNot { it.name.startsWith("browser_") }
        val deepSeekWeb = model.findProvider(settings.providers, checkOverwrite = false) as? ProviderSetting.DeepSeekWeb
        val allowed = if (deepSeekWeb != null) {
            val unlock = deepSeekWeb.unlock
            val allowedNames = DeepSeekWebToolPolicy.allowedNames(unlock)
            scoped.filter {
                it.name in allowedNames || (unlock.active.mcp && it.name.startsWith("mcp__", ignoreCase = true))
            }.map { tool ->
                val execute = tool.execute
                tool.copy(execute = { args ->
                    if (!DeepSeekWebToolPolicy.allows(tool.name, args.jsonObject, unlock)) {
                        throw DeepSeekWebCapabilityRefusalException(blockedToolName = tool.name)
                    }
                    execute(args)
                })
            }
        } else scoped
        // 截断后的文件落在本机 /tool_outputs，只有本地 Rootfs 工作区能再次读取。
        val canReadToolOutputs = workspaceTools.isNotEmpty() &&
            assistant.workspaceId?.let { workspaceRepository.getById(it.toString()) }?.capabilities()?.rootfs == true
        val prepared = if (!canReadToolOutputs) allowed else allowed.map(::withOutputTruncation)
        val modeTools = if (assistant.id == me.rerere.rikkahub.data.model.PULSE_ASSISTANT_ID) {
            me.rerere.rikkahub.data.ai.tools.pulse.applyPulseToolPolicy(prepared, settings.pulse)
        } else prepared
        return applyApprovalMode(modeTools, assistant.toolApprovalMode)
    }

    private fun withOutputTruncation(tool: Tool): Tool {
        if (tool.name in TOOLS_WITHOUT_OUTPUT_TRUNCATION) return tool
        val execute = tool.execute
        return tool.copy(execute = { truncateOutput(execute(it)) })
    }

    private fun truncateOutput(output: List<UIMessagePart>): List<UIMessagePart> {
        val textParts = output.filterIsInstance<UIMessagePart.Text>()
        val totalChars = textParts.sumOf { it.text.length }
        if (totalChars <= MAX_TOOL_OUTPUT_CHARS) return output

        val fileName = "${Uuid.random()}.txt"
        Log.i(TAG, "truncateOutput: saving $totalChars chars to $fileName")
        val fullText = textParts.joinToString("\n") { it.text }
        val outputDir = File(context.filesDir, FileFolders.TOOL_OUTPUTS).apply { mkdirs() }
        File(outputDir, fileName).writeText(fullText)

        return listOf(
            UIMessagePart.Text(
                buildString {
                    appendLine("[Tool output truncated: $totalChars characters total]")
                    appendLine("Full output saved to: /tool_outputs/$fileName")
                    appendLine("Use shell to read: `cat /tool_outputs/$fileName`")
                    appendLine("Use shell to search: `grep \"pattern\" /tool_outputs/$fileName`")
                    appendLine()
                    append(fullText.take(TOOL_OUTPUT_PREVIEW_CHARS))
                }
            )
        ) + output.filter { it !is UIMessagePart.Text }
    }

    private suspend fun createWorkspaceToolsIfReady(
        workspaceId: String?,
        cwd: String?,
        allowedOwners: Set<String?>? = null,
        conversationId: Uuid? = null,
    ): List<Tool> {
        if (workspaceId.isNullOrBlank()) return emptyList()
        val workspace = workspaceRepository.getById(workspaceId) ?: return emptyList()
        if (allowedOwners != null && workspace.owner !in allowedOwners) {
            Log.w(TAG, "createWorkspaceToolsIfReady: skip workspace with wrong owner, workspace=$workspaceId")
            return emptyList()
        }
        if (workspace.shellStatus != WorkspaceShellStatus.READY.name) {
            // Pulse file access does not require a Linux environment; keep shell tools unavailable.
            if (workspace.owner == "pulse" && workspace.capabilities().localFiles) {
                return createWorkspaceTools(workspaceId, workspaceRepository, cwd)
                    .filter { it.name in setOf("workspace_read_file", "workspace_write_file", "workspace_edit_file") }
            }
            Log.d(
                TAG,
                "createWorkspaceToolsIfReady: skip workspace tools, workspace=$workspaceId, status=${workspace.shellStatus}"
            )
            return emptyList()
        }
        return createWorkspaceTools(workspaceId, workspaceRepository, cwd, conversationId, backgroundShellManager)
    }
}
