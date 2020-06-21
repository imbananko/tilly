package com.chsdngm.tilly.similarity

import com.github.kilianB.hashAlgorithms.PerceptiveHash
import com.github.kilianB.matcher.persistent.ConsecutiveMatcher
import net.sourceforge.tess4j.ITesseract
import net.sourceforge.tess4j.Tesseract
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.awt.image.BufferedImage
import java.io.File


@Component
class MemeMatcher(private val normalizedHammingDistance: Double = .15) {

  private var tesseract: ITesseract = Tesseract()
  private val logger = LoggerFactory.getLogger(javaClass)

  private val matcher = ConsecutiveMatcher(true).also {
    it.addHashingAlgorithm(PerceptiveHash(128), normalizedHammingDistance, true)
  }

  init {
    tesseract.setLanguage("rus")
    tesseract.setDatapath(this.javaClass.getResource("/tessdata").toURI().path)
    matcher.addImage("0", BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB))
  }

  fun add(fileId: String, image: BufferedImage) = matcher.addImage(fileId, image)

  fun add(fileId: String, image: File) = matcher.addImage(fileId, image)

  fun tryFindDuplicate(imageFile: File): String? =
      matcher.getMatchingImages(imageFile).poll()
          ?.takeIf { it.normalizedHammingDistance < this.normalizedHammingDistance }
          ?.value

  fun getText(image: File): String? = runCatching {
    tesseract.doOCR(image)
  }.getOrElse { ex ->
    logger.error("can't recognize file because of error", ex)
    null
  }
}
