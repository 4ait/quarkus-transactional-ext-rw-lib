package ru.code4a.quarkus.transactional.rw.ext.tests

import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import jakarta.persistence.EntityManagerFactory
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.sql.DataSource
import org.hibernate.FlushMode
import org.hibernate.SessionFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@QuarkusTest
class SerializableProcessorsTest {
    @Inject
    lateinit var service: TransactionTestService

    @Inject
    lateinit var nested: NestedTransactionTestService

    @Inject
    lateinit var entityManagerFactory: EntityManagerFactory

    @Inject
    lateinit var dataSource: DataSource

    private val statistics get() = entityManagerFactory.unwrap(SessionFactory::class.java).statistics

    @Test
    fun readTransactionHasAllThreeRequiredCharacteristics() {
        assertEquals(listOf("serializable", true, true), service.readState().modes())
    }

    @Test
    fun writeTransactionIsSerializableAndAllowsWrites() {
        assertEquals(listOf("serializable", false, false), service.writeState().modes())
    }

    @Test
    fun readProcessorPreservesHibernateManualFlushMode() {
        assertEquals(FlushMode.MANUAL, service.readFlushMode())
    }

    @Test
    fun postgresRejectsWritesInsideReadOnlyTransaction() {
        val failure = assertThrows(Exception::class.java) { service.insertInsideReadTransaction() }
        assertEquals("25006", sqlState(failure))
    }

    @Test
    fun successfulWriteCommitsItsEntity() {
        val payload = UUID.randomUUID().toString()
        service.createProbe(payload)
        assertEquals(1L, service.countProbes(payload))
    }

    @Test
    fun failedWriteRollsBackAnAlreadyInsertedEntity() {
        val payload = UUID.randomUUID().toString()
        assertThrows(Exception::class.java) { service.createProbeAndRollback(payload) }
        assertEquals(0L, service.countProbes(payload))
    }

    @Test
    fun readJoinedToWriteDoesNotChangeOuterTransactionModeOrConnection() {
        val states = nested.readJoinedToWrite()
        assertEquals(List(3) { states.first() }, states)
        assertEquals(listOf("serializable", false, false), states.first().modes())
    }

    @Test
    fun writeCannotJoinAnExistingReadScope() {
        val failure = assertThrows(Exception::class.java) { nested.writeJoinedToRead() }
        assertTrue(generateSequence<Throwable>(failure) { it.cause }.any {
            it is IllegalStateException && it.message == "Write transaction level cannot be inside read transaction level"
        })
    }

    @Test
    fun requiresNewWriteSuspendsAndRestoresReadOnlyTransaction() {
        val states = nested.requiresNewWriteInsideRead()
        assertEquals(states[0], states[2])
        assertNotEquals(states[0].backendPid, states[1].backendPid)
        assertEquals(listOf(listOf("serializable", true, true), listOf("serializable", false, false)),
            states.take(2).map { it.modes() })
    }

    @Test
    fun requiresNewReadUsesIndependentConnectionAndRestoresOuterRead() {
        val states = nested.requiresNewReadInsideRead()
        assertEquals(states[0], states[2])
        assertNotEquals(states[0].backendPid, states[1].backendPid)
        assertEquals(List(3) { listOf("serializable", true, true) }, states.map { it.modes() })
    }

    @Test
    fun requiresNewCommitSurvivesOuterRollback() {
        val outerPayload = UUID.randomUUID().toString()
        val innerPayload = UUID.randomUUID().toString()
        assertThrows(Exception::class.java) {
            nested.requiresNewCommitSurvivesOuterRollback(outerPayload, innerPayload)
        }
        assertEquals(listOf(0L, 1L), listOf(service.countProbes(outerPayload), service.countProbes(innerPayload)))
    }

