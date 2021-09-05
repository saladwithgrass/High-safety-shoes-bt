package com.example.fragmentlrn;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

import static android.bluetooth.BluetoothDevice.BOND_BONDED;
import static android.bluetooth.BluetoothDevice.BOND_NONE;
import static android.bluetooth.BluetoothGatt.GATT_SUCCESS;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE;
import static android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
import static android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
import static android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_SIGNED;
import static android.bluetooth.BluetoothProfile.GATT;
import static android.bluetooth.BluetoothProfile.STATE_CONNECTED;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTED;
import static android.content.Context.BLUETOOTH_SERVICE;

public class BluetoothService {

    private final String TAG = "BluetoothService";
    private List<String> targetNames = new ArrayList<>();
    private String lName, rName;

    private final UUID SERVICE_UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    private final UUID RW_CHARACTERISTIC_UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");

    private final int TASK_SCAN = 0;
    private final int TASK_CONNECT = 1;
    private final int TASK_DISCONNECT = 2;
    private final int TASK_SEND = 3;
    private final int TIME_TO_CONNECT_MS = 10000;
    static public final int NOTHING_TO_UPDATE = 0;
    static public final int LEFT_TO_UPDATE = 1;
    static public final int RIGHT_TO_UPDATE = 2;

    private int toUpdate, tasksToCOnnectBonded;

    private Queue<Task> tasks = new ArrayDeque<Task>();
    
    private BluetoothAdapter adapter;
    private BluetoothGatt lGatt, rGatt;
    private BluetoothDevice lBoot, rBoot;
    private BluetoothGattCharacteristic lWriteCharacteristic, lReadCharacteristic, rWriteCharacteristic, rReadCharacteristic;
    private BluetoothLeScanner scanner;
    private BluetoothManager manager;
    private BluetoothGattService lGattService, rGattService;
    public List<BluetoothDevice> lDevicesList, rDevicesList;
    ScanSettings scanSettings = new ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
            .setReportDelay(0)
            .build();


    private final Context context;

    private boolean lConnected, rConnected, lGattCallbackFailed, rGattCallbackFailed, queueBusy, setup, lScan, rScan = false;
    // setup tells whether app is trying to connect to devices, or ready to work with one or both
    private final int delayBonding = 5000;

    private Runnable discoverServicesRunnable;
    private Runnable reconnect;
    private Handler handler;

    private MainActivity getMainActivity() {
        return (MainActivity)context;
    }

    private BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            int bondState = gatt.getDevice().getBondState();

            if (status == GATT_SUCCESS) {
                if (newState == STATE_CONNECTED) {
                    Log.d(TAG, "onConnectionStateChange: connected, processing bonding");
                    if (bondState == BOND_BONDED) {
                        Log.i(TAG, "onConnectionStateChange: successfully connected, proceeding to process");

                        int delayBonded = 1000;

                        if (gatt == lGatt) {
                            lBoot = gatt.getDevice();
                            lScan = false;
                        } else {
                            rBoot = gatt.getDevice();
                            rScan = false;
                        }

                        discoverServicesRunnable = new Runnable() {
                            @Override
                            public void run() {
                                Log.d(TAG, "run: starting service discovery after delay");
                                boolean gattResult = gatt.discoverServices();

                                if(!gattResult) {
                                    Log.e(TAG, "run: service discovery failed");
                                }
                                if (gatt == lGatt) {
                                    lConnected = true;
                                    getMainActivity().setLeftPairedBtnMode(MainActivity.MODE_CONNECTED);
                                    if (rConnected) {
                                        ((MainActivity)context).pairingFinished();
                                    }
                                } else {
                                    rConnected = true;
                                    getMainActivity().setRightPairedBtnMode(MainActivity.MODE_CONNECTED);
                                    if (lConnected) {
                                        ((MainActivity)context).pairingFinished();
                                    }
                                }
                                discoverServicesRunnable = null;
                            }
                        };
                        completeTask();
                        handler.postDelayed(discoverServicesRunnable, delayBonded);

                    } else if (bondState == BOND_NONE) {
                        Log.d(TAG, "onConnectionStateChange: no bond found, bonding");

                        if (gatt.getDevice().createBond()) {
                            Log.i(TAG, "onConnectionStateChange: successfully bonded");
                            onConnectionStateChange(gatt, status, newState);
                        } else {
                            Log.e(TAG, "onConnectionStateChange: failed to bond");
                        }

                    } else {
                        Log.d(TAG, "onConnectionStateChange: bonding in process, wait");

                        reconnect = new Runnable() {
                            @Override
                            public void run() {
                                onConnectionStateChange(gatt, status, newState);
                                reconnect = null;
                            }
                        };

                        handler.postDelayed(reconnect, delayBonding);

                    }

                } else if (newState == STATE_DISCONNECTED) {
                    Log.i(TAG, "onConnectionStateChange: successfully disconnected");
                    gatt.close();

                } else {
                    Log.d(TAG, "onConnectionStateChange: disconnection or connection in process");
                }
            } else {
                Log.e(TAG, "onConnectionStateChange: gatt failure, something went wrong, code: " + status);
                gatt.close();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);

