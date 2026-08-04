package org.linlinjava.litemall.promotion.infrastructure.acl.postiz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import org.linlinjava.litemall.promotion.application.ports.PostizGatewayException;
import org.linlinjava.litemall.promotion.application.ports.PostizPort;
import org.linlinjava.litemall.promotion.infrastructure.acl.postiz.dto.PostizCreatePostRequest;
import org.linlinjava.litemall.promotion.infrastructure.acl.postiz.dto.PostizCreatedPostDto;
import org.linlinjava.litemall.promotion.infrastructure.acl.postiz.dto.PostizIntegrationDto;
import org.linlinjava.litemall.promotion.infrastructure.configuration.PostizProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link PostizPort} over the {@link PostizClient} Feign client. Guards every
 * call on {@link PostizProperties#isConfigured()} and translates failures into
 * {@link PostizGatewayException}: Postiz's structured validation 400s
 * ({@code {statusCode, provider, name, message}} from its
 * PostValidationExceptionFilter) keep their provider + verbatim message;
 * anything else degrades to the best available message. Never lets a raw
 * FeignException reach application code.
 */
@Service
public class PostizAdapter implements PostizPort {

    private final PostizClient client;
    private final PostizProperties properties;
    private final ObjectMapper objectMapper;

    public PostizAdapter(PostizClient client, PostizProperties properties, ObjectMapper objectMapper) {
        this.client = client;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<PostizChannel> channels() {
        requireConfigured();
        List<PostizIntegrationDto> integrations;
        try {
            integrations = client.listIntegrations();
        } catch (Exception e) {
            throw translate(e);
        }
        if (integrations == null) {
            return List.of();
        }
        return integrations.stream()
                .map(row -> new PostizChannel(
                        row.getId(),
                        row.getIdentifier(),
                        row.getName(),
                        row.getPicture(),
                        Boolean.TRUE.equals(row.getDisabled())))
                .collect(Collectors.toList());
    }

    @Override
    public List<ScheduledPost> schedulePost(String dateUtcIso, List<ChannelPost> targets) {
        requireConfigured();
        PostizCreatePostRequest request = new PostizCreatePostRequest();
        request.setDate(dateUtcIso);
        request.setPosts(targets.stream().map(this::toPostEntry).collect(Collectors.toList()));
        List<PostizCreatedPostDto> created;
        try {
            created = client.createPost(request);
        } catch (Exception e) {
            throw translate(e);
        }
        if (created == null) {
            return List.of();
        }
        return created.stream()
                .map(row -> new ScheduledPost(row.getIntegration(), row.getPostId()))
                .collect(Collectors.toList());
    }

    private PostizCreatePostRequest.PostEntry toPostEntry(ChannelPost target) {
        PostizCreatePostRequest.PostEntry entry = new PostizCreatePostRequest.PostEntry();
        entry.setIntegration(new PostizCreatePostRequest.Integration(target.integrationId()));
        PostizCreatePostRequest.Value value = new PostizCreatePostRequest.Value();
        value.setContent(target.content());
        if (StringUtils.hasText(target.imageUrl())) {
            // Postiz's MediaDto requires an id string even for external URLs.
            value.getImage().add(new PostizCreatePostRequest.Media(target.imageUrl(), target.imageUrl()));
        }
        entry.getValue().add(value);
        entry.setSettings(target.settings());
        return entry;
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new PostizGatewayException("Postiz is not configured", null, 0, null);
        }
    }

    private PostizGatewayException translate(Exception e) {
        if (e instanceof PostizGatewayException gateway) {
            return gateway;
        }
        if (e instanceof FeignException feign) {
            String body = feign.contentUTF8();
            String provider = null;
            String message = null;
            if (StringUtils.hasText(body)) {
                try {
                    JsonNode node = objectMapper.readTree(body);
                    provider = textOrNull(node, "provider");
                    message = firstText(node, "message", "msg", "error");
                } catch (Exception ignored) {
                    // Non-JSON body — fall through to the raw text.
                }
                if (!StringUtils.hasText(message)) {
                    message = body;
                }
            }
            if (!StringUtils.hasText(message)) {
                message = "Postiz call failed with HTTP " + feign.status();
            }
            return new PostizGatewayException(message, provider, feign.status(), feign);
        }
        return new PostizGatewayException(
                "Postiz unreachable: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()),
                null, 0, e);
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    /** First present field, tolerating Nest's string-or-array "message" shape. */
    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value == null) {
                continue;
            }
            if (value.isTextual() && StringUtils.hasText(value.asText())) {
                return value.asText();
            }
            if (value.isArray() && value.size() > 0 && value.get(0).isTextual()) {
                return value.get(0).asText();
            }
        }
        return null;
    }
}
