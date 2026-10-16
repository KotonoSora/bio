package com.resume.shop.domain

import com.resume.shop.data.ProductRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ProductService(private val repo: ProductRepository) {

    @Transactional(readOnly = true)
    fun mine(userId: String): List<Product> = repo.findByUserId(userId)

    @Transactional(readOnly = true)
    fun byId(id: String): Result<Product> =
        repo.findById(id).map { Result.success(it) }.orElseGet { Result.failure(NoSuchElementException("product not found")) }

    @Transactional
    fun create(userId: String, title: String, kind: ProductKind, priceVnd: Long): Result<Product> {
        if (priceVnd < 0) return Result.failure(IllegalArgumentException("negative price"))
        val saved = repo.save(Product(id = "prd_" + UUID.randomUUID().toString().take(8), userId = userId, kind = kind, title = title, priceVnd = priceVnd))
        return Result.success(saved)
    }
}
