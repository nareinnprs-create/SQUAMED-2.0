package com.squamed.core.ai.service

import com.squamed.core.analytics.service.AnalyticsService
import com.squamed.core.patient.service.PatientService
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Service
import java.util.UUID

enum class AiIntentType { NAVIGATE, COUNT, SEARCH_PATIENTS, WORKFLOW, ANALYTICS, HELP, CHAT }

data class AiIntent(
  val type: AiIntentType,
  val target: String? = null,
  val entityLabel: String? = null,
  val count: Long? = null,
  val detail: String? = null,
  val note: String? = null,
)

@Service
class AiIntentService(
  private val analyticsService: AnalyticsService,
  private val patientService: PatientService,
) {
  @PersistenceContext
  private lateinit var entityManager: EntityManager

  companion object {
    private val PAGE_LABELS =
      mapOf(
        "dashboard" to "Operational Dashboard",
        "analytics" to "Operational Dashboard",
        "patients" to "Patient Registry",
        "patient registry" to "Patient Registry",
        "appointments" to "Appointment Management",
        "schedule" to "Appointment Management",
        "queue" to "Queue Management",
        "waiting room" to "Queue Management",
        "laboratory" to "Laboratory",
        "labs" to "Laboratory",
        "lab" to "Laboratory",
        "pharmacy" to "Pharmacy",
        "medications" to "Pharmacy",
        "billing" to "Billing & Invoicing",
        "finance" to "Billing & Invoicing",
        "invoices" to "Billing & Invoicing",
        "orders" to "Orders",
        "services" to "Orders",
        "communication" to "Communication Centre",
        "workflow" to "Workflow Engine",
        "integrations" to "Integrations",
        "interop" to "Integrations",
        "fhir" to "Integrations",
        "hl7" to "Integrations",
        "ai" to "AI Assistant",
        "assistant" to "AI Assistant",
        "reception" to "Reception",
        "master data" to "Master Data",
        "hospital config" to "Hospital Configuration",
      )

    private val COUNT_ENTITIES =
      mapOf(
        "patients" to "patients",
        "patient" to "patients",
        "encounters" to "encounters",
        "encounter" to "encounters",
        "appointments" to "appointments",
        "appointment" to "appointments",
        "queue" to "queue",
        "queue entries" to "queue",
        "lab orders" to "labOrders",
        "lab" to "labOrders",
        "laboratory orders" to "labOrders",
        "invoices" to "invoices",
        "invoice" to "invoices",
        "approvals" to "approvals",
        "pending approvals" to "approvals",
        "workflow instances" to "workflow",
        "tasks" to "tasks",
      )
  }

  fun parse(
    query: String,
    hospitalId: UUID?,
  ): AiIntent {
    val q = query.trim().lowercase().replace(Regex("\\s+"), " ")

    if (q.isEmpty()) return AiIntent(AiIntentType.HELP)

    if (listOf("help", "what can you do", "capabilities", "commands", "what are you").any { q == it || q.startsWith("$it ") }) {
      return AiIntent(AiIntentType.HELP)
    }

    if (q.startsWith("open ") || q.startsWith("go to ") || q.startsWith("navigate to ") || q.startsWith("take me to ")) {
      val target = q.substringAfterLast(" to ").substringAfter("open ").trim()
      return navigate(target) ?: AiIntent(
        AiIntentType.NAVIGATE,
        target = target,
        note =
          "Unknown destination \"$target\". Try: dashboard, patients, appointments, queue, laboratory, pharmacy, " +
            "billing, orders, communication, workflow, integrations, ai.",
      )
    }

    if (q.contains("approval") || q.contains("workflow")) {
      val pending = workflowPendingCount(hospitalId)
      return AiIntent(
        AiIntentType.WORKFLOW,
        count = pending,
        detail = "$pending workflow approval(s) currently waiting.",
      )
    }

    if (q.contains("revenue") || q.contains("how much") || q.contains("money") ||
      q.startsWith("analytics") || q.startsWith("overview") || q.startsWith("summary") || q == "numbers"
    ) {
      return analyze(hospitalId)
    }

    if (q.contains("search patient") || q.contains("find patient") || q.contains("patient named") || q.startsWith("search ")) {
      val needle = q.replace(Regex("^(open |go to |search patient|find patient|patient named|search)"), "").trim()
      return searchPatients(needle, hospitalId)
    }

    val countMatch =
      Regex(
        "(?i)(how many|count|total|number of)\\s+(.+?)(?:\\s+(?:today|in total|registered|right now|pending|waiting))?\\s*$|" +
          "(?i)^(.+?)\\s+(count|total)$",
      ).find(q)
    val entityText =
      countMatch
        ?.groupValues
        ?.get(2)
        ?.takeIf { it.isNotBlank() }
        ?: countMatch?.groupValues?.get(3)
        ?: q
    if (listOf("how many", "count", "total", "number of").any { q.startsWith(it) } || countMatch != null) {
      val cleaned = entityText.toString().trim()
      val key =
        COUNT_ENTITIES.entries.firstOrNull { cleaned.contains(it.key) }?.value
          ?: COUNT_ENTITIES[cleaned]
      if (key != null) {
        return countEntity(key, hospitalId)
      }
      return AiIntent(
        AiIntentType.COUNT,
        entityLabel = cleaned,
        note = "I can count patients, encounters, appointments, queue entries, lab orders, invoices, approvals and tasks.",
      )
    }

    val known = PAGE_LABELS.keys.firstOrNull { q.contains(it) }
    if (known != null) {
      return navigate(known)!!
    }

    return AiIntent(AiIntentType.CHAT)
  }

  fun render(
    intent: AiIntent,
    query: String,
    hospitalId: UUID?,
  ): String =
    when (intent.type) {
      AiIntentType.NAVIGATE -> {
        intent.target?.let { t ->
          val label = PAGE_LABELS[t.lowercase()] ?: t
          val slug = pageSlug(t)
          "Opening **$label** ($slug). Type the command or click the suggestion to jump there."
        } ?: "I could not resolve a destination from your request."
      }

      AiIntentType.COUNT -> {
        val label = intent.entityLabel ?: "items"
        val n = intent.count ?: 0
        "There ${if (n == 1L) "is" else "are"} **$n $label** ${
          if (n == 1L) "currently on record" else "currently on record"
        }."
      }

      AiIntentType.SEARCH_PATIENTS -> {
        intent.detail ?: "No patients matched that search."
      }

      AiIntentType.WORKFLOW -> {
        val n = intent.count ?: 0
        if (n > 0) {
          "There ${if (n == 1L) "is" else "are"} **$n pending workflow approval(s)**. Review them in Workflow → Approvals."
        } else {
          "No pending workflow approvals. All instances are on track — check Workflow → Instances to review history."
        }
      }

      AiIntentType.ANALYTICS -> {
        intent.detail ?: analyze(hospitalId).detail ?: "No analytics available."
      }

      AiIntentType.HELP -> {
        helpText()
      }

      AiIntentType.CHAT -> {
        themedResponse(query, hospitalId)
      }
    }

  fun suggestions(): List<String> =
    listOf(
      "How many patients?",
      "Open patients",
      "Pending approvals",
      "Search patient Sara",
      "Revenue today",
      "Help",
    )

  fun helpText(): String =
    buildString {
      appendLine("I am the **SQUAMED Operations Assistant**. I can:")
      appendLine("- **Navigate** — \"open laboratory\", \"go to billing\"")
      appendLine("- **Count** — \"how many patients?\", \"appointments today\"")
      appendLine("- **Search** — \"search patient <name or MRN>\"")
      appendLine("- **Workflow** — \"pending approvals\"")
      appendLine("- **Analytics** — \"revenue today\", \"analytics overview\"")
      appendLine("- **Chat** — ask anything about the platform.")
    }

  private fun navigate(target: String): AiIntent? {
    val normalized =
      target
        .lowercase()
        .trim()
        .removeSuffix(" module")
        .removeSuffix(" page")
    val key =
      PAGE_LABELS.keys.firstOrNull { normalized == it || normalized.startsWith("$it ") || normalized.contains(it) }
        ?: PAGE_LABELS[normalized]
        ?: return null
    return AiIntent(AiIntentType.NAVIGATE, target = key, detail = PAGE_LABELS[key])
  }

  fun pageSlug(target: String?): String? {
    if (target == null) return null
    return when (target.lowercase()) {
      "analytics", "dashboard" -> "/dashboard"
      "patient registry", "patients" -> "/patients"
      "appointment management", "appointments", "schedule" -> "/appointments"
      "queue management", "queue", "waiting room" -> "/queue"
      "laboratory", "labs", "lab" -> "/laboratory"
      "pharmacy", "medications" -> "/pharmacy"
      "billing & invoicing", "billing", "finance", "invoices" -> "/billing"
      "orders", "services" -> "/orders"
      "communication centre", "communication" -> "/communication"
      "workflow engine", "workflow" -> "/workflow"
      "integrations", "interop", "fhir", "hl7" -> "/integrations"
      "ai assistant", "ai", "assistant" -> "/ai"
      else -> "/${target.lowercase().replace(" ", "-")}"
    }
  }

  private fun countEntity(
    key: String,
    hospitalId: UUID?,
  ): AiIntent {
    val (label: String, value: Long) =
      when (key) {
        "patients" -> {
          val s = analyticsService.patients(hospitalId)
          "patients" to s.total
        }

        "encounters" -> {
          val s = analyticsService.encounters(hospitalId)
          "encounters" to s.total
        }

        "appointments" -> {
          val s = analyticsService.appointments(hospitalId)
          "appointments (total)" to s.total
        }

        "queue" -> {
          val s = analyticsService.queue(hospitalId)
          "queue entries waiting" to s.waiting
        }

        "labOrders" -> {
          val s = analyticsService.lab(hospitalId)
          "lab orders (total)" to s.total
        }

        "invoices" -> {
          "invoices (total)" to
            entityManager
              .createQuery("select count(i) from Invoice i where (:hid is null or i.hospitalId = :hid)", Long::class.java)
              .setParameter("hid", hospitalId)
              .singleResult
        }

        "approvals" -> {
          "pending approvals" to workflowPendingCount(hospitalId)
        }

        "workflow" -> {
          "workflow instances" to workflowInstanceCount(hospitalId)
        }

        "tasks" -> {
          "open tasks" to taskCount(hospitalId)
        }

        else -> {
          key to 0L
        }
      }
    return AiIntent(AiIntentType.COUNT, entityLabel = label, count = value)
  }

  private fun analyze(hospitalId: UUID?): AiIntent {
    val o = analyticsService.overview(hospitalId)
    val detail =
      buildString {
        appendLine("**Operational snapshot**")
        appendLine("- **${o.totalPatients} patients** in the registry, **${o.patientsToday}** registered today.")
        appendLine(
          "- **${o.activeEncounters} active encounters**; **${o.encountersToday}** started, **${o.completedEncountersToday}** completed today.",
        )
        appendLine("- **${o.appointmentsToday} appointments** today; **${o.queueWaiting} queue entries waiting**.")
        appendLine("- Lab: **${o.labOrdersToday} orders** today, **${o.labPending} pending**.")
        appendLine("- Revenue today: **QR ${"%.2f".format(o.revenueTodayQar)}**; outstanding: **QR ${"%.2f".format(o.amountDueQar)}**.")
      }
    return AiIntent(AiIntentType.ANALYTICS, detail = detail)
  }

  private fun searchPatients(
    needle: String,
    hospitalId: UUID?,
  ): AiIntent {
    val q = needle.trim()
    if (q.isEmpty()) {
      return AiIntent(
        AiIntentType.SEARCH_PATIENTS,
        detail = "Search patients with \"search patient <name or MRN>\".",
      )
    }
    val matches = patientService.search(q, hospitalId).take(8)
    return if (matches.isEmpty()) {
      AiIntent(AiIntentType.SEARCH_PATIENTS, detail = "No patients matched \"$q\".")
    } else {
      val lines =
        matches
          .map { p ->
            val name = listOfNotNull(p.preferredName ?: p.firstName, p.lastName).joinToString(" ").ifBlank { p.firstName ?: "—" }
            "- **$name** · MRN ${p.mrn ?: "—"} · ${p.gender ?: "?"} · DOB ${p.dateOfBirth ?: "—"}"
          }.joinToString("\n")
      AiIntent(
        AiIntentType.SEARCH_PATIENTS,
        target = matches.first().id?.toString(),
        detail = "Found **${matches.size}** patient(s) for \"$q\":\n$lines",
      )
    }
  }

  private fun themedResponse(
    query: String,
    hospitalId: UUID?,
  ): String {
    val lower = query.lowercase()
    return when {
      "hi" == lower || lower.startsWith("hi ") -> {
        "Hello! Ask me about patients, approvals, revenue — or say \"help\" for commands."
      }

      lower.contains("thank") -> {
        "Happy to help. Anything else, press Cmd+K."
      }

      lower.contains("who are you") || lower.contains("your name") -> {
        "I am the SQUAMED Operations Assistant — a permission-aware gateway over your hospital OS. Try \"how many patients?\""
      }

      lower.contains("remember") -> {
        "Noted — I log each interaction, so supervisors can review every assistant response."
      }

      else -> {
        "I can answer live operational questions (\"how many patients?\"), route commands (\"open laboratory\") and search the registry (\"search patient <name>\"). Say \"help\" for the full list."
      }
    }
  }

  private fun workflowPendingCount(hospitalId: UUID?): Long =
    entityManager
      .createQuery(
        "select count(w) from WorkflowInstance w where w.status = 'WAITING_APPROVAL' and (:hid is null or w.hospitalId = :hid)",
        Long::class.java,
      ).setParameter("hid", hospitalId)
      .singleResult

  private fun workflowInstanceCount(hospitalId: UUID?): Long =
    entityManager
      .createQuery("select count(w) from WorkflowInstance w where (:hid is null or w.hospitalId = :hid)", Long::class.java)
      .setParameter("hid", hospitalId)
      .singleResult

  private fun taskCount(hospitalId: UUID?): Long =
    entityManager
      .createQuery(
        "select count(t) from Task t where t.status in ('open', 'in_progress') and (:hid is null or t.hospitalId = :hid)",
        Long::class.java,
      ).setParameter("hid", hospitalId)
      .singleResult
}
