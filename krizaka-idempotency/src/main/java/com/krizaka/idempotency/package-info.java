/**
 * HTTP {@code Idempotency-Key}: an action runs at most once per key, a retry replays its response.
 *
 * <p>Promoted from Orazaka's run starts (ADR-083), where a client whose connection dropped after
 * the engine accepted a paid request could not tell whether the run existed.
 */
package com.krizaka.idempotency;
