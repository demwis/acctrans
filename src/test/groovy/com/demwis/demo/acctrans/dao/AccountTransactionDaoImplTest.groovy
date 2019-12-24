package com.demwis.demo.acctrans.dao

import com.demwis.common.NowProvider
import com.demwis.demo.acctrans.domain.Account
import com.demwis.demo.acctrans.domain.AccountTransaction
import kotlin.Pair
import spock.lang.Specification
import spock.lang.Unroll

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ConcurrentSkipListMap

import static org.unitils.reflectionassert.ReflectionAssert.assertLenientEquals

class AccountTransactionDaoImplTest extends Specification {

    def "Should transfer money properly"() {
        given:
        def balanceLastUpdateDate = LocalDate.of(2019, 12, 23)
        def now = LocalDateTime.of(2019, 12, 23, 1, 2, 3)
        def amount = BigDecimal.valueOf(1000.1)
        def accountFrom = new Account("acc1", false, amount, balanceLastUpdateDate)
        def accountTo = new Account("acc2", false, balanceLastUpdateDate)
        def nowProvider = Mock(NowProvider) {
            getLocalDateTime() >> now
        }
        def dao = new AccountTransactionDaoImpl(nowProvider)

        when:
        def resultTransactionId = dao.transferMoney(accountFrom, accountTo, amount)

        def fromTransaction = new AccountTransaction(resultTransactionId, accountFrom.accId, -amount, now)
        def toTransaction = new AccountTransaction(resultTransactionId, accountTo.accId, amount, now)
        def expectedAccTransactionsById = [(resultTransactionId) : [fromTransaction, toTransaction]]
        def expectedAccTransactionsByAccIdAndDate = [(accountFrom.accId) : [(now.toLocalDate()) : [fromTransaction]],
                                                     (accountTo.accId) : [(now.toLocalDate()) : [toTransaction]]]

        then:
        resultTransactionId == 1
        assertLenientEquals(expectedAccTransactionsById, dao.accTransactionsById)
        assertLenientEquals(expectedAccTransactionsByAccIdAndDate, dao.accTransactionsByAccIdAndDate)
    }

    def "Should transfer money properly when negative balance is allowed"() {
        given:
        def balanceLastUpdateDate = LocalDate.of(2019, 12, 23)
        def now = LocalDateTime.of(2019, 12, 23, 1, 2, 3)
        def accountFrom = new Account("acc1", true, balanceLastUpdateDate)
        def accountTo = new Account("acc2", false, balanceLastUpdateDate)
        def amount = BigDecimal.valueOf(1000.1)
        def nowProvider = Mock(NowProvider) {
            getLocalDateTime() >> now
        }
        def dao = new AccountTransactionDaoImpl(nowProvider)

        when:
        def resultTransactionId = dao.transferMoney(accountFrom, accountTo, amount)

        def fromTransaction = new AccountTransaction(resultTransactionId, accountFrom.accId, amount.negate(), now)
        def toTransaction = new AccountTransaction(resultTransactionId, accountTo.accId, amount, now)
        def expectedAccTransactionsById = [(resultTransactionId) : [fromTransaction, toTransaction]]
        def expectedAccTransactionsByAccIdAndDate = [(accountFrom.accId) : [(now.toLocalDate()) : [fromTransaction]],
                                                     (accountTo.accId) : [(now.toLocalDate()) : [toTransaction]]]

        then:
        resultTransactionId == 1
        assertLenientEquals(expectedAccTransactionsById, dao.accTransactionsById)
        assertLenientEquals(expectedAccTransactionsByAccIdAndDate, dao.accTransactionsByAccIdAndDate)
    }

    def "Should fail properly on money transfer when not enough funds"() {
        given:
        def balanceLastUpdateDate = LocalDate.of(2019, 12, 23)
        def now = LocalDateTime.of(2019, 12, 23, 1, 2, 3)
        def accountFrom = new Account("acc1", false, BigDecimal.valueOf(100), balanceLastUpdateDate)
        def accountTo = new Account("acc2", false, balanceLastUpdateDate)
        def amount = BigDecimal.valueOf(1000.1)
        def nowProvider = Mock(NowProvider) {
            getLocalDateTime() >> now
        }
        def dao = new AccountTransactionDaoImpl(nowProvider)

        when:
        dao.transferMoney(accountFrom, accountTo, amount)

        then:
        thrown(IllegalArgumentException)
    }

