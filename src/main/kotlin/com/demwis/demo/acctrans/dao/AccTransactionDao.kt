package com.demwis.demo.acctrans.dao

import com.demwis.common.DefaultNowProvider
import com.demwis.common.NowProvider
import com.demwis.demo.acctrans.domain.Account
import com.demwis.demo.acctrans.domain.AccountTransaction
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.atomic.AtomicLong

interface AccTransactionDao {

    fun transferMoney(accountFrom: Account, accountTo: Account, amount: BigDecimal): Long

    fun getAccountTransactionBalanceForDays(account: Account, fromDate: LocalDate, toDate: LocalDate,
                                            fromInclusive: Boolean = true, toInclusive: Boolean = true): BigDecimal
}

class AccTransactionDaoImpl(private val nowProvider: NowProvider = DefaultNowProvider): AccTransactionDao {
    private val transactionIdSequencer: AtomicLong =
        AtomicLong()

    private val accTransactionsById =
        ConcurrentHashMap<Long, List<AccountTransaction>>()
    private val accTransactionsByAccIdAndDate =
        ConcurrentHashMap<String, ConcurrentSkipListMap<LocalDate, Queue<AccountTransaction>>>()

    override fun transferMoney(accountFrom: Account, accountTo: Account, amount: BigDecimal): Long {
        val now = nowProvider.localDateTime
        validateBalanceReduction(accountFrom, amount)
        accountFrom.lock.lock()
        try {
            validateBalanceReduction(accountFrom, amount)
            return newTransaction(accountFrom, accountTo, amount, now)
        } finally {
            accountFrom.lock.unlock()
        }
    }

    private fun newTransaction(accountFrom: Account, accountTo: Account, amount: BigDecimal, now: LocalDateTime): Long {
        val transactionId = transactionIdSequencer.incrementAndGet()
        val transactionFrom = AccountTransaction(
            transactionId,
            accountFrom.accId,
            amount.negate(),
            now
        )
        val transactionTo = AccountTransaction(
            transactionId,
            accountTo.accId,
            amount,
            now
        )
        val today = now.toLocalDate()
        accTransactionsById[transactionId] = listOf(transactionFrom, transactionTo)
        accTransactionsByAccIdAndDate
            .computeIfAbsent(accountFrom.accId) { ConcurrentSkipListMap() }
            .computeIfAbsent(today) { ConcurrentLinkedQueue() }
            .offer(transactionFrom)
        accTransactionsByAccIdAndDate
            .computeIfAbsent(accountTo.accId) { ConcurrentSkipListMap() }
            .computeIfAbsent(today) { ConcurrentLinkedQueue() }
            .offer(transactionTo)
        return transactionId
    }

    private fun validateBalanceReduction(account: Account, reduceAmount: BigDecimal) {
        if (account.negativeBalanceAllowed) {
            return
        }
        val accFromBalance = calcAccountBalance(account)
        if (accFromBalance.minus(reduceAmount) < BigDecimal.ZERO) {
            throw IllegalArgumentException("Insufficient balance")
        }
    }

    private fun calcAccountBalance(account: Account) =
        account.eodBalance +
                (accTransactionsByAccIdAndDate[account.accId]
                    ?.tailMap(account.balanceLastUpdateDate)?.values?.stream()
                    ?.flatMap { it.stream() }
                    ?.map { it.amount }
                    ?.reduce(BigDecimal.ZERO) { a, c -> a.add(c) }
                    ?: BigDecimal.ZERO)

    override fun getAccountTransactionBalanceForDays(account: Account,
                                                     fromDate: LocalDate,
                                                     toDate: LocalDate,
                                                     fromInclusive: Boolean,
                                                     toInclusive: Boolean): BigDecimal =
        accTransactionsByAccIdAndDate[account.accId]
            ?.subMap(fromDate, fromInclusive, toDate, toInclusive)?.values?.stream()
            ?.flatMap { it.stream() }
            ?.map { it.amount }
            ?.reduce(BigDecimal.ZERO) { a, c -> a.add(c) }
            ?: BigDecimal.ZERO

}