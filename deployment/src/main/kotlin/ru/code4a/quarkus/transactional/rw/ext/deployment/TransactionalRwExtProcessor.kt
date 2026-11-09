package ru.code4a.quarkus.transactional.rw.ext.deployment

import io.quarkus.arc.deployment.GeneratedBeanBuildItem
import io.quarkus.arc.deployment.GeneratedBeanGizmoAdaptor
import io.quarkus.deployment.annotations.BuildProducer
import io.quarkus.deployment.annotations.BuildStep
import io.quarkus.deployment.builditem.FeatureBuildItem
import io.quarkus.gizmo.ClassCreator
import io.quarkus.gizmo.MethodDescriptor
import jakarta.inject.Singleton
import org.eclipse.microprofile.config.ConfigProvider
import org.hibernate.Session
import org.jboss.logging.Logger
import ru.code4a.quarkus.transactional.rw.ext.HibernateDefaultReadOnlySupport
import ru.code4a.quarkus.transactional.rw.ext.HibernateReadOnlySessionInitializer

class TransactionalRwExtProcessor {
  @BuildStep
  fun feature(): FeatureBuildItem = FeatureBuildItem(FEATURE)

  /**
   * The application build fixes the Hibernate ORM used at runtime, so whether read-only sessions
   * are safe is decided here and compiled into the generated initializer: read transactions run
   * either `setDefaultReadOnly(true)` or nothing, without checking the version again.
   */
  @BuildStep
  fun generateReadOnlySessionInitializer(generatedBeans: BuildProducer<GeneratedBeanBuildItem>) {
    val hibernateProcessorEnabled =
      ConfigProvider.getConfig()
        .getOptionalValue(HIBERNATE_ENABLED_PROPERTY, Boolean::class.javaObjectType)
        .orElse(false)
    // Only the Hibernate processor injects the initializer; without it Hibernate ORM may be absent.
    if (!hibernateProcessorEnabled) return
    val version = org.hibernate.Version.getVersionString()
    val readOnly = HibernateDefaultReadOnlySupport.isDefaultReadOnlySafe(version)
    if (!readOnly) {
      LOG.warnf(
        "Hibernate ORM %s fails on read-only sessions with entities enhanced for lazy loading " +
          "(HHH-20251), so read transactions keep read-write sessions. " +
          "Upgrade Hibernate ORM to 7.1.21, 7.2.9, 7.3.1, 7.4.0.CR1 or later to enable them.",
        version
      )
    }
    ClassCreator
      .builder()
      .classOutput(GeneratedBeanGizmoAdaptor(generatedBeans))
      .className(INITIALIZER_CLASS)
      .interfaces(HibernateReadOnlySessionInitializer::class.java)
      .build()
      .use { type ->
        type.addAnnotation(Singleton::class.java)
        type.getMethodCreator("<init>", Void.TYPE).use { constructor ->
          constructor.invokeSpecialMethod(MethodDescriptor.ofConstructor(Any::class.java), constructor.getThis())
          constructor.returnVoid()
        }
        type.getMethodCreator("initialize", Void.TYPE, Session::class.java).use { method ->
          if (readOnly) {
            method.invokeInterfaceMethod(SET_DEFAULT_READ_ONLY, method.getMethodParam(0), method.load(true))
          }
          method.returnVoid()
        }
      }
  }

  private companion object {
    const val FEATURE = "transactional-rw-ext"
    const val HIBERNATE_ENABLED_PROPERTY = "ru.code4a.transactional-rw.hibernate.enabled"
    const val INITIALIZER_CLASS =
      "ru.code4a.quarkus.transactional.rw.ext.generated.GeneratedHibernateReadOnlySessionInitializer"
    val SET_DEFAULT_READ_ONLY: MethodDescriptor =
      MethodDescriptor.ofMethod(Session::class.java, "setDefaultReadOnly", "void", "boolean")
    val LOG: Logger = Logger.getLogger(TransactionalRwExtProcessor::class.java)
  }
}