            if (status != GATT_SUCCESS) {
                Log.e(TAG, "onServicesDiscovered: error occurred: " + status );
                completeTask();
                return;
            }

            BluetoothGattService desirableService = null;

            List<BluetoothGattService> services =  gatt.getServices();
            for (BluetoothGattService service : services) {
                if (service.getUuid().equals(SERVICE_UUID)) {
                    desirableService = service;
                    Log.i(TAG, "onServicesDiscovered: desirable service found, proceeding");
                    break;
                }
            }
            if(desirableService == null) {
                Log.e(TAG, "onServicesDiscovered: desirable service not found" );
                return;
            }

            if (gatt == lGatt) {
                lGattService = desirableService;
                lReadCharacteristic = lWriteCharacteristic = null;
                lReadCharacteristic = lWriteCharacteristic = lGattService.getCharacteristic(RW_CHARACTERISTIC_UUID);
                gatt.setCharacteristicNotification(lReadCharacteristic, true);
                lConnected = true;
                if (lReadCharacteristic == null) {
                    Log.e(TAG, "onServicesDiscovered: could not find left rw characteristics" );
                    lConnected = false;
                }

            } else {
                rGattService = desirableService;
                rReadCharacteristic = rWriteCharacteristic = null;
                rReadCharacteristic = rWriteCharacteristic = rGattService.getCharacteristic(RW_CHARACTERISTIC_UUID);
                gatt.setCharacteristicNotification(rReadCharacteristic, true);
                rConnected = true;
                if (rReadCharacteristic == null) {
                    Log.e(TAG, "onServicesDiscovered: could not find right rw characteristics" );
                    rConnected = false;
                }


            }
            if (lConnected && rConnected) {
                setup = false;
            }
            completeTask();
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);

        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            Log.d(TAG, "onCharacteristicChanged: characteristic changed");

            if (gatt == null) {
                Log.e(TAG, "onCharacteristicChanged: gatt is null");
                return;
            }

            BluetoothGattCharacteristic toCompare = gatt == lGatt ? lReadCharacteristic : rReadCharacteristic;

            if (characteristic != toCompare) {
                Log.e(TAG, "onCharacteristicChanged: some weird characteristic, ignore" );
                return;
            }

            String message;
            message = characteristic.getStringValue(0);
            Log.d(TAG, "onCharacteristicChanged: received message: " + message);
            ((MainActivity)context).parseAndExecute(message);
            completeTask();
        }

    };

    private ScanCallback scanCallBack = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();

            if (device == null) {
                Log.e(TAG, "onScanResult: device is null");
                return;
            }

            if (lDevicesList.contains(device) || rDevicesList.contains(device)) return;

            Log.d(TAG, "onScanResult: device found");

            if (checkShoeName(device.getName())) {
                if (device.getName().contains(lName)) {
                    lDevicesList.add(device);
                    Log.d(TAG, "onScanResult: left device found: " + device.getName() + " " + device.getAddress());
                } else {
                    rDevicesList.add(device);
                    Log.d(TAG, "onScanResult: right device found: " + device.getName() + " " + device.getAddress());
                }
                update();
            }

        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.e(TAG, "onScanFailed: scan failed: " + errorCode );
        }
    };

    private boolean checkShoeName(String name) {
        if (name == null) {
            Log.e(TAG, "checkShoeName: name is null" );
            return false;
        }
        for (String key : targetNames) {
            if (name.contains(key)) {
                return true;
            }
        }
        return false;
    }


    public BluetoothService(Context context_p) {
        context = context_p;
        lName = context.getResources().getString(R.string.leftBootName);
        rName = context.getResources().getString(R.string.rightBootName);
        manager = (BluetoothManager)context.getSystemService(BLUETOOTH_SERVICE);
        adapter = BluetoothAdapter.getDefaultAdapter();
        lBoot = rBoot = null;
        handler = new Handler(Looper.getMainLooper());
        scanner = adapter.getBluetoothLeScanner();
        // Log.d(TAG, "BluetoothService: scanner null: " + (scanner == null));
        lDevicesList = new ArrayList<>();
        rDevicesList = new ArrayList<>();
        targetNames.add(lName);
        targetNames.add(rName);
        lConnected = false;
        rConnected = true;
        setup = true;
        pair();
    }

    private void addTask(Task task){

        tasks.add(task);
        if (tasks.size() == 1) nextTask();
    }

    public boolean pair() {

         List<BluetoothDevice> lBondedList, rBondedList;
         lBondedList = lookForDeviceInBondedByName(lName);
         rBondedList = lookForDeviceInBondedByName(rName);

        Log.d(TAG, "pair: looking in bonded");

         for (BluetoothDevice device : lBondedList) {
            addTask(new TaskConnectLeftIfNeeded(device));
         }

        Log.d(TAG, "pair: " + String.format("found %d devices for left", lBondedList.size()));

         for (BluetoothDevice device : rBondedList) {
            addTask(new TaskConnectRightIfNeeded(device));
         }
         tasksToCOnnectBonded = tasks.size();
         nextTask();
        return true;
    }

    private abstract class Task implements Runnable {
        int code;
        public int getCode() {
            return code;
        }
    }

    private List<BluetoothDevice> lookForDeviceInBondedByName (String name) {
        Set<BluetoothDevice> bonded = adapter.getBondedDevices();
        List<BluetoothDevice> result = new ArrayList<>();
        Log.d(TAG, "lookForDeviceInBondedByName: starting search for: " + name);
        for (BluetoothDevice device : bonded) {
            if (device.getName().contains(name)) {
                result.add(device);
                Log.d(TAG, "lookForDeviceInBondedByName: found: " + device.getName());
            }
        }
        return result;
    }

    private class TaskConnectLeftIfNeeded extends Task {
        private BluetoothDevice device;

        public TaskConnectLeftIfNeeded(BluetoothDevice device) {
            code = TASK_CONNECT;
            this.device = device;
        }

        @Override
        public void run() {
            if (lBoot != null) {
                Log.d(TAG, "run: connectLeftIfNeeded: no need to connect");
                completeTask();
                return;
            }
            Log.d(TAG, "run: connectLeftIfNeeded: connecting to: " + device.getName() + " " +device.getAddress());
            getMainActivity().setLeftPairedBtnMode(MainActivity.MODE_CONNECTING);
            lGatt = device.connectGatt(context, true, bluetoothGattCallback);
            handler.postDelayed(new TaskAbortConnectionIfNeeded(device, lGatt), TIME_TO_CONNECT_MS);

        }
    }

    private class TaskConnectRightIfNeeded extends Task {
        private BluetoothDevice device;

        public TaskConnectRightIfNeeded(BluetoothDevice device) {
            this.device = device;
            code = TASK_CONNECT;
        }

        @Override
        public void run() {
            if (rBoot != null) {
                Log.d(TAG, "run: connectRightIfNeeded: no need to connect");
                completeTask();
                return;
            }
            Log.d(TAG, "run: connectRightIfNeeded: connecting to: " + device.getName() + " " +device.getAddress());
            getMainActivity().setRightPairedBtnMode(MainActivity.MODE_CONNECTING);
            rGatt = device.connectGatt(context, true, bluetoothGattCallback);
            handler.postDelayed(new TaskAbortConnectionIfNeeded(device, lGatt), TIME_TO_CONNECT_MS);

        }
    }

    private void addNewScanTask() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            addTask(new TaskScan21());
        } else {
            addTask(new TaskScan23());
        }
    }

    private class TaskScan23 extends Task {

        public TaskScan23() {
            code = TASK_SCAN;
        }

        @Override
        public void run() {
            Log.d(TAG, "run: starting scan for device, API 23");
            getMainActivity().setRightPairedBtnMode(MainActivity.MODE_SCANNING);
            getMainActivity().setLeftPairedBtnMode(MainActivity.MODE_SCANNING);
            scanner.startScan(null, scanSettings, scanCallBack);
        }
    };

    private class TaskScan21 extends Task {

        public TaskScan21() {
            code = TASK_SCAN;
        }

        @Override
        public void run() {
            Log.d(TAG, "run: starting scan for device, API 21");
        }
    }

    private class TaskSendLeft extends Task {
        
        private byte[] message;
        
        public TaskSendLeft(String msg) {
            code = TASK_SEND;
            message = msg.getBytes();
        }
        
        @Override
        public void run() {
            Log.d(TAG, "TaskSendLeft: sending " + message.toString());
            int writeProperty, writeType = lWriteCharacteristic.getWriteType();
            switch (writeType) {
                case WRITE_TYPE_DEFAULT: writeProperty = PROPERTY_WRITE; break;
                case WRITE_TYPE_NO_RESPONSE : writeProperty = PROPERTY_WRITE_NO_RESPONSE; break;
                case WRITE_TYPE_SIGNED : writeProperty = PROPERTY_SIGNED_WRITE; break;
                default: writeProperty = 0; break;
            }
            Log.d(TAG, "run: write property: " + writeProperty);

            if((lWriteCharacteristic.getProperties() & writeProperty) == 0 ) {
                Log.e(TAG, String.format(Locale.ENGLISH,"ERROR: Characteristic <%s> does not support writeType '%d'", lWriteCharacteristic.getUuid(), writeProperty));
                completeTask();
                return;
            }
            lWriteCharacteristic.setValue(message);
            // lWriteCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            if(!lGatt.writeCharacteristic(lWriteCharacteristic)) {
                Log.e(TAG, "send: failed to write characteristic: " + lWriteCharacteristic.getUuid());
            } else {
                Log.i(TAG, "send: successfully written");
            }
        }
    }
    
    private class TaskSendRight extends Task {
        private byte[] message;

        public TaskSendRight(String msg) {
            code = TASK_SEND;
            message = msg.getBytes();
        }

        @Override
        public void run() {
            if (!rConnected) {
                Log.e(TAG, "run: right not connected, not sending");
            }
            Log.d(TAG, "TaskSendRight: sending " + message.toString());
            Log.d(TAG, "TaskSendLeft: sending " + message.toString());
            int writeProperty, writeType = lWriteCharacteristic.getWriteType();
            switch (writeType) {
                case WRITE_TYPE_DEFAULT: writeProperty = PROPERTY_WRITE; break;
                case WRITE_TYPE_NO_RESPONSE : writeProperty = PROPERTY_WRITE_NO_RESPONSE; break;
                case WRITE_TYPE_SIGNED : writeProperty = PROPERTY_SIGNED_WRITE; break;
                default: writeProperty = 0; break;
            }
            Log.d(TAG, "run: write property: " + writeProperty);

            if((rWriteCharacteristic.getProperties() & writeProperty) == 0 ) {
                Log.e(TAG, String.format(Locale.ENGLISH,"ERROR: Characteristic <%s> does not support writeType '%d'", rWriteCharacteristic.getUuid(), writeProperty));
                completeTask();
                return;
            }
            rWriteCharacteristic.setValue(message);
            // lWriteCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            if(!rGatt.writeCharacteristic(rWriteCharacteristic)) {
                Log.e(TAG, "send: failed to write characteristic: " + rWriteCharacteristic.getUuid());
            } else {
                Log.i(TAG, "send: successfully written");
            }
        }
    }
    
    public void send(String message) {
        Log.d(TAG, "send: message");
        addTask(new TaskSendLeft(message));
        addTask(new TaskSendRight(message));
    }
    
    private class TaskAbortConnectionIfNeeded implements Runnable {

        private BluetoothDevice device;
        private BluetoothGatt gatt;

        public TaskAbortConnectionIfNeeded(BluetoothDevice device, BluetoothGatt gatt) {
            this.device = device;
            this.gatt = gatt;
        }

        @Override
        public void run() {
            if (manager.getConnectionState(device, GATT) == STATE_DISCONNECTED) {
                Log.d(TAG, "run: aborting connection to device: " + device.getName());
                gatt.close();
                completeTask();
                return;
            }
            if (gatt == lGatt) {
                lConnected = true;
                getMainActivity().setLeftPairedBtnMode(MainActivity.MODE_CONNECTED);
            } else {
                rConnected = true;
                getMainActivity().setRightPairedBtnMode(MainActivity.MODE_CONNECTED);
            }
            if (lConnected && rConnected) {
                setup = false;
                Log.d(TAG, "run: setup finished");
            }
            Log.d(TAG, "run: no need to abort, device connected");
        }
    };

    private void nextTask(){
        if (queueBusy) {
            return;
        }

        Log.d(TAG, "nextTask: proceeding to next task");
        if (tasks.size() == 0 && setup) {
            Log.d(TAG, "nextTask: failed to connect from bonded, scanning, adding scan task");
            addNewScanTask();
            nextTask();
            return;
        }

        if (tasks.size() > 0) {
            final Runnable task = tasks.peek();
            queueBusy = true;
            handler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        task.run();
                    } catch ( Exception ex) {
                        Log.e(TAG, "run: " + ex.getMessage());
                    }
                }
            });

        }
    }

    private void completeTask() {
        Log.d(TAG, "completedCommand: task completed");
        queueBusy = false;
        tasks.poll();
        nextTask();
    }

    private void removeAllTasksByType(Class type) {

        Queue<Task> result = new ArrayDeque<Task>();

        while (tasks.size() > 0) {
            if (tasks.peek().getClass() != type) {
                result.add(tasks.peek());
            } else {
                Log.d(TAG, "removeAllTasksByType: task removed");
            }
            tasks.poll();
        }
        tasks = result;

    }

    private String taskCodeToString (int code) {
        String res;
        switch (code) {
            case 0: return "scan task";
            case 1: return "connect task";
            case 2: return "disconnect task";
            case 3: return "send task";
            default: return "unknown";
        }
    }

    public void setToUpdate(int newVal) {
        toUpdate = newVal;
    }

    private void update() {
        if (toUpdate == NOTHING_TO_UPDATE) {
            Log.e(TAG, "update: update on nothing to update" );
            return;
        }
        if (toUpdate == LEFT_TO_UPDATE) {
            getMainActivity().setLeftPairedBtnMode(MainActivity.MODE_SCANNED);
        } else {
            getMainActivity().setRightPairedBtnMode(MainActivity.MODE_SCANNED);
        }
    }

    public List<BluetoothDevice> getLeftDeviceList() {
        return lDevicesList;
    }

    public void addConnectTask(BluetoothDevice device) {
        if (!checkShoeName(device.getName())) {
            Log.e(TAG, "addConnectTask: wrong device, not connecting");
            return;
        }
        if (device.getName().contains(lName)) {
            Log.d(TAG, "addConnectTask: adding new task to connect to left: " + device.getName() + " " + device.getAddress());
            addTask(new TaskConnectLeftIfNeeded(device));
        } else {
            Log.d(TAG, "addConnectTask: adding new task to connect to left: " + device.getName() + " " + device.getAddress());
            addTask(new TaskConnectRightIfNeeded(device));
        }
    }

    public void setLeftChosen() {
        lScan = false;
        if (!rScan && tasks.peek().getCode() == TASK_SCAN){
            Log.d(TAG, "setLeftChosen: no need to scan for devices, completing");
            setup = false;
            getMainActivity().pairingFinished();
            completeTask();
        }
    }

    public void setRightChosen(){
        rScan = false;
        if (!lScan && tasks.peek().getCode() == TASK_SCAN) {
            Log.d(TAG, "setLeftChosen: no need to scan for devices, completing");
            setup = false;
            getMainActivity().pairingFinished();
            completeTask();
        }
    }



}
