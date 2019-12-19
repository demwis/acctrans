package com.demwis.demo.acctrans.domain

import java.math.BigDecimal
import java.util.concurrent.locks.ReentrantLock

// TODO Once a day recalculate balance
// TODO store date of last recalculation
// TODO is negative balance allowed
data class Account(val accId: String,
                   @Volatile var eodBalance: BigDecimal
) {
    val lock = ReentrantLock()
}