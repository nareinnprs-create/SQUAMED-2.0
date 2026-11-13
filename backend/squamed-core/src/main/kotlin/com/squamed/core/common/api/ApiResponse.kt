package com.squamed.core.common.api

data class ApiResponse<T>(
  val data: T?,
  val success: Boolean,
  val meta: Map<String, Any> = emptyMap(),
) {
  companion object {
    fun <T> ok(data: T? = null): ApiResponse<T> = ApiResponse(data = data, success = true)
  }
}
