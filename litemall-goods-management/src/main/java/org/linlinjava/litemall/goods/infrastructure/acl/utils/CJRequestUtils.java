package org.linlinjava.litemall.goods.infrastructure.acl.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;


public abstract class CJRequestUtils {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public CJRequestUtils(RestTemplate restTemplate, ObjectMapper mapp) {
        this.restTemplate = restTemplate;
        this.objectMapper = mapp;
    }


    public <T> T makeGetRequest(String url,  Class<T> responseType, String accessToken, String errorMessage){
        return makeRequest(url, HttpMethod.GET, responseType, accessToken, errorMessage);
    }

    public <T> T makePostRequest(String url,  Class<T> responseType, String accessToken, String errorMessage){
        return makeRequest(url, HttpMethod.POST, responseType,  accessToken, errorMessage);
    }

    /**
     * POST with a JSON request body (the original {@link #makePostRequest(String, Class, String, String)}
     * sends headers only). {@code body} is serialized with the shared {@link ObjectMapper}.
     */
    public <T> T makePostRequest(String url, Object body, Class<T> responseType, String accessToken, String errorMessage){
        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException(errorMessage + ": failed to serialize request body", e);
        }
        HttpEntity<String> requestEntity = new HttpEntity<>(json, createHeadersWithToken(accessToken));
        ResponseEntity<T> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, responseType);
        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            return response.getBody();
        } else {
            throw new RuntimeException(errorMessage + ": " + response.getStatusCode());
        }
    }

    public <T> T makePutRequest(String url,  Class<T> responseType, String accessToken, String errorMessage){
        return makeRequest(url, HttpMethod.PUT, responseType, accessToken, errorMessage);
    }
    public <T> T makeDeleteRequest(String url,  Class<T> responseType, String accessToken, String errorMessage){
        return makeRequest(url, HttpMethod.DELETE, responseType, accessToken, errorMessage);
    }

    private <T> T makeRequest(String url, HttpMethod method, Class<T> responseType, String accessToken, String errorMessage){

        HttpEntity<String>  requestEntity = createRequestWithToken(accessToken);

        ResponseEntity<T> response = restTemplate.exchange(url, method, requestEntity, responseType);
        if(response.getStatusCode() == HttpStatus.OK && response.getBody()!= null){
            return response.getBody();
        } else {
            throw new RuntimeException(errorMessage + ": " + response.getStatusCode());
        }
    }


    private HttpHeaders createHeadersWithToken(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("CJ-Access-Token", accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    // Helper method to create a request with token
    private HttpEntity<String> createRequestWithToken(String accessToken) {
        return new HttpEntity<>(createHeadersWithToken(accessToken));
    }

}
