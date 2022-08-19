package com.locus.sdk.sampletrackingapp


import android.Manifest
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.locus.sdk.sampletrackingapp.databinding.FragmentLoginBinding
import sh.locus.lotr.sdk.LocusLotrSdk
import sh.locus.lotr.sdk.LotrSdkReadyCallback
import sh.locus.lotr.sdk.auth.ClientAuthParams
import sh.locus.lotr.sdk.exception.LotrSdkError

class LoginFragment : Fragment() {

    private lateinit var loginListener: LoginListener

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {

        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        val view = binding.root

        Dexter.withContext(requireContext())
            .withPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_PHONE_STATE
            ).withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport) { /* ... */
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissions: List<PermissionRequest>,
                    token: PermissionToken
                ) { /* ... */
                }
            }).check()

        binding.etClient.setText("chari-devo")
        binding.etUser.setText("LIV-897133")
        binding.etPassword.setText("d19fad68-8cd7-469c-9e6c-9f84f7a56923")
        binding.btLogin.setOnClickListener {
            attemptLogin()
        }

        binding.etPassword.setOnEditorActionListener { textView, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {

                // Hide keypad
                val inputManager = textView.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                inputManager.hideSoftInputFromWindow(textView.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)

                attemptLogin()
                return@setOnEditorActionListener true
            }
            false
        }

        return view
    }

    private fun attemptLogin() {
        val clientId = binding.etClient.text?.toString()?.trim() ?: ""
        val userId = binding.etUser.text?.toString()?.trim() ?: ""
        val password = binding.etPassword.text?.toString()?.trim() ?: ""

        if (clientId.isEmpty() || userId.isEmpty() || password.isEmpty()) {
            Toast.makeText(context, getString(R.string.error_blank_in_login), Toast.LENGTH_LONG).show()
            return
        }

        login(clientId, userId, password)
        binding.btLogin.isEnabled = false
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        loginListener = context as LoginListener
    }

    private fun login(clientId: String, userId: String, password: String) {

        val context = context

        context ?: throw RuntimeException("Context null when handling login click")

        val sdkReadyCallback = object : LotrSdkReadyCallback {
            override fun onAuthenticated() {
                loginListener.onLogin()
            }

            override fun onError(error: LotrSdkError) {
                binding.btLogin.isEnabled = true
                Toast.makeText(context, error.message, Toast.LENGTH_LONG).show()
                error.throwable?.printStackTrace()
            }
        }

        // App can use either user's or client's login based on BuildConfig
        LocusLotrSdk.init(context, ClientAuthParams(clientId, userId, password), true, sdkReadyCallback)
    }

    interface LoginListener {
        fun onLogin()
    }
}
