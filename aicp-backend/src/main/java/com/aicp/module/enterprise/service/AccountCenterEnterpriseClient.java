package com.aicp.module.enterprise.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Typed HTTP client that calls the 3001 account-center for organization
 * management operations. Forwards the original Bearer token for authentication.
 */
@Slf4j
@Component
public class AccountCenterEnterpriseClient {

    @Value("${new-api.base-url:http://localhost:3001}")
    private String baseUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AccountCenterEnterpriseClient() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    // ─── Departments ────────────────────────────────────────────────────────

    public JsonNode listDepartments(String workspaceId, String bearerToken) {
        return get(workspaceId + "/departments", bearerToken);
    }

    public JsonNode createDepartment(String workspaceId, Map<String, Object> body, String bearerToken) {
        return post(workspaceId + "/departments", body, bearerToken);
    }

    public JsonNode updateDepartment(String workspaceId, String departmentId,
                                      Map<String, Object> body, String bearerToken) {
        return patch(workspaceId + "/departments/" + departmentId, body, bearerToken);
    }

    public JsonNode deleteDepartment(String workspaceId, String departmentId, String bearerToken) {
        return delete(workspaceId + "/departments/" + departmentId, bearerToken);
    }

    // ─── Members ────────────────────────────────────────────────────────────

    public JsonNode listMembers(String workspaceId, int page, int size, String bearerToken) {
        return get(workspaceId + "/members?page=" + page + "&size=" + size, bearerToken);
    }

    public JsonNode updateMember(String workspaceId, Long memberId,
                                  Map<String, Object> body, String bearerToken) {
        return patch(workspaceId + "/members/" + memberId, body, bearerToken);
    }

    // ─── Invitations ────────────────────────────────────────────────────────

    public JsonNode createInvitation(String workspaceId, Map<String, Object> body, String bearerToken) {
        return post(workspaceId + "/invitations", body, bearerToken);
    }

    // ─── Roles ──────────────────────────────────────────────────────────────

    public JsonNode listRoles(String workspaceId, String bearerToken) {
        return get(workspaceId + "/roles", bearerToken);
    }

    public JsonNode createRole(String workspaceId, Map<String, Object> body, String bearerToken) {
        return post(workspaceId + "/roles", body, bearerToken);
    }

    public JsonNode updateRole(String workspaceId, String roleId,
                                Object body, String bearerToken) {
        return patch(workspaceId + "/roles/" + roleId, body, bearerToken);
    }

    // ─── Billing ────────────────────────────────────────────────────────────

    public JsonNode getBillingSummary(String workspaceId, String bearerToken) {
        return get(workspaceId + "/billing-summary", bearerToken);
    }

    // ─── HTTP helpers ───────────────────────────────────────────────────────

    private JsonNode get(String path, String bearerToken) {
        String url = baseUrl + "/api/aicp/workspaces/" + path;
        HttpHeaders headers = authHeaders(bearerToken);
        try {
            ResponseEntity<String> resp = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            return parseBody(resp);
        } catch (Exception e) {
            log.error("3001 GET {} failed: {}", url, e.getMessage());
            return null;
        }
    }

    private JsonNode post(String path, Object body, String bearerToken) {
        String url = baseUrl + "/api/aicp/workspaces/" + path;
        HttpHeaders headers = authHeaders(bearerToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            HttpEntity<Object> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> resp = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);
            return parseBody(resp);
        } catch (Exception e) {
            log.error("3001 POST {} failed: {}", url, e.getMessage());
            return null;
        }
    }

    private JsonNode patch(String path, Object body, String bearerToken) {
        String url = baseUrl + "/api/aicp/workspaces/" + path;
        HttpHeaders headers = authHeaders(bearerToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            HttpEntity<Object> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> resp = restTemplate.exchange(
                    url, HttpMethod.PATCH, entity, String.class);
            return parseBody(resp);
        } catch (Exception e) {
            log.error("3001 PATCH {} failed: {}", url, e.getMessage());
            return null;
        }
    }

    private JsonNode delete(String path, String bearerToken) {
        String url = baseUrl + "/api/aicp/workspaces/" + path;
        HttpHeaders headers = authHeaders(bearerToken);
        try {
            ResponseEntity<String> resp = restTemplate.exchange(
                    url, HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
            return parseBody(resp);
        } catch (Exception e) {
            log.error("3001 DELETE {} failed: {}", url, e.getMessage());
            return null;
        }
    }

    private HttpHeaders authHeaders(String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, bearerToken);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        return headers;
    }

    private JsonNode parseBody(ResponseEntity<String> resp) {
        if (!resp.hasBody()) return null;
        try {
            return objectMapper.readTree(resp.getBody());
        } catch (Exception e) {
            log.error("Failed to parse 3001 response: {}", e.getMessage());
            return null;
        }
    }
}
