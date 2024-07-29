package ru.code4a.quarkus.transactional.rw.ext

import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.EntityManager
import org.hibernate.FlushMode
import org.hibernate.Session
import ru.code4a.quarkus.transactional.rw.processor.NewReadTransactionalRWProcessor

@ApplicationScoped
class HibernateNewReadTransactionalRWProcessor(
  private val entityManager: EntityManager
) : NewReadTransactionalRWProcessor {
  override val priority: Long = 1_001_000

  override fun <T> with(block: () -> T): T {
    val session = entityManager.unwrap(Session::class.java)

    session.isDefaultReadOnly = true
    /*
     * The {@link Session} is only flushed when {@link Session#flush()}
     * is called explicitly. This mode is very efficient for read-only
     * transactions.
     * MANUAL,
     */
    session.hibernateFlushMode = FlushMode.MANUAL

    return block()
  }
}
