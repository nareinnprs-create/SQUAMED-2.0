package com.squamed.core.communication.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "message_attachment", schema = "communication")
class MessageAttachment(
  @Column(name = "message_id", nullable = false)
  var messageId: UUID,
  @Column(name = "file_name", length = 255)
  var fileName: String? = null,
  @Column(name = "content_type", length = 120)
  var contentType: String? = null,
  @Column(name = "size_bytes")
  var sizeBytes: Long? = null,
  @Column(name = "storage_object_key", length = 255)
  var storageObjectKey: String? = null,
) : BaseEntity()
