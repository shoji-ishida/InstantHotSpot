package jp.nemustech.instanthotspot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.util.Log;

import java.util.Collection;


/**
 * Created by ishida on 15/02/15.
 */
public class WifiP2pBroadcastReceiver extends BroadcastReceiver {

    static private final String TAG = "WifiP2pBroadcastReceiver";
    private WifiP2pManager manager;
    private Channel channel;
    private Context context;

    public WifiP2pBroadcastReceiver(WifiP2pManager manager, Channel channel, Context context) {
        super();
        this.manager = manager;
        this.channel = channel;
        this.context = context;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (manager == null) {
            return;
        }
        String action = intent.getAction();

        switch (action) {
            case WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION:
                handleConnectionChangedAction(intent);
                break;
            case WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION:
                handleThisDeviceChangedAction(intent);
                break;
            case WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION:
                handleStateChangedAction(intent);
                break;
            case WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION:
                handlePeersChangedAction(intent);
                break;
            case WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION:
                handleDiscoveryChangedAction(intent);
                break;
            default:
                Log.d(TAG, "Unhandled Broadcast intent: " + action);
        }
    }

    private void handleConnectionChangedAction(Intent intent) {
        NetworkInfo networkInfo = (NetworkInfo) intent
                .getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
        Log.d(TAG, "Connection changed: " + networkInfo);

    }

    private void handleThisDeviceChangedAction(Intent intent) {
        WifiP2pDevice device = (WifiP2pDevice) intent
                .getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
        Log.d(TAG, "Device status = " + device.status);
    }

    private void handleStateChangedAction(Intent intent) {
        int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
        String stateMsg = (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) ? "Enabled" : "Disabled";
        Log.d(TAG, "P2P state changed - " + stateMsg);
    }

    private void handlePeersChangedAction(Intent intent) {
        WifiP2pDeviceList deviceList = (WifiP2pDeviceList) intent
                .getParcelableExtra(WifiP2pManager.EXTRA_P2P_DEVICE_LIST);
        StringBuffer strBuf = new StringBuffer();
        Collection<WifiP2pDevice> devices = deviceList.getDeviceList();
        for (WifiP2pDevice device: devices) {
            strBuf.append("{" + device.deviceName + " " + device.status + "},");
        }
        Log.d(TAG, "P2P Device: " + strBuf);
    }

    private void handleDiscoveryChangedAction(Intent intent) {
        int state = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, -1);
        switch (state) {
            case WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED:
                Log.d(TAG, "P2P discovery started");
                break;
            case WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED:
                Log.d(TAG, "P2P discovery stopped");
                break;
            default:
                Log.d(TAG, "P2P discovery unknown state");
        }
    }
}
