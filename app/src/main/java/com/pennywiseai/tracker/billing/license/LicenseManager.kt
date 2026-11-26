package com.pennywiseai.tracker.billing.license

import com.pennywiseai.tracker.core.entitlement.LicenseRestorer
import com.pennywiseai.tracker.data.preferences.StoredLicense
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Offline fork: server-validated license activation is not supported. */
@Singleton
class LicenseManager @Inject constructor(
) : LicenseRestorer {
    sealed class ActivationOutcome {
        data object Activated : ActivationOutcome()
        data object InvalidKey : ActivationOutcome()
        data object ActiveElsewhere : ActivationOutcome()
        data object Offline : ActivationOutcome()
    }

    private val noLicense = MutableStateFlow<StoredLicense?>(null)
    val license: StateFlow<StoredLicense?> = noLicense.asStateFlow()

    private val noEntitlement = MutableStateFlow<Boolean?>(false)
    val resolvedLicenseEntitlement: StateFlow<Boolean?> = noEntitlement.asStateFlow()

    private val notLicensed = MutableStateFlow(false)
    val isLicensed: StateFlow<Boolean> = notLicensed.asStateFlow()

    suspend fun activate(rawKey: String): ActivationOutcome = ActivationOutcome.Offline
    suspend fun moveHere(rawKey: String, email: String): ActivationOutcome = ActivationOutcome.Offline
    suspend fun remove() = Unit
    override suspend fun restore(key: String, previousInstanceId: String?) = Unit
    suspend fun revalidateIfDue() = Unit
}
