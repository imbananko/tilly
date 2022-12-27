package com.chsdngm.tilly.similarity

import com.chsdngm.tilly.repository.ImageDao
import com.github.kilianB.hash.Hash
import com.github.kilianB.hashAlgorithms.PerceptiveHash
import com.github.kilianB.matcher.persistent.ConsecutiveMatcher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.awt.image.BufferedImage
import java.io.File
import java.math.BigInteger
import javax.annotation.PostConstruct
import javax.imageio.ImageIO

@Service
class ImageMatcher(
    private val imageDao: ImageDao
) : ConsecutiveMatcher(true) {

    private val log = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    @Suppress("unused")
    fun init() {
        addHashingAlgorithm(mainHashingAlgorithm, normalizedHammingDistance, true)
        addImage("0", BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB))

        imageDao.findAllHashes().forEach {
            addImageInternal(it.key, it.value)
        }
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

    fun calculateHash(file: File): ByteArray =
        mainHashingAlgorithm.hash(ImageIO.read(file.inputStream())).hashValue.toByteArray()

    fun add(image: com.chsdngm.tilly.model.dto.Image) {
        addImageInternal(image.fileId, BigInteger(image.hash))
    }

    fun tryFindDuplicate(imageFile: File): String? =
        getMatchingImages(imageFile).poll()?.takeIf { it.normalizedHammingDistance < normalizedHammingDistance }?.value

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

        fun calculateHash(file: ByteArray): ByteArray =
            mainHashingAlgorithm.hash(ImageIO.read(file.inputStream())).hashValue.toByteArray()
    }
}


