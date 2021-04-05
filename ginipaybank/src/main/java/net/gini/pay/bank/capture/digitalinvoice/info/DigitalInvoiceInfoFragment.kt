package net.gini.pay.bank.capture.digitalinvoice.info

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.transition.TransitionInflater
import net.gini.pay.bank.capture.digitalinvoice.onboarding.DigitalInvoiceOnboardingFragmentListener
import net.gini.pay.bank.R
import net.gini.pay.bank.capture.util.autoCleared
import net.gini.pay.bank.databinding.GpbFragmentDigitalInvoiceInfoBinding
import net.gini.pay.bank.databinding.GpbFragmentDigitalInvoiceOnboardingBinding
import net.gini.pay.bank.databinding.GpbFragmentLineItemDetailsBinding

/**
 * Created by Alpar Szotyori on 14.10.2020.
 *
 * Copyright (c) 2020 Gini GmbH.
 */

/**
 * Internal use only.
 *
 * @suppress
 */
internal class DigitalInvoiceInfoFragment : Fragment() {

    companion object {
        @JvmStatic
        fun createInstance() = DigitalInvoiceInfoFragment()
    }

    private var binding by autoCleared<GpbFragmentDigitalInvoiceInfoBinding>()
    var listener: DigitalInvoiceInfoFragmentListener? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = GpbFragmentDigitalInvoiceInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * Internal use only.
     *
     * @suppress
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = TransitionInflater.from(context).inflateTransition(R.transition.fade)
        exitTransition = enterTransition
    }

    /**
     * Internal use only.
     *
     * @suppress
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setInputHandlers()
    }

    /**
     * Internal use only.
     *
     * @suppress
     */
    override fun onDestroy() {
        listener = null
        super.onDestroy()
    }

    private fun setInputHandlers() {
        binding.gpbCloseButton.setOnClickListener {
            listener?.onCloseInfo()
        }
    }
}