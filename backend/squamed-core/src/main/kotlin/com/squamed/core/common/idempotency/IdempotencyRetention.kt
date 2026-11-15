package com.squamed.core.common.idempotency

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

private val log = LoggerFactory.getLogger(IdempotencyRetention::class.java)

/** 01-ENGINEERING-STANDARDS section 3: idempotency keys live 24 h, then the row (and its
 * stored response) is purged. Nightly at 03:17 UTC - after the 03:00 audit export window. */
@Component
class IdempotencyRetention(
  private val repository: IdempotencyKeyRecordRepository,
) {
  @Scheduled(cron = "0 17 3 * * *", zone = "UTC")
  @Transactional
  fun purgeExpired() {
    val cutoff = Instant.now().minus(24, ChronoUnit.HOURS)
    val deleted = repository.deleteByCreatedAtBefore(cutoff)
    if (deleted > 0) log.info("idempotency retention: purged {} keys older than 24h", deleted)
  }
}
