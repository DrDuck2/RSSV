package com.example.rssvkv


import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.io.OutputStream
import java.lang.RuntimeException
import java.util.UUID

class MainActivity : AppCompatActivity() {


    private var btPermission = false

    private var macAddress = ""

    private lateinit var thread: Thread

    // Bluetooth UUID for HC-06
    private val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private var red = 0
    private var green = 0
    private var blue = 0

    private lateinit var textRed: TextView
    private lateinit var textGreen: TextView
    private lateinit var textBlue: TextView
    private lateinit var sliderRed: SeekBar
    private lateinit var sliderGreen: SeekBar
    private lateinit var sliderBlue: SeekBar
    private lateinit var sendButton: Button

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothDevice: BluetoothDevice
    private lateinit var bluetoothSocket: BluetoothSocket
    private lateinit var outputStream: OutputStream

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        textRed = findViewById(R.id.textViewRed)
        textGreen = findViewById(R.id.textViewGreen)
        textBlue = findViewById(R.id.textViewBlue)

        sliderRed = findViewById(R.id.seekBarRed)
        sliderGreen = findViewById(R.id.seekBarGreen)
        sliderBlue = findViewById(R.id.seekBarBlue)

        sendButton = findViewById(R.id.SendButton)

        sendButton.setOnClickListener {
            sendCommand(red, green, blue)
        }

        sliderRed.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                red = (255.toFloat() / 100 * progress).toInt()
                textRed.text = "R: $red"
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })

        sliderGreen.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                green = (255.toFloat() / 100 * progress).toInt()
                textGreen.text = "G: $green"
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })

        sliderBlue.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                blue = (255.toFloat() / 100 * progress).toInt()
                textBlue.text = "B: $blue"
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })

        // Prompt user to turn on the Bluetooth
        runBlocking {
            scanBt()
        }

        // Permission Check ->
        /*if(ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED){
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S){
                Log.e("PERMISSION_CHECK", "Permission: Code 1")
                scanBt()
                return
            }
        }*/

        // Fetch the bluetooth device (name, MacAddress)
        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager.adapter
        val pairedDevices = bluetoothAdapter.bondedDevices
        for(device in pairedDevices){
            if(device.name == "HC-06"){
                macAddress = device.address
                Log.d("MacAddress", "MAC - > $macAddress")
            }
        }

        if(macAddress != ""){
            bluetoothDevice = bluetoothAdapter.getRemoteDevice(macAddress)

            var threadState = Thread.State.NEW // Default state is NEW

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Log.d("PERMISSION_CHECK", "Permission: Code 2")
                    scanBt()
                    return
                }
            }

            // Constantly Trying to connect to the socket
            // Closes only on 'onDestroy' or if the connection via socket passed
            thread = Thread {
                var connectionSuccessful = false
                while (!Thread.currentThread().isInterrupted) {
                    try {
                        bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(uuid)
                        bluetoothAdapter.cancelDiscovery()
                        bluetoothSocket.connect()
                        outputStream = bluetoothSocket.outputStream
                        Log.d("Connection", "Connected to HC-06")
                        runOnUiThread {
                            Toast.makeText(this, "Bluetooth Successfully connected", Toast.LENGTH_LONG).show()
                        }
                        connectionSuccessful = true

                        // Sleep for a while before attempting to reconnect
                        Thread.sleep(4000)
                    } catch (e: IOException) {
                        Log.d("Socket", "${e.message}")
                        runOnUiThread {
                            Toast.makeText(this, "Can't connect via Socket", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: InterruptedException) {
                        // Thread interrupted, terminate loop
                        break
                    } finally {
                        if(connectionSuccessful) thread.interrupt()
                    }
                }
                threadState = Thread.State.TERMINATED
            }

            thread.start()



            // Thread Debugging
            val mainHandler = Handler(Looper.getMainLooper())
            val checkThreadState = object : Runnable {
                override fun run() {
                    if(threadState == Thread.State.TERMINATED){
                        mainHandler.removeCallbacks(this)
                        Log.d("Thread State", "Thread State: $threadState")
                        return
                    }
                    Log.d("Thread State", "Thread State: $threadState")
                    mainHandler.postDelayed(this, 3000)
                }
            }

            mainHandler.postDelayed(checkThreadState, 3000)
            // Thread Debugging
        }else{
            Toast.makeText(this, "No such device HC-06. Change the name or turn on device", Toast.LENGTH_SHORT).show()
        }


    }

    // Send Data to HC-06 Device
    private fun sendCommand(red: Int, green: Int, blue: Int){
        val outputString = "${red},${green},${blue}"
        Log.d("OutputString- > ", outputString)
        if(!::outputStream.isInitialized){
            Log.d("Stream", "Output stream not initialized")
            return
        }
        try{
            var command = outputString
            command += "\n"
            outputStream.write(command.toByteArray())
            Log.d("Command- > ", command)
        }catch (e: IOException){
            throw RuntimeException(e)
        }
    }

    // Scan for Bluetooth Permissions
    private fun scanBt(){
        val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter: BluetoothAdapter = bluetoothManager.adapter

        if(bluetoothAdapter == null){
            Toast.makeText(this, "Device doesn't support Bluetooth", Toast.LENGTH_SHORT).show()
        }else{
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S){
                bluetoothPermissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_SCAN))
            }else{
                bluetoothPermissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_ADMIN,Manifest.permission.BLUETOOTH_SCAN))
            }
        }
    }

    // Bluetooth Permission Launcher
    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()){ permissions ->
        val isGranted =
            permissions[Manifest.permission.BLUETOOTH_CONNECT] ?: false && permissions[Manifest.permission.BLUETOOTH_SCAN] ?: false

        // If user granted the permissions
        if (isGranted) {
            val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
            val bluetoothAdapter: BluetoothAdapter = bluetoothManager.adapter

            btPermission = true
            // Bluetooth not enabled, ENABLE IT
            if (bluetoothAdapter?.isEnabled == false) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                // Prompt user to allow bluetooth
                btActivityResultLauncher.launch(enableBtIntent)
            } else {
                // If it is enabled just show the message
                btScan()
            }
        } else {
            btPermission = false
        }
    }

    // Prompt Launcher
    private val btActivityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ){result: ActivityResult ->
        if( result.resultCode == RESULT_OK){
            // Show the message if result of the prompt is positive
            btScan()
        }
    }

    // Shows a bluetooth message
    private fun btScan(){
        Toast.makeText(this,"BlueTooth Connected successfully", Toast.LENGTH_LONG).show()
    }

    // Destroy threads and close the socket
    override fun onDestroy(){
        super.onDestroy()
        thread.interrupt()
        if(::bluetoothSocket.isInitialized){
            try{
                bluetoothSocket.close()
                Log.d("Socket", "Connection Closed")
            }catch (e: IOException){
                Log.d("Closing", "Error while closing the connection")
            }
        }
    }
}