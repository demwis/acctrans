package com.demwis.demo.acctrans.domain

import com.fasterxml.jackson.annotation.JsonCreator

data class AccCreateRequest
@JsonCreator constructor(val accId: String, val negativeBalanceAllowed: Boolean = false)