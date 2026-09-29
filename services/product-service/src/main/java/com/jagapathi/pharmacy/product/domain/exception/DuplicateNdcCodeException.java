package com.jagapathi.pharmacy.product.domain.exception;

public class DuplicateNdcCodeException extends RuntimeException {
    public DuplicateNdcCodeException(String ndcCode) {
        super("NDC code already exists: " + ndcCode);
    }
}
