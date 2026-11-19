package com.squamed.core.medsafety.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

/** Class expansion: an allergen token must also match these ingredient tokens. */
@Entity
@Table(name = "allergen_class_map", schema = "medication")
class AllergenClassMap(
  @Column(name = "allergen_token", nullable = false, length = 100)
  var allergenToken: String,
  @Column(name = "ingredient_token", nullable = false, length = 100)
  var ingredientToken: String,
) : BaseEntity()
