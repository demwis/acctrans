package com.demwis.demo.acctrans.controller

import com.demwis.demo.acctrans.domain.AccTransferRequest
import com.demwis.demo.acctrans.domain.AccTransferResponse
import com.demwis.demo.acctrans.service.AccountService
import com.fasterxml.jackson.databind.ObjectMapper
import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
import io.undertow.util.HttpString

class AccountsTransferHttpHandler(private val jsonObjectMapper: ObjectMapper,
                                  private val accountService: AccountService
): HttpHandler {

    override fun handleRequest(exchange: HttpServerExchange) {
        if (exchange.requestMethod != HttpString("POST")) {
            // TODO 405 Method Not Allowed
            exchange.statusCode = 405
            exchange.reasonPhrase = "Method Not Allowed"
//            exchange.endExchange()
            return
//            throw UnsupportedOperationException("Operation ${exchange.requestMethod} isn't supported")
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