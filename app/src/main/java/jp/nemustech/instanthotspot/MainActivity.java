package jp.nemustech.instanthotspot;

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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends ActionBarActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int SCAN_DELAY = 1000;

    public static final String TXTRECORD_PROP_AVAILABLE = "available";
    public static final String BT_ADDR = "btAddr";
    public static final String SERVICE_INSTANCE = "Instant HotSpot";
    public static final String SERVICE_REG_TYPE = "_instanthotspot._tcp";

    private static final String WifiApAddr = "F0:6B:CA:35:96:EC";

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
    private BroadcastReceiver receiver;

    private WifiP2pManager p2pManager;
    private WifiP2pManager.Channel channel;
    private WifiP2pDnsSdServiceInfo service;
    private BroadcastReceiver p2PReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //registerWifiP2pReceiver();

        button = (Button)findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BluetoothDevice device = bTAdapter.getRemoteDevice(WifiApAddr);
                bluetoothGatt = device.connectGatt(MainActivity.this, false, gattCallback);
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
                registerWifiP2pReceiver();
                initP2p();
                addLocalService();
                startDiscovery();
            }
        });

        client = (Button)findViewById(R.id.client);
        client.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Paring for client
                initP2p();
                initDnsSd();
                startDiscovery();
            }
        });

        start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        removeLocalService();
        unregisterWifiP2pReceiver();
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
                            gatt.readCharacteristic(characteristic);
                        }
                    }

                } else {
                    Log.d(TAG, "stat = " + status);
                }
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status)
            {
                Log.d(TAG, "onCharacteristicRead: ");
                if (characteristic.getUuid().equals(InstantHotSpotGattServer.field1_characteristic_uuid)) {
                    String str = characteristic.getStringValue(0);
                    Log.d(TAG, str);
                    ssid = str;
                    gatt.close();
                    Timer timer = new Timer();
                    TimerTask task = new TimerTask() {
                        @Override
                        public void run() {
                            scanWifi();
                        }
                    };
                    timer.schedule(task, SCAN_DELAY);
                }
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                Log.d(TAG, "onCharacteristicWrite: " + status + ", " + characteristic.getStringValue(0));
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                Log.d(TAG, "onCharacteristicChanged: ");
            }
        };
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

        manager.startScan();
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
                    Log.d(TAG, "Configed SSID = " + config.SSID);
                    if (config.SSID.equals("\""+ssid+"\"")) {
                        manager.enableNetwork(config.networkId, true);
                        finish();
                        break;
                    }
                }
                break;
            }
        }
        if (!found) {
            try {
                Thread.sleep(SCAN_DELAY);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            scanWifi();
        }
    }

    private void initP2p() {
        p2pManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        WifiP2pManager.ChannelListener listener = new WifiP2pManager.ChannelListener() {
            @Override
            public void onChannelDisconnected() {
                    Log.d(TAG, "Channel disconnected");
            }
        };
        channel = p2pManager.initialize(this, getMainLooper(), listener);
    }

    private void addLocalService() {
        Map<String, String> record = new HashMap<String, String>();
        record.put(TXTRECORD_PROP_AVAILABLE, "visible");
        record.put(BT_ADDR, myAddr);

        if (service != null) {
            Log.d(TAG, "Local service already added");
            return;
        }
        service = WifiP2pDnsSdServiceInfo.newInstance(
                SERVICE_INSTANCE,
                SERVICE_REG_TYPE, record);


                p2pManager.addLocalService(channel, service, new WifiP2pManager.ActionListener() {

                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "Added Local Service: " + service);
                    }

                    @Override
                    public void onFailure(int error) {
                        Log.d(TAG, "Failed to add a service: " + error);
                    }
                });

    }

    private void removeLocalService() {
        if (service == null) {
            return;
        }

        p2pManager.removeLocalService(channel, service, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                Log.d(TAG, "Removed Local Service");
                service = null;
            }

            @Override
            public void onFailure(int error) {
                Log.d(TAG, "Failed to remove a service: " + error);
            }
        });
    }

    void registerWifiP2pReceiver() {
        // registering multiple receivers causes multiple Intents to be
        // dispatched.
        // make sure single receiver is registered at a time
        if (p2PReceiver == null) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION);
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
            intentFilter
                    .addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
            intentFilter
                    .addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

            Log.d(TAG, "Broadcast receiver registered");
            p2PReceiver = new WifiP2pBroadcastReceiver(p2pManager, channel, this);
            registerReceiver(p2PReceiver, intentFilter);
        }
    }

    void unregisterWifiP2pReceiver() {
        if (p2PReceiver != null) {
            unregisterReceiver(p2PReceiver);
        }
    }

    private void initDnsSd() {
        p2pManager.setDnsSdResponseListeners(channel,
                new WifiP2pManager.DnsSdServiceResponseListener() {

                    @Override
                    public void onDnsSdServiceAvailable(String instanceName,
                                                        String registrationType, final WifiP2pDevice srcDevice) {

                        // A service has been discovered. Is this our app?

                        if (instanceName
                                .equalsIgnoreCase(SERVICE_INSTANCE)) {
                                Log.d(TAG, "onBonjourServiceAvailable "
                                        + instanceName + " from " + srcDevice.deviceName + ", btAddr = " + hostAddr);
                            final AlertDialog.Builder alertDialog=new AlertDialog.Builder(MainActivity.this);


                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    //
                                    //alertDialog.setIcon(R.drawable.icon);   //アイコン設定
                                    alertDialog.setTitle("Instant HotSpot");      //タイトル設定
                                    alertDialog.setMessage("Pair with " + srcDevice.deviceName);  //内容(メッセージ)設定

                                    alertDialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            // OKボタン押下時の処理
                                            Log.d("AlertDialog", "Positive which :" + which);
                                            dialog.dismiss();
                                            connect(srcDevice);
                                        }
                                    });

                                    alertDialog.setNegativeButton("No", new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            // NGボタン押下時の処理
                                            Log.d("AlertDialog", "Negative which :" + which);
                                            dialog.dismiss();
                                        }
                                    });
                                    alertDialog.show();
                                }
                            });

                        }

                    }
                }, new WifiP2pManager.DnsSdTxtRecordListener() {

                    /**
                     * A new TXT record is available. Pick up the advertised
                     * buddy name.
                     */
                    @Override
                    public void onDnsSdTxtRecordAvailable(
                            String fullDomainName, Map<String, String> record,
                            WifiP2pDevice device) {
                        Log.d(TAG, "TxtRecord");
                        Log.d(TAG,
                                device.deviceName
                                        + " is "
                                        + record.get(TXTRECORD_PROP_AVAILABLE));
                        String bTAddr = record
                                .get(BT_ADDR);
                        Log.d(TAG, "bTAddr = " + bTAddr);
                        if (bTAddr != null) {
                            hostAddr = bTAddr;
                        }
                        Log.d(TAG, "recrod=" + record.toString());
                    }
                });

        WifiP2pDnsSdServiceRequest serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();
        p2pManager.addServiceRequest(channel, serviceRequest,
                new WifiP2pManager.ActionListener() {

                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "Added service discovery request");
                    }

                    @Override
                    public void onFailure(int arg0) {
                        Log.d(TAG, "Failed adding service discovery request");
                    }
                });

    }

    private void startDiscovery() {
        p2pManager.discoverServices(channel, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                Log.d(TAG, "Service discovery initiated");
            }

            @Override
            public void onFailure(int arg0) {
                Log.d(TAG, "Service discovery failed");

            }
        });
    }

    private void stopDiscovery() {
            p2pManager.stopPeerDiscovery(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Stop Service discovery.");
                }

                @Override
                public void onFailure(int arg0) {
                    Log.d(TAG, "Failed to Stop Service discovery.");
                }
            });
    }

    private void connect(WifiP2pDevice device) {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        // set least inclination to become Group owner for sender
        // to make receiver as a Group owner where Socket Server will be started
        config.groupOwnerIntent = 0;
        config.wps.setup = WpsInfo.PBC;

        Log.d(TAG, "Connecting to " + device);

        p2pManager.connect(channel, config, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                Log.d(TAG, "Connecting to service");
            }

            @Override
            public void onFailure(int errorCode) {
                Log.d(TAG, "Failed connecting to service:" + errorCode);
            }
        });
    }
}
