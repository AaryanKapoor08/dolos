package com.dolos.graph.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One directed {@code TRANSACTED} edge as seen from an account's neighborhood: the counterparty on
 * the other end, the amount, and when it occurred.
 *
 * @param counterparty the account on the other end of the edge
 * @param amount       the transaction amount
 * @param occurredAt   when the transaction took place
 */
public record EdgeView(String counterparty, BigDecimal amount, Instant occurredAt) {}
