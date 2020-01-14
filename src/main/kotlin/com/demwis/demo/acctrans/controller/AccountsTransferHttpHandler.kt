package com.demwis.demo.acctrans.controller

import com.demwis.common.status
import com.demwis.demo.acctrans.domain.AccTransferRequest
import com.demwis.demo.acctrans.domain.AccTransferResponse
import com.demwis.demo.acctrans.exception.BadRequestException
import com.demwis.demo.acctrans.service.AccountService
import com.fasterxml.jackson.databind.ObjectMapper
import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
import io.undertow.util.HttpString
import java.lang.Exception
import java.lang.IllegalArgumentException

class AccountsTransferHttpHandler(private val jsonObjectMapper: ObjectMapper,
                                  private val accountService: AccountService
): HttpHandler {

    override fun handleRequest(exchange: HttpServerExchange) {
        if (exchange.requestMethod != HttpString("POST")) {
            exchange.status(405, "Method Not Allowed")
            return
        }
        handleTransferMoneyRequest(exchange)
    }

    private fun handleTransferMoneyRequest(exchange: HttpServerExchange) {
        val request = try {
            jsonObjectMapper.readValue(exchange.inputStream, AccTransferRequest::class.java)
        } catch (ex: Exception) {
            throw BadRequestException("Can't parse AccTransferRequest params", ex)
        }
        val transactionId = try {
            accountService.transferMoney(request.accFromId, request.accToId, request.amount)
        } catch (ex: IllegalArgumentException) {
            throw BadRequestException("Can't transfer money. Conditions aren't satisfied", ex)
        }

        val response = AccTransferResponse(request, transactionId)
        exchange.responseSender.send(jsonObjectMapper.writeValueAsString(response))
        exchange.endExchange()
    }
}

