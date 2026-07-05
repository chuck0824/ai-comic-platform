package com.aicp.module.trade.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.module.trade.dto.TradeRequests.MarketQuery;
import com.aicp.module.trade.dto.TradeViews.PageView;
import com.aicp.module.trade.dto.TradeViews.ListingCard;
import com.aicp.module.trade.dto.TradeViews.ListingDetail;
import com.aicp.module.trade.dto.TradeViews.PreviewView;
import com.aicp.module.trade.service.TradeMarketQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Public market read endpoints.
 * No workspace context required for browsing the market.
 */
@RestController
@RequestMapping("/api/v1/trade")
@RequiredArgsConstructor
public class TradeController {

    private final TradeMarketQueryService marketQueryService;

    /** Search public market listings. */
    @GetMapping("/market/listings")
    public ApiResponse<PageView<ListingCard>> searchMarket(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String plot,
            @RequestParam(required = false) String tone,
            @RequestParam(required = false) String setting,
            @RequestParam(required = false) String licenseType,
            @RequestParam(required = false) Integer minPrice,
            @RequestParam(required = false) Integer maxPrice,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {

        MarketQuery query = new MarketQuery(keyword, genre, plot, tone, setting,
                licenseType, minPrice, maxPrice, sort, page, pageSize);
        return ApiResponse.success(marketQueryService.search(query));
    }

    /** Get listing detail including license options. */
    @GetMapping("/market/listings/{listingId}")
    public ApiResponse<ListingDetail> getDetail(@PathVariable Long listingId) {
        return ApiResponse.success(marketQueryService.detail(listingId));
    }

    /** Get preview episodes for a listing. */
    @GetMapping("/market/listings/{listingId}/preview")
    public ApiResponse<PreviewView> getPreview(@PathVariable Long listingId) {
        return ApiResponse.success(marketQueryService.preview(listingId));
    }
}
