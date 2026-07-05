package com.aicp.module.trade.wallet;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * HMAC-signed HTTP adapter for the 3001 internal wallet API.
 */
@Slf4j
@Component
public class HttpWalletClient implements WalletClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String serviceName;
    private final String serviceSecret;

    public HttpWalletClient(
            @Value("${aicp.wallet.base-url:http://localhost:3001}") String baseUrl,
            @Value("${aicp.wallet.service-name:aicp-8080}") String serviceName,
            @Value("${aicp.wallet.service-secret:}") String serviceSecret) {
        this.baseUrl = baseUrl;
        this.serviceName = serviceName;
        this.serviceSecret = serviceSecret;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();

        if (serviceSecret == null || serviceSecret.isBlank()) {
            log.warn("aicp.wallet.service-secret is blank — wallet calls will fail");
        }
    }

    @Override
    public BalanceResult getBalance(String ownerType, String ownerId) {
        String path = "/api/aicp/wallets/" + ownerType + "/" + ownerId;
        Map<String, Object> resp = signedGet(path, null);
        Map<String, Object> data = getData(resp);
        return new BalanceResult(
                toLong(data.get("available_cents")),
                toLong(data.get("frozen_cents")),
                (String) data.getOrDefault("currency", "CNY"));
    }

    @Override
    public PrecheckResult precheck(String ownerType, String ownerId, long amountCents, String permission) {
        Map<String, Object> body = Map.of(
                "owner", Map.of("type", ownerType, "id", ownerId),
                "amount_cents", amountCents,
                "permission", permission != null ? permission : "");
        Map<String, Object> resp = signedPost("/api/aicp/wallets/precheck", body, null);
        Map<String, Object> data = getData(resp);
        return new PrecheckResult(Boolean.TRUE.equals(data.get("allowed")));
    }

    @Override
    public PurchaseResult purchase(PurchaseRequest request, String idempotencyKey) {
        Map<String, Object> body = Map.of(
                "business_order_no", request.businessOrderNo(),
                "buyer", Map.of("type", request.buyerType(), "id", request.buyerId()),
                "seller", Map.of("type", request.sellerType(), "id", request.sellerId()),
                "amount_cents", request.amountCents(),
                "platform_fee_cents", request.platformFeeCents(),
                "currency", request.currency(),
                "idempotency_key", idempotencyKey);
        Map<String, Object> resp = signedPost("/api/aicp/wallet-transfers/purchase", body, idempotencyKey);
        Map<String, Object> data = getData(resp);
        return new PurchaseResult(
                (String) data.get("transfer_no"),
                (String) data.get("status"),
                toLong(data.get("buyer_balance_after")));
    }

    @Override
    public TransferRecord findByBusinessOrder(String orderNo) {
        String path = "/api/aicp/wallet-transfers/by-business-order/" + orderNo;
        Map<String, Object> resp = signedGet(path, null);
        Map<String, Object> data = getData(resp);
        return toTransferRecord(data);
    }

    @Override
    public TransferRecord release(String transferNo, String idempotencyKey) {
        String path = "/api/aicp/wallet-transfers/" + transferNo + "/release";
        Map<String, Object> body = Map.of("idempotency_key", idempotencyKey);
        Map<String, Object> resp = signedPost(path, body, idempotencyKey);
        Map<String, Object> data = getData(resp);
        return toTransferRecord(data);
    }

    @Override
    public TransferRecord reverse(String transferNo, long amountCents, String idempotencyKey) {
        String path = "/api/aicp/wallet-transfers/" + transferNo + "/reverse";
        Map<String, Object> body = Map.of("amount_cents", amountCents, "idempotency_key", idempotencyKey);
        Map<String, Object> resp = signedPost(path, body, idempotencyKey);
        Map<String, Object> data = getData(resp);
        return toTransferRecord(data);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<LedgerEntry> getLedger(String ownerType, String ownerId) {
        String path = "/api/aicp/wallet-ledger/" + ownerType + "/" + ownerId;
        Map<String, Object> resp = signedGet(path, null);
        List<Map<String, Object>> data = (List<Map<String, Object>>) resp.get("data");
        if (data == null) return List.of();
        return data.stream().map(m -> new LedgerEntry(
                (String) m.get("transfer_no"),
                (String) m.get("entry_type"),
                toLong(m.get("amount_cents")),
                toLong(m.get("balance_after")),
                toLong(m.get("created_at")))).toList();
    }

    // -- HTTP helpers --

    private Map<String, Object> signedGet(String path, String idempotencyKey) {
        return doRequest(HttpMethod.GET, path, null, idempotencyKey);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> signedPost(String path, Object body, String idempotencyKey) {
        return doRequest(HttpMethod.POST, path, body, idempotencyKey);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> doRequest(HttpMethod method, String path, Object body, String idempotencyKey) {
        try {
            String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
            String bodyJson = body != null ? objectMapper.writeValueAsString(body) : "";
            String bodyHash = sha256Hex(bodyJson);
            String canonical = method.name() + "\n" + path + "\n" + timestamp + "\n"
                    + (idempotencyKey != null ? idempotencyKey : "") + "\n" + bodyHash;
            String signature = hmacSha256(serviceSecret, canonical);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-AICP-Service", serviceName);
            headers.set("X-AICP-Timestamp", timestamp);
            headers.set("X-AICP-Signature", signature);
            if (idempotencyKey != null) {
                headers.set("Idempotency-Key", idempotencyKey);
            }

            HttpEntity<?> entity = body != null
                    ? new HttpEntity<>(body, headers)
                    : new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl + path, method, entity, Map.class);

            Map<String, Object> respBody = response.getBody();
            if (respBody == null || !Boolean.TRUE.equals(respBody.get("success"))) {
                String msg = respBody != null ? (String) respBody.get("message") : "unknown error";
                throw new RuntimeException("Wallet call failed: " + msg);
            }
            return respBody;

        } catch (Exception e) {
            log.error("Wallet call failed: {} {}", method, path, e);
            throw new RuntimeException("Wallet service unavailable: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getData(Map<String, Object> resp) {
        Object data = resp.get("data");
        if (data instanceof Map) return (Map<String, Object>) data;
        return Map.of();
    }

    private TransferRecord toTransferRecord(Map<String, Object> data) {
        return new TransferRecord(
                (String) data.get("transfer_no"),
                (String) data.get("business_order_no"),
                (String) data.get("status"),
                toLong(data.get("amount_cents")),
                toLong(data.get("reversed_cents")));
    }

    private long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) return Long.parseLong(s);
        return 0L;
    }

    // -- crypto --

    private static String sha256Hex(String data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(data.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    private static String hmacSha256(String key, String message) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] sig = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(sig);
    }
}
