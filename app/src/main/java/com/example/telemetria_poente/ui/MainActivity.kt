package com.example.telemetria_poente.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.widget.Toast
import android.provider.Settings
import com.example.telemetria_poente.service.LocationService
import android.app.Activity
import android.content.IntentSender
import android.location.LocationManager
import android.widget.Switch
import com.example.telemetria_poente.R
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationServices
import com.example.telemetria_poente.service.SocketService
import com.google.android.gms.security.ProviderInstaller


class MainActivity : AppCompatActivity() {
    private val PERMISSION_REQUEST_CODE = 100
    private val BACKGROUND_LOCATION_REQUEST_CODE = 200
    private val REQUEST_CHECK_SETTINGS = 300
//    private val SERVER_URL = "http://192.168.15.43:4000"
    private val SERVER_URL = "https://barcos-backend.onrender.com"

    private lateinit var latLongTextView: TextView
    private lateinit var veloTextView: TextView
    private lateinit var rastreamentoSwitch: Switch

    private val locationUpdateReceiver = object : BroadcastReceiver() {
        @SuppressLint("SetTextI18n")
        override fun onReceive(context: Context, intent: Intent) {
            if(!rastreamentoSwitch.isChecked){
                rastreamentoSwitch.isChecked = true
            }
            val latitude = intent.getDoubleExtra("latitude", 0.0)
            val longitude = intent.getDoubleExtra("longitude", 0.0)
            val velocidade = intent.getFloatExtra("velocidade", 0f)
            val velocidadeFormatada = "${"%.2f".format(velocidade).replace(',', '.')}"
            latLongTextView.text = "Lat: $latitude | Long: $longitude"
            veloTextView.text = "$velocidadeFormatada nós"

            if (SocketService.isConnected()) {
                SocketService.send("speed", "$velocidadeFormatada")
            }
        }
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        latLongTextView = findViewById(R.id.latLongTextView)
        veloTextView = findViewById(R.id.veloTextView)
        rastreamentoSwitch = findViewById(R.id.rastreamentoSwitch)

        enableTls12()
        // Conectar ao servidor
        SocketService.connect(SERVER_URL)


        rastreamentoSwitch.isChecked = getSavedSwitchState()
        rastreamentoSwitch.setOnCheckedChangeListener { _, isChecked ->
            saveSwitchState(isChecked)
            if (isChecked) {
                if (isLocationPermissionGranted()) {
                    if (VERSION.SDK_INT >= VERSION_CODES.Q) {
                        if (isBackgroundLocationPermissionGranted()) {
                            startLocationService()
                        } else {
                            requestBackgroundLocationPermission()
                        }
                    } else {
                        startLocationService()
                    }
                } else {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ),
                        PERMISSION_REQUEST_CODE
                    )
                }
            } else {
                // Switch está desligado
                stopLocationService()
                latLongTextView.text = ""
                veloTextView.text = ""
            }
        }


        LocalBroadcastManager.getInstance(this)
            .registerReceiver(locationUpdateReceiver, IntentFilter("LocationUpdate"))
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(locationUpdateReceiver)
    }

    private fun isLocationPermissionGranted(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startLocationService() {
        if (isGpsEnabled() && rastreamentoSwitch.isChecked) {
            val intent = Intent(this, LocationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } else {
            enableGps()
        }
    }


    private fun stopLocationService() {
        val intent = Intent(this, LocationService::class.java)
        stopService(intent)
    }

    private fun requestBackgroundLocationPermission() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", packageName, null)
        intent.data = uri
        startActivityForResult(intent, BACKGROUND_LOCATION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (VERSION.SDK_INT >= VERSION_CODES.Q) {
                    requestBackgroundLocationPermission()
                } else {
                    startLocationService()
                }
            }
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == BACKGROUND_LOCATION_REQUEST_CODE) {
            if (isBackgroundLocationPermissionGranted()) {
                startLocationService()
            } else {
                Toast.makeText(
                    applicationContext,
                    "É necessário ativar o tempo todo",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else if (requestCode == REQUEST_CHECK_SETTINGS) {
            if (resultCode == Activity.RESULT_OK) {
                startLocationService()
            } else {
                Toast.makeText(this, "Ative o GPS para usar o rastreador", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun isBackgroundLocationPermissionGranted(): Boolean {
        return if (VERSION.SDK_INT >= VERSION_CODES.Q) {
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun isGpsEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    private fun enableGps() {
        val locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val settingsClient = LocationServices.getSettingsClient(this)
        val task = settingsClient.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            startLocationService()
        }

        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    exception.startResolutionForResult(this, REQUEST_CHECK_SETTINGS)
                } catch (sendEx: IntentSender.SendIntentException) {
                    // Ignore the error.
                }
            } else {
                Toast.makeText(this, "Ative o GPS para usar o rastreador", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveSwitchState(state: Boolean) {
        val sharedPreferences = getSharedPreferences("appPreferences", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putBoolean("switchState", state)
        editor.apply()
    }

    private fun getSavedSwitchState(): Boolean {
        val sharedPreferences = getSharedPreferences("appPreferences", Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean("switchState", false)
    }

    private fun enableTls12() {
        if (VERSION.SDK_INT in 16..20) {
            try {
                ProviderInstaller.installIfNeeded(this)
            } catch (e: Exception) {
                // Não foi possível habilitar o TLS 1.2, trate o erro conforme necessário
            }
        }
    }

}