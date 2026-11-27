package com.squamed.core.ai.service

import com.squamed.core.analytics.api.BillingStats
import com.squamed.core.analytics.api.EncounterStats
import com.squamed.core.analytics.api.OverviewStats
import com.squamed.core.analytics.api.PatientsStats
import com.squamed.core.analytics.service.AnalyticsService
import com.squamed.core.patient.api.PatientSummaryDto
import com.squamed.core.patient.service.PatientService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class AiIntentServiceTest {
  private val analyticsService = mockk<AnalyticsService>(relaxed = true)
  private val patientService = mockk<PatientService>(relaxed = true)
  private lateinit var intentService: AiIntentService

  @BeforeEach
  fun setUp() {
    intentService = AiIntentService(analyticsService, patientService)
    every { analyticsService.patients(any()) } returns
      PatientsStats(
        total = 5,
        byGender = emptyList(),
        byBloodGroup = emptyList(),
        registrationsByMonth = emptyList(),
        byNationality = emptyList(),
      )
    every { analyticsService.overview(any()) } returns
      OverviewStats(
        totalPatients = 5,
        patientsToday = 2,
        activeEncounters = 3,
        encountersToday = 4,
        completedEncountersToday = 1,
        appointmentsToday = 6,
        pendingAppointments = 2,
        queueWaiting = 7,
        avgWaitMinutes = 12.0,
        labOrdersToday = 8,
        labPending = 3,
        invoicesToday = 9,
        revenueTodayQar = 2500.0,
        amountDueQar = 1200.5,
        dispensationsToday = 1,
      )
    every { patientService.search(any(), any()) } returns
      listOf(
        PatientSummaryDto(
          id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
          mrn = "MRN-777",
          hospitalId = null,
          firstName = "Sara",
          middleName = null,
          lastName = "Khan",
          preferredName = null,
          dateOfBirth = null,
          gender = "F",
          phone = null,
          email = null,
          qid = null,
          recordsStatus = null,
        ),
      )
  }

  @Test
  fun `help intent exposes capabilities`() {
    val intent = intentService.parse("help", null)
    assertEquals(AiIntentType.HELP, intent.type)
    assertTrue(intentService.render(intent, "help", null).contains("Navigate"))
    assertTrue(intentService.suggestions().isNotEmpty())
  }

  @Test
  fun `open laboratory resolves to navigate with page label`() {
    val intent = intentService.parse("open laboratory", null)
    assertEquals(AiIntentType.NAVIGATE, intent.type)
    assertEquals("laboratory", intent.target)
    assertEquals("/laboratory", intentService.pageSlug(intent.target))
  }

  @Test
  fun `go to billing resolves to navigate`() {
    val intent = intentService.parse("go to billing", null)
    assertEquals(AiIntentType.NAVIGATE, intent.type)
    assertEquals("/billing", intentService.pageSlug(intent.target))
  }

  @Test
  fun `unknown destination suggests known pages`() {
    val intent = intentService.parse("open foobar", null)
    assertEquals(AiIntentType.NAVIGATE, intent.type)
    assertNotNull(intent.note)
    assertTrue(intent.note!!.contains("foobar"))
  }

  @Test
  fun `how many patients counts via analytics`() {
    val intent = intentService.parse("how many patients?", null)
    assertEquals(AiIntentType.COUNT, intent.type)
    assertEquals(5L, intent.count)
    assertEquals("patients", intent.entityLabel)
    assertTrue(intentService.render(intent, "how many patients?", null).contains("5 patients"))
  }

  @Test
  fun `bare count phrase is recognised too`() {
    val intent = intentService.parse("count appointments", null)
    assertEquals(AiIntentType.COUNT, intent.type)
    assertNotNull(intent.count)
  }

  @Test
  fun `revenue today produces analytics snapshot`() {
    val intent = intentService.parse("revenue today", null)
    assertEquals(AiIntentType.ANALYTICS, intent.type)
    val rendered = intentService.render(intent, "revenue today", null)
    assertTrue(rendered.contains("QR 2500.00"))
    assertTrue(rendered.contains("QR 1200.50"))
  }

  @Test
  fun `search patient routes to registry search`() {
    val intent = intentService.parse("search patient Sara", null)
    assertEquals(AiIntentType.SEARCH_PATIENTS, intent.type)
    assertEquals("11111111-1111-1111-1111-111111111111", intent.target)
    assertTrue(intent.detail.orEmpty().contains("MRN-777"))
  }

  @Test
  fun `fallback chat intent still produces a response`() {
    val intent = intentService.parse("hello there", null)
    assertEquals(AiIntentType.CHAT, intent.type)
    assertTrue(intentService.render(intent, "hello there", null).isNotBlank())
  }

  @Test
  fun `pdf page slug normalisation is stable`() {
    assertEquals("/appointments", intentService.pageSlug("appointments"))
    assertEquals("/communication", intentService.pageSlug("communication"))
    assertEquals("/integrations", intentService.pageSlug("interop"))
  }

  @Test
  fun `redactor keeps uuid ids intact even when a group looks like a phone number`() {
    val redactor = PromptRedactor()
    // First group is all digits: the phone rule would otherwise eat it and the command bar
    // would answer AI_ACTION_ADMISSION_REQUIRED for a perfectly valid id.
    val id = "12345678-1111-2222-3333-444455556666"
    val result = redactor.redact("prepare discharge $id for MQ-2024-1234 on 55551234")
    assertTrue(result.redacted, "real PHI in the same prompt is still redacted")
    assertTrue(result.text.contains(id), "uuid survives redaction: ${result.text}")
    assertTrue(result.text.contains("[MRN]"), "MRN still redacted")
    assertTrue(result.text.contains("[PHONE]"), "phone still redacted")
  }
}
