package com.squamed.core.analytics.api

data class NameCount(
  val name: String,
  val count: Long,
)

data class TimePoint(
  val label: String,
  val count: Long,
)

data class OverviewStats(
  val totalPatients: Long,
  val patientsToday: Long,
  val activeEncounters: Long,
  val encountersToday: Long,
  val completedEncountersToday: Long,
  val appointmentsToday: Long,
  val pendingAppointments: Long,
  val queueWaiting: Long,
  val avgWaitMinutes: Double?,
  val labOrdersToday: Long,
  val labPending: Long,
  val invoicesToday: Long,
  val revenueTodayQar: Double,
  val amountDueQar: Double,
  val dispensationsToday: Long,
)

data class PatientsStats(
  val total: Long,
  val byGender: List<NameCount>,
  val byBloodGroup: List<NameCount>,
  val registrationsByMonth: List<TimePoint>,
  val byNationality: List<NameCount>,
)

data class EncounterStats(
  val total: Long,
  val today: Long,
  val byStatus: List<NameCount>,
  val byType: List<NameCount>,
  val avgDurationMinutes: Double?,
)

data class AppointmentStats(
  val total: Long,
  val today: Long,
  val byStatus: List<NameCount>,
  val byType: List<NameCount>,
)

data class QueueStats(
  val waiting: Long,
  val calledToday: Long,
  val completedToday: Long,
  val avgWaitMinutes: Double?,
  val byServicePoint: List<NameCount>,
  val byQueueType: List<NameCount>,
)

data class LabStats(
  val total: Long,
  val today: Long,
  val byStatus: List<NameCount>,
  val byResultStatus: List<NameCount>,
  val releasedToday: Long,
)

data class BillingStats(
  val revenueTodayQar: Double,
  val revenueThisWeekQar: Double,
  val revenueThisMonthQar: Double,
  val outstandingQar: Double,
  val byStatus: List<NameCount>,
  val chargesPostedToday: Long,
  val chargesByCategory: List<NameCount>,
)
