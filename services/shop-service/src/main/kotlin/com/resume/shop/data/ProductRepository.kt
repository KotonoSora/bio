package com.resume.shop.data

import com.resume.shop.domain.Product
import org.springframework.data.jpa.repository.JpaRepository

interface ProductRepository : JpaRepository<Product, String> {
    fun findByUserId(userId: String): List<Product>
}
