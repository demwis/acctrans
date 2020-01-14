package com.demwis.demo.acctrans.service

import com.demwis.common.CurrentThreadExecutorService
import com.demwis.common.NowProvider
import com.demwis.demo.acctrans.dao.AccountTransactionDao
import com.demwis.demo.acctrans.dao.AccountsDao
import com.demwis.demo.acctrans.domain.Account
import spock.lang.Specification
import spock.lang.Unroll

import java.time.LocalDate
import java.util.stream.Stream

class AccountServiceImplTest extends Specification {

    def "Should transfer money properly"() {
        given:
        def today = LocalDate.of(2019, 12, 23)
        def accFromId = "accFrom"
        def accToId = "accTo"
        def amount = BigDecimal.valueOf(100)
        def accFrom = new Account(accFromId, today)
        def accTo = new Account(accToId, today)
        def accountsDao = Mock(AccountsDao) {
            1 * findAccountById(accFromId) >> accFrom
            1 * findAccountById(accToId) >> accTo
        }
        def accountTransactionDao = Mock(AccountTransactionDao)
        def nowProvider = Mock(NowProvider)
        def service = new AccountServiceImpl(accountsDao, accountTransactionDao, CurrentThreadExecutorService.INSTANCE, nowProvider)

        when:
        service.transferMoney(accFromId, accToId, amount)

        then:
        1 * accountTransactionDao.transferMoney(accFrom, accTo, amount)
    }

    @Unroll
    def "Should fail if account wasn't found on money transferring"() {
        given:
        def today = LocalDate.of(2019, 12, 23)
        def accFromId = "accFrom"
        def accToId = "accTo"
        def amount = BigDecimal.valueOf(100)
        def accFrom = accFromFound ? new Account(accFromId, today) : null
        def accTo = accToFound ? new Account(accToId, today) : null
        def accountsDao = Mock(AccountsDao) {
            findAccountById(accFromId) >> accFrom
            findAccountById(accToId) >> accTo
        }
        def accountTransactionDao = Mock(AccountTransactionDao)
        def nowProvider = Mock(NowProvider)
        def service = new AccountServiceImpl(accountsDao, accountTransactionDao, CurrentThreadExecutorService.INSTANCE, nowProvider)

        when:
        service.transferMoney(accFromId, accToId, amount)

        then:
        thrown(IllegalArgumentException)

        where:
        accFromFound    | accToFound
        true            | false
        false           | true
    }

    @Unroll
    def "Should create account properly"() {
        given:
        def today = LocalDate.of(2019, 12, 23)
        def accId = "acc1"
        def accExpected = new Account(accId, negativeBalanceAllowed, today)
        def accountsDao = Mock(AccountsDao) {
            1 * createAccount(accId, negativeBalanceAllowed) >> accExpected
        }
        def accountTransactionDao = Mock(AccountTransactionDao)
        def nowProvider = Mock(NowProvider)
        def service = new AccountServiceImpl(accountsDao, accountTransactionDao, CurrentThreadExecutorService.INSTANCE, nowProvider)

        when:
        def result = service.createAccount(accId, negativeBalanceAllowed)

        then:
        result == accExpected

        where:
        negativeBalanceAllowed  | _
        true                    | _
        false                   | _
    }

    @Unroll
    def "Should find account by id properly"() {
        given:
        def today = LocalDate.of(2019, 12, 23)
        def accountsDao = Mock(AccountsDao) {
            findAccountById("acc1") >> new Account("acc1", today)
            findAccountById("acc2") >> null
        }
        def accountTransactionDao = Mock(AccountTransactionDao)
        def nowProvider = Mock(NowProvider)
        def service = new AccountServiceImpl(accountsDao, accountTransactionDao, CurrentThreadExecutorService.INSTANCE, nowProvider)

        when:
        def result = service.findAccountById(accId)

        then:
        result == expected

        where:
        accId    | expected
        "acc1"   | new Account("acc1", LocalDate.of(2019, 12, 23))
        "acc2"   | null
    }

    def "Should recalculate balance properly"() {
        given:
        def today = LocalDate.of(2019, 12, 23)
        def yesterday = today.minusDays(1)
        def acc1 = new Account("acc1", false, BigDecimal.valueOf(100), today.minusDays(2))
        def acc2 = new Account("acc2", false, BigDecimal.valueOf(400), today.minusDays(3))
        def acc3 = new Account("acc3", false, BigDecimal.valueOf(500), yesterday)
        def accountsDao = Mock(AccountsDao) {
            1 * getAllAccountsStream() >> Stream.of(acc1, acc2)
        }
        def accountTransactionDao = Mock(AccountTransactionDao) {
            1 * getAccountTransactionBalanceForDays(acc1, today.minusDays(2), yesterday, false, true) >> 200
            1 * getAccountTransactionBalanceForDays(acc2, today.minusDays(3), yesterday, false, true) >> -150
            0 * getAccountTransactionBalanceForDays(acc3, _, _, false, true) >> -100
        }
        def nowProvider = Mock(NowProvider) {
            1 * getLocalDate() >> today
        }
        def service = new AccountServiceImpl(accountsDao, accountTransactionDao, CurrentThreadExecutorService.INSTANCE, nowProvider)

        when:
        service.recalculateBalance()

        then:
        acc1.balanceLastUpdateDate == yesterday
        acc1.eodBalance == BigDecimal.valueOf(300)
        acc2.balanceLastUpdateDate == yesterday
        acc2.eodBalance == BigDecimal.valueOf(250)
        acc3.balanceLastUpdateDate == yesterday
        acc3.eodBalance == BigDecimal.valueOf(500)
    }

}
