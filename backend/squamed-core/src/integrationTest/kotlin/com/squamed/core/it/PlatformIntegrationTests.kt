package com.squamed.core.it

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.RestTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.random.Random

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class PlatformIntegrationTests {
  @LocalServerPort
  var port: Int = 0

  // Mirrors the production mapper (Boot auto-configures JavaTimeModule; Instant → ISO-8601 string).
  private val om =
    ObjectMapper()
      .registerModule(
        com.fasterxml.jackson.datatype.jsr310
          .JavaTimeModule(),
      ).disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

  private val rest: RestTemplate by lazy {
    RestTemplateBuilder().rootUri("http://localhost:$port").build()
  }

  data class ApiResp(
    val status: Int,
    val body: String,
  )

  private fun api(
    method: HttpMethod,
    path: String,
    bodyJson: String? = null,
    token: String? = null,
    extraHeaders: Map<String, String> = emptyMap(),
  ): ApiResp {
    val headers =
      HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
        if (token != null) setBearerAuth(token)
        extraHeaders.forEach { (name, value) -> add(name, value) }
      }
    val entity = HttpEntity(bodyJson, headers)
    return try {
      val resp = rest.exchange(path, method, entity, String::class.java)
      ApiResp(resp.statusCode.value(), resp.body ?: "")
    } catch (e: RestClientResponseException) {
      ApiResp(e.statusCode.value(), e.responseBodyAsString ?: "")
    }
  }

  private fun json(vararg pairs: Pair<String, Any?>): String = om.writeValueAsString(mapOf(*pairs))

  private fun `data`(r: ApiResp): JsonNode = om.readTree(r.body)["data"]

  private fun assert2xx(
    r: ApiResp,
    what: String,
  ) {
    assertTrue(r.status in 200..299, "$what -> HTTP ${r.status}: ${r.body.take(300)}")
  }

  private fun assert4xx(
    r: ApiResp,
    what: String,
  ) {
    assertTrue(r.status in 400..499, "$what -> HTTP ${r.status}, expected 4xx: ${r.body.take(300)}")
  }

  private fun suffix(): String = "${System.currentTimeMillis()}-${Random.nextInt(1000, 9999)}"

  private var tokenCache: String? = null

  private fun adminToken(): String {
    tokenCache?.let { return it }
    val r = api(HttpMethod.POST, "/api/v1/auth/login", json("username" to "admin@squamed.local", "password" to "Admin@12345"))
    assertEquals(200, r.status, "admin login failed: ${r.body.take(300)}")
    val node = `data`(r)
    val token = node.get("accessToken").asText()
    tokenCache = token
    return token
  }

  private var seedHospitalId: String? = null

  // PIT seed: no migration or bootstrap creates a hospital, yet the workflow tests
  // consume an existing hospital + department. Seed both once (single fork, sequential).
  private fun ensureSeed(token: String): String {
    seedHospitalId?.let { return it }
    val hospitals = `data`(api(HttpMethod.GET, "/api/v1/hospital-config/hospitals", token = token))
    val hospitalId =
      if (hospitals.size() > 0) {
        hospitals[0]["id"].asText()
      } else {
        val r =
          api(
            HttpMethod.POST,
            "/api/v1/hospital-config/hospitals",
            json("name" to "SQUAMED PIT Hospital", "code" to "SQM-PIT", "country" to "India"),
            token,
          )
        assert2xx(r, "seed hospital")
        `data`(r)["id"].asText()
      }
    val departments =
      `data`(api(HttpMethod.GET, "/api/v1/hospital-config/hospitals/$hospitalId/departments", token = token))
    if (departments.size() == 0) {
      val r =
        api(
          HttpMethod.POST,
          "/api/v1/hospital-config/hospitals/$hospitalId/departments",
          json("name" to "PIT Outpatient", "code" to "PIT-OPD", "type" to "outpatient"),
          token,
        )
      assert2xx(r, "seed department")
    }
    seedHospitalId = hospitalId
    return hospitalId
  }

  private fun noRoleToken(): String {
    val s = suffix()
    val email = "noperm-$s@test.local"
    assert2xx(
      api(HttpMethod.POST, "/api/v1/auth/register", json("email" to email, "password" to "Passw0rd!123", "firstName" to "No", "lastName" to "Perms")),
      "register no-role user: $email",
    )
    val login = api(HttpMethod.POST, "/api/v1/auth/login", json("username" to email, "password" to "Passw0rd!123"))
    assert2xx(login, "login no-role user")
    return `data`(login)["accessToken"].asText()
  }

  @BeforeEach
  fun noopSetUp() {
  }

  companion object {
    private val EXTERNAL = System.getenv("SQUAMED_IT_EXTERNAL") == "true"

    private val postgres: PostgreSQLContainer<*>? =
      if (EXTERNAL) {
        null
      } else {
        PostgreSQLContainer("postgres:16-alpine")
          .withDatabaseName("squamed")
          .withUsername("squamed")
          .withPassword("squamed")
          .withStartupTimeout(Duration.ofMinutes(3))
      }

    private val redis: GenericContainer<*>? =
      if (EXTERNAL) {
        null
      } else {
        GenericContainer("redis:7-alpine")
          .withExposedPorts(6379)
          .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2)))
      }

    private val kafka: GenericContainer<*>? =
      if (EXTERNAL) {
        null
      } else {
        GenericContainer("apache/kafka:3.7.1")
          .withEnv(
            mapOf(
              "KAFKA_NODE_ID" to "1",
              "KAFKA_PROCESS_ROLES" to "controller,broker",
              "KAFKA_CONTROLLER_QUORUM_VOTERS" to "1@kafka:9093",
              "KAFKA_LISTENERS" to "PLAINTEXT://:9092,CONTROLLER://:9093",
              "KAFKA_ADVERTISED_LISTENERS" to "PLAINTEXT://localhost:9092",
              "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP" to "CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT",
              "KAFKA_CONTROLLER_LISTENER_NAMES" to "CONTROLLER",
              "KAFKA_INTER_BROKER_LISTENER_NAME" to "PLAINTEXT",
              "KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR" to "1",
              "KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR" to "1",
              "KAFKA_TRANSACTION_STATE_LOG_MIN_ISR" to "1",
              "KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS" to "0",
              "KAFKA_AUTO_CREATE_TOPICS_ENABLE" to "true",
            ),
          ).withExposedPorts(9092)
          .waitingFor(
            Wait
              .forLogMessage(".*Kafka Server started.*", 1)
              .withStartupTimeout(Duration.ofMinutes(3)),
          )
      }

    private val opensearch: GenericContainer<*>? =
      if (EXTERNAL) {
        null
      } else {
        GenericContainer("opensearchproject/opensearch:2.14.0")
          .withEnv(
            mapOf(
              "discovery.type" to "single-node",
              "DISABLE_SECURITY_PLUGIN" to "true",
              "OPENSEARCH_JAVA_OPTS" to "-Xms512m -Xmx512m",
              "bootstrap.memory_lock" to "false",
            ),
          ).withExposedPorts(9200)
          .waitingFor(
            Wait
              .forHttp("/_cluster/health")
              .forPort(9200)
              .forStatusCode(200)
              .withStartupTimeout(Duration.ofMinutes(3)),
          ).withStartupAttempts(3)
      }

    // 00.07 patient photos: S3-API object storage. pgsty/silo = maintained MinIO fork (compose parity).
    private val minio: GenericContainer<*>? =
      if (EXTERNAL) {
        null
      } else {
        GenericContainer("pgsty/silo:RELEASE.2026-11-12T00-00-00Z")
          .withCommand("server", "/data")
          .withEnv(
            mapOf(
              "MINIO_ROOT_USER" to "squamed",
              "MINIO_ROOT_PASSWORD" to "squamed_dev_password",
            ),
          ).withExposedPorts(9000)
          .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2)))
      }

    init {
      if (!EXTERNAL) {
        listOfNotNull(postgres, redis, kafka, opensearch, minio).forEach { container ->
          if (!container.isRunning) {
            container.start()
          }
          Runtime.getRuntime().addShutdownHook(
            Thread {
              if (container.isRunning) {
                container.stop()
              }
            },
          )
        }
      }
    }

    @JvmStatic
    @DynamicPropertySource
    fun properties(registry: DynamicPropertyRegistry) {
      // 00.16: no OTLP export during PIT (no collector in CI); spans asserted via the SPI seam.
      registry.add("otel.traces.exporter") { "none" }
      if (EXTERNAL) {
        registry.add("spring.datasource.url") { "jdbc:postgresql://localhost:5433/squamed" }
        registry.add("spring.datasource.username") { "squamed" }
        registry.add("spring.datasource.password") { "squamed_dev_password" }
        registry.add("spring.data.redis.host") { "localhost" }
        registry.add("spring.data.redis.port") { 6380 }
        registry.add("spring.kafka.bootstrap-servers") { "localhost:9092" }
        registry.add("squamed.search.opensearch-uris") { "http://localhost:9200" }
        registry.add("squamed.search.reindex-on-startup") { "false" }
        // Photos + documents point at the compose MinIO (defaults in application.yml); explicit for clarity.
        registry.add("squamed.photos.endpoint") { "http://localhost:9000" }
        registry.add("squamed.documents.endpoint") { "http://localhost:9000" }
      } else {
        registry.add("spring.datasource.url") { postgres!!.jdbcUrl }
        registry.add("spring.datasource.username") { postgres!!.username }
        registry.add("spring.datasource.password") { postgres!!.password }
        registry.add("spring.data.redis.host") { redis!!.host }
        registry.add("spring.data.redis.port") { redis!!.getMappedPort(6379) }
        registry.add("spring.kafka.bootstrap-servers") { "localhost:${kafka!!.getMappedPort(9092)}" }
        registry.add("squamed.search.opensearch-uris") { "http://localhost:${opensearch!!.getMappedPort(9200)}" }
        registry.add("squamed.search.reindex-on-startup") { "false" }
        registry.add("squamed.photos.endpoint") { "http://localhost:${minio!!.getMappedPort(9000)}" }
        registry.add("squamed.photos.access-key") { "squamed" }
        registry.add("squamed.photos.secret-key") { "squamed_dev_password" }
        registry.add("squamed.documents.endpoint") { "http://localhost:${minio!!.getMappedPort(9000)}" }
        registry.add("squamed.documents.access-key") { "squamed" }
        registry.add("squamed.documents.secret-key") { "squamed_dev_password" }
      }
    }
  }

  @Nested
  inner class JourneyTests {
    @Test
    fun fullPatientRevenueJourney() {
      val token = adminToken()

      assert2xx(api(HttpMethod.GET, "/api/v1/hospital-config/hospitals", token = token), "GET hospitals")
      val hospitalId = ensureSeed(token)

      val s = suffix()
      val dept =
        api(
          HttpMethod.POST,
          "/api/v1/hospital-config/hospitals/$hospitalId/departments",
          json(
            "name" to "IT Dept $s",
            "code" to "IT-$s",
            "type" to "IT-$s",
          ),
          token,
        )
      assert2xx(dept, "create department")
      val departmentId = `data`(dept)["id"].asText()

      val tree = api(HttpMethod.GET, "/api/v1/hospital-config/hospitals/$hospitalId/tree", token = token)
      assert2xx(tree, "hospital tree")

      val drug =
        api(
          HttpMethod.POST,
          "/api/v1/master-data/drugs",
          json(
            "code" to "DRG-$s",
            "name" to "Journey Drug $s",
            "category" to "General",
            "unit" to "tab",
          ),
          token,
        )
      assert2xx(drug, "create drug")
      val drugId = `data`(drug)["id"].asText()

      val lab =
        api(
          HttpMethod.POST,
          "/api/v1/master-data/lab-tests",
          json(
            "code" to "LBT-$s",
            "name" to "Journey CBC $s",
            "category" to "Hematology",
            "unit" to "x10^9/L",
          ),
          token,
        )
      assert2xx(lab, "create lab test")
      val labTest = `data`(lab)

      val login =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/auth/login",
            json(
              "username" to "admin@squamed.local",
              "password" to "Admin@12345",
            ),
          ),
        )
      val adminUserId: String =
        login.get("userId")?.asText()
          ?: login.get("user").get("id").asText()

      val patient =
        api(
          HttpMethod.POST,
          "/api/v1/patients",
          json(
            "firstName" to "Journey",
            "lastName" to "Patient$s",
            "gender" to "male",
            "dateOfBirth" to "1992-05-15",
            "phone" to "55${System.currentTimeMillis()}".take(12),
            "qid" to "2$s".take(11),
            "nationality" to "Qatar",
            "city" to "Doha",
          ),
          token,
        )
      assert2xx(patient, "create patient")
      val patientId = `data`(patient)["patient"]["id"].asText()
      val mrn = `data`(patient)["patient"]["mrn"].asText()
      assertTrue(mrn.matches(Regex("^MQ-\\d{4}-\\d{4,}$")), "MRN format: $mrn")

      val searchByName = api(HttpMethod.GET, "/api/v1/patients?query=Patient$s", token = token)
      assert2xx(searchByName, "search patients by name")

      val match =
        api(
          HttpMethod.POST,
          "/api/v1/patients/match",
          json(
            "firstName" to "Journey",
            "lastName" to "Patient$s",
            "gender" to "male",
            "dateOfBirth" to "1992-05-15",
            "qid" to "2$s".take(11),
          ),
          token,
        )
      assert2xx(match, "EMPI match")

      val allergy =
        api(
          HttpMethod.POST,
          "/api/v1/patients/$patientId/allergies",
          json(
            "allergen" to "Penicillin",
            "reaction" to "Rash",
            "severity" to "moderate",
          ),
          token,
        )
      assert2xx(allergy, "add allergy")

      val appt =
        api(
          HttpMethod.POST,
          "/api/v1/appointments",
          json(
            "patientId" to patientId,
            "departmentId" to departmentId,
            "doctorId" to adminUserId,
            "appointmentType" to "OPD",
            "scheduledStart" to
              java.time.Instant
                .now()
                .plusSeconds(3600)
                .toString(),
            "scheduledEnd" to
              java.time.Instant
                .now()
                .plusSeconds(7200)
                .toString(),
            "durationMinutes" to 30,
            "reason" to "IT journey appointment",
            "priority" to "normal",
          ),
          token,
        )
      assert2xx(appt, "create appointment")
      val appointmentId = `data`(appt)["id"].asText()

      val visit =
        api(
          HttpMethod.POST,
          "/api/v1/reception/visits",
          json(
            "patientId" to patientId,
            "appointmentId" to appointmentId,
            "visitType" to "OPD",
            "departmentId" to departmentId,
            "reason" to "Journey visit",
            "priority" to "normal",
            "addToQueue" to true,
            "queueType" to "OPD",
          ),
          token,
        )
      assert2xx(visit, "check-in visit")
      val visitId = `data`(visit)["visit"]["id"].asText()
      val visitNumber = `data`(visit)["visit"]["visitNumber"].asText()
      assertTrue(visitNumber.startsWith("VN-"), "visit number: $visitNumber")
      val tokenNumber = `data`(visit)["queueEntry"]["tokenNumber"].asText()
      assertTrue(tokenNumber.matches(Regex("^[A-Z]-\\d+$")), "queue token: $tokenNumber")

      val board = api(HttpMethod.GET, "/api/v1/queue/board?queueType=OPD", token = token)
      assert2xx(board, "queue board")

      val qstart = api(HttpMethod.PATCH, "/api/v1/queue/entries/OPD/$tokenNumber/start", json("servedBy" to adminUserId), token)
      assert2xx(qstart, "queue entry start")
      val qstats = api(HttpMethod.GET, "/api/v1/queue/statistics?queueType=OPD", token = token)
      assert2xx(qstats, "queue statistics")

      val enc =
        api(
          HttpMethod.POST,
          "/api/v1/encounters",
          json(
            "patientId" to patientId,
            "visitId" to visitId,
            "departmentId" to departmentId,
            "chiefComplaint" to "Fever and cough for 3 days",
            "reason" to "Outpatient consultation",
          ),
          token,
        )
      assert2xx(enc, "start encounter")
      val encounterId = `data`(enc)["id"].asText()

      val vitals =
        api(
          HttpMethod.POST,
          "/api/v1/encounters/$encounterId/vitals",
          json(
            "bloodPressureSystolic" to 120,
            "bloodPressureDiastolic" to 80,
            "heartRate" to 88,
            "respiratoryRate" to 18,
            "temperatureC" to 37.8,
            "spo2Percent" to 98,
            "painScore" to 3,
            "patientMrn" to mrn,
          ),
          token,
        )
      assert2xx(vitals, "record vitals")

      val note =
        api(
          HttpMethod.POST,
          "/api/v1/encounters/$encounterId/notes",
          json(
            "noteType" to "SOAP",
            "title" to "Initial consult",
            "subjective" to "Cough",
            "objective" to "Vitals recorded",
            "assessment" to "URI suspect",
            "plan" to "CBC",
            "patientMrn" to mrn,
          ),
          token,
        )
      assert2xx(note, "add SOAP note")
      val noteId = `data`(note)["id"].asText()
      val sign = api(HttpMethod.POST, "/api/v1/encounters/notes/$noteId/sign", json("method" to "PIN", "pin" to "1357", "patientMrn" to mrn), token)
      assertTrue(sign.status == 200 || sign.status == 409, "sign note -> ${sign.status}")

      // SAFETY L49: the journey patient already documents a real allergy (Penicillin above)
      // before any prescription - an NKA attestation would conflict, so none is posted here.

      val rx =
        api(
          HttpMethod.POST,
          "/api/v1/encounters/$encounterId/prescriptions",
          json(
            "drugId" to drugId,
            "drugName" to "Journey Drug",
            "strength" to "500 mg",
            "form" to "tablet",
            "dose" to "1 tab",
            "frequency" to "TID",
            "route" to "oral",
            "quantity" to 10,
            "patientMrn" to mrn,
          ),
          token,
        )
      assert2xx(rx, "prescribe medication")

      val labOrder =
        api(
          HttpMethod.POST,
          "/api/v1/lab/orders",
          json(
            "patientId" to patientId,
            "encounterId" to encounterId,
            "tests" to listOf(mapOf("testCode" to labTest["code"].asText(), "testName" to labTest["name"].asText())),
            "priority" to "routine",
            "clinicalNote" to "Journey order",
            "orderedBy" to adminUserId,
          ),
          token,
        )
      assert2xx(labOrder, "create lab order")
      val orderNo = `data`(labOrder)["order"]["orderNumber"].asText()
      assertTrue(orderNo.startsWith("LO-"), "lab order number: $orderNo")
      val orderId = `data`(labOrder)["order"]["id"].asText()

      val detail = api(HttpMethod.GET, "/api/v1/lab/orders/$orderId", token = token)
      assert2xx(detail, "lab order detail")
      val testRequestId = `data`(detail)["tests"][0]["id"].asText()
      assertNotNull(testRequestId)

      assert2xx(
        api(HttpMethod.POST, "/api/v1/lab/orders/$orderId/collect", json("collectedBy" to adminUserId), token),
        "collect specimen",
      )
      assert2xx(api(HttpMethod.POST, "/api/v1/lab/orders/$orderId/receive", json(), token), "receive specimen")
      assert2xx(api(HttpMethod.POST, "/api/v1/lab/orders/$orderId/start", json(), token), "start analysis")
      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/lab/orders/$orderId/results",
          json(
            "results" to
              listOf(
                mapOf(
                  "testRequestId" to testRequestId,
                  "resultValue" to "5.2",
                  "unit" to labTest["unit"].asText(),
                  "referenceRange" to labTest["referenceRange"]?.asText(),
                ),
              ),
          ),
          token,
        ),
        "enter results",
      )
      assert2xx(api(HttpMethod.POST, "/api/v1/lab/orders/$orderId/verify", json(), token), "verify results")
      val release = api(HttpMethod.POST, "/api/v1/lab/orders/$orderId/release", json(), token)
      assert2xx(release, "release results")
      assertEquals("released", `data`(release)["order"]["resultStatus"].asText())

      val stock =
        api(
          HttpMethod.POST,
          "/api/v1/pharmacy/stock",
          json(
            "drugId" to drugId,
            "drugName" to "Journey Drug",
            "batchNumber" to "JT-$s",
            "quantityOnHand" to 100,
            "reorderLevel" to 10,
            "unitPrice" to 12.5,
            "location" to "A-1",
          ),
          token,
        )
      assert2xx(stock, "create stock")
      val stockId = `data`(stock)["id"].asText()
      val qtyBefore = `data`(stock)["quantityOnHand"].asInt()

      val disp =
        api(
          HttpMethod.POST,
          "/api/v1/pharmacy/dispensations",
          json(
            "patientId" to patientId,
            "encounterId" to encounterId,
            "stockId" to stockId,
            "drugId" to drugId,
            "drugName" to "Journey Drug",
            "quantity" to 5,
            "unitPrice" to 12.5,
          ),
          token,
        )
      assert2xx(disp, "dispense 5 units")
      assertEquals("dispensed", `data`(disp)["status"].asText())
      val dispenseId = `data`(disp)["id"].asText()
      assert2xx(api(HttpMethod.POST, "/api/v1/pharmacy/dispensations/$dispenseId/verify", json(), token), "verify dispensation")

      val stockList = api(HttpMethod.GET, "/api/v1/pharmacy/stock", token = token)
      assert2xx(stockList, "list stock")
      val currentQty = `data`(stockList).first { it["id"].asText() == stockId }["quantityOnHand"].asInt()
      assertEquals(qtyBefore - 5, currentQty, "stock decremented")

      val oversell =
        api(
          HttpMethod.POST,
          "/api/v1/pharmacy/dispensations",
          json(
            "patientId" to patientId,
            "stockId" to stockId,
            "drugId" to drugId,
            "drugName" to "Journey Drug",
            "quantity" to 99999,
          ),
          token,
        )
      assert4xx(oversell, "oversell rejected")

      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/billing/tariffs",
          json(
            "code" to "JT-CONSULT",
            "name" to "Journey Consult",
            "category" to "consultation",
            "priceQar" to 200,
            "insuranceApplicable" to false,
          ),
          token,
        ),
        "create tariff",
      )

      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/billing/charges",
          json(
            "patientId" to patientId,
            "encounterId" to encounterId,
            "chargeCode" to "JT-CONSULT",
            "chargeName" to "Journey Consult",
            "quantity" to 1,
            "unitPriceQar" to 200,
            "insuranceClaimable" to false,
          ),
          token,
        ),
        "post charge",
      )

      val inv = api(HttpMethod.POST, "/api/v1/billing/invoices", json("patientId" to patientId, "encounterId" to encounterId), token)
      assert2xx(inv, "issue invoice")
      val invoiceNo = `data`(inv)["invoice"]["invoiceNumber"].asText()
      assertTrue(invoiceNo.startsWith("INV-"), "invoice number: $invoiceNo")
      val invoiceId = `data`(inv)["invoice"]["id"].asText()
      val due = `data`(inv)["invoice"]["amountDueQar"].asDouble()
      assertTrue(due > 0, "invoice unpaid")

      val pay =
        api(
          HttpMethod.POST,
          "/api/v1/billing/invoices/$invoiceId/payments",
          json(
            "method" to "CASH",
            "amountQar" to due,
            "reference" to "JT-PAY",
          ),
          token,
        )
      assert2xx(pay, "pay invoice")
      assertEquals("completed", `data`(pay)["status"].asText())

      val settled = `data`(api(HttpMethod.GET, "/api/v1/billing/invoices/$invoiceId", token = token))
      assertEquals("paid", settled["invoice"]["status"].asText())
      assertEquals(0.0, settled["invoice"]["amountDueQar"].asDouble(), 0.001, "invoice settled")

      val health = api(HttpMethod.GET, "/actuator/health", token = token)
      assert2xx(health, "actuator health")
      assertEquals("UP", om.readTree(health.body)["status"].asText())
    }

    @Test
    fun collaborationAndWorkflowJourney() {
      val token = adminToken()
      val login =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/auth/login",
            json(
              "username" to "admin@squamed.local",
              "password" to "Admin@12345",
            ),
          ),
        )
      val adminUserId: String = login.get("userId")?.asText() ?: login.get("user").get("id").asText()
      val s = suffix()

      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/tasks",
          json(
            "title" to "Follow up $s",
            "description" to "Journey task",
            "priority" to "normal",
            "relatedType" to "PATIENT",
            "relatedId" to UUID.randomUUID().toString(),
            "assigneeId" to adminUserId,
            "dueAt" to
              java.time.Instant
                .now()
                .plusSeconds(86400)
                .toString(),
          ),
          token,
        ),
        "create task",
      )
      val taskId =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/tasks",
            json(
              "title" to "Second $s",
              "description" to "x",
              "priority" to "high",
              "assigneeId" to adminUserId,
            ),
            token,
          ),
        )["id"].asText()
      assert2xx(api(HttpMethod.PATCH, "/api/v1/tasks/$taskId/status", json("status" to "in_progress"), token), "task in_progress")
      assert2xx(api(HttpMethod.PATCH, "/api/v1/tasks/$taskId/status", json("status" to "completed"), token), "task completed")

      assert2xx(api(HttpMethod.GET, "/api/v1/notifications/unread-count", token = token), "notification unread-count")
      assert2xx(api(HttpMethod.GET, "/api/v1/notifications", token = token), "notification list")
      assert2xx(api(HttpMethod.POST, "/api/v1/notifications/read-all", json(), token), "notifications read-all")

      val channel =
        api(
          HttpMethod.POST,
          "/api/v1/communication/channels",
          json(
            "name" to "Ops $s",
            "type" to "group",
            "memberIds" to listOf(adminUserId),
          ),
          token,
        )
      assert2xx(channel, "create channel")
      val channelId = `data`(channel)["id"].asText()

      val channels = `data`(api(HttpMethod.GET, "/api/v1/communication/channels", token = token))
      assertTrue(channels.any { it["id"].asText() == channelId }, "channel in list")

      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/communication/channels/$channelId/messages",
          json(
            "body" to "hello $s",
            "messageType" to "text",
          ),
          token,
        ),
        "send message",
      )

      val messages = `data`(api(HttpMethod.GET, "/api/v1/communication/channels/$channelId/messages?limit=10", token = token))
      assertTrue(messages.size() >= 1, "messages listed")

      assert2xx(api(HttpMethod.POST, "/api/v1/communication/channels/$channelId/read", json(), token), "mark channel read")
      assert2xx(api(HttpMethod.GET, "/api/v1/communication/unread-count", token = token), "communication unread-count")

      val steps =
        listOf(
          mapOf(
            "stepKey" to "notify",
            "stepType" to "NOTIFICATION",
            "sequenceOrder" to 0,
            "config" to mapOf<String, Any?>("recipientId" to adminUserId, "type" to "workflow", "title" to "wf $s", "body" to "x"),
          ),
          mapOf(
            "stepKey" to "check",
            "stepType" to "TASK",
            "sequenceOrder" to 1,
            "config" to mapOf<String, Any?>("title" to "wf task", "assigneeId" to adminUserId),
          ),
          mapOf(
            "stepKey" to "approve",
            "stepType" to "APPROVAL",
            "sequenceOrder" to 2,
            "config" to mapOf<String, Any?>("approvers" to listOf(adminUserId)),
          ),
          mapOf(
            "stepKey" to "finish",
            "stepType" to "ACTION",
            "sequenceOrder" to 3,
            "config" to mapOf<String, Any?>("type" to "complete"),
          ),
        )
      val wdef =
        api(
          HttpMethod.POST,
          "/api/v1/workflow/definitions",
          json(
            "code" to "JT_FLOW_$s",
            "name" to "JT Flow",
            "triggerType" to "MANUAL",
            "steps" to steps,
          ),
          token,
        )
      assert2xx(wdef, "create workflow definition")
      assertEquals("DRAFT", `data`(wdef)["status"].asText())
      val wdefId = `data`(wdef)["id"].asText()

      val act = api(HttpMethod.POST, "/api/v1/workflow/definitions/$wdefId/activate", json(), token)
      assert2xx(act, "activate workflow")
      assertEquals("ACTIVE", `data`(act)["status"].asText())

      val winst =
        api(
          HttpMethod.POST,
          "/api/v1/workflow/instances",
          json(
            "definitionId" to wdefId,
            "payload" to mapOf("source" to "journey"),
          ),
          token,
        )
      assert2xx(winst, "trigger workflow instance")
      assertEquals("WAITING_APPROVAL", `data`(winst)["status"].asText())
      val winstId = `data`(winst)["id"].asText()

      val wdetail = `data`(api(HttpMethod.GET, "/api/v1/workflow/instances/$winstId", token = token))
      assertTrue(wdetail["stepInstances"].size() >= 2, "step instances")
      val approval = wdetail["approvals"].first { it["status"].asText() == "PENDING" }
      assertNotNull(approval)

      val decide =
        api(
          HttpMethod.POST,
          "/api/v1/workflow/instances/$winstId/approvals/${approval["id"].asText()}/decide",
          json(
            "decision" to "APPROVED",
            "comments" to "ok",
          ),
          token,
        )
      assert2xx(decide, "approve workflow")
      assertEquals("COMPLETED", `data`(decide)["status"].asText())
      assert2xx(api(HttpMethod.GET, "/api/v1/workflow/approvals/pending", token = token), "pending approvals")

      assert2xx(api(HttpMethod.GET, "/api/v1/analytics/overview", token = token), "analytics overview")
      assert2xx(api(HttpMethod.GET, "/api/v1/analytics/patients", token = token), "analytics patients")
      assert2xx(api(HttpMethod.GET, "/api/v1/analytics/encounters", token = token), "analytics encounters")
      assert2xx(api(HttpMethod.GET, "/api/v1/analytics/queue", token = token), "analytics queue")
      assert2xx(api(HttpMethod.GET, "/api/v1/analytics/lab", token = token), "analytics lab")
      assert2xx(api(HttpMethod.GET, "/api/v1/analytics/billing", token = token), "analytics billing")

      // 00.02: the /audit/logs filters are now real. The decide call above audited a row whose
      // entityId is the workflow instance id (audit aspect takes the first UUID method arg),
      // so this filter must return at least that row.
      val audit = api(HttpMethod.GET, "/api/v1/audit/logs?entityId=$winstId&limit=10", token = token)
      assert2xx(audit, "audit logs for workflow instance")
      assertTrue(`data`(audit).size() >= 1, "audit rows recorded")
      assert2xx(api(HttpMethod.GET, "/api/v1/audit/logs?limit=50", token = token), "audit logs global")
    }

    @Test
    fun aiGatewayAndInteropJourney() {
      val token = adminToken()
      val s = suffix()

      val fhirP = api(HttpMethod.GET, "/api/v1/fhir/Patient", token = token)
      assert2xx(fhirP, "FHIR Patient")
      assertEquals("Bundle", `data`(fhirP)["resourceType"].asText())
      assert2xx(api(HttpMethod.GET, "/api/v1/fhir/Encounter", token = token), "FHIR Encounter")
      assert2xx(api(HttpMethod.GET, "/api/v1/fhir/DiagnosticReport", token = token), "FHIR DiagnosticReport")

      val adt = "MSH|^~\\&|E2E|HOSP|SQUAMED|MIR|20260906120000||ADT^A04^ADT_A04|CTRL-$s|P|2.5\rEVN|A04|20260906120000\rPID|1||E2E-MRN-$s||Test^E2E||19800101|M"
      val parse = api(HttpMethod.POST, "/api/v1/hl7/parse", json("raw" to adt), token)
      assert2xx(parse, "HL7 parse")
      assertEquals("ADT", `data`(parse)["messageType"].asText())
      assertEquals("CTRL-$s", `data`(parse)["controlId"].asText())

      val import = api(HttpMethod.POST, "/api/v1/hl7/messages", json("raw" to adt), token)
      assert2xx(import, "HL7 import")
      assertEquals("AA", `data`(import)["ackCode"].asText())
      assertNotNull(`data`(import)["importedPatientId"])

      val history = api(HttpMethod.GET, "/api/v1/hl7/messages", token = token)
      assert2xx(history, "HL7 history")
      assertTrue(`data`(history).size() >= 1, "HL7 messages recorded")

      val ih = `data`(api(HttpMethod.GET, "/api/v1/interop/health", token = token))
      assertEquals(true, ih["mllp"]["mllpRunning"].asBoolean(), "MLLP listener running")

      val models = `data`(api(HttpMethod.GET, "/api/v1/ai/models", token = token))
      assertTrue(models.any { it["provider"].asText() == "ECHO" && it["isDefault"].asBoolean() }, "ECHO default model")
      val agents = `data`(api(HttpMethod.GET, "/api/v1/ai/agents", token = token))
      assertTrue(agents.any { it["code"].asText() == "ops" }, "ops agent seeded")
      val tools = `data`(api(HttpMethod.GET, "/api/v1/ai/tools", token = token))
      assertTrue(tools.size() >= 5, "tools seeded")

      val newModel =
        api(
          HttpMethod.POST,
          "/api/v1/ai/models",
          json(
            "name" to "IT OpenAI $s",
            "provider" to "OPENAI",
            "modelName" to "gpt-4o-mini",
            "baseUrl" to "https://api.openai.com/v1",
            "apiKey" to "sk-e2e",
          ),
          token,
        )
      assert2xx(newModel, "create AI model")
      val modelId = `data`(newModel)["id"].asText()
      val del = api(HttpMethod.DELETE, "/api/v1/ai/models/$modelId", token = token)
      assert2xx(del, "delete AI model")
      assertEquals(true, `data`(del)["deleted"].asBoolean())

      val nav = `data`(api(HttpMethod.POST, "/api/v1/ai/command", json("query" to "open laboratory"), token))
      assertEquals("navigate", nav["intent"].asText())
      val cnt = `data`(api(HttpMethod.POST, "/api/v1/ai/command", json("query" to "how many patients?"), token))
      assertEquals("count", cnt["intent"].asText())
      assertTrue(cnt["message"].asText().contains("patients"), "count message")
      val wf = `data`(api(HttpMethod.POST, "/api/v1/ai/command", json("query" to "pending approvals"), token))
      assertEquals("workflow", wf["intent"].asText())
      val rev = `data`(api(HttpMethod.POST, "/api/v1/ai/command", json("query" to "revenue today"), token))
      assertEquals("analytics", rev["intent"].asText())
      assertTrue(rev["message"].asText().contains("QR"), "revenue message")
      val srch = `data`(api(HttpMethod.POST, "/api/v1/ai/command", json("query" to "search patient Sara"), token))
      assertEquals("search_patients", srch["intent"].asText())

      val chat =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/ai/chat",
            json(
              "messages" to listOf(mapOf("role" to "user", "content" to "how many patients?")),
            ),
            token,
          ),
        )
      assertEquals("count", chat["intent"].asText())
      assertTrue(chat["reply"].asText().contains("patients"), "chat reply")

      val stream =
        api(
          HttpMethod.POST,
          "/api/v1/ai/chat/stream",
          json(
            "messages" to listOf(mapOf("role" to "user", "content" to "pending approvals")),
          ),
          token,
        )
      assert2xx(stream, "AI chat stream")
      assertTrue(stream.body.contains("\"type\":\"token\"") && stream.body.contains("\"type\":\"done\""), "SSE token+done")

      // chunk-5 (AI contract §8): command-path interaction logging is governed by the AI
      // gateway; the baseline logs only generate() calls (chat), so assert that floor.
      val alogs = `data`(api(HttpMethod.GET, "/api/v1/ai/logs", token = token))
      assertTrue(alogs.size() >= 1, "AI interaction logs (baseline: chat only)")

      val pipe = `data`(api(HttpMethod.GET, "/api/v1/scribe/pipeline", token = token))
      assertEquals(6, pipe["stages"].size())
      assertTrue(pipe["stages"].any { it.asText() == "SIGNED" }, "scribe pipeline stages")

      val sess =
        api(
          HttpMethod.POST,
          "/api/v1/scribe/sessions",
          json(
            "patientName" to "IT Scribe $s",
            "chiefComplaint" to "Persistent cough $s",
          ),
          token,
        )
      assert2xx(sess, "scribe create")
      assertEquals("CREATED", `data`(sess)["stage"].asText())
      val sid = `data`(sess)["id"].asText()

      val start = api(HttpMethod.POST, "/api/v1/scribe/sessions/$sid/start-recording", json(), token)
      assert2xx(start, "scribe start recording")
      assertEquals("TRANSCRIBING", `data`(start)["stage"].asText())

      val transcript = "Patient: I have had this cough for five days.\nDoctor: Mild wheeze on expiration noted.\nPatient: Temperature was 38 at home."
      val stop =
        api(
          HttpMethod.POST,
          "/api/v1/scribe/sessions/$sid/stop-recording",
          json(
            "transcriptText" to transcript,
            "durationSeconds" to 75,
          ),
          token,
        )
      assert2xx(stop, "scribe stop recording")
      assertEquals("SPEAKERS_RESOLVED", `data`(stop)["stage"].asText())
      assertEquals(3, `data`(stop)["segments"].size(), "speaker segments")

      val gen = api(HttpMethod.POST, "/api/v1/scribe/sessions/$sid/generate", json(), token)
      assert2xx(gen, "scribe generate draft")
      assertEquals("DRAFTED", `data`(gen)["stage"].asText())
      assertTrue(`data`(gen)["draft"].asText().contains("SOAP NOTE"), "SOAP draft")

      val sign = api(HttpMethod.POST, "/api/v1/scribe/sessions/$sid/sign", json(), token)
      assert2xx(sign, "scribe sign")
      assertEquals("SIGNED", `data`(sign)["status"].asText())
      assertEquals("SIGNED", `data`(sign)["stage"].asText())
      assertNotNull(`data`(sign)["signedAt"])

      val delS = api(HttpMethod.DELETE, "/api/v1/scribe/sessions/$sid", token = token)
      assert2xx(delS, "scribe delete")
      assertEquals(true, `data`(delS)["deleted"].asBoolean())
    }

    @Test
    fun scribeAudioUploadDrivesSttStage() {
      val token = adminToken()
      val s = suffix()
      val sess =
        api(
          HttpMethod.POST,
          "/api/v1/scribe/sessions",
          json(
            "patientName" to "IT Audio $s",
            "chiefComplaint" to "Persistent cough $s",
          ),
          token,
        )
      assert2xx(sess, "scribe create audio session")
      val sid = `data`(sess)["id"].asText()
      assert2xx(api(HttpMethod.POST, "/api/v1/scribe/sessions/$sid/start-recording", json(), token), "start recording")

      val parts = org.springframework.util.LinkedMultiValueMap<String, Any>()
      val resource =
        object : org.springframework.core.io.ByteArrayResource(ByteArray(32000)) {
          override fun getFilename(): String = "consultation.wav"
        }
      parts.add("file", resource)
      val headers =
        HttpHeaders().apply {
          contentType = MediaType.MULTIPART_FORM_DATA
          setBearerAuth(token)
        }
      val upload =
        rest.exchange(
          "/api/v1/scribe/sessions/$sid/audio",
          HttpMethod.POST,
          HttpEntity(parts, headers),
          String::class.java,
        )
      assertEquals(200, upload.statusCode.value(), "audio upload -> ${upload.body?.take(200)}")
      val stage = `data`(ApiResp(upload.statusCode.value(), upload.body ?: ""))["stage"].asText()
      assertEquals("SPEAKERS_RESOLVED", stage, "pipeline advanced to speakers")
      val gen = api(HttpMethod.POST, "/api/v1/scribe/sessions/$sid/generate", json(), token)
      assert2xx(gen, "draft from audio transcript")
      assertEquals("DRAFTED", `data`(gen)["stage"].asText())
      assert2xx(api(HttpMethod.DELETE, "/api/v1/scribe/sessions/$sid", token = token), "cleanup audio session")
    }

    @Test
    fun searchAndPatient360Journey() {
      val token = adminToken()
      val s = suffix()

      val patient =
        api(
          HttpMethod.POST,
          "/api/v1/patients",
          json(
            "firstName" to "Sara",
            "lastName" to "Search$s",
            "gender" to "female",
            "dateOfBirth" to "1988-03-20",
            "phone" to "77${System.currentTimeMillis()}".take(12),
            "qid" to "3$s".take(11),
            "nationality" to "Qatar",
            "city" to "Doha",
          ),
          token,
        )
      assert2xx(patient, "create search patient")
      val patientId = `data`(patient)["patient"]["id"].asText()

      var hitsDone = false
      var lastResult: JsonNode? = null
      repeat(8) {
        val sr = api(HttpMethod.GET, "/api/v1/search/search?q=Search$s", token = token)
        lastResult = `data`(sr)
        if (sr.status in 200..299 && `data`(sr)["hits"].size() >= 1) {
          hitsDone = true
          return@repeat
        }
        Thread.sleep(1500)
      }
      assertTrue(hitsDone, "search finds created patient")
      assertNotNull(lastResult)
      assert2xx(api(HttpMethod.GET, "/api/v1/search/search/indices", token = token), "search indices")

      val p360 = api(HttpMethod.GET, "/api/v1/patient360/$patientId", token = token)
      assert2xx(p360, "patient360")
      val agg = `data`(p360)
      assertEquals(patientId, agg["demographics"]["id"].asText())
      assertNotNull(agg["demographics"]["mrn"])
      assertNotNull(agg["encounters"])
      assertNotNull(agg["labOrders"])
      assertNotNull(agg["tasks"])
    }

    // 01.05 Patient 360 v1: documents (S3/MinIO metadata rows + bytes round-trip) and
    // consents surface in the aggregate with effective status; cross-tenant denials.
    @Test
    fun patient360DocumentsAndConsentsJourney() {
      val admin = adminToken()
      val s = suffix()
      val h1 =
        `data`(
          api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "P360 H1 $s", "code" to "P3A-$s", "country" to "Qatar"), admin),
        )["id"].asText()
      val h2 =
        `data`(
          api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "P360 H2 $s", "code" to "P3B-$s", "country" to "Qatar"), admin),
        )["id"].asText()

      fun doctor(
        hospitalId: String,
        tag: String,
      ): String {
        val email = "p360-$tag-$s@test.local"
        assert2xx(
          api(
            HttpMethod.POST,
            "/api/v1/auth/register",
            json(
              "email" to email,
              "password" to "Passw0rd!123",
              "firstName" to "Doc",
              "lastName" to "P360$s",
              "roles" to listOf("DOCTOR"),
              "hospitalId" to hospitalId,
            ),
          ),
          "register $tag doctor",
        )
        val login = api(HttpMethod.POST, "/api/v1/auth/login", json("username" to email, "password" to "Passw0rd!123"))
        assert2xx(login, "login $tag")
        return `data`(login)["accessToken"].asText()
      }

      val doc1 = doctor(h1, "one")
      val doc2 = doctor(h2, "two")

      val patientId =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/patients",
            json("firstName" to "P360", "lastName" to "Patient$s", "gender" to "male", "dateOfBirth" to "1990-04-04"),
            doc1,
          ),
        )["patient"]["id"].asText()

      // Documents: multipart upload -> metadata row + object-storage key; list + aggregate + download round-trip.
      val pdfBytes = "%PDF-1.4\n%scan-test\n".toByteArray()
      val parts = org.springframework.util.LinkedMultiValueMap<String, Any>()
      parts.add(
        "file",
        object : org.springframework.core.io.ByteArrayResource(pdfBytes) {
          override fun getFilename(): String = "discharge-summary.pdf"
        },
      )
      parts.add("name", "Discharge summary $s")
      parts.add("category", "discharge")
      val uploadHeaders =
        HttpHeaders().apply {
          contentType = MediaType.MULTIPART_FORM_DATA
          setBearerAuth(doc1)
        }
      val upload = rest.exchange("/api/v1/patients/$patientId/documents", HttpMethod.POST, HttpEntity(parts, uploadHeaders), String::class.java)
      assertTrue(upload.statusCode.is2xxSuccessful, "document upload -> ${upload.body?.take(300)}")
      val document = om.readTree(upload.body ?: "{}")["data"]
      val documentId = document["id"].asText()
      assertEquals("Discharge summary $s", document["name"].asText(), "document name stored")
      assertEquals("discharge", document["category"].asText(), "document category stored")
      assertEquals("application/pdf", document["contentType"].asText(), "document content type stored")
      assertEquals(pdfBytes.size.toLong(), document["sizeBytes"].asLong(), "document size stored")

      val list = `data`(api(HttpMethod.GET, "/api/v1/patients/$patientId/documents", token = doc1))
      assertEquals(1, list.size(), "document list must contain the upload")
      assertEquals(documentId, list[0]["id"].asText(), "document list id matches")

      val badParts = org.springframework.util.LinkedMultiValueMap<String, Any>()
      badParts.add(
        "file",
        object : org.springframework.core.io.ByteArrayResource(ByteArray(8)) {
          override fun getFilename(): String = "notes.txt"
        },
      )
      badParts.add("name", "Notes")
      val badUploadStatus =
        try {
          rest.exchange("/api/v1/patients/$patientId/documents", HttpMethod.POST, HttpEntity(badParts, uploadHeaders), String::class.java).statusCode.value()
        } catch (e: RestClientResponseException) {
          e.statusCode.value()
        }
      assertEquals(400, badUploadStatus, "unsupported content type must be rejected")

      // Consents with effective status: granted (no expiry), expired (past expiresAt), then withdrawn wins.
      val granted =
        `data`(api(HttpMethod.POST, "/api/v1/consents", json("patientId" to patientId, "purpose" to "treatment", "signedByName" to "P360 Patient$s"), doc1))
      assertEquals("granted", granted["status"].asText(), "treatment consent granted")
      val expired =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/consents",
            json("patientId" to patientId, "purpose" to "data_sharing", "expiresAt" to "2020-01-01T00:00:00Z"),
            doc1,
          ),
        )
      assertEquals("expired", expired["status"].asText(), "past-expiresAt consent surfaces as expired")

      val withdraw = `data`(api(HttpMethod.POST, "/api/v1/consents/${granted["id"].asText()}/withdraw", json("reason" to "opt out"), doc1))
      assertEquals("withdrawn", withdraw["status"].asText(), "withdrawn consent surfaces as withdrawn")

      // The aggregate exposes documents + consents alongside the existing panels.
      val agg = `data`(api(HttpMethod.GET, "/api/v1/patient360/$patientId", token = doc1))
      assertEquals(1, agg["documents"].size(), "360 aggregate carries documents")
      assertEquals("Discharge summary $s", agg["documents"][0]["name"].asText(), "360 document name")
      val aggConsents = agg["consents"].asSequence().map { it["purpose"].asText() to it["status"].asText() }.toMap()
      assertEquals(
        mapOf("treatment" to "withdrawn", "data_sharing" to "expired"),
        aggConsents,
        "360 consent effective statuses (withdrawn wins, expiry applied)",
      )

      val download =
        try {
          val resp =
            rest.exchange(
              "/api/v1/patients/$patientId/documents/$documentId/download",
              HttpMethod.GET,
              HttpEntity<Void?>(
                HttpHeaders().apply {
                  setBearerAuth(doc1)
                },
              ),
              ByteArray::class.java,
            )
          Pair(resp.statusCode.value(), resp.body ?: ByteArray(0))
        } catch (e: RestClientResponseException) {
          Pair(e.statusCode.value(), ByteArray(0))
        }
      assertEquals(200, download.first, "document download must succeed")
      assertTrue(pdfBytes.contentEquals(download.second), "downloaded document bytes must match the upload")

      // Cross-tenant: H2 doctor cannot read/write foreign documents or the foreign 360.
      assertEquals(403, api(HttpMethod.GET, "/api/v1/patients/$patientId/documents", token = doc2).status, "foreign document list denied")
      assertEquals(
        403,
        api(HttpMethod.GET, "/api/v1/patients/$patientId/documents/$documentId/download", token = doc2).status,
        "foreign document download denied",
      )
      assertEquals(403, api(HttpMethod.GET, "/api/v1/patient360/$patientId", token = doc2).status, "foreign patient360 denied")
      val foreignParts = org.springframework.util.LinkedMultiValueMap<String, Any>()
      foreignParts.add(
        "file",
        object : org.springframework.core.io.ByteArrayResource(pdfBytes) {
          override fun getFilename(): String = "intruder.pdf"
        },
      )
      foreignParts.add("name", "Foreign upload")
      val foreignUploadHeaders =
        HttpHeaders().apply {
          contentType = MediaType.MULTIPART_FORM_DATA
          setBearerAuth(doc2)
        }
      val foreignUploadStatus =
        try {
          rest
            .exchange(
              "/api/v1/patients/$patientId/documents",
              HttpMethod.POST,
              HttpEntity(foreignParts, foreignUploadHeaders),
              String::class.java,
            ).statusCode
            .value()
        } catch (e: RestClientResponseException) {
          e.statusCode.value()
        }
      assertEquals(403, foreignUploadStatus, "foreign document write denied")

      // Own-tenant reads still work after the guard.
      assert2xx(api(HttpMethod.GET, "/api/v1/patients/$patientId/documents", token = doc1), "own tenant document list allowed")
      assert2xx(api(HttpMethod.GET, "/api/v1/patient360/$patientId", token = doc1), "own tenant patient360 allowed")
    }
  }

  @Nested
  inner class SecurityTests {
    @Test
    fun unauthenticatedRequestsAreRejected() {
      assertEquals(401, api(HttpMethod.GET, "/api/v1/patients").status, "patients without token")
      assertEquals(401, api(HttpMethod.GET, "/api/v1/audit/logs").status, "audit without token")
      assertEquals(401, api(HttpMethod.POST, "/api/v1/billing/charges", json("x" to 1)).status, "mutating without token")
      assertEquals(401, api(HttpMethod.GET, "/api/v1/ai/models").status, "ai models without token")
    }

    @Test
    fun tamperedOrForgedTokensRejected() {
      assertEquals(401, api(HttpMethod.GET, "/api/v1/auth/me", token = "not.a.jwt").status, "tampered token")
      val garbage = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbkBzcXVhbWVkLmxvY2FsIn0.wrong-signature"
      assertEquals(401, api(HttpMethod.GET, "/api/v1/auth/me", token = garbage).status, "forged signature token")
    }

    @Test
    fun hardenedSecurityHeadersArePresent() {
      val token = adminToken()
      val headers = HttpHeaders().apply { setBearerAuth(token) }
      val resp = rest.exchange("/api/v1/patients", HttpMethod.GET, HttpEntity(null, headers), String::class.java)
      assertEquals("nosniff", resp.headers.getFirst("X-Content-Type-Options"))
      assertEquals("DENY", resp.headers.getFirst("X-Frame-Options"))
      val csp = resp.headers.getFirst("Content-Security-Policy")
      assertTrue(csp != null && csp.contains("frame-ancestors 'none'") && csp.contains("default-src 'none'"), "CSP header: $csp")
      val hsts = resp.headers.getFirst("Strict-Transport-Security")
      assertTrue(hsts != null && hsts.contains("max-age=31536000"), "HSTS header: $hsts")
      assertEquals("no-referrer", resp.headers.getFirst("Referrer-Policy"))
    }

    @Test
    fun userWithoutRolesIsDenied() {
      val s = suffix()
      val email = "noperm-$s@test.local"
      val reg =
        api(
          HttpMethod.POST,
          "/api/v1/auth/register",
          json(
            "email" to email,
            "password" to "Passw0rd!123",
            "firstName" to "No",
            "lastName" to "Perms",
          ),
        )
      assertEquals(201, reg.status, "register: ${reg.body.take(200)}")

      val login = api(HttpMethod.POST, "/api/v1/auth/login", json("username" to email, "password" to "Passw0rd!123"))
      assert2xx(login, "login no-perm user")
      val token = `data`(login)["accessToken"].asText()

      assertEquals(403, api(HttpMethod.GET, "/api/v1/audit/logs", token = token).status, "audit read denied")
      assertEquals(403, api(HttpMethod.GET, "/api/v1/patients", token = token).status, "patient read denied")
      assertEquals(403, api(HttpMethod.GET, "/api/v1/ai/models", token = token).status, "ai read denied")
      assertEquals(403, api(HttpMethod.POST, "/api/v1/billing/charges", json("x" to 1), token).status, "billing write denied")
    }

    @Test
    fun validationErrorsUseCleanContract() {
      val token = adminToken()
      val bad = api(HttpMethod.POST, "/api/v1/patients", json("firstName" to "X"), token)
      assertEquals(400, bad.status, "invalid patient -> 400")
      val node = om.readTree(bad.body)
      assertEquals("PATIENT_LAST_NAME_REQUIRED", node["error"]["code"].asText())
      assertTrue(node.has("timestamp"), "error has timestamp")
    }

    @Test
    fun injectionPayloadsAreNeutralized() {
      val token = adminToken()
      val s = suffix()

      val xssPatient =
        api(
          HttpMethod.POST,
          "/api/v1/patients",
          json(
            "firstName" to "<img src=x onerror=alert(1)>",
            "lastName" to "Xss$s",
            "gender" to "male",
            "dateOfBirth" to "1990-01-01",
            "phone" to "70000000$s".take(11),
            "qid" to "4$s".take(11),
            "nationality" to "QA",
            "city" to "Doha",
          ),
          token,
        )
      assert2xx(xssPatient, "XSS payload accepted safely")

      val sqlName = "O'Brien'; DROP TABLE patients;--"
      val sqlPatient =
        api(
          HttpMethod.POST,
          "/api/v1/patients",
          json(
            "firstName" to sqlName,
            "lastName" to "Sql$s",
            "gender" to "male",
            "dateOfBirth" to "1990-01-01",
            "phone" to "71111111$s".take(11),
            "qid" to "5$s".take(11),
            "nationality" to "QA",
            "city" to "Doha",
          ),
          token,
        )
      assert2xx(sqlPatient, "SQL injection name accepted safely")

      val q = api(HttpMethod.GET, "/api/v1/patients?query=" + java.net.URLEncoder.encode("x' OR 1=1 --", "UTF-8"), token = token)
      assert2xx(q, "SQL injection search safe")

      val sq = api(HttpMethod.GET, "/api/v1/search/search?q=" + java.net.URLEncoder.encode("\"*:*\" OR *", "UTF-8"), token = token)
      assert2xx(sq, "OpenSearch injection query safe")

      val traversal = api(HttpMethod.GET, "/api/v1/patients/..%2f..%2fetc/passwd", token = token)
      assertTrue(traversal.status >= 400, "path traversal rejected (got ${traversal.status})")
    }

    @Test
    fun csrfNotApplicableWithoutSessionCookie() {
      val anon = api(HttpMethod.POST, "/api/v1/tasks", json("title" to "x"))
      assertEquals(401, anon.status, "mutating endpoint requires auth (no CSRF vector)")
      val token = adminToken()
      val withAuth =
        api(
          HttpMethod.POST,
          "/api/v1/tasks",
          json(
            "title" to "csrf check",
            "description" to "x",
            "priority" to "normal",
          ),
          token,
        )
      assertTrue(withAuth.status in 200..201, "authenticated create works without CSRF token")
    }
  }

  @Nested
  inner class ContractTests {
    @Test
    fun openApiExposesAllModuleSurfaces() {
      val r = api(HttpMethod.GET, "/api-docs", token = null)
      assert2xx(r, "openapi docs")
      val doc = om.readTree(r.body)
      val paths = doc["paths"].fieldNames().asSequence().toList()

      val expectedBases =
        listOf(
          "/api/v1/auth",
          "/api/v1/patients",
          "/api/v1/appointments",
          "/api/v1/reception",
          "/api/v1/queue",
          "/api/v1/encounters",
          "/api/v1/lab",
          "/api/v1/pharmacy",
          "/api/v1/billing",
          "/api/v1/tasks",
          "/api/v1/consents",
          "/api/v1/notifications",
          "/api/v1/communication",
          "/api/v1/workflow",
          "/api/v1/analytics",
          "/api/v1/audit",
          "/api/v1/patient360",
          "/api/v1/hospital-config",
          "/api/v1/master-data",
          "/api/v1/fhir",
          "/api/v1/hl7",
          "/api/v1/interop",
          "/api/v1/ai",
          "/api/v1/scribe",
          "/api/v1/search",
        )
      expectedBases.forEach { base ->
        assertTrue(paths.any { it.startsWith(base) }, "OpenAPI missing $base (paths: ${paths.take(8)})")
      }
      assertTrue(paths.size >= 60, "expected broad API surface, got ${paths.size}")
    }

    @Test
    fun errorResponsesFollowContract() {
      val token = adminToken()
      val bad = api(HttpMethod.POST, "/api/v1/patients", json("firstName" to "NoBirth"), token)
      assertEquals(400, bad.status)
      val node = om.readTree(bad.body)
      assertTrue(node.has("error"), "error body has error field")
      assertTrue(node["error"].has("code"), "error has code")
      assertTrue(node.has("timestamp"), "error has timestamp")

      val unknown = api(HttpMethod.GET, "/api/v1/patients/" + UUID.randomUUID(), token = token)
      assertTrue(unknown.status in 404..409 || unknown.status == 400, "unknown patient -> 4xx (${unknown.status})")
    }

    @Test
    fun eventEnvelopeV1ValidatesPublishedShape() {
      // Real envelope shape (com.squamed.core.events.DomainEvent) must validate against the
      // published event-envelope-v1 JSON schema (gate 8: schema-registry harness).
      val registry =
        com.squamed.core.events
          .SchemaRegistry()
      val event =
        com.squamed.core.events.DomainEvent(
          eventId = UUID.randomUUID(),
          eventType = "patient.created",
          occurredAt = java.time.Instant.now(),
          source = "squamed-core",
          entityType = "patient",
          entityId = UUID.randomUUID(),
          hospitalId = null,
          payload = mapOf("body" to "created"),
          recipientIds = listOf(UUID.randomUUID()),
        )
      val valid = om.readTree(om.writeValueAsString(event))
      val violations = registry.validate("event-envelope-v1.schema.json", valid)
      assertTrue(violations.isEmpty(), "published envelope shape must validate: $violations")

      // Breaking shape: uppercase/underscore event type violates the v1 naming contract.
      val broken =
        (valid.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()).apply {
          put("eventType", "Patient_Created")
        }
      assertTrue(registry.validate("event-envelope-v1.schema.json", broken).isNotEmpty(), "broken envelope must fail")

      // Unknown field = breaking change to the envelope (additionalProperties: false).
      val unknown =
        (valid.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()).apply {
          put("futureField", 1)
        }
      assertTrue(registry.validate("event-envelope-v1.schema.json", unknown).isNotEmpty(), "unknown field must fail")

      // 00.09: envelope v1 field completion — actor/correlation fields validate on the real class shape.
      val stampedEvent =
        event.copy(
          version = 1,
          actorType = "user",
          actorId = UUID.randomUUID(),
          correlationId = "corr-123",
          causationId = UUID.randomUUID(),
        )
      val stamped = om.readTree(om.writeValueAsString(stampedEvent))
      assertTrue(
        registry.validate("event-envelope-v1.schema.json", stamped).isEmpty(),
        "stamped envelope must validate: ${registry.validate("event-envelope-v1.schema.json", stamped)}",
      )
      val badActor =
        (stamped.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()).apply { put("actorType", "robot") }
      assertTrue(registry.validate("event-envelope-v1.schema.json", badActor).isNotEmpty(), "bad actorType must fail")
    }

    @Test
    fun openApiSnapshotMatchesLiveContract() {
      // Gate 8: breaking changes are detected by diffing the committed contract snapshot against
      // the live /api-docs document. Any difference = regenerate the snapshot + api-client codegen
      // (docs/development/contract-codegen.md) and land both in the same PR.
      val snapshotFile = java.io.File("../../docs/contracts/openapi-v1.json")
      assertTrue(snapshotFile.exists(), "openapi snapshot missing at ${snapshotFile.absolutePath} — generate it from the live /api-docs")
      val snapshot = normalizeContract(om.readTree(snapshotFile.readText()))
      val liveResp = api(HttpMethod.GET, "/api-docs")
      assert2xx(liveResp, "live api-docs")
      val live = normalizeContract(om.readTree(liveResp.body))
      assertEquals(
        snapshot,
        live,
        "OpenAPI contract drifted from the committed snapshot — regenerate docs/contracts/openapi-v1.json and run: npm run codegen -w @squamed/api-client",
      )
    }

    private fun normalizeContract(doc: com.fasterxml.jackson.databind.JsonNode): com.fasterxml.jackson.databind.JsonNode =
      (doc.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()).apply {
        remove("servers")
      }

    @Test
    fun idempotentRetryReplaysSameResponseWithoutSideEffect() {
      val token = adminToken()
      val s = suffix()
      val body =
        json(
          "firstName" to "Idem",
          "lastName" to "Potent$s",
          "gender" to "male",
          "dateOfBirth" to "1992-05-15",
          "phone" to "55${System.currentTimeMillis()}".take(12),
          "qid" to "2$s".take(11),
          "nationality" to "Qatar",
          "city" to "Doha",
        )
      val key = "pit-idem-$s"

      val first = api(HttpMethod.POST, "/api/v1/patients", body, token, mapOf("Idempotency-Key" to key))
      assert2xx(first, "first create")
      val replay = api(HttpMethod.POST, "/api/v1/patients", body, token, mapOf("Idempotency-Key" to key))
      assert2xx(replay, "idempotent replay")

      assertEquals(first.body, replay.body, "replay must return the byte-identical stored response")
      assertEquals(
        `data`(first)["patient"]["id"].asText(),
        `data`(replay)["patient"]["id"].asText(),
        "replay must not create a second patient",
      )
    }

    @Test
    fun sameIdempotencyKeyDifferentPayloadConflicts() {
      val token = adminToken()
      val s = suffix()
      val key = "pit-conflict-$s"
      val phone = "55${System.currentTimeMillis()}".take(12)
      val qid = "2$s".take(11)

      val first =
        api(
          HttpMethod.POST,
          "/api/v1/patients",
          json(
            "firstName" to "Conflict",
            "lastName" to "Case$s",
            "gender" to "male",
            "dateOfBirth" to "1992-05-15",
            "phone" to phone,
            "qid" to qid,
            "nationality" to "Qatar",
            "city" to "Doha",
          ),
          token,
          mapOf("Idempotency-Key" to key),
        )
      assert2xx(first, "first create")
      val conflict =
        api(
          HttpMethod.POST,
          "/api/v1/patients",
          json(
            "firstName" to "Conflict",
            "lastName" to "Different$s",
            "gender" to "male",
            "dateOfBirth" to "1992-05-15",
            "phone" to phone,
            "qid" to qid,
            "nationality" to "Qatar",
            "city" to "Doha",
          ),
          token,
          mapOf("Idempotency-Key" to key),
        )
      assertEquals(409, conflict.status, "payload change under same key -> 409, got ${conflict.body.take(200)}")
      val node = om.readTree(conflict.body)
      assertEquals("IDEMPOTENCY_CONFLICT", node["error"]["code"].asText())
    }

    @Test
    fun deviceRegistryListAndRevokeInvalidatesSession() {
      val s = suffix()
      val email = "device-$s@test.local"
      val register =
        api(
          HttpMethod.POST,
          "/api/v1/auth/register",
          json(
            "email" to email,
            "password" to "Passw0rd!123",
            "firstName" to "Dev",
            "lastName" to "Ice",
          ),
        )
      assertEquals(201, register.status, "register: ${register.body.take(200)}")

      val login = api(HttpMethod.POST, "/api/v1/auth/login", json("username" to email, "password" to "Passw0rd!123"))
      assert2xx(login, "login for device journey")
      val token = `data`(login)["accessToken"].asText()

      val devices = api(HttpMethod.GET, "/api/v1/auth/devices", token = token)
      assert2xx(devices, "list devices")
      val deviceList = `data`(devices)
      assertTrue(deviceList.isArray && deviceList.size() >= 1, "expected at least one device: ${devices.body.take(200)}")
      val deviceId = deviceList[0]["id"].asText()
      assertTrue(deviceId.isNotBlank(), "device id must be present")

      val revoked = api(HttpMethod.DELETE, "/api/v1/auth/devices/$deviceId", token = token)
      assert2xx(revoked, "revoke device")

      // Revoking the device kills the session bound to it → refresh must be rejected.
      val refresh =
        api(
          HttpMethod.POST,
          "/api/v1/auth/refresh",
          json("refreshToken" to `data`(login)["refreshToken"].asText()),
        )
      val refreshStatus = om.readTree(refresh.body)["data"]["status"].asText()
      assertEquals("INVALID_CREDENTIALS", refreshStatus, "refresh after device revoke must fail: ${refresh.body.take(200)}")
    }
  }

  @Nested
  inner class TenantIsolationTests {
    // ADR-13 stage 1 denial suite: the tenant comes from user_role assignments at login,
    // never from the client; hospital-bound users cannot read or write across tenants.
    private fun hospitalLogin(
      email: String,
      claimHospitalId: String? = null,
    ): String {
      val login =
        api(HttpMethod.POST, "/api/v1/auth/login", json("username" to email, "password" to "Passw0rd!123", "hospitalId" to claimHospitalId))
      assert2xx(login, "login $email: ${login.body.take(300)}")
      return `data`(login)["accessToken"].asText()
    }

    private fun registerDoctor(hospitalId: String): String {
      val s = suffix()
      val email = "doctor-$s@test.local"
      val register =
        api(
          HttpMethod.POST,
          "/api/v1/auth/register",
          json(
            "email" to email,
            "password" to "Passw0rd!123",
            "firstName" to "Doc",
            "lastName" to "Ten$s",
            "roles" to listOf("DOCTOR"),
            "hospitalId" to hospitalId,
          ),
        )
      assert2xx(register, "register doctor $email: ${register.body.take(300)}")
      return email
    }

    private fun createPatient(
      token: String,
      claimHospitalId: String? = null,
    ): JsonNode {
      val s = suffix()
      val r =
        api(
          HttpMethod.POST,
          "/api/v1/patients",
          json(
            "firstName" to "Tnt",
            "lastName" to "Iso$s",
            "gender" to "male",
            "dateOfBirth" to "1990-01-01",
            "hospitalId" to claimHospitalId,
          ),
          token,
        )
      assert2xx(r, "tenant patient create: ${r.body.take(300)}")
      return `data`(r)["patient"]
    }

    @Test
    fun crossTenantAccessIsDeniedAndServerPinsTenant() {
      val admin = adminToken()
      val s = suffix()
      val h1r = api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "Tenant A $s", "code" to "TA-$s", "country" to "Qatar"), admin)
      assert2xx(h1r, "create hospital A")
      val h1 = `data`(h1r)["id"].asText()
      val h2r = api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "Tenant B $s", "code" to "TB-$s", "country" to "Qatar"), admin)
      assert2xx(h2r, "create hospital B")
      val h2 = `data`(h2r)["id"].asText()

      val doc1Email = registerDoctor(h1)
      val doc1 = hospitalLogin(doc1Email)

      // Server-side tenant resolution: create without any hospital claim.
      val p1 = createPatient(doc1)
      assertEquals(h1, p1["hospitalId"].asText(), "patient tenant must be resolved server-side from user_role: $p1")

      // Client claiming a foreign hospital on the payload must be overridden.
      val p2 = createPatient(doc1, h2)
      assertEquals(h1, p2["hospitalId"].asText(), "client hospital claim must not override server-resolved tenant: $p2")

      // Login claiming a foreign hospital must not widen the token's tenant.
      val claimed = hospitalLogin(doc1Email, h2)
      val p3 = createPatient(claimed)
      assertEquals(h1, p3["hospitalId"].asText(), "claimed hospital at login must not widen tenant: $p3")

      // Search pinned to the own hospital even when a foreign id is requested.
      val search = api(HttpMethod.GET, "/api/v1/patients?hospitalId=$h2", token = doc1)
      assert2xx(search, "tenant-scoped search")
      val rows = `data`(search)
      assertTrue(rows.isArray, "search must return an array")
      assertTrue(rows.toList().none { it["hospitalId"].asText() == h2 }, "cross-tenant patients must not leak into search: ${search.body.take(300)}")

      // Foreign hospital-config path identifiers are denied for hospital-bound users.
      val tree = api(HttpMethod.GET, "/api/v1/hospital-config/hospitals/$h2/tree", token = doc1)
      assertEquals(403, tree.status, "foreign hospital-config tree must be denied: ${tree.body.take(300)}")
      val dept =
        api(HttpMethod.POST, "/api/v1/hospital-config/hospitals/$h2/departments", json("name" to "Trespass", "code" to "TR-$s", "type" to "outpatient"), doc1)
      assertEquals(403, dept.status, "foreign hospital-config write must be denied: ${dept.body.take(300)}")

      // Config documents are hospital-scoped the same way (00.05).
      val cfgPut =
        api(
          HttpMethod.PUT,
          "/api/v1/hospital-config/hospitals/$h2/config/working-hours",
          json("timezone" to "UTC", "days" to listOf(mapOf("day" to "mon", "open" to "08:00", "close" to "18:00"))),
          doc1,
        )
      assertEquals(403, cfgPut.status, "foreign config publish must be denied: ${cfgPut.body.take(300)}")

      // 00.17 gate-adequacy fix: patient detail, child reads/writes and photo are tenant-scoped
      // like the write paths (the blind re-verification found GET detail unguarded).
      val doc2Email = registerDoctor(h2)
      val doc2 = hospitalLogin(doc2Email)
      val foreignPatient = createPatient(doc2)
      val foreignId = foreignPatient["id"].asText()
      assertEquals(
        403,
        api(HttpMethod.GET, "/api/v1/patients/$foreignId", token = doc1).status,
        "foreign patient detail read must be denied",
      )
      assertEquals(
        403,
        api(HttpMethod.GET, "/api/v1/patients/$foreignId/allergies", token = doc1).status,
        "foreign patient child read must be denied",
      )
      assertEquals(
        403,
        api(HttpMethod.POST, "/api/v1/patients/$foreignId/allergies", json("allergen" to "Penicillin"), doc1).status,
        "foreign patient child write must be denied",
      )
      assertEquals(
        403,
        api(HttpMethod.GET, "/api/v1/patients/$foreignId/photo", token = doc1).status,
        "foreign patient photo read must be denied",
      )
      // Own-tenant reads still work after the guard (same patient, same hospital).
      assert2xx(api(HttpMethod.GET, "/api/v1/patients/$foreignId", token = doc2), "own tenant detail read allowed")

      // Platform admin (null hospital) keeps unrestricted access to both tenants.
      assert2xx(api(HttpMethod.GET, "/api/v1/hospital-config/hospitals/$h1/tree", token = admin), "admin tree A")
      assert2xx(api(HttpMethod.GET, "/api/v1/hospital-config/hospitals/$h2/tree", token = admin), "admin tree B")
    }

    // 00.04 tenant sweep denial matrix: one scenario per fixed module family,
    // run in one test because each needs its own two fresh tenants.
    @Test
    fun tenantSweepDenialMatrix() {
      val admin = adminToken()
      val s = suffix()
      val h1r = api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "Sweep A $s", "code" to "SA-$s", "country" to "Qatar"), admin)
      assert2xx(h1r, "create sweep hospital A")
      val h1 = `data`(h1r)["id"].asText()
      val h2r = api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "Sweep B $s", "code" to "SB-$s", "country" to "Qatar"), admin)
      assert2xx(h2r, "create sweep hospital B")
      val h2 = `data`(h2r)["id"].asText()

      val doc1 = hospitalLogin(registerDoctor(h1))
      val doc2 = hospitalLogin(registerDoctor(h2))
      val p2 = createPatient(doc2)
      val foreignPatientId = p2["id"].asText()
      val foreignLastName = p2["lastName"].asText()

      // U22/D6 hospital directory: platform-admin-only.
      assertEquals(
        403,
        api(HttpMethod.GET, "/api/v1/hospital-config/hospitals", token = doc1).status,
        "U22: hospital directory must be denied for hospital-bound users",
      )
      assert2xx(api(HttpMethod.GET, "/api/v1/hospital-config/hospitals", token = admin), "U22: admin directory read allowed")

      // U1 OpenSearch: doc1 must never see h2 patients even after the index settles.
      var doc1ForeignHits = -1
      for (i in 0 until 8) {
        val sr = api(HttpMethod.GET, "/api/v1/search/search?q=$foreignLastName", token = doc1)
        assert2xx(sr, "U1 doc1 search: ${sr.body.take(300)}")
        doc1ForeignHits = `data`(sr)["hits"].toList().count { it["fields"]["hospitalId"].asText() == h2 }
        if (doc1ForeignHits > 0) break
        Thread.sleep(1500)
      }
      assertEquals(0, doc1ForeignHits, "U1: doc1 search must not return h2 patients")
      var adminFound = false
      for (i in 0 until 8) {
        val ar = api(HttpMethod.GET, "/api/v1/search/search?q=$foreignLastName", token = admin)
        assert2xx(ar, "U1 admin search: ${ar.body.take(300)}")
        adminFound = `data`(ar)["hits"].toList().any { it["fields"]["hospitalId"].asText() == h2 }
        if (adminFound) break
        Thread.sleep(1500)
      }
      assertTrue(adminFound, "U1: admin must find the h2 patient (patient was never indexed)")

      // U2 analytics: report endpoints are SYSTEM_ADMIN-gated by pre-existing RBAC (no hospital-bound
      // role holds report:*); hospital scoping of hid-bound analytics is covered at service level
      // (AnalyticsService hid-first JPQL). Admin (hid=null) must get unbounded totals.
      assertEquals(
        403,
        api(HttpMethod.GET, "/api/v1/analytics/patients", token = doc1).status,
        "U2: analytics must stay SYSTEM_ADMIN-gated for hospital-bound users",
      )
      assert2xx(api(HttpMethod.GET, "/api/v1/analytics/patients", token = admin), "U2: analytics allowed for platform admin")

      // U4/D2 + U10 + U20: since V54 (01.01), hospital-bound roles hold scheduling READ, so the
      // appointment list is allowed (hid-scoped by the interceptor + hid-first repo); writes stay
      // admin/reception-only. Cross-tenant by-id guards in these domains are covered at service
      // level (TenantGuards.requireSameHospital wiring + hid-first repo tests).
      assert2xx(api(HttpMethod.GET, "/api/v1/appointments", token = doc1), "U4: appointment list allowed for hospital-bound user (V54 scheduling read)")
      assertEquals(
        403,
        api(HttpMethod.POST, "/api/v1/appointments", json("x" to 1), doc2).status,
        "U4/D2: appointment create must be denied for hospital-bound doctor",
      )
      assert2xx(api(HttpMethod.GET, "/api/v1/appointments", token = admin), "U4: appointment list allowed for platform admin")

      val queueType = "SW$s"
      assertEquals(
        403,
        api(HttpMethod.POST, "/api/v1/queue/entries", json("queueType" to queueType, "patientId" to foreignPatientId), doc2).status,
        "U10: queue add must be denied for hospital-bound user",
      )
      assertEquals(
        403,
        api(HttpMethod.GET, "/api/v1/queue/board?queueType=$queueType", token = doc1).status,
        "U10: queue board must be denied for hospital-bound user",
      )
      assert2xx(api(HttpMethod.GET, "/api/v1/queue/board?queueType=$queueType", token = admin), "U10: queue board allowed for platform admin")

      assertEquals(
        403,
        api(HttpMethod.POST, "/api/v1/scribe/sessions", json("patientId" to foreignPatientId, "patientName" to p2["lastName"].asText()), doc2).status,
        "U20: scribe create must be denied for hospital-bound user",
      )
      assertEquals(
        403,
        api(HttpMethod.GET, "/api/v1/scribe/sessions", token = doc1).status,
        "U20: scribe list must be denied for hospital-bound user",
      )
      assert2xx(api(HttpMethod.GET, "/api/v1/scribe/sessions", token = admin), "U20: scribe list allowed for platform admin")
    }
  }

  @Nested
  inner class CoverageTests {
    private fun totpCode(secretBase64: String): String {
      val secret =
        java.util.Base64
          .getDecoder()
          .decode(secretBase64)
      val counter = System.currentTimeMillis() / 1000 / 30
      val counterBytes =
        java.nio.ByteBuffer
          .allocate(8)
          .putLong(counter)
          .array()
      val mac = javax.crypto.Mac.getInstance("HmacSHA1")
      mac.init(javax.crypto.spec.SecretKeySpec(secret, "HmacSHA1"))
      val hash = mac.doFinal(counterBytes)
      val offset = hash[hash.size - 1].toInt() and 0x0f
      val binary =
        ((hash[offset].toInt() and 0x7f) shl 24) or
          ((hash[offset + 1].toInt() and 0xff) shl 16) or
          ((hash[offset + 2].toInt() and 0xff) shl 8) or
          (hash[offset + 3].toInt() and 0xff)
      return (binary % 1_000_000).toString().padStart(6, '0')
    }

    private fun adminUserId(): String {
      val login =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/auth/login",
            json(
              "username" to "admin@squamed.local",
              "password" to "Admin@12345",
            ),
          ),
        )
      return login.get("userId")?.asText() ?: login.get("user").get("id").asText()
    }

    @Test
    fun catalogsAndHospitalStructureCoverage() {
      val token = adminToken()
      val s = suffix()
      val hospitalId = ensureSeed(token)

      val spec =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/hospital-config/hospitals/$hospitalId/specialties",
            json(
              "name" to "Spec $s",
              "code" to "SP-$s",
              "description" to "x",
            ),
            token,
          ),
        )
      assertNotNull(spec["id"])
      assert2xx(api(HttpMethod.GET, "/api/v1/hospital-config/hospitals/$hospitalId/specialties", token = token), "list specialties")

      val ward =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/hospital-config/hospitals/$hospitalId/wards",
            json(
              "name" to "Ward $s",
              "code" to "WD-$s",
              "type" to "General",
            ),
            token,
          ),
        )
      assertNotNull(ward["id"])
      assert2xx(api(HttpMethod.GET, "/api/v1/hospital-config/hospitals/$hospitalId/wards", token = token), "list wards")

      val bed =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/hospital-config/hospitals/$hospitalId/beds",
            json(
              "label" to "Bed-$s",
              "type" to "General",
              "status" to "available",
            ),
            token,
          ),
        )
      val bedId = bed["id"].asText()
      assert2xx(api(HttpMethod.GET, "/api/v1/hospital-config/hospitals/$hospitalId/beds", token = token), "list beds")
      assert2xx(
        api(
          HttpMethod.PATCH,
          "/api/v1/hospital-config/hospitals/$hospitalId/beds/$bedId/status",
          json("status" to "occupied"),
          token,
        ),
        "set bed status",
      )

      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/hospital-config/hospitals/$hospitalId/services",
          json(
            "name" to "Svc $s",
            "code" to "SV-$s",
            "category" to "clinical",
            "isActive" to true,
          ),
          token,
        ),
        "create service",
      )
      assert2xx(api(HttpMethod.GET, "/api/v1/hospital-config/hospitals/$hospitalId/services", token = token), "list services")

      val newHospital =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/hospital-config/hospitals",
            json(
              "name" to "New Hospital $s",
              "code" to "NH-$s",
              "country" to "QA",
              "status" to "active",
            ),
            token,
          ),
        )
      val nhId = newHospital["id"].asText()
      assert2xx(
        api(HttpMethod.PUT, "/api/v1/hospital-config/hospitals/$nhId", json("name" to "New Hospital $s v2"), token),
        "update hospital",
      )

      val drug =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/master-data/drugs",
            json(
              "code" to "CD-$s",
              "name" to "Coverage Drug",
              "category" to "General",
              "unit" to "tab",
              "genericName" to "gen",
              "isControlled" to false,
            ),
            token,
          ),
        )
      val drugId = drug["id"].asText()
      assert2xx(api(HttpMethod.PUT, "/api/v1/master-data/drugs/$drugId", json("name" to "Coverage Drug v2"), token), "update drug")
      assert2xx(
        api(HttpMethod.PATCH, "/api/v1/master-data/drugs/$drugId/active", json("isActive" to true), token),
        "toggle drug active",
      )

      val lab =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/master-data/lab-tests",
            json(
              "code" to "CL-$s",
              "name" to "Coverage Lab",
              "category" to "Hematology",
              "unit" to "u/L",
            ),
            token,
          ),
        )
      val labId = lab["id"].asText()
      assert2xx(
        api(HttpMethod.PUT, "/api/v1/master-data/lab-tests/$labId", json("name" to "Coverage Lab v2"), token),
        "update lab test",
      )
      assert2xx(
        api(HttpMethod.PATCH, "/api/v1/master-data/lab-tests/$labId/active", json("isActive" to true), token),
        "toggle lab active",
      )

      val img =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/master-data/imaging-procedures",
            json(
              "code" to "IM-$s",
              "name" to "Chest X-Ray $s",
              "modality" to "XR",
              "bodyPart" to "chest",
            ),
            token,
          ),
        )
      assert2xx(
        api(HttpMethod.PUT, "/api/v1/master-data/imaging-procedures/${img["id"].asText()}", json("name" to "CXR $s v2"), token),
        "update imaging",
      )
      assert2xx(api(HttpMethod.GET, "/api/v1/master-data/imaging-procedures", token = token), "list imaging")

      val proc =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/master-data/procedure-codes",
            json(
              "code" to "PX-$s",
              "name" to "Procedure $s",
              "system" to "ICD-10",
              "category" to "surgical",
            ),
            token,
          ),
        )
      assert2xx(
        api(HttpMethod.PATCH, "/api/v1/master-data/procedure-codes/${proc["id"].asText()}/active", json("isActive" to true), token),
        "toggle procedure",
      )
      assert2xx(api(HttpMethod.GET, "/api/v1/master-data/procedure-codes", token = token), "list procedure codes")

      val diag =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/master-data/diagnosis-codes",
            json(
              "code" to "DX-$s",
              "name" to "Diagnosis $s",
              "system" to "ICD-10",
              "chapter" to "X",
            ),
            token,
          ),
        )
      assert2xx(
        api(HttpMethod.PUT, "/api/v1/master-data/diagnosis-codes/${diag["id"].asText()}", json("name" to "Diag $s v2"), token),
        "update diagnosis code",
      )
      assert2xx(api(HttpMethod.GET, "/api/v1/master-data/diagnosis-codes", token = token), "list diagnosis codes")

      val ci =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/master-data/charge-items",
            json(
              "code" to "CI-$s",
              "name" to "Charge Item $s",
              "category" to "procedure",
              "priceQar" to 50.0,
            ),
            token,
          ),
        )
      assert2xx(
        api(HttpMethod.PATCH, "/api/v1/master-data/charge-items/${ci["id"].asText()}/active", json("isActive" to true), token),
        "toggle charge item",
      )
      assert2xx(api(HttpMethod.GET, "/api/v1/master-data/charge-items", token = token), "list charge items")
      assert2xx(api(HttpMethod.GET, "/api/v1/master-data/drugs", token = token), "list drugs")
      assert2xx(api(HttpMethod.GET, "/api/v1/master-data/lab-tests", token = token), "list lab tests")
    }

    @Test
    fun catalogFilterAndPaginationCoverage() {
      val token = adminToken()
      val s = suffix()
      // 00.02: seed 3 own drugs so the paged meta block (page/size/total/pages) is provable.
      listOf(1, 2, 3).forEach { i ->
        assert2xx(
          api(HttpMethod.POST, "/api/v1/master-data/drugs", json("code" to "PMD-$s-$i", "name" to "PageMeta Drug $i $s"), token),
          "create page-meta drug $i",
        )
      }
      val p0 = api(HttpMethod.GET, "/api/v1/master-data/drugs?page=0&size=2", token = token)
      assert2xx(p0, "paged drugs page 0")
      val p0Body = om.readTree(p0.body)
      val p0Meta = p0Body["meta"]
      assertEquals(0, p0Meta["page"].asInt(), "meta.page must be 0 on the first page: $p0Meta")
      assertEquals(2, p0Meta["size"].asInt(), "meta.size must echo the requested size: $p0Meta")
      assertTrue(p0Meta["total"].asLong() >= 3, "meta.total must count all drugs: $p0Meta")
      assertEquals(
        (p0Meta["total"].asLong() + 1) / 2,
        p0Meta["pages"].asLong(),
        "meta.pages must be ceil(total/size): $p0Meta",
      )
      assertEquals(2, p0Body["data"].size(), "page 0 holds exactly size rows")
      val p1 = api(HttpMethod.GET, "/api/v1/master-data/drugs?page=1&size=2", token = token)
      assert2xx(p1, "paged drugs page 1")
      val p1Body = om.readTree(p1.body)
      assertEquals(1, p1Body["meta"]["page"].asInt(), "meta.page must echo the requested page")
      assertTrue(
        p1Body["data"][0]["id"].asText() != p0Body["data"][0]["id"].asText(),
        "page 1 must start with a different row than page 0",
      )
      // Unpaged single-resource responses keep the empty meta block.
      val created = api(HttpMethod.POST, "/api/v1/master-data/drugs", json("code" to "PMD-$s-empty", "name" to "Empty Meta $s"), token)
      assert2xx(created, "create unpaged drug")
      assertTrue(om.readTree(created.body)["meta"].isEmpty, "unpaged create keeps meta {}: ${created.body.take(200)}")
      assert2xx(api(HttpMethod.GET, "/api/v1/master-data/drugs?search=Para", token = token), "search drugs")
      assert2xx(api(HttpMethod.GET, "/api/v1/master-data/drugs?active=true", token = token), "active drugs filter")
      assert2xx(api(HttpMethod.GET, "/api/v1/master-data/drugs?search=Para&active=true", token = token), "search+active drugs")
      assert2xx(api(HttpMethod.GET, "/api/v1/master-data/drugs?page=0&size=5", token = token), "paged drugs")
      assert2xx(api(HttpMethod.GET, "/api/v1/master-data/lab-tests?search=CBC", token = token), "search lab tests")
      assert2xx(api(HttpMethod.GET, "/api/v1/master-data/lab-tests?active=false", token = token), "inactive lab filter")
      assert2xx(api(HttpMethod.GET, "/api/v1/master-data/imaging-procedures?search=X-Ray", token = token), "search imaging")
      assert2xx(api(HttpMethod.GET, "/api/v1/master-data/procedure-codes?search=proc", token = token), "search procedures")
      assert2xx(api(HttpMethod.GET, "/api/v1/master-data/diagnosis-codes?search=cough", token = token), "search diagnostics")
      assert2xx(api(HttpMethod.GET, "/api/v1/master-data/charge-items?search=charge", token = token), "search charge items")
      assert2xx(api(HttpMethod.GET, "/api/v1/master-data/charge-items?active=true", token = token), "active charge items")
      assert2xx(
        api(HttpMethod.GET, "/api/v1/master-data/charge-items?search=x&active=true", token = token),
        "search+active charge items",
      )
      listOf(
        arrayOf("code" to "X", "name" to "Y"),
        arrayOf("name" to "OnlyName"),
        arrayOf("code" to "OnlyCode"),
      ).forEach { dto ->
        assert4xx(
          api(HttpMethod.PUT, "/api/v1/master-data/drugs/${UUID.randomUUID()}", json(*dto), token),
          "update missing drug 4xx",
        )
      }
      listOf(
        arrayOf("code" to "X"),
        arrayOf("name" to "Y"),
      ).forEach { dto ->
        assert4xx(
          api(HttpMethod.PUT, "/api/v1/master-data/lab-tests/${UUID.randomUUID()}", json(*dto), token),
          "update missing lab 4xx",
        )
      }
      listOf(
        arrayOf("code" to "IMX"),
        arrayOf("name" to "ImagingX"),
      ).forEach { dto ->
        assert4xx(
          api(HttpMethod.PUT, "/api/v1/master-data/imaging-procedures/${UUID.randomUUID()}", json(*dto), token),
          "update missing imaging 4xx",
        )
      }
    }

    @Test
    fun auditLogsCursorPaginationCoverage() {
      // 00.02: /audit/logs is keyset-paginated (opaque cursor, no offset). Three own patient
      // creates guarantee fresh audit rows regardless of shared dev-DB state or test order.
      val token = adminToken()
      val s = suffix()
      repeat(3) { i ->
        assert2xx(
          api(
            HttpMethod.POST,
            "/api/v1/patients",
            json(
              "firstName" to "Cursor",
              "lastName" to "Walk$i$s",
              "gender" to "male",
              "dateOfBirth" to "1990-01-0${i + 1}",
              "phone" to "55$s$i".take(12),
              "qid" to "c$i$s".take(11),
              "nationality" to "Qatar",
              "city" to "Doha",
            ),
            token,
          ),
          "create cursor patient $i",
        )
      }

      fun metaOf(r: ApiResp): JsonNode = om.readTree(r.body)["meta"]

      fun idsOf(r: ApiResp): List<String> = `data`(r).map { it["id"].asText() }

      fun fetch(cursor: String?): ApiResp {
        val suffix = if (cursor != null) "&cursor=$cursor" else ""
        return api(HttpMethod.GET, "/api/v1/audit/logs?limit=2$suffix", token = token)
      }

      // Page 1: cursor-mode meta shape.
      val r1 = fetch(null)
      assert2xx(r1, "audit page 1")
      val m1 = metaOf(r1)
      assertEquals(2, m1["limit"].asInt(), "meta.limit echoes the limit")
      assertTrue(m1["hasNext"].asBoolean(), "hasNext true while rows remain: $m1")
      var nextCursor: String? = m1["nextCursor"].asText()
      assertTrue(nextCursor!!.isNotBlank(), "hasNext=true must carry nextCursor: $m1")
      val seen = idsOf(r1).toMutableSet()
      assertEquals(2, seen.size, "page 1 holds exactly limit rows")
      var lastSecond = `data`(r1).last()["occurredAt"].asText().take(19)

      // Continuation pages: strictly-after semantics — nothing repeats, newest first, and a
      // present nextCursor is always carried while hasNext holds.
      repeat(2) { page ->
        val cur = nextCursor ?: return@repeat
        val r = fetch(cur)
        assert2xx(r, "audit page ${page + 2}")
        val m = metaOf(r)
        val ids = idsOf(r)
        assertTrue(ids.isNotEmpty(), "page ${page + 2} must return rows")
        assertTrue(ids.none { it in seen }, "page ${page + 2} must not repeat earlier rows: $ids")
        seen.addAll(ids)
        val firstSecond = `data`(r).first()["occurredAt"].asText().take(19)
        assertTrue(lastSecond >= firstSecond, "pages must stay newest-first: $lastSecond vs $firstSecond")
        lastSecond = `data`(r).last()["occurredAt"].asText().take(19)
        if (m["hasNext"].asBoolean()) {
          nextCursor = m["nextCursor"].asText()
          assertTrue(nextCursor!!.isNotBlank(), "hasNext=true must carry nextCursor: $m")
        } else {
          assertTrue(!m.has("nextCursor"), "exhausted page must not carry nextCursor: $m")
          nextCursor = null
        }
      }

      // No matching rows = end of stream: hasNext false, no nextCursor (also exercises the
      // optional entityType filter branch).
      val empty = api(HttpMethod.GET, "/api/v1/audit/logs?limit=2&entityType=NoSuchEntity_$s", token = token)
      assert2xx(empty, "audit empty filter")
      val mEmpty = metaOf(empty)
      assertTrue(!mEmpty["hasNext"].asBoolean(), "no rows -> hasNext false: $mEmpty")
      assertTrue(!mEmpty.has("nextCursor"), "no nextCursor at the end of stream")
      assertEquals(0, `data`(empty).size(), "empty filter page has no rows")

      // Unparsable cursor -> 400 INVALID_CURSOR on the error envelope.
      val bad = api(HttpMethod.GET, "/api/v1/audit/logs?limit=2&cursor=!!bad!!", token = token)
      assert4xx(bad, "invalid cursor rejected")
      assertEquals("INVALID_CURSOR", om.readTree(bad.body)["error"]["code"].asText(), "invalid cursor error code: ${bad.body.take(200)}")
    }

    @Test
    fun masterDataRegistriesCoverage() {
      val token = adminToken()
      val s = suffix()

      // Fresh hospitals per run: the dev database persists rows across runs.
      val h1 =
        `data`(
          api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "Reg A $s", "code" to "RA-$s", "country" to "Qatar"), token),
        )["id"].asText()
      val h2 =
        `data`(
          api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "Reg B $s", "code" to "RB-$s", "country" to "Qatar"), token),
        )["id"].asText()

      // Vendor: server-side pin from the explicit hospital id, per-hospital uniqueness.
      val vendor =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/master-data/vendors",
            json("code" to "VN-$s", "name" to "MedSupply $s", "contactPerson" to "Rep", "phone" to "55111111", "hospitalId" to h1),
            token,
          ),
        )
      assertEquals(h1, vendor["hospitalId"].asText(), "vendor must be pinned to the requested hospital: $vendor")
      assert4xx(
        api(HttpMethod.POST, "/api/v1/master-data/vendors", json("code" to "VN-$s", "name" to "Dup $s", "hospitalId" to h1), token),
        "duplicate vendor code must be rejected",
      )
      assert2xx(
        api(HttpMethod.PUT, "/api/v1/master-data/vendors/${vendor["id"].asText()}", json("phone" to "55222222"), token),
        "update vendor",
      )

      // Equipment referencing the vendor.
      val equipment =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/master-data/equipment",
            json(
              "code" to "EQ-$s",
              "name" to "Ventilator $s",
              "category" to "ICU",
              "vendorId" to vendor["id"].asText(),
              "serialNumber" to "SN-$s",
              "location" to "ICU-1",
              "purchaseDate" to "2025-01-15",
              "warrantyUntil" to "2028-01-15",
              "hospitalId" to h1,
            ),
            token,
          ),
        )
      assertEquals(vendor["id"].asText(), equipment["vendorId"].asText(), "equipment vendor link must persist: $equipment")
      assert2xx(
        api(HttpMethod.PATCH, "/api/v1/master-data/equipment/${equipment["id"].asText()}/active", json("isActive" to false), token),
        "toggle equipment active",
      )

      // Employee master: demographic + credential fields.
      val employee =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/master-data/employees",
            json(
              "code" to "EMP-$s",
              "firstName" to "Asha",
              "lastName" to "Rao",
              "gender" to "female",
              "dateOfBirth" to "1988-03-03",
              "phone" to "55333333",
              "email" to "asha-$s@hospital.local",
              "designation" to "Consultant",
              "department" to "Cardiology",
              "qualification" to "MBBS, MD",
              "licenseNumber" to "LIC-$s",
              "licenseExpiry" to "2027-06-30",
              "hospitalId" to h1,
            ),
            token,
          ),
        )
      assertEquals("1988-03-03", employee["dateOfBirth"].asText(), "employee demographics must persist: $employee")
      assertEquals("LIC-$s", employee["licenseNumber"].asText(), "employee credentials must persist: $employee")
      assert2xx(
        api(HttpMethod.PUT, "/api/v1/master-data/employees/${employee["id"].asText()}", json("designation" to "Senior Consultant"), token),
        "update employee",
      )
      assert2xx(api(HttpMethod.GET, "/api/v1/master-data/employees?search=Asha", token = token), "search employees by name")

      // Price list + items (charge item is a global catalog row).
      val chargeItem =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/master-data/charge-items",
            json("code" to "CI-REG-$s", "name" to "Registry Charge $s", "priceQar" to 100.0),
            token,
          ),
        )
      val priceList =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/master-data/price-lists",
            json("code" to "PL-$s", "name" to "Standard $s", "currency" to "QAR", "validFrom" to "2026-01-01", "hospitalId" to h1),
            token,
          ),
        )
      val plId = priceList["id"].asText()
      assert2xx(
        api(HttpMethod.POST, "/api/v1/master-data/price-lists/$plId/items", json("chargeItemId" to chargeItem["id"].asText(), "priceQar" to 85.5), token),
        "add price list item",
      )
      // Upsert: same pair re-prices instead of failing.
      val repriced =
        `data`(
          api(HttpMethod.POST, "/api/v1/master-data/price-lists/$plId/items", json("chargeItemId" to chargeItem["id"].asText(), "priceQar" to 90.0), token),
        )
      assertEquals(0, java.math.BigDecimal("90.0").compareTo(repriced["priceQar"].decimalValue()), "price list upsert must reprice: $repriced")

      // Isolation: hospital-filtered lists never leak the other hospital's rows.
      val h1Vendors = `data`(api(HttpMethod.GET, "/api/v1/master-data/vendors?hospitalId=$h1&search=MedSupply", token = token))
      assertTrue(
        h1Vendors.size() >= 1 && h1Vendors.toList().all { it["hospitalId"].asText() == h1 },
        "h1 vendor filter must return only h1 rows: $h1Vendors",
      )
      val h2Vendors = `data`(api(HttpMethod.GET, "/api/v1/master-data/vendors?hospitalId=$h2", token = token))
      assertTrue(h2Vendors.toList().none { it["code"].asText() == "VN-$s" }, "h2 vendor list must not contain h1 rows: $h2Vendors")
      assert2xx(api(HttpMethod.GET, "/api/v1/master-data/equipment?hospitalId=$h1", token = token), "hospital-filtered equipment")
      assert2xx(api(HttpMethod.GET, "/api/v1/master-data/employees?hospitalId=$h1&active=true", token = token), "hospital-filtered employees")
      assert2xx(api(HttpMethod.GET, "/api/v1/master-data/price-lists?hospitalId=$h1", token = token), "hospital-filtered price lists")
    }

    @Test
    fun patientMasterCompletionCoverage() {
      val token = adminToken()
      val s = suffix()

      // Fresh hospitals per run: the dev database persists rows across runs.
      val h1 =
        `data`(
          api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "PM-A $s", "code" to "PA-$s", "country" to "Qatar"), token),
        )["id"].asText()
      val h2 =
        `data`(
          api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "PM-B $s", "code" to "PB-$s", "country" to "Qatar"), token),
        )["id"].asText()

      // Unknown-patient flow: temporary record with sentinel identity, pending until identified.
      val unknown =
        `data`(
          api(HttpMethod.POST, "/api/v1/patients", json("unknownPatient" to true, "hospitalId" to h1), token),
        )["patient"]
      val unknownId = unknown["id"].asText()
      assertTrue(unknown["isUnknown"].asBoolean(), "unknown flag must be set: $unknown")
      assertEquals("pending", unknown["recordsStatus"].asText(), "unknown record must be pending: $unknown")
      assertEquals("Unknown", unknown["firstName"].asText(), "sentinel first name: $unknown")
      assertEquals("1900-01-01", unknown["dateOfBirth"].asText(), "sentinel DOB: $unknown")
      assertEquals("unknown", unknown["gender"].asText(), "sentinel gender: $unknown")

      // Identification: a real name + DOB on PUT closes the unknown state.
      val identified =
        `data`(
          api(
            HttpMethod.PUT,
            "/api/v1/patients/$unknownId",
            json("firstName" to "Asha", "lastName" to "Rao", "dateOfBirth" to "1990-05-05", "gender" to "female"),
            token,
          ),
        )
      assertFalse(identified["isUnknown"].asBoolean(), "identification must clear unknown: $identified")
      assertEquals("active", identified["recordsStatus"].asText(), "identified record must be active: $identified")
      assertEquals("1990-05-05", identified["dateOfBirth"].asText(), "identified DOB must replace sentinel: $identified")

      // ABHA identifiers: format-validated, stored as patient_identifier rows, unique per value.
      // Values are unique per run: the dev database persists rows, a fixed number would
      // collide with the previous run's insert (DUPLICATE_IDENTIFIER).
      val abhaNumber = "90%012d".format(System.currentTimeMillis() % 1_000_000_000_000L)
      val abhaAddress = "asha.rao.%010d@abdm".format(System.currentTimeMillis() % 10_000_000_000L)
      assert4xx(
        api(HttpMethod.POST, "/api/v1/patients/$unknownId/identifiers", json("idType" to "ABHA", "idValue" to "1234"), token),
        "ABHA number below 14 digits must be rejected",
      )
      assert4xx(
        api(HttpMethod.POST, "/api/v1/patients/$unknownId/identifiers", json("idType" to "ABHA_ADDRESS", "idValue" to "bad address"), token),
        "malformed ABHA address must be rejected",
      )
      val abha =
        `data`(
          api(HttpMethod.POST, "/api/v1/patients/$unknownId/identifiers", json("idType" to "ABHA", "idValue" to abhaNumber), token),
        )
      assertEquals(abhaNumber, abha["idValue"].asText(), "ABHA number must persist: $abha")
      assert2xx(
        api(HttpMethod.POST, "/api/v1/patients/$unknownId/identifiers", json("idType" to "ABHA_ADDRESS", "idValue" to abhaAddress), token),
        "valid ABHA address is accepted (normalised to lowercase)",
      )
      assert4xx(
        api(HttpMethod.POST, "/api/v1/patients/$unknownId/identifiers", json("idType" to "ABHA", "idValue" to abhaNumber), token),
        "duplicate identifier value must be rejected",
      )
      // ABHA value resolves through patient search (00.07 gap: identifier search).
      val search =
        `data`(api(HttpMethod.GET, "/api/v1/patients?query=$abhaNumber", token = token))
      assertTrue(search.any { it["id"].asText() == unknownId }, "identifier search must resolve to the owning patient: $search")

      // Relationships: guardian/family links, no self-link, cross-hospital rejected, unique triple.
      val related =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/patients",
            json("firstName" to "Ravi", "lastName" to "Rao", "dateOfBirth" to "1965-01-01", "gender" to "male", "hospitalId" to h1),
            token,
          ),
        )["patient"]
      val relatedId = related["id"].asText()
      val relationship =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/patients/$unknownId/relationships",
            json("relatedPatientId" to relatedId, "relationshipType" to "guardian", "isPrimaryGuardian" to true),
            token,
          ),
        )
      assertEquals(relatedId, relationship["relatedPatientId"].asText(), "relationship must persist: $relationship")
      assert4xx(
        api(HttpMethod.POST, "/api/v1/patients/$unknownId/relationships", json("relatedPatientId" to unknownId, "relationshipType" to "guardian"), token),
        "patient cannot be linked to itself",
      )
      assert4xx(
        api(HttpMethod.POST, "/api/v1/patients/$unknownId/relationships", json("relatedPatientId" to relatedId, "relationshipType" to "guardian"), token),
        "duplicate relationship triple must be rejected",
      )
      val outsider =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/patients",
            json("firstName" to "Out", "lastName" to "Sider", "dateOfBirth" to "1980-01-01", "gender" to "female", "hospitalId" to h2),
            token,
          ),
        )["patient"]
      assert4xx(
        api(
          HttpMethod.POST,
          "/api/v1/patients/$unknownId/relationships",
          json("relatedPatientId" to outsider["id"].asText(), "relationshipType" to "spouse"),
          token,
        ),
        "relationship across hospitals must be rejected",
      )
      val relList = `data`(api(HttpMethod.GET, "/api/v1/patients/$relatedId/relationships", token = token))
      assertTrue(
        relList.any {
          it["id"].asText() == relationship["id"].asText() && it["reverse"].asBoolean()
        },
        "reverse relationship must be listed for the related party: $relList",
      )

      // Merge: cross-hospital pairs must be refused (same-hospital merge covered by EMPI tests).
      assert4xx(
        api(HttpMethod.POST, "/api/v1/patients/merge", json("targetId" to unknownId, "duplicateId" to outsider["id"].asText()), token),
        "merge across hospitals must be rejected",
      )

      // Photo: multipart upload to S3/MinIO, bytes retrievable, content types enforced.
      val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00, 0x01, 0x02, 0x03, 0xFF.toByte(), 0xD9.toByte())
      val parts = org.springframework.util.LinkedMultiValueMap<String, Any>()
      val resource =
        object : org.springframework.core.io.ByteArrayResource(jpegBytes) {
          override fun getFilename(): String = "photo.jpg"
        }
      parts.add("file", resource)
      val uploadHeaders =
        HttpHeaders().apply {
          contentType = MediaType.MULTIPART_FORM_DATA
          setBearerAuth(token)
        }
      val upload =
        rest.exchange("/api/v1/patients/$unknownId/photo", HttpMethod.POST, HttpEntity(parts, uploadHeaders), String::class.java)
      assertTrue(upload.statusCode.is2xxSuccessful, "photo upload -> ${upload.body?.take(300)}")
      val photoKey = om.readTree(upload.body ?: "{}")["data"]["photoKey"].asText()
      assertTrue(photoKey.startsWith("photos/"), "photo key must be an object-storage key: $photoKey")

      val photoBytes =
        try {
          val resp =
            rest.exchange(
              "/api/v1/patients/$unknownId/photo",
              HttpMethod.GET,
              HttpEntity<Void?>(HttpHeaders().apply { setBearerAuth(token) }),
              ByteArray::class.java,
            )
          Pair(resp.statusCode.value(), resp.body ?: ByteArray(0))
        } catch (e: RestClientResponseException) {
          Pair(e.statusCode.value(), ByteArray(0))
        }
      assertEquals(200, photoBytes.first, "photo download must succeed")
      assertTrue(jpegBytes.contentEquals(photoBytes.second), "downloaded photo bytes must match the upload")

      val badParts = org.springframework.util.LinkedMultiValueMap<String, Any>()
      badParts.add(
        "file",
        object : org.springframework.core.io.ByteArrayResource(ByteArray(10)) {
          override fun getFilename(): String = "notes.txt"
        },
      )
      // RestTemplate throws on 4xx; capture the status either way.
      val badUploadStatus =
        try {
          rest
            .exchange(
              "/api/v1/patients/$unknownId/photo",
              HttpMethod.POST,
              HttpEntity(
                badParts,
                HttpHeaders().apply {
                  // Request stays multipart; the server derives the part type from the filename
                  // (notes.txt -> text/plain) and must reject it as unsupported.
                  contentType = MediaType.MULTIPART_FORM_DATA
                  setBearerAuth(token)
                },
              ),
              String::class.java,
            ).statusCode
            .value()
        } catch (e: RestClientResponseException) {
          e.statusCode.value()
        }
      assertTrue(badUploadStatus in 400..499, "non-image content type must be rejected: $badUploadStatus")

      // Tenant guard: a hospital-bound doctor cannot update patients of another hospital.
      val doctorEmail = "doctor-$s@test.local"
      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/auth/register",
          json(
            "email" to doctorEmail,
            "password" to "Passw0rd!123",
            "firstName" to "Doc",
            "lastName" to "PM$s",
            "roles" to listOf("DOCTOR"),
            "hospitalId" to h1,
          ),
        ),
        "register doctor for tenant guard",
      )
      val doctorToken =
        `data`(api(HttpMethod.POST, "/api/v1/auth/login", json("username" to doctorEmail, "password" to "Passw0rd!123")))["accessToken"].asText()
      val crossUpdate = api(HttpMethod.PUT, "/api/v1/patients/${outsider["id"].asText()}", json("phone" to "55999999"), doctorToken)
      assertEquals(403, crossUpdate.status, "cross-hospital patient update must be 403: ${crossUpdate.body.take(300)}")
      assert4xx(
        api(HttpMethod.POST, "/api/v1/patients/${outsider["id"].asText()}/identifiers", json("idType" to "OTHER", "idValue" to "X-$s"), doctorToken),
        "cross-hospital identifier write must be denied",
      )
    }

    @Test
    fun empiInputIdentifiersAndLifecycleCoverage() {
      val token = adminToken()
      val s = suffix()
      val qidA = "20000000$s".take(11)

      val created =
        api(
          HttpMethod.POST,
          "/api/v1/patients",
          json(
            "firstName" to "Life",
            "lastName" to "Cycle$s",
            "gender" to "female",
            "dateOfBirth" to "1995-05-05",
            "phone" to "56$s".take(11),
            "qid" to qidA,
            "nationality" to "QA",
            "city" to "Doha",
          ),
          token,
        )
      assert2xx(created, "create lifecycle patient")
      val pid = `data`(created)["patient"]["id"].asText()

      val byInputIdentifiers =
        api(
          HttpMethod.POST,
          "/api/v1/patients/match",
          json(
            "firstName" to "Life",
            "lastName" to "Cycle$s",
            "identifiers" to listOf(mapOf("idType" to "QID", "idValue" to qidA)),
          ),
          token,
        )
      assert2xx(byInputIdentifiers, "match by identifiers input")
      assertTrue(`data`(byInputIdentifiers).any { it["patientId"].asText() == pid }, "identifier input match returned")

      val noIdentityMatch =
        api(
          HttpMethod.POST,
          "/api/v1/patients/match",
          json(
            "firstName" to "Unrelated",
            "lastName" to "Nobody$s",
          ),
          token,
        )
      assert2xx(noIdentityMatch, "match with no criteria")

      assert2xx(
        api(
          HttpMethod.PUT,
          "/api/v1/patients/$pid",
          json(
            "isDeceased" to true,
            "deathDate" to "2026-09-01",
            "deceasedNote" to "recorded",
          ),
          token,
        ),
        "mark patient deceased",
      )
      val detail = `data`(api(HttpMethod.GET, "/api/v1/patients/$pid", token = token))
      assertEquals(true, detail["isDeceased"].asBoolean())
      assertEquals("2026-09-01", detail["deathDate"].asText())

      val dupFlag =
        api(
          HttpMethod.POST,
          "/api/v1/patients/$pid/notes",
          json(
            "title" to "flag",
            "body" to "complaint",
            "isFlagged" to true,
          ),
          token,
        )
      assert2xx(dupFlag, "add flagged note")
    }

    @Test
    fun hl7ReceiveVariantsAndLockoutCoverage() {
      val token = adminToken()
      val s = suffix()

      val oru = "MSH|^~\\&|E2E|HOSP|SQUAMED|MIR|20260906120000||ORU^R01^ORU_R01|ORU-$s|P|2.5\rOBX|1|NM|LOINC^WBC||5.0|10^9/L|4-10|||F"
      val oruResp = api(HttpMethod.POST, "/api/v1/hl7/messages", json("raw" to oru), token)
      assert2xx(oruResp, "receive non-adt oru")
      assertEquals("AA", `data`(oruResp)["ackCode"].asText())

      val unnamed = "MSH|^~\\&|E2E|HOSP|SQUAMED|MIR|20260906120000||ADT^A04^ADT_A04|NM-$s|P|2.5\rEVN|A04|20260906120000\rPID|1||MRN-NM-$s||||19800101|M\r"
      val unnamedResp = api(HttpMethod.POST, "/api/v1/hl7/messages", json("raw" to unnamed), token)
      assert2xx(unnamedResp, "receive adt without name")
      assertEquals("AE", `data`(unnamedResp)["ackCode"].asText())

      val email = "lock-$s@test.local"
      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/auth/register",
          json(
            "email" to email,
            "password" to "Passw0rd!123",
            "firstName" to "Lock",
            "lastName" to "User",
          ),
        ),
        "register lockout user",
      )
      for (attempt in 1..5) {
        assertEquals(
          401,
          api(HttpMethod.POST, "/api/v1/auth/login", json("username" to email, "password" to "WrongPass!1")).status,
          "wrong attempt $attempt",
        )
      }
      val locked = api(HttpMethod.POST, "/api/v1/auth/login", json("username" to email, "password" to "Passw0rd!123"))
      assertTrue(
        locked.status in 401..499 || `data`(locked)?.get("status")?.asText() == "ACCOUNT_LOCKED",
        "account locked after 5 failures -> ${locked.status}",
      )
    }

    @Test
    fun workflowValidationAndEdgeCoverage() {
      val token = adminToken()
      val s = suffix()

      val emptySteps =
        api(
          HttpMethod.POST,
          "/api/v1/workflow/definitions",
          json(
            "code" to "WFE_EMPTY_$s",
            "name" to "Empty",
            "steps" to emptyList<Any>(),
          ),
          token,
        )
      assertTrue(emptySteps.status in 400..499, "workflow without steps rejected")

      val dupKeys =
        api(
          HttpMethod.POST,
          "/api/v1/workflow/definitions",
          json(
            "code" to "WFE_KEYS_$s",
            "name" to "Dup Keys",
            "steps" to
              listOf<Map<String, Any?>>(
                mapOf(
                  "stepKey" to "a",
                  "stepType" to "ACTION",
                  "sequenceOrder" to 0,
                  "config" to mapOf<String, Any?>("type" to "x"),
                ),
                mapOf(
                  "stepKey" to "a",
                  "stepType" to "ACTION",
                  "sequenceOrder" to 1,
                  "config" to mapOf<String, Any?>("type" to "y"),
                ),
              ),
          ),
          token,
        )
      assertTrue(dupKeys.status in 400..499, "duplicate step keys rejected")

      val badType =
        api(
          HttpMethod.POST,
          "/api/v1/workflow/definitions",
          json(
            "code" to "WFE_BAD_$s",
            "name" to "Bad Type",
            "steps" to
              listOf<Map<String, Any?>>(
                mapOf(
                  "stepKey" to "x",
                  "stepType" to "CLICHE",
                  "sequenceOrder" to 0,
                  "config" to mapOf<String, Any?>("type" to "x"),
                ),
              ),
          ),
          token,
        )
      assertTrue(badType.status in 400..499, "unsupported step type rejected")

      val failCond =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/workflow/definitions",
            json(
              "code" to "WFE_COND_$s",
              "name" to "Fail Cond",
              "steps" to
                listOf<Map<String, Any?>>(
                  mapOf(
                    "stepKey" to "c",
                    "stepType" to "CONDITION",
                    "sequenceOrder" to 0,
                    "config" to mapOf<String, Any?>("expression" to "amount > 500", "ifFalse" to "fail"),
                  ),
                ),
            ),
            token,
          ),
        )["id"].asText()
      assert2xx(
        api(HttpMethod.POST, "/api/v1/workflow/definitions/$failCond/activate", json(), token),
        "activate fail-cond definition",
      )
      val failedInstance =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/workflow/instances",
            json(
              "definitionId" to failCond,
              "payload" to mapOf<String, Any?>("amount" to 10),
            ),
            token,
          ),
        )
      assertEquals("FAILED", failedInstance["status"].asText())

      val conditionDef =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/workflow/definitions",
            json(
              "code" to "WFE_LE_$s",
              "name" to "Numeric Cond",
              "steps" to
                listOf<Map<String, Any?>>(
                  mapOf(
                    "stepKey" to "c",
                    "stepType" to "CONDITION",
                    "sequenceOrder" to 0,
                    "config" to mapOf<String, Any?>("expression" to "amount >= 30", "ifFalse" to "fail"),
                  ),
                  mapOf(
                    "stepKey" to "z",
                    "stepType" to "NOTIFICATION",
                    "sequenceOrder" to 1,
                    "config" to mapOf<String, Any?>("recipientId" to adminUserId(), "title" to "cond ok"),
                  ),
                ),
            ),
            token,
          ),
        )["id"].asText()
      assert2xx(
        api(HttpMethod.POST, "/api/v1/workflow/definitions/$conditionDef/activate", json(), token),
        "activate numeric condition definition",
      )
      val okInstance =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/workflow/instances",
            json(
              "definitionId" to conditionDef,
              "payload" to mapOf<String, Any?>("amount" to 50),
            ),
            token,
          ),
        )
      assertEquals("COMPLETED", okInstance["status"].asText())
    }

    @Test
    fun interopAndFhirCoverage() {
      val token = adminToken()
      val s = suffix()

      val conn =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/interop/connections",
            json(
              "name" to "Conn $s",
              "connectionType" to "HL7",
              "endpoint" to "localhost:2575",
              "username" to "u",
              "password" to "p",
            ),
            token,
          ),
        )
      val connId = conn["id"].asText()
      assert2xx(api(HttpMethod.GET, "/api/v1/interop/connections", token = token), "list connections")
      assert2xx(
        api(HttpMethod.PUT, "/api/v1/interop/connections/$connId", json("name" to "Conn $s v2", "enabled" to true), token),
        "update connection",
      )
      val delConn = api(HttpMethod.DELETE, "/api/v1/interop/connections/$connId", token = token)
      assertTrue(delConn.status in 200..299, "delete connection -> ${delConn.status}")

      val patient =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/patients",
            json(
              "firstName" to "Fhir",
              "lastName" to "Patient$s",
              "gender" to "female",
              "dateOfBirth" to "1990-01-01",
              "phone" to "77$s".take(11),
              "qid" to "8$s".take(11),
              "nationality" to "QA",
              "city" to "Doha",
            ),
            token,
          ),
        )["patient"]
      val patientId = patient["id"].asText()
      val patientMrn = patient["mrn"].asText()

      val enc =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/encounters",
            json(
              "patientId" to patientId,
              "reason" to "FHIR coverage",
              "chiefComplaint" to "fever",
            ),
            token,
          ),
        )["id"].asText()

      val vitalId =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/encounters/$enc/vitals",
            json(
              "bloodPressureSystolic" to 120,
              "bloodPressureDiastolic" to 80,
              "heartRate" to 80,
              "spo2Percent" to 98,
              "painScore" to 1,
              "patientMrn" to patientMrn,
            ),
            token,
          ),
        )["id"].asText()
      val allergyId =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/patients/$patientId/allergies",
            json(
              "allergen" to "Latex",
              "reaction" to "Urticaria",
              "severity" to "mild",
            ),
            token,
          ),
        )["id"].asText()
      val dxId =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/encounters/$enc/diagnoses",
            json(
              "code" to "J00",
              "name" to "Acute nasopharyngitis",
              "system" to "ICD-10",
              "patientMrn" to patientMrn,
            ),
            token,
          ),
        )["id"].asText()
      val rxId =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/encounters/$enc/prescriptions",
            json(
              "drugName" to "Paracetamol",
              "dose" to "500 mg",
              "frequency" to "TID",
              "quantity" to 6,
              "patientMrn" to patientMrn,
            ),
            token,
          ),
        )["id"].asText()
      val orderId =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/encounters/$enc/orders",
            json(
              "orderType" to "LAB",
              "orderName" to "CBC",
              "priority" to "routine",
              "patientMrn" to patientMrn,
            ),
            token,
          ),
        )["id"].asText()

      // 01.12 dual review: the FHIR Encounter search bundle is tenant-scoped — a foreign
      // hospital's encounter never leaks into this hospital's bundle.
      val hidForeign =
        `data`(
          api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "Fhir F $s", "code" to "FHF-$s", "country" to "Qatar"), token),
        )["id"].asText()
      val docForeignEmail = "fhirf-$s@test.local"
      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/auth/register",
          json(
            "email" to docForeignEmail,
            "password" to "Passw0rd!123",
            "firstName" to "FhirF",
            "lastName" to "Doc$s",
            "roles" to listOf("DOCTOR"),
            "hospitalId" to hidForeign,
          ),
        ),
        "register foreign doctor",
      )
      val docForeign =
        `data`(api(HttpMethod.POST, "/api/v1/auth/login", json("username" to docForeignEmail, "password" to "Passw0rd!123")))["accessToken"].asText()
      val patientForeign =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/patients",
            json(
              "firstName" to "FhirF",
              "lastName" to "Patient$s",
              "gender" to "female",
              "dateOfBirth" to "1990-01-01",
              "hospitalId" to hidForeign,
            ),
            token,
          ),
        )["patient"]
      val encForeign =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/encounters",
            json("patientId" to patientForeign["id"].asText(), "reason" to "foreign tenant", "chiefComplaint" to "fever"),
            docForeign,
          ),
        )["id"].asText()
      // The admin token is unscoped by design (global admin -> null principal hospital), so the
      // tenant filter is asserted with a hospital-scoped doctor: own hospital, own encounter.
      val hidOwn =
        `data`(
          api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "Fhir O $s", "code" to "FHO-$s", "country" to "Qatar"), token),
        )["id"].asText()
      val docOwnEmail = "fhiro-$s@test.local"
      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/auth/register",
          json(
            "email" to docOwnEmail,
            "password" to "Passw0rd!123",
            "firstName" to "FhirO",
            "lastName" to "Doc$s",
            "roles" to listOf("DOCTOR"),
            "hospitalId" to hidOwn,
          ),
        ),
        "register own-scoped doctor",
      )
      val docOwn =
        `data`(api(HttpMethod.POST, "/api/v1/auth/login", json("username" to docOwnEmail, "password" to "Passw0rd!123")))["accessToken"].asText()
      val patientOwn =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/patients",
            json(
              "firstName" to "FhirO",
              "lastName" to "Patient$s",
              "gender" to "female",
              "dateOfBirth" to "1990-01-01",
              "hospitalId" to hidOwn,
            ),
            docOwn,
          ),
        )["patient"]
      val encOwn =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/encounters",
            json("patientId" to patientOwn["id"].asText(), "reason" to "own tenant", "chiefComplaint" to "fever"),
            docOwn,
          ),
        )["id"].asText()
      val encounterBundle = api(HttpMethod.GET, "/api/v1/fhir/Encounter", token = docOwn)
      assert2xx(encounterBundle, "fhir encounter bundle (scoped doctor)")
      assertFalse(encounterBundle.body.contains(encForeign), "fhir Encounter bundle hides foreign-hospital encounters")
      assertTrue(encounterBundle.body.contains(encOwn), "fhir Encounter bundle keeps own-hospital encounters")

      listOf(
        "Patient",
        "Practitioner",
        "Organization",
        "Encounter",
        "Condition",
        "Observation",
        "MedicationRequest",
        "ServiceRequest",
        "AllergyIntolerance",
        "DiagnosticReport",
      ).forEach { res ->
        assert2xx(api(HttpMethod.GET, "/api/v1/fhir/$res", token = token), "fhir list $res")
      }
      assert2xx(api(HttpMethod.GET, "/api/v1/fhir/Patient?name=Fhir", token = token), "fhir patient search by name")
      assert2xx(api(HttpMethod.GET, "/api/v1/fhir/Patient/$patientId", token = token), "fhir patient by id")
      assert2xx(api(HttpMethod.GET, "/api/v1/fhir/Encounter/$enc", token = token), "fhir encounter by id")
      assert2xx(api(HttpMethod.GET, "/api/v1/fhir/Observation/$vitalId", token = token), "fhir observation by id")
      assert2xx(api(HttpMethod.GET, "/api/v1/fhir/AllergyIntolerance/$allergyId", token = token), "fhir allergy by id")
      assert2xx(api(HttpMethod.GET, "/api/v1/fhir/Condition/$dxId", token = token), "fhir condition by id")
      assert2xx(api(HttpMethod.GET, "/api/v1/fhir/MedicationRequest/$rxId", token = token), "fhir medication request by id")
      assert2xx(api(HttpMethod.GET, "/api/v1/fhir/ServiceRequest/$orderId", token = token), "fhir service request by id")
      assert2xx(api(HttpMethod.GET, "/api/v1/hl7/health", token = token), "hl7 health")

      val adt =
        "MSH|^~\\&|E2E|HOSP|SQUAMED|MIR|20260906120000||ADT^A04^ADT_A04|CTRL-$s|P|2.5" +
          "\rEVN|A04|20260906120000\rPID|1||E2E-MRN-$s||Fhir^Coverage||19800101|M" +
          "\rOBX|1|NM|LOINC^WBC||6.5|10^9/L|4-10|||F"
      val parse = `data`(api(HttpMethod.POST, "/api/v1/hl7/parse", json("raw" to adt), token))
      assertEquals("ADT", parse["messageType"].asText())
      val broken = api(HttpMethod.POST, "/api/v1/hl7/parse", json("raw" to "NOT A REAL HL7 MESSAGE"), token)
      assertTrue(broken.status in 400..499, "invalid hl7 -> 4xx (${broken.status})")
    }

    @Test
    fun empiAndMergeCoverage() {
      val token = adminToken()
      val s = suffix()

      // Fresh hospital per run: the dev database persists rows across runs.
      val hospitalId =
        `data`(
          api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "EMPI-A $s", "code" to "PE-$s", "country" to "Qatar"), token),
        )["id"].asText()
      // Second person for the two-person rule: an auditor (READ_ONLY_VIEWER holds patient:merge:cosign).
      val cosignerEmail = "auditor-$s@test.local"
      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/auth/register",
          json(
            "email" to cosignerEmail,
            "password" to "Passw0rd!123",
            "firstName" to "Aud",
            "lastName" to "Itor$s",
            "roles" to listOf("READ_ONLY_VIEWER"),
            "hospitalId" to hospitalId,
          ),
        ),
        "register auditor cosigner",
      )
      val cosigner =
        `data`(
          api(HttpMethod.POST, "/api/v1/auth/login", json("username" to cosignerEmail, "password" to "Passw0rd!123", "hospitalId" to hospitalId), token),
        )["accessToken"].asText()

      val qidA = "10000000$s".take(11)
      val phoneA = "59$s".take(11)

      val p1Resp =
        api(
          HttpMethod.POST,
          "/api/v1/patients",
          json(
            "firstName" to "Empi",
            "lastName" to "Match$s",
            "gender" to "male",
            "dateOfBirth" to "1988-08-08",
            "phone" to phoneA,
            "qid" to qidA,
            "nationality" to "QA",
            "city" to "Doha",
            "hospitalId" to hospitalId,
          ),
          token,
        )
      assert2xx(p1Resp, "create empi patient p1")
      val p1 = `data`(p1Resp)["patient"]

      val passportPatient =
        api(
          HttpMethod.POST,
          "/api/v1/patients",
          json(
            "firstName" to "Pass",
            "lastName" to "Holder$s",
            "gender" to "female",
            "dateOfBirth" to "1992-02-02",
            "phone" to "58$s".take(11),
            "passportNumber" to "PASS-$s",
            "nationality" to "GB",
            "city" to "Doha",
          ),
          token,
        )
      assert2xx(passportPatient, "create passport patient")
      val byPassport =
        api(
          HttpMethod.POST,
          "/api/v1/patients/match",
          json(
            "firstName" to "Pass",
            "lastName" to "Holder$s",
            "gender" to "female",
            "dateOfBirth" to "1992-02-02",
            "passportNumber" to "PASS-$s",
            "nationality" to "GB",
            "city" to "Doha",
          ),
          token,
        )
      assert2xx(byPassport, "empi match by passport")
      assertTrue(
        `data`(byPassport).any {
          it["patientId"].asText() == `data`(passportPatient)["patient"]["id"].asText()
        },
        "passport match returned",
      )

      val qidPatient =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/patients",
            json(
              "firstName" to "Id",
              "lastName" to "Number$s",
              "gender" to "male",
              "dateOfBirth" to "1980-03-03",
              "phone" to "57$s".take(11),
              "idType" to "ID_NUMBER",
              "idNumber" to "NASA-ID-$s",
              "nationality" to "QA",
              "city" to "Doha",
            ),
            token,
          ),
        )["patient"]
      val byId =
        api(
          HttpMethod.POST,
          "/api/v1/patients/match",
          json(
            "firstName" to "Id",
            "lastName" to "Number$s",
            "gender" to "male",
            "dateOfBirth" to "1980-03-03",
            "idType" to "ID_NUMBER",
            "idNumber" to "NASA-ID-$s",
            "nationality" to "QA",
            "city" to "Doha",
          ),
          token,
        )
      assert2xx(byId, "empi match by id number")
      assertTrue(`data`(byId).any { it["patientId"].asText() == qidPatient["id"].asText() }, "id number match returned")
      val p1Id = p1["id"].asText()

      val byQid =
        api(
          HttpMethod.POST,
          "/api/v1/patients/match",
          json(
            "firstName" to "Empi",
            "lastName" to "Match$s",
            "gender" to "male",
            "dateOfBirth" to "1988-08-08",
            "phone" to phoneA,
            "qid" to qidA,
            "nationality" to "QA",
            "city" to "Doha",
            "hospitalId" to hospitalId,
          ),
          token,
        )
      assert2xx(byQid, "empi match by qid")
      assertTrue(`data`(byQid).any { it["patientId"].asText() == p1Id }, "qid exact match surface")

      val byPhone =
        api(
          HttpMethod.POST,
          "/api/v1/patients/match",
          json(
            "firstName" to "Empi",
            "lastName" to "Match$s",
            "gender" to "male",
            "dateOfBirth" to "1988-08-08",
            "phone" to phoneA,
            "qid" to "DIFFERENT-$s",
            "nationality" to "QA",
            "city" to "Doha",
            "hospitalId" to hospitalId,
          ),
          token,
        )
      assert2xx(byPhone, "empi match by phone")
      assertTrue(`data`(byPhone).any { it["patientId"].asText() == p1Id }, "phone partial match surface")

      val dupResp =
        api(
          HttpMethod.POST,
          "/api/v1/patients",
          json(
            "firstName" to "Empi",
            "lastName" to "Match$s",
            "gender" to "male",
            "dateOfBirth" to "1988-08-08",
            "phone" to phoneA,
            "qid" to qidA,
            "nationality" to "QA",
            "city" to "Doha",
            "hospitalId" to hospitalId,
          ),
          token,
        )
      assert2xx(dupResp, "create duplicate")
      val dup = `data`(dupResp)
      assertTrue(dup["matches"].size() >= 1, "duplicate create returns match candidates: ${dupResp.body.take(300)}")
      assertTrue(dup["matches"][0]["score"].asInt() >= 96, "identical QID scores deterministic: ${dup["matches"][0]}")
      val p2Id = dup["patient"]["id"].asText()

      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/patients/$p2Id/allergies",
          json(
            "allergen" to "Ibuprofen",
            "reaction" to "Dizziness",
            "severity" to "moderate",
          ),
          token,
        ),
        "allergy on duplicate to move",
      )

      // The identical-QID duplicate was auto-enqueued as a PENDING review (two-person rule).
      val queueJson = api(HttpMethod.GET, "/api/v1/patients/empi/reviews?hospitalId=$hospitalId&status=PENDING", token = token)
      assert2xx(queueJson, "list pending reviews")
      val review = `data`(queueJson).first { it["duplicatePatientId"].asText() == p2Id }
      val reviewId = review["id"].asText()
      assertEquals(100, review["score"].asInt(), "auto-enqueued review carries the matcher score")

      val mergeSelf = api(HttpMethod.POST, "/api/v1/patients/merge", json("targetId" to p1Id, "duplicateId" to p1Id), token)
      assertEquals(400, mergeSelf.status, "self merge rejected")
      assertEquals("CANNOT_MERGE_PATIENT_WITH_ITSELF", om.readTree(mergeSelf.body)["error"]["code"].asText())

      // Two-person rule: the submitter cannot approve, even while holding the cosign permission.
      val selfApproval = api(HttpMethod.POST, "/api/v1/patients/empi/reviews/$reviewId/approve", null, token)
      assertEquals(400, selfApproval.status, "two-person rule: submitter cannot approve")
      assertEquals("TWO_PERSON_RULE_SELF_APPROVAL", om.readTree(selfApproval.body)["error"]["code"].asText())

      // Approving without the cosign permission is denied.
      val doctorEmail = "doctor-$s@test.local"
      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/auth/register",
          json(
            "email" to doctorEmail,
            "password" to "Passw0rd!123",
            "firstName" to "Doc",
            "lastName" to "Tor$s",
            "roles" to listOf("DOCTOR"),
            "hospitalId" to hospitalId,
          ),
        ),
        "register doctor (no cosign)",
      )
      val doctorToken =
        `data`(
          api(HttpMethod.POST, "/api/v1/auth/login", json("username" to doctorEmail, "password" to "Passw0rd!123", "hospitalId" to hospitalId), token),
        )["accessToken"].asText()
      val noCosign = api(HttpMethod.POST, "/api/v1/patients/empi/reviews/$reviewId/approve", null, doctorToken)
      assertEquals(403, noCosign.status, "cosign permission required: ${noCosign.body.take(200)}")

      // Cosigner approves → merge executes (versioned log, archived duplicate).
      val approved = api(HttpMethod.POST, "/api/v1/patients/empi/reviews/$reviewId/approve", null, cosigner)
      assert2xx(approved, "cosigner approves review")
      val approvedReview = `data`(approved)
      assertEquals("APPROVED", approvedReview["status"].asText(), "review approved: $approvedReview")
      val mergeId = approvedReview["mergeId"].asText()
      assertTrue(mergeId.isNotBlank(), "approved review links the merge log")

      val survivorAllergies = `data`(api(HttpMethod.GET, "/api/v1/patients/$p1Id/allergies", token = token))
      assertTrue(
        survivorAllergies.any { it["allergen"].asText() == "Ibuprofen" },
        "allergy moved to survivor: $survivorAllergies",
      )
      val dupAfter = `data`(api(HttpMethod.GET, "/api/v1/patients/$p2Id", token = token))
      assertEquals("merged", dupAfter["recordsStatus"].asText(), "duplicate archived, not deleted: $dupAfter")
      val searchAfterMerge = `data`(api(HttpMethod.GET, "/api/v1/patients?query=$phoneA", token = token))
      assertTrue(
        searchAfterMerge.none { it["id"].asText() == p2Id },
        "archived duplicate hidden from search: $searchAfterMerge",
      )

      // Unmerge: children re-pointed back, duplicate restored, log UNMERGED.
      val unmerged = api(HttpMethod.POST, "/api/v1/patients/merges/$mergeId/unmerge", null, token)
      assert2xx(unmerged, "unmerge restores duplicate")
      assertEquals("UNMERGED", `data`(unmerged)["status"].asText(), "merge log flipped to UNMERGED")
      val dupRestored = `data`(api(HttpMethod.GET, "/api/v1/patients/$p2Id", token = token))
      assertEquals("active", dupRestored["recordsStatus"].asText(), "duplicate restored: $dupRestored")
      val allergiesBack = `data`(api(HttpMethod.GET, "/api/v1/patients/$p2Id/allergies", token = token))
      assertTrue(allergiesBack.any { it["allergen"].asText() == "Ibuprofen" }, "allergy re-pointed to duplicate: $allergiesBack")
      val unmergeAgain = api(HttpMethod.POST, "/api/v1/patients/merges/$mergeId/unmerge", null, token)
      assertEquals(400, unmergeAgain.status, "double unmerge rejected")
      assertEquals("MERGE_NOT_UNMERGEABLE", om.readTree(unmergeAgain.body)["error"]["code"].asText())

      // Re-merge the same pair: merge_version increments to 2.
      val intent2 = api(HttpMethod.POST, "/api/v1/patients/merge", json("targetId" to p1Id, "duplicateId" to p2Id), token)
      assert2xx(intent2, "re-submit merge intent")
      val review2Id = `data`(intent2)["id"].asText()
      assert2xx(api(HttpMethod.POST, "/api/v1/patients/empi/reviews/$review2Id/approve", null, cosigner), "cosigner approves re-merge")
      val logs = `data`(api(HttpMethod.GET, "/api/v1/patients/merges?hospitalId=$hospitalId", token = token))
      val versions = logs.map { it["mergeVersion"].asInt() }.sorted()
      assertEquals(listOf(1, 2), versions, "merge log versioned per pair: $logs")

      // Reject path: a manual intent the reviewer declines cannot be approved afterwards.
      val p3Resp =
        api(
          HttpMethod.POST,
          "/api/v1/patients",
          json(
            "firstName" to "Empi",
            "lastName" to "Match$s",
            "gender" to "male",
            "dateOfBirth" to "1975-01-01",
            "phone" to "59$s".take(11) + "x",
            "nationality" to "QA",
            "city" to "Doha",
            "hospitalId" to hospitalId,
          ),
          token,
        )
      assert2xx(p3Resp, "create p3")
      val p3Id = `data`(p3Resp)["patient"]["id"].asText()
      val intent3 = api(HttpMethod.POST, "/api/v1/patients/merge", json("targetId" to p1Id, "duplicateId" to p3Id), token)
      assert2xx(intent3, "submit intent for p3")
      val review3Id = `data`(intent3)["id"].asText()
      val rejected = api(HttpMethod.POST, "/api/v1/patients/empi/reviews/$review3Id/reject", null, token)
      assert2xx(rejected, "review rejected")
      assertEquals("REJECTED", `data`(rejected)["status"].asText())
      val approveRejected = api(HttpMethod.POST, "/api/v1/patients/empi/reviews/$review3Id/approve", null, cosigner)
      assertEquals(400, approveRejected.status, "cannot approve a rejected review")
      assertEquals("REVIEW_NOT_PENDING", om.readTree(approveRejected.body)["error"]["code"].asText())

      // Matcher tenant pin: probing the same QID from another hospital must not leak p1.
      val otherHospital =
        `data`(
          api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "EMPI-B $s", "code" to "PF-$s", "country" to "Qatar"), token),
        )["id"].asText()
      val crossProbe =
        api(
          HttpMethod.POST,
          "/api/v1/patients/match",
          json(
            "firstName" to "Empi",
            "lastName" to "Match$s",
            "gender" to "male",
            "dateOfBirth" to "1988-08-08",
            "phone" to phoneA,
            "qid" to qidA,
            "nationality" to "QA",
            "city" to "Doha",
            "hospitalId" to otherHospital,
          ),
          token,
        )
      assert2xx(crossProbe, "cross-hospital probe allowed")
      assertTrue(`data`(crossProbe).none { it["patientId"].asText() == p1Id }, "matcher pinned to probing hospital")
    }

    /** 00.08 synthetic eval: 210 generated cases (60 deterministic / 70 probabilistic / 70 false-merge probes). */
    @Test
    fun empiSyntheticEvalCoverage() {
      val token = adminToken()
      val s = suffix()
      val millis = System.currentTimeMillis()

      val hospitalId =
        `data`(
          api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "EMPI-EVAL $s", "code" to "EG-$s", "country" to "Qatar"), token),
        )["id"].asText()

      // 60 deterministic cases: unique QID per case → score 100, certain link for one-click approval.
      for (i in 0 until 60) {
        val qid = "9" + "%09d".format(millis % 1_000_000_000) + "%02d".format(i)
        val dob = LocalDate.of(1990, 1, 1).plusDays(i.toLong()).toString()
        assert2xx(
          api(
            HttpMethod.POST,
            "/api/v1/patients",
            json("firstName" to "Det%02d".format(i), "lastName" to "Case", "gender" to "male", "dateOfBirth" to dob, "qid" to qid, "hospitalId" to hospitalId),
            token,
          ),
          "deterministic base $i",
        )
        val dup =
          `data`(
            api(
              HttpMethod.POST,
              "/api/v1/patients",
              json(
                "firstName" to "Det%02d".format(i),
                "lastName" to "Case",
                "gender" to "male",
                "dateOfBirth" to dob,
                "qid" to qid,
                "hospitalId" to hospitalId,
              ),
              token,
            ),
          )
        val matches = dup["matches"]
        assertEquals(1, matches.size(), "deterministic case $i must surface exactly one candidate")
        val score = matches[0]["score"].asInt()
        assertTrue(score >= 96, "deterministic case $i score $score must be >= 96")
      }

      // 70 probabilistic cases: same name+DOB+gender → 85 in [60,96), routed to the queue.
      for (i in 0 until 70) {
        val dob = LocalDate.of(1992, 3, 1).plusDays(i.toLong()).toString()
        assert2xx(
          api(
            HttpMethod.POST,
            "/api/v1/patients",
            json("firstName" to "Prob%02d".format(i), "lastName" to "Same", "gender" to "female", "dateOfBirth" to dob, "hospitalId" to hospitalId),
            token,
          ),
          "probabilistic base $i",
        )
        val dup =
          `data`(
            api(
              HttpMethod.POST,
              "/api/v1/patients",
              json("firstName" to "Prob%02d".format(i), "lastName" to "Same", "gender" to "female", "dateOfBirth" to dob, "hospitalId" to hospitalId),
              token,
            ),
          )
        val matches = dup["matches"]
        assertEquals(1, matches.size(), "probabilistic case $i must surface one candidate")
        val score = matches[0]["score"].asInt()
        assertTrue(score in 60..95, "probabilistic case $i score $score must fall in [60,96)")
        assertTrue(
          matches[0]["matchedOn"].any { it.asText() == "NAME_DOB_GENDER" },
          "probabilistic case $i matched on name+dob+gender: $matches",
        )
      }

      // 70 false-merge probes: shared phone, different identity → 95 < 96; queued, never merged.
      for (i in 0 until 70) {
        val phone = "59" + "%09d".format(millis % 1_000_000_000) + "%02d".format(i)
        assert2xx(
          api(
            HttpMethod.POST,
            "/api/v1/patients",
            json(
              "firstName" to "Real%02d".format(i),
              "lastName" to "Person",
              "gender" to "male",
              "dateOfBirth" to LocalDate.of(1985, 5, 1).plusDays(i.toLong()).toString(),
              "phone" to phone,
              "hospitalId" to hospitalId,
            ),
            token,
          ),
          "probe base $i",
        )
        val dup =
          `data`(
            api(
              HttpMethod.POST,
              "/api/v1/patients",
              json(
                "firstName" to "Different%02d".format(i),
                "lastName" to "Name",
                "gender" to "female",
                "dateOfBirth" to LocalDate.of(2001, 7, 7).plusDays(i.toLong()).toString(),
                "phone" to phone,
                "hospitalId" to hospitalId,
              ),
              token,
            ),
          )
        val matches = dup["matches"]
        assertEquals(1, matches.size(), "probe case $i must surface one candidate")
        val score = matches[0]["score"].asInt()
        assertTrue(score < 96, "probe case $i score $score must stay below the auto-link threshold")
        assertTrue(matches[0]["matchedOn"].all { it.asText() == "PHONE" }, "probe case $i matched on phone only: $matches")
      }

      // Every case landed in the queue; nothing merged itself.
      val queue = `data`(api(HttpMethod.GET, "/api/v1/patients/empi/reviews?hospitalId=$hospitalId&status=PENDING", token = token))
      assertEquals(200, queue.size(), "all synthetic cases queued for review")
      val mergeLogs = `data`(api(HttpMethod.GET, "/api/v1/patients/merges?hospitalId=$hospitalId", token = token))
      assertEquals(0, mergeLogs.size(), "no synthetic case may auto-merge: $mergeLogs")

      // A deterministic row is approved by a distinct cosign-holder, then reversed via unmerge.
      val cosignerEmail = "auditor-$s@test.local"
      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/auth/register",
          json(
            "email" to cosignerEmail,
            "password" to "Passw0rd!123",
            "firstName" to "Eval",
            "lastName" to "Auditor",
            "roles" to listOf("READ_ONLY_VIEWER"),
            "hospitalId" to hospitalId,
          ),
        ),
        "register eval cosigner",
      )
      val cosigner =
        `data`(
          api(HttpMethod.POST, "/api/v1/auth/login", json("username" to cosignerEmail, "password" to "Passw0rd!123", "hospitalId" to hospitalId), token),
        )["accessToken"].asText()

      val detReview = queue.first { it["matchedOn"].any { m -> m.asText() == "QID" } }
      val approvedDet = api(HttpMethod.POST, "/api/v1/patients/empi/reviews/${detReview["id"].asText()}/approve", null, cosigner)
      assert2xx(approvedDet, "deterministic review approved by cosigner")
      val detMergeId = `data`(approvedDet)["mergeId"].asText()
      val unmergeDet = api(HttpMethod.POST, "/api/v1/patients/merges/$detMergeId/unmerge", null, token)
      assert2xx(unmergeDet, "approved merge reversed via unmerge")
      assertEquals("UNMERGED", `data`(unmergeDet)["status"].asText())

      // A false-merge probe is rejected — the queue absorbs the false positive.
      val probeReview = queue.first { it["matchedOn"].any { m -> m.asText() == "PHONE" } }
      val rejectedProbe = api(HttpMethod.POST, "/api/v1/patients/empi/reviews/${probeReview["id"].asText()}/reject", null, token)
      assert2xx(rejectedProbe, "probe review rejected")
      assertEquals("REJECTED", `data`(rejectedProbe)["status"].asText())

      val queueAfter = `data`(api(HttpMethod.GET, "/api/v1/patients/empi/reviews?hospitalId=$hospitalId&status=PENDING", token = token))
      assertEquals(198, queueAfter.size(), "one approved + one rejected leave 198 pending")
    }

    @Test
    fun workflowEngineBranchCoverage() {
      val token = adminToken()
      val s = suffix()
      val adminId = adminUserId()

      fun wfDef(
        code: String,
        steps: List<Map<String, Any?>>,
      ): String =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/workflow/definitions",
            json(
              "code" to code,
              "name" to code,
              "triggerType" to "MANUAL",
              "steps" to steps,
            ),
            token,
          ),
        )["id"].asText()

      val def1 =
        wfDef(
          "WF_MIX_$s",
          listOf<Map<String, Any?>>(
            mapOf(
              "stepKey" to "notify",
              "stepType" to "NOTIFICATION",
              "sequenceOrder" to 0,
              "config" to mapOf<String, Any?>("recipientId" to adminId, "title" to "wf notify $s"),
            ),
            mapOf(
              "stepKey" to "task1",
              "stepType" to "TASK",
              "sequenceOrder" to 1,
              "config" to
                mapOf<String, Any?>(
                  "title" to "Review $s",
                  "assigneeId" to adminId,
                  "priority" to "high",
                  "description" to "review me",
                ),
            ),
            mapOf(
              "stepKey" to "cond",
              "stepType" to "CONDITION",
              "sequenceOrder" to 2,
              "config" to mapOf<String, Any?>("expression" to "amount > 100", "ifFalse" to "skip"),
            ),
            mapOf(
              "stepKey" to "escl",
              "stepType" to "ESCALATION",
              "sequenceOrder" to 3,
              "config" to mapOf<String, Any?>("assigneeId" to adminId, "dueInMinutes" to 60, "title" to "Escalate $s"),
            ),
            mapOf(
              "stepKey" to "act",
              "stepType" to "ACTION",
              "sequenceOrder" to 4,
              "config" to mapOf<String, Any?>("type" to "archive"),
            ),
          ),
        )
      assert2xx(api(HttpMethod.POST, "/api/v1/workflow/definitions/$def1/activate", json(), token), "activate mixed definition")
      val inst1 =
        api(
          HttpMethod.POST,
          "/api/v1/workflow/instances",
          json(
            "definitionId" to def1,
            "payload" to mapOf("amount" to 250),
          ),
          token,
        )
      assert2xx(inst1, "trigger mixed workflow")
      assertEquals("COMPLETED", `data`(inst1)["status"].asText())
      assert2xx(api(HttpMethod.GET, "/api/v1/workflow/instances/${`data`(inst1)["id"].asText()}", token = token), "get instance")

      val def2 =
        wfDef(
          "WF_WAIT_$s",
          listOf<Map<String, Any?>>(
            mapOf("stepKey" to "hold", "stepType" to "WAIT", "sequenceOrder" to 0, "config" to mapOf<String, Any?>()),
            mapOf("stepKey" to "go", "stepType" to "ACTION", "sequenceOrder" to 1, "config" to mapOf<String, Any?>()),
          ),
        )
      assert2xx(api(HttpMethod.POST, "/api/v1/workflow/definitions/$def2/activate", json(), token), "activate wait definition")
      val inst2 =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/workflow/instances",
            json(
              "definitionId" to def2,
              "payload" to mapOf<String, Any?>(),
            ),
            token,
          ),
        )
      assertEquals("WAITING", inst2["status"].asText())
      val resume = api(HttpMethod.POST, "/api/v1/workflow/instances/${inst2["id"].asText()}/resume", json(), token)
      assert2xx(resume, "resume waiting workflow")
      assertEquals("COMPLETED", `data`(resume)["status"].asText())

      val def3 =
        wfDef(
          "WF_APPR_$s",
          listOf<Map<String, Any?>>(
            mapOf(
              "stepKey" to "appr",
              "stepType" to "APPROVAL",
              "sequenceOrder" to 0,
              "config" to mapOf<String, Any?>("approvers" to listOf(adminId)),
            ),
            mapOf("stepKey" to "fin", "stepType" to "ACTION", "sequenceOrder" to 1, "config" to mapOf<String, Any?>()),
          ),
        )
      assert2xx(api(HttpMethod.POST, "/api/v1/workflow/definitions/$def3/activate", json(), token), "activate approval definition")
      val instA =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/workflow/instances",
            json(
              "definitionId" to def3,
              "payload" to mapOf<String, Any?>(),
            ),
            token,
          ),
        )
      assertEquals("WAITING_APPROVAL", instA["status"].asText())
      val pending = `data`(api(HttpMethod.GET, "/api/v1/workflow/approvals/pending", token = token))
      val pid = pending.first { it["instanceId"].asText() == instA["id"].asText() }
      val approvalId = pid["approvalId"].asText()
      val reject =
        api(
          HttpMethod.POST,
          "/api/v1/workflow/instances/${instA["id"].asText()}/approvals/$approvalId/decide",
          json("decision" to "REJECTED", "comments" to "not needed"),
          token,
        )
      assert2xx(reject, "reject approval")
      assertEquals("TERMINATED", `data`(reject)["status"].asText())

      val instB =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/workflow/instances",
            json(
              "definitionId" to def3,
              "payload" to mapOf<String, Any?>(),
            ),
            token,
          ),
        )
      val pendingB =
        `data`(api(HttpMethod.GET, "/api/v1/workflow/approvals/pending", token = token))
          .first { it["instanceId"].asText() == instB["id"].asText() }
      val approve =
        api(
          HttpMethod.POST,
          "/api/v1/workflow/instances/${instB["id"].asText()}/approvals/${pendingB["approvalId"].asText()}/decide",
          json("decision" to "APPROVED"),
          token,
        )
      assert2xx(approve, "approve approval")
      assertEquals("COMPLETED", `data`(approve)["status"].asText())

      val instC =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/workflow/instances",
            json(
              "definitionId" to def1,
              "payload" to mapOf("amount" to 10),
            ),
            token,
          ),
        )
      assertEquals("COMPLETED", instC["status"].asText())

      val badDecision =
        api(
          HttpMethod.POST,
          "/api/v1/workflow/instances/${instB["id"].asText()}/approvals/${pendingB["approvalId"].asText()}/decide",
          json("decision" to "MAYBE"),
          token,
        )
      assertTrue(badDecision.status in 400..499, "invalid decision rejected")
      val draftDef =
        wfDef(
          "WF_DRAFT_$s",
          listOf<Map<String, Any?>>(
            mapOf("stepKey" to "a", "stepType" to "ACTION", "sequenceOrder" to 0, "config" to mapOf<String, Any?>()),
          ),
        )
      val triggerDraft =
        api(
          HttpMethod.POST,
          "/api/v1/workflow/instances",
          json(
            "definitionId" to draftDef,
            "payload" to mapOf<String, Any?>(),
          ),
          token,
        )
      assertTrue(triggerDraft.status in 400..499, "trigger draft rejected")
    }

    @Test
    fun hl7ReceiveAndAuthFailureCoverage() {
      val token = adminToken()
      val s = suffix()

      val raw =
        "MSH|^~\\&|E2E|HOSP|SQUAMED|MIR|20260906120000||ADT^A04^ADT_A04|RX-$s|P|2.5" +
          "\rEVN|A04|20260906120000\rPID|1||E2E-RX-$s||Receive^Hl7||19750505|F" +
          "\rNK1|1|Next^OfKin|MTH\r"
      val received = api(HttpMethod.POST, "/api/v1/hl7/messages", json("raw" to raw), token)
      assert2xx(received, "hl7 receive")
      assertEquals("AA", `data`(received)["ackCode"].asText())
      assert2xx(api(HttpMethod.GET, "/api/v1/hl7/messages", token = token), "hl7 message history")

      val wrongPw =
        api(
          HttpMethod.POST,
          "/api/v1/auth/login",
          json(
            "username" to "admin@squamed.local",
            "password" to "WrongPassword!",
          ),
        )
      assertEquals(401, wrongPw.status, "wrong password -> 401")
      val duplicate =
        api(
          HttpMethod.POST,
          "/api/v1/auth/register",
          json(
            "email" to "admin@squamed.local",
            "password" to "Passw0rd!123",
            "firstName" to "x",
            "lastName" to "y",
          ),
        )
      assertTrue(duplicate.status in 400..499, "duplicate email rejected (${duplicate.status})")
      val weakPw =
        api(
          HttpMethod.POST,
          "/api/v1/auth/register",
          json(
            "email" to "weak-$s@test.local",
            "password" to "123",
            "firstName" to "x",
            "lastName" to "y",
          ),
        )
      assertTrue(weakPw.status in 400..499, "weak password rejected (${weakPw.status})")
      val missingPw = api(HttpMethod.POST, "/api/v1/auth/login", json("username" to "admin@squamed.local"))
      assertEquals(400, missingPw.status, "missing password -> 400")
    }

    @Test
    fun analyzerIntakeZeroSilentDropCoverage() {
      val token = adminToken()
      val s = suffix()

      val countsBefore = `data`(api(HttpMethod.GET, "/api/v1/analyzer/counts", token = token))
      val acceptedBefore = countsBefore["accepted"].asLong()
      val quarantinedBefore = countsBefore["quarantined"].asLong()

      fun astmFrame(body: String): String {
        val frame = 0x02.toChar() + body + 0x03.toChar()
        return frame + (frame.sumOf { it.code } and 0xFF).toString(16).uppercase().padStart(2, '0')
      }

      // The contract sim (chunk-2 TESTS): 1,000 analyzer messages incl. malformed -
      // zero silent drops, every malformed message quarantined + alerted.
      val validHl7 = 790
      val malformedHl7 = 110
      val validAstm = 90
      val malformedAstm = 10
      for (i in 1..validHl7) {
        val raw = "MSH|^~\\&|SIM|HOSP|SQUAMED|MIR|20260906120000||ORU^R01^ORU_R01|SIM-$s-$i|P|2.5\rOBX|1|NM|LOINC^WBC||5.0|10^9/L|4-10|||F"
        val resp = api(HttpMethod.POST, "/api/v1/analyzer/messages", json("raw" to raw, "transport" to "HL7V2", "source" to "sim:cbc"), token)
        assert2xx(resp, "analyzer intake hl7 valid $i")
        val d = `data`(resp)
        assertEquals("ACCEPTED", d["status"].asText(), "hl7 valid $i accepted")
        assertEquals("AA", d["ackCode"].asText(), "hl7 valid $i ack")
      }
      for (i in 1..malformedHl7) {
        val resp =
          api(HttpMethod.POST, "/api/v1/analyzer/messages", json("raw" to "GARBAGE-NOT-HL7-$s-$i", "transport" to "HL7V2", "source" to "sim:cbc"), token)
        assert2xx(resp, "analyzer intake hl7 malformed $i must still answer 2xx")
        val d = `data`(resp)
        assertEquals("QUARANTINED", d["status"].asText(), "hl7 malformed $i quarantined")
        assertEquals("CE", d["ackCode"].asText(), "hl7 malformed $i ack CE")
        assertTrue(d["reason"].asText().isNotBlank(), "hl7 malformed $i carries a reason")
      }
      for (i in 1..validAstm) {
        val raw =
          astmFrame("H|\\^&|||Sim^1.0||CBC-$s|P|$i||20261119090000\r\nP|1||MRN-$s-$i^1||Doe^Jane||19800101|F\r\nR|1|^^^K|4.9|mmol/L|3.5-5.1|N||F\r\nL|1|N")
        val resp = api(HttpMethod.POST, "/api/v1/analyzer/messages", json("raw" to raw, "transport" to "ASTM", "source" to "sim:chem"), token)
        assert2xx(resp, "analyzer intake astm valid $i")
        assertEquals("ACCEPTED", `data`(resp)["status"].asText(), "astm valid $i accepted")
      }
      for (i in 1..malformedAstm) {
        val resp =
          api(
            HttpMethod.POST,
            "/api/v1/analyzer/messages",
            json(
              "raw" to "P|1||no-header-$s-$i\r\nR|1|x",
              "transport" to "ASTM",
              "source" to "sim:chem",
            ),
            token,
          )
        assert2xx(resp, "analyzer intake astm malformed $i must still answer 2xx")
        assertEquals("QUARANTINED", `data`(resp)["status"].asText(), "astm malformed $i quarantined")
      }

      val countsAfter = `data`(api(HttpMethod.GET, "/api/v1/analyzer/counts", token = token))
      assertEquals(acceptedBefore + validHl7 + validAstm, countsAfter["accepted"].asLong(), "accepted delta")
      assertEquals(quarantinedBefore + malformedHl7 + malformedAstm, countsAfter["quarantined"].asLong(), "quarantined delta - zero silent drops")

      val quarantined =
        `data`(api(HttpMethod.GET, "/api/v1/analyzer/messages?status=QUARANTINED&limit=200", token = token))
      assertTrue(quarantined.isArray && quarantined.size() >= malformedHl7 + malformedAstm, "quarantine lane populated")
      assertTrue(
        quarantined.all { it["reason"].asText().isNotBlank() },
        "every quarantined row carries a reason",
      )

      // every quarantined payload surfaced on the integration error queue (00.09 DLQ + alert)
      val dlq = `data`(api(HttpMethod.GET, "/api/v1/admin/events/errors?integration=analyzer&limit=200", token = token))
      assertTrue(dlq.isArray && dlq.size() >= malformedHl7 + malformedAstm, "quarantined payloads alerted on the DLQ")
      assertTrue(dlq.all { it["error"].asText().isNotBlank() }, "DLQ rows carry the quarantine reason")

      // replay: a quarantined row re-runs (still quarantined for malformed), accepted rows are not replayable
      val qRow = quarantined.first()
      val replayResp = api(HttpMethod.POST, "/api/v1/analyzer/messages/${qRow["id"].asText()}:replay", json(), token)
      assert2xx(replayResp, "replay quarantined row")
      assertEquals("QUARANTINED", `data`(replayResp)["status"].asText(), "malformed row stays quarantined on replay")
      val accRow =
        `data`(api(HttpMethod.GET, "/api/v1/analyzer/messages?status=ACCEPTED&limit=1", token = token)).first()
      val replayAccepted = api(HttpMethod.POST, "/api/v1/analyzer/messages/${accRow["id"].asText()}:replay", json(), token)
      assertEquals(400, replayAccepted.status, "accepted rows are not replayable")
    }

    /** 02.02 lab workflow core: barcode/accession, custody, delta check, instrument entry, role-gated validation, recollect. */
    @Test
    fun labWorkflowCoreCoverage() {
      val token = adminToken()
      val s = suffix()
      val hospitalId = ensureSeed(token)

      val patientId =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/patients",
            json(
              "firstName" to "Lab",
              "lastName" to "Workflow$s",
              "gender" to "female",
              "dateOfBirth" to "1990-01-01",
              "phone" to "5512$s".take(12),
              "qid" to "LW$s".take(11),
              "nationality" to "India",
              "city" to "Pune",
            ),
            token,
          ),
        )["patient"]["id"].asText()

      fun labUser(
        role: String,
        email: String,
      ): String {
        assert2xx(
          api(
            HttpMethod.POST,
            "/api/v1/auth/register",
            json(
              "email" to email,
              "password" to "Passw0rd!123",
              "firstName" to role,
              "lastName" to "User$s",
              "roles" to listOf(role),
              "hospitalId" to hospitalId,
            ),
          ),
          "register $role $email",
        )
        val login = api(HttpMethod.POST, "/api/v1/auth/login", json("username" to email, "password" to "Passw0rd!123"))
        assert2xx(login, "login $role")
        return `data`(login)["accessToken"].asText()
      }

      val tech = labUser("LAB_TECHNICIAN", "labtech-$s@test.local")
      val pathologist = labUser("PATHOLOGIST", "path-$s@test.local")
      val testCode = "K-$s"

      fun createLabOrder(priority: String = "routine"): Pair<String, String> {
        val created =
          api(
            HttpMethod.POST,
            "/api/v1/lab/orders",
            json(
              "patientId" to patientId,
              "tests" to listOf(mapOf("testCode" to testCode, "testName" to "Potassium $s")),
              "priority" to priority,
            ),
            token,
          )
        assert2xx(created, "create lab order")
        val order = `data`(created)["order"]
        return order["id"].asText() to order["orderNumber"].asText()
      }

      fun runToProcessing(orderId: String): String {
        val techDetail = `data`(api(HttpMethod.GET, "/api/v1/lab/orders/$orderId", token = tech))
        val testRequestId = techDetail["tests"][0]["id"].asText()
        assert2xx(api(HttpMethod.POST, "/api/v1/lab/orders/$orderId/collect", json(), token = tech), "collect $orderId")
        assert2xx(api(HttpMethod.POST, "/api/v1/lab/orders/$orderId/receive", json(), token), "receive $orderId")
        assert2xx(api(HttpMethod.POST, "/api/v1/lab/orders/$orderId/start", json(), token), "start $orderId")
        return testRequestId
      }

      fun enterResult(
        orderId: String,
        testRequestId: String,
        value: String,
        via: String = "MANUAL",
      ) {
        assert2xx(
          api(
            HttpMethod.POST,
            "/api/v1/lab/orders/$orderId/results",
            json(
              "enteredVia" to via,
              "results" to
                listOf(
                  mapOf(
                    "testRequestId" to testRequestId,
                    "resultValue" to value,
                    "unit" to "mmol/L",
                    "referenceRange" to "3.5-5.1",
                  ),
                ),
            ),
            token = tech,
          ),
          "enter results $orderId",
        )
      }

      // worklist shows uncollected orders, priority-ranked (seed hospital is shared: assert relative order)
      val (_, emergencyNo) = createLabOrder("emergency")
      val (_, urgentNo) = createLabOrder("urgent")
      val (_, routineNo) = createLabOrder("routine")
      val worklist = `data`(api(HttpMethod.GET, "/api/v1/lab/phlebotomy/worklist", token = tech))
      assertTrue(worklist.isArray && worklist.size() >= 3, "phlebotomy worklist lists uncollected orders")
      val numbers = worklist.map { it["orderNumber"].asText() }
      val ranks = listOf(emergencyNo, urgentNo, routineNo).map { numbers.indexOf(it) }
      assertTrue(ranks.all { it >= 0 }, "created orders present in worklist: $numbers")
      assertTrue(ranks[0] < ranks[1] && ranks[1] < ranks[2], "worklist is priority-ranked: $numbers")

      // order 1: full lifecycle with instrument-entered result
      val (order1, orderNo1) = createLabOrder()
      assertEquals("LO-", orderNo1.take(3), "order number format")
      val req1 = runToProcessing(order1)
      enterResult(order1, req1, "5.2", via = "INSTRUMENT")

      val collected = `data`(api(HttpMethod.GET, "/api/v1/lab/orders/$order1", token = tech))
      assertEquals(orderNo1, collected["order"]["barcodeId"].asText(), "barcode stamped at collect")
      assertEquals("ACC-$orderNo1", collected["order"]["accessionId"].asText(), "accession stamped at receive")
      assertEquals("INSTRUMENT", collected["tests"][0]["enteredVia"].asText(), "instrument result entry")

      // role separation: tech cannot validate, pathologist can
      assertEquals(403, api(HttpMethod.POST, "/api/v1/lab/orders/$order1/verify", json(), token = tech).status, "tech verify -> 403")
      val verified = api(HttpMethod.POST, "/api/v1/lab/orders/$order1/verify", json(), token = pathologist)
      assert2xx(verified, "pathologist verify")
      assertEquals("verified", `data`(verified)["order"]["resultStatus"].asText())
      assert2xx(api(HttpMethod.POST, "/api/v1/lab/orders/$order1/release", json(), token = pathologist), "pathologist release")

      val custodyEvents =
        `data`(api(HttpMethod.GET, "/api/v1/lab/orders/$order1", token = tech))["custody"].map { it["event"].asText() }
      assertEquals(
        listOf("COLLECTED", "RECEIVED", "PROCESS_STARTED", "RESULTS_ENTERED", "VERIFIED", "RELEASED"),
        custodyEvents,
        "chain of custody records every status change",
      )

      // order 2: delta check against the prior K result
      val (order2, _) = createLabOrder()
      val req2 = runToProcessing(order2)
      enterResult(order2, req2, "7.8")
      val deltaRow = `data`(api(HttpMethod.GET, "/api/v1/lab/orders/$order2", token = tech))["tests"][0]
      assertEquals("5.2", deltaRow["priorValue"].asText(), "delta check captures prior value")
      assertTrue(deltaRow["deltaPercent"].asDouble() > 0.0, "delta percent computed")

      // reject-and-recollect: successor order re-runs the same tests, original stays rejected
      val (order3, orderNo3) = createLabOrder()
      val recollected =
        api(HttpMethod.POST, "/api/v1/lab/orders/$order3/reject", json("reason" to "hemolyzed sample", "recollect" to true), token)
      assert2xx(recollected, "reject + recollect")
      val successor = `data`(recollected)
      assertEquals("ordered", successor["order"]["status"].asText(), "recollect creates a fresh successor")
      assertEquals(order3, successor["order"]["recollectOf"].asText(), "successor links back to the rejected order")
      assertEquals(1, successor["tests"].size(), "successor carries the same tests")
      assertTrue(successor["order"]["orderNumber"].asText() != orderNo3, "successor gets a new order number")
      val rejected = `data`(api(HttpMethod.GET, "/api/v1/lab/orders/$order3", token = tech))
      assertEquals("rejected", rejected["order"]["status"].asText(), "original stays rejected")
    }

    @Test
    fun catalogValidationBranchesCoverage() {
      val token = adminToken()
      val s = suffix()

      fun entity(
        base: String,
        dto: Map<String, Any?>,
      ): String =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/master-data/$base",
            json(
              *dto.entries
                .map {
                  it.key to it.value as Any?
                }.toTypedArray(),
            ),
            token,
          ),
        )["id"].asText()

      val drugId = entity("drugs", mapOf("code" to "VC-$s", "name" to "Valid Drug", "category" to "General"))
      val labId = entity("lab-tests", mapOf("code" to "VCL-$s", "name" to "Valid Lab", "category" to "Chemistry"))
      val imgId = entity("imaging-procedures", mapOf("code" to "VCI-$s", "name" to "Valid Imaging", "modality" to "CT"))
      val procId = entity("procedure-codes", mapOf("code" to "VCP-$s", "name" to "Valid Proc", "system" to "ICD-9"))
      val dxId = entity("diagnosis-codes", mapOf("code" to "VCD-$s", "name" to "Valid Dx", "system" to "ICD-10"))
      val ciId = entity("charge-items", mapOf("code" to "VCC-$s", "name" to "Valid Charge", "category" to "lab"))

      listOf(
        "drugs" to drugId,
        "lab-tests" to labId,
        "imaging-procedures" to imgId,
        "procedure-codes" to procId,
        "diagnosis-codes" to dxId,
        "charge-items" to ciId,
      ).forEach { (base, id) ->
        val missingActive = api(HttpMethod.PATCH, "/api/v1/master-data/$base/$id/active", json(), token)
        assertEquals(400, missingActive.status, "$base active-flag required -> ${missingActive.status}")
      }

      assert4xx(api(HttpMethod.POST, "/api/v1/master-data/drugs", json(), token), "drug create without fields")
      assert4xx(api(HttpMethod.POST, "/api/v1/master-data/lab-tests", json(), token), "lab create without fields")
      assert4xx(api(HttpMethod.POST, "/api/v1/master-data/imaging-procedures", json(), token), "imaging create without fields")
      assert4xx(api(HttpMethod.POST, "/api/v1/master-data/procedure-codes", json(), token), "procedure create without fields")
      assert4xx(api(HttpMethod.POST, "/api/v1/master-data/diagnosis-codes", json(), token), "diagnosis create without fields")
      assert4xx(api(HttpMethod.POST, "/api/v1/master-data/charge-items", json(), token), "charge create without fields")
    }

    @Test
    fun hospitalConfigValidationBranchesCoverage() {
      val token = adminToken()
      val s = suffix()
      val hospitalId = ensureSeed(token)

      val specId =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/hospital-config/hospitals/$hospitalId/specialties",
            json(
              "name" to "IT Specialty $s",
              "code" to "SP-$s",
            ),
            token,
          ),
        )["id"].asText()
      val wardId =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/hospital-config/hospitals/$hospitalId/wards",
            json(
              "name" to "IT Ward $s",
              "code" to "WD-$s",
              "wardType" to "general",
            ),
            token,
          ),
        )["id"].asText()
      val bedId =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/hospital-config/hospitals/$hospitalId/beds",
            json(
              "label" to "B-$s",
              "type" to "standard",
            ),
            token,
          ),
        )["id"].asText()
      val svcId =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/hospital-config/hospitals/$hospitalId/services",
            json(
              "name" to "IT Service $s",
              "code" to "SV-$s",
            ),
            token,
          ),
        )["id"].asText()

      assertTrue(
        specId.isNotBlank() && wardId.isNotBlank() && bedId.isNotBlank() && svcId.isNotBlank(),
        "hospital config entities created",
      )

      assert4xx(api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json(), token), "hospital create without fields")
      assert4xx(
        api(HttpMethod.POST, "/api/v1/hospital-config/hospitals/$hospitalId/specialties", json(), token),
        "specialty create without fields",
      )
      assert4xx(
        api(HttpMethod.POST, "/api/v1/hospital-config/hospitals/$hospitalId/beds", json(), token),
        "bed create without fields",
      )

      assert4xx(
        api(
          HttpMethod.POST,
          "/api/v1/workflow/definitions",
          json(
            "code" to "",
            "name" to "No Code",
            "steps" to emptyList<Any>(),
          ),
          token,
        ),
        "workflow blank code rejected",
      )
      assert4xx(
        api(
          HttpMethod.POST,
          "/api/v1/workflow/definitions",
          json(
            "code" to "WFE_NM_$s",
            "name" to "",
            "steps" to emptyList<Any>(),
          ),
          token,
        ),
        "workflow blank name rejected",
      )

      val doubleActivate =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/workflow/definitions",
            json(
              "code" to "WFE_2A_$s",
              "name" to "Twice",
              "steps" to
                listOf<Map<String, Any?>>(
                  mapOf("stepKey" to "a", "stepType" to "ACTION", "sequenceOrder" to 0, "config" to mapOf<String, Any?>("type" to "x")),
                ),
            ),
            token,
          ),
        )["id"].asText()
      assert2xx(api(HttpMethod.POST, "/api/v1/workflow/definitions/$doubleActivate/activate", json(), token), "activate once")
      assert2xx(
        api(HttpMethod.POST, "/api/v1/workflow/definitions/$doubleActivate/activate", json(), token),
        "activate twice idempotent",
      )

      val adminId = adminUserId()
      val fhirDoc = api(HttpMethod.GET, "/api/v1/fhir/Practitioner/$adminId", token = token)
      assert2xx(fhirDoc, "fhir practitioner by id")
      assertTrue(`data`(fhirDoc)["resourceType"].asText() == "Practitioner", "practitioner resource shape")
      assert4xx(
        api(HttpMethod.PUT, "/api/v1/hospital-config/hospitals/${UUID.randomUUID()}", json(), token),
        "hospital update missing 4xx",
      )
      assert4xx(
        api(HttpMethod.POST, "/api/v1/workflow/definitions", json("code" to " ", "name" to "x", "steps" to emptyList<Any>()), token),
        "workflow whitespace code rejected",
      )
    }

    @Test
    fun configDocumentLifecycleCoverage() {
      val token = adminToken()
      val s = suffix()
      // Fresh hospital per run: version counters and history asserts must be deterministic.
      val hospitalId =
        `data`(
          api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "Cfg Lifecycle $s", "code" to "CL-$s", "country" to "Qatar"), token),
        )["id"].asText()
      val base = "/api/v1/hospital-config/hospitals/$hospitalId/config"

      // Unknown type rejected before validation (00.05).
      assert4xx(api(HttpMethod.PUT, "$base/nonexistent", json("x" to 1), token), "unknown config type")

      // Content violating the per-type JSON Schema is rejected with violation detail.
      val invalid =
        api(
          HttpMethod.PUT,
          "$base/working-hours",
          json(
            "timezone" to "Asia/Kolkata",
            "days" to
              listOf(
                mapOf("day" to "funday", "open" to "25:99", "close" to "08:00"),
              ),
          ),
          token,
        )
      assert4xx(invalid, "schema-invalid config")
      assertTrue(invalid.body.contains("CONFIG_VALIDATION_FAILED"), "violations must surface: ${invalid.body.take(300)}")

      // Valid publish -> v1 active.
      val v1 =
        api(
          HttpMethod.PUT,
          "$base/working-hours",
          json(
            "timezone" to "Asia/Kolkata",
            "days" to
              listOf(
                mapOf("day" to "mon", "open" to "08:00", "close" to "18:00"),
              ),
          ),
          token,
        )
      assert2xx(v1, "publish v1")
      assertEquals("1", `data`(v1)["version"].asText())
      assertEquals("active", `data`(v1)["status"].asText())

      // Second publish -> v2.
      val v2 =
        api(
          HttpMethod.PUT,
          "$base/working-hours",
          json(
            "timezone" to "Asia/Kolkata",
            "days" to
              listOf(
                mapOf("day" to "mon", "open" to "09:00", "close" to "17:00"),
              ),
          ),
          token,
        )
      assert2xx(v2, "publish v2")
      assertEquals("2", `data`(v2)["version"].asText())

      // GET latest returns v2 content.
      val latest = api(HttpMethod.GET, "$base/working-hours", token = token)
      assert2xx(latest, "latest config")
      assertEquals("2", `data`(latest)["version"].asText())
      assertEquals("09:00", `data`(latest)["content"]["days"][0]["open"].asText())

      // Version history: two rows, v1 superseded, v99 missing.
      val versions = api(HttpMethod.GET, "$base/working-hours/versions", token = token)
      assert2xx(versions, "version list")
      assertEquals(2, `data`(versions).size(), "two published versions")
      val atV1 = api(HttpMethod.GET, "$base/working-hours/versions/1", token = token)
      assert2xx(atV1, "config at v1")
      assertEquals("superseded", `data`(atV1)["status"].asText())
      assert4xx(api(HttpMethod.GET, "$base/working-hours/versions/99", token = token), "missing config version")

      // Second type: invalid date rejected, valid publish accepted.
      assert4xx(
        api(HttpMethod.PUT, "$base/holidays", json("year" to 2026, "holidays" to listOf(mapOf("date" to "2026-13-40", "name" to "X"))), token),
        "invalid holiday date",
      )
      assert2xx(
        api(HttpMethod.PUT, "$base/holidays", json("year" to 2026, "holidays" to listOf(mapOf("date" to "2026-01-26", "name" to "Republic Day"))), token),
        "valid holiday publish",
      )
    }

    @Test
    fun communicationReadComplianceCoverage() {
      val token = adminToken()
      val s = suffix()
      val adminId = adminUserId()

      val dm =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/communication/channels",
            json(
              "name" to "DM $s",
              "type" to "direct",
              "memberIds" to listOf(adminId),
            ),
            token,
          ),
        )
      val dmId = dm["id"].asText()
      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/communication/channels/$dmId/messages",
          json(
            "body" to "first message $s",
            "messageType" to "text",
          ),
          token,
        ),
        "send dm message",
      )
      val msgs = `data`(api(HttpMethod.GET, "/api/v1/communication/channels/$dmId/messages", token = token))
      assertTrue(msgs.size() >= 1, "messages listed")
      assert2xx(
        api(HttpMethod.POST, "/api/v1/communication/channels/$dmId/read", json("beforeId" to msgs[0]["id"].asText()), token),
        "mark read",
      )
      assert2xx(api(HttpMethod.GET, "/api/v1/communication/unread-count", token = token), "unread count")
    }

    @Test
    fun clinicalEncounterAndPharmacyCoverage() {
      val token = adminToken()
      val s = suffix()
      val adminId = adminUserId()
      val hospitalId = ensureSeed(token)

      val patient =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/patients",
            json(
              "firstName" to "Clin",
              "lastName" to "Patient$s",
              "gender" to "male",
              "dateOfBirth" to "1985-06-06",
              "phone" to "66$s".take(11),
              "qid" to "9$s".take(11),
              "nationality" to "QA",
              "city" to "Doha",
            ),
            token,
          ),
        )["patient"]
      val patientId = patient["id"].asText()
      val patientMrn = patient["mrn"].asText()
      val patientDetailUrl = "/api/v1/patients/$patientId"

      assert2xx(api(HttpMethod.GET, patientDetailUrl, token = token), "get patient detail")
      assert2xx(
        api(
          HttpMethod.PUT,
          patientDetailUrl,
          json(
            "firstName" to "Clin",
            "lastName" to "Patient$s",
            "phone" to "66$s".take(11),
            "city" to "Al Rayyan",
          ),
          token,
        ),
        "update patient",
      )

      assert2xx(
        api(
          HttpMethod.POST,
          "$patientDetailUrl/notes",
          json(
            "title" to "Note $s",
            "body" to "flagged note",
            "isFlagged" to true,
          ),
          token,
        ),
        "add patient note",
      )
      assert2xx(api(HttpMethod.GET, "$patientDetailUrl/notes", token = token), "list patient notes")
      assert2xx(
        api(
          HttpMethod.POST,
          "$patientDetailUrl/history",
          json(
            "conditionName" to "Hypertension",
            "diagnosisCode" to "I10",
            "notes" to "chronic",
          ),
          token,
        ),
        "add medical history",
      )
      assert2xx(api(HttpMethod.GET, "$patientDetailUrl/history", token = token), "list medical history")
      assert2xx(api(HttpMethod.GET, "$patientDetailUrl/allergies", token = token), "list allergies")

      val drug =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/master-data/drugs",
            json(
              "code" to "CLDR-$s",
              "name" to "Clin Drug",
              "category" to "General",
              "unit" to "tab",
            ),
            token,
          ),
        )
      val drugId = drug["id"].asText()

      val enc =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/encounters",
            json(
              "patientId" to patientId,
              "departmentId" to
                hospitalId.let {
                  `data`(
                    api(HttpMethod.GET, "/api/v1/hospital-config/hospitals/$it/departments", token = token),
                  )[0]["id"].asText()
                },
              "chiefComplaint" to "Cough",
              "reason" to "coverage",
            ),
            token,
          ),
        )["id"].asText()

      assert2xx(api(HttpMethod.GET, "/api/v1/encounters/$enc", token = token), "encounter detail")
      assert2xx(api(HttpMethod.GET, "/api/v1/encounters/patients/$patientId/vitals", token = token), "patient vitals")
      val note =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/encounters/$enc/notes",
            json(
              "noteType" to "PROGRESS",
              "title" to "t",
              "subjective" to "cough",
              "patientMrn" to patientMrn,
            ),
            token,
          ),
        )
      assertNotNull(note["id"])
      assert2xx(api(HttpMethod.GET, "/api/v1/encounters/$enc/notes", token = token), "list encounter notes")
      val dx =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/encounters/$enc/diagnoses",
            json(
              "code" to "R05",
              "name" to "Cough",
              "system" to "ICD-10",
              "patientMrn" to patientMrn,
            ),
            token,
          ),
        )
      val dxId = dx["id"].asText()
      assert2xx(api(HttpMethod.GET, "/api/v1/encounters/$enc/diagnoses", token = token), "list diagnoses")
      assert2xx(api(HttpMethod.DELETE, "/api/v1/encounters/$enc/diagnoses/$dxId?patientMrn=$patientMrn", token = token), "delete diagnosis")
      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/encounters/$enc/orders",
          json(
            "orderType" to "LAB",
            "orderCode" to "CBC",
            "orderName" to "CBC Panel",
            "priority" to "routine",
            "patientMrn" to patientMrn,
          ),
          token,
        ),
        "place order",
      )
      assert2xx(api(HttpMethod.GET, "/api/v1/encounters/$enc/orders", token = token), "list orders")
      assert2xx(api(HttpMethod.GET, "/api/v1/encounters/$enc/prescriptions", token = token), "list prescriptions")

      val stock =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/pharmacy/stock",
            json(
              "drugId" to drugId,
              "drugName" to "Clin Drug",
              "batchNumber" to "CL-$s",
              "quantityOnHand" to 50,
              "reorderLevel" to 5,
              "unitPrice" to 10.0,
              "location" to "B-2",
            ),
            token,
          ),
        )
      val stockId = stock["id"].asText()
      assert2xx(
        api(HttpMethod.PUT, "/api/v1/pharmacy/stock/$stockId", json("quantityOnHand" to 60, "location" to "B-2"), token),
        "update stock",
      )
      val disp =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/pharmacy/dispensations",
            json(
              "patientId" to patientId,
              "encounterId" to enc,
              "drugId" to drugId,
              "stockId" to stockId,
              "drugName" to "Clin Drug",
              "quantity" to 2,
              "unitPrice" to 10.0,
            ),
            token,
          ),
        )
      val dispId = disp["id"].asText()
      assert2xx(api(HttpMethod.POST, "/api/v1/pharmacy/dispensations/$dispId/verify", json(), token), "verify dispensation")
      val ret = api(HttpMethod.POST, "/api/v1/pharmacy/dispensations/$dispId/return", json("reason" to "test return"), token)
      assertTrue(ret.status in 200..299, "return dispensation -> ${ret.status}")
      assert2xx(api(HttpMethod.GET, "/api/v1/pharmacy/dispensations", token = token), "list dispensations")
      assert2xx(api(HttpMethod.GET, "/api/v1/pharmacy/stock", token = token), "list stock")

      assert2xx(api(HttpMethod.GET, "/api/v1/lab/orders", token = token), "list lab orders")
      val labOrder =
        api(
          HttpMethod.POST,
          "/api/v1/lab/orders",
          json(
            "patientId" to patientId,
            "encounterId" to enc,
            "tests" to listOf(mapOf("testCode" to "CBC", "testName" to "CBC")),
            "priority" to "routine",
          ),
          token,
        )
      assert2xx(labOrder, "create lab order")
      val reject =
        api(
          HttpMethod.POST,
          "/api/v1/lab/orders/${`data`(labOrder)["order"]["id"].asText()}/reject",
          json("reason" to "duplicate"),
          token,
        )
      assertTrue(reject.status in 200..299 || reject.status == 409, "reject lab order -> ${reject.status}")
    }

    // 02.06 pharmacy stock governance: FEFO + near-expiry BLOCK, drug-master activation,
    // formulary, indents, audited substitution, trigger-enforced ledger, min-level alerts,
    // controlled register (CS-04 pharmacist leg), wastage.
    @Test
    fun pharmacyFefoAndLedgerCoverage() {
      val token = adminToken()
      val s = suffix()
      val adminId = adminUserId()
      ensureSeed(token)

      val drugAId =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/master-data/drugs",
            json("code" to "FEFO-A-$s", "name" to "Fefo Drug A", "category" to "General", "unit" to "tab"),
            token,
          ),
        )["id"].asText()
      val drugBId =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/master-data/drugs",
            json("code" to "FEFO-B-$s", "name" to "Fefo Drug B", "category" to "Controlled", "unit" to "tab", "isControlled" to true),
            token,
          ),
        )["id"].asText()
      val drugCId =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/master-data/drugs",
            json("code" to "FEFO-C-$s", "name" to "Fefo Drug C", "category" to "General", "unit" to "tab"),
            token,
          ),
        )["id"].asText()
      val drugDId =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/master-data/drugs",
            json("code" to "FEFO-D-$s", "name" to "Fefo Drug D", "category" to "General", "unit" to "tab"),
            token,
          ),
        )["id"].asText()

      fun addStock(
        drugId: String,
        name: String,
        batch: String,
        qty: Int,
        reorder: Int,
        expiryDays: Long? = null,
      ): JsonNode =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/pharmacy/stock",
            json(
              "drugId" to drugId,
              "drugName" to name,
              "batchNumber" to batch,
              "quantityOnHand" to qty,
              "reorderLevel" to reorder,
              "expiryDate" to expiryDays?.let { LocalDate.now().plusDays(it).toString() },
            ),
            token,
          ),
        )

      val b1 = addStock(drugAId, "Fefo Drug A", "B1-$s", 50, 5, 120)
      val b2 = addStock(drugAId, "Fefo Drug A", "B2-$s", 40, 5, 60)
      addStock(drugDId, "Fefo Drug D", "BD-$s", 10, 5, 10) // near-expiry plain drug: FEFO would pick this one
      val bB2 = addStock(drugBId, "Fefo Drug B", "BB2-$s", 10, 5, 90)
      val bC = addStock(drugCId, "Fefo Drug C", "BC-$s", 3, 5) // below reorder level -> alert

      // Formulary: add A + C, duplicate rejected, list resolves drug names.
      assert2xx(api(HttpMethod.POST, "/api/v1/pharmacy/formulary", json("drugId" to drugAId), token), "add formulary A")
      assert2xx(api(HttpMethod.POST, "/api/v1/pharmacy/formulary", json("drugId" to drugCId), token), "add formulary C")
      assert4xx(api(HttpMethod.POST, "/api/v1/pharmacy/formulary", json("drugId" to drugAId), token), "duplicate formulary entry rejected")
      val formulary = `data`(api(HttpMethod.GET, "/api/v1/pharmacy/formulary", token = token))
      assertTrue(
        formulary.any { it["drugName"].asText() == "Fefo Drug A" } && formulary.any { it["drugName"].asText() == "Fefo Drug C" },
        "formulary lists drugs by name",
      )

      // FEFO: dispense A without stockId -> earliest-expiry batch (B2, +60d) is picked.
      val fefo =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/pharmacy/dispensations",
            json("patientId" to adminId, "drugId" to drugAId, "drugName" to "Fefo Drug A", "quantity" to 5),
            token,
          ),
        )
      assertEquals("B2-$s", fefo["batchNumber"].asText(), "FEFO picks earliest-expiry batch")
      val b2Ledger = `data`(api(HttpMethod.GET, "/api/v1/pharmacy/stock/ledger?stockId=${b2["id"].asText()}", token = token))
      assertEquals("DISPENSE", b2Ledger[0]["movement"].asText(), "dispense lands in ledger")
      assertEquals(35, b2Ledger[0]["balanceAfter"].asInt(), "ledger balance after dispense")
      assertEquals(-5, b2Ledger[0]["quantityDelta"].asInt(), "ledger delta negative on dispense")

      // Near-expiry BLOCK: only near-expiry batch covers drug D via FEFO.
      val blocked =
        api(
          HttpMethod.POST,
          "/api/v1/pharmacy/dispensations",
          json("patientId" to adminId, "drugId" to drugDId, "drugName" to "Fefo Drug D", "quantity" to 1),
          token,
        )
      assert4xx(blocked, "near-expiry dispense blocked")
      assertTrue(blocked.body.contains("NEAR_EXPIRY_BLOCK"), "near-expiry error code: ${blocked.body.take(200)}")

      // Controlled drug (CS-04 pharmacist leg): second authorizer required + register row.
      val noSecond =
        api(
          HttpMethod.POST,
          "/api/v1/pharmacy/dispensations",
          json("patientId" to adminId, "drugId" to drugBId, "stockId" to bB2["id"].asText(), "drugName" to "Fefo Drug B", "quantity" to 2),
          token,
        )
      assert4xx(noSecond, "controlled dispense without second authorizer rejected")
      assertTrue(noSecond.body.contains("SECOND_AUTHORIZER_REQUIRED"), "second-authorizer error code")
      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/pharmacy/dispensations",
          json(
            "patientId" to adminId,
            "drugId" to drugBId,
            "stockId" to bB2["id"].asText(),
            "drugName" to "Fefo Drug B",
            "quantity" to 2,
            "secondAuthorizerId" to adminId,
          ),
          token,
        ),
        "controlled dispense with second authorizer",
      )
      val register = `data`(api(HttpMethod.GET, "/api/v1/pharmacy/controlled-register", token = token))
      assertTrue(
        register.any { it["drugName"].asText() == "Fefo Drug B" && it["secondAuthorizerId"].asText() == adminId && it["batchNumber"].asText() == "BB2-$s" },
        "controlled register row with second authorizer",
      )

      // Substitution audited: reason required; recorded on the dispensation.
      assert4xx(
        api(
          HttpMethod.POST,
          "/api/v1/pharmacy/dispensations",
          json(
            "patientId" to adminId,
            "drugId" to drugAId,
            "stockId" to b1["id"].asText(),
            "drugName" to "Fefo Drug A",
            "quantity" to 1,
            "substitutedDrugName" to "Fefo Generic",
          ),
          token,
        ),
        "substitution without reason rejected",
      )
      val subst =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/pharmacy/dispensations",
            json(
              "patientId" to adminId,
              "drugId" to drugAId,
              "stockId" to b1["id"].asText(),
              "drugName" to "Fefo Drug A",
              "quantity" to 2,
              "substitutedDrugName" to "Fefo Generic",
              "substitutionReason" to "brand out of stock",
            ),
            token,
          ),
        )
      assertEquals("B1-$s", subst["batchNumber"].asText(), "batch snapshot on explicit dispense")
      assertEquals("brand out of stock", subst["substitutionReason"].asText(), "substitution reason recorded")
      assertEquals("Fefo Generic", subst["substitutedDrugName"].asText(), "substituted drug recorded")

      // Drug-master activation gate: inactive drug cannot enter stock or formulary.
      assert2xx(
        api(HttpMethod.PATCH, "/api/v1/master-data/drugs/$drugCId/active", json("isActive" to false), token),
        "deactivate drug C",
      )
      assert4xx(
        api(HttpMethod.POST, "/api/v1/pharmacy/stock", json("drugId" to drugCId, "drugName" to "Fefo Drug C", "quantityOnHand" to 1), token),
        "inactive drug rejected on stock intake",
      )
      assert4xx(api(HttpMethod.POST, "/api/v1/pharmacy/formulary", json("drugId" to drugCId), token), "inactive drug rejected on formulary add")
      val cEntryId = formulary.first { it["drugName"].asText() == "Fefo Drug C" }["id"].asText()
      assert2xx(api(HttpMethod.DELETE, "/api/v1/pharmacy/formulary/$cEntryId", token = token), "remove formulary entry")

      // Wastage: quantity drops, ledger row carries the reason; over-wastage rejected.
      val wasted =
        `data`(
          api(HttpMethod.POST, "/api/v1/pharmacy/stock/${b1["id"].asText()}/wastage", json("quantity" to 5, "reason" to "damaged vial"), token),
        )
      assertEquals(43, wasted["quantityOnHand"].asInt(), "wastage reduces on-hand (50 - 2 dispensed - 5 wasted)")
      assert4xx(
        api(HttpMethod.POST, "/api/v1/pharmacy/stock/${b1["id"].asText()}/wastage", json("quantity" to 999, "reason" to "oops"), token),
        "over-wastage rejected",
      )

      // Trigger-enforced ledger: running balance reconstructs on-hand exactly.
      val b1Ledger = `data`(api(HttpMethod.GET, "/api/v1/pharmacy/stock/ledger?stockId=${b1["id"].asText()}", token = token))
      assertEquals(43, b1Ledger.sumOf { it["quantityDelta"].asInt() }, "ledger deltas sum to on-hand")
      assertEquals(43, b1Ledger[0]["balanceAfter"].asInt(), "latest balance_after matches stock")
      val movements = b1Ledger.map { it["movement"].asText() }.toSet()
      assertTrue(movements.containsAll(setOf("INTAKE", "DISPENSE", "WASTAGE")), "movements logged: $movements")
      val wastageRow = b1Ledger.first { it["movement"].asText() == "WASTAGE" }
      assertEquals("damaged vial", wastageRow["note"].asText(), "wastage reason in ledger note")

      // Indents: create -> receive adds stock through the ledger.
      val indent =
        `data`(
          api(HttpMethod.POST, "/api/v1/pharmacy/indents", json("drugId" to drugAId, "quantityRequested" to 100, "note" to "ward restock"), token),
        )
      assertEquals("Fefo Drug A", indent["drugName"].asText(), "indent drug name resolved from master data")
      val stockBefore =
        `data`(api(HttpMethod.GET, "/api/v1/pharmacy/stock", token = token))
          .filter { it["drugId"].asText() == drugAId }
          .sumOf { it["quantityOnHand"].asInt() }
      assert2xx(
        api(HttpMethod.POST, "/api/v1/pharmacy/indents/${indent["id"].asText()}/receive", json("quantityReceived" to 100), token),
        "receive indent",
      )
      val stockAfter =
        `data`(api(HttpMethod.GET, "/api/v1/pharmacy/stock", token = token))
          .filter { it["drugId"].asText() == drugAId }
          .sumOf { it["quantityOnHand"].asInt() }
      assertEquals(stockBefore + 100, stockAfter, "indent receive adds stock")
      assert2xx(api(HttpMethod.GET, "/api/v1/pharmacy/indents", token = token), "list indents")
      val indents = `data`(api(HttpMethod.GET, "/api/v1/pharmacy/indents", token = token))
      assertTrue(indents.any { it["status"].asText() == "received" && it["quantityReceived"].asInt() == 100 }, "indent received")

      // Min-level alerts: drug C stock (3 <= reorder 5) appears.
      val alerts = `data`(api(HttpMethod.GET, "/api/v1/pharmacy/stock/alerts", token = token))
      assertTrue(alerts.any { it["drugName"].asText() == "Fefo Drug C" }, "low-stock alert for drug C")
    }

    @Test
    fun criticalResultsQcCoverage() {
      val token = adminToken()
      val s = suffix()

      val patientId =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/patients",
            json(
              "firstName" to "Cristina",
              "lastName" to "Critical$s",
              "gender" to "female",
              "dateOfBirth" to "1988-03-03",
              "phone" to "5555$s".take(11),
              "qid" to "CR$s".take(11),
              "nationality" to "QA",
              "city" to "Doha",
            ),
            token,
          ),
        )["patient"]["id"].asText()
      val master =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/master-data/lab-tests",
            json(
              "code" to "CRIT-$s",
              "name" to "Critical Potassium $s",
              "unit" to "mmol/L",
              "referenceRange" to "3.5-5.1",
              "criticalLow" to 2.0,
              "criticalHigh" to 10.0,
              "criticalSlaMinutes" to 60,
            ),
            token,
          ),
        )
      assertEquals(2.0, master["criticalLow"].asDouble(), "master critical low stored")
      assertEquals(60, master["criticalSlaMinutes"].asInt(), "master critical SLA stored")
      val testCode = "CRIT-$s"

      fun createCriticalOrder(): Pair<String, String> {
        val created =
          api(
            HttpMethod.POST,
            "/api/v1/lab/orders",
            json(
              "patientId" to patientId,
              "tests" to listOf(mapOf("testCode" to testCode, "testName" to "Critical Potassium $s")),
            ),
            token,
          )
        assert2xx(created, "create critical order")
        val orderId = `data`(created)["order"]["id"].asText()
        val testRequestId = `data`(api(HttpMethod.GET, "/api/v1/lab/orders/$orderId", token = token))["tests"][0]["id"].asText()
        assert2xx(api(HttpMethod.POST, "/api/v1/lab/orders/$orderId/collect", json(), token), "collect $orderId")
        assert2xx(api(HttpMethod.POST, "/api/v1/lab/orders/$orderId/receive", json(), token), "receive $orderId")
        assert2xx(api(HttpMethod.POST, "/api/v1/lab/orders/$orderId/start", json(), token), "start $orderId")
        return orderId to testRequestId
      }

      fun enter(
        orderId: String,
        testRequestId: String,
        value: String,
      ) {
        assert2xx(
          api(
            HttpMethod.POST,
            "/api/v1/lab/orders/$orderId/results",
            json(
              "results" to
                listOf(
                  mapOf(
                    "testRequestId" to testRequestId,
                    "resultValue" to value,
                    "unit" to "mmol/L",
                    "referenceRange" to "3.5-5.1",
                  ),
                ),
            ),
            token,
          ),
          "enter result $value on $orderId",
        )
      }

      fun openCriticals(orderId: String) =
        `data`(api(HttpMethod.GET, "/api/v1/lab/critical-results?status=OPEN", token = token))
          .filter { it["labOrderId"].asText() == orderId }

      // Order A: critically LOW result opens an unacknowledged critical result.
      val (orderA, reqA) = createCriticalOrder()
      enter(orderA, reqA, "1.0")
      val criticalsA = openCriticals(orderA)
      assertEquals(1, criticalsA.size, "exactly one open critical result for order A")
      val criticalA = criticalsA.single()
      assertEquals("CRITICAL_LOW", criticalA["criticality"].asText(), "criticality below critical_low")
      assertEquals("1.0", criticalA["resultValue"].asText(), "critical result snapshots the triggering value")
      assertEquals(0, criticalA["escalationLevel"].asInt(), "fresh critical result not yet escalated")
      assertEquals("none", criticalA["escalationRole"].asText(), "fresh critical result role none")

      assert2xx(api(HttpMethod.POST, "/api/v1/lab/orders/$orderA/verify", json(), token), "verify order A")
      val blockedA = api(HttpMethod.POST, "/api/v1/lab/orders/$orderA/release", json(), token)
      assert4xx(blockedA, "release blocked while critical result unacked")
      assertTrue(blockedA.body.contains("CRITICAL_RESULT_UNACKED"), "unacked-critical error: ${blockedA.body.take(200)}")

      val ackedA = api(HttpMethod.POST, "/api/v1/lab/critical-results/${criticalA["id"].asText()}/ack", json("note" to "called attending"), token)
      assert2xx(ackedA, "ack critical result A")
      assertEquals("ACKNOWLEDGED", `data`(ackedA)["status"].asText(), "ack flips status")
      val ackTrailA = `data`(api(HttpMethod.GET, "/api/v1/lab/critical-results/${criticalA["id"].asText()}/acks", token = token))
      assertEquals(1, ackTrailA.size(), "ack appends a trail row")
      assertEquals("called attending", ackTrailA[0]["note"].asText(), "ack note recorded")
      assert4xx(
        api(HttpMethod.POST, "/api/v1/lab/critical-results/${criticalA["id"].asText()}/ack", json(), token),
        "second ack rejected",
      )
      assert2xx(api(HttpMethod.POST, "/api/v1/lab/orders/$orderA/release", json(), token), "release after ack")

      // Order B: critically HIGH + >=100% delta jump vs A blocks verify without an override note.
      val (orderB, reqB) = createCriticalOrder()
      enter(orderB, reqB, "12.0")
      val verifyB = api(HttpMethod.POST, "/api/v1/lab/orders/$orderB/verify", json(), token)
      assert4xx(verifyB, "delta-blocked verify without override")
      assertTrue(verifyB.body.contains("DELTA_THRESHOLD_BLOCK"), "delta threshold error: ${verifyB.body.take(200)}")
      assert2xx(
        api(HttpMethod.POST, "/api/v1/lab/orders/$orderB/verify", json("overrideNote" to "clinically expected rise"), token),
        "verify with delta override note",
      )
      val testRowB = `data`(api(HttpMethod.GET, "/api/v1/lab/orders/$orderB", token = token))["tests"][0]
      assertEquals("1.0", testRowB["priorValue"].asText(), "delta prior captured from order A")
      assertTrue(testRowB["deltaPercent"].asDouble() >= 100.0, "delta percent above threshold")
      assertTrue(testRowB["isDeltaBlocked"].asBoolean(), "delta block flagged on the test request")
      val criticalsB = openCriticals(orderB)
      assertEquals(1, criticalsB.size, "order B critical HIGH open")
      assertEquals("CRITICAL_HIGH", criticalsB.single()["criticality"].asText(), "criticality above critical_high")

      // Order C: critical HIGH again, flat delta vs B; QC REJECTED hard-blocks release.
      val (orderC, reqC) = createCriticalOrder()
      enter(orderC, reqC, "12.0")
      assert2xx(api(HttpMethod.POST, "/api/v1/lab/orders/$orderC/verify", json(), token), "verify order C (flat delta)")

      // Westgard: z = (6.0 - 5.0) / 0.2 = 5.0 -> 1_3S REJECTED.
      val qcRejected =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/lab/qc-runs",
            json(
              "testCode" to testCode,
              "level" to "NORMAL",
              "observedValue" to 6.0,
              "targetMean" to 5.0,
              "targetSd" to 0.2,
              "instrument" to "cobas-$s",
            ),
            token,
          ),
        )
      assertEquals("REJECTED", qcRejected["status"].asText(), "z=5 triggers 1_3S reject")
      assertEquals("1_3S", qcRejected["westgardRules"].asText(), "westgard rules recorded as CSV")
      assertEquals(5.0, qcRejected["zValue"].asDouble(), "z-value exposed on the run")
      val qcBlockedC = api(HttpMethod.POST, "/api/v1/lab/orders/$orderC/release", json(), token)
      assert4xx(qcBlockedC, "QC REJECTED blocks release")
      assertTrue(qcBlockedC.body.contains("QC_BLOCK"), "qc block error: ${qcBlockedC.body.take(200)}")

      // Westgard: z = 0 -> ACCEPTED; release now blocked by the open critical result instead.
      val qcAccepted =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/lab/qc-runs",
            json("testCode" to testCode, "level" to "NORMAL", "observedValue" to 5.0, "targetMean" to 5.0, "targetSd" to 0.2),
            token,
          ),
        )
      assertEquals("ACCEPTED", qcAccepted["status"].asText(), "z=0 accepted, no rules fired")
      val critBlockedC = api(HttpMethod.POST, "/api/v1/lab/orders/$orderC/release", json(), token)
      assert4xx(critBlockedC, "release blocked by open critical result after QC fix")
      assertTrue(critBlockedC.body.contains("CRITICAL_RESULT_UNACKED"), "unacked error after QC fix: ${critBlockedC.body.take(200)}")
      val criticalC = `data`(api(HttpMethod.GET, "/api/v1/lab/critical-results?status=OPEN", token = token)).first { it["labOrderId"].asText() == orderC }
      assert2xx(
        api(HttpMethod.POST, "/api/v1/lab/critical-results/${criticalC["id"].asText()}/ack", json(), token),
        "ack order C critical result",
      )
      assert2xx(api(HttpMethod.POST, "/api/v1/lab/orders/$orderC/release", json(), token), "release after QC accepted + ack")

      // Westgard: z = (5.5 - 5.0) / 0.2 = 2.5 -> 1_2S WARNING.
      val qcWarning =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/lab/qc-runs",
            json("testCode" to testCode, "level" to "NORMAL", "observedValue" to 5.5, "targetMean" to 5.0, "targetSd" to 0.2),
            token,
          ),
        )
      assertEquals("WARNING", qcWarning["status"].asText(), "z=2.5 triggers 1_2S warning")

      // Dashboard reads: qc run history + SLA escalation metadata on the critical-result line.
      val qcList =
        `data`(api(HttpMethod.GET, "/api/v1/lab/qc-runs", token = token)).filter { it["testCode"].asText() == testCode }
      assertEquals(3, qcList.size, "qc run history for the test")
      assertTrue(
        qcList.map { it["status"].asText() }.containsAll(listOf("REJECTED", "ACCEPTED", "WARNING")),
        "qc statuses recorded: ${qcList.map { it["status"].asText() }}",
      )
      assertEquals("WARNING", qcList[0]["status"].asText(), "latest qc run listed first")
      val criticals =
        `data`(api(HttpMethod.GET, "/api/v1/lab/critical-results", token = token))
          .filter { setOf(orderA, orderB, orderC).contains(it["labOrderId"].asText()) }
      assertEquals(3, criticals.size, "three critical results across the three orders")
      assertTrue(
        criticals.all { it.has("escalationLevel") && it.has("escalationRole") && it.has("slaMinutes") },
        "dashboard line carries SLA escalation metadata",
      )
      assertEquals(60, criticals[0]["slaMinutes"].asInt(), "sla minutes propagated from master data")
      assertEquals(2, criticals.count { it["status"].asText() == "ACKNOWLEDGED" }, "two acknowledged critical results")
    }

    @Test
    fun labReportsDeliveryCoverage() {
      val token = adminToken()
      val s = suffix()
      val hospitalId = ensureSeed(token)

      val femaleId =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/patients",
            json(
              "firstName" to "Maria",
              "lastName" to "Report$s",
              "gender" to "female",
              "dateOfBirth" to "1988-03-03",
              "phone" to "5561$s".take(11),
              "qid" to "RP1$s".take(11),
              "nationality" to "QA",
              "city" to "Doha",
            ),
            token,
          ),
        )["patient"]["id"].asText()
      val maleId =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/patients",
            json(
              "firstName" to "Victor",
              "lastName" to "ReportM$s",
              "gender" to "male",
              "dateOfBirth" to "1990-01-01",
              "phone" to "5562$s".take(11),
              "qid" to "RP2$s".take(11),
              "nationality" to "QA",
              "city" to "Doha",
            ),
            token,
          ),
        )["patient"]["id"].asText()

      val testCode = "REF-$s"

      // Age/sex reference ranges: the 11-200 female band will be picked for a 38yo; the 0-10 band
      // is out of her age window and must be ignored (age selection proof).
      val rangeF =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/lab/reference-ranges",
            json(
              "testCode" to testCode,
              "sex" to "F",
              "ageMinYears" to 11,
              "ageMaxYears" to 200,
              "rangeText" to "3.5-4.6",
              "hospitalId" to hospitalId,
            ),
            token,
          ),
        )
      assertEquals("3.5-4.6", rangeF["rangeText"].asText(), "female adult range stored")
      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/lab/reference-ranges",
          json("testCode" to testCode, "sex" to "F", "ageMinYears" to 0, "ageMaxYears" to 10, "rangeText" to "2.0-3.0", "hospitalId" to hospitalId),
          token,
        ),
        "child female range stored",
      )
      assert4xx(
        api(
          HttpMethod.POST,
          "/api/v1/lab/reference-ranges",
          json("testCode" to testCode, "sex" to "X", "rangeText" to "1.0-2.0", "hospitalId" to hospitalId),
          token,
        ),
        "invalid sex rejected",
      )
      val rangeList = `data`(api(HttpMethod.GET, "/api/v1/lab/reference-ranges?testCode=$testCode", token = token))
      assertEquals(2, rangeList.size(), "both ranges listed for the test code")

      fun createOrder(patientId: String): Pair<String, String> {
        val created =
          api(
            HttpMethod.POST,
            "/api/v1/lab/orders",
            json(
              "patientId" to patientId,
              "tests" to listOf(mapOf("testCode" to testCode, "testName" to "TSH $s")),
            ),
            token,
          )
        assert2xx(created, "create lab order")
        val orderId = `data`(created)["order"]["id"].asText()
        val testRequestId = `data`(api(HttpMethod.GET, "/api/v1/lab/orders/$orderId", token = token))["tests"][0]["id"].asText()
        return orderId to testRequestId
      }

      fun runLab(
        orderId: String,
        testRequestId: String,
        manualRange: String?,
        release: Boolean,
      ): String {
        assert2xx(api(HttpMethod.POST, "/api/v1/lab/orders/$orderId/collect", json(), token), "collect $orderId")
        assert2xx(api(HttpMethod.POST, "/api/v1/lab/orders/$orderId/receive", json(), token), "receive $orderId")
        assert2xx(api(HttpMethod.POST, "/api/v1/lab/orders/$orderId/start", json(), token), "start $orderId")
        val result =
          mapOf(
            "testRequestId" to testRequestId,
            "resultValue" to "4.8",
            "unit" to "mIU/L",
            "referenceRange" to manualRange,
          )
        assert2xx(api(HttpMethod.POST, "/api/v1/lab/orders/$orderId/results", json("results" to listOf(result)), token), "enter results $orderId")
        assert2xx(api(HttpMethod.POST, "/api/v1/lab/orders/$orderId/verify", json(), token), "verify $orderId")
        if (release) assert2xx(api(HttpMethod.POST, "/api/v1/lab/orders/$orderId/release", json(), token), "release $orderId")
        return `data`(api(HttpMethod.GET, "/api/v1/lab/orders/$orderId", token = token))["order"]["orderNumber"].asText()
      }

      // Female 38y, no manual range: structured adult-female band 3.5-4.6 applies (not the 0-10
      // band), so 4.8 is abnormal.
      val (orderF, reqF) = createOrder(femaleId)
      val orderFNo = runLab(orderF, reqF, manualRange = null, release = true)
      val testF = `data`(api(HttpMethod.GET, "/api/v1/lab/orders/$orderF", token = token))["tests"][0]
      assertEquals("3.5-4.6", testF["referenceRange"].asText(), "structured age/sex range applied")
      assertTrue(testF["isAbnormal"].asBoolean(), "4.8 above the adult female band flagged abnormal")

      // Publish gate: only released orders may produce a report.
      val (orderGate, reqGate) = createOrder(femaleId)
      runLab(orderGate, reqGate, manualRange = null, release = false)
      val gatePub = api(HttpMethod.POST, "/api/v1/lab/orders/$orderGate/report", json(), token)
      assert4xx(gatePub, "publish gated on released status")
      assertTrue(gatePub.body.contains("LAB_ORDER_NOT_RELEASED"), "publish gate error: ${gatePub.body.take(200)}")

      // Publish v1: branded PDF bytes carry the order number, structured range and abnormal flag.
      val report1 = `data`(api(HttpMethod.POST, "/api/v1/lab/orders/$orderF/report", json(), token))
      assertEquals(1, report1["reportVersion"].asInt(), "first publish is version 1")
      assertTrue(report1["deliveredVia"].isNull, "fresh report undelivered")
      val pdf1 = api(HttpMethod.GET, "/api/v1/lab/orders/$orderF/report", token = token)
      assert2xx(pdf1, "download report v1")
      assertTrue(pdf1.body.contains("%PDF-1.4"), "pdf header in bytes: ${pdf1.body.take(120)}")
      assertTrue(pdf1.body.contains(orderFNo), "branded pdf carries the order number")
      assertTrue(pdf1.body.contains("3.5-4.6"), "pdf carries the structured reference range")
      assertTrue(pdf1.body.contains("ABNORMAL"), "pdf carries the abnormal flag")

      // Delivery: counter pickup stamps once; a second delivery and an invalid via are rejected.
      val delivered =
        `data`(api(HttpMethod.POST, "/api/v1/lab/reports/${report1["id"].asText()}/deliver", json("via" to "COUNTER"), token))
      assertEquals("COUNTER", delivered["deliveredVia"].asText(), "counter delivery stamped")
      assertFalse(delivered["deliveredAt"].isNull, "delivery timestamp recorded")
      val secondDeliver =
        api(HttpMethod.POST, "/api/v1/lab/reports/${report1["id"].asText()}/deliver", json("via" to "COUNTER"), token)
      assert4xx(secondDeliver, "second delivery rejected")
      assertTrue(secondDeliver.body.contains("REPORT_ALREADY_DELIVERED"), "double-delivery error: ${secondDeliver.body.take(200)}")
      val badVia = api(HttpMethod.POST, "/api/v1/lab/reports/${report1["id"].asText()}/deliver", json("via" to "COURIER"), token)
      assert4xx(badVia, "invalid delivery via rejected")
      assertTrue(badVia.body.contains("DELIVERY_VIA_INVALID"), "invalid via error: ${badVia.body.take(200)}")

      // Amendment of a released result: old value preserved, new value in place, reason required.
      val noReason = api(HttpMethod.POST, "/api/v1/lab/orders/$orderF/results/$reqF/amend", json("newValue" to "4.0", "reason" to "  "), token)
      assert4xx(noReason, "blank amendment reason rejected")
      assertTrue(noReason.body.contains("AMENDMENT_REASON_REQUIRED"), "reason-required error: ${noReason.body.take(200)}")
      val amended =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/lab/orders/$orderF/results/$reqF/amend",
            json(
              "newValue" to "4.0",
              "reason" to "transcription corrected",
            ),
            token,
          ),
        )
      assertEquals("4.0", amended["tests"][0]["resultValue"].asText(), "amended value in place")
      assertFalse(amended["tests"][0]["isAbnormal"].asBoolean(), "4.0 within 3.5-4.6 not abnormal")
      val amendments = `data`(api(HttpMethod.GET, "/api/v1/lab/orders/$orderF/amendments", token = token))
      assertEquals(1, amendments.size(), "one versioned amendment row")
      assertEquals("4.8", amendments[0]["oldValue"].asText(), "old value preserved")
      assertEquals("4.0", amendments[0]["newValue"].asText(), "new value recorded")
      assertEquals("transcription corrected", amendments[0]["reason"].asText(), "reason recorded")

      // Republish after amendment: version 2 carries the amendment trail.
      val report2 = `data`(api(HttpMethod.POST, "/api/v1/lab/orders/$orderF/report", json(), token))
      assertEquals(2, report2["reportVersion"].asInt(), "republish after amendment is version 2")
      val versions = `data`(api(HttpMethod.GET, "/api/v1/lab/orders/$orderF/reports", token = token))
      assertEquals(2, versions.size(), "two report versions listed")
      assertEquals(2, versions[0]["reportVersion"].asInt(), "latest version first")
      val pdf2 = api(HttpMethod.GET, "/api/v1/lab/orders/$orderF/report", token = token)
      assert2xx(pdf2, "download report v2")
      assertTrue(pdf2.body.contains("AMENDED"), "v2 pdf lists the amendment trail")

      // Male fallback: no structured male/ANY band -> manual master string 3.5-5.1, 4.8 normal.
      val (orderM, reqM) = createOrder(maleId)
      runLab(orderM, reqM, manualRange = "3.5-5.1", release = true)
      val testM = `data`(api(HttpMethod.GET, "/api/v1/lab/orders/$orderM", token = token))["tests"][0]
      assertEquals("3.5-5.1", testM["referenceRange"].asText(), "manual master range fallback")
      assertFalse(testM["isAbnormal"].asBoolean(), "4.8 within the male master band")
      val reportM = `data`(api(HttpMethod.POST, "/api/v1/lab/orders/$orderM/report", json(), token))
      assertEquals(1, reportM["reportVersion"].asInt(), "male order first publish v1")
      val deliveredM =
        `data`(api(HttpMethod.POST, "/api/v1/lab/reports/${reportM["id"].asText()}/deliver", json("via" to "APP", "deliveredTo" to maleId), token))
      assertEquals("APP", deliveredM["deliveredVia"].asText(), "app delivery stamped")
      assertEquals(maleId, deliveredM["deliveredTo"].asText(), "app recipient recorded")

      // TAT analytics: released orders counted per test code plus an ALL row.
      val tat = `data`(api(HttpMethod.GET, "/api/v1/lab/tat?days=30", token = token))
      val allRow = tat.first { it["testCode"].asText() == "ALL" }
      assertTrue(allRow["count"].asInt() >= 2, "released orders counted in the TAT window")
      val refRow = tat.first { it["testCode"].asText() == testCode }
      assertEquals(2, refRow["count"].asInt(), "per-test TAT counts both released orders")
      assertTrue(refRow.has("avgMinutes") && refRow.has("minMinutes") && refRow.has("maxMinutes"), "tat stats fields present")
    }

    @Test
    fun financialAndOperationsCoverage() {
      val token = adminToken()
      val s = suffix()
      val adminId = adminUserId()
      val hospitalId = ensureSeed(token)
      val deptId =
        `data`(
          api(HttpMethod.GET, "/api/v1/hospital-config/hospitals/$hospitalId/departments", token = token),
        )[0]["id"].asText()

      val patient =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/patients",
            json(
              "firstName" to "Fin",
              "lastName" to "Patient$s",
              "gender" to "male",
              "dateOfBirth" to "1982-02-02",
              "phone" to "55$s".take(11),
              "qid" to "6$s".take(11),
              "nationality" to "QA",
              "city" to "Doha",
            ),
            token,
          ),
        )["patient"]
      val patientId = patient["id"].asText()

      val tariff =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/billing/tariffs",
            json(
              "code" to "FIN-$s",
              "name" to "Fin Tariff",
              "category" to "consultation",
              "priceQar" to 150.0,
            ),
            token,
          ),
        )
      assert2xx(
        api(HttpMethod.PUT, "/api/v1/billing/tariffs/${tariff["id"].asText()}", json("priceQar" to 160.0), token),
        "update tariff",
      )
      assert2xx(api(HttpMethod.GET, "/api/v1/billing/tariffs", token = token), "list tariffs")
      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/billing/charges",
          json(
            "patientId" to patientId,
            "chargeCode" to "FIN-CHA",
            "chargeName" to "Fin Charge",
            "quantity" to 1,
            "unitPriceQar" to 150.0,
          ),
          token,
        ),
        "post charge",
      )
      assert2xx(api(HttpMethod.GET, "/api/v1/billing/charges", token = token), "list charges")
      val inv = `data`(api(HttpMethod.POST, "/api/v1/billing/invoices", json("patientId" to patientId), token))["invoice"]
      val invId = inv["id"].asText()
      assert2xx(api(HttpMethod.GET, "/api/v1/billing/invoices", token = token), "list invoices")
      assert2xx(api(HttpMethod.GET, "/api/v1/billing/invoices/$invId", token = token), "invoice detail")
      val cancel = api(HttpMethod.POST, "/api/v1/billing/invoices/$invId/cancel", json("reason" to "duplicate"), token)
      assertTrue(cancel.status in 200..299, "cancel invoice -> ${cancel.status}")

      val appt =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/appointments",
            json(
              "patientId" to patientId,
              "departmentId" to deptId,
              "doctorId" to adminId,
              "appointmentType" to "OPD",
              "scheduledStart" to
                java.time.Instant
                  .now()
                  .plusSeconds(7200)
                  .toString(),
              "scheduledEnd" to
                java.time.Instant
                  .now()
                  .plusSeconds(10800)
                  .toString(),
              "durationMinutes" to 30,
              "reason" to "fin",
              "priority" to "normal",
            ),
            token,
          ),
        )
      val apptId = appt["id"].asText()
      assert2xx(api(HttpMethod.GET, "/api/v1/appointments/$apptId", token = token), "get appointment")
      assert2xx(
        api(HttpMethod.PUT, "/api/v1/appointments/$apptId", json("reason" to "fin v2", "priority" to "high"), token),
        "update appointment",
      )
      val cancelAppt =
        api(
          HttpMethod.PATCH,
          "/api/v1/appointments/$apptId/status",
          json(
            "status" to "cancelled",
            "cancelledReason" to "rescheduled",
          ),
          token,
        )
      assertTrue(cancelAppt.status in 200..299, "cancel appointment -> ${cancelAppt.status}")

      val task =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/tasks",
            json(
              "title" to "Fin task $s",
              "description" to "x",
              "priority" to "normal",
              "assigneeId" to adminId,
            ),
            token,
          ),
        )
      val taskId = task["id"].asText()
      assert2xx(api(HttpMethod.GET, "/api/v1/tasks/$taskId", token = token), "get task")
      assert2xx(api(HttpMethod.PUT, "/api/v1/tasks/$taskId", json("title" to "Fin task $s v2"), token), "update task")
      assert2xx(api(HttpMethod.POST, "/api/v1/tasks/$taskId/assign", json("assigneeId" to adminId), token), "assign task")

      val chan =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/communication/channels",
            json(
              "name" to "Fin Ops $s",
              "type" to "group",
              "memberIds" to listOf(adminId),
            ),
            token,
          ),
        )
      val chanId = chan["id"].asText()
      assert2xx(api(HttpMethod.GET, "/api/v1/communication/channels/$chanId", token = token), "channel detail")
      val chanMember =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/auth/register",
            json(
              "email" to "member-$s@test.local",
              "password" to "Passw0rd!123",
              "firstName" to "Chan",
              "lastName" to "Member",
            ),
          ),
        )["userId"].asText()
      assert2xx(
        api(HttpMethod.POST, "/api/v1/communication/channels/$chanId/members", json("userId" to chanMember), token),
        "add member",
      )
      assert2xx(api(HttpMethod.GET, "/api/v1/communication/channels/$chanId/members", token = token), "list members")
      val remove = api(HttpMethod.DELETE, "/api/v1/communication/channels/$chanId/members/$chanMember", token = token)
      assertTrue(remove.status in 200..299, "remove member -> ${remove.status}")

      val notif = `data`(api(HttpMethod.GET, "/api/v1/notifications", token = token))
      if (notif.size() > 0) {
        assert2xx(
          api(HttpMethod.POST, "/api/v1/notifications/${notif[0]["id"].asText()}/read", json(), token),
          "read notification by id",
        )
      }

      val visit =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/reception/visits",
            json(
              "patientId" to patientId,
              "visitType" to "OPD",
              "departmentId" to deptId,
              "reason" to "fin",
              "priority" to "normal",
            ),
            token,
          ),
        )["visit"]
      val visitId = visit["id"].asText()
      assert2xx(api(HttpMethod.GET, "/api/v1/reception/visits/$visitId", token = token), "get visit")
      assert2xx(api(HttpMethod.GET, "/api/v1/reception/visits", token = token), "list visits")
      assert2xx(api(HttpMethod.PATCH, "/api/v1/reception/visits/$visitId/complete", json(), token), "complete visit")

      val qType = "FIN-$s"
      val qe =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/queue/entries",
            json(
              "queueType" to qType,
              "patientId" to patientId,
              "patientName" to "Fin Queue",
              "priority" to "normal",
            ),
            token,
          ),
        )
      val tokenNo = qe["tokenNumber"].asText()
      assert2xx(api(HttpMethod.GET, "/api/v1/queue/entries/$qType/$tokenNo", token = token), "get queue entry")
      assert2xx(api(HttpMethod.PATCH, "/api/v1/queue/entries/$qType/$tokenNo/call", json(), token), "call entry")
      assert2xx(
        api(HttpMethod.PATCH, "/api/v1/queue/entries/$qType/$tokenNo/start", json("servedBy" to adminId), token),
        "start entry",
      )
      assert2xx(api(HttpMethod.PATCH, "/api/v1/queue/entries/$qType/$tokenNo/complete", json(), token), "complete entry")
      val sp =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/queue/service-points",
            json(
              "name" to "Counter $s",
              "code" to "CTR-$s",
              "queueType" to qType,
            ),
            token,
          ),
        )
      assertNotNull(sp["id"])
      assert2xx(api(HttpMethod.GET, "/api/v1/queue/service-points", token = token), "list service points")
      val stats = api(HttpMethod.GET, "/api/v1/queue/statistics?queueType=$qType", token = token)
      assertTrue(stats.status in 200..299, "queue statistics -> ${stats.status}")
      assert2xx(api(HttpMethod.GET, "/api/v1/analytics/appointments", token = token), "analytics appointments")

      val def =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/workflow/definitions",
            json(
              "code" to "FIN_FLOW_$s",
              "name" to "Fin Flow",
              "triggerType" to "MANUAL",
              "steps" to
                listOf(
                  mapOf(
                    "stepKey" to "approve",
                    "stepType" to "APPROVAL",
                    "sequenceOrder" to 0,
                    "config" to mapOf<String, Any?>("approvers" to listOf(adminId)),
                  ),
                ),
            ),
            token,
          ),
        )
      val defId = def["id"].asText()
      assert2xx(api(HttpMethod.GET, "/api/v1/workflow/definitions", token = token), "list definitions")
      assert2xx(api(HttpMethod.GET, "/api/v1/workflow/definitions/$defId", token = token), "get definition")
      assert2xx(
        api(HttpMethod.PATCH, "/api/v1/workflow/definitions/$defId", json("name" to "Fin Flow v2"), token),
        "update definition",
      )
      assert2xx(api(HttpMethod.POST, "/api/v1/workflow/definitions/$defId/activate", json(), token), "activate definition")
      val instance =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/workflow/instances",
            json(
              "definitionId" to defId,
              "payload" to mapOf("source" to "fin"),
            ),
            token,
          ),
        )
      assert2xx(api(HttpMethod.GET, "/api/v1/workflow/instances", token = token), "list instances")
      assert2xx(api(HttpMethod.POST, "/api/v1/workflow/definitions/$defId/deactivate", json(), token), "deactivate definition")

      assert2xx(api(HttpMethod.GET, "/api/v1/identity/roles", token = token), "list roles")
      val roles = `data`(api(HttpMethod.GET, "/api/v1/identity/roles", token = token))
      assert2xx(
        api(HttpMethod.GET, "/api/v1/identity/roles/${roles[0]["id"].asText()}/permissions", token = token),
        "role permissions",
      )
    }

    @Test
    fun queueAndScribeAndAiCoverage() {
      val token = adminToken()
      val s = suffix()

      val qType = "SKIP-$s"
      val skipPatient =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/patients",
            json(
              "firstName" to "Skip",
              "lastName" to "Patient$s",
              "gender" to "female",
              "dateOfBirth" to "1990-01-01",
              "phone" to "74$s".take(11),
              "qid" to "7$s".take(11),
              "nationality" to "QA",
              "city" to "Doha",
            ),
            token,
          ),
        )["patient"]
      val entry =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/queue/entries",
            json(
              "queueType" to qType,
              "patientId" to skipPatient["id"].asText(),
              "patientName" to "Skip Me",
              "priority" to "high",
            ),
            token,
          ),
        )
      val tokenNo = entry["tokenNumber"].asText()
      assert2xx(api(HttpMethod.PATCH, "/api/v1/queue/entries/$qType/$tokenNo/postpone", json(), token), "postpone entry")
      val skip = api(HttpMethod.PATCH, "/api/v1/queue/entries/$qType/$tokenNo/skip", json(), token)
      assertTrue(skip.status in 200..299, "skip entry -> ${skip.status}")
      val noShow = api(HttpMethod.PATCH, "/api/v1/queue/entries/$qType/$tokenNo/no-show", json(), token)
      assertTrue(noShow.status in 200..299 || noShow.status == 409, "no-show entry -> ${noShow.status}")

      val aiAgents = `data`(api(HttpMethod.GET, "/api/v1/ai/agents", token = token))
      if (aiAgents.size() > 0) {
        assert2xx(
          api(
            HttpMethod.PATCH,
            "/api/v1/ai/agents/${aiAgents[0]["id"].asText()}",
            json(
              "enabled" to true,
              "description" to "updated",
            ),
            token,
          ),
          "update ai agent",
        )
      }

      val session =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/scribe/sessions",
            json(
              "patientName" to "Scribe Cov $s",
              "chiefComplaint" to "cough",
            ),
            token,
          ),
        )
      val sid = session["id"].asText()
      assert2xx(api(HttpMethod.GET, "/api/v1/scribe/sessions", token = token), "list scribe sessions")
      assert2xx(api(HttpMethod.GET, "/api/v1/scribe/sessions/$sid", token = token), "get scribe session")
      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/scribe/sessions/$sid/transcript",
          json(
            "text" to "Patient: cough for a week.\nDoctor: wheeze audible.",
          ),
          token,
        ),
        "set transcript",
      )
      val draftUpdate =
        api(
          HttpMethod.POST,
          "/api/v1/scribe/sessions/$sid/draft",
          json("draft" to "SOAP NOTE — test draft\nPlan: observe"),
          token,
        )
      assertTrue(draftUpdate.status in 200..299, "update scribe draft -> ${draftUpdate.status}")
      assert2xx(api(HttpMethod.DELETE, "/api/v1/scribe/sessions/$sid", token = token), "cleanup scribe session")
    }

    @Test
    fun authRefreshLogoutMfaAndRealtimeCoverage() {
      val token = adminToken()
      val s = suffix()
      val email = "mfa-$s@test.local"

      val reg =
        api(
          HttpMethod.POST,
          "/api/v1/auth/register",
          json(
            "email" to email,
            "password" to "Passw0rd!123",
            "firstName" to "MFA",
            "lastName" to "User",
          ),
        )
      assertEquals(201, reg.status, "register: ${reg.body.take(200)}")

      val login = `data`(api(HttpMethod.POST, "/api/v1/auth/login", json("username" to email, "password" to "Passw0rd!123")))
      val access = login["accessToken"].asText()
      val refresh = login["refreshToken"].asText()
      assert2xx(api(HttpMethod.GET, "/api/v1/auth/me", token = access), "me for mfa user")

      val refreshed = api(HttpMethod.POST, "/api/v1/auth/refresh", json("refreshToken" to refresh))
      assert2xx(refreshed, "refresh token")
      val newAccess = `data`(refreshed)["accessToken"].asText()
      assert2xx(api(HttpMethod.GET, "/api/v1/auth/me", token = newAccess), "me with refreshed token")

      val setup = `data`(api(HttpMethod.GET, "/api/v1/auth/mfa/setup", token = newAccess))
      val secret = setup["secret"].asText()
      assertNotNull(secret)
      val code = totpCode(secret)
      val enable = api(HttpMethod.POST, "/api/v1/auth/mfa/setup", json("verificationCode" to code), token = newAccess)
      assert2xx(enable, "enable mfa")

      val login2 = `data`(api(HttpMethod.POST, "/api/v1/auth/login", json("username" to email, "password" to "Passw0rd!123")))
      val mfaToken = login2["mfaToken"].asText()
      assertNotNull(mfaToken)
      val verify = api(HttpMethod.POST, "/api/v1/auth/mfa/verify", json("mfaToken" to mfaToken, "code" to totpCode(secret)))
      assert2xx(verify, "mfa verify")

      val logout = api(HttpMethod.POST, "/api/v1/auth/logout", json("accessToken" to newAccess), token = newAccess)
      assert2xx(logout, "logout blacklists session")
      assertEquals(401, api(HttpMethod.GET, "/api/v1/auth/me", token = newAccess).status, "blacklisted token rejected")

      val wsToken = adminToken()
      val handlerMessages = java.util.concurrent.CompletableFuture<String>()
      val handler =
        object : org.springframework.web.socket.handler.TextWebSocketHandler() {
          override fun handleTextMessage(
            session: org.springframework.web.socket.WebSocketSession,
            message: org.springframework.web.socket.TextMessage,
          ) {
            handlerMessages.complete(message.payload)
          }
        }
      val wsClient =
        org.springframework.web.socket.client.standard
          .StandardWebSocketClient()
      val session =
        wsClient
          .execute(
            handler,
            "ws://localhost:$port/ws?token=$wsToken",
          ).get(15, java.util.concurrent.TimeUnit.SECONDS)
      val welcome = handlerMessages.get(10, java.util.concurrent.TimeUnit.SECONDS)
      assertTrue(welcome.contains("\"type\":\"connected\""), "ws welcome frame: $welcome")
      session.close()

      val anonymousWs =
        org.springframework.web.socket.client.standard
          .StandardWebSocketClient()
      val rejected =
        anonymousWs.execute(
          object : org.springframework.web.socket.handler.TextWebSocketHandler() {},
          "ws://localhost:$port/ws",
        )
      val anonSession =
        try {
          rejected.get(10, java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: java.util.concurrent.ExecutionException) {
          null
        }
      if (anonSession != null) {
        val deadline = System.currentTimeMillis() + 5000
        while (anonSession.isOpen && System.currentTimeMillis() < deadline) {
          Thread.sleep(200)
        }
        assertFalse(anonSession.isOpen, "anonymous ws must be closed with policy violation")
      }
    }

    @org.springframework.beans.factory.annotation.Autowired
    private lateinit var msDrugRepository: com.squamed.core.masterdata.repository.DrugRepository

    @org.springframework.beans.factory.annotation.Autowired
    private lateinit var msInteractionRuleRepository: com.squamed.core.medsafety.repository.InteractionRuleRepository

    @org.springframework.beans.factory.annotation.Autowired
    private lateinit var msDoseRuleRepository: com.squamed.core.medsafety.repository.DoseRuleRepository

    @org.springframework.beans.factory.annotation.Autowired
    private lateinit var msAllergenMapRepository: com.squamed.core.medsafety.repository.AllergenClassMapRepository

    @org.springframework.beans.factory.annotation.Autowired
    private lateinit var msVitalSignRepository: com.squamed.core.encounter.repository.VitalSignRepository

    @Test
    fun medicationSafetyEvalCoverage() {
      val token = adminToken()
      val s = suffix()
      val hospitalId = ensureSeed(token)
      val dr = msDrugRepository
      val irr = msInteractionRuleRepository
      val drr = msDoseRuleRepository
      val amr = msAllergenMapRepository
      val vsr = msVitalSignRepository

      // 26 Indian brand-name drugs (one molecule family each); eval samples the first 200 of C(26,2).
      data class Brand(
        val code: String,
        val name: String,
        val generic: String,
        val ingredients: String?,
      )

      val brands =
        listOf(
          Brand("CRO", "Crocin", "paracetamol", "paracetamol"),
          Brand("BRUF", "Brufen", "ibuprofen", "ibuprofen"),
          Brand("VOV", "Voveran", "diclofenac", "diclofenac"),
          Brand("ECOSP", "Ecosprin", "aspirin", "aspirin"),
          Brand("WARF", "Warf", "warfarin", "warfarin"),
          Brand("DEPL", "Deplatt", "clopidogrel", "clopidogrel"),
          Brand("LOS", "Losec", "omeprazole", "omeprazole"),
          Brand("PAN", "Pantocid", "pantoprazole", "pantoprazole"),
          Brand("METL", "Metlong", "metformin", "metformin"),
          Brand("AMAR", "Amaryl", "glimepiride", "glimepiride"),
          Brand("GEM", "Gemer", "metformin + glimepiride", "metformin, glimepiride"),
          Brand("SEP", "Septran", "sulfamethoxazole + trimethoprim", "sulfamethoxazole, trimethoprim"),
          Brand("AUG", "Augmentin", "amoxicillin + clavulanic acid", "amoxicillin, clavulanic acid"),
          Brand("MONO", "Monocef", "ceftriaxone", "ceftriaxone"),
          Brand("AZEE", "Azee", "azithromycin", "azithromycin"),
          Brand("CETZ", "Cetzin", "cetirizine", "cetirizine"),
          Brand("TEL", "Telma", "telmisartan", "telmisartan"),
          Brand("AMLO", "Amlopres", "amlodipine", "amlodipine"),
          Brand("METO", "Metolar", "metoprolol", "metoprolol"),
          Brand("LAS", "Lasix", "furosemide", "furosemide"),
          Brand("ATOR", "Atorva", "atorvastatin", "atorvastatin"),
          Brand("THYRO", "Thyronorm", "levothyroxine", "levothyroxine"),
          Brand("ALPR", "Alprax", "alprazolam", "alprazolam"),
          Brand("DOM", "Domstal", "domperidone", "domperidone"),
          Brand("EMES", "Emeset", "ondansetron", "ondansetron"),
        )
      val idByCode = HashMap<String, String>()
      for (b in brands) {
        val d =
          dr.save(
            com.squamed.core.masterdata.domain.Drug(
              code = "${b.code}-$s",
              name = b.name,
              genericName = b.generic,
              brandName = b.name,
              category = "General",
              unit = "tab",
              ingredients = b.ingredients,
            ),
          )
        idByCode["${b.code}-$s"] = d.id!!.toString()
      }

      fun code(b: String) = "$b-$s"

      // Dual-source interaction rules with provenance + confidence (§5 risk 2).
      data class IRule(
        val a: String,
        val b: String,
        val sev: String,
        val conf: Double,
        val src: String,
        val desc: String,
      )

      val rules =
        listOf(
          IRule("WARF", "ECOSP", "BLOCK", 0.98, "DATASET", "Warfarin + aspirin: major bleeding risk"),
          IRule("WARF", "ECOSP", "BLOCK", 0.95, "RULE", "Warfarin + aspirin rule-engine flag"),
          IRule("WARF", "BRUF", "BLOCK", 0.80, "RULE", "Warfarin + NSAID bleeding risk (low confidence)"),
          IRule("LOS", "DEPL", "BLOCK", 0.96, "DATASET", "Omeprazole reduces clopidogrel activation"),
          IRule("ECOSP", "BRUF", "WARN", 0.95, "RULE", "NSAID blunts aspirin antiplatelet effect"),
          IRule("METL", "AMAR", "WARN", 0.90, "RULE", "Sulfonylurea + biguanide hypoglycemia risk"),
          IRule("CRO", "WARF", "INFO", 0.99, "DATASET", "Paracetamol + warfarin: minor, INR monitoring"),
          IRule("SEP", "WARF", "WARN", 0.97, "DATASET", "Sulfamethoxazole potentiates warfarin"),
          IRule("SEP", "WARF", "WARN", 0.92, "RULE", "CYP2C9 inhibition rule"),
          IRule("ECOSP", "DEPL", "WARN", 0.98, "DATASET", "Dual antiplatelet bleeding risk"),
          IRule("BRUF", "TEL", "WARN", 0.90, "RULE", "NSAID reduces ARB effect"),
          IRule("VOV", "WARF", "BLOCK", 0.85, "DATASET", "Diclofenac + warfarin GI bleed risk"),
        )
      for (r in rules) {
        irr.save(
          com.squamed.core.medsafety.domain.InteractionRule(
            drugACode = code(r.a),
            drugBCode = code(r.b),
            severity = r.sev,
            confidence = r.conf,
            source = r.src,
            description = r.desc,
          ),
        )
      }
      drr.save(
        com.squamed.core.medsafety.domain.DoseRule(
          drugCode = code("CRO"),
          maxDailyMgPerKg = 50.0,
          adultMaxDailyMg = 4000.0,
        ),
      )
      drr.save(
        com.squamed.core.medsafety.domain.DoseRule(
          drugCode = code("METL"),
          adultMaxDailyMg = 2550.0,
          renalFlag = true,
          renalEgfrThreshold = 30.0,
        ),
      )
      amr.save(
        com.squamed.core.medsafety.domain
          .AllergenClassMap("penicillin", "amoxicillin"),
      )
      amr.save(
        com.squamed.core.medsafety.domain
          .AllergenClassMap("penicillin", "ampicillin"),
      )
      amr.save(
        com.squamed.core.medsafety.domain
          .AllergenClassMap("penicillin", "cloxacillin"),
      )
      amr.save(
        com.squamed.core.medsafety.domain
          .AllergenClassMap("sulfa", "sulfamethoxazole"),
      )

      fun newPatient(
        first: String,
        tag: String,
        dob: String,
      ): Pair<String, String> {
        val p =
          `data`(
            api(
              HttpMethod.POST,
              "/api/v1/patients",
              json(
                "firstName" to first,
                "lastName" to "Safety$s",
                "gender" to "female",
                "dateOfBirth" to dob,
                "phone" to "77$tag$s".take(11),
                "qid" to "MS$tag$s".take(11),
                "nationality" to "QA",
                "city" to "Doha",
              ),
              token,
            ),
          )["patient"]
        return p["id"].asText() to p["mrn"].asText()
      }

      fun attest(
        id: String,
        allergen: String,
        severity: String,
      ) {
        assert2xx(api(HttpMethod.POST, "/api/v1/patients/$id/allergies", json("allergen" to allergen, "severity" to severity), token), "allergy $allergen")
      }

      val pairId = newPatient("Pair", "1", "1990-01-01").first
      val pedsId = newPatient("Ped", "2", "2019-05-01").first
      val (dupId, dupMrn) = newPatient("Dup", "3", "1980-02-02")
      attest(pairId, "NO KNOWN ALLERGIES", "none")
      attest(pairId, "penicillin", "block")
      attest(pairId, "sulfa drugs", "warn")
      attest(pedsId, "NO KNOWN ALLERGIES", "none")
      attest(dupId, "NO KNOWN ALLERGIES", "none")

      fun check(
        patientId: String,
        drugIds: List<String>,
        doseMgPerDay: Double? = null,
      ): JsonNode =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/medication-safety/check",
            json("patientId" to patientId, "drugIds" to drugIds, "doseMgPerDay" to doseMgPerDay),
            token,
          ),
        )

      fun sevRank(v: String) = mapOf("NONE" to 0, "INFO" to 1, "WARN" to 2, "BLOCK" to 3).getValue(v)

      fun maxInteraction(node: JsonNode): String {
        val fs = node["findings"]
        var best = "NONE"
        for (k in 0 until fs.size()) {
          val f = fs[k]
          if (f["type"].asText() == "INTERACTION" && sevRank(f["severity"].asText()) > sevRank(best)) best = f["severity"].asText()
        }
        return best
      }

      fun hasFinding(
        node: JsonNode,
        type: String,
        severity: String,
      ): Boolean {
        val fs = node["findings"]
        for (k in 0 until fs.size()) {
          if (fs[k]["type"].asText() == type && fs[k]["severity"].asText() == severity) return true
        }
        return false
      }
      // EVAL: first 200 of the row-major pairs over sorted codes; expected severity from the
      // authored truth map (BLOCK only >= 0.90 confidence, else WARN).
      val sortedCodes = brands.map { code(it.code) }.sorted()
      val allPairs = ArrayList<Pair<String, String>>()
      for (i in sortedCodes.indices) for (j in i + 1 until sortedCodes.size) allPairs.add(sortedCodes[i] to sortedCodes[j])
      val evalPairs = allPairs.take(200)
      val truth =
        mapOf(
          listOf(code("WARF"), code("ECOSP")).sorted().joinToString("|") to "BLOCK",
          listOf(code("WARF"), code("BRUF")).sorted().joinToString("|") to "WARN", // BLOCK 0.80 < 0.90 -> WARN
          listOf(code("LOS"), code("DEPL")).sorted().joinToString("|") to "BLOCK",
          listOf(code("ECOSP"), code("BRUF")).sorted().joinToString("|") to "WARN",
          listOf(code("METL"), code("AMAR")).sorted().joinToString("|") to "WARN",
          listOf(code("CRO"), code("WARF")).sorted().joinToString("|") to "INFO",
          listOf(code("SEP"), code("WARF")).sorted().joinToString("|") to "WARN",
          listOf(code("ECOSP"), code("DEPL")).sorted().joinToString("|") to "WARN",
          listOf(code("BRUF"), code("TEL")).sorted().joinToString("|") to "WARN",
          listOf(code("VOV"), code("WARF")).sorted().joinToString("|") to "WARN", // BLOCK 0.85 -> WARN
        )
      var matches = 0
      var falseBlocks = 0
      for ((a, b) in evalPairs) {
        val expected = truth[listOf(a, b).sorted().joinToString("|")] ?: "NONE"
        val actual = maxInteraction(check(pairId, listOf(idByCode[a]!!, idByCode[b]!!)))
        if (actual == expected) matches++
        if (actual == "BLOCK" && expected != "BLOCK") falseBlocks++
      }
      assertEquals(200, evalPairs.size, "eval pair count")
      assertTrue(matches >= 196, "eval accuracy $matches/200 must be >= 98%")
      assertEquals(0, falseBlocks, "0 false-BLOCK required; got $falseBlocks")

      // Provenance + confidence: dual-source merge keeps max confidence and joins sources.
      val dual = check(pairId, listOf(idByCode[code("WARF")]!!, idByCode[code("ECOSP")]!!))["findings"]
      var sawDual = false
      for (k in 0 until dual.size()) {
        val f = dual[k]
        if (f["type"].asText() == "INTERACTION") {
          sawDual = true
          assertEquals("BLOCK", f["severity"].asText(), "high-confidence dual-source stays BLOCK")
          assertEquals("DATASET+RULE", f["source"].asText(), "sources merged with provenance")
          assertEquals(0.98, f["confidence"].asDouble(), 1e-9, "confidence = max across sources")
        }
      }
      assertTrue(sawDual, "warfarin+aspirin interaction present")
      // Threshold downgrade: BLOCK 0.80 fires as WARN, never as BLOCK.
      val downgraded = check(pairId, listOf(idByCode[code("WARF")]!!, idByCode[code("BRUF")]!!))["findings"]
      var sawDowngraded = false
      for (k in 0 until downgraded.size()) {
        val f = downgraded[k]
        if (f["type"].asText() == "INTERACTION") {
          sawDowngraded = true
          assertEquals("WARN", f["severity"].asText(), "low-confidence BLOCK downgraded: ${f["severity"].asText()}")
          assertEquals(0.80, f["confidence"].asDouble(), 1e-9, "confidence preserved")
        }
      }
      assertTrue(sawDowngraded, "downgrade case present")
      // Allergy: class-map expansion + severity arbitration (block -> BLOCK, warn -> WARN).
      assertTrue(hasFinding(check(pairId, listOf(idByCode[code("AUG")]!!)), "ALLERGY", "BLOCK"), "penicillin class map catches Augmentin as BLOCK")
      assertTrue(hasFinding(check(pairId, listOf(idByCode[code("SEP")]!!)), "ALLERGY", "WARN"), "sulfa warn catches Septran as WARN")
      assertEquals(0, check(pairId, listOf(idByCode[code("CRO")]!!))["findings"].size(), "no findings for a clean drug")

      // Max dose: peds weight-based cap (20 kg x 50 mg/kg = 1000) and adult fallback cap.
      vsr.save(
        com.squamed.core.encounter.domain.VitalSign(
          patientId = java.util.UUID.fromString(pedsId),
          recordedAt = Instant.now(),
          weightKg = java.math.BigDecimal("20.0"),
        ),
      )
      assertTrue(hasFinding(check(pedsId, listOf(idByCode[code("CRO")]!!), 1500.0), "MAX_DOSE", "WARN"), "1500 mg/day exceeds 1000 mg peds cap")
      assertFalse(hasFinding(check(pedsId, listOf(idByCode[code("CRO")]!!), 800.0), "MAX_DOSE", "WARN"), "800 mg/day within peds cap")
      assertTrue(hasFinding(check(pairId, listOf(idByCode[code("CRO")]!!), 5000.0), "MAX_DOSE", "WARN"), "5000 mg/day exceeds 4000 mg adult cap")

      // Renal: released eGFR below the drug's threshold flags renal dose adjustment.
      val labOrder =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/lab/orders",
            json("patientId" to pedsId, "tests" to listOf(mapOf("testCode" to "eGFR", "testName" to "eGFR $s"))),
            token,
          ),
        )
      val labOrderId = labOrder["order"]["id"].asText()
      val labReqId = `data`(api(HttpMethod.GET, "/api/v1/lab/orders/$labOrderId", token = token))["tests"][0]["id"].asText()
      assert2xx(api(HttpMethod.POST, "/api/v1/lab/orders/$labOrderId/collect", json(), token), "collect eGFR")
      assert2xx(api(HttpMethod.POST, "/api/v1/lab/orders/$labOrderId/receive", json(), token), "receive eGFR")
      assert2xx(api(HttpMethod.POST, "/api/v1/lab/orders/$labOrderId/start", json(), token), "start eGFR")
      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/lab/orders/$labOrderId/results",
          json("results" to listOf(mapOf("testRequestId" to labReqId, "resultValue" to "28", "unit" to "mL/min/1.73m2"))),
          token,
        ),
        "enter eGFR",
      )
      assert2xx(api(HttpMethod.POST, "/api/v1/lab/orders/$labOrderId/verify", json(), token), "verify eGFR")
      assert2xx(api(HttpMethod.POST, "/api/v1/lab/orders/$labOrderId/release", json(), token), "release eGFR")
      assertTrue(hasFinding(check(pedsId, listOf(idByCode[code("METL")]!!)), "RENAL", "WARN"), "eGFR 28 < 30 triggers renal flag")
      // Duplicate therapy: Metlong (metformin) active -> Gemer shares the ingredient.
      val dupEnc =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/encounters",
            json("patientId" to dupId, "chiefComplaint" to "polypharmacy review", "reason" to "02.07 safety coverage"),
            token,
          ),
        )["id"].asText()

      fun prescribeRx(extra: List<Pair<String, Any?>>) =
        api(
          HttpMethod.POST,
          "/api/v1/encounters/$dupEnc/prescriptions",
          json(*(listOf("patientMrn" to dupMrn) + extra).toTypedArray()),
          token,
        )
      assert2xx(prescribeRx(listOf("drugId" to idByCode[code("METL")], "drugName" to "Metlong")), "prescribe Metlong")
      assertTrue(hasFinding(check(dupId, listOf(idByCode[code("GEM")]!!)), "DUPLICATE", "WARN"), "Gemer duplicates active Metlong")

      // Prescribe gate: warfarin vs the active Ecosprin rx is a BLOCK and needs a typed reason.
      assert2xx(prescribeRx(listOf("drugId" to idByCode[code("ECOSP")], "drugName" to "Ecosprin")), "prescribe Ecosprin")
      val warfBlocked = prescribeRx(listOf("drugId" to idByCode[code("WARF")], "drugName" to "Warf"))
      assert4xx(warfBlocked, "prescribe warfarin blocked without reason")
      assertTrue(warfBlocked.body.contains("MEDICATION_SAFETY_BLOCK"), "prescribe guard code: ${warfBlocked.body.take(200)}")
      val warfRx =
        `data`(
          prescribeRx(
            listOf("drugId" to idByCode[code("WARF")], "drugName" to "Warf", "overrideReason" to "Cardiologist-directed dual therapy"),
          ),
        )["id"].asText()

      // Dispense gate: FEFO picks the seeded Warf batch; BLOCK needs an audited override reason.
      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/pharmacy/stock",
          json(
            "drugId" to idByCode[code("WARF")],
            "drugName" to "Warf",
            "batchNumber" to "W-$s",
            "quantityOnHand" to 50,
            "reorderLevel" to 5,
            "unitPrice" to 12.5,
            "location" to "B-1",
          ),
          token,
        ),
        "seed Warf stock",
      )

      fun dispenseWarf(reason: String?) =
        api(
          HttpMethod.POST,
          "/api/v1/pharmacy/dispensations",
          json(
            "prescriptionId" to warfRx,
            "patientId" to dupId,
            "drugId" to idByCode[code("WARF")],
            "drugName" to "Warf",
            "quantity" to 10,
            "safetyOverrideReason" to reason,
          ),
          token,
        )
      val dNoReason = dispenseWarf(null)
      assert4xx(dNoReason, "dispense blocked without reason")
      assertTrue(dNoReason.body.contains("MEDICATION_SAFETY_BLOCK"), "dispense guard code: ${dNoReason.body.take(200)}")
      val dBlank = dispenseWarf("   ")
      assert4xx(dBlank, "dispense blocked with blank reason")
      assertTrue(dBlank.body.contains("MEDICATION_SAFETY_BLOCK"), "blank-reason guard code: ${dBlank.body.take(200)}")
      assert2xx(dispenseWarf("Cardiologist-directed dual therapy"), "dispense warfarin with audited override")

      // Override API: blank reason rejected outright; KPIs group overrides by type+reason.
      val overrideBlank =
        api(
          HttpMethod.POST,
          "/api/v1/medication-safety/overrides",
          json("patientId" to dupId, "drugCode" to code("WARF"), "findingType" to "INTERACTION", "severity" to "WARN", "reason" to "  "),
          token,
        )
      assert4xx(overrideBlank, "override without reason rejected")
      assertTrue(overrideBlank.body.contains("OVERRIDE_REASON_REQUIRED"), "override guard code: ${overrideBlank.body.take(200)}")
      val kpis = `data`(api(HttpMethod.GET, "/api/v1/medication-safety/override-kpis?days=30", token = token))
      assertTrue(kpis.size() > 0, "override KPI rows present")
      var sawInteractionKpi = false
      for (k in 0 until kpis.size()) {
        val row = kpis[k]
        if (row["findingType"].asText() == "INTERACTION" && row["count"].asLong() >= 1) sawInteractionKpi = true
      }
      assertTrue(sawInteractionKpi, "interaction override KPI row present")
    }

    @Test
    fun chargeEventsAndInventoryCoverage() {
      val token = adminToken()
      val s = suffix()
      val hospitalId = ensureSeed(token)
      val dr = msDrugRepository

      // Hospital-scoped pharmacist for the reorder-proposal surface (admin principal has no hospital pin).
      val pharmEmail = "pharm.$s@squamed.local"
      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/auth/register",
          json(
            "email" to pharmEmail,
            "password" to "Passw0rd!123",
            "firstName" to "Phar",
            "lastName" to "Stock$s",
            "roles" to listOf("PHARMACIST"),
            "hospitalId" to hospitalId,
          ),
        ),
        "register pharmacist",
      )
      val pharm =
        `data`(api(HttpMethod.POST, "/api/v1/auth/login", json("username" to pharmEmail, "password" to "Passw0rd!123")))["accessToken"].asText()

      val patient =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/patients",
            json(
              "firstName" to "Charge",
              "lastName" to "Coverage$s",
              "gender" to "female",
              "dateOfBirth" to "1990-01-01",
              "phone" to "76$s".take(11),
              "qid" to "CH$s".take(11),
              "nationality" to "QA",
              "city" to "Doha",
            ),
            token,
          ),
        )["patient"]
      val patientId = patient["id"].asText()
      val mrn = patient["mrn"].asText()
      assert2xx(
        api(HttpMethod.POST, "/api/v1/patients/$patientId/allergies", json("allergen" to "NKA", "severity" to "none"), token),
        "document NKA allergy status",
      )
      val enc =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/encounters",
            json("patientId" to patientId, "chiefComplaint" to "charge coverage", "reason" to "02.08 coverage"),
            token,
          ),
        )["id"].asText()

      fun drug(tag: String): String =
        dr
          .save(
            com.squamed.core.masterdata.domain.Drug(
              code = "CHG$tag-$s",
              name = "Charge$tag",
              genericName = "charge$tag",
              category = "General",
              unit = "tab",
            ),
          ).id!!
          .toString()
      val drugA = drug("A")
      val expiry =
        java.time.LocalDate
          .now()
          .plusDays(120)

      fun stock(
        drugId: String,
        drugName: String,
        qty: Int,
        reorder: Int,
      ) = `data`(
        api(
          HttpMethod.POST,
          "/api/v1/pharmacy/stock",
          json(
            "drugId" to drugId,
            "drugName" to drugName,
            "batchNumber" to "CHG-$s",
            "expiryDate" to expiry.toString(),
            "quantityOnHand" to qty,
            "reorderLevel" to reorder,
            "unitPrice" to 12.5,
            "location" to "B-1",
          ),
          token,
        ),
      )
      val stockA = stock(drugA, "ChargeA", 50, 5)
      val stockAId = stockA["id"].asText()
      assertEquals(expiry.toString(), stockA["expiryDate"].asText(), "stock expiry seeded")

      // Prescribe 10, dispense 4 (partial) -> eMAR-ready dispensation with batch + expiry snapshot.
      val rxResp =
        api(
          HttpMethod.POST,
          "/api/v1/encounters/$enc/prescriptions",
          json("drugId" to drugA, "drugName" to "ChargeA", "quantity" to 10, "patientMrn" to mrn),
          token,
        )
      assert2xx(rxResp, "prescribe ChargeA")
      val rx = om.readTree(rxResp.body)["data"]["id"].asText()

      fun dispense(qty: Int) =
        api(
          HttpMethod.POST,
          "/api/v1/pharmacy/dispensations",
          json(
            "prescriptionId" to rx,
            "patientId" to patientId,
            "encounterId" to enc,
            "drugId" to drugA,
            "stockId" to stockAId,
            "drugName" to "ChargeA",
            "quantity" to qty,
            "unitPrice" to 12.5,
          ),
          token,
        )
      val disp4 = `data`(dispense(4))
      assertEquals(expiry.toString(), disp4["expiryDate"].asText(), "dispensation snapshots batch expiry for eMAR")
      assertEquals("CHG-$s", disp4["batchNumber"].asText(), "dispensation carries the batch")
      val dispId = disp4["id"].asText()

      // Consumption -> stock ledger: DISPENSE -4 referencing the dispensation, balance 46.
      val ledger = `data`(api(HttpMethod.GET, "/api/v1/pharmacy/stock/ledger?stockId=$stockAId", token = token))
      val consume = ledger.firstOrNull { it["movement"].asText() == "DISPENSE" && it["referenceId"].asText() == dispId }
      assertNotNull(consume, "DISPENSE ledger row references the dispensation: $ledger")
      assertEquals(-4, consume!!["quantityDelta"].asInt(), "consumption delta")
      assertEquals(46, consume["balanceAfter"].asInt(), "ledger balance after dispense")

      // Over-dispense guard: partial dispenses sum to the prescribed quantity; one more is rejected.
      val over = dispense(7)
      assert4xx(over, "dispense past the prescribed quantity")
      assertTrue(over.body.contains("DISPENSE_EXCEEDS_PRESCRIBED"), "over-dispense guard code: ${over.body.take(200)}")
      assert2xx(dispense(6), "final partial dispense tops up to the prescribed quantity")
      val past = dispense(1)
      assert4xx(past, "dispense beyond a fully dispensed prescription")
      assertTrue(past.body.contains("DISPENSE_EXCEEDS_PRESCRIBED"), "fully-dispensed guard code: ${past.body.take(200)}")

      // Reorder proposals: min-level stock -> PROPOSED draft, no duplicates, human approve/reject.
      val stockB = stock(drug("B"), "ChargeB", 3, 10)
      val stockBId = stockB["id"].asText()
      val generated = `data`(api(HttpMethod.POST, "/api/v1/pharmacy/reorder-proposals/generate", json(), pharm))
      val propB = generated.firstOrNull { it["stockId"].asText() == stockBId }
      assertNotNull(propB, "min-level stock generates a proposal: $generated")
      assertEquals("PROPOSED", propB!!["status"].asText(), "proposal starts PROPOSED")
      assertEquals(17, propB["requestedQuantity"].asInt(), "top-up = 2 x reorder level - on hand")
      val again = `data`(api(HttpMethod.POST, "/api/v1/pharmacy/reorder-proposals/generate", json(), pharm))
      assertTrue(again.none { it["stockId"].asText() == stockBId }, "no duplicate proposal while one is open: $again")
      val approved =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/pharmacy/reorder-proposals/${propB["id"].asText()}/approve",
            json("quantity" to 25, "note" to "approved for PO draft"),
            pharm,
          ),
        )
      assertEquals("APPROVED", approved["status"].asText(), "human approval")
      assertEquals(25, approved["requestedQuantity"].asInt(), "approved quantity recorded")
      assertFalse(approved["decidedBy"].isNull, "approver recorded")
      val redecide =
        api(HttpMethod.POST, "/api/v1/pharmacy/reorder-proposals/${propB["id"].asText()}/approve", json("quantity" to 30), pharm)
      assert4xx(redecide, "decided proposal cannot be re-decided")
      assertTrue(redecide.body.contains("REORDER_PROPOSAL_ALREADY_DECIDED"), "re-decision guard code: ${redecide.body.take(200)}")

      val stockC = stock(drug("C"), "ChargeC", 0, 5)
      val stockCId = stockC["id"].asText()
      val generatedC = `data`(api(HttpMethod.POST, "/api/v1/pharmacy/reorder-proposals/generate", json(), pharm))
      val propC = generatedC.firstOrNull { it["stockId"].asText() == stockCId }
      assertNotNull(propC, "zero stock generates a proposal")
      val blankReject =
        api(HttpMethod.POST, "/api/v1/pharmacy/reorder-proposals/${propC!!["id"].asText()}/reject", json("note" to "  "), pharm)
      assert4xx(blankReject, "rejection without a reason")
      assertTrue(blankReject.body.contains("REORDER_REJECTION_REASON_REQUIRED"), "rejection reason guard code: ${blankReject.body.take(200)}")
      val rejected =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/pharmacy/reorder-proposals/${propC["id"].asText()}/reject",
            json("note" to "supplier on backorder"),
            pharm,
          ),
        )
      assertEquals("REJECTED", rejected["status"].asText(), "human rejection")
      assertEquals("supplier on backorder", rejected["note"].asText(), "rejection reason recorded")

      // ChargeablePerformed: a released lab order is a performed billable service (chunk-3 consumes).
      val order =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/lab/orders",
            json("patientId" to patientId, "encounterId" to enc, "tests" to listOf(mapOf("testCode" to "CHG-$s", "testName" to "Charge Panel $s"))),
            token,
          ),
        )
      val orderId = order["order"]["id"].asText()
      val orderNumber = order["order"]["orderNumber"].asText()
      val testReqId = `data`(api(HttpMethod.GET, "/api/v1/lab/orders/$orderId", token = token))["tests"][0]["id"].asText()
      assert2xx(api(HttpMethod.POST, "/api/v1/lab/orders/$orderId/collect", json(), token), "collect charge panel")
      assert2xx(api(HttpMethod.POST, "/api/v1/lab/orders/$orderId/receive", json(), token), "receive charge panel")
      assert2xx(api(HttpMethod.POST, "/api/v1/lab/orders/$orderId/start", json(), token), "start charge panel")
      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/lab/orders/$orderId/results",
          json("results" to listOf(mapOf("testRequestId" to testReqId, "resultValue" to "5.4"))),
          token,
        ),
        "enter charge panel",
      )
      assert2xx(api(HttpMethod.POST, "/api/v1/lab/orders/$orderId/verify", json(), token), "verify charge panel")
      assert2xx(api(HttpMethod.POST, "/api/v1/lab/orders/$orderId/release", json(), token), "release charge panel")

      val outbox = `data`(api(HttpMethod.GET, "/api/v1/admin/events/outbox?limit=200", token = token))
      val charge =
        outbox.firstOrNull { it["eventType"].asText() == "billing.chargeable_performed" && it["envelope"].asText().contains(orderNumber) }
      assertNotNull(charge, "released lab order emits ChargeablePerformed: ${outbox.map { it["eventType"].asText() }.distinct()}")
      val chargeEnvelope = om.readTree(charge!!["envelope"].asText())
      assertEquals("LAB", chargeEnvelope["payload"]["chargeCategory"].asText(), "charge category stamped")
      assertEquals("CHG-$s", chargeEnvelope["payload"]["tests"][0]["testCode"].asText(), "performed tests carried")
      val catalog = `data`(api(HttpMethod.GET, "/api/v1/events/catalog", token = token))
      val entry = catalog.firstOrNull { it["name"].asText() == "ChargeablePerformed" }
      assertNotNull(entry, "catalog lists ChargeablePerformed")
      assertEquals("EMITTED", entry!!["status"].asText(), "catalog status EMITTED")
    }
  }

  /** Collects every text frame pushed to one realtime WebSocket session (shared by fabric tests). */
  private class RealtimeCollector : org.springframework.web.socket.handler.TextWebSocketHandler() {
    val messages = java.util.concurrent.ConcurrentLinkedQueue<String>()

    override fun handleTextMessage(
      session: org.springframework.web.socket.WebSocketSession,
      message: org.springframework.web.socket.TextMessage,
    ) {
      messages.add(message.payload)
    }
  }

  @Nested
  inner class EventFabricTests {
    @org.springframework.beans.factory.annotation.Autowired
    private lateinit var eventOutboxRepository: com.squamed.core.events.EventOutboxRepository

    @Test
    fun eventFabricCoverage() {
      val admin = adminToken()
      val adminId = `data`(api(HttpMethod.GET, "/api/v1/auth/me", token = admin))["userId"].asText()
      val s = suffix()
      val h1 =
        `data`(
          api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "Events H1 $s", "code" to "EV1-$s", "country" to "Qatar"), admin),
        )["id"].asText()
      val h2 =
        `data`(
          api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "Events H2 $s", "code" to "EV2-$s", "country" to "Qatar"), admin),
        )["id"].asText()

      // Doctors in two different hospitals.
      val u1Email = registerHospitalUser(h1)
      val u3Email = registerHospitalUser(h2)
      val u1Token = hospitalLogin(u1Email)
      val u3Token = hospitalLogin(u3Email)
      val u1Id = `data`(api(HttpMethod.GET, "/api/v1/auth/me", token = u1Token))["userId"].asText()
      val u3Id = `data`(api(HttpMethod.GET, "/api/v1/auth/me", token = u3Token))["userId"].asText()

      // WebSocket sessions via the /realtime alias: u1 (H1 token) and u3 (H2 token).
      val ws1 = connectRealtime(u1Token)
      val ws3 = connectRealtime(u3Token)

      // Task created in H1 assigned to u1 -> task.created frame reaches u1's H1 session.
      val taskId =
        `data`(
          api(HttpMethod.POST, "/api/v1/tasks", json("title" to "Events fabric $s", "hospitalId" to h1, "assigneeId" to u1Id), admin),
        )["id"].asText()
      val u1Frame = awaitEventFrame(ws1, "task.created", 30_000)
      assertTrue(u1Frame != null, "u1 must receive the task.created frame; tail=${ws1.messages.toList().takeLast(8)}")
      assertTrue(u1Frame!!.contains("\"entityId\":\"$taskId\""), "task.created frame carries the task id: $u1Frame")

      // Cross-hospital filter: assigning to u3 (H2) must NOT reach u3's H2 session.
      assert2xx(api(HttpMethod.POST, "/api/v1/tasks/$taskId/assign", json("assigneeId" to u3Id), admin), "assign task to u3")
      Thread.sleep(4000)
      assertTrue(
        ws3.messages.none { it.contains("task.assigned") },
        "cross-hospital task.assigned must not reach u3's H2 session: ${ws3.messages.toList().takeLast(8)}",
      )

      // Outbox rows carry the complete stamped envelope, validated against event-envelope-v1.
      val outboxRows = `data`(api(HttpMethod.GET, "/api/v1/admin/events/outbox?limit=200", token = admin))
      val created =
        outboxRows.firstOrNull { it["eventType"].asText() == "task.created" && it["envelope"].asText().contains(taskId) }
      assertNotNull(created, "task.created outbox row present")
      assertEquals("SENT", created!!["status"].asText(), "dispatcher drained the row; head=${outboxRows.take(4)}")
      val envelope = om.readTree(created["envelope"].asText())
      assertEquals(1, envelope["version"].asInt(), "envelope version")
      assertEquals("user", envelope["actorType"].asText(), "actor stamped from SecurityContext")
      assertEquals(adminId, envelope["actorId"].asText(), "actor id stamped from principal")
      assertFalse(envelope["correlationId"].isNull, "correlationId stamped from MDC")
      val registry =
        com.squamed.core.events
          .SchemaRegistry()
      assertTrue(
        registry.validate("event-envelope-v1.schema.json", envelope).isEmpty(),
        "outbox envelope validates against event-envelope-v1: ${registry.validate("event-envelope-v1.schema.json", envelope)}",
      )

      // DLQ: seed a FAILED row directly (test-fixture), replay it via the admin API, poll to SENT.
      val failedEventId = UUID.randomUUID()
      eventOutboxRepository.save(
        com.squamed.core.events.EventOutbox( // test-fixture
          eventId = failedEventId,
          eventType = "task.created",
          occurredAt = java.time.Instant.now(),
          source = "pit-fixture",
          entityType = "task",
          entityId = null,
          payload = om.valueToTree(mapOf("title" to "dlq row")),
          status = "FAILED",
          attempts = 8,
          maxAttempts = 8,
        ),
      )
      val replay =
        `data`(api(HttpMethod.POST, "/api/v1/admin/events/outbox/replay", json("eventId" to failedEventId.toString()), admin))
      assertEquals(1, replay["requeued"].asInt(), "DLQ row requeued: $replay")
      var replayedStatus = ""
      val sentDeadline = System.currentTimeMillis() + 30_000
      while (System.currentTimeMillis() < sentDeadline) {
        val rows = `data`(api(HttpMethod.GET, "/api/v1/admin/events/outbox?limit=200", token = admin))
        val row = rows.firstOrNull { it["eventId"].asText() == failedEventId.toString() }
        if (row != null && row["status"].asText() == "SENT") {
          replayedStatus = "SENT"
          break
        }
        Thread.sleep(500)
      }
      assertEquals("SENT", replayedStatus, "replayed DLQ row must reach SENT")

      // Integration error queue: malformed ADT (PID-3 missing) -> AE + recorded error -> replay fails again.
      val malformed =
        "MSH|^~\\&|SENDING|FAC|SQUAMED|CORE|20260101||ADT^A04|CTRL$s|P|2.5\rEVN|A04|20260101\rPID|||^^^NOID||Missing^Pid3||19800101|M"
      val hl7Resp = `data`(api(HttpMethod.POST, "/api/v1/hl7/messages", json("raw" to malformed), admin))
      assertEquals("AE", hl7Resp["ackCode"].asText(), "malformed ADT must ack AE: $hl7Resp")
      var errorRow: JsonNode? = null
      val errDeadline = System.currentTimeMillis() + 15_000
      while (System.currentTimeMillis() < errDeadline) {
        val rows = `data`(api(HttpMethod.GET, "/api/v1/admin/events/errors?integration=hl7&status=OPEN", token = admin))
        errorRow = rows.firstOrNull { it["error"].asText().contains("PID-3") }
        if (errorRow != null) break
        Thread.sleep(500)
      }
      assertNotNull(errorRow, "integration error recorded for the malformed ADT")
      assertEquals(1, errorRow!!["replayCount"].asInt(), "the original failure is attempt 1")
      val replayErr =
        `data`(api(HttpMethod.POST, "/api/v1/admin/events/errors/${errorRow["id"].asText()}/replay", json(), admin))
      assertEquals(2, replayErr["replayCount"].asInt(), "replay increments the attempt count: $replayErr")
      assertFalse(replayErr["ok"].asBoolean(), "replay still fails while PID-3 is missing")

      // Cursors advanced for both in-app consumers.
      val cursors = `data`(api(HttpMethod.GET, "/api/v1/admin/events/cursors", token = admin))
      val consumers = cursors.map { it["consumer"].asText() }
      assertTrue(consumers.contains("dispatcher"), "dispatcher cursor tracked: $consumers")
      assertTrue(consumers.contains("notification-bridge"), "notification-bridge cursor tracked: $consumers")

      // Event catalog: the chunk-00 EVENTS v1 set (13 types) + 01.01 appointment.* + 01.02 queue.* + 01.04 order + 01.07 announcement + 01.09 urgent arrival + 02.08 chargeable (24 total).
      val catalog = `data`(api(HttpMethod.GET, "/api/v1/events/catalog"))
      assertEquals(
        24,
        catalog.size(),
        "event catalog lists the v1 set + 01.01 appointment + 01.02 queue + 01.04 order + 01.07 announcement + 01.09 urgent-arrival + 02.08 chargeable events: $catalog",
      )
      assertTrue(catalog.any { it["name"].asText() == "AppointmentCreated" && it["status"].asText() == "EMITTED" })
      assertTrue(catalog.any { it["name"].asText() == "QueueCalled" && it["status"].asText() == "EMITTED" })
      assertTrue(catalog.any { it["name"].asText() == "AnnouncementEnqueued" && it["status"].asText() == "EMITTED" })
      assertTrue(catalog.any { it["name"].asText() == "NotificationDispatched" && it["status"].asText() == "EMITTED" })
      assertTrue(catalog.any { it["name"].asText() == "AuditChainBroken" && it["status"].asText() == "EMITTED" })
      assertTrue(catalog.any { it["name"].asText() == "TaskEscalated" && it["status"].asText() == "EMITTED" })
      assertTrue(catalog.any { it["name"].asText() == "ConsentCaptured" && it["status"].asText() == "EMITTED" })
      assertTrue(catalog.any { it["name"].asText() == "ConsentWithdrawn" && it["status"].asText() == "EMITTED" })
      assertTrue(catalog.any { it["name"].asText() == "EncounterOrderPlaced" && it["status"].asText() == "EMITTED" })

      assertEquals(403, api(HttpMethod.GET, "/api/v1/admin/events/outbox", token = u1Token).status, "doctor token denied event ops")
    }

    private fun registerHospitalUser(hospitalId: String): String {
      val s = suffix()
      val email = "events-$s@test.local"
      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/auth/register",
          json(
            "email" to email,
            "password" to "Passw0rd!123",
            "firstName" to "Doc",
            "lastName" to "Events$s",
            "roles" to listOf("DOCTOR"),
            "hospitalId" to hospitalId,
          ),
        ),
        "register events doctor $email",
      )
      return email
    }

    private fun hospitalLogin(email: String): String {
      val login = api(HttpMethod.POST, "/api/v1/auth/login", json("username" to email, "password" to "Passw0rd!123"))
      assert2xx(login, "login $email: ${login.body.take(200)}")
      return `data`(login)["accessToken"].asText()
    }

    private fun connectRealtime(token: String): RealtimeCollector {
      val collector = RealtimeCollector()
      org.springframework.web.socket.client.standard
        .StandardWebSocketClient()
        .execute(collector, "ws://localhost:$port/realtime?token=$token")
        .get(15, java.util.concurrent.TimeUnit.SECONDS)
      return collector
    }

    private fun awaitEventFrame(
      collector: RealtimeCollector,
      eventType: String,
      timeoutMs: Long,
    ): String? {
      val deadline = System.currentTimeMillis() + timeoutMs
      while (System.currentTimeMillis() < deadline) {
        collector.messages
          .firstOrNull { it.contains("\"type\":\"event.received\"") && it.contains(eventType) }
          ?.let { return it }
        Thread.sleep(250)
      }
      return null
    }
  }

  @Nested
  inner class AuditChainTests {
    @org.springframework.beans.factory.annotation.Autowired
    private lateinit var auditJdbc: org.springframework.jdbc.core.JdbcTemplate

    @Test
    fun auditChainCoverage() {
      val admin = adminToken()
      val adminId = `data`(api(HttpMethod.GET, "/api/v1/auth/me", token = admin))["userId"].asText()
      val s = suffix()
      val h1 =
        `data`(
          api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "Audit H1 $s", "code" to "AU1-$s", "country" to "Qatar"), admin),
        )["id"].asText()

      // Doctor + auditor in a fresh hospital (dev DB persists rows). The doctor logs in with a
      // distinct User-Agent so the session carries a registered device identity (00.10).
      val docEmail = "audit-doc-$s@test.local"
      val audEmail = "audit-aud-$s@test.local"
      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/auth/register",
          json(
            "email" to docEmail,
            "password" to "Passw0rd!123",
            "firstName" to "Doc",
            "lastName" to "Chain$s",
            "roles" to listOf("DOCTOR"),
            "hospitalId" to h1,
          ),
        ),
        "register audit doctor",
      )
      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/auth/register",
          json(
            "email" to audEmail,
            "password" to "Passw0rd!123",
            "firstName" to "Aud",
            "lastName" to "Itor$s",
            "roles" to listOf("READ_ONLY_VIEWER"),
            "hospitalId" to h1,
          ),
        ),
        "register auditor",
      )
      val ua = mapOf("User-Agent" to "SquamedAuditTest/1.0 ($s)")
      val docLogin = api(HttpMethod.POST, "/api/v1/auth/login", json("username" to docEmail, "password" to "Passw0rd!123"), null, ua)
      assert2xx(docLogin, "doctor login with device UA")
      val doctor = `data`(docLogin)["accessToken"].asText()
      val doctorId = `data`(api(HttpMethod.GET, "/api/v1/auth/me", token = doctor))["userId"].asText()
      val auditor =
        `data`(
          api(HttpMethod.POST, "/api/v1/auth/login", json("username" to audEmail, "password" to "Passw0rd!123")),
        )["accessToken"].asText()

      // Two writes -> two chained rows (create has no path id, update carries it).
      val oldPhone = "551$s".take(12)
      val newPhone = "552$s".take(12)
      val create =
        api(
          HttpMethod.POST,
          "/api/v1/patients",
          json(
            "firstName" to "Chain",
            "lastName" to "Patient$s",
            "gender" to "male",
            "dateOfBirth" to "1991-03-03",
            "phone" to oldPhone,
            "qid" to "3$s".take(11),
            "nationality" to "Qatar",
            "city" to "Doha",
          ),
          doctor,
          ua,
        )
      assert2xx(create, "patient create (audit chain)")
      val patId = `data`(create)["patient"]["id"].asText()
      assert2xx(api(HttpMethod.PUT, "/api/v1/patients/$patId", json("phone" to newPhone), doctor, ua), "patient update (audit before/after)")

      // ADR-15: chain linkage is asynchronous (AuditChainBatcher, 250 ms cadence) — poll until
      // both rows carry a chain_seq instead of assuming the append happened synchronously.
      var found: Pair<JsonNode, JsonNode>? = null
      val deadline = System.currentTimeMillis() + 15_000
      while (found == null && System.currentTimeMillis() < deadline) {
        val logs = `data`(api(HttpMethod.GET, "/api/v1/audit/logs?actor=$doctorId&limit=100", token = admin))
        val c =
          logs.firstOrNull { it["method"].asText() == "POST" && it["path"].asText() == "/api/v1/patients" && it["entityType"].asText().contains("Patient") }
        val u = logs.firstOrNull { it["method"].asText() == "PUT" && it["path"].asText() == "/api/v1/patients/$patId" }
        if (c != null && u != null && c.hasNonNull("chainSeq") && u.hasNonNull("chainSeq")) {
          found = c to u
        } else {
          Thread.sleep(250)
        }
      }
      val (createRow, updateRow) =
        found ?: error("batcher did not link both audit rows (create + update) within 15 s")
      // ADR-9 chain: fresh tenant -> create is seq 1 (no previous hash), update links to it.
      assertEquals(1L, createRow["chainSeq"].asLong(), "fresh tenant chain starts at seq 1")
      assertTrue(createRow["previousHash"].isNull, "first chained row has no previous hash")
      assertTrue(createRow["rowHash"].asText().matches(Regex("[0-9a-f]{64}")), "row hash is sha-256 hex: ${createRow["rowHash"].asText()}")
      assertEquals(2L, updateRow["chainSeq"].asLong())
      assertEquals(createRow["rowHash"].asText(), updateRow["previousHash"].asText(), "update row links to the create row hash")
      assertTrue(updateRow["beforeState"].asText().contains(oldPhone), "before state captured: ${updateRow["beforeState"].asText().take(120)}")
      assertTrue(updateRow["afterState"].asText().contains(newPhone), "after state captured")
      assertTrue(!createRow["deviceId"].isNull && createRow["deviceId"].asText().isNotEmpty(), "device id stamped from the login session")
      assertEquals(createRow["deviceId"].asText(), updateRow["deviceId"].asText(), "same device across both writes")

      // Chain verifies clean for THIS tenant (stale breaches from previous local runs on other
      // tenants are out of scope here).
      val verify = `data`(api(HttpMethod.POST, "/api/v1/audit/verify", json(), admin))
      assertTrue(verify["checked"].asInt() >= 2, "both chained rows verified: $verify")
      assertTrue(
        verify["breaches"].none { it["hospitalId"].asText() == h1 },
        "fresh tenant chain verifies clean: ${verify["breaches"]}",
      )

      // Permissions: auditor (READ_ONLY_VIEWER) reads + verifies but cannot export; doctor denied.
      assert2xx(api(HttpMethod.GET, "/api/v1/audit/logs?actor=$doctorId", token = auditor), "auditor reads audit logs")
      assert2xx(api(HttpMethod.POST, "/api/v1/audit/verify", json(), auditor), "auditor runs chain verify")
      assertEquals(403, api(HttpMethod.POST, "/api/v1/audit/export", json(), auditor).status, "auditor denied export")
      assertEquals(403, api(HttpMethod.GET, "/api/v1/audit/logs", token = doctor).status, "doctor denied audit read")

      // WORM export covers the fresh tenant rows and returns the object key.
      val export = `data`(api(HttpMethod.POST, "/api/v1/audit/export", json(), admin))
      assertTrue(export["exported"].asInt() >= 3, "export covers chained rows: $export")
      assertTrue(export["objectKey"].asText().contains("/"), "export object key: ${export["objectKey"].asText()}")

      // Tamper: overwrite a stored field -> verify reports HASH_MISMATCH for this tenant and an
      // audit.chain_broken event lands in the outbox (chunk-00 item 8 "audit alerts").
      val tamperRow =
        auditJdbc.queryForMap(
          "SELECT id, response_payload FROM audit.audit_log WHERE id = ?::uuid",
          createRow["id"].asText(),
        )
      val tamperId = tamperRow["id"].toString()
      val originalPayload = tamperRow["response_payload"].toString()
      auditJdbc.update("UPDATE audit.audit_log SET response_payload = ? WHERE id = ?::uuid", "TAMPERED-$s", tamperId)
      val breach = `data`(api(HttpMethod.POST, "/api/v1/audit/verify", json(), admin))
      val h1Breaches = breach["breaches"].filter { it["hospitalId"].asText() == h1 }
      assertTrue(
        h1Breaches.isNotEmpty() && h1Breaches.all { it["reason"].asText().contains("HASH_MISMATCH") },
        "tamper detected on the tenant chain: ${breach["breaches"]}",
      )
      var brokenRow: JsonNode? = null
      val brokenDeadline = System.currentTimeMillis() + 30_000
      while (System.currentTimeMillis() < brokenDeadline) {
        val rows = `data`(api(HttpMethod.GET, "/api/v1/admin/events/outbox?limit=200", token = admin))
        brokenRow = rows.firstOrNull { it["eventType"].asText() == "audit.chain_broken" && it["hospitalId"].asText() == h1 }
        if (brokenRow != null && brokenRow["status"].asText() == "SENT") break
        Thread.sleep(500)
      }
      assertNotNull(brokenRow, "audit.chain_broken published to the outbox")
      assertEquals("SENT", brokenRow!!["status"].asText(), "breach alert dispatched")

      // Restore the tampered row: the shared dev DB must keep a clean chain baseline
      // (the exit demo asserts gauge=0 before its own tamper; a left breach would break it).
      auditJdbc.update("UPDATE audit.audit_log SET response_payload = ? WHERE id = ?::uuid", originalPayload, tamperId)
      `data`(api(HttpMethod.POST, "/api/v1/audit/verify", json(), admin))
    }
  }

  @Nested
  inner class TaskEngineTests {
    @Test
    fun taskEngineCoverage() {
      val admin = adminToken()
      val s = suffix()

      // Fresh hospitals: the task workforce (doctor, now granted task:manage via V50) + an outsider.
      val h =
        `data`(
          api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "Tasks H $s", "code" to "TK1-$s", "country" to "Qatar"), admin),
        )["id"].asText()
      val hOut =
        `data`(
          api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "Tasks Out $s", "code" to "TK2-$s", "country" to "Qatar"), admin),
        )["id"].asText()

      fun doctorIn(hospitalId: String): Pair<String, String> {
        val email = "tasks-doc-$s-${UUID.randomUUID().toString().take(6)}@test.local"
        assert2xx(
          api(
            HttpMethod.POST,
            "/api/v1/auth/register",
            json(
              "email" to email,
              "password" to "Passw0rd!123",
              "firstName" to "Doc",
              "lastName" to "Tasks$s",
              "roles" to listOf("DOCTOR"),
              "hospitalId" to hospitalId,
            ),
          ),
          "register tasks doctor",
        )
        val token =
          `data`(api(HttpMethod.POST, "/api/v1/auth/login", json("username" to email, "password" to "Passw0rd!123")))["accessToken"].asText()
        val userId = `data`(api(HttpMethod.GET, "/api/v1/auth/me", token = token))["userId"].asText()
        return token to userId
      }

      val (doctor, doctorId) = doctorIn(h)
      val (outsider, _) = doctorIn(hOut)

      // Subject to attach: a real patient + a department placeholder id.
      val patient =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/patients",
            json(
              "firstName" to "Task",
              "lastName" to "Subject$s",
              "gender" to "female",
              "dateOfBirth" to "1988-09-09",
              "phone" to "559$s".take(12),
              "qid" to "8$s".take(11),
              "nationality" to "Qatar",
              "city" to "Doha",
            ),
            doctor,
          ),
        )["patient"]["id"].asText()
      val departmentId = UUID.randomUUID()

      // Create with subject links (chunk item 9): patient + department; invalid type rejected.
      val created =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/tasks",
            json(
              "title" to "Verify merged record $s",
              "priority" to "urgent",
              "assigneeId" to doctorId,
              "dueAt" to Instant.now().minusSeconds(3600).toString(),
              "links" to
                listOf(
                  mapOf("subjectType" to "patient", "subjectId" to patient),
                  mapOf("subjectType" to "department", "subjectId" to departmentId.toString()),
                ),
            ),
            doctor,
          ),
        )
      val taskId = created["id"].asText()
      assertEquals(2, created["links"].size(), "subject attachments stored: ${created["links"]}")
      assertEquals(
        400,
        api(
          HttpMethod.POST,
          "/api/v1/tasks",
          json("title" to "bad $s", "links" to listOf(mapOf("subjectType" to "banana", "subjectId" to patient))),
          doctor,
        ).status,
        "invalid subject type rejected",
      )

      // Lifecycle: assign -> execute -> escalate (manual, chunk API `:escalate`) -> complete.
      assert2xx(api(HttpMethod.POST, "/api/v1/tasks/$taskId/assign", json("assigneeId" to doctorId), doctor), "task assign")
      assert2xx(api(HttpMethod.PATCH, "/api/v1/tasks/$taskId/status", json("status" to "in_progress"), doctor), "task in_progress")
      val escalated = `data`(api(HttpMethod.POST, "/api/v1/tasks/$taskId/escalate", json("reason" to "second opinion"), doctor))
      assertEquals("escalated", escalated["status"].asText(), "manual escalation")
      assertEquals(1, escalated["escalationCount"].asInt(), "escalation counter")
      assertTrue(escalated["escalatedAt"].isNull != true, "escalatedAt stamped")

      var escalatedRow: JsonNode? = null
      val escDeadline = System.currentTimeMillis() + 30_000
      while (System.currentTimeMillis() < escDeadline) {
        val rows = `data`(api(HttpMethod.GET, "/api/v1/admin/events/outbox?limit=200", token = admin))
        escalatedRow = rows.firstOrNull { it["eventType"].asText() == "task.escalated" && it["hospitalId"].asText() == h }
        if (escalatedRow != null && escalatedRow["status"].asText() == "SENT") break
        Thread.sleep(500)
      }
      assertNotNull(escalatedRow, "task.escalated published to the outbox")
      assertEquals("SENT", escalatedRow!!["status"].asText(), "escalation notified")

      // Monitor visibility: the due-past task is overdue for its tenant.
      val overdue = `data`(api(HttpMethod.GET, "/api/v1/tasks/overdue", token = doctor))
      assertTrue(overdue.any { it["id"].asText() == taskId }, "overdue monitor lists the past-due task: $overdue")

      // Reverse subject lookup: work attached to the patient comes back with the link.
      val bySubject = `data`(api(HttpMethod.GET, "/api/v1/tasks/subject?subjectType=patient&subjectId=$patient", token = doctor))
      assertTrue(bySubject.any { it["id"].asText() == taskId }, "subject lookup returns the attached task")

      // Cross-tenant: the outsider hospital sees neither the task nor the inbox of h's doctor.
      assertEquals(400, api(HttpMethod.GET, "/api/v1/tasks/$taskId", token = outsider).status, "cross-hospital task read denied (not found)")
      assertTrue(`data`(api(HttpMethod.GET, "/api/v1/tasks?assigneeId=$doctorId", token = outsider)).isEmpty(), "cross-hospital inbox leak denied")

      // Complete the escalated task.
      val completed = `data`(api(HttpMethod.PATCH, "/api/v1/tasks/$taskId/status", json("status" to "completed"), doctor))
      assertEquals("completed", completed["status"].asText(), "escalated task completes")
      assertTrue(completed["completedAt"].isNull != true, "completedAt stamped")

      // SLA rules from config (item 9): urgent breaches instantly (slaHours 0) with DOCTOR as the
      // escalation role target; normal tasks stay under the 72h default. No dueAt -> created-age rule.
      assert2xx(
        api(
          HttpMethod.PUT,
          "/api/v1/hospital-config/hospitals/$h/config/task-sla",
          json("rules" to listOf(mapOf("priority" to "urgent", "slaHours" to 0, "escalateToRole" to "DOCTOR")), "defaultSlaHours" to 72),
          admin,
        ),
        "publish task-sla config",
      )
      val urgentId =
        `data`(
          api(HttpMethod.POST, "/api/v1/tasks", json("title" to "SLA urgent $s", "priority" to "urgent", "assigneeId" to doctorId, "hospitalId" to h), doctor),
        )["id"].asText()
      val normalId =
        `data`(
          api(HttpMethod.POST, "/api/v1/tasks", json("title" to "SLA normal $s", "priority" to "normal", "assigneeId" to doctorId, "hospitalId" to h), doctor),
        )["id"].asText()

      val scan = `data`(api(HttpMethod.POST, "/api/v1/tasks/sla/scan", json(), doctor))
      assertTrue(scan["escalated"].asInt() >= 1, "scan escalates breaches: $scan")
      assertEquals(
        "escalated",
        `data`(api(HttpMethod.GET, "/api/v1/tasks/$urgentId", token = doctor))["status"].asText(),
        "urgent task auto-escalated by SLA rule",
      )
      assertEquals("pending", `data`(api(HttpMethod.GET, "/api/v1/tasks/$normalId", token = doctor))["status"].asText(), "normal task under default SLA")

      // Already-escalated work is not re-escalated on the next pass.
      val scan2 = `data`(api(HttpMethod.POST, "/api/v1/tasks/sla/scan", json(), doctor))
      assertEquals(1, `data`(api(HttpMethod.GET, "/api/v1/tasks/$urgentId", token = doctor))["escalationCount"].asInt(), "no repeat escalation: $scan2")

      // Doctor (task:manage since V50) reads the inbox; the auditor role is out of scope here.
      assert2xx(api(HttpMethod.GET, "/api/v1/tasks?assigneeId=$doctorId", token = doctor), "doctor reads own inbox")
    }
  }

  @Nested
  inner class ConsentNotificationTests {
    @Test
    fun consentNotificationCoverage() {
      val admin = adminToken()
      val s = suffix()

      // Fresh hospitals: the consent workforce (doctor, granted via V51) + an outsider.
      val h =
        `data`(
          api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "Consent H $s", "code" to "CN1-$s", "country" to "Qatar"), admin),
        )["id"].asText()
      val hOut =
        `data`(
          api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "Consent Out $s", "code" to "CN2-$s", "country" to "Qatar"), admin),
        )["id"].asText()

      fun doctorIn(hospitalId: String): Pair<String, String> {
        val email = "consent-doc-$s-${UUID.randomUUID().toString().take(6)}@test.local"
        assert2xx(
          api(
            HttpMethod.POST,
            "/api/v1/auth/register",
            json(
              "email" to email,
              "password" to "Passw0rd!123",
              "firstName" to "Doc",
              "lastName" to "Consent$s",
              "roles" to listOf("DOCTOR"),
              "hospitalId" to hospitalId,
            ),
          ),
          "register consent doctor",
        )
        val token =
          `data`(api(HttpMethod.POST, "/api/v1/auth/login", json("username" to email, "password" to "Passw0rd!123")))["accessToken"].asText()
        val userId = `data`(api(HttpMethod.GET, "/api/v1/auth/me", token = token))["userId"].asText()
        return token to userId
      }

      fun pollSent(eventType: String) {
        var row: JsonNode? = null
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
          val rows = `data`(api(HttpMethod.GET, "/api/v1/admin/events/outbox?limit=200", token = admin))
          row = rows.firstOrNull { it["eventType"].asText() == eventType && it["hospitalId"].asText() == h }
          if (row != null && row["status"].asText() == "SENT") return
          Thread.sleep(500)
        }
        assertNotNull(row, "$eventType published to the outbox")
        assertEquals("SENT", row!!["status"].asText(), "$eventType relayed by the dispatcher")
      }

      val (doctor, doctorId) = doctorIn(h)
      val (outsider, _) = doctorIn(hOut)

      val patient =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/patients",
            json(
              "firstName" to "Consent",
              "lastName" to "Subject$s",
              "gender" to "male",
              "dateOfBirth" to "1990-02-02",
              "phone" to "560$s".take(12),
              "qid" to "9$s".take(11),
              "nationality" to "Qatar",
              "city" to "Doha",
            ),
            doctor,
          ),
        )["patient"]["id"].asText()

      // Capture a COMMUNICATIONS consent (item 11): eSign-ready signature + per-channel opt-ins.
      val consent =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/consents",
            json(
              "patientId" to patient,
              "purpose" to "communications",
              "scope" to "appointment reminders via sms/email",
              "signatureData" to "data:image/png;base64,iVBORw0KGgo=",
              "signedByName" to "Consent Subject$s",
              "channelOptIns" to
                listOf(
                  mapOf("channel" to "sms", "optedIn" to true),
                  mapOf("channel" to "email", "optedIn" to true),
                ),
            ),
            doctor,
          ),
        )
      val consentId = consent["id"].asText()
      assertEquals("granted", consent["status"].asText(), "captured consent granted")
      assertTrue(consent["signaturePresent"].asBoolean(), "signature captured (eSign-ready)")
      assertEquals(2, consent["channelOptIns"].size(), "per-channel opt-ins stored")
      assertEquals(
        400,
        api(HttpMethod.POST, "/api/v1/consents", json("patientId" to patient, "purpose" to "banana"), doctor).status,
        "invalid purpose rejected",
      )

      // consent.captured fans out through the fabric (00.09 outbox).
      pollSent("consent.captured")

      fun send(
        channel: String,
        title: String,
        extra: Map<String, Any?> = mapOf("recipientAddress" to "+974560000000"),
      ): String =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/notifications/send",
            json(
              "patientId" to patient,
              "channel" to channel,
              "type" to "appointment",
              "title" to title,
              "body" to "Visit update $s",
              *extra.entries.map { it.key to it.value!! }.toTypedArray(),
            ),
            doctor,
          ),
        )["status"].asText()

      // DND hours from config (item 12): publish a window that always covers "now" in UTC
      // (wraps midnight when the hour is 23), so the gate defers regardless of run time.
      val utcHour =
        java.time.OffsetDateTime
          .now(java.time.ZoneOffset.UTC)
          .hour
      assert2xx(
        api(
          HttpMethod.PUT,
          "/api/v1/hospital-config/hospitals/$h/config/dnd-hours",
          json("enabled" to true, "startHour" to utcHour, "endHour" to (utcHour + 1) % 24),
          admin,
        ),
        "publish dnd-hours config",
      )
      assertEquals("DEFERRED_DND", send("sms", "DND block $s"), "send inside the DND window defers")

      // DND off: providers are unconfigured in this environment -> honest skips (E2/E3 pending).
      assert2xx(
        api(HttpMethod.PUT, "/api/v1/hospital-config/hospitals/$h/config/dnd-hours", json("enabled" to false, "startHour" to 0, "endHour" to 0), admin),
        "disable dnd-hours config",
      )
      assertEquals("SKIPPED_NO_PROVIDER", send("sms", "No provider $s"), "unconfigured sms provider skips")
      assertEquals(
        "SKIPPED_NO_SMTP_CONFIG",
        send("email", "No smtp $s", mapOf("recipientAddress" to "consent-$s@test.local")),
        "no SMTP host -> honest skip",
      )

      // in_app dispatch writes the in-app row directly (the 00.09 event-driven path is untouched).
      val inApp =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/notifications/send",
            json(
              "patientId" to patient,
              "recipientId" to doctorId,
              "channel" to "in_app",
              "type" to "general",
              "title" to "In-app hello $s",
              "body" to "direct dispatch",
            ),
            doctor,
          ),
        )
      assertEquals("SENT", inApp["status"].asText(), "in_app dispatch sent")
      val inbox = `data`(api(HttpMethod.GET, "/api/v1/notifications", token = doctor))
      assertTrue(inbox.any { it["title"].asText() == "In-app hello $s" }, "in_app row lands in the doctor inbox")

      // Withdrawal (item 11): effective status flips, the channel gate closes, consent.withdrawn fans out.
      val withdrawn = `data`(api(HttpMethod.POST, "/api/v1/consents/$consentId/withdraw", json("reason" to "patient opted out"), doctor))
      assertEquals("withdrawn", withdrawn["status"].asText(), "withdrawal recorded")
      pollSent("consent.withdrawn")
      assertEquals("BLOCKED_NO_CONSENT", send("sms", "After withdraw $s"), "consent gate blocks after withdrawal")

      // Expiry is derived at read time (no sweep job): an already-past expiresAt reads as expired.
      val expired =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/consents",
            json(
              "patientId" to patient,
              "purpose" to "communications",
              "expiresAt" to Instant.now().minusSeconds(60).toString(),
              "channelOptIns" to listOf(mapOf("channel" to "sms", "optedIn" to true)),
            ),
            doctor,
          ),
        )
      assertEquals("expired", expired["status"].asText(), "expiry derived, not swept")
      assertEquals("BLOCKED_NO_CONSENT", send("sms", "After expiry $s"), "consent gate blocks expired consent")

      // Ledger: every outbound attempt recorded with its gate outcome (item 12).
      val ledger = `data`(api(HttpMethod.GET, "/api/v1/notifications/requests?patientId=$patient", token = doctor))
      val ledgerStatuses = ledger.map { it["status"].asText() }.toSet()
      assertTrue(
        ledgerStatuses.containsAll(setOf("SENT", "BLOCKED_NO_CONSENT", "DEFERRED_DND", "SKIPPED_NO_PROVIDER", "SKIPPED_NO_SMTP_CONFIG")),
        "ledger lists all gate outcomes: $ledgerStatuses",
      )

      // Cross-tenant: the outsider sees neither the consent, the list, nor the ledger (no leak).
      assertEquals(400, api(HttpMethod.GET, "/api/v1/consents/$consentId", token = outsider).status, "cross-hospital consent read denied")
      assertTrue(`data`(api(HttpMethod.GET, "/api/v1/consents?patientId=$patient", token = outsider)).isEmpty(), "cross-hospital consent list leak denied")
      assertTrue(
        `data`(api(HttpMethod.GET, "/api/v1/notifications/requests?patientId=$patient", token = outsider)).isEmpty(),
        "cross-hospital ledger leak denied",
      )

      // Catalog: consent.* EMITTED in the set (13 chunk-00 + 4 appointment.* 01.01 + 3 queue.* 01.02 + 1 order 01.04 + 1 announcement 01.07 + 1 urgent-arrival 01.09 + 1 chargeable 02.08).
      val catalog = `data`(api(HttpMethod.GET, "/api/v1/events/catalog"))
      assertEquals(
        24,
        catalog.size(),
        "event catalog lists the v1 set + 01.01 appointment + 01.02 queue + 01.04 order + 01.07 announcement + 01.09 urgent arrival + 02.08 chargeable: $catalog",
      )
      assertTrue(catalog.any { it["name"].asText() == "ConsentCaptured" && it["status"].asText() == "EMITTED" }, "consent.captured EMITTED")
      assertTrue(catalog.any { it["name"].asText() == "ConsentWithdrawn" && it["status"].asText() == "EMITTED" }, "consent.withdrawn EMITTED")
      assertTrue(catalog.any { it["name"].asText() == "QueueBumped" && it["status"].asText() == "EMITTED" }, "queue.bumped EMITTED")
    }
  }

  @Nested
  inner class OfflineIdempotencyTests {
    @Test
    fun offlineTaskCompletionReplayCoverage() {
      val admin = adminToken()
      val s = suffix()

      // Fresh hospital: the task workforce (doctor, task:manage since V50).
      val h =
        `data`(
          api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "Offline H $s", "code" to "OF1-$s", "country" to "Qatar"), admin),
        )["id"].asText()

      val email = "offline-doc-$s-${UUID.randomUUID().toString().take(6)}@test.local"
      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/auth/register",
          json(
            "email" to email,
            "password" to "Passw0rd!123",
            "firstName" to "Doc",
            "lastName" to "Offline$s",
            "roles" to listOf("DOCTOR"),
            "hospitalId" to h,
          ),
        ),
        "register offline doctor",
      )
      val doctor =
        `data`(api(HttpMethod.POST, "/api/v1/auth/login", json("username" to email, "password" to "Passw0rd!123")))["accessToken"].asText()
      val doctorId = `data`(api(HttpMethod.GET, "/api/v1/auth/me", token = doctor))["userId"].asText()

      val taskId =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/tasks",
            json("title" to "Offline replay $s", "priority" to "normal", "assigneeId" to doctorId, "hospitalId" to h),
            doctor,
          ),
        )["id"].asText()

      // The offline path: the completion is queued with a client-generated idempotency key,
      // then flushed once from the outbox and flushed again on the next connectivity tick.
      val body = json("status" to "completed")
      val key = "pit-offline-$s"
      val first = api(HttpMethod.PATCH, "/api/v1/tasks/$taskId/status", body, doctor, mapOf("Idempotency-Key" to key))
      assert2xx(first, "first flush completes the task")
      assertEquals("completed", `data`(first)["status"].asText(), "task completed")

      val replay = api(HttpMethod.PATCH, "/api/v1/tasks/$taskId/status", body, doctor, mapOf("Idempotency-Key" to key))
      assert2xx(replay, "queued replay lands")
      assertEquals(first.body, replay.body, "replay is byte-identical from the idempotency store")
      assertEquals(
        `data`(first)["completedAt"].asText(),
        `data`(replay)["completedAt"].asText(),
        "replay does not move completedAt (no second execution)",
      )

      // Exactly one task.completed event despite the replay (the outbox row is written
      // transactionally with the first flush, so it is already visible here).
      val events =
        `data`(api(HttpMethod.GET, "/api/v1/admin/events/outbox?limit=200", token = admin))
          .filter { it["eventType"].asText() == "task.completed" && it["hospitalId"].asText() == h }
      assertEquals(1, events.size, "task.completed emitted exactly once: ${events.map { it["status"].asText() }}")

      // Same key with a different payload -> 409 IDEMPOTENCY_CONFLICT (payload drift guard).
      val conflict = api(HttpMethod.PATCH, "/api/v1/tasks/$taskId/status", json("status" to "cancelled"), doctor, mapOf("Idempotency-Key" to key))
      assertEquals(409, conflict.status, "payload change under same key -> 409, got ${conflict.body.take(200)}")
      assertEquals("IDEMPOTENCY_CONFLICT", om.readTree(conflict.body)["error"]["code"].asText())
    }

    /** CS-08 sync chaos simulator (08-TEST-STRATEGY section 1): the offline queue replayed
     * under randomized drop/duplicate/reorder/partition must be exactly-once with the final
     * state identical to the canonical online path. Every retry carries the client's
     * idempotency key (the designed protocol), so the middleware absorbs duplicates and
     * reordering; a keyless stale write is caught by the terminal-state guard. */
    @Test
    fun offlineChaosReplayCoverage() {
      val admin = adminToken()
      val s = suffix()

      val h =
        `data`(
          api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "Chaos H $s", "code" to "CHS-$s", "country" to "Qatar"), admin),
        )["id"].asText()

      val email = "chaos-doc-$s-${UUID.randomUUID().toString().take(6)}@test.local"
      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/auth/register",
          json(
            "email" to email,
            "password" to "Passw0rd!123",
            "firstName" to "Doc",
            "lastName" to "Chaos$s",
            "roles" to listOf("DOCTOR"),
            "hospitalId" to h,
          ),
        ),
        "register chaos doctor",
      )
      val doctor =
        `data`(api(HttpMethod.POST, "/api/v1/auth/login", json("username" to email, "password" to "Passw0rd!123")))["accessToken"].asText()

      fun newTask(title: String): String =
        `data`(
          api(HttpMethod.POST, "/api/v1/tasks", json("title" to title, "priority" to "normal", "hospitalId" to h), doctor),
        )["id"].asText()

      fun taskStatus(taskId: String): String = `data`(api(HttpMethod.GET, "/api/v1/tasks/$taskId", token = doctor))["status"].asText()

      fun completedEvents(taskId: String): Int =
        `data`(api(HttpMethod.GET, "/api/v1/admin/events/outbox?limit=200", token = admin))
          .filter { it["eventType"].asText() == "task.completed" }
          .map { om.readTree(it["envelope"].asText()) }
          .count { it["entityId"].asText() == taskId }

      // 1. Canonical control: the same logical writes flushed in queue order, exactly once each.
      val control = newTask("Chaos control $s")
      api(HttpMethod.PATCH, "/api/v1/tasks/$control/status", json("status" to "in_progress"), doctor, mapOf("Idempotency-Key" to "ctl-a-$s"))
      api(HttpMethod.PATCH, "/api/v1/tasks/$control/status", json("status" to "completed"), doctor, mapOf("Idempotency-Key" to "ctl-b-$s"))
      assertEquals("completed", taskStatus(control), "control flushed in order completes")
      assertEquals(1, completedEvents(control), "control: task.completed exactly once")

      // 2. Chaos target: same queue under seeded drop/duplicate/reorder/partition. Some attempts
      //    are dropped (network loss) and retried late; duplicates re-send the same key; the
      //    batch arrives shuffled. Rejected stale attempts are expected conflict resolution, not
      //    failures - the property is the FINAL state, not per-attempt status codes.
      data class QueuedWrite(
        val status: String,
        val key: String,
      )

      for (seed in listOf(1701L, 42L, 99L)) {
        val target = newTask("Chaos target $seed $s")
        val canonical =
          listOf(
            QueuedWrite("in_progress", "chs-$s-$seed-a"),
            QueuedWrite("completed", "chs-$s-$seed-b"),
          )
        val rng = java.util.Random(seed)
        val attempts = mutableListOf<QueuedWrite>()
        for (w in canonical) {
          if (rng.nextBoolean()) attempts.add(w) // first attempt survives
          if (rng.nextBoolean()) attempts.add(w) // duplicate
          attempts.add(w) // guaranteed retry (partition: late arrival)
        }
        attempts.shuffle(rng)
        for (w in attempts) {
          api(HttpMethod.PATCH, "/api/v1/tasks/$target/status", json("status" to w.status), doctor, mapOf("Idempotency-Key" to w.key))
        }
        assertEquals("completed", taskStatus(target), "seed $seed: chaos final state equals canonical")
        assertEquals(1, completedEvents(target), "seed $seed: task.completed exactly once under chaos")
      }

      // 3. Defense-in-depth: a stale write WITHOUT its key (client bug) cannot regress a
      //    completed task - the terminal-state guard rejects it with 409.
      val guarded = newTask("Chaos guarded $s")
      api(HttpMethod.PATCH, "/api/v1/tasks/$guarded/status", json("status" to "completed"), doctor)
      val stale = api(HttpMethod.PATCH, "/api/v1/tasks/$guarded/status", json("status" to "in_progress"), doctor)
      assertEquals(409, stale.status, "keyless stale write -> 409, got ${stale.body.take(200)}")
      assertEquals("TASK_COMPLETED_TERMINAL", om.readTree(stale.body)["error"]["code"].asText())
      assertEquals("completed", taskStatus(guarded), "completed state survives the stale write")
    }
  }

  @Nested
  inner class Phase2InpatientTests {
    @Test
    fun inpatientAdmissionLifecycleCoverage() {
      val token = adminToken()
      val s = suffix()
      val hospitalId = firstHospitalId(token)
      val wardId =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/hospital-config/hospitals/$hospitalId/wards",
            json(
              "name" to "Ward P2 $s",
              "code" to "W2-$s",
              "type" to "ICU",
            ),
            token,
          ),
        )["id"].asText()
      val bed1Id = createBed(token, hospitalId, "ipd-bed1-$s", wardId)["id"].asText()
      val bed2Id = createBed(token, hospitalId, "ipd-bed2-$s", wardId)["id"].asText()
      val bed3Id = createBed(token, hospitalId, "ipd-bed3-$s", wardId)["id"].asText()
      val patientId = createPatient(token, "Luke", "Inpatient", s)

      val noPatient = api(HttpMethod.POST, "/api/v1/admissions", json("hospitalId" to hospitalId), token)
      assertEquals(400, noPatient.status, "admit without patient -> ${noPatient.body.take(200)}")
      val badType = api(HttpMethod.POST, "/api/v1/admissions", json("hospitalId" to hospitalId, "patientId" to patientId, "admissionType" to "walk-in"), token)
      assertEquals(400, badType.status, "invalid admission type -> ${badType.body.take(200)}")
      val noBeds =
        api(
          HttpMethod.POST,
          "/api/v1/admissions",
          json(
            "hospitalId" to hospitalId,
            "patientId" to patientId,
            "wardId" to "11111111-1111-1111-1111-111111111111",
          ),
          token,
        )
      assertEquals(400, noBeds.status, "no bed available in unknown ward -> ${noBeds.body.take(200)}")

      val admission =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/admissions",
            json(
              "hospitalId" to hospitalId,
              "patientId" to patientId,
              "admissionType" to "emergency",
              "source" to "er",
              "admissionDiagnosis" to "Chest pain",
              "bedId" to bed1Id,
            ),
            token,
          ),
        )
      val admissionId = admission["id"].asText()
      assertEquals("admitted", admission["status"].asText(), "admission status")
      assertEquals(bed1Id, admission["bedId"].asText(), "allocated bed")

      assert2xx(api(HttpMethod.GET, "/api/v1/admissions/$admissionId", token = token), "get admission")
      val admitted = `data`(api(HttpMethod.GET, "/api/v1/admissions?status=admitted", token = token))
      assertTrue(admitted.any { it["id"].asText() == admissionId }, "admitted list contains admission")
      assert2xx(api(HttpMethod.GET, "/api/v1/admissions?patientId=$patientId", token = token), "admission by patient")

      val attendant =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/admissions/$admissionId/attendants",
            json(
              "name" to "Mary",
              "relationship" to "Spouse",
              "phone" to "+9745555",
            ),
            token,
          ),
        )
      assertNotNull(attendant["id"])
      val noAttendantName = api(HttpMethod.POST, "/api/v1/admissions/$admissionId/attendants", json("phone" to "1"), token)
      assertEquals(400, noAttendantName.status, "attendant without name")
      assertEquals(1, `data`(api(HttpMethod.GET, "/api/v1/admissions/$admissionId/attendants", token = token)).size(), "attendant count")

      val missingSummary = api(HttpMethod.GET, "/api/v1/admissions/$admissionId/discharge-summary", token = token)
      assertEquals(400, missingSummary.status, "summary missing before discharge")

      val sameBed = api(HttpMethod.POST, "/api/v1/admissions/$admissionId/transfer", json("toBedId" to bed1Id, "reason" to "same"), token)
      assertEquals(400, sameBed.status, "transfer to same bed")
      val occupiedBed =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/admissions",
            json("hospitalId" to hospitalId, "patientId" to patientId, "bedId" to bed3Id),
            token,
          ),
        )
      assertNotNull(occupiedBed["id"], "second admission to spare bed")
      val busyTransfer = api(HttpMethod.POST, "/api/v1/admissions/$admissionId/transfer", json("toBedId" to bed3Id, "reason" to "busy"), token)
      assertEquals(400, busyTransfer.status, "transfer to occupied bed")

      val transferred =
        `data`(api(HttpMethod.POST, "/api/v1/admissions/$admissionId/transfer", json("toBedId" to bed2Id, "reason" to "clinical escalation"), token))
      assertEquals(bed2Id, transferred["bedId"].asText(), "bed after transfer")
      val transfers = `data`(api(HttpMethod.GET, "/api/v1/admissions/$admissionId/transfers", token = token))
      assertEquals(1, transfers.size(), "transfer history count")

      val discharge =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/admissions/$admissionId/discharge",
            json(
              "dischargeType" to "home",
              "dischargeNotes" to "Stable",
              "summaryText" to "Chest pain resolved",
              "followUpPlan" to "Cardiology clinic in 2 weeks",
              "medicationsAtDischarge" to "Aspirin 75mg od",
            ),
            token,
          ),
        )
      assertEquals("discharged", discharge["status"].asText(), "admission discharged")
      assertNotNull(discharge["dischargedAt"])

      val summary = `data`(api(HttpMethod.GET, "/api/v1/admissions/$admissionId/discharge-summary", token = token))
      assertEquals("Chest pain resolved", summary["summaryText"].asText(), "discharge summary text")

      val dupDischarge = api(HttpMethod.POST, "/api/v1/admissions/$admissionId/discharge", json("dischargeType" to "home"), token)
      assertEquals(400, dupDischarge.status, "discharge already discharged")

      val readmit = `data`(api(HttpMethod.POST, "/api/v1/admissions", json("hospitalId" to hospitalId, "patientId" to patientId, "bedId" to bed2Id), token))
      assertNotNull(readmit["id"], "discharged bed is reusable")
      assert2xx(
        api(HttpMethod.POST, "/api/v1/admissions/${readmit["id"].asText()}/discharge", json("dischargeType" to "home"), token),
        "cleanup second admission",
      )
    }

    @Test
    fun nursingDocumentationCoverage() {
      val token = adminToken()
      val s = suffix()
      val hospitalId = firstHospitalId(token)
      val wardId =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/hospital-config/hospitals/$hospitalId/wards",
            json(
              "name" to "Nursing Ward $s",
              "code" to "NW-$s",
              "type" to "General",
            ),
            token,
          ),
        )["id"].asText()
      val patientId = createPatient(token, "Nina", "Florence", s)
      createBed(token, hospitalId, "nurse-bed-$s", wardId)
      val admission =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/admissions",
            json(
              "hospitalId" to hospitalId,
              "patientId" to patientId,
              "admissionType" to "elective",
              "wardId" to wardId,
            ),
            token,
          ),
        )
      val admissionId = admission["id"].asText()
      assertNotNull(admission["bedId"], "auto allocation picked a ward bed")

      val assessment =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/nursing/assessments",
            json(
              "hospitalId" to hospitalId,
              "patientId" to patientId,
              "admissionId" to admissionId,
              "assessmentType" to "admission",
              "findings" to "Awake, alert, oriented",
              "riskLevel" to "low",
            ),
            token,
          ),
        )
      assertNotNull(assessment["id"])
      assertEquals(1, `data`(api(HttpMethod.GET, "/api/v1/nursing/assessments?patientId=$patientId", token = token)).size(), "assessments listed")
      val badAssessment = api(HttpMethod.POST, "/api/v1/nursing/assessments", json("hospitalId" to hospitalId, "patientId" to patientId), token)
      assertEquals(400, badAssessment.status, "assessment without type")

      val plan =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/nursing/careplans",
            json(
              "hospitalId" to hospitalId,
              "patientId" to patientId,
              "admissionId" to admissionId,
              "title" to "Mobility",
              "goal" to "Ambulates independently",
              "interventions" to "Physio twice daily",
            ),
            token,
          ),
        )
      assertNotNull(plan["id"])
      val completedPlan = `data`(api(HttpMethod.PATCH, "/api/v1/nursing/careplans/${plan["id"].asText()}/status", json("status" to "completed"), token))
      assertEquals("completed", completedPlan["status"].asText(), "careplan completed")
      val badPlanStatus = api(HttpMethod.PATCH, "/api/v1/nursing/careplans/${plan["id"].asText()}/status", json("status" to "on-hold"), token)
      assertEquals(400, badPlanStatus.status, "invalid careplan status")

      val note =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/nursing/notes",
            json(
              "hospitalId" to hospitalId,
              "patientId" to patientId,
              "admissionId" to admissionId,
              "noteType" to "progress",
              "content" to "Wound reviewed, clean and dry",
            ),
            token,
          ),
        )
      assertNotNull(note["id"])
      val noContent = api(HttpMethod.POST, "/api/v1/nursing/notes", json("hospitalId" to hospitalId, "patientId" to patientId), token)
      assertEquals(400, noContent.status, "note without content")

      val handover =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/nursing/handovers",
            json(
              "hospitalId" to hospitalId,
              "wardId" to wardId,
              "shift" to "night",
              "situation" to "Stable",
              "background" to "Day 2 post-op",
              "assessment" to "Pain controlled",
              "recommendation" to "Continue plan",
            ),
            token,
          ),
        )
      assertNotNull(handover["id"])
      val badShift = api(HttpMethod.POST, "/api/v1/nursing/handovers", json("hospitalId" to hospitalId, "shift" to "graveyard"), token)
      assertEquals(400, badShift.status, "invalid handover shift")
      assertEquals(1, `data`(api(HttpMethod.GET, "/api/v1/nursing/handovers?wardId=$wardId", token = token)).size(), "handover by ward")

      val vitals =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/nursing/vitals",
            json(
              "hospitalId" to hospitalId,
              "patientId" to patientId,
              "admissionId" to admissionId,
              "systolic" to 118,
              "diastolic" to 76,
              "heartRate" to 72,
              "respiratoryRate" to 16,
              "temperature" to 36.6,
              "oxygenSaturation" to 98,
              "gcs" to 15,
            ),
            token,
          ),
        )
      assertNotNull(vitals["id"])
      assertEquals(98, `data`(api(HttpMethod.GET, "/api/v1/nursing/vitals?patientId=$patientId", token = token))[0]["oxygenSaturation"].asInt(), "latest sat")

      val mar =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/nursing/mar",
            json(
              "hospitalId" to hospitalId,
              "patientId" to patientId,
              "admissionId" to admissionId,
              "medicationName" to "Paracetamol",
              "dose" to "500mg",
              "route" to "PO",
              "scheduledAt" to
                java.time.Instant
                  .now()
                  .plusSeconds(3600)
                  .toString(),
            ),
            token,
          ),
        )
      assertNotNull(mar["id"])
      assertEquals("scheduled", mar["status"].asText())
      val noSchedule = api(HttpMethod.POST, "/api/v1/nursing/mar", json("hospitalId" to hospitalId, "patientId" to patientId, "medicationName" to "X"), token)
      assertEquals(400, noSchedule.status, "mar without scheduledAt")
      val administered = `data`(api(HttpMethod.PATCH, "/api/v1/nursing/mar/${mar["id"].asText()}/status", json("status" to "administered"), token))
      assertEquals("administered", administered["status"].asText(), "mar administered")
      assertNotNull(administered["administeredAt"])
      val badMarStatus = api(HttpMethod.PATCH, "/api/v1/nursing/mar/${mar["id"].asText()}/status", json("status" to "lost"), token)
      assertEquals(400, badMarStatus.status, "invalid mar status")

      val risk =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/nursing/risk-assessments",
            json(
              "hospitalId" to hospitalId,
              "patientId" to patientId,
              "admissionId" to admissionId,
              "riskType" to "falls",
              "score" to 8,
              "level" to "high",
            ),
            token,
          ),
        )
      assertNotNull(risk["id"])
      assertEquals(1, `data`(api(HttpMethod.GET, "/api/v1/nursing/risk-assessments?patientId=$patientId", token = token)).size(), "risk assessments listed")

      val noPerm = noRoleToken()
      assertEquals(403, api(HttpMethod.GET, "/api/v1/nursing/vitals", token = noPerm).status, "nursing read denied for role-less user")
      assertEquals(
        403,
        api(HttpMethod.POST, "/api/v1/nursing/vitals", json("patientId" to patientId), noPerm).status,
        "nursing write denied for role-less user",
      )

      assert2xx(api(HttpMethod.POST, "/api/v1/admissions/$admissionId/discharge", json("dischargeType" to "home"), token), "cleanup nursing admission")
    }

    @Test
    fun icuMonitoringCoverage() {
      val token = adminToken()
      val s = suffix()
      val hospitalId = firstHospitalId(token)
      val patientId = createPatient(token, "Ivana", "Critical", s)
      val admission =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/admissions",
            json(
              "hospitalId" to hospitalId,
              "patientId" to patientId,
              "admissionType" to "emergency",
              "bedId" to createBed(token, hospitalId, "icu-bed-$s", null)["id"].asText(),
            ),
            token,
          ),
        )
      val admissionId = admission["id"].asText()

      val vitals =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/icu/vitals",
            json(
              "hospitalId" to hospitalId,
              "patientId" to patientId,
              "admissionId" to admissionId,
              "systolic" to 82,
              "diastolic" to 54,
              "heartRate" to 118,
              "respiratoryRate" to 24,
              "temperature" to 38.2,
              "oxygenSaturation" to 91,
              "map" to 63,
              "etco2" to 35,
              "gcs" to 13,
            ),
            token,
          ),
        )
      assertNotNull(vitals["id"])
      assertEquals(63, `data`(api(HttpMethod.GET, "/api/v1/icu/vitals?patientId=$patientId", token = token))[0]["map"].asInt(), "MAP recorded")

      val fluid =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/icu/fluid-balance",
            json(
              "hospitalId" to hospitalId,
              "patientId" to patientId,
              "admissionId" to admissionId,
              "inputMl" to 1000.0,
              "outputMl" to 600.0,
              "inputType" to "iv",
              "outputType" to "urine",
            ),
            token,
          ),
        )
      assertNotNull(fluid["id"])
      assertEquals(1, `data`(api(HttpMethod.GET, "/api/v1/icu/fluid-balance?patientId=$patientId", token = token)).size(), "fluid balance listed")

      val vent =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/icu/ventilation",
            json(
              "hospitalId" to hospitalId,
              "patientId" to patientId,
              "admissionId" to admissionId,
              "mode" to "SIMV",
              "fio2Percent" to 40,
              "peep" to 5.0,
              "tidalVolumeMl" to 450,
              "respiratoryRateSet" to 14,
            ),
            token,
          ),
        )
      assertNotNull(vent["id"])
      val noMode = api(HttpMethod.POST, "/api/v1/icu/ventilation", json("hospitalId" to hospitalId, "patientId" to patientId), token)
      assertEquals(400, noMode.status, "ventilation without mode")
      assertEquals(1, `data`(api(HttpMethod.GET, "/api/v1/icu/ventilation?patientId=$patientId", token = token)).size(), "ventilation listed")

      val infusion =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/icu/infusions",
            json(
              "hospitalId" to hospitalId,
              "patientId" to patientId,
              "admissionId" to admissionId,
              "drugName" to "Norepinephrine",
              "dose" to "0.05 mcg/kg/min",
              "rateMlPerHour" to 12.5,
              "route" to "IV",
            ),
            token,
          ),
        )
      assertNotNull(infusion["id"])
      assertEquals("active", infusion["status"].asText())
      val stopped = `data`(api(HttpMethod.PATCH, "/api/v1/icu/infusions/${infusion["id"].asText()}/status", json("status" to "stopped"), token))
      assertEquals("stopped", stopped["status"].asText(), "infusion stopped")
      val noDrug = api(HttpMethod.POST, "/api/v1/icu/infusions", json("hospitalId" to hospitalId, "patientId" to patientId), token)
      assertEquals(400, noDrug.status, "infusion without drug")

      val alert =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/icu/alerts",
            json(
              "hospitalId" to hospitalId,
              "patientId" to patientId,
              "admissionId" to admissionId,
              "alertType" to "desaturation",
              "severity" to "critical",
              "message" to "SpO2 dropped to 88%",
            ),
            token,
          ),
        )
      val alertId = alert["id"].asText()
      assertNotNull(alertId)
      assertEquals("open", alert["status"].asText())
      val openAlerts = `data`(api(HttpMethod.GET, "/api/v1/icu/alerts?patientId=$patientId&status=open", token = token))
      assertTrue(openAlerts.any { it["id"].asText() == alertId }, "open alert visible")
      val noAlertType = api(HttpMethod.POST, "/api/v1/icu/alerts", json("hospitalId" to hospitalId, "patientId" to patientId), token)
      assertEquals(400, noAlertType.status, "alert without type")
      val ack = `data`(api(HttpMethod.PATCH, "/api/v1/icu/alerts/$alertId/acknowledge", token = token))
      assertEquals("acknowledged", ack["status"].asText(), "alert acknowledged")
      assertNotNull(ack["acknowledgedBy"])
      val resolved = `data`(api(HttpMethod.PATCH, "/api/v1/icu/alerts/$alertId/status", json("status" to "resolved"), token))
      assertEquals("resolved", resolved["status"].asText(), "alert resolved")
      val resolvedList = `data`(api(HttpMethod.GET, "/api/v1/icu/alerts?patientId=$patientId&status=resolved", token = token))
      assertTrue(resolvedList.any { it["id"].asText() == alertId }, "resolved alert listed")

      val noPerm = noRoleToken()
      assertEquals(403, api(HttpMethod.GET, "/api/v1/icu/vitals", token = noPerm).status, "icu read denied for role-less user")
      assertEquals(403, api(HttpMethod.POST, "/api/v1/icu/alerts", json("patientId" to patientId), noPerm).status, "icu write denied for role-less user")

      assert2xx(api(HttpMethod.POST, "/api/v1/admissions/$admissionId/discharge", json("dischargeType" to "home"), token), "cleanup icu admission")
    }

    @Test
    fun emergencyWorkflowCoverage() {
      val token = adminToken()
      val s = suffix()
      val hospitalId = firstHospitalId(token)
      val patientId = createPatient(token, "Erica", "Trauma", s)

      val noPatient = api(HttpMethod.POST, "/api/v1/emergency/registrations", json("hospitalId" to hospitalId), token)
      assertEquals(400, noPatient.status, "registration without patient -> ${noPatient.body.take(200)}")
      val badArrival =
        api(
          HttpMethod.POST,
          "/api/v1/emergency/registrations",
          json(
            "hospitalId" to hospitalId,
            "patientId" to patientId,
            "arrivalMode" to "drone",
          ),
          token,
        )
      assertEquals(400, badArrival.status, "invalid arrival mode -> ${badArrival.body.take(200)}")

      val registration =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/emergency/registrations",
            json(
              "hospitalId" to hospitalId,
              "patientId" to patientId,
              "arrivalMode" to "ambulance",
              "presentingComplaint" to "MVC, unrestrained driver",
            ),
            token,
          ),
        )
      val registrationId = registration["id"].asText()
      assertEquals("registered", registration["status"].asText())
      assertEquals("ambulance", registration["arrivalMode"].asText())

      val registeredList = `data`(api(HttpMethod.GET, "/api/v1/emergency/registrations?status=registered", token = token))
      assertTrue(registeredList.any { it["id"].asText() == registrationId }, "registered list contains registration")
      assertEquals(1, `data`(api(HttpMethod.GET, "/api/v1/emergency/registrations?patientId=$patientId", token = token)).size(), "registration by patient")
      assert2xx(api(HttpMethod.GET, "/api/v1/emergency/registrations/$registrationId", token = token), "get registration")

      val badEsi = api(HttpMethod.POST, "/api/v1/emergency/registrations/$registrationId/triage", json("esiLevel" to 6), token)
      assertEquals(400, badEsi.status, "ESI out of range -> ${badEsi.body.take(200)}")
      val noEsi = api(HttpMethod.POST, "/api/v1/emergency/registrations/$registrationId/triage", json("painScale" to 4), token)
      assertEquals(400, noEsi.status, "triage without ESI")

      val triage =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/emergency/registrations/$registrationId/triage",
            json(
              "esiLevel" to 2,
              "triageCategory" to "emergent",
              "painScale" to 7,
              "vitalTriggers" to true,
              "acuityNotes" to "SBP 82, HR 118",
            ),
            token,
          ),
        )
      assertEquals(2, triage["esiLevel"].asInt())
      val triaged = `data`(api(HttpMethod.GET, "/api/v1/emergency/registrations/$registrationId", token = token))
      assertEquals("triaged", triaged["status"].asText(), "registration moved to triaged")
      assertEquals("stat", triaged["priority"].asText(), "ESI<=2 escalates priority to stat")
      assertEquals(2, triaged["triageEsi"].asInt())
      val dupTriage = api(HttpMethod.POST, "/api/v1/emergency/registrations/$registrationId/triage", json("esiLevel" to 3), token)
      assertEquals(400, dupTriage.status, "double triage rejected")

      val trauma =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/emergency/registrations/$registrationId/trauma/activate",
            token = token,
          ),
        )
      assertNotNull(trauma["id"])
      assertEquals("primary", trauma["surveyPhase"].asText())
      val dupTrauma = api(HttpMethod.POST, "/api/v1/emergency/registrations/$registrationId/trauma/activate", token = token)
      assertEquals(400, dupTrauma.status, "trauma workflow already active")

      val badPhase =
        api(
          HttpMethod.PUT,
          "/api/v1/emergency/registrations/$registrationId/trauma",
          json("surveyPhase" to "golden-hour", "airway" to "clear"),
          token,
        )
      assertEquals(400, badPhase.status, "invalid trauma phase -> ${badPhase.body.take(200)}")
      val survey =
        `data`(
          api(
            HttpMethod.PUT,
            "/api/v1/emergency/registrations/$registrationId/trauma",
            json(
              "surveyPhase" to "completed",
              "airway" to "Intact, Guedel No.4",
              "breathing" to "Bilateral air entry, CXR done",
              "circulation" to "IV x2, saline 1L bolus",
              "disability" to "GCS 14, pupils equal",
              "exposure" to "Pelvic binder applied",
              "surveyNotes" to "Booked for OR",
            ),
            token,
          ),
        )
      assertEquals("completed", survey["surveyPhase"].asText())
      assertEquals("GCS 14, pupils equal", survey["disability"].asText())
      assertNotNull(survey["completedAt"])

      val noStatus = api(HttpMethod.PATCH, "/api/v1/emergency/registrations/$registrationId/status", json("admittedAdmissionId" to "x"), token)
      assertEquals(400, noStatus.status, "status required")
      val badStatus = api(HttpMethod.PATCH, "/api/v1/emergency/registrations/$registrationId/status", json("status" to "evacuated"), token)
      assertEquals(400, badStatus.status, "invalid status")
      val admitNoLink = api(HttpMethod.PATCH, "/api/v1/emergency/registrations/$registrationId/status", json("status" to "admitted"), token)
      assertEquals(400, admitNoLink.status, "admit requires admission id")

      val wardId =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/hospital-config/hospitals/$hospitalId/wards",
            json("name" to "ER Ward $s", "code" to "ER-$s", "type" to "General"),
            token,
          ),
        )["id"].asText()
      createBed(token, hospitalId, "er-bed-$s", wardId)
      val admissionId =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/admissions",
            json("hospitalId" to hospitalId, "patientId" to patientId, "wardId" to wardId),
            token,
          ),
        )["id"].asText()
      val admitted =
        `data`(
          api(
            HttpMethod.PATCH,
            "/api/v1/emergency/registrations/$registrationId/status",
            json("status" to "admitted", "admittedAdmissionId" to admissionId),
            token,
          ),
        )
      assertEquals("admitted", admitted["status"].asText(), "registration admitted")
      assertEquals(admissionId, admitted["admittedAdmissionId"].asText(), "admitting admission linked")
      val discharged =
        `data`(
          api(
            HttpMethod.PATCH,
            "/api/v1/emergency/registrations/$registrationId/status",
            json("status" to "discharged"),
            token,
          ),
        )
      assertEquals("discharged", discharged["status"].asText(), "registration discharged")
      assertNotNull(discharged["dischargedAt"])
      assertTrue(discharged["admittedAdmissionId"].isNull, "discharge clears admission link")

      val noPerm = noRoleToken()
      assertEquals(403, api(HttpMethod.GET, "/api/v1/emergency/registrations", token = noPerm).status, "emergency read denied for role-less user")
      assertEquals(
        403,
        api(HttpMethod.POST, "/api/v1/emergency/registrations", json("hospitalId" to hospitalId, "patientId" to patientId), noPerm).status,
        "emergency write denied for role-less user",
      )
    }

    @Test
    fun surgicalWorkflowCoverage() {
      val token = adminToken()
      val s = suffix()
      val hospitalId = firstHospitalId(token)
      val patientId = createPatient(token, "Harry", "Scalpel", s)
      val now = java.time.Instant.now()

      val room =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/surgery/ot-rooms",
            json("hospitalId" to hospitalId, "name" to "OT Main $s", "code" to "OT1-$s"),
            token,
          ),
        )
      val roomId = room["id"].asText()
      assertEquals("operational", room["status"].asText())
      val noRoomName = api(HttpMethod.POST, "/api/v1/surgery/ot-rooms", json("hospitalId" to hospitalId, "code" to "X"), token)
      assertEquals(400, noRoomName.status, "ot room without name")
      val otRooms = `data`(api(HttpMethod.GET, "/api/v1/surgery/ot-rooms?hospitalId=$hospitalId", token = token))
      assertTrue(otRooms.any { it["id"].asText() == roomId }, "ot room visible in hospital listing")

      val noPatient = api(HttpMethod.POST, "/api/v1/surgery/schedules", json("hospitalId" to hospitalId, "procedureName" to "Appendectomy"), token)
      assertEquals(400, noPatient.status, "schedule without patient")
      val noProcedure = api(HttpMethod.POST, "/api/v1/surgery/schedules", json("hospitalId" to hospitalId, "patientId" to patientId), token)
      assertEquals(400, noProcedure.status, "schedule without procedure")
      val noStart =
        api(
          HttpMethod.POST,
          "/api/v1/surgery/schedules",
          json(
            "hospitalId" to hospitalId,
            "patientId" to patientId,
            "procedureName" to "Appendectomy",
          ),
          token,
        )
      assertEquals(400, noStart.status, "schedule without start time")

      val schedule =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/surgery/schedules",
            json(
              "hospitalId" to hospitalId,
              "patientId" to patientId,
              "procedureName" to "Laparoscopic appendectomy",
              "procedureCode" to "LA-01",
              "specialty" to "General Surgery",
              "scheduledStartAt" to now.plusSeconds(3600).toString(),
              "scheduledEndAt" to now.plusSeconds(7200).toString(),
              "notes" to "Third case of the day",
            ),
            token,
          ),
        )
      val scheduleId = schedule["id"].asText()
      assertEquals("scheduled", schedule["status"].asText())
      assertEquals(
        1,
        `data`(api(HttpMethod.GET, "/api/v1/surgery/schedules?patientId=$patientId", token = token)).size(),
        "schedule by patient",
      )
      val scheduledList = `data`(api(HttpMethod.GET, "/api/v1/surgery/schedules?status=scheduled", token = token))
      assertTrue(scheduledList.any { it["id"].asText() == scheduleId }, "scheduled list contains booking")
      assert2xx(api(HttpMethod.GET, "/api/v1/surgery/schedules/$scheduleId", token = token), "get schedule")

      val badStatus = api(HttpMethod.PATCH, "/api/v1/surgery/schedules/$scheduleId/status", json("status" to "concluded"), token)
      assertEquals(400, badStatus.status, "invalid schedule status")
      val completeTooEarly = api(HttpMethod.PATCH, "/api/v1/surgery/schedules/$scheduleId/status", json("status" to "completed"), token)
      assertEquals(400, completeTooEarly.status, "cannot complete before start")
      val noRoom = api(HttpMethod.POST, "/api/v1/surgery/schedules/$scheduleId/ot-allocation", json("startTime" to now.plusSeconds(3600).toString()), token)
      assertEquals(400, noRoom.status, "allocation without ot room")
      val unknownRoom =
        api(
          HttpMethod.POST,
          "/api/v1/surgery/schedules/$scheduleId/ot-allocation",
          json(
            "otRoomId" to "11111111-1111-1111-1111-111111111111",
            "startTime" to now.plusSeconds(3600).toString(),
            "endTime" to now.plusSeconds(7200).toString(),
          ),
          token,
        )
      assertEquals(400, unknownRoom.status, "allocation to unknown room")

      val allocation =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/surgery/schedules/$scheduleId/ot-allocation",
            json("otRoomId" to roomId, "startTime" to now.plusSeconds(3600).toString(), "endTime" to now.plusSeconds(7200).toString()),
            token,
          ),
        )
      assertNotNull(allocation["id"])
      assertEquals("allocated", allocation["status"].asText())
      val secondSchedule =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/surgery/schedules",
            json(
              "hospitalId" to hospitalId,
              "patientId" to patientId,
              "procedureName" to "Cholecystectomy",
              "scheduledStartAt" to now.plusSeconds(3900).toString(),
              "scheduledEndAt" to now.plusSeconds(4500).toString(),
            ),
            token,
          ),
        )
      val secondScheduleId = secondSchedule["id"].asText()
      val conflict =
        api(
          HttpMethod.POST,
          "/api/v1/surgery/schedules/$secondScheduleId/ot-allocation",
          json("otRoomId" to roomId, "startTime" to now.plusSeconds(3900).toString(), "endTime" to now.plusSeconds(4500).toString()),
          token,
        )
      assertEquals(400, conflict.status, "overlapping allocation on the same OT room rejected")

      val badRole = api(HttpMethod.POST, "/api/v1/surgery/schedules/$scheduleId/team", json("staffName" to "Dr. X", "role" to "clown"), token)
      assertEquals(400, badRole.status, "invalid team role")
      val noName = api(HttpMethod.POST, "/api/v1/surgery/schedules/$scheduleId/team", json("role" to "surgeon"), token)
      assertEquals(400, noName.status, "team member without name")
      val member =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/surgery/schedules/$scheduleId/team",
            json("staffName" to "Dr. Ayesha", "role" to "surgeon"),
            token,
          ),
        )
      assertNotNull(member["id"])
      assertEquals(1, `data`(api(HttpMethod.GET, "/api/v1/surgery/schedules/$scheduleId/team", token = token)).size(), "team listed")

      val checklistItem =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/surgery/schedules/$scheduleId/checklist",
            json("checklistItem" to "Consent signed", "category" to "consent"),
            token,
          ),
        )
      val checklistItemId = checklistItem["id"].asText()
      assertEquals(false, checklistItem["checked"].asBoolean())
      val noItem = api(HttpMethod.POST, "/api/v1/surgery/schedules/$scheduleId/checklist", json("category" to "consent"), token)
      assertEquals(400, noItem.status, "checklist without item")
      val checked = `data`(api(HttpMethod.PATCH, "/api/v1/surgery/schedules/$scheduleId/checklist/$checklistItemId/check", token = token))
      assertEquals(true, checked["checked"].asBoolean(), "checklist item marked")
      assertNotNull(checked["checkedAt"])
      assertEquals(1, `data`(api(HttpMethod.GET, "/api/v1/surgery/schedules/$scheduleId/checklist", token = token)).size(), "checklist listed")

      val instrument =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/surgery/schedules/$scheduleId/instruments",
            json("instrumentName" to "Titanium forceps", "setName" to "Lap set A"),
            token,
          ),
        )
      val instrumentId = instrument["id"].asText()
      assertEquals("booked", instrument["status"].asText())
      val badInstrumentStatus = api(HttpMethod.PATCH, "/api/v1/surgery/schedules/$scheduleId/instruments/$instrumentId/status", json("status" to "lost"), token)
      assertEquals(400, badInstrumentStatus.status, "invalid instrument status")
      val returned =
        `data`(api(HttpMethod.PATCH, "/api/v1/surgery/schedules/$scheduleId/instruments/$instrumentId/status", json("status" to "returned"), token))
      assertEquals("returned", returned["status"].asText(), "instrument returned")
      assertNotNull(returned["countedBy"])
      val noInstrument = api(HttpMethod.POST, "/api/v1/surgery/schedules/$scheduleId/instruments", json("setName" to "Lap set A"), token)
      assertEquals(400, noInstrument.status, "instrument without name")

      val implant =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/surgery/schedules/$scheduleId/implants",
            json("implantName" to "Titanium mesh", "catalogNumber" to "TM-100", "lotNumber" to "LOT-9", "quantity" to 2, "notes" to "15x10cm"),
            token,
          ),
        )
      assertNotNull(implant["id"])
      assertEquals(2, implant["quantity"].asInt())
      val noImplant = api(HttpMethod.POST, "/api/v1/surgery/schedules/$scheduleId/implants", json("lotNumber" to "LOT-9"), token)
      assertEquals(400, noImplant.status, "implant without name")
      assertEquals(1, `data`(api(HttpMethod.GET, "/api/v1/surgery/schedules/$scheduleId/implants", token = token)).size(), "implants listed")

      val timeout =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/surgery/schedules/$scheduleId/intra-op",
            json("eventName" to "timeout", "notes" to "Patient, procedure, site confirmed"),
            token,
          ),
        )
      assertNotNull(timeout["id"])
      val noEvent = api(HttpMethod.POST, "/api/v1/surgery/schedules/$scheduleId/intra-op", json("notes" to "x"), token)
      assertEquals(400, noEvent.status, "intra-op without event name")
      assertEquals(1, `data`(api(HttpMethod.GET, "/api/v1/surgery/schedules/$scheduleId/intra-op", token = token)).size(), "intra-op entries listed")

      val missingPostOp = api(HttpMethod.GET, "/api/v1/surgery/schedules/$scheduleId/post-op", token = token)
      assertEquals(400, missingPostOp.status, "post-op record missing before completion")
      val badDestination =
        api(HttpMethod.POST, "/api/v1/surgery/schedules/$scheduleId/post-op", json("patientCondition" to "Stable", "destination" to "moon"), token)
      assertEquals(400, badDestination.status, "invalid post-op destination")
      val postOp =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/surgery/schedules/$scheduleId/post-op",
            json("patientCondition" to "Stable, awake", "destination" to "ward", "painScore" to 2, "drainOutputMl" to 25),
            token,
          ),
        )
      assertNotNull(postOp["id"])
      val fetchedPostOp = `data`(api(HttpMethod.GET, "/api/v1/surgery/schedules/$scheduleId/post-op", token = token))
      assertEquals("ward", fetchedPostOp["destination"].asText(), "post-op destination")

      val started =
        `data`(
          api(
            HttpMethod.PATCH,
            "/api/v1/surgery/schedules/$scheduleId/status",
            json("status" to "started"),
            token,
          ),
        )
      assertEquals("started", started["status"].asText(), "surgery started")
      assertNotNull(started["actualStartAt"])
      val completed =
        `data`(
          api(
            HttpMethod.PATCH,
            "/api/v1/surgery/schedules/$scheduleId/status",
            json("status" to "completed"),
            token,
          ),
        )
      assertEquals("completed", completed["status"].asText(), "surgery completed")
      assertNotNull(completed["actualEndAt"])
      val locked = api(HttpMethod.PATCH, "/api/v1/surgery/schedules/$scheduleId/status", json("status" to "cancelled"), token)
      assertEquals(400, locked.status, "completed schedule is locked")

      val noPerm = noRoleToken()
      assertEquals(403, api(HttpMethod.GET, "/api/v1/surgery/schedules", token = noPerm).status, "surgery read denied for role-less user")
      assertEquals(
        403,
        api(HttpMethod.POST, "/api/v1/surgery/schedules", json("hospitalId" to hospitalId, "patientId" to patientId, "procedureName" to "X"), noPerm).status,
        "surgery write denied for role-less user",
      )
    }

    @Test
    fun anaesthesiaWorkflowCoverage() {
      val token = adminToken()
      val s = suffix()
      val hospitalId = firstHospitalId(token)
      val patientId = createPatient(token, "Anya", "Sedated", s)
      val now = java.time.Instant.now()

      val scheduleId =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/surgery/schedules",
            json(
              "hospitalId" to hospitalId,
              "patientId" to patientId,
              "procedureName" to "Laparoscopic cholecystectomy",
              "scheduledStartAt" to now.plusSeconds(1800).toString(),
            ),
            token,
          ),
        )["id"].asText()

      val badSchedule =
        api(
          HttpMethod.POST,
          "/api/v1/anaesthesia/evaluations",
          json("surgeryScheduleId" to "11111111-1111-1111-1111-111111111111", "patientId" to patientId, "asaStatus" to "II"),
          token,
        )
      assertEquals(400, badSchedule.status, "evaluation for unknown schedule")
      val noAsa = api(HttpMethod.POST, "/api/v1/anaesthesia/evaluations", json("surgeryScheduleId" to scheduleId, "patientId" to patientId), token)
      assertEquals(400, noAsa.status, "evaluation without ASA class")
      val badAsa =
        api(
          HttpMethod.POST,
          "/api/v1/anaesthesia/evaluations",
          json(
            "surgeryScheduleId" to scheduleId,
            "patientId" to patientId,
            "asaStatus" to "VII",
          ),
          token,
        )
      assertEquals(400, badAsa.status, "invalid ASA class")

      val evaluation =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/anaesthesia/evaluations",
            json(
              "hospitalId" to hospitalId,
              "surgeryScheduleId" to scheduleId,
              "patientId" to patientId,
              "asaStatus" to "II",
              "airwayAssessment" to "Mallampati II",
              "allergies" to "Latex",
              "medicalConditions" to "Hypertension",
              "fastingHours" to 8,
              "riskLevel" to "moderate",
            ),
            token,
          ),
        )
      val evaluationId = evaluation["id"].asText()
      assertEquals("draft", evaluation["status"].asText())
      assertEquals(
        1,
        `data`(api(HttpMethod.GET, "/api/v1/anaesthesia/evaluations?surgeryScheduleId=$scheduleId", token = token)).size(),
        "evaluation by schedule",
      )
      assertEquals(1, `data`(api(HttpMethod.GET, "/api/v1/anaesthesia/evaluations?patientId=$patientId", token = token)).size(), "evaluation by patient")
      val confirmed = `data`(api(HttpMethod.PATCH, "/api/v1/anaesthesia/evaluations/$evaluationId/confirm", token = token))
      assertEquals("confirmed", confirmed["status"].asText(), "evaluation confirmed")
      assertNotNull(confirmed["evaluatedAt"])
      val dupConfirm = api(HttpMethod.PATCH, "/api/v1/anaesthesia/evaluations/$evaluationId/confirm", token = token)
      assertEquals(400, dupConfirm.status, "double confirmation rejected")

      val noRecordPatient = api(HttpMethod.POST, "/api/v1/anaesthesia/records", json("surgeryScheduleId" to scheduleId, "airwayDevice" to "ett"), token)
      assertEquals(400, noRecordPatient.status, "record without patient")
      val badAirway =
        api(
          HttpMethod.POST,
          "/api/v1/anaesthesia/records",
          json(
            "surgeryScheduleId" to scheduleId,
            "patientId" to patientId,
            "airwayDevice" to "whale",
          ),
          token,
        )
      assertEquals(400, badAirway.status, "invalid airway device")

      val record =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/anaesthesia/records",
            json("hospitalId" to hospitalId, "surgeryScheduleId" to scheduleId, "patientId" to patientId, "airwayDevice" to "ett", "intubationGrade" to "1"),
            token,
          ),
        )
      val recordId = record["id"].asText()
      assertEquals("planned", record["status"].asText())
      val badRecordStatus = api(HttpMethod.PATCH, "/api/v1/anaesthesia/records/$recordId/status", json("status" to "suspended"), token)
      assertEquals(400, badRecordStatus.status, "invalid record status")
      val completeEarly = api(HttpMethod.PATCH, "/api/v1/anaesthesia/records/$recordId/status", json("status" to "completed"), token)
      assertEquals(400, completeEarly.status, "cannot complete before induction")
      val induced = `data`(api(HttpMethod.PATCH, "/api/v1/anaesthesia/records/$recordId/status", json("status" to "induced"), token))
      assertEquals("induced", induced["status"].asText(), "record induced")
      assertNotNull(induced["startedAt"])
      assert2xx(api(HttpMethod.PATCH, "/api/v1/anaesthesia/records/$recordId/status", json("status" to "maintenance"), token), "maintenance phase")
      val completedRecord = `data`(api(HttpMethod.PATCH, "/api/v1/anaesthesia/records/$recordId/status", json("status" to "completed"), token))
      assertEquals("completed", completedRecord["status"].asText(), "record completed")
      assertNotNull(completedRecord["endedAt"])
      val lockedRecord = api(HttpMethod.PATCH, "/api/v1/anaesthesia/records/$recordId/status", json("status" to "emergence"), token)
      assertEquals(400, lockedRecord.status, "completed record locked")

      val noHeartRate = api(HttpMethod.POST, "/api/v1/anaesthesia/records/$recordId/monitoring", json("systolic" to 120, "diastolic" to 80), token)
      assertEquals(400, noHeartRate.status, "monitoring without heart rate")
      val monitoring =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/anaesthesia/records/$recordId/monitoring",
            json(
              "systolic" to 122,
              "diastolic" to 78,
              "heartRate" to 68,
              "oxygenSaturation" to 99,
              "etco2" to 36,
              "temperature" to 36.4,
              "anaesthesiaDepth" to "adequate",
            ),
            token,
          ),
        )
      assertNotNull(monitoring["id"])
      assertEquals(
        68,
        `data`(api(HttpMethod.GET, "/api/v1/anaesthesia/records/$recordId/monitoring", token = token))[0]["heartRate"].asInt(),
        "monitoring listed",
      )

      val noMedication = api(HttpMethod.POST, "/api/v1/anaesthesia/records/$recordId/medications", json("dose" to "2mg"), token)
      assertEquals(400, noMedication.status, "medication without name")
      val medication =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/anaesthesia/records/$recordId/medications",
            json("medicationName" to "Propofol", "dose" to "120mg", "route" to "IV"),
            token,
          ),
        )
      assertNotNull(medication["id"])
      assertEquals(1, `data`(api(HttpMethod.GET, "/api/v1/anaesthesia/records/$recordId/medications", token = token)).size(), "medications listed")

      val noScore = api(HttpMethod.POST, "/api/v1/anaesthesia/records/$recordId/recovery", json("observations" to "x"), token)
      assertEquals(400, noScore.status, "recovery without aldrete score")
      val badScore = api(HttpMethod.POST, "/api/v1/anaesthesia/records/$recordId/recovery", json("aldreteScore" to 11), token)
      assertEquals(400, badScore.status, "invalid aldrete score")
      val recovery =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/anaesthesia/records/$recordId/recovery",
            json("aldreteScore" to 9, "dischargeCriteriaMet" to true, "observations" to "Awake, moving all limbs"),
            token,
          ),
        )
      assertEquals(9, recovery["aldreteScore"].asInt(), "aldrete score recorded")
      assertEquals(true, recovery["dischargeCriteriaMet"].asBoolean())
      assertNotNull(recovery["recoveryEndedAt"])
      assertEquals(1, `data`(api(HttpMethod.GET, "/api/v1/anaesthesia/records/$recordId/recovery", token = token)).size(), "recovery listed")

      val pacu =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/anaesthesia/records/$recordId/pacu",
            json(
              "nurseAssignment" to "RN Omar",
              "painScore" to 1,
              "nauseaScore" to 0,
              "oxygenSaturation" to 98,
              "heartRate" to 74,
              "systolic" to 120,
              "diastolic" to 76,
            ),
            token,
          ),
        )
      assertNotNull(pacu["id"])
      assertEquals("RN Omar", pacu["nurseAssignment"].asText())
      assertEquals(1, `data`(api(HttpMethod.GET, "/api/v1/anaesthesia/records/$recordId/pacu", token = token)).size(), "pacu listed")

      val noPerm = noRoleToken()
      assertEquals(
        403,
        api(HttpMethod.GET, "/api/v1/anaesthesia/records/$recordId/monitoring", token = noPerm).status,
        "anaesthesia read denied for role-less user",
      )
      assertEquals(
        403,
        api(
          HttpMethod.POST,
          "/api/v1/anaesthesia/evaluations",
          json("surgeryScheduleId" to scheduleId, "patientId" to patientId, "asaStatus" to "I"),
          noPerm,
        ).status,
        "anaesthesia write denied for role-less user",
      )
    }

    @Test
    fun bloodBankWorkflowCoverage() {
      val token = adminToken()
      val s = suffix()
      val hospitalId = firstHospitalId(token)
      val patientId = createPatient(token, "Bella", "Oblood", s)
      val today = java.time.LocalDate.now()

      val noName = api(HttpMethod.POST, "/api/v1/blood-bank/donors", json("hospitalId" to hospitalId, "bloodGroup" to "O+"), token)
      assertEquals(400, noName.status, "donor without name")
      val badGroup = api(HttpMethod.POST, "/api/v1/blood-bank/donors", json("hospitalId" to hospitalId, "fullName" to "Donor X", "bloodGroup" to "Z+"), token)
      assertEquals(400, badGroup.status, "invalid donor blood group")
      val donor =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/blood-bank/donors",
            json(
              "hospitalId" to hospitalId,
              "donorNumber" to "D-$s",
              "fullName" to "Donor Salim",
              "bloodGroup" to "O+",
              "gender" to "male",
              "phone" to "551100",
            ),
            token,
          ),
        )
      assertNotNull(donor["id"])
      val deferred = `data`(api(HttpMethod.PATCH, "/api/v1/blood-bank/donors/${donor["id"].asText()}/status", json("status" to "deferred"), token))
      assertEquals("deferred", deferred["status"].asText(), "donor deferred")
      val badDonorStatus = api(HttpMethod.PATCH, "/api/v1/blood-bank/donors/${donor["id"].asText()}/status", json("status" to "frozen"), token)
      assertEquals(400, badDonorStatus.status, "invalid donor status")
      val deferredList = `data`(api(HttpMethod.GET, "/api/v1/blood-bank/donors?status=deferred", token = token))
      assertTrue(deferredList.any { it["id"].asText() == donor["id"].asText() }, "donor listing by status")

      val noUnitNumber =
        api(
          HttpMethod.POST,
          "/api/v1/blood-bank/units",
          json(
            "hospitalId" to hospitalId,
            "bloodGroup" to "O+",
            "expiryDate" to today.plusDays(30).toString(),
          ),
          token,
        )
      assertEquals(400, noUnitNumber.status, "unit without number")
      val badComponent =
        api(
          HttpMethod.POST,
          "/api/v1/blood-bank/units",
          json(
            "hospitalId" to hospitalId,
            "unitNumber" to "U-$s-bad",
            "bloodGroup" to "O+",
            "component" to "serum",
            "expiryDate" to today.plusDays(30).toString(),
          ),
          token,
        )
      assertEquals(400, badComponent.status, "invalid component")
      val expired =
        api(
          HttpMethod.POST,
          "/api/v1/blood-bank/units",
          json(
            "hospitalId" to hospitalId,
            "unitNumber" to "U-$s-exp",
            "bloodGroup" to "O+",
            "expiryDate" to today.minusDays(1).toString(),
          ),
          token,
        )
      assertEquals(400, expired.status, "expired unit rejected")

      val unitA =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/blood-bank/units",
            json(
              "hospitalId" to hospitalId,
              "unitNumber" to "U-$s-A",
              "bloodGroup" to "O+",
              "component" to "prbc",
              "expiryDate" to today.plusDays(35).toString(),
              "volumeMl" to 350,
              "storageLocation" to "Fridge-2",
            ),
            token,
          ),
        )
      val unitAId = unitA["id"].asText()
      assertEquals("available", unitA["status"].asText())
      val unitB =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/blood-bank/units",
            json(
              "hospitalId" to hospitalId,
              "unitNumber" to "U-$s-B",
              "bloodGroup" to "O-",
              "component" to "ffp",
              "expiryDate" to today.plusDays(20).toString(),
            ),
            token,
          ),
        )
      val unitBId = unitB["id"].asText()
      val badUnitStatus = api(HttpMethod.PATCH, "/api/v1/blood-bank/units/$unitBId/status", json("status" to "vampire"), token)
      assertEquals(400, badUnitStatus.status, "invalid unit status")
      val discarded = `data`(api(HttpMethod.PATCH, "/api/v1/blood-bank/units/$unitBId/status", json("status" to "discarded"), token))
      assertEquals("discarded", discarded["status"].asText(), "unit discarded")
      val unitC =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/blood-bank/units",
            json(
              "hospitalId" to hospitalId,
              "unitNumber" to "U-$s-C",
              "bloodGroup" to "O-",
              "component" to "ffp",
              "expiryDate" to today.plusDays(20).toString(),
            ),
            token,
          ),
        )
      val unitCId = unitC["id"].asText()
      val prbcUnits = `data`(api(HttpMethod.GET, "/api/v1/blood-bank/units?component=prbc", token = token))
      assertTrue(prbcUnits.any { it["id"].asText() == unitAId }, "units listed by component")
      assertTrue(
        `data`(api(HttpMethod.GET, "/api/v1/blood-bank/units?status=available", token = token)).any {
          it["id"].asText() == unitCId
        },
        "available units listed",
      )

      val badAbo =
        api(
          HttpMethod.POST,
          "/api/v1/blood-bank/grouping",
          json(
            "hospitalId" to hospitalId,
            "unitId" to unitCId,
            "aboResult" to "X",
            "rhResult" to "positive",
          ),
          token,
        )
      assertEquals(400, badAbo.status, "invalid ABO result")
      val noAbo = api(HttpMethod.POST, "/api/v1/blood-bank/grouping", json("hospitalId" to hospitalId, "unitId" to unitCId, "rhResult" to "positive"), token)
      assertEquals(400, noAbo.status, "grouping without ABO")
      val grouping =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/blood-bank/grouping",
            json("hospitalId" to hospitalId, "unitId" to unitAId, "aboResult" to "O", "rhResult" to "negative"),
            token,
          ),
        )
      val groupingId = grouping["id"].asText()
      assertEquals(false, grouping["confirmed"].asBoolean())
      val dupGrouping =
        api(
          HttpMethod.POST,
          "/api/v1/blood-bank/grouping",
          json(
            "hospitalId" to hospitalId,
            "unitId" to unitAId,
            "aboResult" to "O",
            "rhResult" to "negative",
          ),
          token,
        )
      assertEquals(400, dupGrouping.status, "duplicate grouping rejected")
      val confirmedGrouping = `data`(api(HttpMethod.PATCH, "/api/v1/blood-bank/grouping/$groupingId/confirm", token = token))
      assertEquals(true, confirmedGrouping["confirmed"].asBoolean(), "grouping confirmed")
      val dupConfirmGrouping = api(HttpMethod.PATCH, "/api/v1/blood-bank/grouping/$groupingId/confirm", token = token)
      assertEquals(400, dupConfirmGrouping.status, "grouping already confirmed")
      assertEquals("O", `data`(api(HttpMethod.GET, "/api/v1/blood-bank/grouping?unitId=$unitAId", token = token))["aboResult"].asText(), "grouping by unit")

      val noTechnique =
        api(HttpMethod.POST, "/api/v1/blood-bank/crossmatch", json("hospitalId" to hospitalId, "patientId" to patientId, "unitId" to unitAId), token)
      assertEquals(400, noTechnique.status, "crossmatch without technique")
      val badTechnique =
        api(
          HttpMethod.POST,
          "/api/v1/blood-bank/crossmatch",
          json(
            "hospitalId" to hospitalId,
            "patientId" to patientId,
            "unitId" to unitAId,
            "technique" to "crystal",
          ),
          token,
        )
      assertEquals(400, badTechnique.status, "invalid crossmatch technique")
      val crossmatch =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/blood-bank/crossmatch",
            json("hospitalId" to hospitalId, "patientId" to patientId, "unitId" to unitAId, "technique" to "gel"),
            token,
          ),
        )
      val crossmatchId = crossmatch["id"].asText()
      assertEquals("equivocal", crossmatch["compatibility"].asText())
      val badCompatibility = api(HttpMethod.PATCH, "/api/v1/blood-bank/crossmatch/$crossmatchId/result", json("compatibility" to "maybe"), token)
      assertEquals(400, badCompatibility.status, "invalid compatibility")
      val compatible =
        `data`(
          api(
            HttpMethod.PATCH,
            "/api/v1/blood-bank/crossmatch/$crossmatchId/result",
            json(
              "compatibility" to "compatible",
              "resultNote" to "2+, no agglutination",
            ),
            token,
          ),
        )
      assertEquals("compatible", compatible["compatibility"].asText(), "crossmatch compatible")
      assertEquals(1, `data`(api(HttpMethod.GET, "/api/v1/blood-bank/crossmatch?patientId=$patientId", token = token)).size(), "crossmatch by patient")

      val reservation =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/blood-bank/reservations",
            json("hospitalId" to hospitalId, "patientId" to patientId, "unitId" to unitCId),
            token,
          ),
        )
      assertNotNull(reservation["id"])
      assertEquals("reserved", reservation["status"].asText())
      val dupReserve =
        api(HttpMethod.POST, "/api/v1/blood-bank/reservations", json("hospitalId" to hospitalId, "patientId" to patientId, "unitId" to unitCId), token)
      assertEquals(400, dupReserve.status, "double reservation rejected")
      val released = `data`(api(HttpMethod.POST, "/api/v1/blood-bank/reservations/$unitCId/release", token = token))
      assertEquals("released", released["status"].asText(), "reservation released")

      val issue =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/blood-bank/issues",
            json("hospitalId" to hospitalId, "patientId" to patientId, "unitId" to unitAId),
            token,
          ),
        )
      assertNotNull(issue["id"])
      val reissue = api(HttpMethod.POST, "/api/v1/blood-bank/issues", json("hospitalId" to hospitalId, "patientId" to patientId, "unitId" to unitAId), token)
      assertEquals(400, reissue.status, "issued unit cannot be issued again")
      assertEquals(1, `data`(api(HttpMethod.GET, "/api/v1/blood-bank/issues?patientId=$patientId", token = token)).size(), "issue by patient")

      val unissuedTransfusion =
        api(
          HttpMethod.POST,
          "/api/v1/blood-bank/transfusions",
          json("hospitalId" to hospitalId, "patientId" to patientId, "unitId" to unitCId),
          token,
        )
      assertEquals(400, unissuedTransfusion.status, "transfusion requires issued unit")
      val transfusion =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/blood-bank/transfusions",
            json(
              "hospitalId" to hospitalId,
              "patientId" to patientId,
              "unitId" to unitAId,
              "volumeMl" to 300,
              "startRateMlPerHour" to 120,
              "observations" to "No initial reaction",
            ),
            token,
          ),
        )
      val transfusionId = transfusion["id"].asText()
      assertEquals("in_progress", transfusion["status"].asText())
      val completedTransfusion = `data`(api(HttpMethod.PATCH, "/api/v1/blood-bank/transfusions/$transfusionId/status", json("status" to "completed"), token))
      assertEquals("completed", completedTransfusion["status"].asText(), "transfusion completed")
      assertNotNull(completedTransfusion["endedAt"])
      val finishedStatus = api(HttpMethod.PATCH, "/api/v1/blood-bank/transfusions/$transfusionId/status", json("status" to "stopped"), token)
      assertEquals(400, finishedStatus.status, "finished transfusion locked")

      val noType = api(HttpMethod.POST, "/api/v1/blood-bank/transfusions/$transfusionId/reactions", json("severity" to "severe"), token)
      assertEquals(400, noType.status, "reaction without type")
      val badSeverity =
        api(
          HttpMethod.POST,
          "/api/v1/blood-bank/transfusions/$transfusionId/reactions",
          json(
            "reactionType" to "hemolytic",
            "severity" to "apocalyptic",
          ),
          token,
        )
      assertEquals(400, badSeverity.status, "invalid reaction severity")
      val reaction =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/blood-bank/transfusions/$transfusionId/reactions",
            json(
              "reactionType" to "hemolytic",
              "severity" to "severe",
              "symptoms" to "Back pain, rigors",
              "actions" to "Stopped transfusion, notified blood bank",
            ),
            token,
          ),
        )
      assertNotNull(reaction["id"])
      assertEquals(1, `data`(api(HttpMethod.GET, "/api/v1/blood-bank/transfusions/$transfusionId/reactions", token = token)).size(), "reactions listed")

      val noPerm = noRoleToken()
      assertEquals(403, api(HttpMethod.GET, "/api/v1/blood-bank/donors", token = noPerm).status, "blood bank read denied for role-less user")
      assertEquals(
        403,
        api(
          HttpMethod.POST,
          "/api/v1/blood-bank/units",
          json("unitNumber" to "X", "bloodGroup" to "O+", "expiryDate" to today.plusDays(10).toString()),
          noPerm,
        ).status,
        "blood bank write denied for role-less user",
      )
    }

    private fun firstHospitalId(token: String): String = ensureSeed(token)

    private fun createBed(
      token: String,
      hospitalId: String,
      label: String,
      wardId: String?,
    ): JsonNode =
      `data`(
        api(
          HttpMethod.POST,
          "/api/v1/hospital-config/hospitals/$hospitalId/beds",
          json("label" to label, "type" to "ICU", "status" to "available", "wardId" to wardId),
          token,
        ),
      )

    private fun createPatient(
      token: String,
      firstName: String,
      lastName: String,
      s: String,
    ): String =
      `data`(
        api(
          HttpMethod.POST,
          "/api/v1/patients",
          json(
            "firstName" to firstName,
            "lastName" to "$lastName$s",
            "gender" to "female",
            "dateOfBirth" to "1985-05-10",
            "phone" to "5511$s".take(12),
            "qid" to "P2$s".take(11),
            "nationality" to "Qatar",
            "city" to "Doha",
          ),
          token,
        ),
      )["patient"]["id"].asText()
  }

  /** 00.16: metrics + trace pipeline verification (09-INFRASTRUCTURE.md §3). */
  @Nested
  inner class ObservabilityTests {
    @org.springframework.beans.factory.annotation.Autowired
    private lateinit var obsJdbc: org.springframework.jdbc.core.JdbcTemplate

    private fun prom(): String {
      val r = api(HttpMethod.GET, "/actuator/prometheus")
      assert2xx(r, "prometheus scrape")
      return r.body
    }

    private fun registerUser(hospitalId: String): String {
      val s = suffix()
      val email = "obs-$s@test.local"
      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/auth/register",
          json(
            "email" to email,
            "password" to "Passw0rd!123",
            "firstName" to "Obs",
            "lastName" to "Doctor$s",
            "roles" to listOf("DOCTOR"),
            "hospitalId" to hospitalId,
          ),
        ),
        "register obs doctor $email",
      )
      return email
    }

    private fun hospitalLogin(email: String): String =
      `data`(api(HttpMethod.POST, "/api/v1/auth/login", json("username" to email, "password" to "Passw0rd!123")))["accessToken"].asText()

    private fun connectRealtime(token: String): RealtimeCollector {
      val collector = RealtimeCollector()
      org.springframework.web.socket.client.standard
        .StandardWebSocketClient()
        .execute(collector, "ws://localhost:$port/realtime?token=$token")
        .get(15, java.util.concurrent.TimeUnit.SECONDS)
      return collector
    }

    private fun awaitEventFrame(
      collector: RealtimeCollector,
      eventType: String,
      timeoutMs: Long,
    ): String? {
      val deadline = System.currentTimeMillis() + timeoutMs
      while (System.currentTimeMillis() < deadline) {
        collector.messages
          .firstOrNull { isEventFrame(it, eventType) }
          ?.let { return it }
        Thread.sleep(250)
      }
      return null
    }

    // Substring matching is not enough: notification payloads can embed the triggering event
    // type (e.g. notification.dispatched of a task.created). Parse the frame's eventType field.
    private fun isEventFrame(
      message: String,
      eventType: String,
    ): Boolean =
      runCatching {
        val node = om.readTree(message)
        node.path("type").asText("") == "event.received" && node.path("event").path("eventType").asText("") == eventType
      }.getOrDefault(false)

    private fun awaitSpan(
      name: String,
      timeoutMs: Long = 15_000,
    ): Boolean {
      val deadline = System.currentTimeMillis() + timeoutMs
      while (System.currentTimeMillis() < deadline) {
        if (ObservabilitySpanCapture.spans(name).isNotEmpty()) return true
        Thread.sleep(200)
      }
      return false
    }

    @Test
    fun redAndBusinessMetricsCoverage() {
      ObservabilitySpanCapture.reset()
      val admin = adminToken()
      val h = ensureSeed(admin)
      val email = registerUser(h)
      val doctor = hospitalLogin(email)
      val doctorId = `data`(api(HttpMethod.GET, "/api/v1/auth/me", token = doctor))["userId"].asText()

      // RED traffic + business events: a read, a WS session, a task create -> task.created
      // dispatched (outbox -> Kafka/Redis -> WS relay) with the doctor as recipient.
      assert2xx(api(HttpMethod.GET, "/api/v1/events/catalog", token = admin), "RED read")
      val ws = connectRealtime(doctor)
      val s = suffix()
      api(
        HttpMethod.POST,
        "/api/v1/tasks",
        json("title" to "Obs metrics $s", "hospitalId" to h, "assigneeId" to doctorId),
        admin,
      )
      assertNotNull(awaitEventFrame(ws, "task.created", 20_000), "WS frame reached the connected session")
      assertEquals(
        1,
        ws.messages.count { isEventFrame(it, "task.created") },
        "exactly one task.created frame; all frames: ${ws.messages.joinToString(" || ").take(2000)}",
      )

      // SLA breach counter: the meter only registers on the first breach, so drive one through
      // the explicit scan route (§3: SLA breach counts as a business metric).
      api(
        HttpMethod.POST,
        "/api/v1/tasks",
        json(
          "title" to "Obs SLA $s",
          "hospitalId" to h,
          "assigneeId" to doctorId,
          "priority" to "normal",
          "dueAt" to "2020-01-01T00:00:00Z",
        ),
        admin,
      )
      assert2xx(api(HttpMethod.POST, "/api/v1/tasks/sla/scan", json(), admin), "SLA scan")

      val metrics = prom()
      // RED (per-module dashboards read these).
      assertTrue(metrics.contains("http_server_requests_seconds_count"), "HTTP RED counters present")
      assertTrue(metrics.contains("http_server_requests_seconds_bucket"), "HTTP latency histogram present")
      assertTrue(metrics.contains("jvm_memory_used_bytes"), "JVM base metrics present")
      // Business metrics (§3: events/min per type, SLA breach counts, DLQ + audit + WS).
      assertTrue(
        Regex("squamed_events_dispatched_total\\{type=\"task.created\"").containsMatchIn(metrics),
        "dispatched counter with type tag: ${metrics.lines().filter { it.startsWith("squamed_events_dispatched") }}",
      )
      assertTrue(
        Regex("squamed_ws_relayed_total\\{type=\"task.created\"").containsMatchIn(metrics),
        "WS relay counter with type tag",
      )
      val wsSessions =
        metrics
          .lines()
          .first { it.startsWith("squamed_ws_sessions") }
          .substringAfterLast(" ")
          .toDouble()
      assertTrue(wsSessions >= 1.0, "the connected session is gauged: $wsSessions")
      assertTrue(metrics.contains("squamed_events_dlq_depth"), "outbox DLQ depth gauge present")
      assertTrue(metrics.contains("squamed_integration_errors"), "integration error backlog gauge present")
      assertTrue(metrics.contains("squamed_audit_chain_breaches"), "audit breach gauge present")
      assertTrue(metrics.contains("squamed_audit_chain_seconds_since_verify"), "audit verify staleness gauge present")
      assertTrue(
        Regex("squamed_sla_breaches_total\\{priority=\"normal\"").containsMatchIn(metrics),
        "SLA breach counter present with priority tag",
      )

      // Trace pipeline: HTTP server span, JDBC/DB span, WS relay span for task.created.
      assertTrue(
        ObservabilitySpanCapture.names().any { it.startsWith("GET /api/") },
        "HTTP server span recorded: ${ObservabilitySpanCapture.names().take(12)}",
      )
      assertTrue(
        ObservabilitySpanCapture.exporter.finishedSpanItems.any {
          it.attributes.get(
            io.opentelemetry.api.common.AttributeKey
              .stringKey("db.system"),
          ) == "postgresql" ||
            it.attributes.get(
              io.opentelemetry.api.common.AttributeKey
                .stringKey("db.system.name"),
            ) == "postgresql"
        },
        "DB span recorded (db.system=postgresql)",
      )
      assertTrue(awaitSpan("squamed.realtime.relay"), "WS relay span recorded")
      // Later events (e.g. notification.dispatched) relay after task.created; match by type tag.
      val relay =
        ObservabilitySpanCapture.spans("squamed.realtime.relay").lastOrNull {
          it.attributes.get(
            io.opentelemetry.api.common.AttributeKey
              .stringKey("squamed.event.type"),
          ) == "task.created"
        }
      assertNotNull(relay, "relay span for task.created recorded")
      assertEquals(
        "task.created",
        relay!!.attributes.get(
          io.opentelemetry.api.common.AttributeKey
            .stringKey("squamed.event.type"),
        ),
        "relay span carries the event type",
      )
    }

    @Test
    fun dlqDepthAndAuditBreachGaugesFlip() {
      val admin = adminToken()
      val h = ensureSeed(admin)

      // Outbox DLQ: seed a FAILED row directly (dead-letter depth gauge must reflect it).
      val before = obsJdbc.queryForObject("SELECT count(*) FROM events.event_outbox WHERE status = 'FAILED'", Long::class.java)
      obsJdbc.update(
        "INSERT INTO events.event_outbox (event_id, event_type, occurred_at, source, entity_type, status, attempts, max_attempts, payload, recipient_ids) " +
          "VALUES (gen_random_uuid(), 'obs.dlq_seed', NOW(), 'obs', 'ObservabilityProbe', 'FAILED', 8, 8, '{}'::jsonb, '[]'::jsonb)",
      )
      var metrics = prom()
      val depth =
        metrics
          .lines()
          .firstOrNull { it.startsWith("squamed_events_dlq_depth") }
          ?.substringAfterLast(" ")
          ?.toDouble() ?: -1.0
      assertEquals(
        (before ?: 0L) + 1,
        depth.toLong(),
        "DLQ depth gauge counts the FAILED row: ${metrics.lines().filter { it.startsWith("squamed_events_dlq_depth") }}",
      )

      // Audit chain: fresh tenant -> one audited write -> tamper its row -> the breaches gauge
      // gains exactly that tenant (a fresh tenant cannot be breached before this), restore ->
      // gauge back to the baseline. Baseline may be > 0 (older PITs leave tampered tenants).
      val s = suffix()
      val h2 =
        `data`(
          api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "Obs H2 $s", "code" to "OBS-$s", "country" to "Qatar"), admin),
        )["id"].asText()
      val email = registerUser(h2)
      val doctor = hospitalLogin(email)
      val s2 = suffix()
      api(
        HttpMethod.POST,
        "/api/v1/patients",
        json(
          "firstName" to "Obs",
          "lastName" to "Patient$s2",
          "gender" to "female",
          "dateOfBirth" to "1985-05-10",
          "phone" to "5511$s2".take(12),
          "qid" to "P2$s2".take(11),
          "nationality" to "Qatar",
          "city" to "Doha",
        ),
        doctor,
      )
      // Establish the real baseline: prior failed PIT runs leave tampered tenants in the DB,
      // and the gauge reads 0.0 until the first verify runs in this JVM.
      `data`(api(HttpMethod.POST, "/api/v1/audit/verify", json(), admin))
      val b0 =
        prom()
          .lines()
          .first { it.startsWith("squamed_audit_chain_breaches") }
          .substringAfterLast(" ")
          .toDouble()
      // chain_seq is stamped async (ADR-15 batcher, 250ms schedule) — poll for linkage, never race it.
      val target: Map<String, Any> =
        run {
          val deadline = System.currentTimeMillis() + 10_000
          var row: Map<String, Any>? = null
          while (System.currentTimeMillis() < deadline && row == null) {
            row =
              obsJdbc
                .queryForList(
                  "SELECT id, response_payload FROM audit.audit_log WHERE hospital_id = ?::uuid AND chain_seq IS NOT NULL AND response_payload IS NOT NULL LIMIT 1",
                  h2,
                ).firstOrNull()
            if (row == null) Thread.sleep(250)
          }
          row ?: throw AssertionError("no chain-linked audit row for the fresh tenant within 10s (ADR-15 linkage lag)")
        }
      val id = target["id"].toString()
      val original = target["response_payload"].toString()
      obsJdbc.update("UPDATE audit.audit_log SET response_payload = ? WHERE id = ?::uuid", "TAMPERED-OBS-$s2", id)
      val verify = `data`(api(HttpMethod.POST, "/api/v1/audit/verify", json(), admin))
      assertTrue(verify["breaches"].any { it["hospitalId"].asText() == h2 }, "tamper detected on the fresh tenant: $verify")
      val afterTamper =
        prom()
          .lines()
          .first { it.startsWith("squamed_audit_chain_breaches") }
          .substringAfterLast(" ")
          .toDouble()
      assertEquals(b0 + 1.0, afterTamper, "breach gauge gains exactly the fresh tenant: b0=$b0 after=$afterTamper")
      assertTrue(prom().contains("squamed_audit_chain_seconds_since_verify"), "staleness gauge present after verify")

      obsJdbc.update("UPDATE audit.audit_log SET response_payload = ? WHERE id = ?::uuid", original, id)
      `data`(api(HttpMethod.POST, "/api/v1/audit/verify", json(), admin))
      val afterRestore =
        prom()
          .lines()
          .first { it.startsWith("squamed_audit_chain_breaches") }
          .substringAfterLast(" ")
          .toDouble()
      assertEquals(b0, afterRestore, "breach gauge returns to baseline after restore: b0=$b0 after=$afterRestore")
    }
  }

  /** 01.01: scheduling engine PIT — template → slot generation → booking lifecycle, policy, waitlist, cursor list. */
  @Nested
  inner class QueueEngineTests {
    private fun registerRoleUser(
      role: String,
      hospitalId: String,
      s: String,
      tag: String = role,
    ): String {
      val email = "queue-${tag.lowercase()}-$s@test.local"
      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/auth/register",
          json(
            "email" to email,
            "password" to "Passw0rd!123",
            "firstName" to "Queue",
            "lastName" to "$role$s",
            "roles" to listOf(role),
            "hospitalId" to hospitalId,
          ),
        ),
        "register $role $email",
      )
      return email
    }

    private fun login(email: String): String {
      val login = api(HttpMethod.POST, "/api/v1/auth/login", json("username" to email, "password" to "Passw0rd!123"))
      assert2xx(login, "login $email: ${login.body.take(200)}")
      return `data`(login)["accessToken"].asText()
    }

    /** Admin creates the patient with an explicit hospitalId (admin is not hospital-bound). */
    private fun patientIn(
      admin: String,
      hospitalId: String,
      tag: String,
      s: String,
    ): String =
      `data`(
        api(
          HttpMethod.POST,
          "/api/v1/patients",
          json(
            "firstName" to "Queue",
            "lastName" to "$tag$s",
            "gender" to "female",
            "dateOfBirth" to "1990-02-02",
            "hospitalId" to hospitalId,
          ),
          admin,
        ),
      )["patient"]["id"].asText()

    @Test
    fun queueTokenEngineNamedContentsCoverage() {
      val admin = adminToken()
      val s = suffix()
      // Fresh hospital per run: daily token sequences (C-01…) would collide with leftovers in the shared seed hospital.
      val h =
        `data`(
          api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "Queue L $s", "code" to "QUL-$s", "country" to "Qatar"), admin),
        )["id"].asText()
      val hForeign =
        `data`(
          api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "Queue F $s", "code" to "QUF-$s", "country" to "Qatar"), admin),
        )["id"].asText()
      val rec = login(registerRoleUser("RECEPTIONIST", h, s))
      val recForeign = login(registerRoleUser("RECEPTIONIST", hForeign, s, tag = "foreign"))

      // Config-driven priority (01.02): vip ranked ABOVE emergency proves the board order comes from
      // the published doc, not the built-in fallback (emergency first). avg 10 min feeds estimated wait.
      assert2xx(
        api(
          HttpMethod.PUT,
          "/api/v1/hospital-config/hospitals/$h/config/queue",
          json(
            "queues" to
              listOf(
                mapOf(
                  "code" to "consult",
                  "name" to "Consultation",
                  "strategy" to "priority",
                  "avgConsultMinutes" to 10,
                  "priorityClasses" to listOf("vip", "emergency", "routine"),
                ),
                mapOf("code" to "lab", "name" to "Laboratory", "strategy" to "fifo", "avgConsultMinutes" to 5),
              ),
          ),
          admin,
        ),
        "publish queue config",
      )

      val pA = patientIn(admin, h, "A", s)
      val pB = patientIn(admin, h, "B", s)
      val pC = patientIn(admin, h, "C", s)
      val pD = patientIn(admin, h, "D", s)

      fun add(
        queueType: String,
        patientId: String,
        priority: String,
      ): ApiResp = api(HttpMethod.POST, "/api/v1/queue/entries", json("queueType" to queueType, "patientId" to patientId, "priority" to priority), rec)

      // Estimated wait = waiting-ahead count × avg consult minutes (0/10/20 at avg 10).
      val a = `data`(add("consult", pA, "routine"))
      val b = `data`(add("consult", pB, "vip"))
      val c = `data`(add("consult", pC, "emergency"))
      assertEquals("C-01", a["tokenNumber"].asText(), "daily token sequence")
      assertEquals(0, a["estimatedWaitMinutes"].asInt(), "first entry waits 0")
      assertEquals(10, b["estimatedWaitMinutes"].asInt(), "one ahead × 10 min")
      assertEquals(20, c["estimatedWaitMinutes"].asInt(), "two ahead × 10 min")

      // Board order follows the config priorityClasses (vip before emergency), not the built-in default.
      val board = `data`(api(HttpMethod.GET, "/api/v1/queue/board?queueType=consult", token = rec))
      assertEquals(
        listOf("C-02", "C-03", "C-01"),
        board.map { it["tokenNumber"].asText() },
        "config priorityClasses drives board order: $board",
      )
      // callNext pulls the highest-ranked waiting entry and emits queue.called to displays.
      val next = `data`(api(HttpMethod.POST, "/api/v1/queue/entries/call-next", json("queueType" to "consult"), rec))
      assertEquals("C-02", next["tokenNumber"].asText(), "callNext honors config order (vip first)")

      // Transfer: waiting entry moves to lab with a fresh token from the target sequence (L-01).
      val transferred = `data`(api(HttpMethod.POST, "/api/v1/queue/entries/consult/C-01/transfer", json("queueType" to "lab"), rec))
      assertEquals("L-01", transferred["tokenNumber"].asText(), "transfer issues a fresh target-sequence token")
      assertEquals("lab", transferred["queueType"].asText())
      val labBoard = `data`(api(HttpMethod.GET, "/api/v1/queue/board?queueType=lab", token = rec))
      assertEquals(listOf("L-01"), labBoard.map { it["tokenNumber"].asText() }, "transferred entry on the lab board")
      assertEquals(
        409,
        api(HttpMethod.POST, "/api/v1/queue/entries/consult/C-02/transfer", json("queueType" to "lab"), rec).status,
        "called entry cannot transfer (state conflict)",
      )

      // Re-print: stateless view fetch with the QR queue id.
      val reprint = `data`(api(HttpMethod.GET, "/api/v1/queue/entries/lab/L-01/reprint", token = rec))
      assertEquals("L-01", reprint["tokenNumber"].asText(), "re-print is stateless (same token)")
      assertEquals("SQM:$h:lab:L-01:${LocalDate.now()}", reprint["qrPayload"].asText(), "QR queue id format")

      // Emergency bump: priority raised to emergency and queue.bumped reaches the WS display <2 s.
      `data`(add("consult", pD, "routine")) // C-04, waiting
      val ws = connectRealtime(rec)
      val t0 = System.currentTimeMillis()
      val bumped = `data`(api(HttpMethod.POST, "/api/v1/queue/entries/consult/C-04/bump", null, rec))
      assertEquals("emergency", bumped["priority"].asText(), "bump raises priority to emergency")
      val frame = awaitEventFrame(ws, "queue.bumped", 5000)
      assertNotNull(frame, "queue.bumped frame broadcast to the hospital WS session: ${ws.messages}")
      val latencyMs = System.currentTimeMillis() - t0
      assertTrue(latencyMs < 2000, "emergency bump to display <2 s (publish→WS receipt), took ${latencyMs}ms")

      // Cross-tenant: another hospital's receptionist cannot read this hospital's token.
      assertEquals(400, api(HttpMethod.GET, "/api/v1/queue/entries/lab/L-01", token = recForeign).status, "cross-tenant token read denied")
    }

    // 01.07 voice announcements: rule + zone resolution on call, repeat doubling for emergency,
    // manual enqueue, no-rule defaults, tenant-scoped zones and cross-tenant denials; the event
    // reaches the WS display session (SpeechSynthesis speaking is browser-side by design).
    @Test
    fun voiceAnnouncementsJourney() {
      val admin = adminToken()
      val s = suffix()
      val h1 =
        `data`(
          api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "Voice H1 $s", "code" to "VA1-$s", "country" to "Qatar"), admin),
        )["id"].asText()
      val hF =
        `data`(
          api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "Voice F $s", "code" to "VAF-$s", "country" to "Qatar"), admin),
        )["id"].asText()
      val rec = login(registerRoleUser("RECEPTIONIST", h1, s))
      val recForeign = login(registerRoleUser("RECEPTIONIST", hF, s, tag = "foreign"))

      // Counter + audio zone bound to it + rule (repeat 3, en→hi order, scoped to the zone).
      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/queue/service-points",
          json("name" to "Counter 1", "code" to "SP1", "queueType" to "consult", "counterNumber" to "1"),
          rec,
        ),
        "create service point",
      )
      val zone =
        `data`(
          api(HttpMethod.POST, "/api/v1/queue/zones", json("name" to "Main Display", "servicePointId" to "SP1", "volume" to 90), rec),
        )
      val zoneId = zone["id"].asText()
      assertEquals("Main Display", zone["name"].asText(), "zone created")
      assertEquals(90, zone["volume"].asInt(), "zone volume stored")
      val rule =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/queue/announcement-rules",
            json("queueType" to "consult", "repeatCount" to 3, "languages" to "en,hi", "zoneId" to zoneId),
            rec,
          ),
        )
      assertEquals(3, rule["repeatCount"].asInt(), "rule repeat stored")
      assertEquals("en,hi", rule["languages"].asText(), "rule language order stored")

      fun announcementRow(
        hid: String,
        needle: String,
        eventType: String,
      ): JsonNode? =
        `data`(api(HttpMethod.GET, "/api/v1/admin/events/outbox?limit=200", token = admin))
          .firstOrNull { it["eventType"].asText() == eventType && it["envelope"].asText().contains(hid) && it["envelope"].asText().contains(needle) }
          ?.let { om.readTree(it["envelope"].asText()) }

      // Call token C-01 (service point SP1): announcement carries the resolved rule + zone.
      val patient1 = patientIn(admin, h1, "A", s)
      val entry1 =
        `data`(api(HttpMethod.POST, "/api/v1/queue/entries", json("queueType" to "consult", "patientId" to patient1, "servicePointId" to "SP1"), rec))
      val token1 = entry1["tokenNumber"].asText()
      assert2xx(api(HttpMethod.PATCH, "/api/v1/queue/entries/consult/$token1/call", null, rec), "call $token1")
      val ann1 = announcementRow(h1, token1, "announcement.enqueued")
      assertNotNull(ann1, "announcement.enqueued in the outbox for $token1")
      assertEquals("announcement-service", ann1!!["source"].asText(), "announcement source stamped")
      assertEquals("queue-entry", ann1["entityType"].asText())
      assertEquals(3, ann1["payload"]["repeatCount"].asInt(), "rule repeat 3 applied")
      assertEquals(zoneId, ann1["payload"]["zoneId"].asText(), "rule zone scoped the announcement")
      assertEquals("Main Display", ann1["payload"]["zoneName"].asText())
      assertEquals("Counter 1", ann1["payload"]["counter"].asText(), "counter resolved from the service point name")
      assertEquals(90, ann1["payload"]["volume"].asInt(), "zone volume 90 applied")
      assertEquals(listOf("en", "hi"), ann1["payload"]["languages"].map { it.asText() }, "rule language order drives texts")
      assertEquals("Token $token1, counter Counter 1", ann1["payload"]["texts"]["en"].asText(), "en TTS template")
      assertEquals("टोकन $token1, काउंटर Counter 1", ann1["payload"]["texts"]["hi"].asText(), "hi TTS template")

      // queue.called is enriched with counter/priority so displays can rank and label.
      val called1 = announcementRow(h1, token1, "queue.called")
      assertNotNull(called1, "queue.called in the outbox")
      assertEquals("Counter 1", called1!!["payload"]["servicePointName"].asText(), "queue.called carries servicePointName")
      assertEquals("SP1", called1["payload"]["servicePointId"].asText())
      assertEquals("routine", called1["payload"]["priority"].asText())

      // Emergency call: rule repeat doubled (3 → 6); announcement.enqueued reaches the WS display session.
      val ws = connectRealtime(rec)
      val patient2 = patientIn(admin, h1, "B", s)
      val entry2 =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/queue/entries",
            json(
              "queueType" to "consult",
              "patientId" to patient2,
              "priority" to "emergency",
              "servicePointId" to "SP1",
            ),
            rec,
          ),
        )
      val token2 = entry2["tokenNumber"].asText()
      assert2xx(api(HttpMethod.PATCH, "/api/v1/queue/entries/consult/$token2/call", null, rec), "call emergency $token2")
      val ann2 = announcementRow(h1, token2, "announcement.enqueued")
      assertNotNull(ann2, "announcement.enqueued for the emergency token")
      assertEquals(6, ann2!!["payload"]["repeatCount"].asInt(), "emergency doubles the rule repeat (3 → 6)")
      assertEquals("emergency", ann2["payload"]["priority"].asText())
      // Wait for a frame carrying THIS token: consumer lag can replay the first
      // call's announcement.enqueued (published pre-connect) after the WS opens.
      val frame = awaitEventFrame(ws, "announcement.enqueued", 5000, mustContain = token2)
      assertNotNull(frame, "announcement.enqueued carrying $token2 broadcast to the hospital WS session: ${ws.messages.toList().takeLast(4)}")

      // Manual enqueue (contract /announcements:enqueue): texts echoed, zone volume applied, default repeat 2.
      val manual =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/queue/announcements",
            json("texts" to mapOf("en" to "Please proceed to the pharmacy", "hi" to "कृपया फार्मेसी की ओर बढ़ें"), "zoneId" to zoneId),
            rec,
          ),
        )
      assertEquals(2, manual["repeatCount"].asInt(), "manual enqueue default repeat 2")
      assertEquals("Main Display", manual["zoneName"].asText(), "manual enqueue zone applied")
      assertEquals(90, manual["volume"].asInt(), "zone volume applied")
      assertEquals("Please proceed to the pharmacy", manual["texts"]["en"].asText(), "manual texts echoed")

      // No-rule hospital: defaults (repeat 2, en+hi, every zone, default volume, counter = queue type).
      val patientF = patientIn(admin, hF, "F", s)
      val entryF = `data`(api(HttpMethod.POST, "/api/v1/queue/entries", json("queueType" to "consult", "patientId" to patientF), recForeign))
      val tokenF = entryF["tokenNumber"].asText()
      assert2xx(api(HttpMethod.PATCH, "/api/v1/queue/entries/consult/$tokenF/call", null, recForeign), "call foreign token")
      val annF = announcementRow(hF, tokenF, "announcement.enqueued")
      assertNotNull(annF, "announcement even without a rule (defaults)")
      assertEquals(2, annF!!["payload"]["repeatCount"].asInt(), "no-rule default repeat 2")
      assertEquals(listOf("en", "hi"), annF["payload"]["languages"].map { it.asText() }, "default languages en,hi")
      assertTrue(!annF["payload"].has("zoneId"), "no zone bound → announced in every zone (zoneId omitted)")
      assertEquals(80, annF["payload"]["volume"].asInt(), "default volume 80")
      assertEquals("consult", annF["payload"]["counter"].asText(), "counter falls back to queue type")

      // Tenant scoping: zones/rules/enqueue are hospital-scoped; foreign zone references denied.
      assertTrue(`data`(api(HttpMethod.GET, "/api/v1/queue/zones", token = recForeign)).isEmpty(), "foreign doctor sees no zones")
      assertEquals(
        400,
        api(HttpMethod.POST, "/api/v1/queue/announcement-rules", json("queueType" to "consult", "zoneId" to zoneId), recForeign).status,
        "rule referencing another hospital's zone denied",
      )
      assertEquals(
        400,
        api(HttpMethod.POST, "/api/v1/queue/announcements", json("texts" to mapOf("en" to "x"), "zoneId" to zoneId), recForeign).status,
        "enqueue into another hospital's zone denied",
      )

      // Validation guards.
      assertEquals(400, api(HttpMethod.POST, "/api/v1/queue/zones", json("servicePointId" to "SP1"), rec).status, "zone name required")
      assertEquals(400, api(HttpMethod.POST, "/api/v1/queue/zones", json("name" to "Loud", "volume" to 150), rec).status, "zone volume 0-100")
      assertEquals(
        400,
        api(HttpMethod.POST, "/api/v1/queue/announcement-rules", json("queueType" to "consult", "repeatCount" to 11), rec).status,
        "rule repeat 1-10",
      )
      assertEquals(400, api(HttpMethod.POST, "/api/v1/queue/announcements", json("texts" to mapOf("en" to " ")), rec).status, "announcement text required")
      assertEquals(
        400,
        api(HttpMethod.POST, "/api/v1/queue/announcements", json("texts" to mapOf("en" to "x"), "repeatCount" to 0), rec).status,
        "enqueue repeat 1-10",
      )
    }

    // 01.06 TV display: whole-hospital board grouped per counter — current/next per counter,
    // priority-ranked next list, department names resolved server-side, unassigned entries
    // omitted, NO patient identifiers on the public payload, and cross-tenant isolation.
    @Test
    fun tvDisplayBoardJourney() {
      val admin = adminToken()
      val s = suffix()
      val h1 =
        `data`(
          api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "Display H1 $s", "code" to "TB1-$s", "country" to "Qatar"), admin),
        )["id"].asText()
      val hF =
        `data`(
          api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "Display F $s", "code" to "TBF-$s", "country" to "Qatar"), admin),
        )["id"].asText()
      val rec = login(registerRoleUser("RECEPTIONIST", h1, s))
      val recForeign = login(registerRoleUser("RECEPTIONIST", hF, s, tag = "foreign"))

      // Department (resolved server-side: RECEPTIONIST holds no hospital-config read) + two counters.
      val dept =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/hospital-config/hospitals/$h1/departments",
            json("name" to "Cardiology $s", "code" to "CARD-$s", "type" to "clinical"),
            admin,
          ),
        )
      val deptId = dept["id"].asText()
      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/queue/service-points",
          json("name" to "Counter 1", "code" to "SP1", "queueType" to "consult", "counterNumber" to "1"),
          rec,
        ),
        "create counter 1",
      )
      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/queue/service-points",
          json("name" to "Counter 2", "code" to "SP2", "queueType" to "consult", "counterNumber" to "2"),
          rec,
        ),
        "create counter 2",
      )

      // Tokens: routine + urgent + emergency queued on Counter 1, one routine on Counter 2, one UNASSIGNED.
      val p1 = patientIn(admin, h1, "A", s)
      val p2 = patientIn(admin, h1, "B", s)
      val p3 = patientIn(admin, h1, "C", s)
      val p4 = patientIn(admin, h1, "D", s)
      val p5 = patientIn(admin, h1, "E", s)
      val token1 =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/queue/entries",
            json("queueType" to "consult", "patientId" to p1, "servicePointId" to "SP1", "departmentId" to deptId),
            rec,
          ),
        )["tokenNumber"].asText()
      val token2 =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/queue/entries",
            json("queueType" to "consult", "patientId" to p2, "priority" to "urgent", "servicePointId" to "SP1", "departmentId" to deptId),
            rec,
          ),
        )["tokenNumber"].asText()
      val token3 =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/queue/entries",
            json("queueType" to "consult", "patientId" to p3, "priority" to "emergency", "servicePointId" to "SP1", "departmentId" to deptId),
            rec,
          ),
        )["tokenNumber"].asText()
      val token4 =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/queue/entries",
            json("queueType" to "consult", "patientId" to p4, "servicePointId" to "SP2", "departmentId" to deptId),
            rec,
          ),
        )["tokenNumber"].asText()
      val tokenUnassigned =
        `data`(api(HttpMethod.POST, "/api/v1/queue/entries", json("queueType" to "consult", "patientId" to p5), rec))["tokenNumber"].asText()

      // Call next: the emergency token is served first and stamped on its counter.
      val called = `data`(api(HttpMethod.POST, "/api/v1/queue/entries/call-next", json("queueType" to "consult"), rec))
      assertEquals(token3, called["tokenNumber"].asText(), "call-next serves the emergency token first")

      // The board: Counter 1 shows the called token as current and the priority-ranked waiting tokens as next.
      val board = `data`(api(HttpMethod.GET, "/api/v1/queue/display-board", token = rec))
      assertNotNull(board["generatedAt"], "board generation timestamp present")
      val counters = board["counters"]
      assertEquals(2, counters.size(), "unassigned token omitted from the board")
      assertEquals("Counter 1", counters[0]["servicePointName"].asText(), "counters sorted by name")
      assertEquals("SP1", counters[0]["servicePointId"].asText())
      assertEquals(deptId, counters[0]["departmentId"].asText())
      assertEquals("Cardiology $s", counters[0]["departmentName"].asText(), "department name resolved server-side")
      val current = counters[0]["current"]
      assertEquals(token3, current?.at("/tokenNumber")?.asText(), "called token is the counter's current")
      assertEquals("emergency", current?.at("/priority")?.asText(), "current keeps its priority")
      val next = counters[0]["next"]
      assertEquals(2, next.size(), "next = the counter's waiting tokens")
      assertEquals(token2, next[0]["tokenNumber"].asText(), "next priority-ranked (urgent before routine)")
      assertEquals("urgent", next[0]["priority"].asText())
      assertEquals(token1, next[1]["tokenNumber"].asText())
      // Counter 2: nothing called yet, one waiting token.
      assertEquals("Counter 2", counters[1]["servicePointName"].asText())
      assertEquals(token4, counters[1]["next"][0]["tokenNumber"].asText())
      assertTrue(!counters[1].has("current") || counters[1]["current"].isNull(), "no token called on counter 2 yet")

      // Privacy: the public display payload carries no patient identifiers anywhere.
      counters.forEach { counter ->
        listOfNotNull(counter["current"]).plus(counter["next"]).forEach { token ->
          assertTrue(!token.has("patientName"), "display token carries no patient name")
          assertTrue(!token.has("patientId"), "display token carries no patient id")
        }
      }
      assertTrue(!board.toString().contains(tokenUnassigned), "unassigned token omitted entirely")
      assertTrue(!board.toString().contains(p1), "no patient ids in the board payload")

      // Cross-tenant: another hospital's display sees an empty board.
      val foreignBoard = `data`(api(HttpMethod.GET, "/api/v1/queue/display-board", token = recForeign))
      assertEquals(0, foreignBoard["counters"].size(), "cross-tenant board is empty")
    }

    private fun connectRealtime(token: String): RealtimeCollector {
      val collector = RealtimeCollector()
      org.springframework.web.socket.client.standard
        .StandardWebSocketClient()
        .execute(collector, "ws://localhost:$port/realtime?token=$token")
        .get(15, java.util.concurrent.TimeUnit.SECONDS)
      return collector
    }

    // Consumer lag can replay pre-connect frames of the same event type; scope
    // the wait to a frame actually carrying this content when events repeat.
    private fun awaitEventFrame(
      collector: RealtimeCollector,
      eventType: String,
      timeoutMs: Long,
      mustContain: String? = null,
    ): String? {
      val deadline = System.currentTimeMillis() + timeoutMs
      while (System.currentTimeMillis() < deadline) {
        collector.messages
          .firstOrNull { it.contains("\"type\":\"event.received\"") && it.contains(eventType) && (mustContain == null || it.contains(mustContain)) }
          ?.let { return it }
        Thread.sleep(250)
      }
      return null
    }
  }

  /** 01.08 kiosk-lite PIT — ABHA/address/mobile/QR check-in, idempotency, tenancy and KIOSK role bounds. */
  @Nested
  inner class KioskTests {
    private fun registerRoleUser(
      role: String,
      hospitalId: String,
      s: String,
      tag: String = role,
    ): String {
      val email = "kiosk-${tag.lowercase()}-$s@test.local"
      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/auth/register",
          json(
            "email" to email,
            "password" to "Passw0rd!123",
            "firstName" to "Kiosk",
            "lastName" to "$role$s",
            "roles" to listOf(role),
            "hospitalId" to hospitalId,
          ),
        ),
        "register $role $email",
      )
      return email
    }

    private fun login(email: String): String {
      val login = api(HttpMethod.POST, "/api/v1/auth/login", json("username" to email, "password" to "Passw0rd!123"))
      assert2xx(login, "login $email: ${login.body.take(200)}")
      return `data`(login)["accessToken"].asText()
    }

    private fun errCode(resp: ApiResp): String = om.readTree(resp.body)["error"]["code"].asText()

    private fun adminUserId(): String {
      val login =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/auth/login",
            json("username" to "admin@squamed.local", "password" to "Admin@12345"),
          ),
        )
      return login.get("userId")?.asText() ?: login.get("user").get("id").asText()
    }

    @Test
    fun kioskCheckInJourney() {
      val admin = adminToken()
      val adminId = adminUserId()
      val s = suffix()
      val h1 =
        `data`(
          api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "Kiosk L $s", "code" to "KSL-$s", "country" to "Qatar"), admin),
        )["id"].asText()
      val hF =
        `data`(
          api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "Kiosk F $s", "code" to "KSF-$s", "country" to "Qatar"), admin),
        )["id"].asText()
      val kiosk = login(registerRoleUser("KIOSK", h1, s))
      val kioskForeign = login(registerRoleUser("KIOSK", hF, s, tag = "foreign"))
      val rec = login(registerRoleUser("RECEPTIONIST", h1, s, tag = "rec"))

      // Department with wayfinding description; KIOSK may read it, RECEPTIONIST (no hospital-config
      // grants in V54) may not — the kiosk role must carry exactly the kiosk's needs.
      val dept =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/hospital-config/hospitals/$h1/departments",
            json("name" to "Kiosk Dept $s", "code" to "KD-$s", "type" to "outpatient", "description" to "First floor, past the pharmacy"),
            admin,
          ),
        )
      val departmentId = dept["id"].asText()
      val deptName = dept["name"].asText()
      assertEquals(403, api(HttpMethod.GET, "/api/v1/hospital-config/hospitals/$h1/departments", token = rec).status, "RECEPTIONIST must not list departments")
      val depts = `data`(api(HttpMethod.GET, "/api/v1/hospital-config/hospitals/$h1/departments", token = kiosk))
      assertTrue(depts.any { it["id"].asText() == departmentId }, "KIOSK reads the department catalog: $depts")

      // Patients: one with ABHA number + address, one mobile-only, two sharing a phone (ambiguity),
      // one with no appointment today.
      val abhaNumber = "90%012d".format(System.currentTimeMillis() % 1_000_000_000_000L)
      val abhaAddress = "kiosk.rao.%010d@abdm".format(System.currentTimeMillis() % 10_000_000_000L)
      val unknownAbha = "91%012d".format(System.currentTimeMillis() % 1_000_000_000_000L)
      val pAbha =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/patients",
            json(
              "firstName" to "Kiran",
              "lastName" to "Rao$s",
              "gender" to "male",
              "dateOfBirth" to "1988-03-03",
              // Tail tag A: same-ms creates must not collide after takeLast(12) truncation (CI run 44162724119).
              "phone" to ("55${System.currentTimeMillis()}A").takeLast(12),
              "hospitalId" to h1,
            ),
            admin,
          ),
        )["patient"]["id"].asText()
      assert2xx(api(HttpMethod.POST, "/api/v1/patients/$pAbha/identifiers", json("idType" to "ABHA", "idValue" to abhaNumber), admin), "add ABHA number")
      assert2xx(
        api(HttpMethod.POST, "/api/v1/patients/$pAbha/identifiers", json("idType" to "ABHA_ADDRESS", "idValue" to abhaAddress), admin),
        "add ABHA address",
      )

      val pPhone =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/patients",
            json(
              "firstName" to "Mira",
              "lastName" to "Nair$s",
              "gender" to "female",
              "dateOfBirth" to "1992-07-07",
              // Tail tag B: same-ms creates must not collide after takeLast(12) truncation (CI run 44162724119).
              "phone" to ("55${System.currentTimeMillis()}B").takeLast(12),
              "hospitalId" to h1,
            ),
            admin,
          ),
        )["patient"]["id"].asText()
      val sharedPhone = "77${System.currentTimeMillis()}".take(12)
      val pAmb1 =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/patients",
            json(
              "firstName" to "Am",
              "lastName" to "Big$s",
              "gender" to "male",
              "dateOfBirth" to "1970-01-01",
              "phone" to sharedPhone,
              "hospitalId" to h1,
            ),
            admin,
          ),
        )["patient"]["id"].asText()
      val pAmb2 =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/patients",
            json(
              "firstName" to "Am2",
              "lastName" to "Big$s",
              "gender" to "male",
              "dateOfBirth" to "1971-01-01",
              "phone" to sharedPhone,
              "hospitalId" to h1,
            ),
            admin,
          ),
        )["patient"]["id"].asText()
      val pLate =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/patients",
            json(
              "firstName" to "Late",
              "lastName" to "Comer$s",
              "gender" to "female",
              "dateOfBirth" to "1980-02-02",
              // Tail tag C: same-ms creates must not collide after takeLast(12) truncation (CI run 44162724119).
              "phone" to ("55${System.currentTimeMillis()}C").takeLast(12),
              "hospitalId" to h1,
            ),
            admin,
          ),
        )["patient"]["id"].asText()

      fun appointment(patientId: String): String =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/appointments",
            json(
              "hospitalId" to h1,
              "patientId" to patientId,
              "departmentId" to departmentId,
              "doctorId" to adminId,
              "appointmentType" to "OPD",
              // Fixed local noon: now+1h crosses local midnight for runs starting 23:00-00:00 and
              // kiosk's findTodayAppointment then misses it (KIOSK_NO_APPOINTMENT, CI 44633955177).
              "scheduledStart" to
                java.time.LocalDate
                  .now()
                  .atTime(12, 0)
                  .atZone(java.time.ZoneId.systemDefault())
                  .toInstant()
                  .toString(),
              "scheduledEnd" to
                java.time.LocalDate
                  .now()
                  .atTime(12, 30)
                  .atZone(java.time.ZoneId.systemDefault())
                  .toInstant()
                  .toString(),
              "durationMinutes" to 30,
              "reason" to "kiosk journey",
              "priority" to "normal",
            ),
            admin,
          ),
        )["id"].asText()
      val apptAbha = appointment(pAbha)
      appointment(pPhone)

      // ABHA check-in: slip carries the token, the appointment context and the wayfinding department.
      val slipAbhaResp = api(HttpMethod.POST, "/api/v1/kiosk/check-in", json("identifierType" to "abha", "identifier" to abhaNumber), kiosk)
      assert2xx(slipAbhaResp, "abha check-in")
      val slipAbha = om.readTree(slipAbhaResp.body)["data"]
      assertEquals("C-01", slipAbha["token"]["tokenNumber"].asText(), "fresh hospital daily token sequence: $slipAbha")
      assertEquals(apptAbha, slipAbha["appointmentId"].asText(), "slip references today's appointment")
      assertEquals(deptName, slipAbha["departmentName"].asText(), "slip wayfinding department name")
      assertEquals("SQM:$h1:consult:C-01:${LocalDate.now()}", slipAbha["token"]["qrPayload"].asText(), "QR queue id on the slip")
      assertTrue(slipAbha["patientName"].asText().contains("Rao$s"), "patient's own name on the slip")
      assert2xx(api(HttpMethod.GET, "/api/v1/queue/board?queueType=consult", token = kiosk), "kiosk reads the board (queue:read)")

      // Idempotent re-check-in: appointment already arrived and entry live → same token, no duplicate.
      val again =
        `data`(
          api(HttpMethod.POST, "/api/v1/kiosk/check-in", json("identifierType" to "abha", "identifier" to abhaNumber), kiosk),
        )
      assertEquals("C-01", again["token"]["tokenNumber"].asText(), "re-check-in reuses the live entry")
      val boardAfterRecheck = `data`(api(HttpMethod.GET, "/api/v1/queue/board?queueType=consult", token = kiosk))
      assertEquals(1, boardAfterRecheck.size(), "no duplicate entry after re-check-in: $boardAfterRecheck")

      // Mobile path.
      val pPhonePhone = `data`(api(HttpMethod.GET, "/api/v1/patients/$pPhone", token = admin))["phone"].asText()
      val slipPhone =
        `data`(
          api(HttpMethod.POST, "/api/v1/kiosk/check-in", json("identifierType" to "mobile", "identifier" to pPhonePhone), kiosk),
        )
      assertEquals("C-02", slipPhone["token"]["tokenNumber"].asText(), "mobile check-in gets the next token")

      // QR re-entry / re-print: same token, no state change (entry stays waiting, board unchanged).
      val boardBeforeQr = `data`(api(HttpMethod.GET, "/api/v1/queue/board?queueType=consult", token = kiosk))
      val qrReentry =
        `data`(
          api(HttpMethod.POST, "/api/v1/kiosk/check-in", json("identifierType" to "qr", "identifier" to slipAbha["token"]["qrPayload"].asText()), kiosk),
        )
      assertEquals("C-01", qrReentry["token"]["tokenNumber"].asText(), "QR re-entry re-prints the same token")
      val boardAfterQr = `data`(api(HttpMethod.GET, "/api/v1/queue/board?queueType=consult", token = kiosk))
      assertEquals(boardBeforeQr.size(), boardAfterQr.size(), "QR re-entry must not touch the queue")

      // Tenancy: another hospital's QR, garbage QR, foreign kiosk resolving this hospital's patient.
      assertEquals(
        "KIOSK_QR_INVALID",
        errCode(
          api(HttpMethod.POST, "/api/v1/kiosk/check-in", json("identifierType" to "qr", "identifier" to "SQM:$hF:consult:C-01:${LocalDate.now()}"), kiosk),
        ),
        "foreign QR must be rejected without leaking existence",
      )
      assertEquals(
        "KIOSK_QR_INVALID",
        errCode(api(HttpMethod.POST, "/api/v1/kiosk/check-in", json("identifierType" to "qr", "identifier" to "junk"), kiosk)),
        "garbage QR",
      )
      assertEquals(
        "KIOSK_PATIENT_NOT_FOUND",
        errCode(api(HttpMethod.POST, "/api/v1/kiosk/check-in", json("identifierType" to "abha", "identifier" to abhaNumber), kioskForeign)),
        "foreign kiosk cannot resolve another hospital's patient",
      )

      // Identifier validation and resolution failures.
      assertEquals(
        "KIOSK_PATIENT_NOT_FOUND",
        errCode(
          api(
            HttpMethod.POST,
            "/api/v1/kiosk/check-in",
            json(
              "identifierType" to "abha",
              "identifier" to unknownAbha,
            ),
            kiosk,
          ),
        ),
        "unknown ABHA",
      )
      assertEquals(
        "KIOSK_NO_APPOINTMENT",
        errCode(
          api(
            HttpMethod.POST,
            "/api/v1/kiosk/check-in",
            json(
              "identifierType" to "mobile",
              "identifier" to `data`(api(HttpMethod.GET, "/api/v1/patients/$pLate", token = admin))["phone"].asText(),
            ),
            kiosk,
          ),
        ),
        "no appointment today",
      )
      assertEquals(
        "KIOSK_IDENTIFIER_TYPE_INVALID",
        errCode(
          api(
            HttpMethod.POST,
            "/api/v1/kiosk/check-in",
            json(
              "identifierType" to "passport",
              "identifier" to "X",
            ),
            kiosk,
          ),
        ),
        "unsupported identifier type",
      )
      assertEquals(
        "ABHA_NUMBER_INVALID",
        errCode(api(HttpMethod.POST, "/api/v1/kiosk/check-in", json("identifierType" to "abha", "identifier" to "1234"), kiosk)),
        "ABHA below 14 digits",
      )
      assertEquals(
        "ABHA_ADDRESS_INVALID",
        errCode(
          api(
            HttpMethod.POST,
            "/api/v1/kiosk/check-in",
            json(
              "identifierType" to "abha_address",
              "identifier" to "bad address",
            ),
            kiosk,
          ),
        ),
        "malformed ABHA address",
      )
      assert2xx(
        api(HttpMethod.POST, "/api/v1/kiosk/check-in", json("identifierType" to "abha_address", "identifier" to abhaAddress), kiosk),
        "ABHA address check-in works",
      )
      assertEquals(
        "KIOSK_IDENTIFIER_AMBIGUOUS",
        errCode(api(HttpMethod.POST, "/api/v1/kiosk/check-in", json("identifierType" to "mobile", "identifier" to sharedPhone), kiosk)),
        "two patients on one phone are ambiguous at a kiosk",
      )

      // KIOSK is read/patient-scoped: patient writes must be denied (403).
      assertEquals(
        403,
        api(
          HttpMethod.POST,
          "/api/v1/patients",
          json("firstName" to "No", "lastName" to "Write$s", "gender" to "male", "dateOfBirth" to "1990-01-01"),
          kiosk,
        ).status,
        "KIOSK must not write patients",
      )

      // Soak seed: 10 check-ins in a row, all accepted, all distinct tokens (demo loop mirrors this x5).
      val burstTokens = mutableListOf<String>()
      for (i in 0 until 10) {
        val p =
          `data`(
            api(
              HttpMethod.POST,
              "/api/v1/patients",
              json(
                "firstName" to "Burst$i",
                "lastName" to "Kiosk$s",
                "gender" to "male",
                "dateOfBirth" to "1985-05-05",
                "phone" to ("55$s$i".replace("-", "")).takeLast(12),
                "hospitalId" to h1,
              ),
              admin,
            ),
          )["patient"]["id"].asText()
        val phone = `data`(api(HttpMethod.GET, "/api/v1/patients/$p", token = admin))["phone"].asText()
        appointment(p)
        val slip =
          `data`(
            api(HttpMethod.POST, "/api/v1/kiosk/check-in", json("identifierType" to "mobile", "identifier" to phone), kiosk),
          )
        assertEquals(
          200,
          api(HttpMethod.GET, "/api/v1/queue/entries/consult/${slip["token"]["tokenNumber"].asText()}/reprint", token = kiosk).status,
          "burst token ${i + 1} resolvable",
        )
        burstTokens.add(slip["token"]["tokenNumber"].asText())
      }
      assertEquals(10, burstTokens.distinct().size, "burst check-ins must each get a distinct token: $burstTokens")
    }
  }

  /** 01.03: reception workspace PIT — passes, consult-fee receipts, cancel, calling, tenancy. */
  @Nested
  inner class ReceptionWorkspaceTests {
    private fun registerRoleUser(
      role: String,
      hospitalId: String,
      s: String,
      tag: String = role,
    ): String {
      val email = "recep-${tag.lowercase()}-$s@test.local"
      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/auth/register",
          json(
            "email" to email,
            "password" to "Passw0rd!123",
            "firstName" to "Recep",
            "lastName" to "$role$s",
            "roles" to listOf(role),
            "hospitalId" to hospitalId,
          ),
        ),
        "register $role $email",
      )
      return email
    }

    private fun login(email: String): String {
      val login = api(HttpMethod.POST, "/api/v1/auth/login", json("username" to email, "password" to "Passw0rd!123"))
      assert2xx(login, "login $email: ${login.body.take(200)}")
      return `data`(login)["accessToken"].asText()
    }

    /** Admin creates the patient with an explicit hospitalId (admin is not hospital-bound). */
    private fun patientIn(
      admin: String,
      hospitalId: String,
      tag: String,
      s: String,
    ): String =
      `data`(
        api(
          HttpMethod.POST,
          "/api/v1/patients",
          json(
            "firstName" to "Recep",
            "lastName" to "$tag$s",
            "gender" to "female",
            "dateOfBirth" to "1990-02-02",
            "hospitalId" to hospitalId,
          ),
          admin,
        ),
      )["patient"]["id"].asText()

    @Test
    fun receptionWorkspaceNamedContentsCoverage() {
      val admin = adminToken()
      val s = suffix()
      // Fresh hospital per run: pass/receipt sequences (VPS-/RCP-) would collide with leftovers
      // in the shared seed hospital (same lesson as 01.02's token sequences).
      val h =
        `data`(
          api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "Recep L $s", "code" to "RCL-$s", "country" to "Qatar"), admin),
        )["id"].asText()
      val hForeign =
        `data`(
          api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "Recep F $s", "code" to "RCF-$s", "country" to "Qatar"), admin),
        )["id"].asText()
      val rec = login(registerRoleUser("RECEPTIONIST", h, s))
      val recForeign = login(registerRoleUser("RECEPTIONIST", hForeign, s, tag = "foreign"))

      val patient = patientIn(admin, h, "Life", s)
      // Registration fast-path: walk-in visit with EMPI lookup riding the patient module.
      val visit =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/reception/visits",
            json("patientId" to patient, "visitType" to "outpatient", "reason" to "reception workspace"),
            rec,
          ),
        )["visit"]
      val visitId = visit["id"].asText()
      assertEquals("VN-", visit["visitNumber"].asText().take(3), "visit number format")
      assert2xx(api(HttpMethod.PATCH, "/api/v1/reception/visits/$visitId/check-in", null, rec), "check-in")

      // Visitor/attendant passes: per-hospital sequence, return + state guard.
      val pass1 =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/reception/visits/$visitId/passes",
            json(
              "visitorName" to "Ali Attendant",
              "relation" to "attendant",
              "phone" to "+9745000001",
            ),
            rec,
          ),
        )
      assertEquals("VPS-0001", pass1["passNumber"].asText(), "first pass number")
      assertEquals("active", pass1["status"].asText())
      `data`(api(HttpMethod.POST, "/api/v1/reception/visits/$visitId/passes", json("visitorName" to "Sara Visitor", "relation" to "visitor"), rec))
      val passes = `data`(api(HttpMethod.GET, "/api/v1/reception/visits/$visitId/passes", null, rec))
      assertEquals(2, passes.size(), "two passes listed")
      val returned = `data`(api(HttpMethod.PATCH, "/api/v1/reception/passes/${pass1["id"].asText()}/return", null, rec))
      assertEquals("returned", returned["status"].asText(), "pass return closes it")
      assertNotNull(returned["returnedAt"].asText(), "returnedAt stamped")
      assertEquals(
        409,
        api(HttpMethod.PATCH, "/api/v1/reception/passes/${pass1["id"].asText()}/return", null, rec).status,
        "re-returning a closed pass is a conflict",
      )

      // Consult-fee receipts: issue → void (audit kept) → sequence continues.
      val rcp1 =
        `data`(
          api(HttpMethod.POST, "/api/v1/reception/visits/$visitId/receipts", json("amountQar" to 500, "mode" to "cash"), rec),
        )
      assertEquals("RCP-0001", rcp1["receiptNumber"].asText(), "first receipt number")
      assertEquals(0, java.math.BigDecimal("500").compareTo(rcp1["amountQar"].decimalValue()), "amount carried")
      val voided = `data`(api(HttpMethod.PATCH, "/api/v1/reception/receipts/${rcp1["id"].asText()}/void", null, rec))
      assertEquals("voided", voided["status"].asText(), "void keeps the row (audit trail)")
      assertNotNull(voided["voidedAt"].asText(), "voidedAt stamped")
      assertEquals(
        409,
        api(HttpMethod.PATCH, "/api/v1/reception/receipts/${rcp1["id"].asText()}/void", null, rec).status,
        "re-voiding a voided receipt is a conflict",
      )
      val rcp2 = `data`(api(HttpMethod.POST, "/api/v1/reception/visits/$visitId/receipts", json("amountQar" to 300, "mode" to "card"), rec))
      assertEquals("RCP-0002", rcp2["receiptNumber"].asText(), "receipt sequence continues after void")
      val receipts = `data`(api(HttpMethod.GET, "/api/v1/reception/visits/$visitId/receipts", null, rec))
      assertEquals(2, receipts.size(), "both receipts listed (1 voided + 1 issued)")

      // Patient calling from the workspace: queue call + call-next with reception perms (queue:manage:queue).
      val entry = `data`(api(HttpMethod.POST, "/api/v1/queue/entries", json("queueType" to "consult", "patientId" to patient), rec))
      assertEquals("C-01", entry["tokenNumber"].asText(), "fresh hospital token sequence")
      val called = `data`(api(HttpMethod.PATCH, "/api/v1/queue/entries/consult/C-01/call", null, rec))
      assertEquals("called", called["status"].asText(), "workspace calls a specific token")

      // Cancel with reason; closed visits refuse new passes/receipts (409).
      val cancelled = `data`(api(HttpMethod.PATCH, "/api/v1/reception/visits/$visitId/cancel", json("reason" to "no show"), rec))
      assertEquals("cancelled", cancelled["status"].asText())
      assertTrue(
        api(HttpMethod.POST, "/api/v1/reception/visits/$visitId/passes", json("visitorName" to "Late"), rec).body.contains("VISIT_CANCELLED"),
        "cancelled visit refuses pass issue",
      )

      // Cross-tenant: foreign receptionist denied on this hospital's visit + passes + receipts
      // (requireSameHospital -> 403 ACCESS_DENIED, the reception module's existing convention);
      // their own-hospital workspace works with its own sequence (VPS-0001).
      assertEquals(403, api(HttpMethod.GET, "/api/v1/reception/visits/$visitId", token = recForeign).status, "cross-tenant visit read denied")
      assertEquals(403, api(HttpMethod.GET, "/api/v1/reception/visits/$visitId/passes", null, recForeign).status, "cross-tenant pass list denied")
      assertEquals(403, api(HttpMethod.GET, "/api/v1/reception/visits/$visitId/receipts", null, recForeign).status, "cross-tenant receipt list denied")
      val fPatient = patientIn(admin, hForeign, "F", s)
      val fVisit = `data`(api(HttpMethod.POST, "/api/v1/reception/visits", json("patientId" to fPatient), recForeign))["visit"]
      val fPass =
        `data`(
          api(HttpMethod.POST, "/api/v1/reception/visits/${fVisit["id"].asText()}/passes", json("visitorName" to "Foreign Attendant"), recForeign),
        )
      assertEquals("VPS-0001", fPass["passNumber"].asText(), "foreign hospital pass sequence starts at 0001 (hospital-scoped)")
    }
  }

  @Nested
  inner class EmergencyFastPathTests {
    private fun errCode(resp: ApiResp): String = om.readTree(resp.body)["error"]["code"].asText()

    private fun registerRoleUser(
      role: String,
      hospitalId: String,
      s: String,
      tag: String = role,
    ): String {
      val email = "efp-${tag.lowercase()}-$s@test.local"
      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/auth/register",
          json(
            "email" to email,
            "password" to "Passw0rd!123",
            "firstName" to "FastPath",
            "lastName" to "$role$s",
            "roles" to listOf(role),
            "hospitalId" to hospitalId,
          ),
        ),
        "register $role $email",
      )
      return email
    }

    private fun login(email: String): String {
      val login = api(HttpMethod.POST, "/api/v1/auth/login", json("username" to email, "password" to "Passw0rd!123"))
      assert2xx(login, "login $email: ${login.body.take(200)}")
      return `data`(login)["accessToken"].asText()
    }

    private fun createTaggedPatient(
      token: String,
      hospitalId: String,
      first: String,
      last: String,
      s: String,
      tag: String,
    ): String =
      `data`(
        api(
          HttpMethod.POST,
          "/api/v1/patients",
          json(
            "firstName" to first,
            "lastName" to "$last$s",
            "gender" to "male",
            "dateOfBirth" to "1980-01-01",
            // Tail tag: same-ms creates must not collide after takeLast(12) truncation (01.08 CI lesson).
            "phone" to ("55${System.currentTimeMillis()}$tag").takeLast(12),
            "hospitalId" to hospitalId,
          ),
          token,
        ),
      )["patient"]["id"].asText()

    @Test
    fun emergencyFastPathAndDowntimePack() {
      val admin = adminToken()
      val s = suffix()
      val h1 =
        `data`(
          api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "FastPath L $s", "code" to "FPL-$s", "country" to "Qatar"), admin),
        )["id"].asText()
      val rec = login(registerRoleUser("RECEPTIONIST", h1, s, tag = "rec"))

      // Walk-ins A/B/C join today's live queue as routine; board is insertion-ordered.
      val pA = createTaggedPatient(admin, h1, "Arjun", "Walkin", s, "A")
      val pB = createTaggedPatient(admin, h1, "Beena", "Walkin", s, "B")
      val pC = createTaggedPatient(admin, h1, "Chirag", "Walkin", s, "C")
      val entryA =
        `data`(
          api(HttpMethod.POST, "/api/v1/queue/entries", json("queueType" to "consult", "patientId" to pA, "patientName" to "Arjun", "hospitalId" to h1), admin),
        )
      val entryB =
        `data`(
          api(HttpMethod.POST, "/api/v1/queue/entries", json("queueType" to "consult", "patientId" to pB, "patientName" to "Beena", "hospitalId" to h1), admin),
        )
      val entryC =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/queue/entries",
            json("queueType" to "consult", "patientId" to pC, "patientName" to "Chirag", "hospitalId" to h1),
            admin,
          ),
        )
      val tokenA = entryA["tokenNumber"].asText()
      val tokenB = entryB["tokenNumber"].asText()
      val tokenC = entryC["tokenNumber"].asText()
      assertEquals("C-01", tokenA, "fresh hospital consult queue starts at C-01")
      assertEquals("C-02", tokenB, "token sequence per queue type")

      val board0 = `data`(api(HttpMethod.GET, "/api/v1/queue/board?queueType=consult", token = rec))
      assertTrue(board0.any { it["tokenNumber"].asText() == tokenA && it["priority"].asText() == "routine" }, "walk-in A waiting routine: $board0")

      // Triage-lite validation before any boost.
      val noTag = api(HttpMethod.POST, "/api/v1/queue/entries/consult/$tokenA/triage-lite", json(), rec)
      assertEquals(400, noTag.status, "tag required -> ${noTag.body.take(200)}")
      val badTag = api(HttpMethod.POST, "/api/v1/queue/entries/consult/$tokenA/triage-lite", json("tag" to "purple"), rec)
      assertEquals(400, badTag.status, "unknown tag rejected -> ${badTag.body.take(200)}")
      assertEquals("TRIAGE_TAG_INVALID", errCode(badTag), "bad tag error code")
      val missing = api(HttpMethod.POST, "/api/v1/queue/entries/consult/ZZ-99/triage-lite", json("tag" to "red"), rec)
      assertEquals(400, missing.status, "unknown token rejected -> ${missing.body.take(200)}")

      // Yellow -> urgent, green -> stays routine, red -> emergency.
      val yellow = `data`(api(HttpMethod.POST, "/api/v1/queue/entries/consult/$tokenB/triage-lite", json("tag" to "yellow"), rec))
      assertEquals("urgent", yellow["priority"].asText(), "yellow tag boosts to urgent")
      val green = `data`(api(HttpMethod.POST, "/api/v1/queue/entries/consult/$tokenC/triage-lite", json("tag" to "green"), rec))
      assertEquals("routine", green["priority"].asText(), "green tag leaves priority as-is")
      val red = `data`(api(HttpMethod.POST, "/api/v1/queue/entries/consult/$tokenA/triage-lite", json("tag" to "red"), rec))
      assertEquals("emergency", red["priority"].asText(), "red tag boosts to emergency")

      val board = `data`(api(HttpMethod.GET, "/api/v1/queue/board?queueType=consult", token = rec))
      val ordered = board.map { it["tokenNumber"].asText() }
      assertTrue(ordered.indexOf(tokenA) < ordered.indexOf(tokenB), "red ranks before yellow on the board: $ordered")
      assertTrue(ordered.indexOf(tokenB) < ordered.indexOf(tokenC), "urgent ranks before routine: $ordered")

      // Boost ordering holds when the counter pulls: red first, then yellow, routine last.
      assertEquals(
        tokenA,
        `data`(api(HttpMethod.POST, "/api/v1/queue/entries/call-next", json("queueType" to "consult"), rec))["tokenNumber"].asText(),
        "call-next pulls the red-tagged token first",
      )
      assertEquals(
        tokenB,
        `data`(api(HttpMethod.POST, "/api/v1/queue/entries/call-next", json("queueType" to "consult"), rec))["tokenNumber"].asText(),
        "call-next pulls the yellow token before routine",
      )

      // CriticalQueueEvent (contract EVENTS: urgent arrival) + display bump event on red.
      val catalog = `data`(api(HttpMethod.GET, "/api/v1/events/catalog"))
      assertTrue(
        catalog.any { it["name"].asText() == "CriticalQueueEvent" && it["status"].asText() == "EMITTED" },
        "CriticalQueueEvent cataloged EMITTED: $catalog",
      )
      val outbox = `data`(api(HttpMethod.GET, "/api/v1/admin/events/outbox?limit=200", token = admin))
      assertTrue(
        outbox.any {
          it["eventType"].asText() == "queue.urgent_arrival" && it["envelope"].asText().contains(tokenA)
        },
        "queue.urgent_arrival outbox row present",
      )
      assertTrue(
        outbox.any {
          it["eventType"].asText() == "queue.bumped" && it["envelope"].asText().contains(tokenA)
        },
        "queue.bumped outbox row present (displays escalate)",
      )

      // Role-less user never boosts.
      val noPerm = noRoleToken()
      assertEquals(
        403,
        api(HttpMethod.POST, "/api/v1/queue/entries/consult/$tokenA/triage-lite", json("tag" to "red"), noPerm).status,
        "triage-lite denied for role-less user",
      )

      // --- Downtime pack v1 (CS-09): window -> paper tokens/check-ins -> re-entry restores without loss.
      val noReason = api(HttpMethod.POST, "/api/v1/downtime/windows", json("hospitalId" to h1), rec)
      assertEquals(400, noReason.status, "downtime reason required")
      val openWindow = `data`(api(HttpMethod.POST, "/api/v1/downtime/windows", json("reason" to "PG outage drill $s", "hospitalId" to h1), rec))
      assertNotNull(openWindow["id"])
      assertTrue(openWindow["open"].asBoolean(), "window opens open")
      val dupOpen = api(HttpMethod.POST, "/api/v1/downtime/windows", json("reason" to "second window", "hospitalId" to h1), rec)
      assertEquals(409, dupOpen.status, "second open window rejected -> ${dupOpen.body.take(200)}")
      assertEquals("DOWNTIME_ALREADY_OPEN", errCode(dupOpen), "open-window guard")

      val windows = `data`(api(HttpMethod.GET, "/api/v1/downtime/windows", token = rec))
      assertEquals(1, windows.size(), "one window listed for the fresh hospital")

      // Paper tokens issued during the outage keep their written numbers on re-entry.
      val pD = createTaggedPatient(admin, h1, "Devika", "Paper", s, "D")
      val pE = createTaggedPatient(admin, h1, "Eshan", "Paper", s, "E")
      val reentry =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/downtime/reentries",
            json(
              "hospitalId" to h1,
              "tokens" to
                listOf(
                  mapOf("queueType" to "consult", "tokenNumber" to "DT-01", "patientId" to pD, "patientName" to "Devika"),
                  mapOf("queueType" to "consult", "tokenNumber" to "DT-02", "patientId" to pE, "patientName" to "Eshan", "priority" to "urgent"),
                ),
            ),
            rec,
          ),
        )
      assertEquals(2, reentry["tokens"].size(), "both paper tokens re-entered")
      val dt1 = reentry["tokens"][0]
      assertEquals("DT-01", dt1["tokenNumber"].asText(), "paper token number preserved")
      assertEquals("routine", dt1["priority"].asText(), "untagged paper token defaults to routine")

      val dupToken =
        api(
          HttpMethod.POST,
          "/api/v1/downtime/reentries",
          json("hospitalId" to h1, "tokens" to listOf(mapOf("queueType" to "consult", "tokenNumber" to "DT-01", "patientId" to pD))),
          rec,
        )
      assertEquals(409, dupToken.status, "paper token number already live -> ${dupToken.body.take(200)}")
      assertEquals("DOWNTIME_TOKEN_EXISTS", errCode(dupToken), "duplicate paper token rejected, nothing silently dropped")

      // Paper check-in: a pre-booked visit marked checked-in on paper re-stamps the visit.
      val pF = createTaggedPatient(admin, h1, "Farah", "Paper", s, "F")
      val visit =
        `data`(
          api(HttpMethod.POST, "/api/v1/reception/visits", json("patientId" to pF, "hospitalId" to h1, "reason" to "pre-booked before outage"), admin),
        )["visit"]
      val visitId = visit["id"].asText()
      assertEquals("registered", visit["status"].asText(), "visit starts unregistered-checkin")
      `data`(api(HttpMethod.POST, "/api/v1/downtime/reentries", json("hospitalId" to h1, "visitIds" to listOf(visitId)), rec))
      assertEquals("checked-in", `data`(api(HttpMethod.GET, "/api/v1/reception/visits/$visitId", token = rec))["status"].asText(), "paper check-in restored")

      // Close the window; closing twice is rejected.
      val closed = `data`(api(HttpMethod.PATCH, "/api/v1/downtime/windows/${openWindow["id"].asText()}/close", json(), rec))
      assertFalse(closed["open"].asBoolean(), "window closed")
      assertNotNull(closed["endedAt"], "close stamps endedAt")
      val reclose = api(HttpMethod.PATCH, "/api/v1/downtime/windows/${openWindow["id"].asText()}/close", json(), rec)
      assertEquals(409, reclose.status, "already-closed window rejected")
      assertEquals("DOWNTIME_NOT_OPEN", errCode(reclose), "reclose error code")

      // Nothing was lost: all five consult tokens (3 live + 2 paper) still on the board.
      val boardAfter = `data`(api(HttpMethod.GET, "/api/v1/queue/board?queueType=consult", token = rec))
      val tokensAfter = boardAfter.map { it["tokenNumber"].asText() }
      assertTrue(tokensAfter.containsAll(listOf("DT-01", "DT-02", tokenC)), "re-entered tokens live next to originals: $tokensAfter")
    }
  }

  @Nested
  inner class SchedulingEngineTests {
    private fun registerRoleUser(
      role: String,
      hospitalId: String,
      s: String,
      tag: String = role,
    ): String {
      val email = "sched-${tag.lowercase()}-$s@test.local"
      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/auth/register",
          json(
            "email" to email,
            "password" to "Passw0rd!123",
            "firstName" to "Sched",
            "lastName" to "$role$s",
            "roles" to listOf(role),
            "hospitalId" to hospitalId,
          ),
        ),
        "register $role $email",
      )
      return email
    }

    private fun login(email: String): String {
      val login = api(HttpMethod.POST, "/api/v1/auth/login", json("username" to email, "password" to "Passw0rd!123"))
      assert2xx(login, "login $email: ${login.body.take(200)}")
      return `data`(login)["accessToken"].asText()
    }

    /** Admin creates the patient with an explicit hospitalId (admin is not hospital-bound). */
    private fun patientIn(
      admin: String,
      hospitalId: String,
      tag: String,
      s: String,
    ): String =
      `data`(
        api(
          HttpMethod.POST,
          "/api/v1/patients",
          json(
            "firstName" to "Sched",
            "lastName" to "$tag$s",
            "gender" to "female",
            "dateOfBirth" to "1990-02-02",
            "hospitalId" to hospitalId,
          ),
          admin,
        ),
      )["patient"]["id"].asText()

    private fun slotsOfDay(
      token: String,
      doctorId: String,
      day: LocalDate,
    ): ApiResp = api(HttpMethod.GET, "/api/v1/schedules/$doctorId/slots?from=$day&to=$day", token = token)

    @Test
    fun schedulingEngineBookingLifecycle() {
      val admin = adminToken()
      val adminId = `data`(api(HttpMethod.GET, "/api/v1/auth/me", token = admin))["userId"].asText()
      val s = suffix()
      // Fresh hospital per run: slot keys (hospital, doctor, date, start) are unique, and leftover
      // slots from earlier runs in the shared seed hospital would shadow a new template's slots.
      val hid =
        `data`(
          api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "Sched L $s", "code" to "SCL-$s", "country" to "Qatar"), admin),
        )["id"].asText()
      val rec = login(registerRoleUser("RECEPTIONIST", hid, s))
      val day = LocalDate.now().plusDays(3)

      // Template validation errors surface as 400 with the guard code (house mapping).
      val badWeekday =
        api(
          HttpMethod.POST,
          "/api/v1/schedules/templates",
          json("hospitalId" to hid, "doctorId" to adminId, "weekday" to 0, "startTime" to "09:00", "endTime" to "10:00"),
          admin,
        )
      assertEquals(400, badWeekday.status, "weekday 0 rejected")
      assertTrue(badWeekday.body.contains("SCHEDULE_WEEKDAY_INVALID"), "weekday guard code: ${badWeekday.body.take(200)}")
      val badEnd =
        api(
          HttpMethod.POST,
          "/api/v1/schedules/templates",
          json("hospitalId" to hid, "doctorId" to adminId, "weekday" to 1, "startTime" to "10:00", "endTime" to "09:00"),
          admin,
        )
      assertEquals(400, badEnd.status, "end before start rejected")
      assertTrue(badEnd.body.contains("SCHEDULE_END_BEFORE_START"), "end guard code: ${badEnd.body.take(200)}")

      // Template for the weekday of now+3 → two 30-min in_person slots (09:00, 09:30).
      val template =
        api(
          HttpMethod.POST,
          "/api/v1/schedules/templates",
          json(
            "hospitalId" to hid,
            "doctorId" to adminId,
            "weekday" to day.dayOfWeek.value,
            "sessionType" to "in_person",
            "startTime" to "09:00",
            "endTime" to "10:00",
            "slotMinutes" to 30,
            "capacity" to 1,
          ),
          admin,
        )
      assert2xx(template, "create template: ${template.body.take(300)}")
      val templateId = `data`(template)["id"].asText()

      fun mySlots(r: ApiResp): List<JsonNode> = `data`(r).filter { it["templateId"].asText() == templateId }

      val first = slotsOfDay(rec, adminId, day)
      assert2xx(first, "slots page 1")
      val slots = mySlots(first)
      assertEquals(2, slots.size, "30-min template yields two slots: ${`data`(first)}")
      assertEquals("09:00:00", slots[0]["startTime"].asText())
      assertEquals("09:30:00", slots[1]["startTime"].asText())
      assertEquals("10:00:00", slots[1]["endTime"].asText())
      assertEquals("open", slots[0]["status"].asText())
      val slot0 = slots[0]["id"].asText()
      val slot1 = slots[1]["id"].asText()

      // Slot generation is idempotent: the second call returns the same rows.
      val second = slotsOfDay(rec, adminId, day)
      assert2xx(second, "slots regeneration")
      val again = mySlots(second)
      assertEquals(listOf(slot0, slot1), again.map { it["id"].asText() }, "repeated generation returns identical slot ids")

      val patient = patientIn(admin, hid, "Life", s)

      fun book(slotId: String): ApiResp =
        api(
          HttpMethod.POST,
          "/api/v1/appointments",
          json(
            "slotId" to slotId,
            "patientId" to patient,
            "appointmentType" to "OPD",
            "reason" to "engine lifecycle",
            "priority" to "routine",
            "visitType" to "outpatient",
          ),
          rec,
        )

      val b1 = book(slot0)
      assertEquals(201, b1.status, "book slot 0: ${b1.body.take(300)}")
      val appt1 = `data`(b1)
      assertEquals(slot0, appt1["slotId"].asText(), "booking carries the slot id")
      assertEquals(adminId, appt1["doctorId"].asText(), "doctor derives from the slot")
      assertEquals(false, appt1["telemedicine"].asBoolean(), "in_person slot books non-telemedicine")
      assertEquals("${day}T09:00:00Z", appt1["scheduledStart"].asText(), "start is the slot time as a UTC instant")
      assertEquals("${day}T09:30:00Z", appt1["scheduledEnd"].asText(), "end derives from slotMinutes")

      val full = book(slot0)
      assertEquals(400, full.status, "second booking on a full slot rejected")
      assertTrue(full.body.contains("SCHEDULE_SLOT_FULL"), "capacity guard code: ${full.body.take(200)}")

      // Cancel releases the slot; the freed slot can be booked again.
      val appt1Id = appt1["id"].asText()
      assert2xx(
        api(HttpMethod.PATCH, "/api/v1/appointments/$appt1Id/status", json("status" to "cancelled", "cancelledReason" to "engine"), rec),
        "cancel booking",
      )
      val released = mySlots(slotsOfDay(rec, adminId, day))
      assertEquals(0, released[0]["bookedCount"].asInt(), "cancel releases the slot count")

      val b2 = book(slot0)
      assertEquals(201, b2.status, "rebook the released slot: ${b2.body.take(300)}")
      val appt2Id = `data`(b2)["id"].asText()

      // Reschedule moves the booking to slot 1 (release old + consume new).
      val rescheduled = api(HttpMethod.POST, "/api/v1/appointments/$appt2Id/reschedule", json("slotId" to slot1), rec)
      assert2xx(rescheduled, "reschedule to slot 1: ${rescheduled.body.take(300)}")
      assertEquals(slot1, `data`(rescheduled)["slotId"].asText(), "reschedule carries the new slot")
      assertEquals("${day}T09:30:00Z", `data`(rescheduled)["scheduledStart"].asText(), "reschedule adopts the new slot time")
      val moved = mySlots(slotsOfDay(rec, adminId, day))
      assertEquals(0, moved[0]["bookedCount"].asInt(), "old slot released on reschedule")
      assertEquals(1, moved[1]["bookedCount"].asInt(), "new slot consumed on reschedule")

      // No-show tracks the slot's noShowCount without changing the booking count.
      assert2xx(api(HttpMethod.PATCH, "/api/v1/appointments/$appt2Id/status", json("status" to "no-show"), rec), "mark no-show")
      val afterNoShow = mySlots(slotsOfDay(rec, adminId, day))
      assertEquals(1, afterNoShow[1]["noShowCount"].asInt(), "no-show increments the slot counter")
      assertEquals(1, afterNoShow[1]["bookedCount"].asInt(), "no-show keeps the booking count")

      // Deactivation rides DELETE (update active=false); the template stays listed for history.
      assert2xx(api(HttpMethod.DELETE, "/api/v1/schedules/templates/$templateId", token = admin), "deactivate template")
      val listed = `data`(api(HttpMethod.GET, "/api/v1/schedules/templates?doctorId=$adminId", token = rec))
      val row = listed.first { it["id"].asText() == templateId }
      assertEquals(false, row["active"].asBoolean(), "deactivated template stays listed with active=false")
    }

    @Test
    fun analyticsShadowRunCoverage() {
      val admin = adminToken()
      val adminId = `data`(api(HttpMethod.GET, "/api/v1/auth/me", token = admin))["userId"].asText()
      val s = suffix()
      val hid =
        `data`(
          api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "Shadow L $s", "code" to "SHD-$s", "country" to "Qatar"), admin),
        )["id"].asText()
      val rec = login(registerRoleUser("RECEPTIONIST", hid, s, tag = "RA"))
      // Only SYSTEM_ADMIN carries report:read:report; bound to the hospital via the role's
      // hospitalId so its analytics reads are hospital-scoped (principal.hospitalId).
      val reader = login(registerRoleUser("SYSTEM_ADMIN", hid, s, tag = "SAA"))
      val day = LocalDate.now().plusDays(3)

      // Seed known terminal outcomes: one completed booking, one no-show → rate 50.0.
      val template =
        api(
          HttpMethod.POST,
          "/api/v1/schedules/templates",
          json(
            "hospitalId" to hid,
            "doctorId" to adminId,
            "weekday" to day.dayOfWeek.value,
            "sessionType" to "in_person",
            "startTime" to "09:00",
            "endTime" to "10:00",
            "slotMinutes" to 30,
            "capacity" to 1,
          ),
          admin,
        )
      assert2xx(template, "create template: ${template.body.take(300)}")
      val templateId = `data`(template)["id"].asText()
      val slots = `data`(slotsOfDay(rec, adminId, day)).filter { it["templateId"].asText() == templateId }
      assertEquals(2, slots.size, "two slots for the shadow-run hospital: ${`data`(slotsOfDay(rec, adminId, day))}")

      val p1 = patientIn(admin, hid, "PA", s)
      val p2 = patientIn(admin, hid, "PB", s)

      fun book(
        slotId: String,
        patientId: String,
      ): ApiResp =
        api(
          HttpMethod.POST,
          "/api/v1/appointments",
          json(
            "slotId" to slotId,
            "patientId" to patientId,
            "appointmentType" to "OPD",
            "reason" to "shadow run",
            "priority" to "routine",
            "visitType" to "outpatient",
          ),
          rec,
        )

      val a1 = book(slots[0]["id"].asText(), p1)
      assertEquals(201, a1.status, "book slot 0: ${a1.body.take(300)}")
      val a2 = book(slots[1]["id"].asText(), p2)
      assertEquals(201, a2.status, "book slot 1: ${a2.body.take(300)}")
      val a1Id = `data`(a1)["id"].asText()
      val a2Id = `data`(a2)["id"].asText()
      assert2xx(api(HttpMethod.PATCH, "/api/v1/appointments/$a1Id/status", json("status" to "completed"), rec), "complete booking 1")
      assert2xx(api(HttpMethod.PATCH, "/api/v1/appointments/$a2Id/status", json("status" to "no-show"), rec), "mark booking 2 no-show")

      // 01.11: appointments analytics carries the exact no-show rate over terminal outcomes.
      val stats = `data`(api(HttpMethod.GET, "/api/v1/analytics/appointments", token = reader))
      assertEquals(2L, stats["total"].asLong(), "fresh hospital sees only its own appointments")
      assertEquals(50.0, stats["noShowRate"].asDouble(), 0.0001, "no-show rate = no-show / (completed + no-show)")
      val statuses = stats["byStatus"].associate { it["name"].asText() to it["count"].asLong() }
      assertEquals(1L, statuses["completed"], "completed bucket")
      assertEquals(1L, statuses["no-show"], "no-show bucket")

      // Queue entries seed the 01.02 wait-time estimates → avg wait non-null for this hospital.
      assert2xx(
        api(HttpMethod.POST, "/api/v1/queue/entries", json("queueType" to "OPD", "patientId" to p1, "patientName" to "ShadowA$s", "priority" to "normal"), rec),
        "queue entry 1",
      )
      assert2xx(
        api(HttpMethod.POST, "/api/v1/queue/entries", json("queueType" to "OPD", "patientId" to p2, "patientName" to "ShadowB$s", "priority" to "normal"), rec),
        "queue entry 2",
      )
      val queue = `data`(api(HttpMethod.GET, "/api/v1/analytics/queue", token = reader))
      assertTrue(queue.has("avgWaitMinutes") && !queue["avgWaitMinutes"].isNull, "queue avg wait populated: $queue")
      val overview = `data`(api(HttpMethod.GET, "/api/v1/analytics/overview", token = reader))
      assertTrue(overview.has("avgWaitMinutes") && !overview["avgWaitMinutes"].isNull, "overview avg wait populated")

      // Cross-tenant scoping: a second fresh hospital reads only its own (empty) numbers.
      val hid2 =
        `data`(
          api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "Shadow R $s", "code" to "SHR-$s", "country" to "Qatar"), admin),
        )["id"].asText()
      val rec2 = login(registerRoleUser("RECEPTIONIST", hid2, s, tag = "RB"))
      val reader2 = login(registerRoleUser("SYSTEM_ADMIN", hid2, s, tag = "SAB"))
      val foreign = `data`(api(HttpMethod.GET, "/api/v1/analytics/appointments", token = reader2))
      assertEquals(0L, foreign["total"].asLong(), "foreign hospital sees no cross-tenant appointments")
      assertTrue(foreign["noShowRate"].isNull, "no terminal outcomes → null no-show rate")
      val foreignQueue = `data`(api(HttpMethod.GET, "/api/v1/analytics/queue", token = reader2))
      assertTrue(foreignQueue.has("avgWaitMinutes") && foreignQueue["avgWaitMinutes"].isNull, "no queue entries → null avg wait")
    }

    @Test
    fun schedulingPolicyOverbookNoshowRbacTenant() {
      val admin = adminToken()
      val adminId = `data`(api(HttpMethod.GET, "/api/v1/auth/me", token = admin))["userId"].asText()
      val s = suffix()
      val h =
        `data`(
          api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "Sched H $s", "code" to "SCH-$s", "country" to "Qatar"), admin),
        )["id"].asText()
      val hForeign =
        `data`(
          api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "Sched F $s", "code" to "SCF-$s", "country" to "Qatar"), admin),
        )["id"].asText()
      val rec = login(registerRoleUser("RECEPTIONIST", h, s))
      val doc = login(registerRoleUser("DOCTOR", h, s))
      val foreign = login(registerRoleUser("RECEPTIONIST", hForeign, s, tag = "foreign"))

      // Policy doc drives the overbook ceiling + no-show window/limit (defaults otherwise).
      assert2xx(
        api(
          HttpMethod.PUT,
          "/api/v1/hospital-config/hospitals/$h/config/scheduling",
          json("overbookFactor" to 2.0, "noShowWindowDays" to 30, "noShowLimit" to 1),
          admin,
        ),
        "publish scheduling config",
      )

      val day = LocalDate.now().plusDays(3)
      // Reception owns template management (V54 grant); a 2-hour 60-min session → two slots.
      val template =
        api(
          HttpMethod.POST,
          "/api/v1/schedules/templates",
          json("doctorId" to adminId, "weekday" to day.dayOfWeek.value, "startTime" to "09:00", "endTime" to "11:00", "slotMinutes" to 60, "capacity" to 1),
          rec,
        )
      assert2xx(template, "receptionist creates template: ${template.body.take(300)}")

      val slots = `data`(slotsOfDay(rec, adminId, day))
      assertEquals(2, slots.size(), "fresh tenant yields exactly two slots: $slots")
      val slotA = slots[0]["id"].asText()
      val slotB = slots[1]["id"].asText()

      val pA = patientIn(admin, h, "A", s)
      val pB = patientIn(admin, h, "B", s)
      val pC = patientIn(admin, h, "C", s)

      fun book(
        token: String,
        slotId: String,
        patientId: String,
        overrideNoShow: Boolean = false,
      ): ApiResp =
        api(
          HttpMethod.POST,
          "/api/v1/appointments",
          json(
            "slotId" to slotId,
            "patientId" to patientId,
            "appointmentType" to "OPD",
            "overrideNoShowLimit" to overrideNoShow,
          ),
          token,
        )

      // Overbook factor 2.0 on capacity 1 → ceil(2) = 2 concurrent bookings, the third rejected.
      assertEquals(201, book(rec, slotA, pA).status, "first booking on slot A")
      assertEquals(201, book(rec, slotA, pB).status, "overbook allows the second booking")
      val third = book(rec, slotA, pC)
      assertEquals(400, third.status, "overbook ceiling rejects the third booking")
      assertTrue(third.body.contains("SCHEDULE_SLOT_FULL"), "capacity guard code: ${third.body.take(200)}")

      // No-show policy: window counts 1 >= limit 1 → rebooking the same patient is denied; override wins.
      val c1 = book(rec, slotB, pA)
      assertEquals(201, c1.status, "first booking on slot B")
      val c1Id = `data`(c1)["id"].asText()
      assert2xx(api(HttpMethod.PATCH, "/api/v1/appointments/$c1Id/status", json("status" to "no-show"), rec), "mark slot B no-show")
      val rebook = book(rec, slotB, pA)
      assertEquals(400, rebook.status, "no-show limit denies the rebooking before capacity is even consulted")
      assertTrue(rebook.body.contains("SCHEDULE_NOSHOW_LIMIT"), "no-show guard code: ${rebook.body.take(200)}")
      assertEquals(201, book(rec, slotB, pA, overrideNoShow = true).status, "override books past the no-show limit")

      // RBAC: doctors read schedules but cannot write templates or book; foreign hospital is denied.
      assert2xx(api(HttpMethod.GET, "/api/v1/schedules/templates?doctorId=$adminId", token = doc), "doctor reads schedules")
      assertEquals(
        403,
        api(
          HttpMethod.POST,
          "/api/v1/schedules/templates",
          json("doctorId" to adminId, "weekday" to 1, "startTime" to "09:00", "endTime" to "10:00"),
          doc,
        ).status,
        "doctor template write denied",
      )
      assertEquals(403, book(doc, slotA, pB).status, "doctor booking denied")
      val crossTenant = book(foreign, slotA, pB)
      assertEquals(403, crossTenant.status, "cross-tenant booking denied")
      assertTrue(crossTenant.body.contains("TENANT_FORBIDDEN"), "tenant guard message: ${crossTenant.body.take(200)}")
    }

    @Test
    fun schedulingWaitlistTeleconsultCursorAudit() {
      val admin = adminToken()
      val adminId = `data`(api(HttpMethod.GET, "/api/v1/auth/me", token = admin))["userId"].asText()
      val s = suffix()
      val h =
        `data`(
          api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "Sched W $s", "code" to "SCW-$s", "country" to "Qatar"), admin),
        )["id"].asText()
      val rec = login(registerRoleUser("RECEPTIONIST", h, s))

      // Teleconsult session: booking inherits telemedicine from the slot's session type.
      val dayT = LocalDate.now().plusDays(4)
      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/schedules/templates",
          json(
            "doctorId" to adminId,
            "weekday" to dayT.dayOfWeek.value,
            "sessionType" to "teleconsult",
            "startTime" to "14:00",
            "endTime" to "14:30",
            "slotMinutes" to 30,
            "capacity" to 1,
          ),
          rec,
        ),
        "teleconsult template",
      )
      val teleSlots = `data`(slotsOfDay(rec, adminId, dayT))
      assertEquals(1, teleSlots.size(), "teleconsult template yields one slot: $teleSlots")
      val teleSlot = teleSlots[0]["id"].asText()
      val pT = patientIn(admin, h, "Tele", s)
      val teleBook = api(HttpMethod.POST, "/api/v1/appointments", json("slotId" to teleSlot, "patientId" to pT, "appointmentType" to "OPD"), rec)
      assertEquals(201, teleBook.status, "book teleconsult slot: ${teleBook.body.take(300)}")
      assertEquals(true, `data`(teleBook)["telemedicine"].asBoolean(), "teleconsult slot books a telemedicine appointment")

      // Waitlist fairness: urgent heads the queue regardless of creation order.
      val urgent = `data`(api(HttpMethod.POST, "/api/v1/schedules/waitlist", json("patientId" to pT, "priority" to "urgent"), rec))
      val routine = `data`(api(HttpMethod.POST, "/api/v1/schedules/waitlist", json("patientId" to pT, "priority" to "routine"), rec))
      val waiting = `data`(api(HttpMethod.GET, "/api/v1/schedules/waitlist", token = rec))
      assertEquals(2, waiting.size(), "two waiting entries: $waiting")
      assertEquals(urgent["id"].asText(), waiting[0]["id"].asText(), "urgent entry heads the queue")
      assertEquals("waiting", waiting[0]["status"].asText())

      // Promote into a concrete slot; the promoted entry leaves the waiting queue.
      val dayP = LocalDate.now().plusDays(5)
      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/schedules/templates",
          json("doctorId" to adminId, "weekday" to dayP.dayOfWeek.value, "startTime" to "15:00", "endTime" to "16:00", "slotMinutes" to 30, "capacity" to 1),
          rec,
        ),
        "promote target template",
      )
      val promoteSlot = `data`(slotsOfDay(rec, adminId, dayP))[0]["id"].asText()
      val promoted = api(HttpMethod.POST, "/api/v1/schedules/waitlist/${urgent["id"].asText()}/promote", json("slotId" to promoteSlot), rec)
      assertEquals(201, promoted.status, "promote urgent entry: ${promoted.body.take(300)}")
      assertEquals(pT, `data`(promoted)["patientId"].asText(), "promoted booking belongs to the waitlisted patient")
      assertEquals(
        listOf(routine["id"].asText()),
        `data`(api(HttpMethod.GET, "/api/v1/schedules/waitlist", token = rec)).map { it["id"].asText() },
        "promoted entry leaves the waiting queue",
      )
      val rePromote = api(HttpMethod.POST, "/api/v1/schedules/waitlist/${urgent["id"].asText()}/promote", json("slotId" to promoteSlot), rec)
      assertEquals(400, rePromote.status, "double promote rejected")
      assertTrue(rePromote.body.contains("WAITLIST_ENTRY_NOT_WAITING"), "waitlist guard code: ${rePromote.body.take(200)}")

      assert2xx(api(HttpMethod.DELETE, "/api/v1/schedules/waitlist/${routine["id"].asText()}", token = rec), "cancel routine entry")
      assertEquals(0, `data`(api(HttpMethod.GET, "/api/v1/schedules/waitlist", token = rec)).size(), "cancelled entry leaves the queue")

      // Legacy (no slot) creations feed the keyset-paginated list: ascending order + opaque cursor.
      val pC = patientIn(admin, h, "Cursor", s)
      val created = mutableListOf<String>()
      repeat(3) { i ->
        val d = LocalDate.now().plusDays(i + 1L)
        val r =
          api(
            HttpMethod.POST,
            "/api/v1/appointments",
            json(
              "patientId" to pC,
              "doctorId" to adminId,
              "appointmentType" to "OPD",
              "scheduledStart" to "${d}T12:00:00Z",
              "scheduledEnd" to "${d}T12:15:00Z",
              "durationMinutes" to 15,
            ),
            rec,
          )
        assertEquals(201, r.status, "legacy appointment $i: ${r.body.take(300)}")
        created += `data`(r)["id"].asText()
      }

      fun fetch(cursor: String?): ApiResp {
        val q = if (cursor == null) "" else "&cursor=$cursor"
        return api(HttpMethod.GET, "/api/v1/appointments?patientId=$pC&limit=2$q", token = rec)
      }

      val page1 = fetch(null)
      assert2xx(page1, "appointments page 1")
      val ids1 = `data`(page1).map { it["id"].asText() }
      assertEquals(listOf(created[0], created[1]), ids1, "limit=2 yields the two earliest rows ascending")
      val starts1 = `data`(page1).map { Instant.parse(it["scheduledStart"].asText()) }
      assertTrue(starts1[0].isBefore(starts1[1]), "rows ascend by scheduledStart")
      val cursor = om.readTree(page1.body)["meta"]["nextCursor"].asText()
      assertTrue(cursor.isNotBlank(), "full page carries nextCursor")

      val page2 = fetch(cursor)
      assert2xx(page2, "appointments page 2")
      val ids2 = `data`(page2).map { it["id"].asText() }
      assertEquals(listOf(created[2]), ids2, "continuation returns the remaining row")
      assertFalse(om.readTree(page2.body)["meta"].has("nextCursor"), "last page has no nextCursor")
      assertTrue(ids1.toSet().intersect(ids2.toSet()).isEmpty(), "no id repeats across pages")

      val bad =
        fetch(
          java.util.Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString("nonsense".toByteArray()),
        )
      assertEquals(400, bad.status, "malformed cursor rejected")
      assertTrue(bad.body.contains("INVALID_CURSOR"), "cursor guard code: ${bad.body.take(200)}")

      assert2xx(
        api(HttpMethod.PATCH, "/api/v1/appointments/${created[0]}/status", json("status" to "cancelled", "cancelledReason" to "cursor test"), rec),
        "cancel one appointment",
      )
      val cancelled = api(HttpMethod.GET, "/api/v1/appointments?patientId=$pC&status=cancelled&limit=50", token = rec)
      assert2xx(cancelled, "appointments filtered by status")
      assertEquals(listOf(created[0]), `data`(cancelled).map { it["id"].asText() }, "status filter returns exactly the cancelled row")

      // Audit: the PATCH write is recorded and queryable by entity id (00.02 semantics).
      val audit = api(HttpMethod.GET, "/api/v1/audit/logs?entityId=${created[0]}&limit=10", token = admin)
      assert2xx(audit, "audit logs for the cancelled appointment")
      assertTrue(`data`(audit).size() >= 1, "status change audited: ${audit.body.take(300)}")
    }
  }

  /** 01.04 OPD EMR core: allergy gate, two-identifier confirm, note signature, catalog, procedures/referrals/follow-up. */
  @Nested
  inner class OpdEmrCoreTests {
    private fun registerRoleUser(
      role: String,
      hospitalId: String,
      s: String,
      tag: String = role,
    ): String {
      val email = "emr-${tag.lowercase()}-$s@test.local"
      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/auth/register",
          json(
            "email" to email,
            "password" to "Passw0rd!123",
            "firstName" to "Emr",
            "lastName" to "$role$s",
            "roles" to listOf(role),
            "hospitalId" to hospitalId,
          ),
        ),
        "register $role $email",
      )
      return email
    }

    private fun login(email: String): String {
      val login = api(HttpMethod.POST, "/api/v1/auth/login", json("username" to email, "password" to "Passw0rd!123"))
      assert2xx(login, "login $email: ${login.body.take(200)}")
      return `data`(login)["accessToken"].asText()
    }

    /** Admin creates the patient; returns id + MRN (CS-01 two-identifier confirm needs both). */
    private fun patientIn(
      admin: String,
      hospitalId: String,
      s: String,
    ): Pair<String, String> {
      val patient =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/patients",
            json(
              "firstName" to "Emr",
              "lastName" to "Patient$s",
              "gender" to "female",
              "dateOfBirth" to "1990-02-02",
              "hospitalId" to hospitalId,
            ),
            admin,
          ),
        )["patient"]
      return patient["id"].asText() to patient["mrn"].asText()
    }

    private fun prom(): String {
      val r = api(HttpMethod.GET, "/actuator/prometheus")
      assert2xx(r, "prometheus scrape")
      return r.body
    }

    @Test
    fun opdEmrCoreClinicalSafetyAndWorkflow() {
      val admin = adminToken()
      val adminId = `data`(api(HttpMethod.GET, "/api/v1/auth/me", token = admin))["userId"].asText()
      val s = suffix()
      // Fresh hospital per run: global catalogs are shared, but encounters/allergies/slots are per-hospital.
      val hid =
        `data`(
          api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "Emr L $s", "code" to "EMR-$s", "country" to "Qatar"), admin),
        )["id"].asText()
      val hidForeign =
        `data`(
          api(HttpMethod.POST, "/api/v1/hospital-config/hospitals", json("name" to "Emr F $s", "code" to "EMF-$s", "country" to "Qatar"), admin),
        )["id"].asText()
      val doc = login(registerRoleUser("DOCTOR", hid, s))
      val rec = login(registerRoleUser("RECEPTIONIST", hid, s))
      val docForeign = login(registerRoleUser("DOCTOR", hidForeign, s, tag = "foreign"))
      // 01.12 dual review: a second doctor in the SAME hospital for the ABAC owner checks.
      val doc2 = login(registerRoleUser("DOCTOR", hid, s, tag = "two"))
      val docId = `data`(api(HttpMethod.GET, "/api/v1/auth/me", token = doc))["userId"].asText()

      val (patientId, mrn) = patientIn(admin, hid, s)

      val enc =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/encounters",
            json("patientId" to patientId, "reason" to "OPD EMR core", "chiefComplaint" to "sore throat"),
            doc,
          ),
        )["id"].asText()

      // SAFETY L49: with no allergy documented at all, the Rx gate fails closed (dual review).
      val rxUndocumented =
        api(HttpMethod.POST, "/api/v1/encounters/$enc/prescriptions", json("drugName" to "Paracetamol", "patientMrn" to mrn), doc)
      assertEquals(409, rxUndocumented.status, "Rx without documented allergies fails closed: ${rxUndocumented.body.take(300)}")
      assertTrue(rxUndocumented.body.contains("ALLERGY_NOT_DOCUMENTED"), "undocumented guard code: ${rxUndocumented.body.take(200)}")

      // SAFETY L49: the NKA attestation satisfies the gate for allergy-free patients.
      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/patients/$patientId/allergies",
          json("allergen" to "NO KNOWN ALLERGIES", "severity" to "none"),
          doc,
        ),
        "nka attestation",
      )

      // Documenting a real allergy invalidates the standing NKA attestation; severity arbitrates the outcome.
      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/patients/$patientId/allergies",
          json("allergen" to "Penicillin", "reaction" to "Urticaria", "severity" to "block"),
          doc,
        ),
        "block allergy",
      )
      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/patients/$patientId/allergies",
          json("allergen" to "Ibuprofen", "reaction" to "Gastritis", "severity" to "warn"),
          doc,
        ),
        "warn allergy",
      )

      // CS-02/CS-03: match detection is unchanged (exact OR >=4-char substring); block severity hard-fails both paths.
      val blocked =
        api(
          HttpMethod.POST,
          "/api/v1/encounters/$enc/prescriptions",
          json("drugName" to "Penicillin", "patientMrn" to mrn),
          doc,
        )
      assertEquals(409, blocked.status, "exact allergy match blocks: ${blocked.body.take(300)}")
      assertTrue(blocked.body.contains("ALLERGY_BLOCK"), "block guard code: ${blocked.body.take(200)}")
      val blockedSub =
        api(
          HttpMethod.POST,
          "/api/v1/encounters/$enc/prescriptions",
          json("drugName" to "Benzylpenicillin", "patientMrn" to mrn),
          doc,
        )
      assertEquals(409, blockedSub.status, "block severity also hard-fails the substring path: ${blockedSub.body.take(300)}")
      assertTrue(blockedSub.body.contains("ALLERGY_BLOCK"), "substring block guard code: ${blockedSub.body.take(200)}")

      // CS-03: warn severity warns — override without a typed reason is rejected.
      val warnNoReason =
        api(
          HttpMethod.POST,
          "/api/v1/encounters/$enc/prescriptions",
          json("drugName" to "Ibuprofen", "patientMrn" to mrn),
          doc,
        )
      assertEquals(409, warnNoReason.status, "warn requires an override reason: ${warnNoReason.body.take(300)}")
      assertTrue(warnNoReason.body.contains("ALLERGY_WARN_REASON_REQUIRED"), "warn guard code: ${warnNoReason.body.take(200)}")

      val warnOverridden =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/encounters/$enc/prescriptions",
            json("drugName" to "Ibuprofen", "patientMrn" to mrn, "overrideReason" to "Gastroprotection co-prescribed"),
            doc,
          ),
        )
      assertEquals("warn_override", warnOverridden["allergyStatus"].asText(), "warn override recorded: $warnOverridden")
      assertNotNull(warnOverridden["overrideReason"].asText(), "override reason kept for audit")
      val metrics = prom()
      assertTrue(metrics.contains("squamed_rx_allergy_warn_overrides_total"), "warn-override KPI counter exported")

      // CS-01: the two-identifier confirm rejects a missing or wrong patient MRN.
      val rxNoMrn = api(HttpMethod.POST, "/api/v1/encounters/$enc/prescriptions", json("drugName" to "Paracetamol"), doc)
      assertEquals(400, rxNoMrn.status, "Rx without MRN rejected: ${rxNoMrn.body.take(200)}")
      assertTrue(rxNoMrn.body.contains("PATIENT_MISMATCH"), "Rx MRN guard code: ${rxNoMrn.body.take(200)}")
      val rxWrongMrn =
        api(HttpMethod.POST, "/api/v1/encounters/$enc/prescriptions", json("drugName" to "Paracetamol", "patientMrn" to "MQ-0000-0001"), doc)
      assertEquals(400, rxWrongMrn.status, "Rx with a wrong MRN rejected")
      val cleanRx =
        `data`(api(HttpMethod.POST, "/api/v1/encounters/$enc/prescriptions", json("drugName" to "Paracetamol", "patientMrn" to mrn), doc))
      assertEquals("checked", cleanRx["allergyStatus"].asText(), "clean Rx records the allergy screen: $cleanRx")

      val orderNoMrn = api(HttpMethod.POST, "/api/v1/encounters/$enc/orders", json("orderType" to "lab", "orderName" to "CBC"), doc)
      assertEquals(400, orderNoMrn.status, "order without MRN rejected: ${orderNoMrn.body.take(200)}")
      assertTrue(orderNoMrn.body.contains("PATIENT_MISMATCH"), "order MRN guard code: ${orderNoMrn.body.take(200)}")
      val order =
        `data`(api(HttpMethod.POST, "/api/v1/encounters/$enc/orders", json("orderType" to "lab", "orderName" to "CBC", "patientMrn" to mrn), doc))
      assertEquals("pending", order["status"].asText(), "confirmed order lands pending: $order")

      // E5 signature: PIN required, re-sign conflicts, ESIGN needs its transaction id.
      val noteId =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/encounters/$enc/notes",
            json(
              "noteType" to "progress",
              "title" to "OPD note",
              "subjective" to "sore throat 2 days",
              "assessment" to "pharyngitis",
              "plan" to "rest",
              "patientMrn" to mrn,
            ),
            doc,
          ),
        )["id"].asText()
      val noPin = api(HttpMethod.POST, "/api/v1/encounters/notes/$noteId/sign", json("method" to "PIN", "patientMrn" to mrn), doc)
      assertEquals(400, noPin.status, "sign without a PIN rejected: ${noPin.body.take(200)}")
      assertTrue(noPin.body.contains("SIGNATURE_PIN_REQUIRED"), "PIN guard code: ${noPin.body.take(200)}")
      val signed = api(HttpMethod.POST, "/api/v1/encounters/notes/$noteId/sign", json("method" to "PIN", "pin" to "4821", "patientMrn" to mrn), doc)
      assert2xx(signed, "PIN sign: ${signed.body.take(300)}")
      assertEquals("PIN", `data`(signed)["signatureMethod"].asText(), "signature method recorded")
      val resign = api(HttpMethod.POST, "/api/v1/encounters/notes/$noteId/sign", json("method" to "PIN", "pin" to "4821", "patientMrn" to mrn), doc)
      assertEquals(409, resign.status, "re-signing a signed note conflicts: ${resign.body.take(200)}")
      assertTrue(resign.body.contains("NOTE_ALREADY_SIGNED"), "re-sign guard code: ${resign.body.take(200)}")

      val note2Id =
        `data`(
          api(HttpMethod.POST, "/api/v1/encounters/$enc/notes", json("noteType" to "progress", "title" to "eSign note", "patientMrn" to mrn), doc),
        )["id"].asText()
      val esignNoTx = api(HttpMethod.POST, "/api/v1/encounters/notes/$note2Id/sign", json("method" to "ESIGN", "patientMrn" to mrn), doc)
      assertEquals(400, esignNoTx.status, "ESIGN without a transaction id rejected: ${esignNoTx.body.take(200)}")
      assertTrue(esignNoTx.body.contains("ESIGN_TRANSACTION_REQUIRED"), "eSign guard code: ${esignNoTx.body.take(200)}")
      val esigned =
        api(HttpMethod.POST, "/api/v1/encounters/notes/$note2Id/sign", json("method" to "ESIGN", "eSignTransactionId" to "tx-$s", "patientMrn" to mrn), doc)
      assert2xx(esigned, "ESIGN with tx id: ${esigned.body.take(300)}")
      assertEquals("ESIGN", `data`(esigned)["signatureMethod"].asText(), "eSign method recorded")

      // Coded diagnosis: unknown codes rejected; catalog code resolves its display name.
      val unknownDx =
        api(HttpMethod.POST, "/api/v1/encounters/$enc/diagnoses", json("code" to "ZZZ9", "system" to "ICD-10", "patientMrn" to mrn), doc)
      assertEquals(400, unknownDx.status, "unknown dx code rejected: ${unknownDx.body.take(200)}")
      assertTrue(unknownDx.body.contains("DIAGNOSIS_CODE_UNKNOWN"), "dx guard code: ${unknownDx.body.take(200)}")
      val codedDx =
        `data`(api(HttpMethod.POST, "/api/v1/encounters/$enc/diagnoses", json("code" to "E11.9", "system" to "ICD-10", "patientMrn" to mrn), doc))
      assertEquals("Type 2 diabetes mellitus without complications", codedDx["name"].asText(), "catalog display fills the name: $codedDx")
      assertEquals("ICD-10", codedDx["system"].asText(), "canonical system saved")

      // Global catalogs: 8+ specialty SOAP templates and the seeded dx catalog (47 rows).
      val templates = `data`(api(HttpMethod.GET, "/api/v1/encounters/note-templates", token = doc))
      assertTrue(templates.size() >= 8, "specialty note templates seeded: ${templates.size()}")
      val catalog = `data`(api(HttpMethod.GET, "/api/v1/encounters/diagnosis-catalog", token = doc))
      assertTrue(catalog.size() >= 45, "diagnosis catalog seeded: ${catalog.size()}")

      // Procedures: documented on the encounter, listable.
      val procedure =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/encounters/$enc/procedures",
            json("procedureName" to "Nebulisation", "status" to "completed", "notes" to "single dose", "patientMrn" to mrn),
            doc,
          ),
        )
      assertEquals("completed", procedure["status"].asText(), "procedure recorded: $procedure")
      val procedures = `data`(api(HttpMethod.GET, "/api/v1/encounters/$enc/procedures", token = doc))
      assertEquals(1, procedures.size(), "one procedure listed: $procedures")

      // Referrals: target required; specialty referral persists with the referring doctor.
      val noTarget = api(HttpMethod.POST, "/api/v1/encounters/$enc/referrals", json("reason" to "unclear", "patientMrn" to mrn), doc)
      assertEquals(400, noTarget.status, "referral without a target rejected: ${noTarget.body.take(200)}")
      assertTrue(noTarget.body.contains("REFERRAL_TARGET_REQUIRED"), "referral guard code: ${noTarget.body.take(200)}")
      val referral =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/encounters/$enc/referrals",
            json("toSpecialty" to "Cardiology", "reason" to "chest pain workup", "urgency" to "urgent", "patientMrn" to mrn),
            doc,
          ),
        )
      assertEquals("pending", referral["status"].asText(), "referral starts pending: $referral")
      assertEquals(1, `data`(api(HttpMethod.GET, "/api/v1/encounters/$enc/referrals", token = doc)).size(), "one referral listed")

      // 01.12 dual-review hardening: ABAC ownership, the amendment chain, diagnosis soft delete.
      val spoof =
        api(
          HttpMethod.POST,
          "/api/v1/encounters",
          json("patientId" to patientId, "doctorId" to docId, "reason" to "spoof", "chiefComplaint" to "spoof"),
          doc2,
        )
      assertEquals(403, spoof.status, "startEncounter owner spoof rejected: ${spoof.body.take(300)}")
      assertTrue(spoof.body.contains("ENCOUNTER_OWNER_MISMATCH"), "owner spoof guard code: ${spoof.body.take(200)}")
      val foreignNote =
        api(HttpMethod.POST, "/api/v1/encounters/$enc/notes", json("noteType" to "progress", "title" to "foreign write", "patientMrn" to mrn), doc2)
      assertEquals(403, foreignNote.status, "non-owner doctor write rejected: ${foreignNote.body.take(300)}")
      assertTrue(foreignNote.body.contains("ENCOUNTER_NOT_OWNED"), "non-owner guard code: ${foreignNote.body.take(200)}")

      // SAFETY L51: an amendment is a NEW unsigned note linked to the signed original.
      val draftNoteId =
        `data`(
          api(HttpMethod.POST, "/api/v1/encounters/$enc/notes", json("noteType" to "progress", "title" to "draft note", "patientMrn" to mrn), doc),
        )["id"].asText()
      val amendUnsigned =
        api(HttpMethod.POST, "/api/v1/encounters/notes/$draftNoteId/amendments", json("title" to "x", "patientMrn" to mrn), doc)
      assertEquals(409, amendUnsigned.status, "amending an unsigned note rejected: ${amendUnsigned.body.take(300)}")
      assertTrue(amendUnsigned.body.contains("NOTE_NOT_SIGNED"), "amendment guard code: ${amendUnsigned.body.take(200)}")
      val amendDraft =
        `data`(
          api(
            HttpMethod.POST,
            "/api/v1/encounters/notes/$noteId/amendments",
            json("noteType" to "progress", "title" to "Amendment", "assessment" to "pharyngitis, improving", "patientMrn" to mrn),
            doc,
          ),
        )
      assertEquals(noteId, amendDraft["amendsNoteId"].asText(), "amendment links to the signed original: $amendDraft")
      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/encounters/notes/${amendDraft["id"].asText()}/sign",
          json("method" to "PIN", "pin" to "4821", "patientMrn" to mrn),
          doc,
        ),
        "amendment signs via the existing path",
      )

      // Dual review: diagnosis removal is a soft delete — the list hides it, the row keeps its history.
      assert2xx(api(HttpMethod.DELETE, "/api/v1/encounters/$enc/diagnoses/${codedDx["id"].asText()}?patientMrn=$mrn", token = doc), "soft-delete diagnosis")
      val dxAfter = `data`(api(HttpMethod.GET, "/api/v1/encounters/$enc/diagnoses", token = doc))
      assertEquals(0, dxAfter.size(), "removed diagnosis hidden from the list: $dxAfter")

      // Follow-up: booking rights stay with reception/admin (V54); the encounter gets the appointment stamp.
      val doctorFollowUp = api(HttpMethod.POST, "/api/v1/encounters/$enc/follow-up", json("slotId" to "00000000-0000-0000-0000-000000000000"), doc)
      assertEquals(403, doctorFollowUp.status, "doctor cannot book follow-up: ${doctorFollowUp.body.take(200)}")
      val day = LocalDate.now().plusDays(3)
      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/schedules/templates",
          json("doctorId" to adminId, "weekday" to day.dayOfWeek.value, "startTime" to "09:00", "endTime" to "10:00", "slotMinutes" to 30, "capacity" to 1),
          rec,
        ),
        "follow-up slot template",
      )
      val followUpSlot = `data`(api(HttpMethod.GET, "/api/v1/schedules/$adminId/slots?from=$day&to=$day", token = rec))[0]["id"].asText()
      val followUp = api(HttpMethod.POST, "/api/v1/encounters/$enc/follow-up", json("slotId" to followUpSlot), rec)
      assert2xx(followUp, "receptionist books the follow-up: ${followUp.body.take(300)}")
      val followUpId = `data`(followUp)["followUpAppointmentId"].asText()
      assertTrue(followUpId.isNotBlank(), "encounter stamped with the follow-up appointment: ${followUp.body.take(300)}")

      // 01.04 orders→tasks contract: the placed order lands in the outbox for the chunk-2 task engine.
      val outboxRows = `data`(api(HttpMethod.GET, "/api/v1/admin/events/outbox?limit=200", token = admin))
      val emitted = outboxRows.firstOrNull { it["eventType"].asText() == "encounter.order.placed" }
      assertNotNull(emitted, "encounter.order.placed outbox row present: head=${outboxRows.take(3)}")

      // Cross-tenant: a foreign doctor cannot read the encounter.
      assertEquals(403, api(HttpMethod.GET, "/api/v1/encounters/$enc", token = docForeign).status, "cross-tenant encounter denied")
      assertEquals(403, api(HttpMethod.GET, "/api/v1/encounters/$enc/procedures", token = docForeign).status, "cross-tenant procedures denied")

      // 01.12 dual review: completed encounters refuse writes and cannot be re-completed.
      assert2xx(api(HttpMethod.PATCH, "/api/v1/encounters/$enc/complete", null, doc), "complete encounter")
      val lateNote =
        api(HttpMethod.POST, "/api/v1/encounters/$enc/notes", json("noteType" to "progress", "title" to "late entry", "patientMrn" to mrn), doc)
      assertEquals(409, lateNote.status, "write after completion rejected: ${lateNote.body.take(300)}")
      assertTrue(lateNote.body.contains("ENCOUNTER_NOT_ACTIVE"), "closed-encounter guard code: ${lateNote.body.take(200)}")
      val recomplete = api(HttpMethod.PATCH, "/api/v1/encounters/$enc/complete", null, doc)
      assertEquals(409, recomplete.status, "re-complete rejected: ${recomplete.body.take(300)}")
      assertTrue(recomplete.body.contains("ENCOUNTER_NOT_ACTIVE"), "re-complete guard code: ${recomplete.body.take(200)}")
    }
  }
}
