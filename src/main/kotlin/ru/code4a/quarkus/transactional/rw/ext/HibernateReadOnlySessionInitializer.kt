package ru.code4a.quarkus.transactional.rw.ext

import org.hibernate.Session

/**
 * Prepares the Session of a new read transaction for read-only use.
 *
 * Whether the Session can be read-only depends on the Hibernate ORM version of the application,
 * which its build fixes. The deployment module therefore generates the implementation at build
 * time: it calls `setDefaultReadOnly(true)` where [HibernateDefaultReadOnlySupport] allows it and
 * does nothing otherwise, so read transactions do not check the version at runtime.
 */
interface HibernateReadOnlySessionInitializer {
  fun initialize(session: Session)
}
