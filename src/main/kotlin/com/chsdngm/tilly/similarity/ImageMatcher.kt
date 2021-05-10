package com.chsdngm.tilly.similarity

import com.chsdngm.tilly.repository.ImageRepository
import com.chsdngm.tilly.utility.DocumentPage
import com.github.kilianB.hash.Hash
import com.github.kilianB.hashAlgorithms.PerceptiveHash
import com.github.kilianB.matcher.persistent.ConsecutiveMatcher
import opennlp.tools.stemmer.snowball.SnowballStemmer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import smile.nlp.SimpleCorpus
import smile.nlp.Text
import smile.nlp.relevance.BM25
import java.awt.image.BufferedImage
import java.io.File
import java.math.BigInteger
import javax.annotation.PostConstruct
import javax.imageio.ImageIO
import kotlin.system.measureTimeMillis


@Service
class ImageMatcher(private val imageRepository: ImageRepository) : ConsecutiveMatcher(true) {

  private val log = LoggerFactory.getLogger(javaClass)

  private val corpus: SimpleCorpus = SimpleCorpus()

  @PostConstruct
  @Suppress("unused")
  fun init() {
    addHashingAlgorithm(mainHashingAlgorithm, normalizedHammingDistance, true)
    addImage("0", BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB))

    imageRepository.findAllHashes().forEach {
      addImageInternal(it.fileId, BigInteger(it.hash))
    }

    imageRepository.findAllTexts().forEach { image ->
      corpus.add(Text(image.fileId, "", image.words.joinToString()))
    }
  }

  fun find(text: String, page: DocumentPage): List<Text> {
//    val stemmedText = text.split(' ')
//      .map { SnowballStemmer(SnowballStemmer.ALGORITHM.RUSSIAN).stem(it).toString() }
//      .toTypedArray()

    return corpus.search(BM25(), text.split(' ').toTypedArray())
      .asSequence()
      .drop(page.pageNumber * page.pageSize)
      .map { it.text }
      .toList()
  }

  private fun addImageInternal(uniqueId: String, computedHash: BigInteger) {
    if (addedImages.contains(uniqueId)) {
      log.info("An image with uniqueId already exists. Skip request")
    }

    val hash: Hash = mainHashingAlgorithm.hash(computedHash)
    binTreeMap[mainHashingAlgorithm]!!.addHash(hash, uniqueId)
    cachedHashes[mainHashingAlgorithm]!![uniqueId] = hash

    addedImages.add(uniqueId)
  }

  fun calculateHash(file: File): ByteArray = mainHashingAlgorithm.hash(ImageIO.read(file.inputStream())).hashValue.toByteArray()

  fun add(fileId: String, file: File) = addImageInternal(fileId, mainHashingAlgorithm.hash(ImageIO.read(file.inputStream())).hashValue)

  fun tryFindDuplicate(imageFile: File): String? =
      getMatchingImages(imageFile).poll()
          ?.takeIf { it.normalizedHammingDistance < normalizedHammingDistance }
          ?.value

  companion object {
    private const val normalizedHammingDistance: Double = .15

    private val mainHashingAlgorithm = object : PerceptiveHash(128) {
      fun hash(hashValue: BigInteger): Hash {
        this.immutableState = true

        if (keyResolution < 0) {
          keyResolution = bitResolution
        }

        return Hash(hashValue, keyResolution, algorithmId())
      }
    }

    fun calculateHash(file: ByteArray): ByteArray = mainHashingAlgorithm.hash(ImageIO.read(file.inputStream())).hashValue.toByteArray()
  }
}


