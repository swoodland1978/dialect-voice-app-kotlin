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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
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

private const val MAX_QUERY_ATTEMPTS = 5

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
    // Offer token for the one-time purchase offer, when the product uses Play's newer
    // "purchase options" model. Null/empty for legacy backwards-compatible products.
    private var oneTimeOfferToken: String? = null

    // reconcilePendingPurchases() calls verifyPurchase, which needs a Firebase auth token, so
    // it can only run once BOTH billing is connected AND the user is signed in - the two
    // happen independently (billing connects in onCreate, sign-in may be later). Without this
    // gate, a leftover purchase gets verified before auth is ready and the call fails with
    // "Sign in required".
    private var billingConnected = false
    private var authReady = false

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                android.util.Log.i(
                    "BillingManager",
                    "onBillingSetupFinished: code=${billingResult.responseCode} msg=${billingResult.debugMessage}"
                )
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    billingConnected = true
                    queryProductDetails()
                    maybeReconcile()
                } else {
                    _purchaseState.value = PurchaseUiState.Error("Billing unavailable: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                android.util.Log.i("BillingManager", "onBillingServiceDisconnected")
                billingConnected = false
                // BillingClient reconnects automatically the next time it's needed.
            }
        })
    }

    /** Called by the UI once Firebase Auth reports a signed-in user. */
    fun onAuthReady() {
        authReady = true
        maybeReconcile()
    }

    private fun maybeReconcile() {
        if (billingConnected && authReady) reconcilePendingPurchases()
    }

    /**
     * Re-run the product query if we still don't have a price - called when the paywall
     * opens. The query at connect time can come back empty on a flaky network and there's
     * otherwise nothing to retrigger it, which left the paywall stuck on "...".
     */
    fun ensureProductLoaded() {
        if (productDetails == null) {
            if (billingConnected) queryProductDetails() else startConnection()
        }
    }

    private fun queryProductDetails(attempt: Int = 1) {
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
            android.util.Log.i(
                "BillingManager",
                "queryProductDetails(attempt $attempt): code=${billingResult.responseCode} " +
                    "msg=${billingResult.debugMessage} count=${productDetailsList.size} " +
                    "ids=${productDetailsList.map { it.productId }}"
            )
            val details = productDetailsList.firstOrNull()

            // Billing Library 8: a one-time product created with Play's newer "purchase
            // options" model exposes its price only via oneTimePurchaseOfferDetailsList -
            // the legacy singular oneTimePurchaseOfferDetails comes back null for it, which
            // is why the paywall was stuck showing "...".
            val offer = details?.oneTimePurchaseOfferDetails
                ?: details?.oneTimePurchaseOfferDetailsList?.firstOrNull()

            if (offer?.formattedPrice != null) {
                productDetails = details
                oneTimeOfferToken = offer.offerToken?.takeIf { it.isNotEmpty() }
                _priceText.value = offer.formattedPrice
                android.util.Log.i(
                    "BillingManager",
                    "price=${offer.formattedPrice} hasOfferToken=${oneTimeOfferToken != null} " +
                        "offerListSize=${details?.oneTimePurchaseOfferDetailsList?.size}"
                )
            } else if (attempt < MAX_QUERY_ATTEMPTS) {
                // Empty/failed result (usually a transient network blip) - back off and retry
                // rather than leaving the paywall on "..." forever.
                val delayMs = 1500L * attempt
                android.util.Log.i("BillingManager", "no price yet - retrying in ${delayMs}ms")
                scope.launch {
                    kotlinx.coroutines.delay(delayMs)
                    queryProductDetails(attempt + 1)
                }
            } else {
                android.util.Log.w("BillingManager", "gave up loading price after $attempt attempts")
            }
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

        val launchResult = billingClient.launchBillingFlow(activity, flowParams)
        android.util.Log.i(
            "BillingManager",
            "launchBillingFlow: code=${launchResult.responseCode} msg=${launchResult.debugMessage}"
        )
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        android.util.Log.i(
            "BillingManager",
            "onPurchasesUpdated: code=${billingResult.responseCode} msg=${billingResult.debugMessage} " +
                "purchases=${purchases?.size ?: 0}"
        )
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases?.forEach { handlePurchase(it, userInitiated = true) }
            BillingClient.BillingResponseCode.USER_CANCELED -> _purchaseState.value = PurchaseUiState.Idle
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                // A prior purchase was never consumed (e.g. verify failed, or the app died
                // mid-flow). Finish it off so the product can be bought again.
                android.util.Log.i("BillingManager", "ITEM_ALREADY_OWNED - reconciling to clear it")
                reconcilePendingPurchases()
            }
            else -> _purchaseState.value = PurchaseUiState.Error(billingResult.debugMessage)
        }
    }

    private fun reconcilePendingPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { _, purchases ->
            android.util.Log.i("BillingManager", "reconcile: ${purchases.size} outstanding purchase(s)")
            // A consumable that hasn't been consumed yet still shows up here (e.g. the app
            // was killed between purchase and consume) - needs finishing off.
            purchases
                .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                .forEach { handlePurchase(it, userInitiated = false) }
        }
    }

    // userInitiated: true when this came from the user tapping Buy (drive the purchase UI);
    // false for background reconcile (stay quiet - don't flip the paywall into Verifying/etc).
    private fun handlePurchase(purchase: Purchase, userInitiated: Boolean) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (FirebaseAuth.getInstance().currentUser == null) {
            // Can't verify without an auth token. Reconcile will re-run from onAuthReady().
            android.util.Log.i("BillingManager", "handlePurchase deferred - not signed in yet")
            return
        }

        if (userInitiated) _purchaseState.value = PurchaseUiState.Verifying
        scope.launch {
            try {
                val productId = purchase.products.firstOrNull() ?: CREDIT_PRODUCT_ID
                val data = hashMapOf(
                    "purchaseToken" to purchase.purchaseToken,
                    "productId" to productId
                )
                functions.getHttpsCallable("verifyPurchase").call(data).await()
                consume(purchase, "verified")
                if (userInitiated) _purchaseState.value = PurchaseUiState.Success
            } catch (e: Exception) {
                val code = (e as? FirebaseFunctionsException)?.code
                if (code == FirebaseFunctionsException.Code.FAILED_PRECONDITION) {
                    // Google reports this purchase isn't in a purchased state - refunded,
                    // cancelled, or already consumed server-side. No credit to grant, but
                    // consume it so it stops blocking new purchases.
                    android.util.Log.w("BillingManager", "verify FAILED_PRECONDITION - consuming to unstick: ${e.message}")
                    consume(purchase, "voided")
                    if (userInitiated) _purchaseState.value = PurchaseUiState.Idle
                } else {
                    // Transient/config failure (network, Publisher API not set up, etc.) -
                    // do NOT consume; leave the purchase for reconcile to retry. Play
                    // auto-refunds an unconsumed purchase after 3 days.
                    android.util.Log.w("BillingManager", "verify failed (code=$code) - leaving for retry: ${e.message}")
                    if (userInitiated) {
                        _purchaseState.value = PurchaseUiState.Error(e.message ?: "Purchase verification failed")
                    }
                }
            }
        }
    }

    private fun consume(purchase: Purchase, reason: String) {
        val consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.consumeAsync(consumeParams) { result, _ ->
            android.util.Log.i(
                "BillingManager",
                "consume ($reason): code=${result.responseCode} msg=${result.debugMessage}"
            )
        }
    }

    fun endConnection() {
        billingClient.endConnection()
    }
}
