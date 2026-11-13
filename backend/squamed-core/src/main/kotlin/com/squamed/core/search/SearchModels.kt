package com.squamed.core.search

data class SearchHitView(
  val index: String,
  val id: String,
  val type: String,
  val title: String,
  val subtitle: String? = null,
  val score: Double? = null,
  val fields: Map<String, Any?> = emptyMap(),
)

data class SearchResponseView(
  val total: Long,
  val hits: List<SearchHitView>,
)
