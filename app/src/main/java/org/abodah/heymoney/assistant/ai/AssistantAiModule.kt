package org.abodah.heymoney.assistant.ai

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AssistantAiModule {

    @Binds
    @Singleton
    abstract fun bindLlmEngine(impl: MediaPipeLlmEngine): LlmEngine
}
