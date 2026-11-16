package com.squamed.core.common.api

import org.springframework.data.domain.Page

data class ApiResponse<T>(
  val data: T?,
  val success: Boolean,
  val meta: Map<String, Any> = emptyMap(),
) {
  companion object {
    fun <T> ok(data: T? = null): ApiResponse<T> = ApiResponse(data = data, success = true)

    /** Paged list response: emits the offset-mode meta block from the repository Page (style guide §Pagination). */
    fun <T> ok(
      data: T?,
      page: Page<*>,
    ): ApiResponse<T> =
      ApiResponse(
        data = data,
        success = true,
        meta =
          mapOf(
            "page" to page.number,
            "size" to page.size,
            "total" to page.totalElements,
            "pages" to page.totalPages,
          ),
      )

    /** Cursor-mode (or otherwise custom) meta block, e.g. {limit, hasNext, nextCursor} for append-only lists. */
    fun <T> ok(
      data: T?,
      meta: Map<String, Any>,
    ): ApiResponse<T> = ApiResponse(data = data, success = true, meta = meta)
  }
}
