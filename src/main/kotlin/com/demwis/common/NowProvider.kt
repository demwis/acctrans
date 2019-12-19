package com.demwis.common

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

interface NowProvider {
    val currentTimeMillis
        get() = System.currentTimeMillis()

    val localDateTime
        get() = LocalDateTime.ofInstant(Instant.ofEpochMilli(currentTimeMillis), ZoneId.of("UTC"))

    val localDate
        get() = localDateTime.toLocalDate()

    @Throws(InterruptedException::class)
    fun sleep(millis: Long) {
        Thread.sleep(millis)
    }
}

object DefaultNowProvider: NowProvider