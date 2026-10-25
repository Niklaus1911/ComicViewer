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
private const val INITIAL_OPERATION_RETRY_DELAY_MS = 1_500L
private const val MAX_OPERATION_RETRY_DELAY_MS = 60_000L
private const val MAX_OPERATION_RETRY_ATTEMPTS = 6
private const val TAG = "ProBillingRepository"

sealed interface ProBillingStatus {
    data object Loading : ProBillingStatus
    data object Available : ProBillingStatus
    data object Unavailable : ProBillingStatus
}

enum class ProBillingMessage {
    CheckingPurchase,
    PurchasePending,
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
        BillingClient.BillingResponseCode.ITEM_NOT_OWNED,
        -> true
        else -> false
    }

internal fun userMessageForBillingResponseCode(responseCode: Int): ProBillingMessage =
    if (isRecoverableBillingResponseCode(responseCode)) {
        ProBillingMessage.CheckingPurchase
    } else {
        ProBillingMessage.PurchaseFailed
    }

internal fun billingRetryDelayMillis(
    attempt: Int,
    initialDelayMs: Long = INITIAL_OPERATION_RETRY_DELAY_MS,
    maxDelayMs: Long = MAX_OPERATION_RETRY_DELAY_MS,
): Long {
    require(initialDelayMs > 0L)
    require(maxDelayMs >= initialDelayMs)
    var delayMs = initialDelayMs
    repeat((attempt - 1).coerceAtLeast(0)) {
        delayMs = if (delayMs >= maxDelayMs / 2L) maxDelayMs else delayMs * 2L
    }
    return delayMs
}

private enum class BillingRetryOperation {
    Connection,
    ProductDetails,
    Purchases,
    PurchaseFlow,
}

class ProBillingRepository(context: Context) : PurchasesUpdatedListener {
    private val appContext = context.applicationContext
    private var proProductDetails: ProductDetails? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scheduledRetries = mutableMapOf<BillingRetryOperation, Runnable>()
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
    private var isDisposed = false
    private var purchaseFlowInProgress = false
    private var purchaseRecheckAttempts = 0
    private var purchaseSuccessEventId = 0L

    fun startConnection() {
        startConnection(retryAttempt = 0)
    }

    private fun startConnection(retryAttempt: Int) {
        if (isDisposed) return
        if (billingClient.isReady) {
            cancelScheduledRetry(BillingRetryOperation.Connection)
            queryProductDetails()
            queryPurchases(allowSuccessEvent = purchaseFlowInProgress)
            return
        }
        if (isConnectionInProgress) return

        isConnectionInProgress = true
        _state.value = _state.value.copy(
            status = ProBillingStatus.Loading,
            message = if (retryAttempt > 0) ProBillingMessage.CheckingPurchase else null,
        )
        billingClient.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (isDisposed) return
                    isConnectionInProgress = false
                    if (billingResult.responseCode == OK_RESPONSE_CODE) {
                        cancelScheduledRetry(BillingRetryOperation.Connection)
                        queryProductDetails()
                        queryPurchases(allowSuccessEvent = purchaseFlowInProgress)
                    } else {
                        handleConnectionFailure(billingResult, retryAttempt)
                    }
                }

