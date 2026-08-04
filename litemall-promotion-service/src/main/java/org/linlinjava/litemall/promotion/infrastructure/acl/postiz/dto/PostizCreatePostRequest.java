package org.linlinjava.litemall.promotion.infrastructure.acl.postiz.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Body of Postiz {@code POST /public/v1/posts} (its {@code CreatePostDto}).
 * Field names verified against the Postiz source (create.post.dto.ts):
 * {@code shortLink} and {@code tags} are MANDATORY keys (400 if omitted), so
 * they are non-null initialized here and always serialized; {@code date} must
 * be an explicit UTC ISO instant with {@code type:"schedule"} ({@code "now"}
 * discards the date). One call targets N channels via {@code posts[]} — one
 * throttle hit per CALL, not per channel.
 */
@Getter
@Setter
public class PostizCreatePostRequest {

    private String type = "schedule";

    private boolean shortLink = false;

    /** UTC ISO-8601 instant, e.g. {@code 2026-08-05T14:00:00.000Z}. */
    private String date;

    /** Mandatory key; empty is valid. Entries would be {@code {value, label}}. */
    private List<Map<String, String>> tags = new ArrayList<>();

    private List<PostEntry> posts = new ArrayList<>();

    /** One target channel of the call. */
    @Getter
    @Setter
    public static class PostEntry {
        private Integration integration;
        private List<Value> value = new ArrayList<>();
        /**
         * Provider settings — required EVEN FOR DRAFTS (facebook {@code {}},
         * instagram {@code {post_type}}, x {@code {who_can_reply_post}});
         * Postiz injects the {@code __type} discriminator server-side.
         */
        private Map<String, Object> settings;
    }

    @Getter
    @Setter
    public static class Integration {
        private String id;

        public Integration() {
        }

        public Integration(String id) {
            this.id = id;
        }
    }

    /** Post content: sanitizer-safe HTML + image attachments. */
    @Getter
    @Setter
    public static class Value {
        private String content;
        /** Always present ({@code IsArray} on Postiz's side). */
        private List<Media> image = new ArrayList<>();
    }

    /**
     * Postiz {@code MediaDto}: {@code id} AND {@code path} are both required
     * strings; {@code path} passes as an external URL only when it ends in a
     * real media extension (query string ignored).
     */
    @Getter
    @Setter
    public static class Media {
        private String id;
        private String path;

        public Media() {
        }

        public Media(String id, String path) {
            this.id = id;
            this.path = path;
        }
    }
}
