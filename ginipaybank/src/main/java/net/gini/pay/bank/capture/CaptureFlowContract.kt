package net.gini.pay.bank.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContract
import net.gini.android.capture.GiniCaptureError
import net.gini.android.capture.camera.CameraActivity
import net.gini.android.capture.internal.util.FileImportValidator
import net.gini.android.capture.network.model.GiniCaptureCompoundExtraction
import net.gini.android.capture.network.model.GiniCaptureReturnReason
import net.gini.android.capture.network.model.GiniCaptureSpecificExtraction
import net.gini.pay.bank.GiniBank
import net.gini.pay.bank.capture.CaptureFlowImportContract.Companion.EXTRA_OUT_ERROR_MESSAGE
import net.gini.pay.bank.capture.CaptureFlowImportContract.Companion.EXTRA_OUT_IMPORT_ERROR

/**
 * Activity Result Api custom contract for starting the capture flow.
 *
 * It doesn't take any input.
 * It returns a [CaptureResult]
 */
class CaptureFlowContract : ActivityResultContract<Unit, CaptureResult>() {
    override fun createIntent(context: Context, input: Unit) = Intent(
        context, CaptureFlowActivity::class.java
    )

    override fun parseResult(resultCode: Int, result: Intent?): CaptureResult {
        return internalParseResult(resultCode, result)
    }
}

/**
 * Activity Result Api custom contract for starting the capture flow for the case in
 * which the a document was shared from another app.
 *
 * The input is generated by Gini Pay Bank SDK internally when calling [GiniBank.startCaptureFlowForIntent]
 *
 * It returns a [CaptureResult] same as [CaptureFlowContract]
 */
class CaptureFlowImportContract : ActivityResultContract<CaptureImportInput, CaptureResult>() {
    override fun createIntent(context: Context, input: CaptureImportInput) = when (input) {
        is CaptureImportInput.Forward -> {
            Intent(context, CaptureFlowActivity::class.java).apply {
                putExtra(EXTRA_FORWARD_INTENT, input.intent)
            }
        }
        is CaptureImportInput.Error -> {
            Intent(context, CaptureFlowActivity::class.java).apply {
                putExtra(EXTRA_ERROR, input.error?.name)
                putExtra(EXTRA_ERROR_MESSAGE, input.message)
            }
        }
        else -> Intent(context, CaptureFlowActivity::class.java)
    }

    override fun parseResult(resultCode: Int, result: Intent?): CaptureResult {
        return internalParseResult(resultCode, result)
    }

    internal companion object {
        private const val EXTRA_FORWARD_INTENT = "EXTRA_FORWARD_INTENT"
        private const val EXTRA_ERROR = "EXTRA_ERROR"
        private const val EXTRA_ERROR_MESSAGE = "EXTRA_ERROR_MESSAGE"
        internal const val EXTRA_OUT_IMPORT_ERROR = "EXTRA_OUT_IMPORT_ERROR"
        internal const val EXTRA_OUT_ERROR_MESSAGE = "EXTRA_OUT_IMPORT_ERROR"
    }

    internal interface Contract {
        fun Intent.getCaptureImportInput(): CaptureImportInput {
            val forwardIntent = getParcelableExtra<Intent?>(EXTRA_FORWARD_INTENT)
            val error = getStringExtra(EXTRA_ERROR)?.let {FileImportValidator.Error.valueOf(it)}
            val errorMessage = getStringExtra(EXTRA_ERROR_MESSAGE)
            return when {
                forwardIntent != null -> CaptureImportInput.Forward(forwardIntent)
                error == null && errorMessage == null -> CaptureImportInput.Default
                else -> CaptureImportInput.Error(error, errorMessage)
            }
        }

        fun Intent.setImportResultError(error: FileImportValidator.Error?, message: String?) = Intent().apply {
            putExtra(EXTRA_OUT_IMPORT_ERROR, error)
            putExtra(EXTRA_OUT_ERROR_MESSAGE, message)
        }

        fun Intent.setCaptureResultError(error: GiniCaptureError) = Intent().apply {
            putExtra(CameraActivity.EXTRA_OUT_ERROR, error)
        }
    }
}

/**
 * Input used when a document was shared from another app. It will be created internally.
 */
sealed class CaptureImportInput {
    data class Forward(val intent: Intent) : CaptureImportInput()
    data class Error(val error: FileImportValidator.Error? = null, val message: String? = null) : CaptureImportInput()
    object Default : CaptureImportInput()
}

internal class CaptureImportContract : ActivityResultContract<Intent, CaptureResult>() {
    override fun createIntent(context: Context, input: Intent) = input

    override fun parseResult(resultCode: Int, intent: Intent?): CaptureResult {
        return internalParseResult(resultCode, intent)
    }
}

internal fun internalParseResult(resultCode: Int, result: Intent?): CaptureResult {
    if (resultCode != Activity.RESULT_OK) {
        val captureError: GiniCaptureError? = result?.getParcelableExtra(CameraActivity.EXTRA_OUT_ERROR)
        val importError: FileImportValidator.Error? = result?.getParcelableExtra(EXTRA_OUT_IMPORT_ERROR)
        val importErrorMessage: String? = result?.getStringExtra(EXTRA_OUT_ERROR_MESSAGE)
        return if (captureError != null) {
            CaptureResult.Error(ResultError.Capture(captureError))
        } else if (importError != null || importErrorMessage != null) {
            CaptureResult.Error(ResultError.FileImport(importError, importErrorMessage))
        } else {
            CaptureResult.Cancel
        }
    }
    val specificExtractionsBundle: Bundle? = result?.getBundleExtra(CameraActivity.EXTRA_OUT_EXTRACTIONS)
    val compoundExtractionsBundle: Bundle? = result?.getBundleExtra(CameraActivity.EXTRA_OUT_COMPOUND_EXTRACTIONS)
    val returnReasons: List<GiniCaptureReturnReason>? = result?.getParcelableArrayListExtra(CameraActivity.EXTRA_OUT_COMPOUND_EXTRACTIONS)
    return if (specificExtractionsBundle == null ||
        !pay5ExtractionsAvailable(specificExtractionsBundle) &&
        !epsPaymentAvailable(specificExtractionsBundle)
    ) {
        CaptureResult.Empty
    } else {
        CaptureResult.Success(
            specificExtractions = specificExtractionsBundle.keySet().asSequence()
            .mapNotNull { name -> specificExtractionsBundle.getParcelable<GiniCaptureSpecificExtraction>(name)?.let { name to it } }
            .associate { it },
            compoundExtractions = compoundExtractionsBundle?.keySet()?.asSequence()
                ?.mapNotNull { name -> compoundExtractionsBundle.getParcelable<GiniCaptureCompoundExtraction>(name)?.let { name to it } }
                ?.associate { it } ?: emptyMap(),
            returnReasons = returnReasons ?: emptyList()
        )
    }
}

private fun isPay5Extraction(extractionName: String): Boolean {
    return extractionName == "amountToPay" ||
            extractionName == "bic" ||
            extractionName == "iban" ||
            extractionName == "paymentReference" ||
            extractionName == "paymentRecipient"
}

private fun pay5ExtractionsAvailable(extractionsBundle: Bundle) =
    extractionsBundle.keySet().any { key -> isPay5Extraction(key) }

private fun epsPaymentAvailable(extractionsBundle: Bundle) =
    extractionsBundle.keySet().contains("epsPaymentQRCodeUrl")
