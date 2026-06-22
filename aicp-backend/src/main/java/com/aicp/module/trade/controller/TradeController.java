package com.aicp.module.trade.controller;

import com.aicp.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/trade")
public class TradeController {

    @GetMapping("/market/search")
    public ApiResponse<Map<String, Object>> searchMarket(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String sort) {
        List<Map<String, Object>> items = new ArrayList<>();

        Map<String, Object> item1 = new LinkedHashMap<>();
        item1.put("script_id", 1);
        item1.put("title", "霸道总裁的替身新娘");
        item1.put("author", Map.of("uuid", "usr_xxx", "nickname", "编剧小王"));
        item1.put("cover_image_url", null);
        item1.put("tags", Map.of("genre", "言情", "plot", List.of("重生", "先婚后爱"),
                "tone", List.of("甜宠", "打脸"), "setting", "现代"));
        item1.put("episode_count", 40);
        item1.put("total_words", 58000);
        item1.put("rating", 4.8);
        item1.put("review_count", 128);
        item1.put("sales_count", 128);
        item1.put("licenses", List.of(
                Map.of("type", "normal", "price", 29.90),
                Map.of("type", "exclusive", "price", 199.90),
                Map.of("type", "buyout", "price", 999.90)));
        item1.put("listed_at", "2026-05-01T00:00:00+08:00");
        items.add(item1);

        Map<String, Object> item2 = new LinkedHashMap<>();
        item2.put("script_id", 2);
        item2.put("title", "重生之商业帝国");
        item2.put("author", Map.of("uuid", "usr_yyy", "nickname", "漫剧达人"));
        item2.put("tags", Map.of("genre", "言情", "plot", List.of("重生", "权谋"),
                "tone", List.of("爽文", "逆袭"), "setting", "现代"));
        item2.put("episode_count", 80);
        item2.put("rating", 4.5);
        item2.put("sales_count", 56);
        item2.put("licenses", List.of(Map.of("type", "normal", "price", 19.90)));
        items.add(item2);

        return ApiResponse.success(Map.of(
                "items", items,
                "pagination", Map.of("page", page, "page_size", pageSize, "total", 2, "total_pages", 1, "has_more", false)));
    }

    @GetMapping("/market/scripts/{id}")
    public ApiResponse<Map<String, Object>> getScriptDetail(@PathVariable Long id) {
        return ApiResponse.success(Map.of("script_id", id, "title", "霸道总裁的替身新娘"));
    }

    @GetMapping("/market/scripts/{id}/preview")
    public ApiResponse<Map<String, Object>> getPreview(@PathVariable Long id) {
        return ApiResponse.success(Map.of("episodes", List.of(
                Map.of("episode_number", 1, "title", "命运的相遇", "content", "苏小晚端着咖啡推门而入..."))));
    }

    @PostMapping("/orders")
    public ApiResponse<Map<String, Object>> createOrder(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(Map.of(
                "order_no", "ORD2026" + System.currentTimeMillis(),
                "amount", 29.90, "status", "pending",
                "expire_at", "2026-06-08T15:45:00+08:00"));
    }

    @GetMapping("/orders/{id}")
    public ApiResponse<Map<String, Object>> getOrder(@PathVariable Long id) {
        return ApiResponse.success(Map.of("order_no", "ORD20260608153000001", "status", "pending"));
    }

    @GetMapping("/orders")
    public ApiResponse<List<Map<String, Object>>> getOrders() {
        return ApiResponse.success(List.of());
    }

    @PostMapping("/orders/{id}/pay")
    public ApiResponse<Map<String, Object>> payOrder(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ApiResponse.success(Map.of(
                "payment_method", body.getOrDefault("payment_method", "wechat"),
                "payment_params", Map.of("appId", "wx1234567890",
                        "timeStamp", String.valueOf(System.currentTimeMillis() / 1000))));
    }

    @PostMapping("/enterprise/purchase-request")
    public ApiResponse<Map<String, Object>> submitPurchaseRequest(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(Map.of("id", 1, "status", "pending"));
    }

    @PutMapping("/enterprise/purchase-request/{id}/approve")
    public ApiResponse<Void> approvePurchaseRequest(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ApiResponse.success();
    }

    @GetMapping("/sales")
    public ApiResponse<Map<String, Object>> getSales() {
        return ApiResponse.success(Map.of("total_revenue", 358.80, "total_orders", 12, "scripts_sold", 3));
    }

    @GetMapping("/earnings")
    public ApiResponse<Map<String, Object>> getEarnings() {
        return ApiResponse.success(Map.of("balance", 287.04, "total_earned", 358.80, "total_withdrawn", 0));
    }

    @PostMapping("/earnings/withdraw")
    public ApiResponse<Map<String, Object>> withdraw(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(Map.of("withdraw_id", "WD_" + System.currentTimeMillis(), "status", "pending"));
    }
}
