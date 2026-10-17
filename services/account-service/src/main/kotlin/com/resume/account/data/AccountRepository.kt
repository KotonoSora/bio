package com.resume.account.data

import com.resume.account.domain.Account
import org.springframework.data.jpa.repository.JpaRepository

interface AccountRepository : JpaRepository<Account, String> {
    fun findByUserId(userId: String): Account?
}
