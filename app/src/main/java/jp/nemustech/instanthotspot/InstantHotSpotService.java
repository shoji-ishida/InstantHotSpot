package jp.nemustech.instanthotspot;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.util.Log;

import java.lang.reflect.Method;

public class InstantHotSpotService extends Service {
    private static final String TAG = InstantHotSpotService.class.getSimpleName();

    private BluetoothManager bTManager;
    private BluetoothAdapter bTAdapter;
    private BluetoothGattServer bTGattServer;

    private WifiManager wifiManager;
    private Method setWifiApEnabled;
    private Method getWifiApConfiguration;
    private Method setWifiApConfiguration;
    private Method isWifiApEnabled;

    private InstantHotSpotGattServer gattServer;

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");

        // initialize
        bTManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bTAdapter = bTManager.getAdapter();

        initWifiMethods();

        gattServer = new InstantHotSpotGattServer(this, bTManager, bTAdapter);
        gattServer.startGattServer();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "start command:" + intent);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        if (gattServer != null) {
            gattServer.startGattServer();
            gattServer = null;
        }
    }

    private void initWifiMethods() {
        wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);

        Method[] methods = wifiManager.getClass().getDeclaredMethods();
        for (Method method : methods) {
            if (method.getName().equals("setWifiApEnabled")) {
                setWifiApEnabled = method;
            } else if (method.getName().equals("getWifiApConfiguration")) {
                getWifiApConfiguration = method;
            } else if (method.getName().equals("setWifiApConfiguration")) {
                setWifiApConfiguration = method;
            } else if (method.getName().equals("isWifiApEnabled")) {
                isWifiApEnabled = method;
            }
        }
    }

    void setWifiTetheringEnabled(boolean enable) {
        try {
            WifiConfiguration config = (WifiConfiguration) getWifiApConfiguration.invoke(wifiManager);
            setWifiApEnabled.invoke(wifiManager, config, enable);
        } catch (Exception ex) {
            ex.printStackTrace();
        };
    }

}
