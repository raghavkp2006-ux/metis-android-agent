package dev.metis.agent

/** Production names remain stable. The synthetic build also gets explicit resource namespaces.
 * Source comments can evolve independently of the historical retirement certificate.
 */
object AppIsolation {
    fun name(productionName: String): String = if (BuildConfig.IS_CAPABILITY_TEST) {
        "${BuildConfig.APPLICATION_ID}.$productionName"
    } else productionName

    val databaseName: String get() = name("metis-personal.db")
    val fieldsAlias: String get() = name("metis.personal.fields.v1")
}
