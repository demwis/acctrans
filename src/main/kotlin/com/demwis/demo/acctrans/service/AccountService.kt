package com.demwis.demo.acctrans.service

import com.demwis.common.DefaultNowProvider
import com.demwis.common.NowProvider
import com.demwis.demo.acctrans.dao.AccountTransactionDao
import com.demwis.demo.acctrans.dao.AccountsDao
import com.demwis.demo.acctrans.domain.Account
import org.slf4j.LoggerFactory
import java.lang.Exception
import java.math.BigDecimal
import java.util.concurrent.ExecutorService

interface AccountService {

    fun transferMoney(accFromId: String, accToId: String, amount: BigDecimal): Long

    fun createAccount(accId: String, negativeBalanceAllowed: Boolean): Account

    fun findAccountById(accId: String): Account?
    fun recalculateBalance()
}

class AccountServiceImpl(private val accountsDao: AccountsDao,
                         private val accountTransactionDao: AccountTransactionDao,
                         private val recalculateBalanceExecutor: ExecutorService,
                         private val nowProvider: NowProvider = DefaultNowProvider
): AccountService {
    private val log = LoggerFactory.getLogger(AccountServiceImpl::class.java)

    override fun transferMoney(accFromId: String, accToId: String, amount: BigDecimal): Long {
        val accountFrom = accountsDao.findAccountById(accFromId) ?: throw IllegalArgumentException("Account with id $accFromId doesn't exist")
        val accountTo = accountsDao.findAccountById(accToId) ?: throw IllegalArgumentException("Account with id $accToId doesn't exist")

        return accountTransactionDao.transferMoney(accountFrom, accountTo, amount)
    }

    override fun createAccount(accId: String, negativeBalanceAllowed: Boolean): Account =
        accountsDao.createAccount(accId, negativeBalanceAllowed)

    override fun findAccountById(accId: String): Account? = accountsDao.findAccountById(accId)

    override fun recalculateBalance() {
        val yesterday = nowProvider.localDate.minusDays(1)
        log.debug("Balance recalculation job started for date {}", yesterday)
        accountsDao.getAllAccountsStream().forEach {
            recalculateBalanceExecutor.submit {
                try {
                    it.lock.lock()
                    try {
                        if (yesterday.isAfter(it.balanceLastUpdateDate)) {
                            log.debug("Balance recalculation for account {} started for date {}", it, yesterday)
                            val daysBalance = accountTransactionDao.getAccountTransactionBalanceForDays(
                                it,
                                it.balanceLastUpdateDate,
                                yesterday,
                                false
                            )
                            it.eodBalance += daysBalance
                            it.balanceLastUpdateDate = yesterday
                            log.debug("Balance recalculation for account {} for date {} was finished with days balance {}", it, yesterday, daysBalance)
                        } else {
                            log.debug("Balance recalculation for account {} for date {} was skipped", it, yesterday)
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