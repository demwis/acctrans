package com.demwis.demo.acctrans.server

import com.demwis.demo.acctrans.controller.AccountsTransferHttpHandler
import com.demwis.demo.acctrans.dao.AccTransactionDaoImpl
import com.demwis.demo.acctrans.dao.AccountsDaoImpl
import com.demwis.demo.acctrans.service.AccountServiceImpl
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.undertow.Handlers
import io.undertow.Undertow
import io.undertow.server.handlers.BlockingHandler

/**
 * * Create mechanisms for money transferring: via internal storage, via H2 usage or CQEngine
 * * Create mechanism for filling accounts with data, starting balance
 * * Create mechanism for balance recalculation
 * * Write unit tests on money transfer
 * * Write integration tests
 * * Make server configurable via HOCON
 */
fun main(args: Array<String>) {
    val jsonObjectMapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule())
    val accountsDao = AccountsDaoImpl()
    val accTransactionDao = AccTransactionDaoImpl()
    val accountService =
        AccountServiceImpl(accountsDao, accTransactionDao)
    val accountTransferHttpHandler = AccountsTransferHttpHandler(
        jsonObjectMapper,
        accountService
    )

    val server: Undertow = Undertow.builder()
        .addHttpListener(8080, "localhost")
        .setHandler(Handlers.pathTemplate()
            .add("accounts/transfer", BlockingHandler(accountTransferHttpHandler))
            .add("accounts", BlockingHandler(accountTransferHttpHandler))
        )
        .build()
    server.start()
}

