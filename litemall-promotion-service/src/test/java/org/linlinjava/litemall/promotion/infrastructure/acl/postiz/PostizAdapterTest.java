package org.linlinjava.litemall.promotion.infrastructure.acl.postiz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.promotion.application.ports.PostizGatewayException;
import org.linlinjava.litemall.promotion.application.ports.PostizPort.ChannelPost;
import org.linlinjava.litemall.promotion.application.ports.PostizPort.ScheduledPost;
import org.linlinjava.litemall.promotion.infrastructure.acl.postiz.dto.PostizCreatePostRequest;
import org.linlinjava.litemall.promotion.infrastructure.acl.postiz.dto.PostizCreatedPostDto;
import org.linlinjava.litemall.promotion.infrastructure.acl.postiz.dto.PostizIntegrationDto;
import org.linlinjava.litemall.promotion.infrastructure.configuration.PostizProperties;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wire contract with Postiz: mandatory payload keys ({@code shortLink},
 * {@code tags}, {@code type:"schedule"}, {@code date}), MediaDto's id+path
 * pair, the BARE Authorization header, and verbatim decoding of Postiz's
 * structured validation 400s.
 */
class PostizAdapterTest {

    private static class FakeClient implements PostizClient {
        PostizCreatePostRequest lastRequest;
        RuntimeException failWith;
        List<PostizCreatedPostDto> response = List.of();

        @Override
        public List<PostizIntegrationDto> listIntegrations() {
            if (failWith != null) {
                throw failWith;
            }
            return List.of();
        }

        @Override
        public List<PostizCreatedPostDto> createPost(PostizCreatePostRequest request) {
            lastRequest = request;
            if (failWith != null) {
                throw failWith;
            }
            return response;
        }
    }

    private FakeClient client;
    private PostizProperties properties;
    private PostizAdapter adapter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        client = new FakeClient();
        properties = new PostizProperties();
        properties.setBaseUrl("http://localhost:4007/api/public/v1");
        properties.setApiKey("the-api-key");
        adapter = new PostizAdapter(client, properties, objectMapper);
    }

    @Test
    void schedulePayloadCarriesTheMandatoryKeysOnTheWire() throws Exception {
        ChannelPost target = new ChannelPost("int-1", "<p>hello</p>",
                "https://trovemo.com/_cdn/cf/a.jpg", Map.of("post_type", "post"));
        adapter.schedulePost("2030-01-01T10:00:00Z", List.of(target));

        // Serialize exactly what Feign would hand to Jackson.
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(client.lastRequest));
        assertEquals("schedule", json.get("type").asText());
        assertEquals(false, json.get("shortLink").asBoolean());
        assertTrue(json.get("tags").isArray());
        assertEquals(0, json.get("tags").size());
        assertEquals("2030-01-01T10:00:00Z", json.get("date").asText());

        JsonNode post = json.get("posts").get(0);
        assertEquals("int-1", post.get("integration").get("id").asText());
        JsonNode value = post.get("value").get(0);
        assertEquals("<p>hello</p>", value.get("content").asText());
        // Postiz's MediaDto demands BOTH id and path, even for external URLs.
        assertEquals("https://trovemo.com/_cdn/cf/a.jpg", value.get("image").get(0).get("path").asText());
        assertTrue(value.get("image").get(0).hasNonNull("id"));
        assertEquals("post", post.get("settings").get("post_type").asText());
    }

    @Test
    void responsePairsIntegrationWithPostId() {
        PostizCreatedPostDto created = new PostizCreatedPostDto();
        created.setPostId("cuid-post");
        created.setIntegration("int-1");
        client.response = List.of(created);

        List<ScheduledPost> result = adapter.schedulePost("2030-01-01T10:00:00Z",
                List.of(new ChannelPost("int-1", "<p>x</p>", null, Map.of())));
        assertEquals("int-1", result.get(0).integrationId());
        assertEquals("cuid-post", result.get(0).postizPostId());
    }

    @Test
    void structuredValidation400KeepsProviderAndVerbatimMessage() {
        String body = "{\"statusCode\":400,\"provider\":\"facebook\",\"name\":\"Facebook\","
                + "\"message\":\"Please fix your settings\"}";
        client.failWith = feign(400, body);

        PostizGatewayException e = assertThrows(PostizGatewayException.class,
                () -> adapter.schedulePost("2030-01-01T10:00:00Z",
                        List.of(new ChannelPost("int-1", "<p>x</p>", null, Map.of()))));
        assertEquals("facebook", e.getProvider());
        assertEquals("Please fix your settings", e.getMessage());
        assertEquals(400, e.getStatus());
    }

    @Test
    void transportFailureDegradesToUnreachableMessage() {
        client.failWith = new RuntimeException("Connection refused");
        PostizGatewayException e = assertThrows(PostizGatewayException.class, () -> adapter.channels());
        assertNull(e.getProvider());
        assertTrue(e.getMessage().contains("Connection refused"));
    }

    @Test
    void unconfiguredAdapterRefusesToCall() {
        properties.setApiKey("");
        assertThrows(PostizGatewayException.class, () -> adapter.channels());
        assertNull(client.lastRequest);
    }

    @Test
    void authInterceptorSendsTheBareKey() {
        RequestTemplate template = new RequestTemplate();
        new PostizFeignConfig().postizAuthInterceptor(properties).apply(template);
        assertEquals(List.of("the-api-key"), List.copyOf(template.headers().get("Authorization")));
    }

    private feign.FeignException feign(int status, String body) {
        Request request = Request.create(Request.HttpMethod.POST, "/posts",
                new HashMap<>(), null, StandardCharsets.UTF_8, null);
        return feign.FeignException.errorStatus("createPost",
                feign.Response.builder()
                        .status(status)
                        .reason("Bad Request")
                        .request(request)
                        .body(body, StandardCharsets.UTF_8)
                        .build());
    }
}
