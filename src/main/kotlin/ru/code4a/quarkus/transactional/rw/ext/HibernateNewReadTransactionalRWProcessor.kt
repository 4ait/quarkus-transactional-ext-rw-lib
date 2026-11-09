package ru.code4a.quarkus.transactional.rw.ext

import io.quarkus.arc.properties.IfBuildProperty
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.EntityManager
import org.hibernate.FlushMode
import org.hibernate.Session
import ru.code4a.quarkus.transactional.rw.processor.NewReadTransactionalRWProcessor

@ApplicationScoped
@IfBuildProperty(name = "ru.code4a.transactional-rw.hibernate.enabled", stringValue = "true")
class HibernateNewReadTransactionalRWProcessor(
  private val entityManager: EntityManager,
  private val readOnlySessionInitializer: HibernateReadOnlySessionInitializer
) : NewReadTransactionalRWProcessor {
  override val priority: Long = 1_001_000

  override fun <T> with(block: () -> T): T {
    val session = entityManager.unwrap(Session::class.java)

    // Generated at build time for the application's Hibernate ORM: affected 7.x releases fail
    // on read-only entities enhanced for lazy loading, so there it leaves the Session as is (HHH-20251).
    readOnlySessionInitializer.initialize(session)
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
