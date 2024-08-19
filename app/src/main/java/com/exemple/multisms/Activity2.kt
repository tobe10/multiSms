package com.exemple.multisms

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import com.exemple.multisms.R

class Activity2 : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences("com.example.sms_prefs", Context.MODE_PRIVATE)

        // Check for permission to access subscription info
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            val subscriptionManager = getSystemService(SubscriptionManager::class.java)
            val activeSubscriptions = subscriptionManager.activeSubscriptionInfoList

            if (activeSubscriptions.size > 1) {
                val simOptions = activeSubscriptions.map { it.displayName }.toTypedArray()

                AlertDialog.Builder(this)
                    .setTitle("Select SIM")
                    .setItems(simOptions) { _, which ->
                        val selectedSubscription = activeSubscriptions[which]
                        saveSimPreference(selectedSubscription.subscriptionId)
                        promptForMessageTemplate()
                    }
                    .setCancelable(false)
                    .show()
            } else if (activeSubscriptions.size == 1) {
                saveSimPreference(activeSubscriptions[0].subscriptionId)
                promptForMessageTemplate()
            } else {
                Toast.makeText(this, "No active SIM found", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            Toast.makeText(this, "Permission required to read SIM info", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun saveSimPreference(simId: Int) {
        sharedPreferences.edit().putInt("selectedSimId", simId).apply()
    }

    private fun promptForMessageTemplate() {
        val input = EditText(this)
        input.hint = "Enter your message template"

        AlertDialog.Builder(this)
            .setTitle("Message Template")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val messageTemplate = input.text.toString()
                saveMessageTemplatePreference(messageTemplate)
                finish()
            }
            .setNegativeButton("Cancel") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun saveMessageTemplatePreference(template: String) {
        sharedPreferences.edit().putString("messageTemplate", template).apply()
    }
}
