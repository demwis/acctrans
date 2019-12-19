package com.demwis.demo.acctrans.dao

import com.demwis.demo.acctrans.domain.Account
import com.demwis.demo.acctrans.domain.AccountTransaction
import java.math.BigDecimal
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

interface AccTransactionDao {

    fun transferMoney(accountFrom: Account, accountTo: Account, amount: BigDecimal): Long
}

class AccTransactionDaoImpl(): AccTransactionDao {
    private val transactionIdSequencer: AtomicLong =
        AtomicLong()

    private val accTransactionsById =
        ConcurrentHashMap<Long, List<AccountTransaction>>()
    private val accTransactionsByAccId =
        ConcurrentHashMap<String, Queue<AccountTransaction>>()

    override fun transferMoney(accountFrom: Account, accountTo: Account, amount: BigDecimal): Long {
        validateBalanceReduction(accountFrom, amount)
        accountFrom.lock.lock()
        try {
            validateBalanceReduction(accountFrom, amount)
            return newTransaction(accountFrom, accountTo, amount)
        } finally {
            accountFrom.lock.unlock()
        }
    }

    private fun newTransaction(accountFrom: Account, accountTo: Account, amount: BigDecimal): Long {
        val transactionId = transactionIdSequencer.incrementAndGet()
        val transactionFrom = AccountTransaction(
            transactionId,
            accountFrom.accId,
            amount.negate()
        )
        val transactionTo = AccountTransaction(
            transactionId,
            accountTo.accId,
            amount
        )
        accTransactionsById[transactionId] = listOf(transactionFrom, transactionTo)
        accTransactionsByAccId.computeIfAbsent(accountFrom.accId) { ConcurrentLinkedQueue() }
            .offer(transactionFrom)
        accTransactionsByAccId.computeIfAbsent(accountTo.accId) { ConcurrentLinkedQueue() }
            .offer(transactionTo)
        return transactionId
    }

    private fun validateBalanceReduction(account: Account, reduceAmount: BigDecimal) {
        val accFromBalance = calcAccountBalance(account)
        if (accFromBalance.minus(reduceAmount) < BigDecimal.ZERO) {
            throw IllegalArgumentException("Insufficient balance")
        }
    }

    private fun calcAccountBalance(account: Account) =
        account.eodBalance +
                (accTransactionsByAccId[account.accId]?.stream()
                    ?.map { it.amount }
                    ?.reduce(BigDecimal.ZERO) { a, c -> a.add(c) }
                    ?: BigDecimal.ZERO)

}