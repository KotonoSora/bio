package com.resume.account.domain

import com.resume.account.data.AccountRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AccountService(private val repo: AccountRepository) {
    @Transactional(readOnly = true)
    fun me(userId: String): Result<Account> =
        repo.findByUserId(userId)?.let { Result.success(it) }
            ?: Result.failure(NoSuchElementException("account not found"))
}
