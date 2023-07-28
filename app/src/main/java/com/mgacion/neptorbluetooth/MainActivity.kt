package com.mgacion.neptorbluetooth

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.MotionEvent
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.util.*
import java.util.UUID.fromString


///////////Global Variables///////////
const val REQUEST_ENABLE_BT = 1
//////////////////
class MainActivity : AppCompatActivity() {
    ///////////Comunication Variables///////////
    //Bluetooth Adapter
    lateinit var mBtAdapter: BluetoothAdapter
    private var bluetoothGatt: BluetoothGatt? = null
    var mAddressDevices: ArrayAdapter<String>? = null
    var mNameDevices: ArrayAdapter<String>? = null
    private var scanning = false
    private var gattConnected = false
    private val mBLEHandler = Handler()


    companion object{
        //var m_myUUID: UUID = fromString("00001101-0000-1000-8000-00805F9B34FB")
        var m_myUUID: UUID = fromString("00001800-0000-1000-8000-00805f9b34fb")
        private var m_BluetoothSocket: BluetoothSocket? = null

        var m_isConnected: Boolean = false
        lateinit var m_address: String
    }

    ///////////Implementing ScanCallback///////////
    private val leScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {

            val device: BluetoothDevice = result.device

            mNameDevices?.add(device?.name.toString())
            mAddressDevices?.add(device?.address)

        }
    }





    ///////////Implementing GattCallback///////////
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.w("BluetoothGattCallback",
                        "Successfully connected to $deviceAddress")
                    gattConnected=true
                    bluetoothGatt=gatt

                    //Discover Gatt services
                    gatt.discoverServices()

                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    gattConnected = false
                    Log.w("BluetoothGattCallback", "Disconnected from $deviceAddress")
                    gatt.close()
                }
            } else {
                Log.w("BluetoothGattCallback", "Error $status encountered for " +
                        "$deviceAddress! Disconnecting...")
                gatt.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int){
            with(gatt) {
                Log.w("BluetoothGattCallback", "Discovered ${services.size} services for ${device.address}")
                printGattTable() // See implementation just above this section
                // Consider connection setup as complete here
            }
        }
    }

    private fun BluetoothGatt.printGattTable() {
        if (services.isEmpty()) {
            Log.i("printGattTable", "No service and characteristic available, call discoverServices() first?")
            return
        }
        services.forEach { service ->
            val characteristicsTable = service.characteristics.joinToString(
                separator = "\n|--",
                prefix = "|--"
            ) { it.uuid.toString() }
            Log.i("printGattTable", "\nService ${service.uuid}\nCharacteristics:\n$characteristicsTable"
            )
        }
    }






    ///////////OnCreate///////////
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ///////////Initializing ArrayAdapter///////////
        mAddressDevices = ArrayAdapter(this, android.R.layout.simple_list_item_1)
        mNameDevices = ArrayAdapter(this, android.R.layout.simple_spinner_item)

        ///////////Relations with xml objects///////////
        val idScanBtn = findViewById<Button>(R.id.idScanBtn)
        val idUpArrow = findViewById<ImageButton>(R.id.idUpArrow)
        val idLeftArrow = findViewById<ImageButton>(R.id.idLeftArrow)
        val idRightArrow = findViewById<ImageButton>(R.id.idRightArrow)
        val idDownArrow = findViewById<ImageButton>(R.id.idDownArrow)
        val idModeBtn = findViewById<ToggleButton>(R.id.idModeBtn)
        val idSpinList = findViewById<Spinner>(R.id.idSpinList)
        val idConnBtn = findViewById<Button>(R.id.idConnBtn)

        //Add adapter to Spinner
        idSpinList.adapter = mNameDevices

        ///////////Bluetooth device communication///////////
        val someActivityResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ){
            result ->
            if (result.resultCode == REQUEST_ENABLE_BT) {
                Log.i("MainActivity","ACTIVIDAD REGISTRADA")
            }
        }

        ///////////Bluetooth device Init///////////
        mBtAdapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter

        ///////////Check if BT is possible to activate///////////
        if(mBtAdapter == null){
            Toast.makeText(this, "Bluetooth no está disponible en este dispositivo",
                Toast.LENGTH_LONG).show()
        }

        ///////////Ask for BT Permission///////////
        if(ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ){
            Log.i("MainActivity", "ActivityCompat#requestPermission")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT),
                    1
                )
            }
        }

        ///////////BLE scanner Config///////////
        val bluetoothLeScanner = mBtAdapter.bluetoothLeScanner


        ///////////Objects' Listeners///////////

        idScanBtn.setOnClickListener {
             //Scan Toggled
                //I scan for devices, else I activate Bluetooth
                if(mBtAdapter.isEnabled){
                    val pairedDevices: Set<BluetoothDevice>? = mBtAdapter?.bondedDevices
                    mAddressDevices!!.clear()
                    mNameDevices!!.clear()

                    //registerReceiver(receiver, filter)

                    val bluetoothLeScanner = mBtAdapter.bluetoothLeScanner

                    val scanPeriod: Long = 10000 //10 seconds

                    //This needs to stop after 10 seconds or it will drawn the battery
                    //That's why we use a Handler with the postDelayed method.
                    if (!scanning) {
                            mBLEHandler.postDelayed({
                                scanning = false
                                bluetoothLeScanner.stopScan(leScanCallback)
                                Toast.makeText(this, "Scanning Stopped",
                                    Toast.LENGTH_LONG).show()
                            }, scanPeriod)
                            scanning = true
                            Toast.makeText(this, "Scanning", Toast.LENGTH_LONG).show()
                            bluetoothLeScanner.startScan(leScanCallback)
                    } else {
                            scanning = false
                            bluetoothLeScanner.stopScan(leScanCallback)
                            Toast.makeText(this, "Scanning Stopped",
                                Toast.LENGTH_LONG).show()
                    }


                } else {
                    //Start Bluetooth
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    someActivityResultLauncher.launch(enableBtIntent)
                }
        }

        idConnBtn.setOnClickListener{ //Connect to selected device
            try{
                val bluetoothLeScanner = mBtAdapter.bluetoothLeScanner

                if(m_BluetoothSocket == null || !m_isConnected){
                    //Get address of the selected device
                    val intValSpin = idSpinList.selectedItemPosition
                    m_address = mAddressDevices!!.getItem(intValSpin).toString()
                    Toast.makeText(this, m_address, Toast.LENGTH_LONG).show()

                    //Cancel scanning or it will slow down connection
                    if(scanning) {
                        bluetoothLeScanner.stopScan(leScanCallback)
                        scanning = false
                        Toast.makeText(this, "Scanning Stopped",
                            Toast.LENGTH_LONG).show()
                    }


                    //Get device by address
                    val device: BluetoothDevice = mBtAdapter.getRemoteDevice(m_address)

                    //Connecting to BLE device
                    device.connectGatt(this,false,gattCallback,2) //Gatt Connect
                    //The 4th parameter, "transport" is optional and indicates that it's trying to
                    //connect to a BLE device, this is avoiding error 133 in Gatt

                }
            } catch(e: IOException){
                e.printStackTrace()
                Toast.makeText(this, "ERROR DE CONEXIÓN", Toast.LENGTH_LONG).show()
                Log.i("MainActivity","ERROR DE CONEXION")
            }
        }

        idModeBtn.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                writeCharacteristic("M")
            } else {
                writeCharacteristic("A")
            }
        }


        idUpArrow.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN ->   // PRESSED
                    writeCharacteristic("U")
                MotionEvent.ACTION_UP ->     // RELEASED
                    writeCharacteristic("S")
            }
            false
        }

        idLeftArrow.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN ->   // PRESSED
                    writeCharacteristic("L")
                MotionEvent.ACTION_UP ->     // RELEASED
                    writeCharacteristic("S")
            }
            false
        }

        idRightArrow.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN ->   // PRESSED
                    writeCharacteristic("R")
                MotionEvent.ACTION_UP ->     // RELEASED
                    writeCharacteristic("S")
            }
            false
        }

        idDownArrow.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN ->   // PRESSED
                    writeCharacteristic("D")
                MotionEvent.ACTION_UP ->     // RELEASED
                    writeCharacteristic("S")
            }
            false
        }

    }

    ///////////Sent Function///////////
    private fun sendCommand(input: String){
        if (m_BluetoothSocket != null){
            try{
                m_BluetoothSocket!!.outputStream.write(input.toByteArray())
            } catch(e: IOException){
                e.printStackTrace()
            }
        }
    }

    ///////////Check if Writable functions///////////
    fun BluetoothGattCharacteristic.isWritable(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE)

    fun BluetoothGattCharacteristic.isWritableWithoutResponse(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)

    private fun BluetoothGattCharacteristic.containsProperty(property: Int): Boolean {
        return properties and property != 0
    }

    ///////////Write to BLE function///////////
    fun writeCharacteristic(input: String) {

        bluetoothGatt?.let { gatt ->

            var services = gatt.getServices()
            var mGattCharacteristic: BluetoothGattCharacteristic  = services[3].characteristics[0]

            val writeType = when {
                mGattCharacteristic.isWritable() -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                mGattCharacteristic.isWritableWithoutResponse() -> {
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                }
                else -> error("Characteristic ${mGattCharacteristic.uuid} cannot be written to")
            }

            mGattCharacteristic.writeType = writeType
            var payload: ByteArray = input.toByteArray()
            mGattCharacteristic.value = payload
            gatt.writeCharacteristic(mGattCharacteristic)
            Log.w("Writen?","Yes...")
        } ?: error("Not connected to a BLE device!")
    }

}


