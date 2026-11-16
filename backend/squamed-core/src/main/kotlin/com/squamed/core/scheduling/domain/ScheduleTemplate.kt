package com.squamed.core.scheduling.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * Session-based doctor schedule template (01.01): one row = one recurring weekday session for a
 * doctor, generating [SessionSlot]s between validFrom/validTo. sessionType in_person|teleconsult
 * (teleconsult slots feed the chunk-6 telemedicine flow). equipmentIds = masterdata.Equipment ids
 * bound to the session (doctor+room+equipment resource binding, blueprint gap #20).
 */
@Entity
@Table(name = "schedule_template", schema = "scheduling")
class ScheduleTemplate(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID? = null,
  @Column(name = "doctor_id", nullable = false)
  var doctorId: UUID,
  @Column(name = "department_id")
  var departmentId: UUID? = null,
  @Column(name = "session_type", nullable = false, length = 20)
  var sessionType: String = "in_person",
  // ISO weekday 1=Monday .. 7=Sunday
  @Column(name = "weekday", nullable = false)
  var weekday: Int,
  @Column(name = "start_time", nullable = false)
  var startTime: LocalTime,
  @Column(name = "end_time", nullable = false)
  var endTime: LocalTime,
  @Column(name = "slot_minutes", nullable = false)
  var slotMinutes: Int = 15,
  @Column(name = "capacity", nullable = false)
  var capacity: Int = 1,
  @Column(name = "room_id")
  var roomId: UUID? = null,
  // JSON array of masterdata.Equipment ids, e.g. ["...uuid..."] (house pattern: EventOutbox.recipientIds)
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "equipment_ids", columnDefinition = "jsonb")
  var equipmentIds: String? = null,
  @Column(name = "valid_from")
  var validFrom: LocalDate? = null,
  @Column(name = "valid_to")
  var validTo: LocalDate? = null,
  @Column(name = "active", nullable = false)
  var active: Boolean = true,
) : BaseEntity()
