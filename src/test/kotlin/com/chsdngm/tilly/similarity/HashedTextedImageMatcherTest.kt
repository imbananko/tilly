package com.chsdngm.tilly.similarity

import com.chsdngm.tilly.model.dto.Image
import com.chsdngm.tilly.repository.ImageDao
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.io.File

class HashedTextedImageMatcherTest {
  private lateinit var imageMatcher: ImageMatcher

  @Suppress("unused")
  companion object {
    @JvmStatic
    val memes = listOf(
      "original_meme.jpg",
      "meme_changed_more.jpg",
      "doctor1.jpg",
      "doctor2.jpg",
      "doctor3.jpg",
      "meme1.jpg",
      "meme2.jpg",
      "duplicate1.jpg",
      "duplicate2.jpg"
    ).associateWith { loadMeme(it) }

    @JvmStatic
    fun imageProvider() = memes.values.stream()

    @JvmStatic
    fun loadMeme(memeName: String) =
        File(this::class.java.classLoader.getResource(memeName)?.file
            ?: error("meme not found: $memeName"))
  }

  @BeforeEach
  fun init() {
    imageMatcher = ImageMatcher(mock(ImageDao::class.java))
    imageMatcher.init()
  }

  @ParameterizedTest
  @MethodSource("imageProvider")
  fun `MemeMatcher should identify equal memes`(file: File) {
    val fileId = file.name

    val image = mock(Image::class.java)
    `when`(image.file).thenReturn(file.readBytes())
    `when`(image.fileId).thenReturn(fileId)
    `when`(image.hash).thenReturn(imageMatcher.calculateHash(file))
    imageMatcher.add(image)

    assertEquals(fileId, imageMatcher.tryFindDuplicate(file))
  }

  @Test
  fun `Almost same memes with a difference in text should be distinguished`() {
    val originalMeme = memes["original_meme.jpg"] ?: error("original_meme.jpg is not found")
    val doctor3 = memes["doctor3.jpg"] ?: error("doctor3.jpg is not found")

    val originalImage = mock(Image::class.java)
    `when`(originalImage.file).thenReturn(originalMeme.readBytes())
    `when`(originalImage.fileId).thenReturn("original_meme.jpg")
    `when`(originalImage.hash).thenReturn(imageMatcher.calculateHash(originalMeme))

    imageMatcher.add(originalImage)
    val memeMatch1 = imageMatcher.tryFindDuplicate(doctor3)
    assertNull(memeMatch1, "Changed meme ${doctor3.name} should be different from original one ${originalMeme.name}")
  }

  // TODO: currently falling
  @Disabled
  @Test
  fun `Same memes with a difference in text should be distinguished`() {
    val doctor1 = memes["doctor1.jpg"] ?: error("doctor1.jpg is not found")
    val doctor2 = memes["doctor2.jpg"] ?: error("doctor2.jpg is not found")

    val image1 = mock(Image::class.java)
    `when`(image1.file).thenReturn(doctor1.readBytes())
    `when`(image1.fileId).thenReturn("meme1.jpg")
    `when`(image1.hash).thenReturn(imageMatcher.calculateHash(doctor1))

    imageMatcher.add(image1)
    val memeMatch1 = imageMatcher.tryFindDuplicate(doctor2)
    assertNull(memeMatch1, "Changed meme ${doctor2.name} should be different from original one ${doctor2.name}")
  }

  @Test
  fun `Meme1 and Meme2 should be the same`() {
    val meme1 = memes["meme1.jpg"] ?: error("meme1.jpg is not found")
    val meme2 = memes["meme2.jpg"] ?: error("meme2.jpg is not found")

    val image1 = mock(Image::class.java)
    `when`(image1.file).thenReturn(meme1.readBytes())
    `when`(image1.fileId).thenReturn("meme1.jpg")
    `when`(image1.hash).thenReturn(imageMatcher.calculateHash(meme1))

    imageMatcher.add(image1)
    val memeMatch = imageMatcher.tryFindDuplicate(meme2)

    assertEquals(memeMatch, meme1.name)
  }

  @Test
  fun `Duplicate1 and Duplicate2 should be the same`() {
    val meme1 = memes["duplicate1.jpg"] ?: error("meme1.jpg is not found")
    val meme2 = memes["duplicate2.jpg"] ?: error("meme2.jpg is not found")

    val image1 = mock(Image::class.java)
    `when`(image1.file).thenReturn(meme1.readBytes())
    `when`(image1.fileId).thenReturn("duplicate1.jpg")
    `when`(image1.hash).thenReturn(imageMatcher.calculateHash(meme1))

    imageMatcher.add(image1)
    val memeMatch = imageMatcher.tryFindDuplicate(meme2)

    assertEquals(memeMatch, meme1.name)
  }
}
