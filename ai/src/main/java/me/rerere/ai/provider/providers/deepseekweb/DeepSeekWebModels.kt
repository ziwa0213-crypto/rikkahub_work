package me.rerere.ai.provider.providers.deepseekweb

import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility

object DeepSeekWebModels {
    private val modelIds = setOf("deepseek-web", "deepseek-web-thinking")

    fun defaults(): List<Model> = listOf(
        Model(
            "deepseek-web", "快速模式",
            inputModalities = listOf(Modality.TEXT, Modality.IMAGE),
            abilities = listOf(ModelAbility.TOOL),
        ),
        Model(
            "deepseek-web-thinking", "思考模式",
            inputModalities = listOf(Modality.TEXT, Modality.IMAGE),
            abilities = listOf(ModelAbility.TOOL, ModelAbility.REASONING),
        ),
    )

    fun withImageInput(models: List<Model>): List<Model> = models.map { model ->
        if (model.modelId in modelIds && Modality.IMAGE !in model.inputModalities) {
            model.copy(inputModalities = model.inputModalities + Modality.IMAGE)
        } else model
    }
}
