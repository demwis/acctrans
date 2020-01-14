package com.demwis.demo.acctrans.server

import com.demwis.common.DefaultNowProvider
import com.demwis.common.NowProvider
import com.demwis.common.createObjectMapper
import com.demwis.demo.acctrans.controller.AccountsHttpHandler
import com.demwis.demo.acctrans.controller.AccountsTransferHttpHandler
import com.demwis.demo.acctrans.dao.AccountTransactionDaoImpl
import com.demwis.demo.acctrans.dao.AccountsDaoImpl
import com.demwis.demo.acctrans.exception.BadRequestException
import com.demwis.demo.acctrans.service.AccountService
import com.demwis.demo.acctrans.service.AccountServiceImpl
import com.typesafe.config.ConfigFactory
import io.undertow.Handlers
import io.undertow.Undertow
import io.undertow.server.HttpHandler
import io.undertow.server.handlers.BlockingHandler
import io.undertow.server.handlers.PathTemplateHandler
import org.quartz.*
import org.quartz.impl.StdSchedulerFactory
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors


private val log = LoggerFactory.getLogger("com.demwis.demo.acctrans.server.AppServer")
/**
 * * Create mechanisms for money transferring:
 * ** +via internal storage,
 * ** via H2 usage or CQEngine
 * * +Create mechanism for filling accounts with data, starting balance
 * * +Create mechanism for balance recalculation
 * * +Logging everywhere,
 * * +proper exception processing
 * * +Write unit tests on money transfer
 * * +Write integration tests
 * * +Make server configurable via HOCON
 * * Currencies aspect?
 * * +logging configs
 */
fun main(args: Array<String>) {
    val conf = ConfigFactory.load()
    val host = conf.getConfig("server")?.getString("host") ?: "localhost"
    val port = conf.getConfig("server")?.getInt("port") ?: 8080
    initAndStart(host, port)
}

fun initAndStart(host: String, port: Int, runSchedules: Boolean = true, nowProvider: NowProvider = DefaultNowProvider): Undertow {
    val jsonObjectMapper = createObjectMapper()

    val accountsDao = AccountsDaoImpl(nowProvider)

    val accTransactionDao = AccountTransactionDaoImpl(nowProvider)


    val recalculateBalanceExecutor = Executors.newFixedThreadPool(10)
    val accountService =
        AccountServiceImpl(accountsDao, accTransactionDao, recalculateBalanceExecutor, nowProvider)

    val accountTransferHttpHandler = AccountsTransferHttpHandler(jsonObjectMapper, accountService)

    val accountsHttpHandler = AccountsHttpHandler(jsonObjectMapper, accountService)

    if (runSchedules)
        initScheduler(accountService) {
            registerRecalculationJob("0 5 0 * * ?")
        }

    return initHttpServer(host, port) {
        add("accounts/transfer", BlockingHandler(withExceptionHandling(accountTransferHttpHandler)))
            .add("accounts", BlockingHandler(withExceptionHandling(accountsHttpHandler)))
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

private fun initHttpServer(host: String, port: Int, handlers: PathTemplateHandler.() -> PathTemplateHandler): Undertow {
    log.info("Starting undertow server on host {} and port {}", host, port)
    val server: Undertow = Undertow.builder()
        .addHttpListener(port, host)
        .setHandler(handlers(Handlers.pathTemplate()))
        .build()
    server.start()
    return server
}

private fun withExceptionHandling(httpHandler: HttpHandler): HttpHandler =
    Handlers.exceptionHandler(httpHandler)
        .addExceptionHandler(BadRequestException::class.java, ExceptionHandlers::handleBadRequestException)
        .addExceptionHandler(Throwable::class.java, ExceptionHandlers::handleOtherExceptions)


private fun Scheduler.registerRecalculationJob(cron: String) {
    log.info("Starting recalculation job with schedule: {}", cron)
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