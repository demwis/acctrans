package com.demwis.demo.acctrans.server

import com.demwis.demo.acctrans.controller.AccountsHttpHandler
import com.demwis.demo.acctrans.controller.AccountsTransferHttpHandler
import com.demwis.demo.acctrans.dao.AccountTransactionDaoImpl
import com.demwis.demo.acctrans.dao.AccountsDaoImpl
import com.demwis.demo.acctrans.service.AccountService
import com.demwis.demo.acctrans.service.AccountServiceImpl
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.undertow.Handlers
import io.undertow.Undertow
import io.undertow.server.handlers.BlockingHandler
import io.undertow.server.handlers.PathTemplateHandler
import org.quartz.*
import org.quartz.impl.StdSchedulerFactory
import org.slf4j.LoggerFactory
import java.lang.Exception
import java.util.concurrent.Executors


/**
 * * Create mechanisms for money transferring:
 * ** +via internal storage,
 * ** via H2 usage or CQEngine
 * * +Create mechanism for filling accounts with data, starting balance
 * * +Create mechanism for balance recalculation
 * * Logging everywhere, proper exception processing
 * * Write unit tests on money transfer
 * * Write integration tests
 * * Make server configurable via HOCON
 * * Currencies aspect?
 */
fun main(args: Array<String>) {
    val jsonObjectMapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule())

    val accountsDao = AccountsDaoImpl()

    val accTransactionDao = AccountTransactionDaoImpl()


    val recalculateBalanceExecutor = Executors.newFixedThreadPool(10)
    val accountService =
        AccountServiceImpl(accountsDao, accTransactionDao, recalculateBalanceExecutor)

    val accountTransferHttpHandler = AccountsTransferHttpHandler(jsonObjectMapper, accountService)

    val accountsHttpHandler = AccountsHttpHandler(jsonObjectMapper, accountService)

    initScheduler(accountService) {
        registerRecalculationJob("0 5 0 * * ?")
    }

    initHttpServer("localhost", 8080) {
        add("accounts/transfer", BlockingHandler(accountTransferHttpHandler))
        .add("accounts", BlockingHandler(accountsHttpHandler))
    }
}

private fun initScheduler(accountService: AccountService, registerJobs: Scheduler.() -> Unit) {
    val sf: SchedulerFactory = StdSchedulerFactory()
    val quartzScheduler = sf.scheduler
    quartzScheduler.context["accountService"] = accountService
    registerJobs(quartzScheduler)
    quartzScheduler.start()
    Runtime.getRuntime().addShutdownHook(object: Thread() {
        override fun run() {
            quartzScheduler.shutdown(true)
        }
    })
}

private fun initHttpServer(host: String, port: Int, handlers: PathTemplateHandler.() -> PathTemplateHandler) {
    val server: Undertow = Undertow.builder()
        .addHttpListener(port, host)
        .setHandler(handlers(Handlers.pathTemplate()))
        .build()
    server.start()
}

private fun Scheduler.registerRecalculationJob(cron: String) {
    val recalculateBalanceJob = JobBuilder.newJob(RecalculateBalanceJob::class.java)
        .withIdentity("recalculateBalanceJob")
        .build()
    val justAfterMidnightTrigger = TriggerBuilder.newTrigger()
        .withIdentity("justAfterMidnightTrigger")
        .withSchedule(CronScheduleBuilder.cronSchedule(cron))
        .build()
    this.scheduleJob(recalculateBalanceJob, justAfterMidnightTrigger)
}

private class RecalculateBalanceJob: Job {
    private val log = LoggerFactory.getLogger(RecalculateBalanceJob::class.java)

    override fun execute(context: JobExecutionContext) {
        try {
            (context["accountService"] as AccountService).recalculateBalance()
        } catch (e: Exception) {
            log.error("Can't recalculate balance", e)
        }
    }
}