package com.demwis.demo.acctrans.controller

import com.demwis.common.status
import com.demwis.demo.acctrans.domain.AccCreateRequest
import com.demwis.demo.acctrans.domain.AccGetResponse
import com.demwis.demo.acctrans.exception.BadRequestException
import com.demwis.demo.acctrans.exception.DuplicatedRecordException
import com.demwis.demo.acctrans.service.AccountService
import com.fasterxml.jackson.databind.ObjectMapper
import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
import io.undertow.util.HttpString
import org.slf4j.LoggerFactory

class AccountsHttpHandler(private val jsonObjectMapper: ObjectMapper,
                          private val accountService: AccountService
): HttpHandler {

    private val log = LoggerFactory.getLogger(AccountsHttpHandler::class.java)

    override fun handleRequest(exchange: HttpServerExchange) {
        if (exchange.requestMethod == HttpString("POST")) {
            handleCreateAccountRequest(exchange)
            return
        } else if (exchange.requestMethod == HttpString("GET")) {
            handleGetAccountRequest(exchange)
            return
        }
        exchange.status(405, "Method Not Allowed")
    }

    private fun handleCreateAccountRequest(exchange: HttpServerExchange) {
        val request = try {
            jsonObjectMapper.readValue(exchange.inputStream, AccCreateRequest::class.java)
        } catch (e: Exception) {
            throw BadRequestException("Can't parse create account request parameters", e)
        }
        try {
            accountService.createAccount(request.accId, request.negativeBalanceAllowed)
        } catch (e: DuplicatedRecordException) {
            throw BadRequestException("Can't create account", e)
        }
    }

    private fun handleGetAccountRequest(exchange: HttpServerExchange) {
        val accId: String = exchange.queryParameters["accId"]?.first ?: throw BadRequestException("accId parameter is required")

        val account = accountService.findAccountById(accId)
        if (account != null) {
            exchange.responseSender.send(jsonObjectMapper.writeValueAsString(AccGetResponse(account, accountService.getAccountBalance(account))))
        } else {
            exchange.status(404, "Not Found")
        }
    }
}