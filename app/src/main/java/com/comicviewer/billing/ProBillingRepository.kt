package com.comicviewer.billing

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val PRO_UNLOCK_PRODUCT_ID = "comicviewer_pro_unlock"

private const val OK_RESPONSE_CODE = BillingClient.BillingResponseCode.OK
private const val PURCHASE_RECHECK_DELAY_MS = 1_500L
private const val MAX_PURCHASE_RECHECK_ATTEMPTS = 2
private const val TAG = "ProBillingRepository"

sealed interface ProBillingStatus {
    data object Loading : ProBillingStatus
    data object Available : ProBillingStatus
    data object Unavailable : ProBillingStatus
}

enum class ProBillingMessage {
    CheckingPurchase,
    PurchaseUnavailable,
    PurchaseFailed,
}

data class ProBillingState(
    val status: ProBillingStatus = ProBillingStatus.Loading,
    val isProUnlocked: Boolean = false,
    val productTitle: String? = null,
    val formattedPrice: String? = null,
    val message: ProBillingMessage? = null,
    val purchaseSuccessEventId: Long = 0L,
)

internal fun isRecoverableBillingResponseCode(responseCode: Int): Boolean =
    when (responseCode) {
        BillingClient.BillingResponseCode.NETWORK_ERROR,
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
        BillingClient.BillingResponseCode.ERROR,
        BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED,
        -> true
        else -> false
    }

internal fun userMessageForBillingResponseCode(responseCode: Int): ProBillingMessage =
    if (isRecoverableBillingResponseCode(responseCode)) {
        ProBillingMessage.CheckingPurchase
    } else {
        ProBillingMessage.PurchaseFailed
    }

