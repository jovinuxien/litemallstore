package org.linlinjava.litemall.goods.infrastructure.acl.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;


public abstract class CJRequestUtils {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public CJRequestUtils(RestTemplate restTemplate, ObjectMapper mapp) {
        this.restTemplate = restTemplate;
        this.objectMapper = mapp;
    }


    // Get methods with optional parameters

    public <T> T makeGetRequest(String url,  Class<T> responseType, String accessToken, String errorMessage){
        return makeGetRequest(url, responseType, accessToken, errorMessage, Collections.emptyMap());
    }

    public <T> T makeGetRequest(String url,  Class<T> responseType, String accessToken, String errorMessage, Map<String, Object> params){
        return makeRequest(url, HttpMethod.GET, null, responseType, accessToken, errorMessage, params);
    }

    /**
     * Build POST method with optional parameters and body
     * @param url
     * @param responseType
     * @param accessToken
     * @param errorMessage
     * @return
     * @param <T>
     */

    public <T> T makePostRequest(String url, Class<T> responseType, String accessToken, String errorMessage) {
        return makePostRequest(url, null, responseType, accessToken, errorMessage, null);
    }

    public <T> T makePostRequest(String url, Object requestBody, Class<T> responseType,
                                 String accessToken, String errorMessage) {
        return makePostRequest(url, requestBody, responseType, accessToken, errorMessage, null);
    }
    public <T> T makePostRequest(String url,Object requestBody,  Class<T> responseType, String accessToken, String errorMessage, Map<String, Object> params){
        return makeRequest(url, HttpMethod.POST, requestBody, responseType,  accessToken, errorMessage, params);
    }

    /**
     * Put methods with optional parameters and body
     * @param url
     * @param responseType
     * @param accessToken
     * @param errorMessage
     * @return
     * @param <T>
     */
    public <T> T makePutRequest(String url, Class<T> responseType, String accessToken, String errorMessage) {
        return makePutRequest(url, null, responseType, accessToken, errorMessage, null);
    }

    public <T> T makePutRequest(String url, Object requestBody, Class<T> responseType,
                                String accessToken, String errorMessage) {
        return makePutRequest(url, requestBody, responseType, accessToken, errorMessage, null);
    }
    public <T> T makePutRequest(String url, Object requestBody, Class<T> responseType,
                                String accessToken, String errorMessage, Map<String, Object> params) {
        return makeRequest(url, HttpMethod.PUT, requestBody, responseType, accessToken, errorMessage, params);
    }


    /**
     * Delete methods with optional parameters
     * @param url
     * @param responseType
     * @param accessToken
     * @param errorMessage
     * @return
     * @param <T>
     */
    public <T> T makeDeleteRequest(String url, Class<T> responseType, String accessToken, String errorMessage) {
        return makeDeleteRequest(url, responseType, accessToken, errorMessage, null);
    }

    public <T> T makeDeleteRequest(String url, Class<T> responseType, String accessToken,
                                   String errorMessage, Map<String, Object> params) {
        return makeRequest(url, HttpMethod.DELETE, null, responseType, accessToken, errorMessage, params);
    }

    private <T> T makeRequest(String url, HttpMethod method, Object requestBody, Class<T> responseType, String accessToken,
                              String errorMessage, Map<String, Object> params){

        //Build the URL with query parameters if provided
        String finalUrl  = buildUrlWithParams(url, params);

        HttpEntity<?> requestEntity = createRequestEntity(requestBody, accessToken);

        try {
            ResponseEntity<T> response = restTemplate.exchange(
                    finalUrl, method, requestEntity, responseType);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            } else {
                throw new RuntimeException(errorMessage + ": " + response.getStatusCode());
            }
        } catch (Exception e) {
            throw new RuntimeException(errorMessage + " - " + e.getMessage(), e);
        }

    }


    // Build URL with query parameters
    private String buildUrlWithParams(String url, Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return url;
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);

        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (entry.getValue() != null) {
                builder.queryParam(entry.getKey(), entry.getValue());
            }
        }

        return builder.toUriString();
    }


    // Create request entity with optional body
    private HttpEntity<?> createRequestEntity(Object requestBody, String accessToken) {
        HttpHeaders headers = createHeadersWithToken(accessToken);

        if (requestBody != null) {
            try {
                String jsonBody = objectMapper.writeValueAsString(requestBody);
                return new HttpEntity<>(jsonBody, headers);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize request body: " + e.getMessage(), e);
            }
        } else {
            return new HttpEntity<>(headers);
        }
    }

    // Helper method to create a request with token
    private HttpEntity<String> createRequestWithToken(String accessToken) {
        return new HttpEntity<>(createHeadersWithToken(accessToken));
    }

    private HttpHeaders createHeadersWithToken(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("CJ-Access-Token", accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    // Helper method for paginated requests
    public <T> List<T> makePaginatedRequest(String baseUrl, Class<T> responseType,
                                            String accessToken, String errorMessage,
                                            String listPropertyName) {
        return makePaginatedRequest(baseUrl, responseType, accessToken, errorMessage,
                listPropertyName, null, 100);
    }

    public <T> List<T> makePaginatedRequest(String baseUrl, Class<T> responseType,
                                            String accessToken, String errorMessage,
                                            String listPropertyName, Map<String, Object> baseParams,
                                            int pageSize) {

        List<T> allItems = new ArrayList<>();
        int page = 1;
        boolean hasMore = true;

        while (hasMore) {
            try {
                Map<String, Object> params = new HashMap<>();
                if (baseParams != null) {
                    params.putAll(baseParams);
                }
                params.put("page", page);
                params.put("pageSize", pageSize);

                Object response = makeGetRequest(baseUrl, Object.class, accessToken, errorMessage, params);

                // Extract the list from response using reflection
                List<T> items = extractListFromResponse(response, listPropertyName, responseType);

                if (items == null || items.isEmpty()) {
                    hasMore = false;
                } else {
                    allItems.addAll(items);

                    // Check if we got fewer items than requested (end of data)
                    if (items.size() < pageSize) {
                        hasMore = false;
                    }

                    page++;

                    // Respect rate limits
                    Thread.sleep(200);
                }

            } catch (Exception e) {
                throw new RuntimeException("Error in paginated request: " + e.getMessage(), e);
            }
        }

        return allItems;
    }

    // Helper to extract list from response using reflection
    @SuppressWarnings("unchecked")
    private <T> List<T> extractListFromResponse(Object response, String propertyName, Class<T> itemType) {
        try {
            if (response instanceof Map) {
                Map<String, Object> responseMap = (Map<String, Object>) response;
                Object data = responseMap.get("data");

                if (data instanceof Map) {
                    Map<String, Object> dataMap = (Map<String, Object>) data;
                    Object list = dataMap.get(propertyName);

                    if (list instanceof List) {
                        return (List<T>) list;
                    }
                }
            }

            return Collections.emptyList();

        } catch (Exception e) {
            throw new RuntimeException("Failed to extract list from response: " + e.getMessage(), e);
        }
    }

}
