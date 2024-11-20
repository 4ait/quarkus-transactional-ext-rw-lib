package ru.code4a.quarkus.transactional.rw.ext

import jakarta.persistence.OptimisticLockException
import jakarta.persistence.PessimisticLockException
import org.hibernate.exception.LockAcquisitionException
import org.postgresql.util.PSQLException

object TransactionUtils {

  fun isSerializationError(e: Throwable): Boolean {
    var cause: Throwable? = e
    while (cause != null) {
      when (cause) {
        is OptimisticLockException,      // jakarta.persistence
        is LockAcquisitionException,     // org.hibernate.exception
        is PessimisticLockException -> return true  // jakarta.persistence
        is org.hibernate.PessimisticLockException -> return true
      }

      if (cause is PSQLException) {
        when (cause.sqlState) {
          "40001", // serialization_failure
          "40P01" -> return true  // deadlock_detected
        }
      }

      cause.message?.let { message ->
        if (message.contains("could not serialize access") ||
          message.contains("concurrent update") ||
          message.contains("deadlock detected") ||
          message.contains("serialization failure")
        ) {
          return true
        }
      }

      cause = cause.cause
    }
    return false
  }

  fun <T> executeSerializableWithRetry(
    maxAttempts: Int = 3,
    delayMs: Long = 100,
    operation: () -> T,
  ): T {
    var attempts = 0
    while (true) {
      try {
        attempts++
        return operation()
      } catch (e: Exception) {
        if (!isSerializationError(e) || attempts >= maxAttempts) {
          throw e
        }
        try {
          Thread.sleep(delayMs * attempts)
        } catch (ie: InterruptedException) {
          Thread.currentThread().interrupt()
          throw RuntimeException(ie)
        }
      }
    }
  }
}
