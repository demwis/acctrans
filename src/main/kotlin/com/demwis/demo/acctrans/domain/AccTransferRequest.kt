package com.demwis.demo.acctrans.domain

import com.fasterxml.jackson.annotation.JsonCreator
import java.math.BigDecimal

data class AccTransferRequest
    @JsonCreator constructor(val accFromId: String, val accToId: String, val amount: BigDecimal)