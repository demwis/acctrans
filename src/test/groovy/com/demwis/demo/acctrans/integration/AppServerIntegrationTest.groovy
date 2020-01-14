package com.demwis.demo.acctrans.integration

import com.demwis.common.JsonUtilsKt
import com.demwis.common.NowProvider
import com.demwis.common.test.SettableNowProvider
import com.demwis.demo.acctrans.domain.AccGetResponse
import com.demwis.demo.acctrans.server.AppServerKt
import com.fasterxml.jackson.databind.ObjectMapper
import io.undertow.Undertow
import org.apache.http.client.ResponseHandler
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.BasicResponseHandler
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import spock.lang.Shared
import spock.lang.Specification

import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger


class AppServerIntegrationTest extends Specification {
    private static String HOST = "localhost"
    private static int PORT = 8081
    private static ACCOUNT_ID_SEQUENCER = new AtomicInteger(1)

    @Shared Undertow server
    @Shared ObjectMapper jsonMapper
    @Shared NowProvider nowProvider
    @Shared CloseableHttpClient httpclient = HttpClients.createDefault()

    def setupSpec() {
        nowProvider = new SettableNowProvider(LocalDateTime.of(2020, 1, 14, 10, 18))
        server = AppServerKt.initAndStart(HOST, PORT, false, nowProvider)
        jsonMapper = JsonUtilsKt.createObjectMapper()
        def cm = new PoolingHttpClientConnectionManager()
        cm.setMaxTotal(200)
        httpclient = HttpClients.custom().setConnectionManager(cm).build()
    }

    def "Should create and get account properly"() {
        given:
        def accId = nextAccountId()
        def negativeBalanceAllowed = true
        def getAccountRequest = getAccountRequest(accId)
        def createAccountRequest = createAccountRequest(accId, negativeBalanceAllowed)
        def expectedAccountResponse = new AccGetResponse(accId, negativeBalanceAllowed, BigDecimal.valueOf(0),
                BigDecimal.valueOf(0), nowProvider.localDate)
        def responseBodyHandler = new BasicResponseHandler()

        when:
        def createAccountResponse = httpclient.execute(createAccountRequest)

        then:
        createAccountResponse.getStatusLine().statusCode == 200

        when:
        def getAccountResponse = httpclient.execute(getAccountRequest)

        then:
        getAccountResponse.getStatusLine().statusCode == 200
        jsonMapper.readValue(responseBodyHandler.handleResponse(getAccountResponse), AccGetResponse.class) == expectedAccountResponse

        cleanup:
        createAccountResponse.close()
        getAccountResponse.close()
    }

    def "Should transfer money properly"() {
        given:
        def acc2Id = nextAccountId()
        def acc3Id = nextAccountId()
        def expectedAccount2Response = new AccGetResponse(acc2Id, true, BigDecimal.valueOf(0), BigDecimal.valueOf(-1100.5d), nowProvider.localDate)
        def expectedAccount3Response = new AccGetResponse(acc3Id, false, BigDecimal.valueOf(0), BigDecimal.valueOf(1100.5d), nowProvider.localDate)
        def responseBodyHandler = new BasicResponseHandler()

        httpclient.execute(createAccountRequest(acc2Id, true))
        httpclient.execute(createAccountRequest(acc3Id, false))

        when:
        def response1 = httpclient.execute(transferMoneyRequest(acc2Id, acc3Id, BigDecimal.valueOf(1000.1d)))
        then:
        response1.getStatusLine().statusCode == 200
        response1.close()

        when:
        def response2 = httpclient.execute(transferMoneyRequest(acc3Id, acc2Id, BigDecimal.valueOf(500.1d)))
        then:
        response2.getStatusLine().statusCode == 200
        response2.close()

        when:
        def response3 = httpclient.execute(transferMoneyRequest(acc2Id, acc3Id, BigDecimal.valueOf(600.5d)))
        then:
        response3.getStatusLine().statusCode == 200
        response3.close()

        when:
        def account2Response = httpclient.execute(getAccountRequest(acc2Id))
        def account3Response = httpclient.execute(getAccountRequest(acc3Id))

        then:
        jsonMapper.readValue(responseBodyHandler.handleResponse(account2Response), AccGetResponse.class) == expectedAccount2Response
        jsonMapper.readValue(responseBodyHandler.handleResponse(account3Response), AccGetResponse.class) == expectedAccount3Response

        cleanup:
        account2Response.close()
        account3Response.close()
    }

