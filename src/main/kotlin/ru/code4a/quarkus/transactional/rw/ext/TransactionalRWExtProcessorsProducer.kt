package ru.code4a.quarkus.transactional.rw.ext

import io.quarkus.arc.Unremovable
import io.quarkus.arc.properties.IfBuildProperty
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.Dependent
import jakarta.enterprise.inject.Produces
import ru.code4a.quarkus.transactional.rw.processor.NewReadTransactionalRWProcessor

@Dependent
class TransactionalRWExtProcessorsProducer(
  private val serializableNewReadTransactionalRWProcessor: SerializableNewReadTransactionalRWProcessor,
  private val hibernateNewReadTransactionalRWProcessor: HibernateNewReadTransactionalRWProcessor
) {
  @Produces
  @ApplicationScoped
  @IfBuildProperty(name = "ru.code4a.transactional-rw.serializable.enabled", stringValue = "true")
  fun getSerializableNewReadTransactionalRWProcessor(): NewReadTransactionalRWProcessor = serializableNewReadTransactionalRWProcessor

  @Produces
  @ApplicationScoped
  @IfBuildProperty(name = "ru.code4a.transactional-rw.hibernate.enabled", stringValue = "true")
  fun getHibernateNewReadTransactionalRWProcessor(): NewReadTransactionalRWProcessor = hibernateNewReadTransactionalRWProcessor
}
