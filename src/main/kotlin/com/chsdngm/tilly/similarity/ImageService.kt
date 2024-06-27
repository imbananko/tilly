package com.chsdngm.tilly.similarity

import com.chsdngm.tilly.model.MemeUpdate
import com.chsdngm.tilly.model.dto.Image
import com.chsdngm.tilly.repository.ImageDao
import org.springframework.stereotype.Service
import java.io.File

@Service
class ImageService(private val imageDao: ImageDao,
                   private val imageTextRecognizer: ImageTextRecognizer,
                   private val imageMatcher: ImageMatcher) {

    suspend fun handleImage(update: MemeUpdate, file: File) {
        val analyzingResults = imageTextRecognizer.analyze(file, update.fileId)
        val image = Image(
            update.fileId,
            file.readBytes(),
            hash = imageMatcher.calculateHash(file),
            rawText = analyzingResults?.words,
            rawLabels = analyzingResults?.labels
        )

        imageDao.insert(image)
        imageMatcher.add(image)
    }

    fun tryFindDuplicate(file: File): String? =
        imageMatcher.tryFindDuplicate(file)
}