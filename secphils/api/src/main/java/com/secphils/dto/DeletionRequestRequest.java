package com.secphils.dto;

/** Optional free-text reason the client adds when asking SECPhils to delete a document. */
public record DeletionRequestRequest(String note) {}
