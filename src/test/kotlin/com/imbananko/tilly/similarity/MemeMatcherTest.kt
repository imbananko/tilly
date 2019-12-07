package com.imbananko.tilly.similarity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File

class MemeMatcherTest {
  private lateinit var memeMatcher: MemeMatcher

  @Suppress("unused")
  companion object {
    @JvmStatic
    val memes = listOf("meme_changed_less.jpg", "meme_changed_more.jpg", "original_meme.jpg", "meme1.jpg", "meme2.jpg")
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

    assertEquals(fileId, memeMatcher.checkMemeExists(fileId, file))
  }

  @ParameterizedTest
  @MethodSource("changedMemesProvider")
  fun `Memes with a difference in text should be distinguished`(changedMeme: File) {
    val originalMeme = memes["original_meme.jpg"] ?: error("original_meme.jpg is not fount")
    memeMatcher.addMeme(originalMeme.name, originalMeme)
    val memeMatch = memeMatcher.checkMemeExists(changedMeme.name, changedMeme)

    assertNull(memeMatch, "Changed meme ${changedMeme.name} should be different from original one ${originalMeme.name}")
  }

  @Test
  fun `Meme1 and Meme2 should be distinguished`() {
    val meme1 = memes["meme1.jpg"] ?: error("meme1.jpg is not fount")
    val meme2 = memes["meme2.jpg"] ?: error("meme2.jpg is not fount")
    memeMatcher.addMeme(meme1.name, meme1)
    val memeMatch = memeMatcher.checkMemeExists(meme2.name, meme2)

    assertNull(memeMatch, "Changed meme ${meme2.name} should be different from original one ${meme1.name}")
  }
}
