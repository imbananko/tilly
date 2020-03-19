package com.chsdngm.tilly.similarity

import net.sourceforge.tess4j.Tesseract
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
    val memes = listOf("original_meme.jpg", "meme_changed_more.jpg", "doctor1.jpg", "doctor2.jpg", "doctor3.jpg", "meme1.jpg", "meme2.jpg", "duplicate1.jpg", "duplicate2.jpg")
        .map { it to loadMeme(it) }.toMap()

    @JvmStatic
    fun imageProvider() = memes.values.stream()

    @JvmStatic
    fun loadMeme(memeName: String) =
        File(this::class.java.classLoader.getResource(memeName)?.file
            ?: error("meme not found: $memeName"))

    var tesseract = Tesseract()
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

    assertEquals(fileId, memeMatcher.tryFindDuplicate(file))
  }

  @Test
  fun `Memes with a difference in text should be distinguished`() {
    val originalMeme = memes["original_meme.jpg"] ?: error("doctor1.jpg is not found")
    val doctor1 = memes["doctor3.jpg"] ?: error("doctor2.jpg is not found")

    val text1 = tesseract.doOCR(originalMeme);
    val text2 = tesseract.doOCR(doctor1);

    memeMatcher.addMeme(originalMeme.name, originalMeme)
    val memeMatch1 = memeMatcher.tryFindDuplicate(doctor1)
    assertNull(memeMatch1, "Changed meme ${doctor1.name} should be different from original one ${originalMeme.name}")
  }

  @Test
  fun `Meme1 and Meme2 should be the same`() {
    val meme1 = memes["meme1.jpg"] ?: error("meme1.jpg is not found")
    val meme2 = memes["meme2.jpg"] ?: error("meme2.jpg is not found")
    memeMatcher.addMeme(meme1.name, meme1)
    val memeMatch = memeMatcher.tryFindDuplicate(meme2)

    assertEquals(memeMatch, meme1.name)
  }

  @Test
  fun `Duplicate1 and Duplicate2 should be the same`() {
    val meme1 = memes["duplicate1.jpg"] ?: error("meme1.jpg is not found")
    val meme2 = memes["duplicate2.jpg"] ?: error("meme2.jpg is not found")
    memeMatcher.addMeme(meme1.name, meme1)
    val memeMatch = memeMatcher.tryFindDuplicate(meme2)

    assertEquals(memeMatch, meme1.name)
  }
}
