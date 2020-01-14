package com.demwis.common

import io.undertow.server.HttpServerExchange

fun HttpServerExchange.status(code: Int, reason: String) {
    this.statusCode = code
    this.reasonPhrase = reason
}