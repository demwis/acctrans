package com.demwis.demo.acctrans.service

import com.demwis.common.DefaultNowProvider
import com.demwis.common.NowProvider
import com.demwis.demo.acctrans.dao.AccTransactionDao
import com.demwis.demo.acctrans.dao.AccountsDao
import com.demwis.demo.acctrans.domain.Account
import org.slf4j.LoggerFactory
import java.lang.Exception
import java.math.BigDecimal
import java.util.concurrent.ExecutorService

interface AccountService {

    fun transferAssets(accFromId: String, accToId: String, amount: BigDecimal): Long

    fun createAccount(accId: String, negativeBalanceAllowed: Boolean): Account

    fun findAccountById(accId: String): Account?
    fun recalculateBalance()
}

class AccountServiceImpl(private val accountsDao: AccountsDao,
                         private val accTransactionDao: AccTransactionDao,
                         private val recalculateBalanceExecutor: ExecutorService,
                         private val nowProvider: NowProvider = DefaultNowProvider
): AccountService {
    private val log = LoggerFactory.getLogger(AccountServiceImpl::class.java)

    override fun transferAssets(accFromId: String, accToId: String, amount: BigDecimal): Long {
        val accountFrom = accountsDao.findAccountById(accFromId) ?: throw IllegalArgumentException("Account with id $accFromId doesn't exist")
        val accountTo = accountsDao.findAccountById(accToId) ?: throw IllegalArgumentException("Account with id $accToId doesn't exist")

        return accTransactionDao.transferMoney(accountFrom, accountTo, amount)
    }

    override fun createAccount(accId: String, negativeBalanceAllowed: Boolean): Account =
        accountsDao.createAccount(accId, negativeBalanceAllowed)

    override fun findAccountById(accId: String): Account? = accountsDao.findAccountById(accId)

    override fun recalculateBalance() {
        val yesterday = nowProvider.localDate.minusDays(1)
        accountsDao.getAllAccountsStream().forEach {
            recalculateBalanceExecutor.submit {
                try {
                    it.lock.lock()
                    try {
                        if (yesterday.isAfter(it.balanceLastUpdateDate)) {
                            val daysBalance = accTransactionDao.getAccountTransactionBalanceForDays(
                                it,
                                it.balanceLastUpdateDate,
                                yesterday,
                                false
                            )
                            it.eodBalance += daysBalance
                            it.balanceLastUpdateDate = yesterday
                        }
                    } finally {
                        it.lock.unlock()
                    }
                } catch (e: Exception) {
                    log.error("Can't udpate EOD balance for account {}, yesterday {}", it, yesterday)
                }
            }
        }
    }
}