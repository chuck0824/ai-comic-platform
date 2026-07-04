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

import java.util.*;

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

    /**
     * Look up the authenticated user's workspace list.
     *
     * @param bearerToken the original Authorization header
     * @return list of workspace membership summaries
     * @throws UpstreamUnavailableException if 3001 is unreachable
     */
    public List<MembershipResponse> listWorkspaces(String bearerToken) throws UpstreamUnavailableException {
        String url = baseUrl + "/api/aicp/workspaces";

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, bearerToken);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);

            if (!response.hasBody()) {
                return Collections.emptyList();
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            if (!root.path("success").asBoolean(false)) {
                return Collections.emptyList();
            }

            JsonNode items = root.path("data").path("items");
            if (!items.isArray()) {
                return Collections.emptyList();
            }

            List<MembershipResponse> results = new ArrayList<>();
            for (JsonNode item : items) {
                MembershipResponse mr = parseMembershipItem(item);
                if (mr != null) {
                    results.add(mr);
                }
            }
            return results;

        } catch (RestClientException e) {
            log.error("账户中心工作区列表不可用: {}", e.getMessage());
            throw new UpstreamUnavailableException("账户中心暂不可用，请稍后重试", e);
        } catch (Exception e) {
            log.error("解析工作区列表失败: {}", e.getMessage());
            return Collections.emptyList();
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
            return parseMembershipItem(data);
        } catch (Exception e) {
            log.error("解析账户中心响应失败: {}", e.getMessage());
            return null;
        }
    }

    private MembershipResponse parseMembershipItem(JsonNode data) {
        try {
            String workspaceId = data.path("workspace_id").asText();
            String workspaceType = data.path("workspace_type").asText();
            long userId = data.path("user_id").asLong();
            String departmentId = data.path("department_id").asText("");

            // Permissions
            List<String> permissions = new ArrayList<>();
            JsonNode permsNode = data.path("permissions");
            if (permsNode.isArray()) {
                for (JsonNode p : permsNode) {
                    permissions.add(p.asText());
                }
            }

            // Roles
            List<String> roles = new ArrayList<>();
            JsonNode rolesNode = data.path("roles");
            if (rolesNode.isArray()) {
                for (JsonNode r : rolesNode) {
                    roles.add(r.asText());
                }
            }

            // Scoped permission grants
            List<PermissionGrant> grants = new ArrayList<>();
            JsonNode grantsNode = data.path("permission_grants");
            if (grantsNode.isArray()) {
                for (JsonNode g : grantsNode) {
                    String permission = g.path("permission").asText();
                    String scope = g.path("scope").asText("WORKSPACE");
                    Set<String> scopeIds = new LinkedHashSet<>();
                    JsonNode idsNode = g.path("scope_ids");
                    if (idsNode.isArray()) {
                        for (JsonNode id : idsNode) {
                            scopeIds.add(id.asText());
                        }
                    }
                    grants.add(new PermissionGrant(permission, scope, scopeIds));
                }
            }

            return new MembershipResponse(workspaceId, workspaceType, userId,
                    departmentId, permissions, roles, grants);
        } catch (Exception e) {
            log.error("解析成员数据失败: {}", e.getMessage());
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
            String departmentId,
            List<String> permissions,
            List<String> roles,
            List<PermissionGrant> permissionGrants) {
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
}
