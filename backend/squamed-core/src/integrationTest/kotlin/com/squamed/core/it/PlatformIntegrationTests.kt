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

    init {
      if (!EXTERNAL) {
        listOfNotNull(postgres, redis, kafka, opensearch).forEach { container ->
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
      if (EXTERNAL) {
        registry.add("spring.datasource.url") { "jdbc:postgresql://localhost:5433/squamed" }
        registry.add("spring.datasource.username") { "squamed" }
        registry.add("spring.datasource.password") { "squamed_dev_password" }
        registry.add("spring.data.redis.host") { "localhost" }
        registry.add("spring.data.redis.port") { 6380 }
        registry.add("spring.kafka.bootstrap-servers") { "localhost:9092" }
        registry.add("squamed.search.opensearch-uris") { "http://localhost:9200" }
        registry.add("squamed.search.reindex-on-startup") { "false" }
      } else {
        registry.add("spring.datasource.url") { postgres!!.jdbcUrl }
        registry.add("spring.datasource.username") { postgres!!.username }
        registry.add("spring.datasource.password") { postgres!!.password }
        registry.add("spring.data.redis.host") { redis!!.host }
        registry.add("spring.data.redis.port") { redis!!.getMappedPort(6379) }
        registry.add("spring.kafka.bootstrap-servers") { "localhost:${kafka!!.getMappedPort(9092)}" }
        registry.add("squamed.search.opensearch-uris") { "http://localhost:${opensearch!!.getMappedPort(9200)}" }
        registry.add("squamed.search.reindex-on-startup") { "false" }
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
      assertTrue(mrn.matches(Regex("^MQ-\\d{4}-\\d{4}$")), "MRN format: $mrn")

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
          ),
          token,
        )
      assert2xx(note, "add SOAP note")
      val noteId = `data`(note)["id"].asText()
      val sign = api(HttpMethod.POST, "/api/v1/encounters/notes/$noteId/sign", json(), token)
      assertTrue(sign.status == 200 || sign.status == 409, "sign note -> ${sign.status}")

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

      val audit = api(HttpMethod.GET, "/api/v1/audit/logs?entityType=Patient&limit=10", token = token)
      assert2xx(audit, "audit logs for patient")
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

      // Platform admin (null hospital) keeps unrestricted access to both tenants.
      assert2xx(api(HttpMethod.GET, "/api/v1/hospital-config/hospitals/$h1/tree", token = admin), "admin tree A")
      assert2xx(api(HttpMethod.GET, "/api/v1/hospital-config/hospitals/$h2/tree", token = admin), "admin tree B")
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
            ),
            token,
          ),
        )["id"].asText()

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
            "qid" to "10000002$s".take(11),
            "nationality" to "QA",
            "city" to "Doha",
          ),
          token,
        )
      assert2xx(dupResp, "create duplicate")
      val dup = `data`(dupResp)
      assertTrue(dup["matches"].size() >= 1, "duplicate create returns match candidates: ${dupResp.body.take(300)}")
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

      val mergeSelf = api(HttpMethod.POST, "/api/v1/patients/merge", json("targetId" to p1Id, "duplicateId" to p1Id), token)
      assertEquals(400, mergeSelf.status, "self merge rejected")
      assertEquals("CANNOT_MERGE_PATIENT_WITH_ITSELF", om.readTree(mergeSelf.body)["error"]["code"].asText())

      val merged = api(HttpMethod.POST, "/api/v1/patients/merge", json("targetId" to p1Id, "duplicateId" to p2Id), token)
      assert2xx(merged, "merge duplicate into target")
      val moved = `data`(merged)["moved"]
      val movedCount =
        listOf("identifiers", "contacts", "addresses", "allergies", "notes", "history", "episodes")
          .map { moved[it].asInt() }
          .sum()
      assertTrue(movedCount > 0, "records moved on merge: $moved")
      assert2xx(api(HttpMethod.GET, "/api/v1/patients/$p1Id", token = token), "target detail after merge")
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
            ),
            token,
          ),
        )
      val dxId = dx["id"].asText()
      assert2xx(api(HttpMethod.GET, "/api/v1/encounters/$enc/diagnoses", token = token), "list diagnoses")
      assert2xx(api(HttpMethod.DELETE, "/api/v1/encounters/$enc/diagnoses/$dxId", token = token), "delete diagnosis")
      assert2xx(
        api(
          HttpMethod.POST,
          "/api/v1/encounters/$enc/orders",
          json(
            "orderType" to "LAB",
            "orderCode" to "CBC",
            "orderName" to "CBC Panel",
            "priority" to "routine",
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
}
