package com.exemple.multisms

import android.Manifest
import android.app.Activity.RESULT_OK
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat

class SmsSendingService : Service() {
    private var selectedSimId: Int = -1
    private var contacts: List<Contact>? = null
    private var messageTemplate: String? = null
    private var currentContactIndex = 0
    private var smsSentReceiver: BroadcastReceiver? = null
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "SmsSendingService"
        private const val CHANNEL_ID = "sms_sending_channel"
        private const val SMS_SENT_ACTION = "comAction"
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.e(TAG, "CALLING onStartCommand OF SMS SENDING SERVICE.")
        contacts = intent?.getParcelableArrayListExtra("contacts", Contact::class.java)
        messageTemplate = getSharedPreferences("sms_prefs", MODE_PRIVATE).getString(
            "messageTemplate", "BONJOUR CHER PARTENAIRE \${value1}!,LE SERVICE DES " +
                    "IMPÔTS (OTR-DOFR-KARA VOUS INVITE A PROCEDER AU PAIEMENT DU TROISIEME ACOMPTE 2024" +
                    "D'UN MONTANT DE \${value2}! AVANT LE 31 JUILLET SOUS PEINE DE PENALITES." +
                    "VOUS ÊTES EGALEMENT CONVIES A REGULARISER LES IMPOSITIONS ANTERIEURS " +
                    "ET LES REDRESSEMENTS D'UN MONTANT TOTAL DE  \${value3}! POUR PLUS DE DETAILS" +
                    "VEUILLEZ PASSER DANS NOS LOCAUX" +
                    "MERCI POUR LA COLLABORATION "
        )
        Log.e(TAG, "selectedSimId before  ${selectedSimId}")
        selectedSimId = getSharedPreferences("sms_prefs", MODE_PRIVATE).getInt("selectedId", -1)
        Log.e(TAG, "selectedSimId after  ${selectedSimId}")
        Log.e(TAG, messageTemplate.let { it.toString() })

        if (contacts.isNullOrEmpty() || messageTemplate.isNullOrEmpty() || selectedSimId == -1) {
            Log.e(TAG, contacts.let { it.toString() })
            Log.e(TAG, messageTemplate.let { it.toString() })
            Log.e(TAG, selectedSimId.toString())
            Log.e(TAG, "Missing required data, stopping service.")
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(1, buildNotification())
        registerSmsSentReceiver()
        sendNextSms()
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent =
            PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Sending SMS Messages")
            .setContentText("Sending messages in progress...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun sendSmsUsingSim(
        context: Context,
        phoneNumber: String,
        message: String,
        subscriptionId: Int
    ) {
        val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)

        val subscriptionInfo = if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            return
        } else {
            subscriptionManager.getActiveSubscriptionInfo(subscriptionId)
        }


        if (subscriptionInfo != null) {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
                    .createForSubscriptionId(subscriptionId)
            } else {
                SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
            }
            val parts = smsManager.divideMessage(message)
            val sentIntents = ArrayList<PendingIntent>()

            val sentIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(SMS_SENT_ACTION),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            for (i in parts.indices) {
                sentIntents.add(sentIntent)
            }

            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, sentIntents, null)
        } else {
            Log.e(TAG, "No active subscription found for the given ID.")
            Toast.makeText(
                context,
                "Failed to send SMS: Invalid subscription ID",
                Toast.LENGTH_SHORT
            ).show()
        }
    }


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun sendNextSms() {
        if (currentContactIndex < (contacts?.size ?: 0)) {
            val contact = contacts?.get(currentContactIndex)
            val message = contact?.let {
                var msg = messageTemplate ?: ""
                it.customValues.forEachIndexed { index, value ->
                    msg = msg.replace("\${value${index + 1}}", value)
                }
                msg
            }

            contact?.phoneNumber?.let { phoneNumber ->

                    sendSmsUsingSim(
                        this, phoneNumber,
                        message ?: ("BONJOUR CHER PARTENAIRE \${value1}!,LE SERVICE DES " +
                                "IMPÔTS (OTR-DOFR-KARA VOUS INVITE A PROCEDER AU PAIEMENT DU TROISIEME ACOMPTE 2024" +
                                "D'UN MONTANT DE \${value2}! AVANT LE 31 JUILLET SOUS PEINE DE PENALITES." +
                                "VOUS ÊTES EGALEMENT CONVIES A REGULARISER LES IMPOSITIONS ANTERIEURS " +
                                "ET LES REDRESSEMENTS D'UN MONTANT TOTAL DE  \${value3}! POUR PLUS DE DETAILS" +
                                "VEUILLEZ PASSER DANS NOS LOCAUX" +
                                "MERCI POUR LA COLLABORATION "), selectedSimId
                    )

                currentContactIndex++
                Toast.makeText(this, "Message number $currentContactIndex sent.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.d(TAG, "All messages sent, stopping service.")
            stopSelf()
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun registerSmsSentReceiver() {
        smsSentReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == SMS_SENT_ACTION) {
                    Log.d(TAG, "SMS sent action received with resultCode: $resultCode")
                    when (resultCode) {
                        RESULT_OK -> {
                            handler.post {
                                Toast.makeText(
                                    this@SmsSendingService,
                                    "SMS sent successfully",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }

                        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                            handler.post {
                                Toast.makeText(
                                    this@SmsSendingService,
                                    "Generic failure occurred",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }

                        SmsManager.RESULT_ERROR_NO_SERVICE -> {
                            handler.post {
                                Toast.makeText(
                                    this@SmsSendingService,
                                    "No service available",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }

                        SmsManager.RESULT_ERROR_NULL_PDU -> {
                            handler.post {
                                Toast.makeText(
                                    this@SmsSendingService,
                                    "Null PDU error",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }

                        SmsManager.RESULT_ERROR_RADIO_OFF -> {
                            handler.post {
                                Toast.makeText(
                                    this@SmsSendingService,
                                    "Radio is off",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                    Log.d(TAG, "Proceeding to send the next SMS after a delay.")
                    sendNextSms()
                }
            }
        }
        val intentFilter = IntentFilter(SMS_SENT_ACTION)
        registerReceiver(smsSentReceiver, intentFilter, Context.RECEIVER_EXPORTED)
        Log.d(TAG, "SMS sent receiver registered")
    }

    override fun onDestroy() {
        super.onDestroy()
        smsSentReceiver?.let {
            unregisterReceiver(it)
        }
        Log.d(TAG, "SMS sent receiver unregistered")
    }
}
