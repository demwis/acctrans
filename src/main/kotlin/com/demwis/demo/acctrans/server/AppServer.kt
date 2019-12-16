package com.demwis.demo.acctrans.server

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.undertow.Handlers
import io.undertow.Undertow
import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
import io.undertow.server.handlers.BlockingHandler
import io.undertow.util.Headers
import io.undertow.util.HttpString
import java.lang.IllegalArgumentException
import java.lang.UnsupportedOperationException
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import java.util.stream.Collectors

fun main(args: Array<String>) {
    val jsonObjectMapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule())
    val accountsDao = AccountsDaoImpl()
    val accTransactionDao = AccTransactionDaoImpl()
    val accountService = AccountServiceImpl(accountsDao, accTransactionDao)
    val accountTransferHandler = AccountsTransferHandler(jsonObjectMapper, accountService)

    val server: Undertow = Undertow.builder()
        .addHttpListener(8080, "localhost")
        .setHandler(Handlers.pathTemplate()
            .add("accounts/transfer", BlockingHandler(accountTransferHandler)))
        .build()
    server.start()
}

class AccountsTransferHandler(private val jsonObjectMapper: ObjectMapper,
                              private val accountService: AccountService): HttpHandler {

    override fun handleRequest(exchange: HttpServerExchange) {
        if (exchange.requestMethod != HttpString("POST")) {
            throw UnsupportedOperationException("Operation ${exchange.requestMethod} isn't supported")
        }
        val request = jsonObjectMapper.readValue(exchange.inputStream, AccTransferRequest::class.java)
//        val accFromId: String = exchange.queryParameters["accFrom"]?.first ?: throw IllegalArgumentException("accFrom parameter is required")
//        val accToId: String = exchange.queryParameters["accTo"]?.first ?: throw IllegalArgumentException("accTo parameter is required")
//        val amount: BigDecimal = exchange.queryParameters["amount"]?.first?.toBigDecimal() ?: throw IllegalArgumentException("Amount parameter is required")
        val transactionId = accountService.transferAssets(request.accFromId, request.accToId, request.amount)

        val response = AccTransferResponse(request, transactionId)
        exchange.responseSender.send(jsonObjectMapper.writeValueAsString(response))
//        exchange.endExchange()
    }
}

interface AccountService {

    fun transferAssets(accFromId: String, accToId: String, amount: BigDecimal): Long
}

class AccountServiceImpl(private val accountsDao: AccountsDao,
                         private val accTransactionDao: AccTransactionDao): AccountService {

    override fun transferAssets(accFromId: String, accToId: String, amount: BigDecimal): Long {
        val accountFrom = accountsDao.findAccountById(accFromId) ?: throw IllegalArgumentException("Account with id $accFromId doesn't exist")
        val accountTo = accountsDao.findAccountById(accToId) ?: throw IllegalArgumentException("Account with id $accToId doesn't exist")

        return accTransactionDao.transferMoney(accountFrom, accountTo, amount)
    }

}

interface AccountsDao {

    fun findAccountById(accId: String): Account?
}

class AccountsDaoImpl: AccountsDao {
    private val accounts = ConcurrentHashMap<String, Account>()

    override fun findAccountById(accId: String): Account?  = accounts[accId]
}

interface AccTransactionDao {

    fun transferMoney(accountFrom: Account, accountTo: Account, amount: BigDecimal): Long
}

class AccTransactionDaoImpl(): AccTransactionDao {
    private val transactionIdSequencer: AtomicLong = AtomicLong()

    private val accTransactionsById = ConcurrentHashMap<Long, List<AccountTransaction>>()
    private val accTransactionsByAccId = ConcurrentHashMap<String, List<AccountTransaction>>()

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
        accTransactionsById[transactionId] = listOf(
            AccountTransaction(transactionId, accountFrom.accId, amount.negate()),
            AccountTransaction(transactionId, accountTo.accId, amount))
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

data class Account(val accId: String, @Volatile var eodBalance: BigDecimal) {
    val lock = ReentrantLock()
}

data class AccountTransaction(val transactionId: Long, val accId: String, val amount: BigDecimal)

data class AccTransferRequest
    @JsonCreator constructor(val accFromId: String, val accToId: String, val amount: BigDecimal)

data class AccTransferResponse(val accFromId: String, val accToId: String, val amount: BigDecimal, val transactionId: Long) {
    constructor(request: AccTransferRequest, transactionId: Long): this(request.accFromId, request.accToId, request.amount, transactionId)
}
