package ru.code4a.quarkus.transactional.rw.ext

import io.quarkus.arc.properties.IfBuildProperty
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.EntityManager
import java.sql.Connection
import ru.code4a.quarkus.transactional.rw.processor.NewWriteTransactionalRWProcessor

@ApplicationScoped
@IfBuildProperty(name = "ru.code4a.transactional-rw.serializable.enabled", stringValue = "true")
class SerializableNewWriteTransactionalRWProcessor(
  private val entityManager: EntityManager
) : NewWriteTransactionalRWProcessor {
  override val priority: Long = 1_000_000

  override fun <T> with(block: () -> T): T {
    // SET only changes transaction characteristics. Native DML execution would
    // additionally invalidate Hibernate caches for unrelated tables.
    entityManager.runWithConnection<Connection> { connection ->
      connection.createStatement().use { statement ->
        statement.execute(
          "SET TRANSACTION ISOLATION LEVEL SERIALIZABLE READ WRITE"
        )
      }
    }

    return block()
  }
}
