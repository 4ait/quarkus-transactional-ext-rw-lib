package ru.code4a.quarkus.transactional.rw.ext

import io.quarkus.arc.properties.IfBuildProperty
import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import ru.code4a.quarkus.transactional.rw.processor.BeforeNewWriteTransactionalRWProcessor
import kotlin.properties.Delegates

@ApplicationScoped
@IfBuildProperty(name = "ru.code4a.transactional-rw.serializable.auto-retry.enabled", stringValue = "true")
class SerializableRetryerBeforeNewWriteTransactionalRWProcessor : BeforeNewWriteTransactionalRWProcessor {
  override val priority: Long = 1_000_000

  @ConfigProperty(name = "ru.code4a.transactional-rw.serializable.auto-retry.max-attempts", defaultValue="100")
  lateinit var configMaxAttempts: String

  @ConfigProperty(name = "ru.code4a.transactional-rw.serializable.auto-retry.delay-ms", defaultValue="10")
  lateinit var configDelayMs: String

  var maxAttempts by Delegates.notNull<Int>()
  var delayMs by Delegates.notNull<Long>()

  @PostConstruct
  fun initialized() {
    maxAttempts = configMaxAttempts.toInt()
    delayMs = configDelayMs.toLong()
  }

  override fun <T> with(block: () -> T): T {
    return TransactionUtils.executeSerializableWithRetry(
      maxAttempts = maxAttempts,
      delayMs = delayMs
    ) {
      block()
    }
  }
}
