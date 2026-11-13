#!/bin/bash
# Creates all Phase 1 Kafka topics for SQUAMED

KAFKA_BOOTSTRAP="localhost:9092"
PARTITIONS=3
REPLICATION=1

declare -a TOPICS=(
  "squamed.patient.registered"
  "squamed.patient.updated"
  "squamed.patient.merged"
  "squamed.patient.unmerged"
  "squamed.appointment.created"
  "squamed.appointment.cancelled"
  "squamed.appointment.rescheduled"
  "squamed.appointment.checked-in"
  "squamed.appointment.started"
  "squamed.appointment.completed"
  "squamed.checkin.completed"
  "squamed.queue.token-issued"
  "squamed.queue.patient-called"
  "squamed.queue.patient-completed"
  "squamed.queue.escalated"
  "squamed.encounter.started"
  "squamed.encounter.completed"
  "squamed.vitals.recorded"
  "squamed.note.created"
  "squamed.note.signed"
  "squamed.exam.recorded"
  "squamed.referral.created"
  "squamed.careplan.created"
  "squamed.order.created"
  "squamed.order.submitted"
  "squamed.order.cancelled"
  "squamed.order.completed"
  "squamed.sample.collected"
  "squamed.sample.received"
  "squamed.result.preliminary"
  "squamed.result.validated"
  "squamed.result.released"
  "squamed.result.critical"
  "squamed.prescription.created"
  "squamed.prescription.verified"
  "squamed.medication.dispensed"
  "squamed.charge.created"
  "squamed.estimate.created"
  "squamed.invoice.generated"
  "squamed.invoice.issued"
  "squamed.payment.received"
  "squamed.payment.failed"
  "squamed.refund.processed"
  "squamed.deposit.created"
  "squamed.task.created"
  "squamed.task.assigned"
  "squamed.task.started"
  "squamed.task.completed"
  "squamed.task.escalated"
  "squamed.task.cancelled"
  "squamed.notification.sent"
  "squamed.message.sent"
  "squamed.workflow.triggered"
  "squamed.workflow.step-completed"
  "squamed.workflow.completed"
  "squamed.workflow.failed"
  "squamed.audit.entry-created"
  "squamed.ai.interaction-logged"
  "squamed.ai.scribe.session-started"
  "squamed.ai.scribe.session-completed"
  "squamed.ai.scribe.note-signed"
)

for TOPIC in "${TOPICS[@]}"; do
  docker exec squamed-kafka /opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server "$KAFKA_BOOTSTRAP" \
    --create \
    --if-not-exists \
    --topic "$TOPIC" \
    --partitions "$PARTITIONS" \
    --replication-factor "$REPLICATION"
  echo "Created: $TOPIC"
done

echo "All topics created."