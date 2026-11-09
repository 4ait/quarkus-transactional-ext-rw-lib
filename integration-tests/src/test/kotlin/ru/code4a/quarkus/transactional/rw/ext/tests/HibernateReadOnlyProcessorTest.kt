package ru.code4a.quarkus.transactional.rw.ext.tests

import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.hibernate.Version
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.code4a.quarkus.transactional.rw.ext.HibernateDefaultReadOnlySupport

@QuarkusTest
class HibernateReadOnlyProcessorTest {
    @Inject
    lateinit var service: ReadOnlyGraphTestService

    @BeforeEach
    fun createGraph() {
        service.createGraph()
    }

    @Test
    fun readTransactionsUseReadOnlySessionsWhereTheHibernateVersionSupportsThem() {
        val expected = HibernateDefaultReadOnlySupport.isDefaultReadOnlySafe(Version.getVersionString())
        val result = service.loadAccountWithFetchGraph()
        assertEquals(listOf(expected, expected), listOf(result.defaultReadOnly, result.accountReadOnly))
    }

    // HHH-20251: affected Hibernate versions fail here when the Session is read-only.
    @Test
    fun fetchGraphOverReadOnlyEnhancedEntitiesLoadsTheWholeGraph() {
        val result = service.loadAccountWithFetchGraph()
        assertEquals(1L, result.userNameId)
        assertEquals(setOf(1L, 2L), result.assignmentUserNameIds)
        assertTrue(result.graphInitialized)
    }
}
