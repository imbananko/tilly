package com.chsdngm.tilly.repository

import com.chsdngm.tilly.model.Image
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface ImageRepository : CrudRepository<Image, String> {
    @Query("select file_id as fileId, hash from image", nativeQuery = true)
    fun findAllHashes(): List<HashedImage>

    @Query("select i.fileId as fileId, i.words as words from Image i where i.words is not null")
    fun findAllTexts(): List<TextedImage>

    interface HashedImage {
        val fileId: String
        val hash: ByteArray
    }

    interface TextedImage {
        val fileId: String
        val words: List<String>
    }
}