    @Test
    fun transactionModesDoNotLeakWhenTheSamePhysicalConnectionIsReused() {
        // Holding one of the two pool connections forces all calls below to reuse the other.
        dataSource.connection.use { held ->
            val heldPid = readTransactionState(held).backendPid
            val read = service.readState()
            val write = service.writeState()
            val defaults = dataSource.connection.use(::readTransactionState)
            assertNotEquals(heldPid, read.backendPid)
            assertEquals(listOf(read.backendPid, read.backendPid), listOf(write.backendPid, defaults.backendPid))
            assertEquals(listOf(listOf("serializable", true, true), listOf("serializable", false, false),
                listOf("read committed", false, false)), listOf(read.modes(), write.modes(), defaults.modes()))
        }
    }

    @Test
    fun readSetupDoesNotInvalidateQueryCacheTimestamps() {
        statistics.clear()
        service.readState()
        assertTrue(statistics.isStatisticsEnabled)
        assertEquals(0L, statistics.updateTimestampsCachePutCount)
    }

    @Test
    fun writeSetupWithoutDataChangesDoesNotInvalidateQueryCacheTimestamps() {
        statistics.clear()
        service.writeState()
        assertEquals(0L, statistics.updateTimestampsCachePutCount)
    }

    @Test
    fun queryCacheSurvivesIndependentReadTransactions() {
        val id = service.createProbe("cached")
        primeQueryCache(id, "cached")
        statistics.clear()
        service.readState()
        assertEquals("cached", service.cachedPayload(id))
        assertEquals(listOf(0L, 1L), listOf(statistics.queryCacheMissCount, statistics.queryCacheHitCount))
    }

    @Test
    fun realWritesStillInvalidatePreviouslyCachedQueryResults() {
        val id = service.createProbe("before")
        primeQueryCache(id, "before")
        statistics.clear()
        service.renameProbe(id, "after")
        assertEquals("after", service.cachedPayload(id))
        assertTrue(statistics.updateTimestampsCachePutCount > 0)
        assertEquals(1L, statistics.queryCacheMissCount)
    }

    @Test
    fun nativeDmlPositiveControlReproducesTheOriginalCacheInvalidation() {
        statistics.clear()
        service.executeLegacyNativeSetForRegressionControl()
        assertTrue(statistics.updateTimestampsCachePutCount > 0)
    }

    @Test
    fun failedSetupPropagatesPostgresErrorAndDoesNotRunBusinessCode() {
        val blockRan = AtomicBoolean()
        val failure = assertThrows(Exception::class.java) { service.changeReadModeAfterFirstQuery(blockRan) }
        assertEquals("25001", sqlState(failure))
        assertFalse(blockRan.get())
    }

    @Test
    fun serializationConflictRetriesTheWholeOperationInANewConfiguredTransaction() {
        val id = counterIds.incrementAndGet()
        service.createCounter(id)
        val barrier = CyclicBarrier(2)
        val attempts = List(2) { RetryAttempts() }
        val executor = Executors.newFixedThreadPool(2)
        try {
            val futures = attempts.map { trace -> executor.submit {
                service.incrementAfterBarrier(id, barrier, trace)
            } }
            futures.forEach { it.get(30, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
        assertEquals(2, service.counterValue(id))
        assertEquals(3, attempts.sumOf { it.count.get() })
        assertEquals(3, attempts.flatMap { it.transactions }.toSet().size)
        assertEquals(List(3) { listOf("serializable", false, false) },
            attempts.flatMap { it.states }.map { it.modes() })
    }

    private fun primeQueryCache(id: Long, expected: String) {
        // Establish a valid entry before measuring SET or invalidation. Hibernate may
        // conservatively reject an entry whose timestamp is too close to a preceding write.
        statistics.clear()
        repeat(10) {
            assertEquals(expected, service.cachedPayload(id))
            if (statistics.queryCacheHitCount > 0) return
        }
        throw AssertionError("The query cache did not produce a hit while preparing the test")
    }

    private fun sqlState(failure: Throwable): String? =
        generateSequence(failure) { it.cause }.filterIsInstance<SQLException>().firstOrNull()?.sqlState

    private companion object {
        val counterIds = AtomicLong(1000)
    }
}
