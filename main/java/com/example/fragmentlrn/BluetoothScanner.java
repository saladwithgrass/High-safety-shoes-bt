package com.example.fragmentlrn;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class BluetoothScanner {

    private BluetoothLeScanner scanner;
    private String TAG = "BluetoothScanner";
    private String lName, rName, leftSavedMac, rightSavedMac;
    private Context context;
    private List<BluetoothDevice> lDevicesList = new ArrayList<>(),
            rDevicesList = new ArrayList<>(),
            scannedDevices = new ArrayList<>();


    BluetoothScanner(Context context, BluetoothAdapter adapter) {
        Log.d(TAG, "BluetoothScanner: initialization");
        this.context = context;
        lName = context.getString(R.string.leftBootName);
        rName = context.getString(R.string.rightBootName);
        leftSavedMac = mainActivity().getLMac();
        rightSavedMac = mainActivity().getRMac();
        scanner = adapter.getBluetoothLeScanner();
    }

    private MainActivity mainActivity() {
        return (MainActivity)context;
    }

    private ScanCallback scanCallBack = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            // Log.d(TAG, "onScanResult: arrived");
            BluetoothDevice device = result.getDevice();

            if (device == null) {
                // Log.e(TAG, "onScanResult: device is null");
                return;
            }

            if (lDevicesList.contains(device) || rDevicesList.contains(device) || scannedDevices.contains(device)) return;

            scannedDevices.add(device);

            // Log.d(TAG, "onScanResult: device found " + device.getName() + " " + device.getAddress());

            if (device.getName() == null) {
                // Log.e(TAG, "onScanResult: device name is null");
                return;
            }

            if (device.getName().contains(lName)) {
                Log.d(TAG, "onScanResult: left device found: " + device.getName());
                lDevicesList.add(device);
                mainActivity().updateList();
            } else if (device.getName().contains(rName)) {
                Log.d(TAG, "onScanResult: right device found: " + device.getName());
                rDevicesList.add(device);
                mainActivity().updateList();
            } else {
                Log.e(TAG, "onScanResult: name is wrong "  + device.getName());
                return;
            }
            if (device.getAddress().equals(leftSavedMac) && device.getName().contains(lName)) {
                Log.d(TAG, "onScanResult: left saved mac " + leftSavedMac + " found");
                if (device.getAddress().equals(rightSavedMac)) {
                    Log.e(TAG, "onScanResult: left mac also equals right, cleaning right mac" );
                    mainActivity().rememberRightAddress("");
                }
                connectLeft(device);
            } else if (device.getAddress().equals(rightSavedMac) && device.getName().contains(rName)) {
                Log.d(TAG, "onScanResult: right saved mac " + rightSavedMac + " found");
                if (device.getAddress().equals(leftSavedMac)) {
                    Log.e(TAG, "onScanResult: right mac also equals left, cleaning left mac");
                    mainActivity().rememberLeftAddress("");
                }
                connectRight(device);
            } else {
                Log.d(TAG, "onScanResult: that's none of the saved macs");
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.e(TAG, "onScanFailed: scan failed: "  + errorCode);
        }
    };

    public void startScan() {
        Log.d(TAG, "startScan: starting");
        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            startScan21();
        } else {
            startScan23();
        }
    }

    private void startScan21() {
        Log.d(TAG, "startScan21: starting");
        scanner.startScan(scanCallBack);
    }

    private void startScan23() {
        Log.d(TAG, "startScan23: starting");
        ScanSettings scanSettings = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            scanSettings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                    .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                    .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
                    .setReportDelay(0)
                    .build();
        }
        scanner.startScan(null, scanSettings, scanCallBack);
    }

    private void connectLeft(BluetoothDevice device) {
        Log.d(TAG, "connectLeft: connecting device: " + device.getName());
        mainActivity().serialSocketConnectLeft(device);
    }

    private void connectRight(BluetoothDevice device) {
        Log.d(TAG, "connectRight: connecting device: " + device.getName());
        mainActivity().serialSocketConnectRight(device);
    }

    public List<BluetoothDevice> getLeftDevicesList () {
        return lDevicesList;
    }

    public List<BluetoothDevice> getRightDevicesList() {
        return rDevicesList;
    }

}
