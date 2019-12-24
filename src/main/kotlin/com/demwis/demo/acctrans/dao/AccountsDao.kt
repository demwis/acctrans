package com.demwis.demo.acctrans.dao

import com.demwis.common.DefaultNowProvider
import com.demwis.common.NowProvider
import com.demwis.demo.acctrans.domain.Account
import org.slf4j.LoggerFactory
import java.lang.RuntimeException
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Stream

interface AccountsDao {

    fun findAccountById(accId: String): Account?

    fun createAccount(accId: String, negativeBalanceAllowed: Boolean): Account

    fun getAllAccountsStream(): Stream<Account>
}

class AccountsDaoImpl(private val nowProvider: NowProvider = DefaultNowProvider): AccountsDao {
    private val log = LoggerFactory.getLogger(AccountsDaoImpl::class.java)
    private val accounts =
        ConcurrentHashMap<String, Account>()

    override fun findAccountById(accId: String): Account? {
        val result = accounts[accId]
        if (result != null)
            log.debug("Account {} was found by id", result)
        else
            log.debug("No account was found by id {}", accId)
        return result
    }

    override fun createAccount(accId: String, negativeBalanceAllowed: Boolean): Account {
        val account = Account(
            accId = accId,
            negativeBalanceAllowed = negativeBalanceAllowed,
            balanceLastUpdateDate = nowProvider.localDate)
        if (accounts.putIfAbsent(accId, account) != null)
            // TODO process exception
            throw DuplicatedRecordException("Account with id $accId already exists")
        log.debug("Account {} was created succesfully", account)
        return account
    }

    override fun getAllAccountsStream(): Stream<Account> = accounts.values.stream()
}

// TODO Move to a separate package
class DuplicatedRecordException : RuntimeException {
    constructor(message: String, cause: Throwable): super(message, cause)
    constructor(message: String): super(message)
}