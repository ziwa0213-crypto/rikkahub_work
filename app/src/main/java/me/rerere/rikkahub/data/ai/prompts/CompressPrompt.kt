package me.rerere.rikkahub.data.ai.prompts

internal val DEFAULT_COMPRESS_PROMPT = """
    You are a conversation compression assistant. Compress the following conversation into a concise summary.

    Requirements:
    1. Preserve key facts, decisions, and important context that would be needed to continue the conversation
    2. Keep the summary in the same language as the original conversation
    3. Target approximately {target_tokens} tokens
    4. Output the summary directly without any explanations or meta-commentary
    5. Format the summary as context information that can be used to continue the conversation
    6. Use {locale} language
    7. Start the output with a clear indicator that this is a summary (e.g., "[Summary of previous conversation]" or equivalent in the target language)

    {additional_context}

    <conversation>
    {content}
    </conversation>
""".trimIndent()

internal val DEFAULT_GROUP_COMPRESS_PROMPT = """
    Compress this group conversation into a briefing for future context in {locale}.
    Preserve the owner's and deputy's requirements, decisions and conclusions in detail.
    Summarise other members by name with one or two sentences each. Do not invent facts.
    Preserve necessary filenames, identifiers and commands. Target {target_tokens} tokens.
    Start with a clear summary heading. Output only the briefing.
    {additional_context}
    <primary>
    {primary}
    </primary>
    <others>
    {others}
    </others>
""".trimIndent()
