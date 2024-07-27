package ru.code4a.quarkus.transactional.rw.ext

import io.quarkus.arc.properties.IfBuildProperty
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.EntityManager
import ru.code4a.quarkus.transactional.rw.processor.NewReadTransactionalRWProcessor

@ApplicationScoped
@IfBuildProperty(name = "ru.code4a.transactional-rw.serializable.enabled", stringValue = "true")
class SerializableNewReadTransactionalRWProcessor(
  private val entityManager: EntityManager
) : NewReadTransactionalRWProcessor {
  override val priority: Long = 1_001_000

  override fun <T> with(block: () -> T): T {
    entityManager
      .createNativeQuery("SET TRANSACTION ISOLATION LEVEL SERIALIZABLE READ ONLY DEFERRABLE")
      .executeUpdate()

    return block()
  }
}
