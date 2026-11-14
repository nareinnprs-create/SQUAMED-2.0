package com.squamed.core.events.gateway

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer

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
      addMessageListener(bridge, ChannelTopic(REALTIME_CHANNEL))
    }

  companion object {
    const val REALTIME_CHANNEL = "squamed.events"
  }
}
