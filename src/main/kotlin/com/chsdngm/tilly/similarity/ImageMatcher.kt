package com.chsdngm.tilly.similarity

import com.chsdngm.tilly.model.Image
import com.chsdngm.tilly.repository.ImageRepository
import com.github.kilianB.hashAlgorithms.PerceptiveHash
import com.github.kilianB.matcher.persistent.ConsecutiveMatcher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.awt.image.BufferedImage
import java.io.File
import javax.annotation.PostConstruct
import javax.imageio.ImageIO
import kotlin.system.measureTimeMillis


@Service
class ImageMatcher(private val imageRepository: ImageRepository,
                   private val normalizedHammingDistance: Double = .15) {

  private val log = LoggerFactory.getLogger(javaClass)

  @PostConstruct
  fun init() {
    matcher.addImage("0", BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB))
    loadImages()
  }

  fun loadImages() {
    log.info("Start loading memes into matcher")
    measureTimeMillis {
      imageRepository.findAll().forEach { matcher.addImage(it.fileId, ImageIO.read(it.file.inputStream())) }
    }.also { log.info("Finished loading memes into matcher. took: $it ms") }
  }

  private val matcher = ConsecutiveMatcher(true).also {
    it.addHashingAlgorithm(PerceptiveHash(128), normalizedHammingDistance, true)
  }

  fun add(fileId: String, image: File) {
    imageRepository.save(Image(fileId, image.readBytes()))
    matcher.addImage(fileId, image)
  }

  fun tryFindDuplicate(imageFile: File): String? =
      matcher.getMatchingImages(imageFile).poll()
          ?.takeIf { it.normalizedHammingDistance < this.normalizedHammingDistance }
          ?.value
}
