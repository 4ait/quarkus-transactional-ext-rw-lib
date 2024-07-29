package ru.code4a.quarkus.transactional.rw.ext

import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.EntityManager
import ru.code4a.quarkus.transactional.rw.processor.NewReadTransactionalRWProcessor

@ApplicationScoped
class SerializableNewReadTransactionalRWProcessor(
  private val entityManager: EntityManager
) : NewReadTransactionalRWProcessor {
  override val priority: Long = 1_000_000

  override fun <T> with(block: () -> T): T {
    entityManager
      .createNativeQuery("SET TRANSACTION ISOLATION LEVEL SERIALIZABLE READ ONLY DEFERRABLE")
      .executeUpdate()

    return block()
  }
}
