/**
 * Shared, framework-agnostic building blocks for the Dolos platform.
 *
 * <p>This module holds value objects, identifiers, error envelopes, and constants reused
 * across services. It deliberately has <strong>no</strong> Spring (or other framework)
 * dependency so every module can depend on it freely without coupling to a runtime.
 *
 * <p>Populated with shared types (e.g. {@code Money}, {@code AccountId}, a standard
 * error envelope) as services need them.
 */
package com.dolos.common;
