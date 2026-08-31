/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.examtube

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import org.json.JSONObject
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R

class AccessGateActivity : AppCompatActivity(), PaymentResultWithDataListener {
    private lateinit var statusText: TextView
    private lateinit var signInButton: Button
    private lateinit var subscribeButton: Button

    private var googleAccount: GoogleSignInAccount? = null
    private var pendingSubscriptionId: String? = null

    private val signInLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val signInTask = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            onGoogleSignIn(signInTask.getResult(ApiException::class.java))
        } catch (exception: ApiException) {
            val reason = if (exception.statusCode == CommonStatusCodes.CANCELED) {
                "Google sign-in was cancelled."
            } else {
                "Google sign-in failed. Check the OAuth client configuration."
            }
            showError(reason)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(buildContent())
        Checkout.preload(applicationContext)
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (AccessSessionStore(this).hasVerifiedAccess() && account?.idToken != null) {
            verifyCachedAccess(account)
        } else {
            if (AccessSessionStore(this).hasVerifiedAccess()) {
                AccessSessionStore(this).clear()
            }
            updateSignedInState(account)
        }
    }

    private fun buildContent(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(48, 64, 48, 48)
        }

        val logo = TextView(this).apply {
            text = "E>"
            textSize = 44f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(91, 33, 182))
            contentDescription = getString(R.string.app_name)
        }
        content.addView(logo, LinearLayout.LayoutParams(180, 180))

        val title = TextView(this).apply {
            text = getString(R.string.examtube_gate_title)
            textSize = 28f
            gravity = Gravity.CENTER
            setPadding(0, 36, 0, 12)
        }
        content.addView(title)

        val message = TextView(this).apply {
            text = getString(R.string.examtube_gate_message)
            textSize = 16f
            gravity = Gravity.CENTER
        }
        content.addView(message)

        statusText = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 20)
        }
        content.addView(statusText)

        signInButton = Button(this).apply {
            text = getString(R.string.examtube_sign_in_with_google)
            setOnClickListener { startGoogleSignIn() }
        }
        content.addView(
            signInButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        subscribeButton = Button(this).apply {
            text = getString(R.string.examtube_subscribe)
            isEnabled = false
            setOnClickListener { createSubscription() }
        }
        content.addView(
            subscribeButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val scroll = ScrollView(this)
        scroll.addView(content)
        return scroll
    }

    private fun startGoogleSignIn() {
        val clientId = BuildConfig.EXAMTUBE_GOOGLE_WEB_CLIENT_ID
        if (clientId.isBlank()) {
            showError("Google sign-in is not configured. Set examtubeGoogleWebClientId.")
            return
        }

        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(clientId)
            .requestEmail()
            .build()
        signInLauncher.launch(GoogleSignIn.getClient(this, options).signInIntent)
    }

    private fun onGoogleSignIn(account: GoogleSignInAccount) {
        googleAccount = account
        updateSignedInState(account)
    }

    private fun updateSignedInState(account: GoogleSignInAccount?) {
        googleAccount = account
        if (account == null) {
            statusText.text = getString(R.string.examtube_sign_in_required)
            subscribeButton.isEnabled = false
            return
        }

        statusText.text = getString(
            R.string.examtube_signed_in_as,
            account.email ?: getString(R.string.examtube_google_account)
        )
        subscribeButton.isEnabled = true
    }

    private fun verifyCachedAccess(account: GoogleSignInAccount) {
        val idToken = account.idToken
        val subscriptionId = AccessSessionStore(this).subscriptionId()
        if (idToken.isNullOrBlank() || subscriptionId.isNullOrBlank()) {
            AccessSessionStore(this).clear()
            updateSignedInState(account)
            return
        }

        setLoading(true, getString(R.string.examtube_checking_subscription))
        RazorpaySubscriptionClient.checkSubscription(idToken, subscriptionId) { result ->
            result.onSuccess { payload ->
                if (payload.optBoolean("active", false)) {
                    openMainApp()
                } else {
                    AccessSessionStore(this).clear()
                    updateSignedInState(account)
                    showError(getString(R.string.examtube_subscription_not_active))
                }
            }.onFailure {
                AccessSessionStore(this).clear()
                updateSignedInState(account)
                showError(it.message ?: getString(R.string.examtube_billing_unavailable))
            }
        }
    }

    private fun createSubscription() {
        val account = googleAccount
        val idToken = account?.idToken
        if (account == null || idToken.isNullOrBlank()) {
            showError(getString(R.string.examtube_sign_in_required))
            return
        }
        if (BuildConfig.EXAMTUBE_RAZORPAY_KEY_ID.isBlank()) {
            showError("Razorpay is not configured. Set examtubeRazorpayKeyId.")
            return
        }

        setLoading(true, getString(R.string.examtube_creating_subscription))
        RazorpaySubscriptionClient.createSubscription(idToken) { result ->
            result.onSuccess { payload ->
                pendingSubscriptionId = payload.optString("subscriptionId")
                if (pendingSubscriptionId.isNullOrBlank()) {
                    showError("Razorpay did not return a subscription ID.")
                    setLoading(false)
                } else {
                    openRazorpayCheckout(pendingSubscriptionId!!)
                }
            }.onFailure {
                showError(it.message ?: getString(R.string.examtube_billing_unavailable))
                setLoading(false)
            }
        }
    }

    private fun openRazorpayCheckout(subscriptionId: String) {
        val options = JSONObject()
            .put("key", BuildConfig.EXAMTUBE_RAZORPAY_KEY_ID)
            .put("name", getString(R.string.app_name))
            .put("description", getString(R.string.examtube_subscription_description))
            .put("subscription_id", subscriptionId)
            .put("theme", JSONObject().put("color", "#5B21B6"))

        try {
            Checkout().open(this, options)
        } catch (exception: Exception) {
            showError(exception.message ?: getString(R.string.examtube_checkout_failed))
            setLoading(false)
        }
    }

    override fun onPaymentSuccess(paymentId: String?, paymentData: PaymentData?) {
        val account = googleAccount
        val idToken = account?.idToken
        val subscriptionId = pendingSubscriptionId
        val signature = paymentData?.signature
        if (idToken.isNullOrBlank() || subscriptionId.isNullOrBlank() || signature.isNullOrBlank()) {
            showError(getString(R.string.examtube_verification_failed))
            setLoading(false)
            return
        }

        setLoading(true, getString(R.string.examtube_verifying_payment))
        RazorpaySubscriptionClient.verifySubscription(
            idToken = idToken,
            paymentId = paymentId.orEmpty(),
            subscriptionId = subscriptionId,
            signature = signature
        ) { result ->
            result.onSuccess { payload ->
                if (payload.optBoolean("active", false)) {
                    AccessSessionStore(this).saveVerifiedAccess(
                        googleUserId = account.id.orEmpty(),
                        subscriptionId = subscriptionId
                    )
                    openMainApp()
                } else {
                    showError(getString(R.string.examtube_subscription_not_active))
                    setLoading(false)
                }
            }.onFailure {
                showError(it.message ?: getString(R.string.examtube_verification_failed))
                setLoading(false)
            }
        }
    }

    override fun onPaymentError(code: Int, response: String?, paymentData: PaymentData?) {
        showError(response ?: getString(R.string.examtube_checkout_failed))
        setLoading(false)
    }

    private fun setLoading(loading: Boolean, message: String? = null) {
        signInButton.isEnabled = !loading
        subscribeButton.isEnabled = !loading && googleAccount != null
        if (message != null) {
            statusText.text = message
        }
    }

    private fun showError(message: String) {
        statusText.text = message
    }

    private fun openMainApp() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
