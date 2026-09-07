package com.squamed.core.scribe.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScribeSttTest {
  @Test
  fun echoProviderReturnsCanonicalTranscript() {
    val provider = EchoSpeechToTextProvider()
    val text = provider.transcribe(ByteArray(32000) { 1 }, "audio/wav")
    assertTrue(text.contains("Patient:"), "transcript has patient line")
    assertTrue(text.contains("Doctor:"), "transcript has doctor line")
    assertEquals("echo", provider.providerId())
  }

  @Test
  fun echoProviderRejectsEmptyAudio() {
    val provider = EchoSpeechToTextProvider()
    val ex =
      assertThrows(IllegalArgumentException::class.java) {
        provider.transcribe(ByteArray(0), "audio/wav")
      }
    assertEquals("SCRIBE_AUDIO_EMPTY", ex.message)
  }

  @Test
  fun openAiProviderRejectsMissingKey() {
    val provider = OpenAiWhisperProvider("", "https://api.openai.com/v1", "whisper-1")
    val ex =
      assertThrows(IllegalStateException::class.java) {
        provider.transcribe(ByteArray(10) { 1 }, "audio/mp3")
      }
    assertTrue(ex.message!!.contains("AI_STT_PROVIDER_MISCONFIGURED"))
  }
}
