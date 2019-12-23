package com.demwis.demo.acctrans.controller

import com.demwis.demo.acctrans.domain.AccCreateRequest
import com.demwis.demo.acctrans.domain.AccGetResponse
import com.demwis.demo.acctrans.service.AccountService
import com.fasterxml.jackson.databind.ObjectMapper
import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
import io.undertow.util.HttpString

class AccountsHttpHandler(private val jsonObjectMapper: ObjectMapper,
                          private val accountService: AccountService
): HttpHandler {

    override fun handleRequest(exchange: HttpServerExchange) {
        if (exchange.requestMethod == HttpString("POST")) {
            handleCreateAccountRequest(exchange)
            return
        } else if (exchange.requestMethod == HttpString("GET")) {
            handleGetAccountRequest(exchange)
        }
        // TODO 405 Method Not Allowed
        exchange.statusCode = 405
        exchange.reasonPhrase = "Method Not Allowed"
//            exchange.endExchange()
//            throw UnsupportedOperationException("Operation ${exchange.requestMethod} isn't supported")
    }

    private fun handleCreateAccountRequest(exchange: HttpServerExchange) {
        // TODO process exceptions
        val request = jsonObjectMapper.readValue(exchange.inputStream, AccCreateRequest::class.java)
        accountService.createAccount(request.accId, request.negativeBalanceAllowed)
//        exchange.endExchange()
    }

    private fun handleGetAccountRequest(exchange: HttpServerExchange) {
        // TODO process exceptions
        val accId: String = exchange.queryParameters["accId"]?.first ?: throw IllegalArgumentException("accTo parameter is required")

        val account = accountService.findAccountById(accId)
        if (account != null) {
            exchange.responseSender.send(jsonObjectMapper.writeValueAsString(AccGetResponse(account)))
        } else {
            exchange.statusCode = 404
            exchange.reasonPhrase = "Not Found"
        }
//        exchange.endExchange()
    }
}