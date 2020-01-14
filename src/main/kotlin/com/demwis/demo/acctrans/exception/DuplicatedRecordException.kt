package com.demwis.demo.acctrans.exception

import java.lang.RuntimeException

class DuplicatedRecordException : RuntimeException {
    constructor(message: String, cause: Throwable): super(message, cause)
    constructor(message: String): super(message)
}