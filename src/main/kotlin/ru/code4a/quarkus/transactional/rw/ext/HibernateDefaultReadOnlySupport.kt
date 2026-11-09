package ru.code4a.quarkus.transactional.rw.ext

/**
 * Decides whether `Session.setDefaultReadOnly(true)` is safe for a Hibernate ORM version.
 *
 * Read-only entities keep no loaded state. Hibernate ORM 7.1.10–7.1.20, 7.2.0–7.2.8 and 7.3.0
 * still read that state in `EntityInitializerImpl.updateInitializedEntityInstance` when a query
 * meets an already initialized entity enhanced for lazy loading, and fail with
 * `NullPointerException: Cannot read the array length because "loadedState" is null`
 * (https://hibernate.atlassian.net/browse/HHH-20251, also reported as HHH-20190).
 * The fix is released in 7.1.21, 7.2.9, 7.3.1 and 7.4.0.CR1. The 7.0 line never received it.
 */
object HibernateDefaultReadOnlySupport {
  private val versionPattern = Regex("""^(\d+)\.(\d+)\.(\d+)(?:[.-]([A-Za-z]+))?""")

  // The first release of each affected 7.x line that contains the fix.
  private val fixedReleases =
    mapOf(
      1 to Release(21, Qualifier.FINAL),
      2 to Release(9, Qualifier.FINAL),
      3 to Release(1, Qualifier.FINAL),
      4 to Release(0, Qualifier.CR)
    )

  fun isDefaultReadOnlySafe(hibernateVersion: String): Boolean {
    val match =
      versionPattern.find(hibernateVersion)
        // An unrecognized format keeps the previous conservative rule for 7.x.
        ?: return !hibernateVersion.startsWith("7.")
    val (major, minor, patch, qualifier) = match.destructured
    if (major.toInt() != 7) return true
    // Later 7.x lines branch from code that already contains the fix.
    if (minor.toInt() >= 5) return true
    val fixed = fixedReleases[minor.toInt()] ?: return false
    val release = Release(patch.toInt(), Qualifier.of(qualifier))
    return compareValuesBy(release, fixed, Release::patch, Release::qualifier) >= 0
  }

  private data class Release(
    val patch: Int,
    val qualifier: Qualifier
  )

  // Hibernate orders pre-releases as Alpha < Beta < CR < Final; snapshots rank below them.
  private enum class Qualifier {
    UNKNOWN,
    ALPHA,
    BETA,
    CR,
    FINAL;

    companion object {
      fun of(text: String): Qualifier =
        when (text.lowercase()) {
          "", "final" -> FINAL
          "cr" -> CR
          "beta" -> BETA
          "alpha" -> ALPHA
          else -> UNKNOWN
        }
    }
  }
}