class ProBillingRepository(context: Context) : PurchasesUpdatedListener {
    private val appContext = context.applicationContext
    private var proProductDetails: ProductDetails? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val billingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enableAutoServiceReconnection()
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build(),
        )
        .build()

    private val _state = MutableStateFlow(ProBillingState())
    val state: StateFlow<ProBillingState> = _state.asStateFlow()
    private var isConnectionInProgress = false
    private var purchaseFlowInProgress = false
    private var purchaseRecheckAttempts = 0
    private var acknowledgeRetryScheduled = false
    private var purchaseSuccessEventId = 0L

    fun startConnection() {
        if (billingClient.isReady) {
            queryProductDetails()
            queryPurchases(allowSuccessEvent = purchaseFlowInProgress)
            return
        }
        if (isConnectionInProgress) return

        isConnectionInProgress = true
        _state.value = _state.value.copy(status = ProBillingStatus.Loading, message = null)
        billingClient.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    isConnectionInProgress = false
                    if (billingResult.responseCode == OK_RESPONSE_CODE) {
                        queryProductDetails()
                        queryPurchases(allowSuccessEvent = purchaseFlowInProgress)
                    } else {
                        handleUnavailableBillingResult(billingResult)
                    }
                }

                override fun onBillingServiceDisconnected() {
                    isConnectionInProgress = false
                    _state.value = _state.value.copy(
                        status = ProBillingStatus.Unavailable,
                        message = null,
                    )
                }
            },
        )
    }

    fun endConnection() {
        mainHandler.removeCallbacksAndMessages(null)
        if (billingClient.isReady) billingClient.endConnection()
    }

    fun launchPurchase(activity: Activity) {
        val productDetails = proProductDetails
        if (!billingClient.isReady || productDetails == null) {
            _state.value = _state.value.copy(
                status = ProBillingStatus.Unavailable,
                message = ProBillingMessage.PurchaseUnavailable,
            )
            return
        }

        purchaseFlowInProgress = true
        purchaseRecheckAttempts = 0
        _state.value = _state.value.copy(message = null)
        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()
        val result = billingClient.launchBillingFlow(activity, flowParams)
        if (result.responseCode != OK_RESPONSE_CODE) {
            handlePurchaseFlowResult(result)
        }
    }

    fun restorePurchases() {
        if (billingClient.isReady) {
            queryPurchases(allowSuccessEvent = purchaseFlowInProgress)
        } else {
            startConnection()
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            OK_RESPONSE_CODE -> {
                if (purchases.isNullOrEmpty()) {
                    queryPurchases(allowSuccessEvent = purchaseFlowInProgress)
                } else {
                    handlePurchases(purchases, allowSuccessEvent = purchaseFlowInProgress)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                purchaseFlowInProgress = false
            }
            else -> handlePurchaseFlowResult(billingResult)
        }
    }

    private fun queryProductDetails() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRO_UNLOCK_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsResult ->
            if (billingResult.responseCode != OK_RESPONSE_CODE) {
                handleUnavailableBillingResult(billingResult)
                return@queryProductDetailsAsync
            }

            proProductDetails = productDetailsResult.productDetailsList.firstOrNull()
            val productDetails = proProductDetails
            _state.value = if (productDetails != null) {
                _state.value.copy(
                    status = ProBillingStatus.Available,
                    productTitle = productDetails.title,
                    formattedPrice = productDetails.oneTimePurchaseOfferDetails?.formattedPrice,
                    message = null,
                )
            } else {
                _state.value.copy(
                    status = ProBillingStatus.Unavailable,
                    message = ProBillingMessage.PurchaseUnavailable,
                )
            }
        }
    }

    private fun queryPurchases(allowSuccessEvent: Boolean) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == OK_RESPONSE_CODE) {
                handlePurchases(purchases, allowSuccessEvent = allowSuccessEvent)
            } else {
                handleQueryPurchasesFailure(billingResult)
            }
        }
    }

    private fun handlePurchases(purchases: List<Purchase>, allowSuccessEvent: Boolean) {
        val wasUnlocked = _state.value.isProUnlocked
        val proPurchase = purchases.firstOrNull { purchase ->
            PRO_UNLOCK_PRODUCT_ID in purchase.products && purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        if (proPurchase != null) {
            acknowledgeIfNeeded(proPurchase)
            val shouldEmitSuccess = allowSuccessEvent && purchaseFlowInProgress && !wasUnlocked
            if (shouldEmitSuccess) {
                purchaseSuccessEventId += 1
            }
            purchaseFlowInProgress = false
            purchaseRecheckAttempts = 0
            _state.value = _state.value.copy(
                isProUnlocked = true,
                message = null,
                purchaseSuccessEventId = purchaseSuccessEventId,
            )
        } else {
            if (allowSuccessEvent) {
                schedulePurchaseFlowRecheckOrFail()
            } else {
                _state.value = _state.value.copy(isProUnlocked = false)
            }
        }
    }

    private fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.isAcknowledged) return

        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { billingResult ->
            if (billingResult.responseCode == OK_RESPONSE_CODE) {
                acknowledgeRetryScheduled = false
            } else {
                logBillingResult("Acknowledge Pro purchase failed", billingResult)
                if (isRecoverableBillingResponseCode(billingResult.responseCode)) {
                    if (!acknowledgeRetryScheduled) {
                        acknowledgeRetryScheduled = true
                        schedulePurchaseRecheck()
                    }
                } else if (!_state.value.isProUnlocked) {
                    _state.value = _state.value.copy(message = ProBillingMessage.PurchaseFailed)
                }
            }
        }
    }

    private fun handlePurchaseFlowResult(billingResult: BillingResult) {
        logBillingResult("Pro purchase flow returned a non-OK result", billingResult)
        if (isRecoverableBillingResponseCode(billingResult.responseCode)) {
            _state.value = _state.value.copy(message = ProBillingMessage.CheckingPurchase)
            queryPurchases(allowSuccessEvent = true)
        } else {
            purchaseFlowInProgress = false
            _state.value = _state.value.copy(message = userMessageForBillingResponseCode(billingResult.responseCode))
        }
    }

    private fun handleUnavailableBillingResult(billingResult: BillingResult) {
        logBillingResult("Play Billing is unavailable", billingResult)
        _state.value = _state.value.copy(
            status = ProBillingStatus.Unavailable,
            message = ProBillingMessage.PurchaseUnavailable,
        )
    }

    private fun handleQueryPurchasesFailure(billingResult: BillingResult) {
        logBillingResult("Query Pro purchases failed", billingResult)
        if (isRecoverableBillingResponseCode(billingResult.responseCode)) {
            _state.value = _state.value.copy(message = ProBillingMessage.CheckingPurchase)
            if (purchaseFlowInProgress) {
                schedulePurchaseFlowRecheckOrFail()
            }
        } else if (!_state.value.isProUnlocked) {
            _state.value = _state.value.copy(message = ProBillingMessage.PurchaseFailed)
        }
    }

    private fun schedulePurchaseFlowRecheckOrFail() {
        if (!purchaseFlowInProgress) {
            _state.value = _state.value.copy(message = ProBillingMessage.CheckingPurchase)
            return
        }
        if (purchaseRecheckAttempts >= MAX_PURCHASE_RECHECK_ATTEMPTS) {
            purchaseFlowInProgress = false
            purchaseRecheckAttempts = 0
            _state.value = _state.value.copy(message = ProBillingMessage.PurchaseFailed)
            return
        }

        purchaseRecheckAttempts += 1
        _state.value = _state.value.copy(message = ProBillingMessage.CheckingPurchase)
        schedulePurchaseRecheck()
    }

    private fun schedulePurchaseRecheck() {
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed(
            {
                if (billingClient.isReady) {
                    queryPurchases(allowSuccessEvent = purchaseFlowInProgress)
                } else {
                    startConnection()
                }
            },
            PURCHASE_RECHECK_DELAY_MS,
        )
    }

    private fun logBillingResult(message: String, billingResult: BillingResult) {
        Log.w(
            TAG,
            "$message. responseCode=${billingResult.responseCode}, debugMessage=${billingResult.debugMessage}",
        )
    }
}
