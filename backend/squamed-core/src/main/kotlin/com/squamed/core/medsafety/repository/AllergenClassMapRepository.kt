package com.squamed.core.medsafety.repository

import com.squamed.core.medsafety.domain.AllergenClassMap
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AllergenClassMapRepository : JpaRepository<AllergenClassMap, UUID>
