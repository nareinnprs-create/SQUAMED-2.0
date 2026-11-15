package com.squamed.core.it

import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor

/**
 * SPI seam (00.16): registers the in-memory exporter on the app's real SdkTracerProvider so PITs
 * can assert the trace pipeline (HTTP -> service -> DB -> WS) with `otel.traces.exporter=none`
 * (no live OTLP endpoint needed). Loaded via META-INF/services.
 */
object ObservabilitySpanCapture {
  val exporter: InMemorySpanExporter = InMemorySpanExporter.create()

  fun names(): Set<String> = exporter.finishedSpanItems.map { it.name }.toSet()

  fun spans(name: String) = exporter.finishedSpanItems.filter { it.name == name }

  fun reset() = exporter.reset()
}

class SpanCaptureConfigurer : AutoConfigurationCustomizerProvider {
  override fun customize(customizer: AutoConfigurationCustomizer) {
    customizer.addTracerProviderCustomizer { builder: SdkTracerProviderBuilder, _: ConfigProperties ->
      builder.addSpanProcessor(SimpleSpanProcessor.create(ObservabilitySpanCapture.exporter))
      builder
    }
  }
}
