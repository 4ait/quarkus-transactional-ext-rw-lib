package ru.code4a.quarkus.transactional.rw.ext

import jakarta.persistence.OptimisticLockException
import jakarta.persistence.PessimisticLockException
import java.sql.SQLException
import java.util.concurrent.atomic.AtomicInteger
import org.hibernate.exception.LockAcquisitionException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import org.postgresql.util.PSQLException
import org.postgresql.util.PSQLState

class TransactionUtilsTest {
    @ParameterizedTest
    @MethodSource("retryableFailures")
    fun recognizesSupportedConcurrencyFailures(failure: Throwable) {
        assertTrue(TransactionUtils.isSerializationError(failure))
    }

    @ParameterizedTest
    @ValueSource(strings = ["could not serialize access", "concurrent update", "deadlock detected", "serialization failure"])
    fun recognizesExistingMessageBasedCompatibilityCases(message: String) {
        assertTrue(TransactionUtils.isSerializationError(IllegalStateException(message)))
    }

    @Test
    fun followsWrappedJdbcCauses() {
        assertTrue(TransactionUtils.isSerializationError(RuntimeException(RuntimeException(serializationFailure()))))
    }

    @Test
    fun followsSuppressedJdbcFailures() {
        val failure = RuntimeException("Transaction completion failed").apply {
            addSuppressed(serializationFailure())
        }
        assertTrue(TransactionUtils.isSerializationError(failure))
    }

    @Test
    fun doesNotClassifyUniqueConstraintViolationAsSerializationConflict() {
        assertFalse(TransactionUtils.isSerializationError(PSQLException("Duplicate key", PSQLState.UNIQUE_VIOLATION)))
    }

    @Test
    fun returnsSuccessfulResultWithoutRepeatingTheOperation() {
        val calls = AtomicInteger()
        val result = TransactionUtils.executeSerializableWithRetry(delayMs = 0) {
            calls.incrementAndGet()
            "result"
        }
        assertEquals(listOf("result", 1), listOf(result, calls.get()))
    }

    @Test
    fun retriesTransientConflictsAndReturnsTheFinalResult() {
        val calls = AtomicInteger()
        val result = TransactionUtils.executeSerializableWithRetry(maxAttempts = 3, delayMs = 0) {
            if (calls.incrementAndGet() < 3) throw serializationFailure()
            "committed"
        }
        assertEquals(listOf("committed", 3), listOf(result, calls.get()))
    }

    @Test
    fun retriesRuntimeWrappersProducedByRunWithConnection() {
        val calls = AtomicInteger()
        TransactionUtils.executeSerializableWithRetry(delayMs = 0) {
            if (calls.incrementAndGet() == 1) throw RuntimeException(serializationFailure())
        }
        assertEquals(2, calls.get())
    }

    @Test
    fun exhaustsTheConfiguredNumberOfAttemptsAndPreservesTheLastFailure() {
        val calls = AtomicInteger()
        val expected = serializationFailure()
        val actual = assertThrows(PSQLException::class.java) {
            TransactionUtils.executeSerializableWithRetry(maxAttempts = 3, delayMs = 0) {
                calls.incrementAndGet()
                throw expected
            }
        }
        assertSame(expected, actual)
        assertEquals(3, calls.get())
    }

    @Test
    fun doesNotRetryNonConcurrencyExceptions() {
        val calls = AtomicInteger()
        val expected = IllegalArgumentException("Invalid request")
        val actual = assertThrows(IllegalArgumentException::class.java) {
            TransactionUtils.executeSerializableWithRetry(delayMs = 0) {
                calls.incrementAndGet()
                throw expected
            }
        }
        assertSame(expected, actual)
        assertEquals(1, calls.get())
    }

    @Test
    fun oneAllowedAttemptDoesNotRetry() {
        val calls = AtomicInteger()
        assertThrows(PSQLException::class.java) {
            TransactionUtils.executeSerializableWithRetry(maxAttempts = 1, delayMs = 0) {
                calls.incrementAndGet()
                throw serializationFailure()
            }
        }
        assertEquals(1, calls.get())
    }

    @Test
    fun interruptedBackoffPreservesInterruptFlagAndStopsRetrying() {
        val calls = AtomicInteger()
        Thread.currentThread().interrupt()
        try {
            val failure = assertThrows(RuntimeException::class.java) {
                TransactionUtils.executeSerializableWithRetry(delayMs = 1) {
                    calls.incrementAndGet()
                    throw serializationFailure()
                }
            }
            assertTrue(Thread.currentThread().isInterrupted)
            assertTrue(failure.cause is InterruptedException)
            assertEquals(1, calls.get())
        } finally {
            Thread.interrupted()
        }
    }

    companion object {
        @JvmStatic
        fun retryableFailures(): List<Throwable> = listOf(
            serializationFailure(),
            PSQLException("Conflict", PSQLState.DEADLOCK_DETECTED),
            OptimisticLockException("Conflict"),
            PessimisticLockException("Conflict"),
            LockAcquisitionException("Conflict", SQLException("Conflict")),
            org.hibernate.PessimisticLockException("Conflict", SQLException("Conflict"), null)
        )

        private fun serializationFailure(): PSQLException =
            PSQLException("Conflict", PSQLState.SERIALIZATION_FAILURE)
    }
}
