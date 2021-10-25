package com.example.fragmentlrn;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
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
    private boolean IsScanning = false;
    private Handler handler;


    BluetoothScanner(Context context, BluetoothAdapter adapter) {
        Log.d(TAG, "BluetoothScanner: initialization");
        this.context = context;
        lName = context.getString(R.string.leftBootName);
        rName = context.getString(R.string.rightBootName);
        leftSavedMac = mainActivity().getLMac();
        rightSavedMac = mainActivity().getRMac();
        scanner = adapter.getBluetoothLeScanner();
        handler = new Handler();
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

            if (device == null || device.getName() == null) {
                // Log.e(TAG, "onScanResult: device is null");
                return;
            }
            // Log.d(TAG, "onScanResult: device found " + device.getName() + " " + device.getAddress());
            if (lDevicesList.contains(device) || rDevicesList.contains(device) || scannedDevices.contains(device)) return;

            scannedDevices.add(device);



            if (device.getName() == null) {
                // Log.e(TAG, "onScanResult: device name is null");
                return;
            }

            onDeviceFound(device);

        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.e(TAG, "onScanFailed: scan failed: "  + errorCode);
        }
    };

    public void startScan(int timeout_ms) {
        Log.d(TAG, "startScan: starting");
        if (!isScanning()) {
            lDevicesList.clear();
            rDevicesList.clear();
            scannedDevices.clear();
            if (mainActivity().getLeftBoot() != null) {
                lDevicesList.add(mainActivity().getLeftBoot());
                scannedDevices.add(mainActivity().getLeftBoot());
                Log.d(TAG, "startScan: left device added");
                // mainActivity().updateSelected(mainActivity().getLeftBoot());
            }
            if (mainActivity().getRightBoot() != null) {
                rDevicesList.add(mainActivity().getRightBoot());
                scannedDevices.add(mainActivity().getRightBoot());
                Log.d(TAG, "startScan: right device added");
                // mainActivity().updateSelected(mainActivity().getRightBoot());
            }
        } else {
            Log.e(TAG, "startScan: already scanning");
            return;
        }
        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            startScan21();
        } else {
            startScan23();
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                stopScan();
            }
        }, timeout_ms);
    }

    public void startScan() {
        Log.d(TAG, "startScan: starting");
        if (!isScanning()) {
            lDevicesList.clear();
            rDevicesList.clear();
            scannedDevices.clear();
            if (mainActivity().getLeftBoot() != null) {
                lDevicesList.add(mainActivity().getLeftBoot());
                scannedDevices.add(mainActivity().getLeftBoot());
                Log.d(TAG, "startScan: left device added");
                // mainActivity().updateSelected(mainActivity().getLeftBoot());
            }
            if (mainActivity().getRightBoot() != null) {
                rDevicesList.add(mainActivity().getRightBoot());
                scannedDevices.add(mainActivity().getRightBoot());
                Log.d(TAG, "startScan: right device added");
                // mainActivity().updateSelected(mainActivity().getRightBoot());
            }
        } else {
            Log.e(TAG, "startScan: already scanning");
            return;
        }
        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            startScan21();
        } else {
            startScan23();
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                stopScan();
            }
        }, 30000);
    }

    private void startScan21() {
        Log.d(TAG, "startScan21: starting");
        scanner.startScan(scanCallBack);
        IsScanning = true;

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
        IsScanning = true;
    }

    public void stopScan() {
        Log.d(TAG, "stopScan: stopping");
        IsScanning = false;
        scanner.stopScan(scanCallBack);
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

    private void onDeviceFound(BluetoothDevice device){
        if (device.getName().contains(lName)) {
            Log.d(TAG, "onScanResult: left device found: " + device.getName());
            lDevicesList.add(device);
            mainActivity().updateList();
            if (device.getAddress().equals(leftSavedMac)) {
                Log.d(TAG, "onScanResult: left saved mac " + leftSavedMac + " found");
                if (device.getAddress().equals(rightSavedMac)) {
                    Log.e(TAG, "onScanResult: left mac also equals right, cleaning right mac" );
                    mainActivity().rememberRightAddress("");
                }
                connectLeft(device);
            } else {
                Log.d(TAG, "onDeviceFound: thats none of saved macs");
            }
        } else if (device.getName().contains(rName)) {
            Log.d(TAG, "onScanResult: right device found: " + device.getName());
            rDevicesList.add(device);
            mainActivity().updateList();
            if (device.getAddress().equals(rightSavedMac)) {
                Log.d(TAG, "onScanResult: right saved mac " + rightSavedMac + " found");
                if (device.getAddress().equals(leftSavedMac)) {
                    Log.e(TAG, "onScanResult: right mac also equals left, cleaning left mac");
                    mainActivity().rememberLeftAddress("");
                }
                connectRight(device);
            } else {
                Log.d(TAG, "onDeviceFound: thats none of the saved macs");
            }
        } else {
            Log.e(TAG, "onScanResult: name is wrong "  + device.getName());
        }
    }

    public void notFoundDeviceConnected(BluetoothDevice device) {
        Log.d(TAG, "notFoundDeviceConnected: " + device.getName());
        if (device.getName().contains(lName)) {
            if (lDevicesList.contains(device)) return;
            lDevicesList.add(device);
            scannedDevices.add(device);
        } else {
            if (rDevicesList.contains(device)) return;
            rDevicesList.add(device);
            scannedDevices.add(device);
        }
        mainActivity().updateList();
    }

    public boolean isScanning() {
        return IsScanning;
    }
}
