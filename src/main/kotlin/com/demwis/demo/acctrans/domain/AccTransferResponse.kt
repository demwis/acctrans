package com.demwis.demo.acctrans.domain

import java.math.BigDecimal

data class AccTransferResponse(val accFromId: String, val accToId: String, val amount: BigDecimal, val transactionId: Long) {
    constructor(request: AccTransferRequest, transactionId: Long): this(request.accFromId, request.accToId, request.amount, transactionId)
}