package com.dolos.transaction.service;

import java.util.UUID;

/** Thrown when a transaction lookup finds no matching row. */
public class TransactionNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TransactionNotFoundException(UUID id) {
        super("Transaction not found: " + id);
    }
}
