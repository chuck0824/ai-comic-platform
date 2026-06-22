package com.aicp.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
public class PageResult<T> {
    private List<T> items;
    private Pagination pagination;

    private PageResult(List<T> items, int page, int pageSize, long total) {
        this.items = items;
        this.pagination = new Pagination(page, pageSize, total);
    }

    public static <T> PageResult<T> of(List<T> items, int page, int pageSize, long total) {
        return new PageResult<>(items, page, pageSize, total);
    }

    @Data
    @AllArgsConstructor
    public static class Pagination {
        private int page;
        private int pageSize;
        private long total;
        private int totalPages;
        private boolean hasMore;

        public Pagination(int page, int pageSize, long total) {
            this.page = page;
            this.pageSize = pageSize;
            this.total = total;
            this.totalPages = pageSize > 0 ? (int) Math.ceil((double) total / pageSize) : 0;
            this.hasMore = (long) page * pageSize < total;
        }
    }
}
