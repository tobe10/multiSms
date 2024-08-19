package com.exemple.multisms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import android.widget.Toast

class DeliveryReportReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "SMS_SENT" -> {
                if (resultCode == Activity.RESULT_OK) {
                    // SMS sent successfully
                    Toast.makeText(context, "SMS sent", Toast.LENGTH_SHORT).show()
                } else {
                    // SMS sending failed
                    Toast.makeText(context, "SMS sending failed", Toast.LENGTH_SHORT).show()
                }
            }
            "SMS_DELIVERED" -> {
                if (resultCode == Activity.RESULT_OK) {
                    // SMS delivered
                    Toast.makeText(context, "SMS delivered", Toast.LENGTH_SHORT).show()
                } else {
                    // SMS delivery failed
                    Toast.makeText(context, "SMS delivery failed", Toast.LENGTH_SHORT).show()
                }
            }

             "SMS_RECEIVED"-> {
                val bundle = intent.extras
                val messages: Array<SmsMessage?>?
                 var strMessage = ""

                if (bundle != null) {
                    try {
                        val pdus = bundle.getSerializable("pdus") as Array<*>
                        val format = bundle.getString("format") // Get the format
                        messages = arrayOfNulls(pdus.size)
                        for (i in pdus.indices) {

                            messages[i] = SmsMessage.createFromPdu(pdus[i] as ByteArray, format)
                            strMessage += "SMS from ${messages[i]?.originatingAddress} : ${messages[i]?.messageBody}\n"
                        }
                        Toast.makeText(context, strMessage, Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        }
    }

