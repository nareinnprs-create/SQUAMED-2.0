package com.squamed.core.common.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProductionHygieneTest {
  @Test
  fun `development profile is not guarded`() {
    ProductionHygiene.validate(
      activeProfiles = listOf("dev"),
      jwtSecret = null,
      dbPassword = null,
      bootstrapAdminPassword = null,
      bootstrapAdminEmail = null,
    )
  }

  @Test
  fun `prod with development jwt secret fails fast`() {
    val e =
      assertThrows(IllegalStateException::class.java) {
        ProductionHygiene.validate(
          activeProfiles = listOf("prod"),
          jwtSecret = ProductionHygiene.devJwtSecret,
          dbPassword = "real-db-secret",
          bootstrapAdminPassword = "RealAdminPass1",
          bootstrapAdminEmail = "admin@example.org",
        )
      }
    assertTrue(e.message!!.contains("JWT_SECRET"), e.message)
  }

  @Test
  fun `prod with development db password fails fast`() {
    val e =
      assertThrows(IllegalStateException::class.java) {
        ProductionHygiene.validate(
          activeProfiles = listOf("prod"),
          jwtSecret = "a-real-jwt-secret-that-is-long-enough-ok",
          dbPassword = ProductionHygiene.devDbPassword,
          bootstrapAdminPassword = "RealAdminPass1",
          bootstrapAdminEmail = "admin@example.org",
        )
      }
    assertTrue(e.message!!.contains("DB_PASSWORD"), e.message)
  }

  @Test
  fun `prod with weak admin bootstrap fails fast`() {
    val e =
      assertThrows(IllegalStateException::class.java) {
        ProductionHygiene.validate(
          activeProfiles = listOf("prod"),
          jwtSecret = "a-real-jwt-secret-that-is-long-enough-ok",
          dbPassword = "real-db-secret",
          bootstrapAdminPassword = "short",
          bootstrapAdminEmail = "admin@example.org",
        )
      }
    assertTrue(e.message!!.contains("BOOTSTRAP_ADMIN_PASSWORD"), e.message)
  }

  @Test
  fun `prod with missing admin email fails fast`() {
    val e =
      assertThrows(IllegalStateException::class.java) {
        ProductionHygiene.validate(
          activeProfiles = listOf("prod"),
          jwtSecret = "a-real-jwt-secret-that-is-long-enough-ok",
          dbPassword = "real-db-secret",
          bootstrapAdminPassword = "RealAdminPass1",
          bootstrapAdminEmail = null,
        )
      }
    assertTrue(e.message!!.contains("BOOTSTRAP_ADMIN_EMAIL"), e.message)
  }

  @Test
  fun `prod with fully secure values passes`() {
    ProductionHygiene.validate(
      activeProfiles = listOf("prod"),
      jwtSecret = "a-real-jwt-secret-that-is-long-enough-ok",
      dbPassword = "real-db-secret",
      bootstrapAdminPassword = "RealAdminPass1",
      bootstrapAdminEmail = "admin@example.org",
    )
  }

  @Test
  fun `short jwt secret is rejected`() {
    val e =
      assertThrows(IllegalStateException::class.java) {
        ProductionHygiene.validate(
          activeProfiles = listOf("prod"),
          jwtSecret = "too-short",
          dbPassword = "real-db-secret",
          bootstrapAdminPassword = "RealAdminPass1",
          bootstrapAdminEmail = "admin@example.org",
        )
      }
    assertTrue(e.message!!.contains("at least 32"), e.message)
  }

  @Test
  fun `empty values accumulate all messages`() {
    val e =
      assertThrows(IllegalStateException::class.java) {
        ProductionHygiene.validate(
          activeProfiles = listOf("prod"),
          jwtSecret = "",
          dbPassword = "",
          bootstrapAdminPassword = "",
          bootstrapAdminEmail = "",
        )
      }
    assertEquals(4, e.message!!.split("  - ").size - 1)
  }
}
