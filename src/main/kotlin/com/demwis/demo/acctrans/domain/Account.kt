package com.demwis.demo.acctrans.domain

import java.math.BigDecimal
import java.time.LocalDate
import java.util.concurrent.locks.ReentrantLock

data class Account
@JvmOverloads constructor(val accId: String,
                   val negativeBalanceAllowed: Boolean = false,
                   @Volatile var eodBalance: BigDecimal = BigDecimal.ZERO,
                   @Volatile var balanceLastUpdateDate: LocalDate
) {
    val lock = ReentrantLock()
}