    def "Should transfer money when balance is formed of several transactions"() {
        given:
        def balanceLastUpdateDate = LocalDate.of(2019, 12, 20)
        def firstTime = LocalDateTime.of(balanceLastUpdateDate.plusDays(1), LocalTime.of(2, 3, 4))
        def secondTime = LocalDateTime.of(balanceLastUpdateDate.plusDays(2), LocalTime.of(5, 1, 50))
        def thirdTime = LocalDateTime.of(balanceLastUpdateDate.plusDays(2), LocalTime.of(5, 3, 45))

        def now = LocalDateTime.of(2019, 12, 23, 1, 2, 3)
        def amount = BigDecimal.valueOf(1201.0)
        def accountExt =  new Account("extAccount", true, balanceLastUpdateDate)
        def accountFrom = new Account("acc1", false, balanceLastUpdateDate)
        def accountTo = new Account("acc2", false, balanceLastUpdateDate)
        def nowProvider = Mock(NowProvider) {
            getLocalDateTime() >> now
        }
        def dao = new AccountTransactionDaoImpl(nowProvider)
        def firstTranPair = addTransaction(dao, accountExt.accId, accountFrom.accId, BigDecimal.valueOf(500.4), firstTime)
        def secondTranPair = addTransaction(dao, accountExt.accId, accountFrom.accId, BigDecimal.valueOf(400.6), secondTime)
        def thirdTranPair = addTransaction(dao, accountExt.accId, accountFrom.accId, BigDecimal.valueOf(300), thirdTime)

        when:
        def resultTransactionId = dao.transferMoney(accountFrom, accountTo, amount)

        def fromTransaction = new AccountTransaction(resultTransactionId, accountFrom.accId, -amount, now)
        def toTransaction = new AccountTransaction(resultTransactionId, accountTo.accId, amount, now)
        def expectedAccTransactionsById = [1L : [firstTranPair.first, firstTranPair.second],
                                           2L : [secondTranPair.first, secondTranPair.second],
                                           3L : [thirdTranPair.first, thirdTranPair.second],
                                           4L : [fromTransaction, toTransaction]]
        def expectedAccTransactionsByAccIdAndDate = [(accountExt.accId) : [(firstTime.toLocalDate()) : [firstTranPair.first],
                                                                           (secondTime.toLocalDate()) : [secondTranPair.first, thirdTranPair.first]],
                                                     (accountFrom.accId) : [(firstTime.toLocalDate()) : [firstTranPair.second],
                                                                            (secondTime.toLocalDate()) : [secondTranPair.second, thirdTranPair.second],
                                                                            (now.toLocalDate()) : [fromTransaction]],
                                                     (accountTo.accId) : [(now.toLocalDate()) : [toTransaction]]]

        then:
        resultTransactionId == 4
        assertLenientEquals(expectedAccTransactionsById, dao.accTransactionsById)
        assertLenientEquals(expectedAccTransactionsByAccIdAndDate, dao.accTransactionsByAccIdAndDate)
    }

    def "Should fail transferring money between accounts with the same id"() {
        given:
        def balanceLastUpdateDate = LocalDate.of(2019, 12, 23)
        def now = LocalDateTime.of(2019, 12, 23, 1, 2, 3)
        def accountFrom = new Account("acc1", false, BigDecimal.valueOf(100), balanceLastUpdateDate)
        def amount = BigDecimal.valueOf(1000.1)
        def nowProvider = Mock(NowProvider) {
            getLocalDateTime() >> now
        }
        def dao = new AccountTransactionDaoImpl(nowProvider)

        when:
        dao.transferMoney(accountFrom, accountFrom, amount)

        then:
        thrown(IllegalArgumentException)
    }

