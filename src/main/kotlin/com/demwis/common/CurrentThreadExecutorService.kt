package com.demwis.common

import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Used in cases when we need to turn concurrent code into single-threaded
 */
object CurrentThreadExecutorService: AbstractExecutorService() {
    private val isWorking = AtomicBoolean(true)

    override fun isTerminated(): Boolean = !isWorking.get()

    override fun execute(command: Runnable) {
        if (isWorking.get()) {
            command.run()
        }
    }

    override fun shutdown() {
        isWorking.set(false)
    }

    override fun shutdownNow(): List<Runnable> {
        isWorking.set(false)
        return emptyList()
    }

    override fun isShutdown(): Boolean = !isWorking.get()

    override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = true
}
