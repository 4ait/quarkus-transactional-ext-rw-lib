package ru.code4a.quarkus.transactional.rw.ext

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class HibernateDefaultReadOnlySupportTest {
  @ParameterizedTest
  @ValueSource(
    strings = [
      "6.6.42.Final", "7.1.21.Final", "7.2.9.Final", "7.3.1.Final", "7.4.0.CR1", "7.4.0.Final",
      "7.4.9.Final", "7.4.10-SNAPSHOT", "7.5.0.Alpha1", "8.0.0.Final"
    ]
  )
  fun enablesReadOnlySessionsWhereTheNpeIsFixed(version: String) {
    assertTrue(HibernateDefaultReadOnlySupport.isDefaultReadOnlySafe(version))
  }

  @ParameterizedTest
  @ValueSource(
    strings = [
      "7.0.5.Final", "7.1.10.Final", "7.1.20.Final", "7.2.0.Final", "7.2.4.Final", "7.2.8.Final",
      "7.3.0.Final", "7.4.0.Beta2", "7.4.0-SNAPSHOT"
    ]
  )
  fun keepsReadOnlySessionsDisabledWhereHibernateCanFail(version: String) {
    assertFalse(HibernateDefaultReadOnlySupport.isDefaultReadOnlySafe(version))
  }

  @Test
  fun anUnrecognizedVersionKeepsThePreviousRule() {
    assertFalse(HibernateDefaultReadOnlySupport.isDefaultReadOnlySafe("7.x-custom"))
    assertTrue(HibernateDefaultReadOnlySupport.isDefaultReadOnlySafe("custom"))
  }
}
