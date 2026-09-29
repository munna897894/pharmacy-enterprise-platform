package com.jagapathi.pharmacy.product.api.response;

import java.util.List;

public record PageResponse<T>(
        List<T> content,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages,
        boolean isLast
) {
}