    @Unroll
    def "Should fail transferring not positive amount [#amount] of money"() {
        given:
        def balanceLastUpdateDate = LocalDate.of(2019, 12, 23)
        def now = LocalDateTime.of(2019, 12, 23, 1, 2, 3)
        def accountFrom = new Account("acc1", false, BigDecimal.valueOf(100), balanceLastUpdateDate)
        def accountTo = new Account("acc2", false, balanceLastUpdateDate)
        def nowProvider = Mock(NowProvider) {
            getLocalDateTime() >> now
        }
        def dao = new AccountTransactionDaoImpl(nowProvider)

        when:
        dao.transferMoney(accountFrom, accountTo, BigDecimal.valueOf(amount))

        then:
        thrown(IllegalArgumentException)

        where:
        amount  | _
        -1000.0 | _
        0.0     | _
    }

    def "Should calculate account balance properly"() {
        given:
        def balanceLastUpdateDate = LocalDate.of(2019, 12, 20)
        def firstTime = LocalDateTime.of(balanceLastUpdateDate.plusDays(1), LocalTime.of(2, 3, 4))
        def secondTime = LocalDateTime.of(balanceLastUpdateDate.plusDays(2), LocalTime.of(5, 1, 50))
        def thirdTime = LocalDateTime.of(balanceLastUpdateDate.plusDays(2), LocalTime.of(5, 3, 45))

        def now = LocalDateTime.of(2019, 12, 23, 1, 2, 3)
        def accountFrom = new Account("acc1", true, BigDecimal.valueOf(2000), balanceLastUpdateDate)
        def accountTo = new Account("acc2", false, balanceLastUpdateDate)
        def nowProvider = Mock(NowProvider) {
            getLocalDateTime() >> now
        }
        def dao = new AccountTransactionDaoImpl(nowProvider)
        addTransaction(dao, accountFrom.accId, accountTo.accId, BigDecimal.valueOf(500.4), firstTime)
        addTransaction(dao, accountFrom.accId, accountTo.accId, BigDecimal.valueOf(400.6), secondTime)
        addTransaction(dao, accountFrom.accId, accountTo.accId, BigDecimal.valueOf(300), thirdTime)
        addTransaction(dao, accountTo.accId, accountFrom.accId, BigDecimal.valueOf(551), now)

        when:
        def result = dao.calcAccountBalance(accountFrom)

        then:
        result == BigDecimal.valueOf(1350)

        when:
        result = dao.calcAccountBalance(accountTo)

        then:
        result == BigDecimal.valueOf(650)
    }

    private Pair<AccountTransaction, AccountTransaction> addTransaction(AccountTransactionDaoImpl dao, String accFromId, String accToId, BigDecimal amount, LocalDateTime time)
    {
        def transactionId = dao.transactionIdSequencer.incrementAndGet()
        def fromTransaction = new AccountTransaction(transactionId, accFromId, amount.negate(), time)
        def toTransaction = new AccountTransaction(transactionId, accToId, amount, time)

        dao.accTransactionsByAccIdAndDate.computeIfAbsent(accFromId, { new ConcurrentSkipListMap<>() })
                .computeIfAbsent(time.toLocalDate(), { new ConcurrentLinkedQueue<>() })
                .add(fromTransaction)
        dao.accTransactionsByAccIdAndDate.computeIfAbsent(accToId, { new ConcurrentSkipListMap<>() })
                .computeIfAbsent(time.toLocalDate(), { new ConcurrentLinkedQueue<>() })
                .add(toTransaction)
        dao.accTransactionsById[transactionId] = [fromTransaction, toTransaction]
        return new Pair<AccountTransaction, AccountTransaction>(fromTransaction, toTransaction)
    }

