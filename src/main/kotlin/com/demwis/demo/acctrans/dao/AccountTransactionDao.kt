package com.demwis.demo.acctrans.dao

import com.demwis.common.DefaultNowProvider
import com.demwis.common.NowProvider
import com.demwis.demo.acctrans.domain.Account
import com.demwis.demo.acctrans.domain.AccountTransaction
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ConcurrentNavigableMap
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.atomic.AtomicLong

interface AccountTransactionDao {

    fun transferMoney(accountFrom: Account, accountTo: Account, amount: BigDecimal): Long

    fun getAccountTransactionBalanceForDays(account: Account, fromDate: LocalDate, toDate: LocalDate,
                                            fromInclusive: Boolean = true, toInclusive: Boolean = true): BigDecimal
}

class AccountTransactionDaoImpl
    @JvmOverloads constructor(private val nowProvider: NowProvider = DefaultNowProvider,
                            transactionIdSequencerSupplier: () -> AtomicLong = { AtomicLong() }): AccountTransactionDao {
    private val log = LoggerFactory.getLogger(AccountTransactionDaoImpl::class.java)

    private val transactionIdSequencer: AtomicLong = transactionIdSequencerSupplier()

    private val accTransactionsById =
        ConcurrentHashMap<Long, List<AccountTransaction>>()
    private val accTransactionsByAccIdAndDate =
        ConcurrentHashMap<String, ConcurrentNavigableMap<LocalDate, Queue<AccountTransaction>>>()

    override fun transferMoney(accountFrom: Account, accountTo: Account, amount: BigDecimal): Long {
        if (accountFrom.accId == accountTo.accId) {
            throw IllegalArgumentException("Can't transfer money between accounts with the same id. from - $accountFrom, to - $accountTo, amount=$amount")
        }
        if (amount <= BigDecimal.ZERO) {
            throw IllegalArgumentException("Can't transfer not positive amount[$amount] of money. from - $accountFrom, to - $accountTo")
        }
        val now = nowProvider.localDateTime
        log.debug("Current time is {} for transfer money: accountFrom {}, accountTo {}, amount {}", now, accountFrom, accountTo, amount)
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
        log.debug("Transactions from {} and to {} were generated for accountFrom {}, accountTo {}, amount {}",
            transactionFrom, transactionTo, accountFrom, accountTo, amount)
        return transactionId
    }

    private fun validateBalanceReduction(account: Account, reduceAmount: BigDecimal) {
        if (account.negativeBalanceAllowed) {
            log.debug("Account's {} balance is valid as negative balance is allowed for it", account)
            return
        }
        val accFromBalance = calcAccountBalance(account)
        if (accFromBalance.minus(reduceAmount) < BigDecimal.ZERO) {
            throw IllegalArgumentException("$account with current balance of [$accFromBalance] has insufficient funds to transfer $reduceAmount")
        }
        log.debug("Account's {} balance is valid. Calculated balance {}, amount to subtract {}", account, accFromBalance, reduceAmount)
    }

    private fun calcAccountBalance(account: Account): BigDecimal {
        val result = account.eodBalance +
                (accTransactionsByAccIdAndDate[account.accId]
                    ?.tailMap(account.balanceLastUpdateDate)?.values?.stream()
                    ?.flatMap { it.stream() }
                    ?.map { it.amount }
                    ?.reduce(BigDecimal.ZERO) { a, c -> a.add(c) }
                    ?: BigDecimal.ZERO)
        log.debug("Balance {} was calculated for account {}", result, account)
        return result
    }

    override fun getAccountTransactionBalanceForDays(account: Account,
                                                     fromDate: LocalDate,
                                                     toDate: LocalDate,
                                                     fromInclusive: Boolean,
                                                     toInclusive: Boolean): BigDecimal {
        if (!fromDate.isBefore(toDate))
            throw IllegalArgumentException("fromDate[$fromDate] must be earlier than toDate[$toDate]")
        val result = accTransactionsByAccIdAndDate[account.accId]
            ?.subMap(fromDate, fromInclusive, toDate, toInclusive)?.values?.stream()
            ?.flatMap { it.stream() }
            ?.map { it.amount }
            ?.reduce(BigDecimal.ZERO) { a, c -> a.add(c) }
            ?: BigDecimal.ZERO
        log.debug("Balance {} was calculated for account {}, days from [{}, inc={}] to [{}, inc={}]", result, account, fromDate, fromInclusive, toDate, toInclusive)
        return result
    }

}