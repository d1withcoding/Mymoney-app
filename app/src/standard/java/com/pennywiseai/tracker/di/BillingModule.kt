package com.pennywiseai.tracker.di

import com.pennywiseai.tracker.billing.EntitlementSource
import com.pennywiseai.tracker.billing.FdroidBillingGateway
import com.pennywiseai.tracker.billing.PurchaseGateway
import com.pennywiseai.tracker.billing.PurchaseLauncher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for the standard (Play Store) flavor. [PurchaseGateway] is
 * uses the offline always-Pro implementation; purchases are not supported in
 * this network-free fork.
 *
 * F-Droid has a sibling module under `app/src/fdroid/` binding everything to
 * the always-Pro stub. Shared `main` sources only ever import the interfaces.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BillingModule {

    @Binds
    @Singleton
    abstract fun bindPurchaseGateway(impl: FdroidBillingGateway): PurchaseGateway

    @Binds
    @Singleton
    abstract fun bindEntitlementSource(impl: FdroidBillingGateway): EntitlementSource

    @Binds
    @Singleton
    abstract fun bindPurchaseLauncher(impl: FdroidBillingGateway): PurchaseLauncher
}
