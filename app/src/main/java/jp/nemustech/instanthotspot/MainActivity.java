package jp.nemustech.instanthotspot;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;


public class MainActivity extends ActionBarActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int SCAN_DELAY = 1000;
    private static final int SCAN_COUNT = 6;

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;

    private static final int SCAN_DURATION = 30;

    private static final String PREFS = "myprefs";
    private static final String PREF_WIFIAPADDR = "WifiApAddr";
    private String WifiApAddr = null;

    private Button button;
    private Button start;
    private Button stop;
    private Button host;
    private Button client;

    private BluetoothManager bTManager;
    private BluetoothAdapter bTAdapter;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCallback gattCallback;
    private String myAddr;
    private String hostAddr;

    private WifiManager manager;
    private String ssid;
    private String preSharedKey;
    private BroadcastReceiver receiver;

    private int scanCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        WifiApAddr = prefs.getString(PREF_WIFIAPADDR, null);

        button = (Button)findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                manager = (WifiManager)getSystemService(WIFI_SERVICE);
                if (!manager.isWifiEnabled()) {
                    manager.setWifiEnabled(true);
                }
                if (WifiApAddr == null) {
                    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(MainActivity.this);
                    alertDialogBuilder.setTitle(R.string.none_paired);
                    alertDialogBuilder.setMessage(R.string.ask_pairing);
                    alertDialogBuilder.setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            return;
                        }
                    });
                    AlertDialog dialog = alertDialogBuilder.create();
                    dialog.show();
                } else {
                    BluetoothDevice device = bTAdapter.getRemoteDevice(WifiApAddr);
                    Toast.makeText(MainActivity.this, R.string.connecting, Toast.LENGTH_LONG).show();
                    bluetoothGatt = device.connectGatt(MainActivity.this, false, gattCallback);
                }
            }
        });

        start = (Button)findViewById(R.id.start);
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent serviceIntent = new Intent(MainActivity.this, InstantHotSpotService.class);
                MainActivity.this.startService(serviceIntent);
            }
        });

        stop = (Button)findViewById(R.id.stop);
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent serviceIntent = new Intent(MainActivity.this, InstantHotSpotService.class);
                MainActivity.this.stopService(serviceIntent);
            }
        });

        host = (Button)findViewById(R.id.host);
        host.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Paring for host
                ensureDiscoverable();
            }
        });

        client = (Button)findViewById(R.id.client);
        client.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Paring for client
                // Launch the DeviceListActivity to see devices and do scan
                Intent intent = new Intent(MainActivity.this, DeviceListActivity.class);
                startActivityForResult(intent, REQUEST_CONNECT_DEVICE_INSECURE);

            }
        });

        start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE_SECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                }
                break;
            case REQUEST_CONNECT_DEVICE_INSECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    String address = data.getExtras()
                            .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    Log.d(TAG, "Host addr = " + address);
                    SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString(PREF_WIFIAPADDR, address);
                    editor.apply();
                    WifiApAddr = address;
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a chat session
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    finish();
                }
        }
    }

    private void start() {
        bTManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bTAdapter = bTManager.getAdapter();
        myAddr = bTAdapter.getAddress();

        gattCallback = new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                Log.d(TAG, "onConnectionStateChange: " + status + "->" + newState);
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i(TAG, "Connected to GATT server. " + gatt.getDevice().getName());
                    bluetoothGatt = gatt;
                    //gatt.requestMtu(256);
                    gatt.discoverServices();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.i(TAG, "Disconnected from GATT server. " + gatt.getDevice().getName());
                }
            }

            @Override
            public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
                Log.d(TAG, "onServicesDiscovered: ");
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    BluetoothGattService service = gatt.getService(InstantHotSpotGattServer.service_uuid);
                    if (service != null) {
                        Log.d(TAG, "Found Instant HotSpot service");
                        BluetoothGattCharacteristic characteristic = service.getCharacteristic(InstantHotSpotGattServer.field1_characteristic_uuid);
                        if (characteristic != null) {
                            boolean ret = gatt.readCharacteristic(characteristic);
                            if (!ret) {
                                Log.d(TAG, "Reading characteristic failed: " + characteristic);
                            }
                        }
                    } else {
                        Log.d(TAG, "Instant HotSpot not available");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, R.string.not_available, Toast.LENGTH_LONG).show();
                            }
                        });
                    }

                } else {
                    Log.d(TAG, "stat = " + status);
                }
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status)
            {
                Log.d(TAG, "onCharacteristicRead: status = "+ status);
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    return;
                }
                if (characteristic.getUuid().equals(InstantHotSpotGattServer.field1_characteristic_uuid)) {
                    String str = characteristic.getStringValue(0);
                    Log.d(TAG, str);
                    ssid = str;
                    if (!isWifiApConfigured()) {
                        // if WiFi Ap is not configured read PreShared Key and configure WiFi Ap to connect to
                        readCharacteristics(gatt, InstantHotSpotGattServer.field2_characteristic_uuid);
                    } else {
                        characteristic.setValue("GO");
                        boolean ret = gatt.writeCharacteristic(characteristic);
                        if (!ret) {
                            Log.d(TAG, "Writing characteristic failed: " + characteristic);
                        }
                    }
                } else if (characteristic.getUuid().equals(InstantHotSpotGattServer.field2_characteristic_uuid)) {
                    String str = characteristic.getStringValue(0);
                    preSharedKey = "\""+str+"\"";
                    registerWifiAp();
                    BluetoothGattService service = gatt.getService(InstantHotSpotGattServer.service_uuid);
                    if (service != null) {
                        characteristic = service.getCharacteristic(InstantHotSpotGattServer.field1_characteristic_uuid);
                        characteristic.setValue("GO");
                        boolean ret = gatt.writeCharacteristic(characteristic);
                        if (!ret) {
                            Log.d(TAG, "Writing characteristic failed: " + characteristic);
                        }
                    }
                }
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                Log.d(TAG, "onCharacteristicWrite: " + status + ", " + characteristic.getStringValue(0));
                Log.d(TAG, "Closing Gatt");
                gatt.disconnect();
                gatt.close();
                Timer timer = new Timer();
                TimerTask task = new TimerTask() {
                    @Override
                    public void run() {
                        scanCount = 0;
                        scanWifi();
                    }
                };
                timer.schedule(task, SCAN_DELAY);
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                Log.d(TAG, "onCharacteristicChanged: ");
            }
        };
    }

    private void readCharacteristics(BluetoothGatt gatt, UUID uuid) {
        BluetoothGattService service = gatt.getService(InstantHotSpotGattServer.service_uuid);
        if (service != null) {
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(uuid);
            if (characteristic != null) {
                boolean ret = gatt.readCharacteristic(characteristic);
                if (!ret) {
                    Log.d(TAG, "Read characteristic failed: " + characteristic);
                }
            } else {
                Log.d(TAG, "Specified UUID " + uuid.toString() + " does not exist");
            }
        }
    }

    private void scanWifi() {
        manager = (WifiManager)getSystemService(WIFI_SERVICE);

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
                    unregisterReceiver(receiver);
                    connectWifi();
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(receiver, filter);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, R.string.scanning_wifi, Toast.LENGTH_LONG).show();
            }
        });

        manager.startScan();
        scanCount++;
    }

    private boolean isWifiApConfigured() {
        List<WifiConfiguration> configs = manager.getConfiguredNetworks();
        // verify if WiFi Ap has already been configured
        for (WifiConfiguration config: configs) {
            if (config.SSID.equals("\""+ssid+"\"")) {
                return true;
            }
        }
        return false;
    }

    private void connectWifi() {
        boolean found = false;
        List<ScanResult> results = manager.getScanResults();
        for (ScanResult result: results) {
            Log.d(TAG, "SSID = " + result.SSID);
            if (result.SSID.equals(ssid)) {
                found = true;
                List<WifiConfiguration> configs = manager.getConfiguredNetworks();
                for (WifiConfiguration config: configs) {
                    Log.d(TAG, "Configured SSID = " + config.SSID);
                    if (config.SSID.equals("\""+ssid+"\"")) {
                        runOnUiThread(new Runnable() {
                                          @Override
                                          public void run() {
                                              Toast.makeText(MainActivity.this, R.string.connecting_wifi, Toast.LENGTH_LONG).show();

                                          }
                                      });
                        boolean state = manager.enableNetwork(config.networkId, true);
                        Log.d(TAG, "state = " + state);
                        finish();
                        break;
                    }
                }
                break;
            }
        }
        if (!found && scanCount < SCAN_COUNT) {
            try {
                Thread.sleep(SCAN_DELAY);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            scanWifi();
        }
    }

    private void registerWifiAp() {
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\""+ssid+"\"";
        config.preSharedKey = preSharedKey;
        config.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
        config.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
        config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
        config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
        config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
        config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
        config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
        int netId = manager.addNetwork(config);
        if (netId == -1) {
            Log.d(TAG, "failed to add WifiConfig");
        }
    }

    private void ensureDiscoverable() {
        Log.d(TAG, "ensure discoverable");
        if (bTAdapter.getScanMode() !=
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, SCAN_DURATION);
            startActivity(discoverableIntent);
        }
    }
}
