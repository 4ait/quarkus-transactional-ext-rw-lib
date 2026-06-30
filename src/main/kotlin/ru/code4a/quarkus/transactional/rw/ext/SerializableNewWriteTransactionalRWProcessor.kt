package ru.code4a.quarkus.transactional.rw.ext

import io.quarkus.arc.properties.IfBuildProperty
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.EntityManager
import ru.code4a.quarkus.transactional.rw.processor.NewWriteTransactionalRWProcessor

@ApplicationScoped
@IfBuildProperty(name = "ru.code4a.transactional-rw.serializable.enabled", stringValue = "true")
class SerializableNewWriteTransactionalRWProcessor(
  private val entityManager: EntityManager
) : NewWriteTransactionalRWProcessor {
  override val priority: Long = 1_000_000

  override fun <T> with(block: () -> T): T {
    entityManager
      .createNativeQuery("SET TRANSACTION ISOLATION LEVEL SERIALIZABLE READ WRITE")
      .executeUpdate()

    return block()
  }
}
