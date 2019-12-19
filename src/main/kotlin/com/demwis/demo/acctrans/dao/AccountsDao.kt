package com.demwis.demo.acctrans.dao

import com.demwis.demo.acctrans.domain.Account
import java.util.concurrent.ConcurrentHashMap

interface AccountsDao {

    fun findAccountById(accId: String): Account?
}

class AccountsDaoImpl: AccountsDao {
    private val accounts =
        ConcurrentHashMap<String, Account>()

    override fun findAccountById(accId: String): Account?  = accounts[accId]
}