package com.squamed.core.patient.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "patient_record", schema = "patient")
class PatientRecord(
  @Column(name = "hospital_id")
  var hospitalId: UUID? = null,
  @Column(name = "mrn", nullable = false, length = 50)
  var mrn: String,
  @Column(name = "external_id", length = 100)
  var externalId: String? = null,
  @Column(name = "title", length = 20)
  var title: String? = null,
  @Column(name = "first_name", nullable = false, length = 100)
  var firstName: String,
  @Column(name = "middle_name", length = 100)
  var middleName: String? = null,
  @Column(name = "last_name", nullable = false, length = 100)
  var lastName: String,
  @Column(name = "preferred_name", length = 100)
  var preferredName: String? = null,
  @Column(name = "date_of_birth", nullable = false)
  var dateOfBirth: LocalDate,
  @Column(name = "gender", nullable = false, length = 20)
  var gender: String,
  @Column(name = "blood_group", length = 5)
  var bloodGroup: String? = null,
  @Column(name = "nationality", length = 100)
  var nationality: String? = null,
  @Column(name = "religion", length = 50)
  var religion: String? = null,
  @Column(name = "marital_status", length = 20)
  var maritalStatus: String? = null,
  @Column(name = "occupation", length = 100)
  var occupation: String? = null,
  @Column(name = "photo_url", length = 500)
  var photoUrl: String? = null,
  @Column(name = "phone", length = 30)
  var phone: String? = null,
  @Column(name = "alternate_phone", length = 30)
  var alternatePhone: String? = null,
  @Column(name = "email", length = 150)
  var email: String? = null,
  @Column(name = "address_line1", length = 300)
  var addressLine1: String? = null,
  @Column(name = "address_line2", length = 300)
  var addressLine2: String? = null,
  @Column(name = "city", length = 100)
  var city: String? = null,
  @Column(name = "country", length = 100)
  var country: String? = null,
  @Column(name = "postal_code", length = 20)
  var postalCode: String? = null,
  @Column(name = "emergency_contact_name", length = 150)
  var emergencyContactName: String? = null,
  @Column(name = "emergency_contact_phone", length = 30)
  var emergencyContactPhone: String? = null,
  @Column(name = "emergency_contact_relation", length = 50)
  var emergencyContactRelation: String? = null,
  @Column(name = "qid", length = 20)
  var qid: String? = null,
  @Column(name = "passport_number", length = 30)
  var passportNumber: String? = null,
  @Column(name = "id_type", length = 30)
  var idType: String? = null,
  @Column(name = "id_number", length = 50)
  var idNumber: String? = null,
  @Column(name = "insurance_company", length = 150)
  var insuranceCompany: String? = null,
  @Column(name = "insurance_member_id", length = 100)
  var insuranceMemberId: String? = null,
  @Column(name = "insurance_policy_no", length = 100)
  var insurancePolicyNo: String? = null,
  @Column(name = "insurance_plan", length = 100)
  var insurancePlan: String? = null,
  @Column(name = "is_deceased", nullable = false)
  var isDeceased: Boolean = false,
  @Column(name = "death_date")
  var deathDate: LocalDate? = null,
  @Column(name = "deceased_note", length = 255)
  var deceasedNote: String? = null,
  @Column(name = "language_preference", length = 30)
  var languagePreference: String = "en",
  @Column(name = "communication_preference", length = 30)
  var communicationPreference: String = "phone",
  @Column(name = "registration_channel", length = 30)
  var registrationChannel: String = "walk-in",
  @Column(name = "records_status", nullable = false, length = 20)
  var recordsStatus: String = "active",
) : BaseEntity()
