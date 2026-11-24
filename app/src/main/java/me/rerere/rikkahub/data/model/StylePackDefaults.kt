package me.rerere.rikkahub.data.model

import kotlin.uuid.Uuid

internal const val MAX_STYLE_PACK_RULES = 12

val DEFAULT_STYLE_PACKS: List<StylePack> = listOf(
    StylePack(
        id = Uuid.parse("a1b2c3d4-0001-4000-8000-000000000001"),
        name = "去人机感",
        builtIn = true,
        rules = listOf(
            "只写角色在当下做的事和说的话，不用『我明白了』一类汇报。",
            "用具体动作写出细节。",
            "不总结对方的情绪，不替对方描述感受。",
            "说完就停，不在段末升华。",
            "少用『不是……而是……』的句式。",
            "使用自然口语，避免翻译腔。",
            "避免套式的外貌与声音描写。",
            "通过人物的行动表现情绪。",
        ),
    ),
)
