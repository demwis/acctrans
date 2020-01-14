package com.demwis.demo.acctrans.server

import com.demwis.common.status
import com.demwis.demo.acctrans.exception.BadRequestException
import io.undertow.server.HttpServerExchange
import io.undertow.server.handlers.ExceptionHandler
import org.slf4j.LoggerFactory

object ExceptionHandlers {
    private val log =
        LoggerFactory.getLogger(ExceptionHandlers::class.java)

    fun handleBadRequestException(exchange: HttpServerExchange) {
        val exception = exchange.getAttachment(ExceptionHandler.THROWABLE) as BadRequestException
        log.warn("Bad request", exception)
        exchange.status(403, "Bad request")
    }

    fun handleOtherExceptions(exchange: HttpServerExchange) {
        val exception = exchange.getAttachment(ExceptionHandler.THROWABLE) as Throwable
        log.error("Internal server error", exception)

        exchange.status(500, "Internal server error")
    }
}