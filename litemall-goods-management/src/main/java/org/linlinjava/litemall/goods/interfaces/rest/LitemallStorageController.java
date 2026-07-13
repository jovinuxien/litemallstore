package org.linlinjava.litemall.goods.interfaces.rest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.linlinjava.litemall.core.storage.StorageService;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.domain.LitemallStorage;
import org.linlinjava.litemall.db.service.LitemallStorageService;
import org.linlinjava.litemall.goods.infrastructure.configuration.CustomerStorageProperties;
import org.linlinjava.litemall.goods.utils.UserContext;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Customer object storage (litemall-wx-api {@code WxStorageController} parity, hardened):
 * <ul>
 * <li>{@code POST /upload} requires a gateway-authenticated customer ({@code X-User-Id}), enforces
 *     the {@code litemall.customer-storage.max-size-bytes} cap and a magic-byte image whitelist
 *     (jpeg/png/gif/webp) — the client Content-Type and filename are never trusted;
 * <li>{@code GET /fetch/{key}} is the anonymous read path the minted storage URLs point at
 *     ({@code litemall.storage.local.address}).
 * </ul>
 */
@RestController
@RequestMapping("/srv/storage")
public class LitemallStorageController {
    private final Log logger = LogFactory.getLog(LitemallStorageController.class);

    /** Longest magic-byte prefix we need (WEBP: RIFF....WEBP = 12 bytes). */
    private static final int HEADER_LEN = 12;

    private final StorageService storageService;
    private final LitemallStorageService litemallStorageService;
    private final CustomerStorageProperties properties;

    public LitemallStorageController(StorageService storageService,
                                     LitemallStorageService litemallStorageService,
                                     CustomerStorageProperties properties) {
        this.storageService = storageService;
        this.litemallStorageService = litemallStorageService;
        this.properties = properties;
    }

    @PostMapping("/upload")
    public Object upload(@RequestParam("file") MultipartFile file) {
        if (UserContext.getUserIdAsInt() == null) {
            return ResponseUtil.unlogin();
        }
        if (file == null || file.isEmpty()) {
            return ResponseUtil.badArgument();
        }
        long maxSizeBytes = properties.getMaxSizeBytes();
        if (file.getSize() > maxSizeBytes) {
            return ResponseUtil.fail(400, "file exceeds the upload limit of " + maxSizeBytes + " bytes (5MB)");
        }

        try {
            ImageType type = detectImageType(file);
            if (type == null) {
                return ResponseUtil.fail(400, "only jpeg/png/gif/webp images are allowed");
            }
            // Never pass the client filename: StorageService.generateKey substrings on the last '.'
            // (dotless names throw) and the name may carry path junk. Always our own safe name.
            String safeFileName = "customer-upload" + type.extension;
            LitemallStorage stored;
            try (InputStream in = file.getInputStream()) {
                stored = storageService.store(in, file.getSize(), type.mimeType, safeFileName);
            }
            Map<String, Object> data = new LinkedHashMap<>(5);
            data.put("key", stored.getKey());
            data.put("name", stored.getName());
            data.put("type", stored.getType());
            data.put("size", stored.getSize());
            data.put("url", stored.getUrl());
            return ResponseUtil.ok(data);
        } catch (IOException e) {
            logger.error("customer upload failed", e);
            return ResponseUtil.fail(502, "upload failed");
        }
    }

    /**
     * Anonymous fetch of a stored object; {@code {key:.+}} is required so Spring does not truncate
     * the extension as a content-negotiation suffix.
     */
    @GetMapping("/fetch/{key:.+}")
    public ResponseEntity<Resource> fetch(@PathVariable String key) {
        if (key == null || key.contains("../")) {
            return ResponseEntity.badRequest().build();
        }
        LitemallStorage litemallStorage = litemallStorageService.findByKey(key);
        if (litemallStorage == null) {
            return ResponseEntity.notFound().build();
        }
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(litemallStorage.getType());
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        Resource resource = storageService.loadAsResource(key);
        if (resource == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().contentType(mediaType).body(resource);
    }

    /** Magic-byte sniffing over the first 12 bytes of a fresh stream; null = not a whitelisted image. */
    private static ImageType detectImageType(MultipartFile file) throws IOException {
        byte[] header = new byte[HEADER_LEN];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(header, 0, HEADER_LEN);
        }
        // JPEG: FF D8 FF
        if (read >= 3
                && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return new ImageType("image/jpeg", ".jpg");
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (read >= 8
                && (header[0] & 0xFF) == 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47
                && header[4] == 0x0D && header[5] == 0x0A && header[6] == 0x1A && header[7] == 0x0A) {
            return new ImageType("image/png", ".png");
        }
        // GIF: 'GIF8'
        if (read >= 4
                && header[0] == 'G' && header[1] == 'I' && header[2] == 'F' && header[3] == '8') {
            return new ImageType("image/gif", ".gif");
        }
        // WEBP: 'RIFF' + bytes 8-11 == 'WEBP'
        if (read >= 12
                && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return new ImageType("image/webp", ".webp");
        }
        return null;
    }

    private static final class ImageType {
        final String mimeType;
        final String extension;

        ImageType(String mimeType, String extension) {
            this.mimeType = mimeType;
            this.extension = extension;
        }
    }
}