    @Unroll
    def "Should get balance [#expected] for days from [#fromDate, inc=#fromInclusive] to [#toDate, inc=#toInclusive]"() {
        given:
        def balanceLastUpdateDate = LocalDate.of(2019, 12, 20)
        def firstTime = LocalDateTime.of(balanceLastUpdateDate.plusDays(1), LocalTime.of(2, 3, 4))
        def secondTime = LocalDateTime.of(balanceLastUpdateDate.plusDays(2), LocalTime.of(5, 1, 50))
        def thirdTime = LocalDateTime.of(balanceLastUpdateDate.plusDays(2), LocalTime.of(5, 3, 45))
        def forthTime = LocalDateTime.of(balanceLastUpdateDate.plusDays(3), LocalTime.of(1, 2, 3))
        def fifthTime = LocalDateTime.of(balanceLastUpdateDate.plusDays(4), LocalTime.of(4, 3, 2))

        def accountFrom = new Account("acc1", true, BigDecimal.valueOf(2000), balanceLastUpdateDate)
        def accountTo = new Account("acc2", false, balanceLastUpdateDate)
        def nowProvider = Mock(NowProvider) {
            getLocalDateTime() >> fifthTime
        }
        def dao = new AccountTransactionDaoImpl(nowProvider)
        addTransaction(dao, accountFrom.accId, accountTo.accId, BigDecimal.valueOf(500.4), firstTime)
        addTransaction(dao, accountFrom.accId, accountTo.accId, BigDecimal.valueOf(400.6), secondTime)
        addTransaction(dao, accountFrom.accId, accountTo.accId, BigDecimal.valueOf(300), thirdTime)
        addTransaction(dao, accountTo.accId, accountFrom.accId, BigDecimal.valueOf(551), forthTime)
        addTransaction(dao, accountFrom.accId, accountTo.accId, BigDecimal.valueOf(150), fifthTime)

        when:
        def result = dao.getAccountTransactionBalanceForDays(accountFrom, fromDate, toDate, fromInclusive, toInclusive)

        then:
        result == BigDecimal.valueOf(expected)

        where:
        fromDate           | toDate             | fromInclusive | toInclusive   || expected
        date(2019, 12, 22) | date(2019, 12, 23) | true          | true          || -149.6
        date(2019, 12, 22) | date(2019, 12, 23) | true          | false         || -700.6
        date(2019, 12, 22) | date(2019, 12, 23) | false         | true          || 551.0
        date(2019, 12, 22) | date(2019, 12, 23) | false         | false         || 0.0
        date(2019, 12, 21) | date(2019, 12, 24) | false         | false         || -149.6
    }

    @Unroll
    def "Should fail if fromDate[#fromDate] is not earlier than toDate[#toDate] while getting account transaction balance"() {
        given:
        def balanceLastUpdateDate = LocalDate.of(2019, 12, 20)
        def firstTime = LocalDateTime.of(balanceLastUpdateDate.plusDays(1), LocalTime.of(2, 3, 4))
        def secondTime = LocalDateTime.of(balanceLastUpdateDate.plusDays(2), LocalTime.of(5, 1, 50))
        def thirdTime = LocalDateTime.of(balanceLastUpdateDate.plusDays(2), LocalTime.of(5, 3, 45))
        def forthTime = LocalDateTime.of(balanceLastUpdateDate.plusDays(3), LocalTime.of(1, 2, 3))
        def fifthTime = LocalDateTime.of(balanceLastUpdateDate.plusDays(4), LocalTime.of(4, 3, 2))

        def accountFrom = new Account("acc1", true, BigDecimal.valueOf(2000), balanceLastUpdateDate)
        def accountTo = new Account("acc2", false, balanceLastUpdateDate)
        def nowProvider = Mock(NowProvider) {
            getLocalDateTime() >> fifthTime
        }
        def dao = new AccountTransactionDaoImpl(nowProvider)
        addTransaction(dao, accountFrom.accId, accountTo.accId, BigDecimal.valueOf(500.4), firstTime)
        addTransaction(dao, accountFrom.accId, accountTo.accId, BigDecimal.valueOf(400.6), secondTime)
        addTransaction(dao, accountFrom.accId, accountTo.accId, BigDecimal.valueOf(300), thirdTime)
        addTransaction(dao, accountTo.accId, accountFrom.accId, BigDecimal.valueOf(551), forthTime)
        addTransaction(dao, accountFrom.accId, accountTo.accId, BigDecimal.valueOf(150), fifthTime)

        when:
        dao.getAccountTransactionBalanceForDays(accountFrom, fromDate, toDate, fromInclusive, toInclusive)

        then:
        def e = thrown(IllegalArgumentException)
        println(e)

        where:
        fromDate           | toDate             | fromInclusive | toInclusive
        date(2019, 12, 22) | date(2019, 12, 22) | true          | true
        date(2019, 12, 22) | date(2019, 12, 21) | true          | true
    }

    def date(int year, int month, int dayOfMonth) {
        return LocalDate.of(year, month, dayOfMonth)
    }
}
