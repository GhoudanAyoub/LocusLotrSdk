package com.locus.sdk.sampletrackingapp


import android.Manifest
import android.annotation.SuppressLint
import android.app.Fragment
import android.content.Context
import android.content.IntentSender
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.fragment.app.Fragment
import com.google.android.gms.common.api.ResolvableApiException
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.locus.sdk.sampletrackingapp.databinding.FragmentTrackinBinding
import io.reactivex.schedulers.Schedulers
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import sh.locus.lotr.model.Geofence
import sh.locus.lotr.model.Task
import sh.locus.lotr.model.TaskStatusRequest
import sh.locus.lotr.model.VisitStatusRequest
import sh.locus.lotr.sdk.*
import sh.locus.lotr.sdk.exception.LotrSdkError
import sh.locus.lotr.sdk.logging.LotrSdkEventType
import java.security.AccessController.getContext
import java.util.*

class TrackinFragment : Fragment(), TrackingListener {

    private var _binding: FragmentTrackinBinding? = null
    private val binding get() = _binding!!

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var logger: Logger
    private lateinit var logoutListener: LogoutListener

    private lateinit var toggleButton: Button
    private lateinit var start: Button

    private var isTracking: Boolean = false

    @SuppressLint("CheckResult")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val context = context

        context ?: return null

        _binding = FragmentTrackinBinding.inflate(inflater, container, false)
        val view = binding.root

        sharedPreferences = context.getSharedPreferences("sample-app-prefs", Context.MODE_PRIVATE)
        isTracking = sharedPreferences.getBoolean(KEY_IS_TRACKING, false)

        logger = LoggerFactory.getLogger(this::class.java)

        toggleButton = view.findViewById(R.id.bt_start_stop)

        start = view.findViewById(R.id.bt_login)
        setToggleButtonText()

        start.setOnClickListener {
            beginAsyncUpdateInsertion()
        }
        toggleButton.setOnClickListener {
            toggleTracker()
        }

        view.findViewById<Button>(R.id.bt_logout).setOnClickListener {
            LocusLotrSdk.logout(false, true, object : LogoutStatusListener {
                override fun onFailure() {
                    Toast.makeText(context, R.string.error_logout_fail, Toast.LENGTH_SHORT).show()
                }

                override fun onSuccess() {
                    logoutListener.onLogout()
                }
            })
        }

        val lastLocationText = LocusLotrSdk.getLastUploadedLocation()?.let {
            getString(R.string.last_known_location, it.toIndentedString())
        } ?: "Last known location not available"
        view.findViewById<TextView>(R.id.tv_location).text = lastLocationText

        LocusLotrSdk.getSdkEventsObservable()
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.io())
            .subscribe({ lotrSdkEvent ->

                when (lotrSdkEvent.type) {

                    LotrSdkEventType.PASSWORD_EXPIRED -> Toast.makeText(
                        getContext(),
                        lotrSdkEvent.message,
                        Toast.LENGTH_SHORT
                    ).show()

                    LotrSdkEventType.AUTH_FAILURE -> Toast.makeText(
                        getContext(),
                        lotrSdkEvent.message,
                        Toast.LENGTH_SHORT
                    ).show()

                    else -> Log.v("TAG", "${lotrSdkEvent.type} ${lotrSdkEvent.message}")
                }
            }, { throwable ->
                throwable.printStackTrace()
            })

        return view
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        logoutListener = context as LogoutListener
    }

    private fun setToggleButtonText() {
        toggleButton.text = if (isTracking) {
            "STOP"
        } else {
            "START"
        }
    }

    private fun toggleTracker() {

        isTracking = !isTracking
        setToggleButtonText()


        if (isTracking) {

            val requestParams = TrackingRequestParams.Builder()
                .setNotificationTitleText(R.string.notification_text) // Customize the notification text
                //.setNotificationChannelId("") // Customize channel Id if required
                //.setNotificationChannelName("") // Customize channel name if required
                .build()
            try {
                LocusLotrSdk.startTracking(this, requestParams)
            } catch (e: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Dexter.withContext(requireContext())
                        .withPermissions(
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.ACTIVITY_RECOGNITION,
                            Manifest.permission.READ_PHONE_STATE,
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION
                        ).withListener(object : MultiplePermissionsListener {
                            override fun onPermissionsChecked(report: MultiplePermissionsReport) { /* ... */
                            }

                            override fun onPermissionRationaleShouldBeShown(
                                permissions: List<PermissionRequest>,
                                token: PermissionToken
                            ) {

                            }
                        }).check()
                }
            }

            binding.tvErrorMessage.text = ""
            binding.tvErrorTime.text = ""
        } else {
            LocusLotrSdk.stopTracking()
        }
    }

    override fun onLocationUpdated(location: LocusLocation) {
        val locationString = location.toIndentedString()
        binding.tvLocation.text = locationString
        logger.info(locationString.replace('\n', ' '))
    }

    override fun onLocationError(lotrSdkError: LotrSdkError) {

        if (lotrSdkError.code == LotrSdkError.Code.RESOLVABLE_API_EXCEPTION) {
            try {
                val resolvableApiException = lotrSdkError.throwable as ResolvableApiException
                // Show the dialog by calling startResolutionForResult() and check the result in onActivityResult().
                resolvableApiException.startResolutionForResult(requireActivity(), 1001)
            } catch (sendEx: IntentSender.SendIntentException) {
                // Ignore the error.
            }
            return
        }

        binding.tvErrorMessage.text = lotrSdkError.message
        binding.tvErrorTime.text = getString(R.string.error_time, Date().toString())
        lotrSdkError.throwable?.let {
            logger.error("Error", it)
        }
    }

    override fun onLocationUploaded(location: LocusLocation) {
        // Nothing to do
    }

    interface LogoutListener {
        fun onLogout()
    }

    companion object {
        const val KEY_IS_TRACKING = "KEY_IS_TRACKING"
    }

    fun beginAsyncUpdateInsertion() {
       val status : TaskStatusRequest.StatusEnum =  when(binding.etStatus.selectedItem.toString()){
            "RECEIVED" -> {
               TaskStatusRequest.StatusEnum.RECEIVED
           }
            "WAITING" -> {
               TaskStatusRequest.StatusEnum.WAITING
           }
            "ACCEPTED" -> {
               TaskStatusRequest.StatusEnum.ACCEPTED
           }
            "STARTED" -> {
               TaskStatusRequest.StatusEnum.STARTED
           }
            "COMPLETED" -> {
               TaskStatusRequest.StatusEnum.COMPLETED
           }
            "CANCELLED" -> {
               TaskStatusRequest.StatusEnum.CANCELLED
           }
           else -> {
               TaskStatusRequest.StatusEnum.ERROR}
       }
       val task =  TaskStatusUpdateParams(binding.etTask.text.toString(),status,null)
        LocusLotrSdk.beginAsyncUpdateInsertion()
            .addTaskStatusUpdate(task)
            .commit()
        Toast.makeText(requireContext(), "Sended Successfully ", Toast.LENGTH_SHORT).show()
        binding.etTask.setText("")
    }

}
