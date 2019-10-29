package com.imbananko.tilly.similarity

import com.github.kilianB.hashAlgorithms.AverageHash
import com.github.kilianB.hashAlgorithms.PerceptiveHash
import com.github.kilianB.matcher.persistent.ConsecutiveMatcher
import io.vavr.control.Option
import io.vavr.control.Try
import org.springframework.stereotype.Component
import java.io.File

@Component
class MemeMatcher {
    private val matcher = ConsecutiveMatcher(true).also {
        it.addHashingAlgorithm(AverageHash(128), .3)
        it.addHashingAlgorithm(PerceptiveHash(128), .3)
    }

    fun addMeme(fileId: String, imageFile: File) = matcher.addImage(fileId, imageFile)

    fun checkMemeExists(memeId: String, imageFile: File): Try<Option<String?>> = Try.of {
        Option.of(matcher.getMatchingImages(imageFile).poll()?.value).also {
            if (it.isEmpty) matcher.addImage(memeId, imageFile)
        }
    }
}