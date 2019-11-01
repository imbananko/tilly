package com.imbananko.tilly.similarity

import io.vavr.control.Option
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File

class MemeMatcherTest {
    private lateinit var memeMatcher: MemeMatcher

    @Suppress("unused")
    companion object {
        @JvmStatic
        val memes = listOf("meme_changed_less.jpg", "meme_changed_more.jpg", "original_meme.jpg")
                .map { it to loadMeme(it) }.toMap()

        @JvmStatic
        fun imageProvider() = memes.values.stream()

        @JvmStatic
        fun changedMemesProvider() = memes.values.filterNot { "original" in it.name }

        @JvmStatic
        fun loadMeme(memeName: String) =
                File(this::class.java.classLoader.getResource(memeName)?.file
                        ?: error("meme not found: $memeName"))
    }

    @BeforeEach
    fun init() {
        memeMatcher = MemeMatcher()
    }

    @ParameterizedTest
    @MethodSource("imageProvider")
    fun `MemeMatcher should identify equal memes`(file: File) {
        val fileId = file.name
        memeMatcher.addMeme(fileId, file)

        assertEquals(fileId, memeMatcher.checkMemeExists(fileId, file).get().get())
    }

    @ParameterizedTest
    @MethodSource("changedMemesProvider")
    fun `Memes with a difference in text should be distinguished`(changedMeme: File) {
        val originalMeme = memes["original_meme.jpg"] ?: error("original_meme.jpg is not fount")
        memeMatcher.addMeme(originalMeme.name, originalMeme)
        val memeMatch = memeMatcher.checkMemeExists(changedMeme.name, changedMeme).get()

        assertEquals(Option.none<String>(),
                memeMatch,
                "Changed meme ${changedMeme.name} should be different from original one ${originalMeme.name}")
    }
}