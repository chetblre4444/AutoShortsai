package com.autoshorts.studio.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.autoshorts.studio.data.SecurePrefsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

enum class SubscriptionState {
    LOADING,
    ACTIVE_OR_TRIAL,
    EXPIRED_OR_NONE
}

class BillingManager(
    context: Context,
    private val prefs: SecurePrefsManager
) : PurchasesUpdatedListener {

    companion object {
        const val SUBSCRIPTION_PRODUCT_ID = "autoshorts_pro_monthly"
    }

    private val _subState = MutableStateFlow(
        if (prefs.isSubscriptionActiveCached) SubscriptionState.ACTIVE_OR_TRIAL else SubscriptionState.LOADING
    )
    val subState: StateFlow<SubscriptionState> = _subState

    private var productDetails: ProductDetails? = null

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProductDetails()
                    refreshPurchasesAsync()
                } else {
                    _subState.value = if (prefs.isSubscriptionActiveCached) {
                        SubscriptionState.ACTIVE_OR_TRIAL
                    } else {
                        SubscriptionState.EXPIRED_OR_NONE
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                // Повторное подключение при следующем запросе
            }
        })
    }

    private fun queryProductDetails() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SUBSCRIPTION_PRODUCT_ID)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )
        val params = QueryProductDetailsParams.newBuilder().setProductList(productList).build()
        billingClient.queryProductDetailsAsync(params) { result, detailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && detailsList.isNotEmpty()) {
                productDetails = detailsList.first()
            }
        }
    }

    fun refreshPurchasesAsync() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                processPurchases(purchases)
            }
        }
    }

    suspend fun verifyActiveSubscriptionBlocking(): Boolean = suspendCancellableCoroutine { cont ->
        if (!billingClient.isReady) {
            cont.resume(prefs.isSubscriptionActiveCached)
            return@suspendCancellableCoroutine
        }
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val active = purchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                prefs.isSubscriptionActiveCached = active
                _subState.value = if (active) SubscriptionState.ACTIVE_OR_TRIAL else SubscriptionState.EXPIRED_OR_NONE
                cont.resume(active)
            } else {
                cont.resume(prefs.isSubscriptionActiveCached)
            }
        }
    }

    fun launchSubscriptionFlow(activity: Activity) {
        val details = productDetails ?: return
        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .setOfferToken(offerToken)
                .build()
        )
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            processPurchases(purchases)
        }
    }

    private fun processPurchases(purchases: List<Purchase>) {
        var hasValidSub = false
        for (purchase in purchases) {
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                hasValidSub = true
                if (!purchase.isAcknowledged) {
                    val ackParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    billingClient.acknowledgePurchase(ackParams) {}
                }
            }
        }
        prefs.isSubscriptionActiveCached = hasValidSub
        _subState.value = if (hasValidSub) SubscriptionState.ACTIVE_OR_TRIAL else SubscriptionState.EXPIRED_OR_NONE
    }
}

