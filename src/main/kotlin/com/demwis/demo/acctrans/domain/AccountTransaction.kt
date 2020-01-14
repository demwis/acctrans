package com.demwis.demo.acctrans.domain

import java.math.BigDecimal
import java.time.LocalDateTime

data class AccountTransaction(val transactionId: Long,
                              val accId: String,
                              val amount: BigDecimal,
                              val transactionTime: LocalDateTime)