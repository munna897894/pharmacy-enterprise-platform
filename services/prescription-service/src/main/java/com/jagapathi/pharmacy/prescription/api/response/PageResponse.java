package com.jagapathi.pharmacy.prescription.api.response;

import java.util.List;

public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long total,
    int totalPages
) {
}
