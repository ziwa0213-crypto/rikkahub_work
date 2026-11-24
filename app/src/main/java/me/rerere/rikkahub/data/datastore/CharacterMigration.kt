package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.BuildConfig

internal fun Settings.shouldOfferCharacterMigration(launches: Int): Boolean =
    !init && !characterMigrationPrompted &&
        characterMigrationState == CharacterMigrationState.NOT_ASKED &&
        lastRunVersionCode == 0 && launches >= 2

/** Manual opt-in is the only entry point for the character transfer flow. */
internal fun Settings.shouldOfferCharacterMigrationManually(launches: Int): Boolean =
    shouldOfferCharacterMigration(launches)

/**
 * Mark a fresh installation before the user can enable the experimental role mode later.
 *
 * An upgraded installation keeps version 0 so it can still be recognized as a legacy user
 * once its persisted launch count reaches the migration threshold.
 */
internal fun Settings.markNewInstallVersionIfNeeded(
    launches: Int,
    versionCode: Int = BuildConfig.VERSION_CODE.toInt(),
): Settings = if (
    launches <= 1 &&
    lastRunVersionCode == 0 &&
    !characterMigrationPrompted &&
    characterMigrationState == CharacterMigrationState.NOT_ASKED
) {
    copy(lastRunVersionCode = versionCode)
} else {
    this
}

internal fun Settings.withRestoredCharacterMigration(versionCode: Int = BuildConfig.VERSION_CODE.toInt()) = copy(
    characterMigrationPrompted = true,
    lastRunVersionCode = versionCode,
)
