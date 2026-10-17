package com.resume.shop.api

import com.resume.shop.domain.Product
import com.resume.shop.domain.ProductService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Mesh-only catalog read (storefront reads go through the public API). */
@RestController
@RequestMapping("/internal/v1/products")
class InternalProductController(private val products: ProductService) {
    @GetMapping("/{id}")
    fun byId(@PathVariable id: String): Product = products.byId(id).getOrThrow()
}
