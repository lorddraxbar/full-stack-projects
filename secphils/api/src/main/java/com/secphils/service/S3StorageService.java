package com.secphils.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secphils.common.ApiException;
import com.secphils.repository.SystemSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * S3-compatible object storage for documents.
 *
 * <p>Configuration (bucket, region, credentials, optional custom endpoint for
 * MinIO/LocalStack, optional public base URL) lives in the {@code system_settings}
 * row's {@code storage} JSONB column — entered from Admin → Settings, not in
 * env vars. The client is built lazily and rebuilt only when the effective
 * configuration changes, so editing settings in the UI takes effect immediately
 * without a restart.
 *
 * <p>Objects are stored one folder per project: {@code projects/{projectId}/documents/YYYY/MM/DD/<uuid>__<name>}
 * (an optional configured {@code folder} prefixes the whole tree). The project scope in the
 * key is informational — access control is enforced in the API layer, not by S3 paths.
 */
@Service
public class S3StorageService {

    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);
    public static final String SECRET_MASK = "********";

    /** One storage configuration as stored in system_settings.storage. */
    public record StorageConfig(
            String provider, String region, String bucket,
            String accessKey, String secretKey, String endpoint,
            String publicBaseUrl, String folder, int maxUploadMb) {

        public boolean isConfigured() {
            return !blank(bucket) && !blank(accessKey) && !blank(secretKey);
        }

        public static StorageConfig empty() {
            return new StorageConfig("S3", "us-east-1", "", "", "", "", "", "", 25);
        }

        private static boolean blank(String s) {
            return s == null || s.isBlank();
        }
    }

    /** A parsed s3://bucket/key reference. */
    public record S3Ref(String bucket, String key) {}

    private final SystemSettingsRepository settingsRepository;
    private final ObjectMapper objectMapper;

    private final Object lock = new Object();
    private volatile S3Client client;
    private volatile String clientFingerprint = "";

    public S3StorageService(SystemSettingsRepository settingsRepository, ObjectMapper objectMapper) {
        this.settingsRepository = settingsRepository;
        this.objectMapper = objectMapper;
    }

    // ---------- configuration ----------

    public StorageConfig currentConfig() {
        return settingsRepository.findAll().stream().findFirst()
                .map(s -> parseConfig(s.getStorage()))
                .orElse(StorageConfig.empty());
    }

    @SuppressWarnings("unchecked")
    public StorageConfig parseConfig(String json) {
        if (json == null || json.isBlank()) return StorageConfig.empty();
        try {
            Map<String, Object> m = objectMapper.readValue(json, Map.class);
            return fromMap(m);
        } catch (Exception e) {
            return StorageConfig.empty();
        }
    }

    /** Builds a config from a raw map (the /storage/test endpoint takes a JSON body). */
    @SuppressWarnings("unchecked")
    public static StorageConfig fromMap(Map<String, Object> m) {
        if (m == null) return StorageConfig.empty();
        return new StorageConfig(
                str(m, "provider", "S3"),
                str(m, "region", "us-east-1"),
                str(m, "bucket", ""),
                str(m, "accessKey", ""),
                str(m, "secretKey", ""),
                str(m, "endpoint", ""),
                str(m, "publicBaseUrl", ""),
                str(m, "folder", ""),
                intVal(m, "maxUploadMb", 25));
    }

    /** Serializes a config for the storage JSONB column. */
    public String serialize(StorageConfig cfg) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "provider", cfg.provider(),
                    "region", cfg.region(),
                    "bucket", cfg.bucket(),
                    "accessKey", cfg.accessKey(),
                    "secretKey", cfg.secretKey(),
                    "endpoint", cfg.endpoint(),
                    "publicBaseUrl", cfg.publicBaseUrl(),
                    "folder", cfg.folder(),
                    "maxUploadMb", cfg.maxUploadMb()));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize storage config", e);
        }
    }

    private static String str(Map<String, Object> m, String key, String fallback) {
        Object v = m.get(key);
        if (v == null) return fallback;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? fallback : s;
    }

    private static int intVal(Map<String, Object> m, String key, int fallback) {
        Object v = m.get(key);
        if (v == null) return fallback;
        try {
            int i = Integer.parseInt(String.valueOf(v).trim());
            return i > 0 && i <= 500 ? i : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // ---------- client management ----------

    private S3Client client(StorageConfig cfg) {
        if (!cfg.isConfigured()) {
            throw ApiException.badRequest("Object storage is not configured yet — complete Admin → Settings → Object Storage");
        }
        String fp = String.join("|", cfg.region(), cfg.bucket(), cfg.endpoint(), cfg.accessKey(), cfg.secretKey());
        if (client != null && fp.equals(clientFingerprint)) return client;
        synchronized (lock) {
            if (client != null && fp.equals(clientFingerprint)) return client;
            S3Client old = client;
            client = buildClient(cfg);
            clientFingerprint = fp;
            if (old != null) {
                try {
                    old.close();
                } catch (Exception ignored) {
                    // closing a broken client is not a problem
                }
            }
        }
        return client;
    }

    private S3Client buildClient(StorageConfig cfg) {
        Region region;
        try {
            region = Region.of(cfg.region());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Invalid AWS region: " + cfg.region());
        }
        var builder = S3Client.builder()
                .region(region)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(cfg.accessKey(), cfg.secretKey())))
                .overrideConfiguration(o -> o
                        .apiCallAttemptTimeout(Duration.ofSeconds(15))
                        .apiCallTimeout(Duration.ofMinutes(3)));
        // Custom endpoints (MinIO / LocalStack / Cloudflare R2 / DigitalOcean Spaces):
        // address objects by path (path-style) rather than a virtual-hosted Host header,
        // which S3-compatible gateways may reject for SigV4.
        //
        // We also DISABLE the AWS "chunked" (streaming) SigV4 payload
        // (STREAMING-AWS4-HMAC-SHA256-PAYLOAD). It is the SDK's default for uploads,
        // but many S3-compatible gateways — notably MinIO — reject its signature,
        // producing a 403 SignatureDoesNotMatch on PutObject while HEAD/LIST (no body)
        // still succeed. With the payload already in memory (RequestBody.fromBytes)
        // there is no streaming benefit, so we send the universally-compatible
        // plain sha256 + Content-Length form instead.
        if (!cfg.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(cfg.endpoint().trim()))
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(true)
                            .chunkedEncodingEnabled(false)
                            .build());
        } else {
            builder.serviceConfiguration(S3Configuration.builder().chunkedEncodingEnabled(false).build());
        }
        return builder.build();
    }

    // ---------- operations ----------

    /** Stores the file under the project folder and returns its s3:// reference. */
    public String upload(StorageConfig cfg, Long projectId, byte[] bytes, String originalName, String contentType) {
        S3Client c = client(cfg);
        if (bytes == null || bytes.length == 0) {
            throw ApiException.badRequest("The uploaded file is empty");
        }
        if (bytes.length > (long) cfg.maxUploadMb() * 1024 * 1024) {
            throw ApiException.badRequest("File exceeds the " + cfg.maxUploadMb() + " MB upload limit");
        }
        String key = buildKey(cfg, projectId, originalName);
        String ct = (contentType == null || contentType.isBlank()) ? "application/octet-stream" : contentType;
        try {
            c.putObject(
                    PutObjectRequest.builder().bucket(cfg.bucket()).key(key).contentType(ct).build(),
                    RequestBody.fromBytes(bytes));
        } catch (S3Exception e) {
            throw storageFailure(e, "save the file");
        } catch (SdkClientException e) {
            throw transportFailure(e, "save the file");
        }
        return "s3://" + cfg.bucket() + "/" + key;
    }

    public String buildKey(StorageConfig cfg, Long projectId, String originalName) {
        String folder = cfg.folder() == null ? "" : cfg.folder().trim()
                .replaceAll("^/+", "").replaceAll("/+$", "");
        String base = folder.isEmpty() ? "" : folder + "/";
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        return base + "projects/" + projectId + "/documents/" + date + "/" + UUID.randomUUID() + "__" + safeName(originalName);
    }

    /** Makes a filename safe for use as an object key. */
    public static String safeName(String name) {
        if (name == null) return "file";
        String tail = name.replace('\\', '/');
        tail = tail.substring(tail.lastIndexOf('/') + 1);
        tail = tail.replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        if (tail.length() > 180) tail = tail.substring(0, 180);
        return tail.isEmpty() ? "file" : tail;
    }

    public S3Ref parse(String s3Uri) {
        if (s3Uri == null) throw ApiException.badRequest("Document has no file reference");
        if (!s3Uri.startsWith("s3://")) throw ApiException.badRequest("Not an object-storage file: " + s3Uri);
        String rest = s3Uri.substring("s3://".length());
        int slash = rest.indexOf('/');
        if (slash <= 0) throw ApiException.badRequest("Malformed S3 URI: " + s3Uri);
        return new S3Ref(rest.substring(0, slash), rest.substring(slash + 1));
    }

    public byte[] download(String s3Uri) {
        S3Ref ref = parse(s3Uri);
        try {
            var res = client(currentConfig())
                    .getObject(GetObjectRequest.builder().bucket(ref.bucket()).key(ref.key()).build());
            return res.readAllBytes();
        } catch (S3Exception e) {
            int code = e.statusCode();
            throw ApiException.badRequest("Storage object is missing or unreadable (HTTP " + code + ")");
        } catch (SdkClientException e) {
            throw transportFailure(e, "read the file");
        } catch (java.io.IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read the storage object");
        }
    }

    /** Best-effort delete of an object (used when a document is removed or re-uploaded). */
    public void deleteQuietly(String s3Uri) {
        if (s3Uri == null || !s3Uri.startsWith("s3://")) return;
        try {
            S3Ref ref = parse(s3Uri);
            client(currentConfig())
                    .deleteObject(DeleteObjectRequest.builder().bucket(ref.bucket()).key(ref.key()).build());
        } catch (Exception e) {
            log.warn("Failed to delete S3 object {}: {}", s3Uri, e.toString());
        }
    }

    /** If a public base URL is configured, direct links point there; otherwise null (use the API proxy). */
    public String publicUrl(StorageConfig cfg, String s3Uri) {
        if (s3Uri == null || !s3Uri.startsWith("s3://") || cfg.publicBaseUrl() == null || cfg.publicBaseUrl().isBlank()) {
            return null;
        }
        S3Ref ref = parse(s3Uri);
        String base = cfg.publicBaseUrl().trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/" + ref.key();
    }

    /**
     * Verifies reachability of the configured (or candidate) storage with a
     * throwaway client: head-bucket + list-bucket. Never touches the live
     * cached client, so a half-typed config cannot clobber a working one.
     */
    public Map<String, Object> testConnection(StorageConfig cfg) {
        if (!cfg.isConfigured()) {
            return Map.of("ok", false, "message", "Bucket and credentials are required to test a connection");
        }
        String where = cfg.endpoint().isBlank()
                ? "S3 (" + cfg.region() + ")"
                : "custom endpoint " + cfg.endpoint();
        S3Client c = null;
        try {
            c = buildClient(cfg);
            c.headBucket(HeadBucketRequest.builder().bucket(cfg.bucket()).build());
            ListObjectsV2Response resp = c.listObjectsV2(b -> {
                b.bucket(cfg.bucket()).maxKeys(3);
                String p = prefix(cfg);
                if (!p.isEmpty()) b.prefix(p);
            });
            long n = resp.keyCount() == null ? 0 : resp.keyCount();
            Map<String, Object> out = new HashMap<>();
            out.put("ok", true);
            out.put("bucket", cfg.bucket());
            out.put("message", "Connected to " + where + " — bucket " + cfg.bucket()
                    + " is reachable (" + n + " objects under the project folders)");
            return out;
        } catch (S3Exception e) {
            int code = e.statusCode();
            return Map.of("ok", false,
                    "message", "HTTP " + code + " from " + where + " — " + clean(e.getMessage()));
        } catch (Exception e) {
            return Map.of("ok", false, "message", clean(e.getMessage()));
        } finally {
            if (c != null) {
                try {
                    c.close();
                } catch (Exception ignored) {
                    // closing a broken test client is not a problem
                }
            }
        }
    }

    /** Maps an S3-level rejection (403/404/...) to a friendly 503 the UI can surface. */
    private static ApiException storageFailure(S3Exception e, String verb) {
        String detail = e.awsErrorDetails() == null ? null : e.awsErrorDetails().errorMessage();
        log.warn("Object storage rejected while trying to {} — HTTP {} {}", verb, e.statusCode(),
                detail == null ? "" : "(" + detail + ")");
        return ApiException.serviceUnavailable(
                "Object storage rejected the request (HTTP " + e.statusCode() + "). "
                        + "Check the bucket name and credentials in Admin → Settings → Object Storage.");
    }

    /** Maps a transport-level failure (endpoint down / unreachable / timeout) to a friendly 503. */
    private static ApiException transportFailure(SdkClientException e, String verb) {
        log.error("Object storage transport failure while trying to {} — {}", verb, e.getMessage());
        return ApiException.serviceUnavailable(
                "Could not reach the configured object storage. The endpoint may be down or unreachable — "
                        + "verify it in Admin → Settings → Object Storage and re-test the connection.");
    }

    private String prefix(StorageConfig cfg) {
        String folder = cfg.folder() == null ? "" : cfg.folder().trim()
                .replaceAll("^/+", "").replaceAll("/+$", "");
        return folder.isEmpty() ? "projects/" : folder + "/projects/";
    }

    private static String clean(String s) {
        if (s == null) return "no further details";
        return s.replaceAll("\\s+", " ").trim();
    }
}
