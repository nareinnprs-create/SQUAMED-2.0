package com.squamed.core.interop.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "integration_connection", schema = "interop")
class IntegrationConnection(
  @Column(name = "hospital_id")
  var hospitalId: UUID? = null,
  @Column(name = "name", nullable = false, length = 160)
  var name: String,
  @Column(name = "connection_type", nullable = false, length = 30)
  var connectionType: String,
  @Column(name = "endpoint", length = 500)
  var endpoint: String? = null,
  @Column(name = "username", length = 255)
  var username: String? = null,
  @Column(name = "password", length = 255)
  var password: String? = null,
  @Column(name = "enabled", nullable = false)
  var enabled: Boolean = true,
  @Column(name = "last_health_check_at")
  var lastHealthCheckAt: Instant? = null,
  @Column(name = "last_success_at")
  var lastSuccessAt: Instant? = null,
  @Column(name = "last_error", length = 4000)
  var lastError: String? = null,
  @Column(name = "error_count", nullable = false)
  var errorCount: Int = 0,
) : BaseEntity()

@Entity
@Table(name = "hl7_message", schema = "interop")
class Hl7MessageLog(
  @Column(name = "hospital_id")
  var hospitalId: UUID? = null,
  @Column(name = "message_type", length = 50)
  var messageType: String? = null,
  @Column(name = "control_id", length = 100)
  var controlId: String? = null,
  @Column(name = "direction", nullable = false, length = 20)
  var direction: String,
  @Column(name = "source", length = 50)
  var source: String? = null,
  @Column(name = "ack_code", length = 10)
  var ackCode: String? = null,
  @Column(name = "raw_message")
  var rawMessage: String? = null,
  @Column(name = "parsed_json")
  var parsedJson: String? = null,
) : BaseEntity()
