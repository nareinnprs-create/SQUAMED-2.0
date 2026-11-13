package com.squamed.core.search.config

import org.apache.hc.core5.http.HttpHost
import org.opensearch.client.json.jackson.JacksonJsonpMapper
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(name = ["squamed.search.enabled"], havingValue = "true", matchIfMissing = true)
class OpenSearchConfig {
  @Bean
  fun openSearchClient(
    @Value("\${squamed.search.opensearch-uris:http://localhost:9200}") uris: String,
  ): OpenSearchClient {
    val hosts =
      uris
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { HttpHost.create(it) }
    val builder = ApacheHttpClient5TransportBuilder.builder(*hosts.toTypedArray())
    builder.setMapper(JacksonJsonpMapper())
    return OpenSearchClient(builder.build())
  }
}