                override fun onBillingServiceDisconnected() {
                    if (isDisposed) return
                    isConnectionInProgress = false
                    _state.value = _state.value.copy(
                        status = ProBillingStatus.Loading,
                        message = ProBillingMessage.CheckingPurchase,
                    )
                    scheduleOperationRetry(
                        operation = BillingRetryOperation.Connection,
                        attempt = 1,
                    ) {
                        startConnection(retryAttempt = 1)
                    }
                }
            },
        )
    }

    fun endConnection() {
        if (isDisposed) return
        isDisposed = true
        isConnectionInProgress = false
        mainHandler.removeCallbacksAndMessages(null)
        scheduledRetries.clear()
        billingClient.endConnection()
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
        cancelScheduledRetry(BillingRetryOperation.PurchaseFlow)
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
        if (isDisposed) return
        if (billingClient.isReady) {
            queryPurchases(allowSuccessEvent = purchaseFlowInProgress)
        } else {
            startConnection()
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (isDisposed) return
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
                purchaseRecheckAttempts = 0
                cancelScheduledRetry(BillingRetryOperation.PurchaseFlow)
                _state.value = _state.value.copy(message = null)
            }
            else -> handlePurchaseFlowResult(billingResult)
        }
    }

    private fun queryProductDetails(retryAttempt: Int = 0) {
        if (isDisposed) return
        if (!billingClient.isReady) {
            startConnection()
            return
        }
        cancelScheduledRetry(BillingRetryOperation.ProductDetails)
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRO_UNLOCK_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsResult ->
            if (isDisposed) return@queryProductDetailsAsync
            if (billingResult.responseCode != OK_RESPONSE_CODE) {
                handleProductDetailsFailure(billingResult, retryAttempt)
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

    private fun queryPurchases(
        allowSuccessEvent: Boolean,
        retryAttempt: Int = 0,
    ) {
        if (isDisposed) return
        if (!billingClient.isReady) {
            startConnection()
            return
        }
        cancelScheduledRetry(BillingRetryOperation.Purchases)
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (isDisposed) return@queryPurchasesAsync
            if (billingResult.responseCode == OK_RESPONSE_CODE) {
                handlePurchases(purchases, allowSuccessEvent = allowSuccessEvent)
            } else {
                handleQueryPurchasesFailure(
                    billingResult = billingResult,
                    allowSuccessEvent = allowSuccessEvent,
                    retryAttempt = retryAttempt,
                )
            }
        }
    }

    private fun handlePurchases(purchases: List<Purchase>, allowSuccessEvent: Boolean) {
        val wasUnlocked = _state.value.isProUnlocked
        val proPurchase = purchases.firstOrNull { purchase ->
            PRO_UNLOCK_PRODUCT_ID in purchase.products && purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        val proPurchasePending = purchases.any { purchase ->
            PRO_UNLOCK_PRODUCT_ID in purchase.products && purchase.purchaseState == Purchase.PurchaseState.PENDING
        }
        if (proPurchase != null) {
            acknowledgeIfNeeded(proPurchase)
            val shouldEmitSuccess = allowSuccessEvent && purchaseFlowInProgress && !wasUnlocked
            if (shouldEmitSuccess) {
                purchaseSuccessEventId += 1
            }
            purchaseFlowInProgress = false
            purchaseRecheckAttempts = 0
            cancelScheduledRetry(BillingRetryOperation.PurchaseFlow)
            _state.value = _state.value.copy(
                isProUnlocked = true,
                message = null,
                purchaseSuccessEventId = purchaseSuccessEventId,
            )
        } else if (proPurchasePending) {
            // Paid with a delayed method such as cash: Pro unlocks when Play reports the purchase as
            // completed. The purchase flow stays open so that completion still shows the success screen.
            purchaseRecheckAttempts = 0
            cancelScheduledRetry(BillingRetryOperation.PurchaseFlow)
            _state.value = _state.value.copy(
                isProUnlocked = false,
                message = ProBillingMessage.PurchasePending,
            )
        } else {
            if (allowSuccessEvent && purchaseFlowInProgress) {
                schedulePurchaseFlowRecheckOrFail()
            } else {
                _state.value = _state.value.copy(isProUnlocked = false)
            }
        }
    }

    private fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.isAcknowledged) {
            cancelProPurchaseAcknowledgement(appContext, purchase.purchaseToken)
            return
        }

        enqueueProPurchaseAcknowledgement(
            context = appContext,
            purchaseToken = purchase.purchaseToken,
        )
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { billingResult ->
            if (billingResult.responseCode == OK_RESPONSE_CODE) {
                cancelProPurchaseAcknowledgement(appContext, purchase.purchaseToken)
            } else {
                logBillingResult("Acknowledge Pro purchase failed; persistent retry remains queued", billingResult)
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
            purchaseRecheckAttempts = 0
            cancelScheduledRetry(BillingRetryOperation.PurchaseFlow)
            _state.value = _state.value.copy(message = userMessageForBillingResponseCode(billingResult.responseCode))
        }
    }

    private fun handleConnectionFailure(billingResult: BillingResult, retryAttempt: Int) {
        logBillingResult("Play Billing connection failed", billingResult)
        if (
            isRecoverableBillingResponseCode(billingResult.responseCode) &&
            retryAttempt < MAX_OPERATION_RETRY_ATTEMPTS
        ) {
            val nextAttempt = retryAttempt + 1
            _state.value = _state.value.copy(
                status = ProBillingStatus.Loading,
                message = ProBillingMessage.CheckingPurchase,
            )
            scheduleOperationRetry(BillingRetryOperation.Connection, nextAttempt) {
                startConnection(retryAttempt = nextAttempt)
            }
        } else {
            setBillingUnavailable()
        }
    }

    private fun handleProductDetailsFailure(billingResult: BillingResult, retryAttempt: Int) {
        logBillingResult("Query Pro product details failed", billingResult)
        if (
            isRecoverableBillingResponseCode(billingResult.responseCode) &&
            retryAttempt < MAX_OPERATION_RETRY_ATTEMPTS
        ) {
            val nextAttempt = retryAttempt + 1
            _state.value = _state.value.copy(
                status = ProBillingStatus.Loading,
                message = ProBillingMessage.CheckingPurchase,
            )
            scheduleOperationRetry(BillingRetryOperation.ProductDetails, nextAttempt) {
                queryProductDetails(retryAttempt = nextAttempt)
            }
        } else {
            setBillingUnavailable()
        }
    }

    private fun handleQueryPurchasesFailure(
        billingResult: BillingResult,
        allowSuccessEvent: Boolean,
        retryAttempt: Int,
    ) {
        logBillingResult("Query Pro purchases failed", billingResult)
        if (
            isRecoverableBillingResponseCode(billingResult.responseCode) &&
            retryAttempt < MAX_OPERATION_RETRY_ATTEMPTS
        ) {
            val nextAttempt = retryAttempt + 1
            _state.value = _state.value.copy(message = ProBillingMessage.CheckingPurchase)
            scheduleOperationRetry(BillingRetryOperation.Purchases, nextAttempt) {
                queryPurchases(
                    allowSuccessEvent = allowSuccessEvent,
                    retryAttempt = nextAttempt,
                )
            }
        } else {
            if (allowSuccessEvent && purchaseFlowInProgress) {
                purchaseFlowInProgress = false
                purchaseRecheckAttempts = 0
            }
            _state.value = _state.value.copy(
                message = if (_state.value.isProUnlocked) null else ProBillingMessage.PurchaseFailed,
            )
        }
    }

    private fun schedulePurchaseFlowRecheckOrFail() {
        if (!purchaseFlowInProgress) return
        if (purchaseRecheckAttempts >= MAX_PURCHASE_RECHECK_ATTEMPTS) {
            purchaseFlowInProgress = false
            purchaseRecheckAttempts = 0
            cancelScheduledRetry(BillingRetryOperation.PurchaseFlow)
            _state.value = _state.value.copy(message = ProBillingMessage.PurchaseFailed)
            return
        }

        purchaseRecheckAttempts += 1
        _state.value = _state.value.copy(message = ProBillingMessage.CheckingPurchase)
        scheduleRetry(
            operation = BillingRetryOperation.PurchaseFlow,
            delayMs = PURCHASE_RECHECK_DELAY_MS,
        ) {
            if (billingClient.isReady) {
                queryPurchases(allowSuccessEvent = true)
            } else {
                startConnection()
            }
        }
    }

    private fun scheduleOperationRetry(
        operation: BillingRetryOperation,
        attempt: Int,
        action: () -> Unit,
    ) {
        scheduleRetry(
            operation = operation,
            delayMs = billingRetryDelayMillis(attempt),
            action = action,
        )
    }

    private fun scheduleRetry(
        operation: BillingRetryOperation,
        delayMs: Long,
        action: () -> Unit,
    ) {
        if (isDisposed) return
        cancelScheduledRetry(operation)
        lateinit var runnable: Runnable
        runnable = Runnable {
            if (scheduledRetries[operation] !== runnable) return@Runnable
            scheduledRetries.remove(operation)
            if (!isDisposed) action()
        }
        scheduledRetries[operation] = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun cancelScheduledRetry(operation: BillingRetryOperation) {
        scheduledRetries.remove(operation)?.let(mainHandler::removeCallbacks)
    }

    private fun setBillingUnavailable() {
        _state.value = _state.value.copy(
            status = ProBillingStatus.Unavailable,
            message = if (_state.value.isProUnlocked) null else ProBillingMessage.PurchaseUnavailable,
        )
    }

    private fun logBillingResult(message: String, billingResult: BillingResult) {
        Log.w(
            TAG,
            "$message. responseCode=${billingResult.responseCode}, debugMessage=${billingResult.debugMessage}",
        )
    }
}
