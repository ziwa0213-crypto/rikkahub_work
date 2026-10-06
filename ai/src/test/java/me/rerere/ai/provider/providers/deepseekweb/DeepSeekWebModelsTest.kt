package me.rerere.ai.provider.providers.deepseekweb

import me.rerere.ai.provider.CustomHeader
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSeekWebModelsTest {
    @Test
    fun defaultsDeclareImagesForFastAndThinkingModes() {
        val models = DeepSeekWebModels.defaults()
        assertEquals(listOf("deepseek-web", "deepseek-web-thinking"), models.map { it.modelId })
        models.forEach { assertEquals(listOf(Modality.TEXT, Modality.IMAGE), it.inputModalities) }
        assertTrue(ModelAbility.REASONING in models.last().abilities)
    }

    @Test
    fun upgradesExistingModelsWithoutReplacingIdsOrCustomizations() {
        val legacy = Model("deepseek-web-thinking", "My model", customHeaders = listOf(CustomHeader("X-Test", "value")))
        val migrated = DeepSeekWebModels.withImageInput(listOf(legacy)).single()
        assertEquals(legacy.copy(inputModalities = listOf(Modality.TEXT, Modality.IMAGE)), migrated)
        assertEquals(legacy.id, migrated.id)
        assertEquals(listOf(migrated), DeepSeekWebModels.withImageInput(listOf(migrated)))
    }

    @Test
    fun doesNotAlterUnrecognizedModelDefinitions() {
        val model = Model("custom-model", "Custom")
        assertSame(model, DeepSeekWebModels.withImageInput(listOf(model)).single())
    }
}
