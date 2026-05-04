package com.petsocial.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiServiceFactoryTest {

    @Test
    fun createBarkWiseApiService_usesMockServiceWhenMockDataEnabled() {
        val api = createBarkWiseApiService(
            useMockData = true,
            baseUrl = "http://10.0.2.2:8000/",
            fallbackBaseUrl = "https://api.barkwiseai.com/",
            authTokenProvider = { error("mock service should not request auth token") },
        )

        assertEquals("MockApiService", api::class.java.simpleName)
    }
}
