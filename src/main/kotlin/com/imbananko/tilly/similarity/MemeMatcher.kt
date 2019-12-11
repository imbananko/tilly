package com.imbananko.tilly.similarity

import com.github.kilianB.hashAlgorithms.PerceptiveHash
import com.github.kilianB.matcher.persistent.ConsecutiveMatcher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File

@Component
class MemeMatcher {
  private val log = LoggerFactory.getLogger(javaClass)

  private val normalizedHammingDistance = .02

  private val matcher = ConsecutiveMatcher(true).also {
    it.addHashingAlgorithm(PerceptiveHash(128), normalizedHammingDistance, true)
  }

  @Synchronized
  fun addMeme(fileId: String, imageFile: File) = matcher.addImage(fileId, imageFile)

  fun checkMemeExists(memeId: String, imageFile: File): String? = runCatching {
    matcher.getMatchingImages(imageFile).poll()
        ?.takeIf { it.normalizedHammingDistance < this.normalizedHammingDistance }
        ?.value
        .also {
          if (it == null) matcher.addImage(memeId, imageFile)
          else log.info("Meme $memeId is not unique")
        }
  }.onFailure {
    log.error("Failed to check if meme $memeId unique. Exception=", it)
  }.getOrNull()
}