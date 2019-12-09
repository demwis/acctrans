package com.demwis.demo.acctrans.server

import io.undertow.Handlers
import io.undertow.Undertow
import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
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

    val server: Undertow = Undertow.builder()
        .addHttpListener(8080, "localhost")
        .setHandler(Handlers.pathTemplate()
            .add("accounts/transfer", AccountsTransferHandler()))
        .build()
    server.start()
}

class AccountsTransferHandler: HttpHandler {
    private val accounts = ConcurrentHashMap<String, Account>()
    private val accTransactionsById = ConcurrentHashMap<Long, List<AccountTransaction>>()
    private val accTransactionsByAccId = ConcurrentHashMap<String, List<AccountTransaction>>()
    private val transactionIdSequencer: AtomicLong = AtomicLong()

    override fun handleRequest(exchange: HttpServerExchange) {
        if (exchange.requestMethod != HttpString("POST")) {
            throw UnsupportedOperationException("Operation ${exchange.requestMethod} isn't supported")
        }
        val accFromId: String = exchange.queryParameters["accFrom"]?.first ?: throw IllegalArgumentException("accFrom parameter is required")
        val accToId: String = exchange.queryParameters["accTo"]?.first ?: throw IllegalArgumentException("accTo parameter is required")
        val amount: BigDecimal = exchange.queryParameters["amount"]?.first?.toBigDecimal() ?: throw IllegalArgumentException("Amount parameter is required")

        val accountFrom = accounts[accFromId] ?: throw IllegalArgumentException("Account with id $accFromId doesn't exist")
        val accountTo = accounts[accToId] ?: throw IllegalArgumentException("Account with id $accToId doesn't exist")

        val accFromBalance = calcAccountBalance(accountFrom)
        if (accFromBalance.minus(amount) < BigDecimal.ZERO) {
            throw IllegalArgumentException("Insufficient balance")
        }
        val transactionId = transactionIdSequencer.incrementAndGet()
        accountFrom.lock.lock()
        try {
            if ()
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

data class AccountTransaction(val transactionId: String, val accId: String, val amount: BigDecimal) {

}