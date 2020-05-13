package com.chsdngm.tilly.similarity

import com.github.kilianB.hashAlgorithms.PerceptiveHash
import com.github.kilianB.matcher.persistent.ConsecutiveMatcher
import org.springframework.stereotype.Component
import java.awt.image.BufferedImage
import java.io.File
import javax.annotation.PostConstruct

@Component
class MemeMatcher(private val normalizedHammingDistance: Double = .15) {

  @PostConstruct
  private fun init() = matcher.addImage("0", BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB))

  private val matcher = ConsecutiveMatcher(true).also {
    it.addHashingAlgorithm(PerceptiveHash(128), normalizedHammingDistance, true)
  }

  fun add(fileId: String, image: BufferedImage) = matcher.addImage(fileId, image)

  fun add(fileId: String, image: File) = matcher.addImage(fileId, image)

  fun tryFindDuplicate(imageFile: File): String? =
      matcher.getMatchingImages(imageFile).poll()
          ?.takeIf { it.normalizedHammingDistance < this.normalizedHammingDistance }
          ?.value
}
