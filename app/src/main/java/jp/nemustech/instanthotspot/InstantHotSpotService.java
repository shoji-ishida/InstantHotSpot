package jp.nemustech.instanthotspot;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.util.Log;

import java.lang.reflect.Method;

public class InstantHotSpotService extends Service {
    private static final String TAG = InstantHotSpotService.class.getSimpleName();

    private BluetoothManager bTManager;
    private BluetoothAdapter bTAdapter;


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
    }

    private void setWifiTetheringEnabled(boolean enable) {
        WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);

        Method[] methods = wifiManager.getClass().getDeclaredMethods();
        for (Method method : methods) {
            if (method.getName().equals("setWifiApEnabled")) {
                try {
                    method.invoke(wifiManager, null, enable);
                } catch (Exception ex) {
                }
                break;
            }
        }
    }
}
