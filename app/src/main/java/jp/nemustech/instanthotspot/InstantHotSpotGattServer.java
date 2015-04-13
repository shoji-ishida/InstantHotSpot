package jp.nemustech.instanthotspot;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;
import android.widget.Toast;

import java.util.UUID;

/**
 * Created by ishida on 2015/04/06.
 */
public class InstantHotSpotGattServer {
    private static final String TAG = InstantHotSpotGattServer.class.getSimpleName();
    //public static final String DONE = "DONE";

    static final UUID service_uuid = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb");
    static final UUID field1_characteristic_uuid = UUID.fromString("00002a49-0000-1000-8000-00805f9b34fb");
    static final UUID field2_characteristic_uuid = UUID.fromString("00002a59-0000-1000-8000-00805f9b34fb");

    private BluetoothManager bTManager;
    private BluetoothAdapter bTAdapter;
    private BluetoothGattServer gattServer;
    private BluetoothGattServerCallback gattCallback;
    private BluetoothGattService gattService;
    private Context context;

    public InstantHotSpotGattServer(Context context, BluetoothManager manager, BluetoothAdapter adapter) {
        this.context = context;
        this.bTManager = manager;
        this.bTAdapter = adapter;

        init();
    }

    private void init() {
        // BLE check
        if (!isBLESupported()) {
            Toast.makeText(context, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            Log.d(TAG, context.getString(R.string.ble_not_supported));
            return;
        }

        gattCallback = new BluetoothGattServerCallback() {
            @Override
            public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
                Log.d(TAG, "onConnectionStateChange: " + device.getName() + " status=" + status + "->" + newState);
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    // if someone connects then we should stop BLE adv here
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    // maybe need to clean up staff
                }

            }

            @Override
            public void onServiceAdded(int status, BluetoothGattService service) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "onServiceAdded: status=GATT_SUCCESS service="
                            + service.getUuid().toString());
                    gattService = service;
                } else {
                    Log.d(TAG, "onServiceAdded: status!=GATT_SUCCESS");
                }

            }

            @Override
            public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
                Log.d(TAG, "onCharacteristicReadRequest: requestId=" + requestId + " offset=" + offset);
                Log.d(TAG, "uuid: " + characteristic.getUuid().toString());
                if (!characteristic.getService().getUuid().equals(service_uuid)) {
                    Log.d(TAG, "Different service uuid ignored.");
                    return;
                }
                String value = null;
                if (characteristic.getUuid().equals(field1_characteristic_uuid)) {
                    value = ((InstantHotSpotService) context).getWifiTetheringSSID();
                } else if (characteristic.getUuid().equals(field2_characteristic_uuid)) {
                    value = ((InstantHotSpotService) context).getWifiTetheringPreSharedKey();
                } else {
                    Log.d(TAG, "Unknown characteristic");
                    return;
                }
                characteristic.setValue(value);
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic.getValue());
            }

            @Override
            public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
                // enable WiFi Ap
                ((InstantHotSpotService)context).setWifiTetheringEnabled(true);
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);
            }
        };
    }

    /** check if BLE Supported device */
    private boolean isBLESupported() {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    public void startGattServer() {
        gattServer = bTManager.openGattServer(context, gattCallback);

        BluetoothGattService gs = new BluetoothGattService(
                service_uuid, BluetoothGattService.SERVICE_TYPE_PRIMARY
        );

        BluetoothGattCharacteristic gc1 = new BluetoothGattCharacteristic(
                field1_characteristic_uuid, BluetoothGattCharacteristic.PROPERTY_READ|BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ|BluetoothGattCharacteristic.PERMISSION_WRITE
        );

        BluetoothGattCharacteristic gc2 = new BluetoothGattCharacteristic(
                field2_characteristic_uuid, BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
        );

        gs.addCharacteristic(gc1);
        gs.addCharacteristic(gc2);
        gattServer.addService(gs);
    }

    public void stopGattServer() {
        if (gattServer != null) {
            if (gattService != null) {
                gattServer.removeService(gattService);
                gattService = null;
            }
            gattServer.close();
            gattServer = null;
        }
    }
}
