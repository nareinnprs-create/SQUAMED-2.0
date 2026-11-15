package com.squamed.core.events.gateway

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import java.util.concurrent.Executors

@Configuration
class RealtimeRedisConfig {
  /** Redis pub/sub relay (chunk-00 item 8): fan the dispatcher's channel publishes out to WebSocket sessions. */
  @Bean
  fun realtimeListenerContainer(
    connectionFactory: RedisConnectionFactory,
    bridge: RealtimeEventBridge,
  ): RedisMessageListenerContainer =
    RedisMessageListenerContainer().apply {
      setConnectionFactory(connectionFactory)
      // Container default executors are SimpleAsyncTaskExecutor = one new thread per dispatched message;
      // under load that spawned ~6 threads/s and burned kernel time (JFR nfr-burst.jfr, 2026-11-15).
      setSubscriptionExecutor(
        Executors.newFixedThreadPool(1) { r -> Thread(r, "realtime-subscription").apply { isDaemon = true } },
      )
      setTaskExecutor(
        Executors.newFixedThreadPool(2) { r -> Thread(r, "realtime-dispatch").apply { isDaemon = true } },
      )
      addMessageListener(bridge, ChannelTopic(REALTIME_CHANNEL))
    }

  companion object {
    const val REALTIME_CHANNEL = "squamed.events"
  }
}
