package com.demwis.demo.acctrans.exception

import java.lang.RuntimeException

class BadRequestException : RuntimeException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}