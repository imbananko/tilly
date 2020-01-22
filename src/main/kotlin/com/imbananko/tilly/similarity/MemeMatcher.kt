package com.imbananko.tilly.similarity

import com.github.kilianB.hashAlgorithms.PerceptiveHash
import com.github.kilianB.matcher.persistent.ConsecutiveMatcher
import org.springframework.stereotype.Component
import java.io.File

@Component
class MemeMatcher {
  private val normalizedHammingDistance = .02

  private val matcher = ConsecutiveMatcher(true).also {
    it.addHashingAlgorithm(PerceptiveHash(128), normalizedHammingDistance, true)
  }

  @Synchronized
  fun addMeme(fileId: String, imageFile: File) = matcher.addImage(fileId, imageFile)

  fun tryFindDuplicate(imageFile: File): String? =
      matcher.getMatchingImages(imageFile).poll()
          ?.takeIf { it.normalizedHammingDistance < this.normalizedHammingDistance }
          ?.value
}