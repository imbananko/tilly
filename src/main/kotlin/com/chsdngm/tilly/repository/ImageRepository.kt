package com.chsdngm.tilly.repository

import com.chsdngm.tilly.model.Image
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface ImageRepository : CrudRepository<Image, String>