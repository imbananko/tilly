package com.chsdngm.tilly.similarity

import com.chsdngm.tilly.repository.ImageRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito.mock
import java.io.File

class ImageMatcherTest {
  private lateinit var imageMatcher: ImageMatcher

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
  }

  @BeforeEach
  fun init() {
    imageMatcher = ImageMatcher(mock(ImageRepository::class.java))
    imageMatcher.init()
  }

  @ParameterizedTest
  @MethodSource("imageProvider")
  fun `MemeMatcher should identify equal memes`(file: File) {
    val fileId = file.name
    imageMatcher.add(fileId, file)

    assertEquals(fileId, imageMatcher.tryFindDuplicate(file))
  }

  @Test
  fun `Almost same memes with a difference in text should be distinguished`() {
    val originalMeme = memes["original_meme.jpg"] ?: error("original_meme.jpg is not found")
    val doctor3 = memes["doctor3.jpg"] ?: error("doctor3.jpg is not found")

    imageMatcher.add(originalMeme.name, originalMeme)
    val memeMatch1 = imageMatcher.tryFindDuplicate(doctor3)
    assertNull(memeMatch1, "Changed meme ${doctor3.name} should be different from original one ${originalMeme.name}")
  }

  // TODO: currently falling
  @Disabled
  @Test
  fun `Same memes with a difference in text should be distinguished`() {
    val doctor1 = memes["doctor1.jpg"] ?: error("doctor1.jpg is not found")
    val doctor2 = memes["doctor2.jpg"] ?: error("doctor2.jpg is not found")

    imageMatcher.add(doctor1.name, doctor1)
    val memeMatch1 = imageMatcher.tryFindDuplicate(doctor2)
    assertNull(memeMatch1, "Changed meme ${doctor2.name} should be different from original one ${doctor2.name}")
  }

  @Test
  fun `Meme1 and Meme2 should be the same`() {
    val meme1 = memes["meme1.jpg"] ?: error("meme1.jpg is not found")
    val meme2 = memes["meme2.jpg"] ?: error("meme2.jpg is not found")
    imageMatcher.add(meme1.name, meme1)
    val memeMatch = imageMatcher.tryFindDuplicate(meme2)

    assertEquals(memeMatch, meme1.name)
  }

  @Test
  fun `Duplicate1 and Duplicate2 should be the same`() {
    val meme1 = memes["duplicate1.jpg"] ?: error("meme1.jpg is not found")
    val meme2 = memes["duplicate2.jpg"] ?: error("meme2.jpg is not found")
    imageMatcher.add(meme1.name, meme1)
    val memeMatch = imageMatcher.tryFindDuplicate(meme2)

    assertEquals(memeMatch, meme1.name)
  }
}
