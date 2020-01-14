package com.demwis.demo.acctrans.domain

import com.fasterxml.jackson.annotation.JsonCreator
import java.math.BigDecimal
import java.time.LocalDate

data class AccGetResponse
@JsonCreator constructor(val accId: String,
                         val negativeBalanceAllowed: Boolean,
                         val eodBalance: BigDecimal,
                         val currentBalance: BigDecimal,
                         val balanceLastUpdateDate: LocalDate
) {
    constructor(account: Account, currentBalance: BigDecimal) : this(account.accId, account.negativeBalanceAllowed, account.eodBalance, currentBalance, account.balanceLastUpdateDate)
}