    def "Should not transfer money if the same account is used"() {
        given:
        def acc2Id = nextAccountId()
        def acc3Id = acc2Id

        httpclient.execute(createAccountRequest(acc2Id, true))

        when:
        def response1 = httpclient.execute(transferMoneyRequest(acc2Id, acc3Id, BigDecimal.valueOf(1000.1d)))

        then:
        response1.getStatusLine().statusCode == 403

        cleanup:
        response1.close()
    }

    def "Should not transfer money if amount is negative"() {
        given:
        def acc2Id = nextAccountId()
        def acc3Id = nextAccountId()

        httpclient.execute(createAccountRequest(acc2Id, true))
        httpclient.execute(createAccountRequest(acc3Id, false))

        when:
        def response1 = httpclient.execute(transferMoneyRequest(acc2Id, acc3Id, BigDecimal.valueOf(-1000.1d)))

        then:
        response1.getStatusLine().statusCode == 403

        cleanup:
        response1.close()
    }

    def "Should not transfer money if insufficient funds"() {
        given:
        def acc2Id = nextAccountId()
        def acc3Id = nextAccountId()

        httpclient.execute(createAccountRequest(acc2Id, true))
        httpclient.execute(createAccountRequest(acc3Id, false))

        when:
        def response1 = httpclient.execute(transferMoneyRequest(acc3Id, acc2Id, BigDecimal.valueOf(1000.1d)))

        then:
        response1.getStatusLine().statusCode == 403

        cleanup:
        response1.close()
    }

    def "Should not find account with 404 code"() {
        given:
        def accId = "ACC_UNKNOWN"

        when:
        def getAccountResponse = httpclient.execute(getAccountRequest(accId))

        then:
        getAccountResponse.getStatusLine().statusCode == 404
        getAccountResponse.getStatusLine().getReasonPhrase() == "Not Found"

        cleanup:
        getAccountResponse.close()
    }

    def "Should fail properly on account retrieval if parameter wasn't provided"() {
        when:
        def getAccountResponse = httpclient.execute(new HttpGet("http://$HOST:$PORT/accounts"))

        then:
        getAccountResponse.getStatusLine().statusCode == 403

        cleanup:
        getAccountResponse.close()
    }

    def "Should fail properly on account creation if parameter is invalid"() {
        given:
        def request = new HttpPost("http://$HOST:$PORT/accounts")
        def entity = new StringEntity("""\
            {"unknownParam" : "140", "negativeBalanceAllowed" : "false"}
            """)
        request.setEntity(entity)

        when:
        def response = httpclient.execute(request)

        then:
        response.getStatusLine().statusCode == 403

        cleanup:
        response.close()
    }

    def "Should fail properly on money transfer if parameter is invalid"() {
        given:
        def request = new HttpPost("http://$HOST:$PORT/accounts/transfer")
        def entity = new StringEntity("""\
            {"accFromId" : "1", "acc" : "2", "amount" : "500.1"}
            """)
        request.setEntity(entity)

        when:
        def response = httpclient.execute(request)

        then:
        response.getStatusLine().statusCode == 403

        cleanup:
        response.close()
    }

    private HttpPost createAccountRequest(String accId, boolean negativeBalanceAllowed) {
        def request = new HttpPost("http://$HOST:$PORT/accounts")
        def entity = new StringEntity("""\
            {"accId" : "$accId", "negativeBalanceAllowed" : "$negativeBalanceAllowed"}
            """)
        request.setEntity(entity)
        return request
    }

    private HttpGet getAccountRequest(String accId) {
        return new HttpGet("http://$HOST:$PORT/accounts?accId=$accId")
    }

    private HttpPost transferMoneyRequest(String accFromId, String accToId, BigDecimal amount) {
        def request = new HttpPost("http://$HOST:$PORT/accounts/transfer")
        def entity = new StringEntity("""\
            {"accFromId" : "$accFromId", "accToId" : "$accToId", "amount" : "$amount"}
            """)
        request.setEntity(entity)
        return request
    }

    private String nextAccountId() {
        return "ACC_" + ACCOUNT_ID_SEQUENCER.getAndIncrement()
    }

    def cleanupSpec() {
        server.stop()
    }
}