package com.squamed.core.identity.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "user_profile", schema = "identity")
class UserProfile(
  @Column(name = "user_id", nullable = false, unique = true)
  var userId: UUID,
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", insertable = false, updatable = false)
  var user: UserAccount? = null,
  @Column(name = "first_name", nullable = false, length = 100)
  var firstName: String,
  @Column(name = "last_name", nullable = false, length = 100)
  var lastName: String,
  @Column(name = "display_name", length = 200)
  var displayName: String? = null,
  @Column(name = "avatar_url", length = 500)
  var avatarUrl: String? = null,
  @Column(name = "language_preference", nullable = false, length = 20)
  var languagePreference: String = "en",
  @Column(name = "timezone", nullable = false, length = 50)
  var timezone: String = "UTC",
  @Column(name = "theme_preference", nullable = false, length = 20)
  var themePreference: String = "clinical",
) : BaseEntity() {
  val effectiveDisplayName: String
    get() = displayName ?: "$firstName $lastName"
}
