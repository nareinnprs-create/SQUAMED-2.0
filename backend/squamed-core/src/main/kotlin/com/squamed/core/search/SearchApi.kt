package com.squamed.core.search

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.identity.security.RequiresPermission
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/search")
class SearchApi(
  private val searchService: SearchService,
) {
  @GetMapping("/search")
  @RequiresPermission("patient:read:patient")
  fun search(
    @RequestParam(name = "q") q: String,
    @RequestParam(name = "type", required = false) type: String?,
    @RequestParam(name = "from", defaultValue = "0") from: Int,
    @RequestParam(name = "size", defaultValue = "20") size: Int,
  ): ApiResponse<SearchResponseView> {
    val hits = searchService.search(q, type, from, size)
    return ApiResponse.ok(SearchResponseView(total = hits.size.toLong(), hits = hits))
  }

  @GetMapping("/search/indices")
  @RequiresPermission("patient:read:patient")
  fun indices(): ApiResponse<Map<String, Long>> = ApiResponse.ok(searchService.indexCounts())
}
