package com.andromeda.healthmonitoring;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    FirebaseDatabase database = FirebaseDatabase.getInstance();
    DatabaseReference bodyTemperature = database.getReference("Body_Temperature");
    DatabaseReference heartRate = database.getReference("Heart_Rate");
    DatabaseReference bodyMovement = database.getReference("Body_Movement");

    ListView listView;
    LinearLayout refreshList;
    TextView temp, bpm, bm;
    FloatingActionButton floatingActionButton;
    EditText editText;

    public String address;
    public String[] parsedData;
    boolean smsSent = false;

    private BluetoothAdapter mBluetoothAdapter;
    BluetoothSocket bluetoothSocket;
    private static final UUID mUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    String receivedMessage;
    private boolean isBtConnected = false;

    String geocode, number = "+8801791616240", smsBody;

    FusedLocationProviderClient mFusedLocationClient;

    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler() { ///////////////////////////////////////////////////// handle received messages
        @Override
        public void handleMessage(Message msg) {
            byte[] writeBuf = (byte[]) msg.obj;
            int begin = msg.arg1;
            int end = msg.arg2;

            switch(msg.what) {
                case 1:
                    receivedMessage = new String(writeBuf);
                    receivedMessage = receivedMessage.substring(begin, end);
                    onBluetoothMessageReceive();
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        listView = findViewById(R.id.paired_device_list);
        refreshList = findViewById(R.id.refresh_list);
        temp = findViewById(R.id.temp);
        bpm = findViewById(R.id.bpm);
        bm = findViewById(R.id.bm);
        editText = findViewById(R.id.phone);
        floatingActionButton = findViewById(R.id.floatingActionButton);
        floatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                smsSent = false;
            }
        });

        refreshList.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pairedDevicesList();
            }
        });

        turnOnBluetooth();

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            return;
        }
        mFusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                            geocode = location.getLatitude() + "," + location.getLongitude();
                            //locationData = "geo:0,0?q=" + geocode + "(Help)";
                            //l.setText(geocode);
                        }
                    }
                });
    }


    void turnOnBluetooth() { /////////////////////////////////////////////////////////////////////// turn on bluetooth
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(getApplicationContext(), "Bluetooth Device Not Available", Toast.LENGTH_LONG).show();
            finish();
        } else {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 1);

            } else {
                pairedDevicesList();
            }
        }
    }


    public void pairedDevicesList() { ////////////////////////////////////////////////////////////// load paired devices and show them in the list
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        ArrayList<String> list = new ArrayList<>();

        if (pairedDevices.size() > 0) {
            for (BluetoothDevice bt : pairedDevices) {
                list.add(bt.getName() + "\n" + bt.getAddress());
            }
        } else {
            Toast.makeText(getApplicationContext(), "No Paired Bluetooth Devices Found.", Toast.LENGTH_LONG).show();
        }

        final ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, list);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(mListClickListener);
    }

    private AdapterView.OnItemClickListener mListClickListener = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView av, View v, int arg2, long arg3) {
            String info = ((TextView) v).getText().toString();
            address = info.substring(info.length() - 17);
            ConnectBluetooth connectBluetooth = new ConnectBluetooth();
            connectBluetooth.execute(); /////////////////////////////////////////////////////// connect
        }
    };

    @SuppressLint("StaticFieldLeak")
    class ConnectBluetooth extends AsyncTask<Void, Void, Void> { /////////////////////////// connect in background
        private boolean ConnectSuccess = true;

        @Override
        protected Void doInBackground(Void... devices) {
            try {
                if (bluetoothSocket == null || !isBtConnected) {
                    BluetoothDevice bluetoothDevice = mBluetoothAdapter.getRemoteDevice(address);
                    bluetoothSocket = bluetoothDevice.createInsecureRfcommSocketToServiceRecord(mUUID);
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                    bluetoothSocket.connect();
                }
            } catch (IOException e) {
                ConnectSuccess = false;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);

            if (ConnectSuccess) {
                isBtConnected = true;
                ConnectedThread connectedThread = new ConnectedThread(bluetoothSocket);
                Toast.makeText(getApplicationContext(), "Connected", Toast.LENGTH_SHORT).show();
                connectedThread.start(); /////////////////////////////////////////////////////////// start looking for received data
            }
        }
    }

    private class ConnectedThread extends Thread { ///////////////////////////////////////////////// received data listener
        private final InputStream mInStream;

        ConnectedThread(BluetoothSocket socket) {
            bluetoothSocket = socket;
            InputStream tmpIn = null;
            try {
                tmpIn = socket.getInputStream();
            } catch (IOException ignored) {
            }
            mInStream = tmpIn;
        }

        public void run() {
            byte[] buffer = new byte[1024];
            int begin = 0;
            int bytes = 0;
            while (true) {
                try {
                    bytes += mInStream.read(buffer, bytes, buffer.length - bytes);
                    for (int i = begin; i < bytes; i++) {
                        if (buffer[i] == "\n".getBytes()[0]) { ///////////////////////////////////// read data until new line
                            mHandler.obtainMessage(1, begin, i, buffer).sendToTarget();
                            begin = i + 1;
                            if (i == bytes - 1) {
                                bytes = 0;
                                begin = 0;
                            }
                        }
                    }
                } catch (IOException e) {
                    break;
                }
            }
        }
    }

    public void onBluetoothMessageReceive(){
        if(!editText.getText().toString().equals("")) {
            number = editText.getText().toString();
            /////////////////////////////////////
        }
        parsedData = receivedMessage.split(",");
        if(parsedData[0] != null) {
            temp.setText(parsedData[0]);
            int t = Integer.parseInt(parsedData[0]);
            if(t > 101 && !smsSent) {
                smsBody = String.format(Locale.getDefault(), "Body Temperature : %d Location : https://www.google.com/maps/?q=%s", t, geocode);
                SmsManager sms = SmsManager.getDefault();
                sms.sendTextMessage(number, null, smsBody, null, null);
                Toast.makeText(MainActivity.this, "Message Sent to " + number, Toast.LENGTH_SHORT).show();
                smsSent = true;
            }
            bodyTemperature.setValue(t);

        }
        if(parsedData[1] != null) {
            bpm.setText(parsedData[1]);
            int h = Integer.parseInt(parsedData[1]);
            if(h > 120 && !smsSent) {
                smsBody = String.format(Locale.getDefault(), "Heart Rate : %d Location : https://www.google.com/maps/?q=%s", h, geocode);
                SmsManager sms = SmsManager.getDefault();
                sms.sendTextMessage(number, null, smsBody, null, null);
                Toast.makeText(MainActivity.this, "Message Sent to " + number, Toast.LENGTH_SHORT).show();
                smsSent = true;
            }
            heartRate.setValue(h);

        }
        if(parsedData[2] != null) {
            bm.setText(parsedData[2]);
            if(parsedData[2].equals("abnormal") && !smsSent) {
                smsBody = String.format(Locale.getDefault(), "Body Movement : %s Location : https://www.google.com/maps/?q=%s", parsedData[2], geocode);
                SmsManager sms = SmsManager.getDefault();
                sms.sendTextMessage(number, null, smsBody, null, null);
                Toast.makeText(MainActivity.this, "Message Sent to " + number, Toast.LENGTH_SHORT).show();
                smsSent = true;
            }
            bodyMovement.setValue(parsedData[2]);
        }
    }

}
