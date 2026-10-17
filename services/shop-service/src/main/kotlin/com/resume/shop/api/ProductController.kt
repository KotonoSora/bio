package com.resume.shop.api

import com.resume.shop.domain.Product
import com.resume.shop.domain.ProductKind
import com.resume.shop.domain.ProductService
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

data class CreateProductRequest(@field:NotBlank val title: String, val kind: ProductKind, @field:Min(0) val priceVnd: Long)

@RestController
@RequestMapping("/api/v1/products")
class ProductController(private val products: ProductService) {
    @GetMapping("/mine")
    fun mine(auth: Authentication?): List<Product> {
        val userId = auth?.name ?: "dev-user"
        return products.mine(userId)
    }

    @PostMapping
    fun create(auth: Authentication?, @RequestBody req: CreateProductRequest): Product {
        val userId = auth?.name ?: "dev-user"
        return products.create(userId, req.title, req.kind, req.priceVnd).getOrThrow()
    }
}
