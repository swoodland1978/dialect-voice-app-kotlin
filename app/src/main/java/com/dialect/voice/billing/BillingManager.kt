package com.dialect.voice.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// Must match the one-time product created in Play Console -> Monetize -> Products ->
// One-time products, and functions/src/lib/config.ts. Consumable - can be bought repeatedly,
// credit just keeps stacking. No subscription, no separate unlock purchase. The price itself
// is never hardcoded anywhere in the app - it's read live from Play Billing (see priceText
// below) so the UI can never go stale when the price is changed in Play Console.
// TEMPORARY: pointed at the cheap "one_minute_test" product to verify the run-out -> upsell
// -> rebuy loop end to end. Swap back to dialect_voice_credit_30min before going live.
const val CREDIT_PRODUCT_ID = "one_minute_test"

sealed class PurchaseUiState {
    data object Idle : PurchaseUiState()
    data object Connecting : PurchaseUiState()
    data object Verifying : PurchaseUiState()
    data object Success : PurchaseUiState()
    data class Error(val message: String) : PurchaseUiState()
}

// Wraps Play Billing for the single credit product. Purchases are only ever fulfilled AFTER
// the verifyPurchase Cloud Function confirms them server-side against the Android Publisher
// API - never consumed purely client-side, per Google's documented anti-fraud guidance. The
// server-side write to Firestore (creditSecondsRemaining) is the real activation; this class
// just drives the purchase UI and hands the token off to be verified.
class BillingManager(
    context: Context,
    private val functions: FirebaseFunctions
) : PurchasesUpdatedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _purchaseState = MutableStateFlow<PurchaseUiState>(PurchaseUiState.Idle)
    val purchaseState: StateFlow<PurchaseUiState> = _purchaseState.asStateFlow()

    // Play's own locale-formatted price for the credit product (e.g. "£8.99"), null until
    // queryProductDetailsAsync returns. Always the real, current price - never hardcoded.
    private val _priceText = MutableStateFlow<String?>(null)
    val priceText: StateFlow<String?> = _priceText.asStateFlow()

    private var productDetails: ProductDetails? = null

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                android.util.Log.d(
                    "BillingManager",
                    "onBillingSetupFinished: code=${billingResult.responseCode} msg=${billingResult.debugMessage}"
                )
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProductDetails()
                    reconcilePendingPurchases()
                } else {
                    _purchaseState.value = PurchaseUiState.Error("Billing unavailable: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                android.util.Log.d("BillingManager", "onBillingServiceDisconnected")
                // BillingClient reconnects automatically the next time it's needed.
            }
        })
    }

    private fun queryProductDetails() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(CREDIT_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()

        // Billing Library 8+ wraps the result in QueryProductDetailsResult (adds
        // unfetchedProductList alongside productDetailsList) instead of handing back the
        // list directly - see the migration guide for PBL 7 -> 8.
        billingClient.queryProductDetailsAsync(params) { billingResult, queryProductDetailsResult ->
            val productDetailsList = queryProductDetailsResult.productDetailsList
            android.util.Log.d(
                "BillingManager",
                "queryProductDetails: code=${billingResult.responseCode} msg=${billingResult.debugMessage} " +
                    "count=${productDetailsList.size} ids=${productDetailsList.map { it.productId }}"
            )
            val details = productDetailsList.firstOrNull()
            productDetails = details
            _priceText.value = details?.oneTimePurchaseOfferDetails?.formattedPrice
        }
    }

    fun launchBillingFlow(activity: Activity) {
        val details = productDetails ?: run {
            _purchaseState.value = PurchaseUiState.Error("Not available yet - try again in a moment")
            return
        }
        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        billingClient.launchBillingFlow(activity, flowParams)
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases?.forEach { handlePurchase(it) }
            BillingClient.BillingResponseCode.USER_CANCELED -> _purchaseState.value = PurchaseUiState.Idle
            else -> _purchaseState.value = PurchaseUiState.Error(billingResult.debugMessage)
        }
    }

    private fun reconcilePendingPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { _, purchases ->
            // A consumable that hasn't been consumed yet still shows up here (e.g. the app
            // was killed between purchase and consume) - needs finishing off.
            purchases
                .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                .forEach { handlePurchase(it) }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        _purchaseState.value = PurchaseUiState.Verifying
        scope.launch {
            try {
                val productId = purchase.products.firstOrNull() ?: CREDIT_PRODUCT_ID
                val data = hashMapOf(
                    "purchaseToken" to purchase.purchaseToken,
                    "productId" to productId
                )
                functions.getHttpsCallable("verifyPurchase").call(data).await()

                // Consumable - consuming both fulfils it and frees it up to be bought again.
                val consumeParams = ConsumeParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient.consumeAsync(consumeParams) { _, _ -> }

                _purchaseState.value = PurchaseUiState.Success
            } catch (e: Exception) {
                // Verification failed server-side - deliberately do NOT consume locally. Play
                // keeps the purchase pending and auto-refunds it after 3 days if this never
                // succeeds, rather than us granting credit on an unverified purchase.
                _purchaseState.value = PurchaseUiState.Error(e.message ?: "Purchase verification failed")
            }
        }
    }

    fun endConnection() {
        billingClient.endConnection()
    }
}
