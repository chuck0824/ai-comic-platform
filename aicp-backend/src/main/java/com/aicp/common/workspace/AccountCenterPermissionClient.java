package com.aicp.common.workspace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * HTTP client that calls the 3001 account-center for workspace membership
 * verification. Forwards the original Bearer token to maintain end-to-end
 * authentication.
 */
@Slf4j
@Component
public class AccountCenterPermissionClient {

    @Value("${new-api.base-url:http://localhost:3001}")
    private String baseUrl;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AccountCenterPermissionClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);  // 5s connection timeout
        factory.setReadTimeout(5000);     // 5s read timeout
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Look up the authenticated user's membership in the given workspace.
     *
     * @param workspaceId the workspace to query
     * @param bearerToken the original Authorization header from the incoming request
     * @return membership data or null if not a member / workspace not found
     * @throws UpstreamUnavailableException if 3001 is unreachable or returns 5xx
     */
    public MembershipResponse membership(String workspaceId, String bearerToken) throws UpstreamUnavailableException {
        // Dev mode: return a trusted mock membership without calling 3001
        if ("dev".equals(activeProfile)) {
            log.debug("Dev mode: returning mock membership for workspace={}", workspaceId);
            String workspaceType = workspaceId.startsWith("enterprise_") || workspaceId.startsWith("ent_") ? "enterprise" : "personal";
            long userId = extractUserIdFromWorkspace(workspaceId);
            List<String> permissions = List.of(
                    "can_generate_script", "can_purchase_script",
                    "can_generate_video", "can_export_no_watermark",
                    "can_manage_assets"
            );
            return new MembershipResponse(workspaceId, workspaceType, userId, permissions);
        }

        String url = baseUrl + "/api/aicp/workspaces/" + workspaceId + "/membership";

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, bearerToken);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode() == HttpStatus.NOT_FOUND || !response.hasBody()) {
                // Unified 404: workspace not found OR user not a member
                return null;
            }

            if (response.getBody() == null) {
                return null;
            }

            return parseMembershipResponse(response.getBody());

        } catch (RestClientException e) {
            log.error("账户中心({})不可用: {}", url, e.getMessage());
            throw new UpstreamUnavailableException("账户中心暂不可用，请稍后重试", e);
        }
    }

    private MembershipResponse parseMembershipResponse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (!root.path("success").asBoolean(false)) {
                return null;
            }
            JsonNode data = root.path("data");
            if (data.isMissingNode()) {
                return null;
            }

            String workspaceId = data.path("workspace_id").asText();
            String workspaceType = data.path("workspace_type").asText();
            long userId = data.path("user_id").asLong();

            List<String> permissions = new ArrayList<>();
            JsonNode permsNode = data.path("permissions");
            if (permsNode.isArray()) {
                for (JsonNode p : permsNode) {
                    permissions.add(p.asText());
                }
            }

            return new MembershipResponse(workspaceId, workspaceType, userId, permissions);
        } catch (Exception e) {
            log.error("解析账户中心响应失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Parsed membership information from the 3001 permission service.
     */
    public record MembershipResponse(
            String workspaceId,
            String workspaceType,
            long userId,
            List<String> permissions) {
    }

    /**
     * Thrown when the 3001 account-center is unreachable or returns a server
     * error. Callers must fail-closed for protected operations.
     */
    public static class UpstreamUnavailableException extends Exception {
        public UpstreamUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Extract user ID from workspace ID format (e.g., "personal_1" → 1).
     */
    private long extractUserIdFromWorkspace(String workspaceId) {
        if (workspaceId == null) return 1L;
        int underscoreIdx = workspaceId.lastIndexOf('_');
        if (underscoreIdx >= 0 && underscoreIdx < workspaceId.length() - 1) {
            try {
                return Long.parseLong(workspaceId.substring(underscoreIdx + 1));
            } catch (NumberFormatException e) {
                // fall through
            }
        }
        return 1L; // default fallback
    }
}
