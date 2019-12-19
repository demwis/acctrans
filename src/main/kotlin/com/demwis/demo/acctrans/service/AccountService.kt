package com.demwis.demo.acctrans.service

import com.demwis.demo.acctrans.dao.AccTransactionDao
import com.demwis.demo.acctrans.dao.AccountsDao
import java.math.BigDecimal

interface AccountService {

    fun transferAssets(accFromId: String, accToId: String, amount: BigDecimal): Long
}

class AccountServiceImpl(private val accountsDao: AccountsDao,
                         private val accTransactionDao: AccTransactionDao
): AccountService {

    override fun transferAssets(accFromId: String, accToId: String, amount: BigDecimal): Long {
        val accountFrom = accountsDao.findAccountById(accFromId) ?: throw IllegalArgumentException("Account with id $accFromId doesn't exist")
        val accountTo = accountsDao.findAccountById(accToId) ?: throw IllegalArgumentException("Account with id $accToId doesn't exist")

        return accTransactionDao.transferMoney(accountFrom, accountTo, amount)
    }

}