package ru.code4a.quarkus.transactional.rw.ext.tests

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.Cacheable
import jakarta.persistence.Entity
import jakarta.persistence.EntityManager
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.transaction.TransactionSynchronizationRegistry
import java.sql.Connection
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.hibernate.FlushMode
import org.hibernate.Session
import org.hibernate.annotations.Cache
import org.hibernate.annotations.CacheConcurrencyStrategy
import org.hibernate.jpa.HibernateHints
import ru.code4a.quarkus.transactional.rw.annotations.TransactionalReadOnly
import ru.code4a.quarkus.transactional.rw.annotations.TransactionalWrite
import ru.code4a.quarkus.transactional.rw.ext.SerializableNewReadTransactionalRWProcessor

@Entity
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
open class CacheProbe {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null
    open var payload: String = ""
}

// This entity must reach PostgreSQL on every read to exercise a real SSI conflict.
@Entity
open class RetryCounter {
    @Id
    open var id: Long = 0
    open var counterValue: Int = 0
}

data class TransactionState(
    val isolation: String,
    val readOnly: Boolean,
    val deferrable: Boolean,
    val backendPid: Int
) {
    fun modes(): List<Any> = listOf(isolation, readOnly, deferrable)
}

fun readTransactionState(connection: Connection): TransactionState =
    connection.createStatement().use { statement ->
        statement.executeQuery(
            "SELECT current_setting('transaction_isolation'), " +
                "current_setting('transaction_read_only'), " +
                "current_setting('transaction_deferrable'), pg_backend_pid()"
        ).use { rows ->
            check(rows.next()) { "PostgreSQL did not return transaction characteristics" }
            TransactionState(rows.getString(1), rows.getString(2) == "on",
                rows.getString(3) == "on", rows.getInt(4))
        }
    }

class ExpectedRollback : RuntimeException("Expected rollback of the outer transaction")

class RetryAttempts {
    val count = AtomicInteger()
    val states = CopyOnWriteArrayList<TransactionState>()
    val transactions = CopyOnWriteArrayList<Any>()
}

@ApplicationScoped
open class TransactionTestService {
    @Inject
    lateinit var entityManager: EntityManager

    @Inject
    lateinit var transactions: TransactionSynchronizationRegistry

    @Inject
    lateinit var readProcessor: SerializableNewReadTransactionalRWProcessor

    fun currentState(): TransactionState =
        entityManager.callWithConnection<Connection, TransactionState>(::readTransactionState)

    @TransactionalReadOnly
    open fun readState(): TransactionState = currentState()

    @TransactionalWrite
    open fun writeState(): TransactionState = currentState()

    @TransactionalWrite(TransactionalWrite.Type.REQUIRES_NEW)
    open fun writeStateInNewTransaction(): TransactionState = currentState()

    @TransactionalReadOnly(TransactionalReadOnly.Type.REQUIRES_NEW)
    open fun readStateInNewTransaction(): TransactionState = currentState()

    @TransactionalReadOnly
    open fun readFlushMode(): FlushMode = entityManager.unwrap(Session::class.java).hibernateFlushMode

    @TransactionalWrite
    open fun createProbe(payload: String): Long = persistProbe(payload)

    @TransactionalWrite(TransactionalWrite.Type.REQUIRES_NEW)
    open fun createProbeInNewTransaction(payload: String): Long = persistProbe(payload)

    fun persistProbe(payload: String): Long {
        val probe = CacheProbe().apply { this.payload = payload }
        entityManager.persist(probe)
        return requireNotNull(probe.id)
    }

    @TransactionalWrite
    open fun createProbeAndRollback(payload: String) {
        persistProbe(payload)
        throw ExpectedRollback()
    }

    @TransactionalWrite
    open fun renameProbe(id: Long, payload: String) {
        entityManager.find(CacheProbe::class.java, id).payload = payload
    }

    @TransactionalReadOnly
    open fun cachedPayload(id: Long): String {
        val builder = entityManager.criteriaBuilder
        val query = builder.createQuery(String::class.java)
        val probe = query.from(CacheProbe::class.java)
        query.select(probe.get("payload")).where(builder.equal(probe.get<Long>("id"), id))
        return entityManager.createQuery(query)
            .setHint(HibernateHints.HINT_CACHEABLE, true).singleResult
    }

    @TransactionalReadOnly
    open fun countProbes(payload: String): Long {
        val builder = entityManager.criteriaBuilder
        val query = builder.createQuery(Long::class.javaObjectType)
        val probe = query.from(CacheProbe::class.java)
        query.select(builder.count(probe)).where(builder.equal(probe.get<String>("payload"), payload))
        return entityManager.createQuery(query).singleResult
    }

    @TransactionalReadOnly
    open fun insertInsideReadTransaction() {
        // Bypass MANUAL flush so PostgreSQL itself must enforce READ ONLY.
        entityManager.runWithConnection<Connection> { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("INSERT INTO CacheProbe (payload) VALUES ('forbidden-write')")
            }
        }
    }

    @TransactionalWrite
    open fun changeReadModeAfterFirstQuery(blockRan: AtomicBoolean) {
        currentState()
        readProcessor.with { blockRan.set(true) }
    }

    @TransactionalReadOnly
    open fun executeLegacyNativeSetForRegressionControl() {
        // Deliberately reproduce the original bug only in this unpublished test application.
        entityManager.createNativeQuery(
            "SET TRANSACTION ISOLATION LEVEL SERIALIZABLE READ ONLY DEFERRABLE"
        ).executeUpdate()
    }

    @TransactionalWrite
    open fun createCounter(id: Long) {
        entityManager.persist(RetryCounter().apply { this.id = id })
    }

    @TransactionalReadOnly
    open fun counterValue(id: Long): Int = entityManager.find(RetryCounter::class.java, id).counterValue

    @TransactionalWrite
    open fun incrementAfterBarrier(id: Long, barrier: CyclicBarrier, attempts: RetryAttempts) {
        val attempt = attempts.count.incrementAndGet()
        attempts.states.add(currentState())
        attempts.transactions.add(requireNotNull(transactions.transactionKey))
        val counter = entityManager.find(RetryCounter::class.java, id)
        if (attempt == 1) barrier.await(10, TimeUnit.SECONDS)
        counter.counterValue++
    }
}

@ApplicationScoped
open class NestedTransactionTestService {
    @Inject
    lateinit var service: TransactionTestService

    @TransactionalWrite
    open fun readJoinedToWrite(): List<TransactionState> =
        listOf(service.currentState(), service.readState(), service.currentState())

    @TransactionalReadOnly
    open fun writeJoinedToRead(): TransactionState = service.writeState()

    @TransactionalReadOnly
    open fun requiresNewWriteInsideRead(): List<TransactionState> =
        listOf(service.currentState(), service.writeStateInNewTransaction(), service.currentState())

    @TransactionalReadOnly
    open fun requiresNewReadInsideRead(): List<TransactionState> =
        listOf(service.currentState(), service.readStateInNewTransaction(), service.currentState())

    @TransactionalWrite
    open fun requiresNewCommitSurvivesOuterRollback(outerPayload: String, innerPayload: String) {
        service.persistProbe(outerPayload)
        service.createProbeInNewTransaction(innerPayload)
        throw ExpectedRollback()
    }
}
