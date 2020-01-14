package com.demwis.common.test

import com.demwis.common.NowProvider
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicReference

class SettableNowProvider(time: LocalDateTime = LocalDateTime.now()): NowProvider {
    private val currentTime = AtomicReference(time)

    override val currentTimeMillis: Long
        get() = localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    override val localDateTime: LocalDateTime
        get() = currentTime.get()

    override fun sleep(millis: Long) {
        var done: Boolean
        do {
            val current = currentTime.get()
            done = currentTime.compareAndSet(current, current.plusNanos(millis * 1_000_000))
        } while (!done)
    }
}