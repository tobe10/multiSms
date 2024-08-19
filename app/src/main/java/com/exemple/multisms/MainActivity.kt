package com.exemple.multisms

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.exemple.multisms.SmsSendingService
import com.exemple.multisms.ui.theme.MultiSMSTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class MainActivity : ComponentActivity() {
    private lateinit var sharedPreferences: SharedPreferences
    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allPermissionsGranted = permissions.entries.all { it.value }
        if (allPermissionsGranted) {
            Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "All permissions are required to send SMS", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private val defaultSmsAppLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            if (isDefaultSmsApp()) {
                println("App is set as default SMS app")
                Toast.makeText(this, "App set as default SMS app", Toast.LENGTH_SHORT).show()
                // Proceed with SMS sending functionality
            } else {
                // User denied permission
                Toast.makeText(
                    this,
                    "App needs to be default SMS app to send messages",
                    Toast.LENGTH_LONG
                ).show()
                // Optionally, provide instructions on how to change default SMS app in settings
            }
        } else {
            // Handle cases where the activity result is not OK (e.g., canceled)
        }
    }


    private fun saveSimPreference(simId: Int) {

        sharedPreferences = getSharedPreferences("sms_prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putInt("selectedId", simId).commit()
        Log.e("MainActivity", "Saved SIM ID: $simId")
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
                //   finish()
            }
            .setNegativeButton("Cancel") { _, _ ->
                //    finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun saveMessageTemplatePreference(template: String) {
        sharedPreferences.edit().putString("messageTemplate", template).apply()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {

            val isDefaultSmsApp by remember { mutableStateOf(isDefaultSmsApp()) }
            val hasPermissions by remember { mutableStateOf(checkPermissions()) }

            LaunchedEffect(Unit) {
                if (!isDefaultSmsApp) {
                    requestDefaultSmsApp()
                }


                if (!hasPermissions) {
                    requestPermissions()
                }
            }


            MultiSMSTheme {
                // if (isDefaultSmsApp && hasPermissions) {
                saveMsgAndSimId()
                //  } else {requestPermissions()
                // saveMsgAndSimId()
                //   }
                SmsApp(
                )
            }
        }
    }


    private fun checkPermissions(): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            arrayOf(
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.RECEIVE_MMS,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.RECEIVE_WAP_PUSH,
                Manifest.permission.FOREGROUND_SERVICE

            )
        } else {
            arrayOf(
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.RECEIVE_MMS,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.RECEIVE_WAP_PUSH
            )
        }
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }


    private fun isDefaultSmsApp(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
            roleManager.isRoleHeld(RoleManager.ROLE_SMS)
        } else {
            packageName == Telephony.Sms.getDefaultSmsPackage(this)
        }
    }


    //
    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_PHONE_STATE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            permissions.addAll(
                listOf(
                    Manifest.permission.RECEIVE_MMS,
                    Manifest.permission.RECEIVE_WAP_PUSH,
                    Manifest.permission.FOREGROUND_SERVICE
                )
            )
        }

        permissionsLauncher.launch(permissions.toTypedArray())
    }


    private fun requestDefaultSmsApp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager

            if (!roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                defaultSmsAppLauncher.launch(intent)
            }
        } else {
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
            defaultSmsAppLauncher.launch(intent)
        }
    }

    private fun saveMsgAndSimId() {

//        val idSaved =getSharedPreferences("sms_prefs", MODE_PRIVATE).contains("sms")
//        val idNonNull =getSharedPreferences("sms_prefs", MODE_PRIVATE).getInt("selectedId",-1)>0
//        if ( !idSaved || !idNonNull){
        CoroutineScope(Dispatchers.IO).launch {
            sharedPreferences = getSharedPreferences("com.example.sms_prefs", Context.MODE_PRIVATE)
            //    requestPermissions()
            // Check for permission to access subscription info
            if (ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val subscriptionManager = getSystemService(SubscriptionManager::class.java)
                val activeSubscriptions = subscriptionManager.activeSubscriptionInfoList

                if (activeSubscriptions.size > 1) {
                    val simOptions = activeSubscriptions.map { it.displayName }.toTypedArray()
                    withContext(Dispatchers.Main) {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Select SIM")
                            .setItems(simOptions) { _, which ->
                                val selectedSubscription = activeSubscriptions[which]
                                saveSimPreference(selectedSubscription.subscriptionId)
                                promptForMessageTemplate()
                            }
                            .setCancelable(false)
                            .show()
                    }
                } else if (activeSubscriptions.size == 1) {
                    saveSimPreference(activeSubscriptions[0].subscriptionId)
                    promptForMessageTemplate()
                } else {
                    withContext(Dispatchers.Main){
                        Toast.makeText(this@MainActivity, "No active SIM found", Toast.LENGTH_SHORT)
                            .show()
                        finish()
                    }
//                    Toast.makeText(this@MainActivity, "No active SIM found", Toast.LENGTH_SHORT)
//                        .show()
//                    finish()
                }
            } else {
                withContext(Dispatchers.Main){
                    Toast.makeText(this@MainActivity, "Permission required to read SIM info", Toast.LENGTH_SHORT)
                        .show()
                    finish()
                }
//                Toast.makeText(
//                    this@MainActivity,
//                    "Permission required to read SIM info",
//                    Toast.LENGTH_SHORT
//                )
//                    .show()
//                finish()
           }


        }

    }


}

@Composable
fun SmsApp() {
    var permissionGranted by remember { mutableStateOf(false) }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        permissionGranted = isGranted
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.SEND_SMS)

    }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        selectedFileUri = uri
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (selectedFileUri == null) {
            Button(onClick = { filePickerLauncher.launch(arrayOf("application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) }) {
                Text("Import File")
            }
        } else {
            Text("File selected: ${selectedFileUri?.path}")
            Spacer(modifier = Modifier.height(16.dp))
            if (permissionGranted) {
                Button(onClick = {
                    val contacts = readExcelFromUri(context, selectedFileUri!!)
                    val intent = Intent(context, SmsSendingService::class.java).apply {
                        putParcelableArrayListExtra("contacts", ArrayList(contacts))
                        //putExtra("messageTemplate", messageTemplate)
                    }

                    context.startService(intent)
                }) {Text("Send Messages")
                }
            } else {
                Text("SMS permission is required to send messages.")
            }
        }
    }
}