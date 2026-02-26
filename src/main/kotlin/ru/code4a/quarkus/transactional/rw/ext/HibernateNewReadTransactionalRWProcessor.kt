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
  private val entityManager: EntityManager
) : NewReadTransactionalRWProcessor {
  private val enabledDefaultReadOnly: Boolean

  init {
    val hibernateVersion = org.hibernate.Version.getVersionString()

    // NPE bug in Hibernate >= 7.0.0 and <= 7.2.4
    enabledDefaultReadOnly = hibernateVersion.startsWith("7.") == false
  }

  override val priority: Long = 1_001_000

  override fun <T> with(block: () -> T): T {
    val session = entityManager.unwrap(Session::class.java)

    if(enabledDefaultReadOnly) {
      session.isDefaultReadOnly = true
    }
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
