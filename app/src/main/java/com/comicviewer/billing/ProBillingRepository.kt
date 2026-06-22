package com.comicviewer.billing

import android.app.Activity
import android.content.Context
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

sealed interface ProBillingStatus {
    data object Loading : ProBillingStatus
    data object Available : ProBillingStatus
    data object Unavailable : ProBillingStatus
}

data class ProBillingState(
    val status: ProBillingStatus = ProBillingStatus.Loading,
    val isProUnlocked: Boolean = false,
    val productTitle: String? = null,
    val formattedPrice: String? = null,
    val message: String? = null,
)

class ProBillingRepository(context: Context) : PurchasesUpdatedListener {
    private val appContext = context.applicationContext
    private var proProductDetails: ProductDetails? = null
    private val billingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build(),
        )
        .build()

    private val _state = MutableStateFlow(ProBillingState())
    val state: StateFlow<ProBillingState> = _state.asStateFlow()
    private var isConnectionInProgress = false

    fun startConnection() {
        if (billingClient.isReady) {
            queryProductDetails()
            queryPurchases()
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
                        queryPurchases()
                    } else {
                        setUnavailable(billingResult.debugMessage)
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
        if (billingClient.isReady) billingClient.endConnection()
    }

    fun launchPurchase(activity: Activity) {
        val productDetails = proProductDetails
        if (!billingClient.isReady || productDetails == null) {
            _state.value = _state.value.copy(
                status = ProBillingStatus.Unavailable,
                message = null,
            )
            return
        }

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()
        val result = billingClient.launchBillingFlow(activity, flowParams)
        if (result.responseCode != OK_RESPONSE_CODE) {
            _state.value = _state.value.copy(message = result.debugMessage)
        }
    }

    fun restorePurchases() {
        if (billingClient.isReady) {
            queryPurchases()
        } else {
            startConnection()
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            OK_RESPONSE_CODE -> handlePurchases(purchases.orEmpty())
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> _state.value = _state.value.copy(message = billingResult.debugMessage)
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
                setUnavailable(billingResult.debugMessage)
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
                _state.value.copy(status = ProBillingStatus.Unavailable)
            }
        }
    }

    private fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == OK_RESPONSE_CODE) {
                handlePurchases(purchases)
            } else {
                _state.value = _state.value.copy(message = billingResult.debugMessage)
            }
        }
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        val proPurchase = purchases.firstOrNull { purchase ->
            PRO_UNLOCK_PRODUCT_ID in purchase.products && purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        if (proPurchase != null) {
            acknowledgeIfNeeded(proPurchase)
            _state.value = _state.value.copy(isProUnlocked = true, message = null)
        } else {
            _state.value = _state.value.copy(isProUnlocked = false)
        }
    }

    private fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.isAcknowledged) return

        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { billingResult ->
            if (billingResult.responseCode != OK_RESPONSE_CODE) {
                _state.value = _state.value.copy(message = billingResult.debugMessage)
            }
        }
    }

    private fun setUnavailable(message: String?) {
        _state.value = _state.value.copy(
            status = ProBillingStatus.Unavailable,
            message = message?.takeIf { it.isNotBlank() },
        )
    }
}
