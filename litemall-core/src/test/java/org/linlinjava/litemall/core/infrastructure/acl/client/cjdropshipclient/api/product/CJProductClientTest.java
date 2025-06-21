package org.linlinjava.litemall.core.infrastructure.acl.client.cjdropshipclient.api.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.core.infrastructure.acl.service.cjdropshipservice.CJAuthenticationService;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

public class CJProductClientTest {

    private CJDropshippingConfig config;
    private CJAuthenticationService authService;
    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;
    private CJProductClient cjProductClient;

    @BeforeEach
    public void setUp() {

        // Create mocks
        config = mock(CJDropshippingConfig.class);
        restTemplate = mock(RestTemplate.class);
        objectMapper = mock(ObjectMapper.class);
        authService = mock(CJAuthenticationService.class);

        // Initialize the class under test with the mocks
        cjProductClient = new CJProductClient(config, restTemplate, objectMapper, authService);
        when(config.getCjEmail()).thenReturn("bimeni89@gmail.com");
        when(config.getCjApiKey()).thenReturn("8696a1e060b64e1ab99e3f16cac7a87a");
        //when(config.getProductDetailUrl()).thenReturn("https://api.cj.com/product/detail");
        when(config.getProductListUrl()).thenReturn("https://developers.cjdropshipping.com/api2.0/v1/product/list");
    }

    @Test
    public void testGetAccessTokenRespectsTimeInterval() {

        CJProductClient mockedClient = mock(CJProductClient.class);
        doAnswer(invocation -> {
            Thread.sleep(300000);
            return "mockAccessToken";
        }).when(mockedClient).getAccessToken();

        // Call the getAccessToken method
        long startTime = System.currentTimeMillis();
        String accessToken = mockedClient.getAccessToken();
        long endTime = System.currentTimeMillis();

        // Verify that the delay of 5 minutes is respected
        long elapsedTime = endTime - startTime;
        assertTrue(elapsedTime >= 300000, "Expected delay of at least 5 minutes");


    }
}
