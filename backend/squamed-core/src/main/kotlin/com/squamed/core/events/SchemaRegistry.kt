package com.squamed.core.events

import com.fasterxml.jackson.databind.JsonNode
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion

/**
 * Schema-registry harness (00.02 / gate 8): every published wire contract lives as a JSON Schema
 * under `schemas/` on the classpath and is validated here. Breaking a contract must fail the
 * contract tests, never reach a consumer.
 */
class SchemaRegistry {
  private val factory: JsonSchemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)

  private val cache = HashMap<String, com.networknt.schema.JsonSchema>()

  /** @return human-readable violations; empty list = valid. */
  fun validate(
    schemaName: String,
    node: JsonNode,
  ): List<String> = schemaFor(schemaName).validate(node).map { it.message }

  private fun schemaFor(schemaName: String): com.networknt.schema.JsonSchema =
    cache.getOrPut(schemaName) {
      val stream =
        SchemaRegistry::class.java.getResourceAsStream("/schemas/$schemaName")
          ?: error("schema not on classpath: schemas/$schemaName")
      stream.use { factory.getSchema(it.readBytes().decodeToString()) }
    }
}
