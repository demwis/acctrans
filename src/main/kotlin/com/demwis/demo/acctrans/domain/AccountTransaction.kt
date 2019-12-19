package com.demwis.demo.acctrans.domain

import java.math.BigDecimal

// TODO add time
data class AccountTransaction(val transactionId: Long, val accId: String, val amount: BigDecimal)