package com.demwis.demo.acctrans.dao

import com.demwis.common.NowProvider
import com.demwis.demo.acctrans.domain.Account
import com.demwis.demo.acctrans.exception.DuplicatedRecordException
import spock.lang.Specification

import java.time.LocalDate

import static java.util.stream.Collectors.toList
import static org.unitils.reflectionassert.ReflectionAssert.assertLenientEquals

class AccountsDaoImplTest extends Specification {

    def "Should find account by id properly"() {
        given:
        def today = LocalDate.of(2019, 12, 23)
        def expectedAccId = "acc2"
        def expectedAcc = new Account(expectedAccId, today)
        def nowProvider = Mock(NowProvider)
        def dao = new AccountsDaoImpl(nowProvider)
        dao.accounts.putAll(['acc1' : new Account('acc1', today),
                             (expectedAccId) : expectedAcc,
                             'acc3' : new Account('acc3', today)])

        when:
        def result = dao.findAccountById(expectedAccId)

        then:
        result == expectedAcc
    }

    def "Should not find account by id"() {
        given:
        def today = LocalDate.of(2019, 12, 23)
        def unknownAccountId = "acc4"
        def nowProvider = Mock(NowProvider)
        def dao = new AccountsDaoImpl(nowProvider)
        dao.accounts.putAll(['acc1' : new Account('acc1', today),
                             'acc2' : new Account('acc2', today),
                             'acc3' : new Account('acc3', today)])

        when:
        def result = dao.findAccountById(unknownAccountId)

        then:
        result == null
    }

    def "Should create account properly"() {
        given:
        def today = LocalDate.of(2019, 12, 23)
        def accountId = "acc4"
        def negativeBalanceAllowed = true
        def expectedAccount = new Account(accountId, negativeBalanceAllowed, today)
        def nowProvider = Mock(NowProvider) {
            1 * getLocalDate() >> today
        }
        def dao = new AccountsDaoImpl(nowProvider)
        dao.accounts.putAll(['acc1' : new Account('acc1', today),
                             'acc2' : new Account('acc2', today),
                             'acc3' : new Account('acc3', today)])

        when:
        def result = dao.createAccount(accountId, negativeBalanceAllowed)

        then:
        result == expectedAccount
        dao.accounts.get(accountId) == expectedAccount
    }

    def "Should fail properly on account  creation if exists"() {
        given:
        def today = LocalDate.of(2019, 12, 23)
        def accountId = "acc3"
        def negativeBalanceAllowed = true
        def nowProvider = Mock(NowProvider) {
            1 * getLocalDate() >> today
        }
        def dao = new AccountsDaoImpl(nowProvider)
        dao.accounts.putAll(['acc1' : new Account('acc1', today),
                             'acc2' : new Account('acc2', today),
                             'acc3' : new Account('acc3', today)])

        when:
        dao.createAccount(accountId, negativeBalanceAllowed)

        then:
        thrown(DuplicatedRecordException)
    }

    def "Should get all accounts stream properly"() {
        given:
        def today = LocalDate.of(2019, 12, 23)
        def accountsMap = ['acc1' : new Account('acc1', today),
                        'acc2' : new Account('acc2', today),
                        'acc3' : new Account('acc3', today)]
        def nowProvider = Mock(NowProvider)
        def dao = new AccountsDaoImpl(nowProvider)
        dao.accounts.putAll(accountsMap)

        when:
        def result = dao.getAllAccountsStream()

        then:
        assertLenientEquals(accountsMap.values(), result.collect(toList()))
    }
}
