package com.squamed.core.audit.service

/**
 * 01.12 dual-review hardening: the durable audit row could not be written. Fail-closed
 * contract — callers must let this propagate so the business transaction (and the
 * unaudited change inside it) rolls back; the generic handler surfaces a 500.
 */
class AuditWriteException(
  action: String,
  cause: Throwable,
) : RuntimeException("AUDIT_WRITE_FAILED action=$action", cause)
