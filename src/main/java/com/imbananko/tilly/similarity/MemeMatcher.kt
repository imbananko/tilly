package com.imbananko.tilly.similarity

import com.github.kilianB.hashAlgorithms.PerceptiveHash
import com.github.kilianB.matcher.persistent.ConsecutiveMatcher
import io.vavr.control.Option
import io.vavr.control.Try
import org.springframework.stereotype.Component
import java.io.File

@Component
class MemeMatcher {
    private val normalizedHammingDistance = .02
    private val matcher = ConsecutiveMatcher(true).also {
        it.addHashingAlgorithm(PerceptiveHash(128), .02, true)
    }

    fun addMeme(fileId: String, imageFile: File) = matcher.addImage(fileId, imageFile)

    fun checkMemeExists(memeId: String, imageFile: File): Try<Option<String>> = Try.of {
        Option.of(
                matcher.getMatchingImages(imageFile).poll()
                        ?.takeIf { it.normalizedHammingDistance < this.normalizedHammingDistance }
                        ?.value!!
        ).also { if (it.isEmpty) matcher.addImage(memeId, imageFile) }
    }
}