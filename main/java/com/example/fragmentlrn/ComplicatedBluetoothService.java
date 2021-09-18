
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

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
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

public class ComplicatedBluetoothService {

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

    private final int STAGE_DISCONNECTED = 0;
    private final int STAGE_LOOKING_IN_BONDED = 1;
    private final int STAGE_CONNECTING = 2;
    private final int STAGE_SCANNING = 3;
    private final int STAGE_FUNCTIONING = 4;
    private final int STAGE_FAILED_TO_CONNECT = 5;

    private final int scan_timeout_ms = 30000;
    private int leftStage, rightStage;

    private int toUpdate;

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

    private boolean lConnected, rConnected, lGattCallbackFailed, rGattCallbackFailed, queueBusy, setup, lScan, rScan = false, holdOn = false;
    // setup tells whether app is trying to connect to devices, or ready to work with one or both
    private final int delayBonding = 5000;

    private Runnable discoverServicesRunnable;
    private Runnable reconnect;
    private Handler handler;

    private MainActivity mainActivity() {
        return (MainActivity) context;
    }

    private boolean checkShoeName(String name) {
        if (name == null) {
            Log.e(TAG, "checkShoeName: name is null");
            return false;
        }
        for (String key : targetNames) {
            if (name.contains(key)) {
                return true;
            }
        }
        return false;
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

                                if (!gattResult) {
                                    Log.e(TAG, "run: service discovery failed");
                                }
                                if (gatt == lGatt) {
                                    lConnected = true;
                                    mainActivity().setLeftPairedBtnMode(MainActivity.MODE_CONNECTED);
                                    if (rConnected) {
                                        ((MainActivity) context).pairingFinished();
                                    }
                                } else {
                                    rConnected = true;
                                    mainActivity().setRightPairedBtnMode(MainActivity.MODE_CONNECTED);
                                    if (lConnected) {
                                        ((MainActivity) context).pairingFinished();
                                    }
                                }
                                discoverServicesRunnable = null;
                            }
                        };
                        handler.postDelayed(discoverServicesRunnable, delayBonded);
                        tasks.peek().satisfy();
                        Log.d(TAG, "onConnectionStateChange: callback completes task");
                        completeTask();

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
                Log.e(TAG, "onServicesDiscovered: error occurred: " + status);
                completeTask();
                return;
            }

            BluetoothGattService desirableService = null;

            List<BluetoothGattService> services = gatt.getServices();
            for (BluetoothGattService service : services) {
                if (service.getUuid().equals(SERVICE_UUID)) {
                    desirableService = service;
                    Log.i(TAG, "onServicesDiscovered: desirable service found, proceeding");
                    break;
                }
            }
            if (desirableService == null) {
                Log.e(TAG, "onServicesDiscovered: desirable service not found");
                return;
            }

            if (gatt == lGatt) {
                lGattService = desirableService;
                lReadCharacteristic = lWriteCharacteristic = null;
                lReadCharacteristic = lWriteCharacteristic = lGattService.getCharacteristic(RW_CHARACTERISTIC_UUID);
                gatt.setCharacteristicNotification(lReadCharacteristic, true);
                if (lReadCharacteristic == null) {
                    Log.e(TAG, "onServicesDiscovered: could not find left rw characteristics");
                }
                mainActivity().setLeftPairedBtnMode(MainActivity.MODE_CONNECTED);
                lConnected = true;
            } else {
                rGattService = desirableService;
                rReadCharacteristic = rWriteCharacteristic = null;
                rReadCharacteristic = rWriteCharacteristic = rGattService.getCharacteristic(RW_CHARACTERISTIC_UUID);
                gatt.setCharacteristicNotification(rReadCharacteristic, true);
                if (rReadCharacteristic == null) {
                    Log.e(TAG, "onServicesDiscovered: could not find right rw characteristics");
                }
                mainActivity().setRightPairedBtnMode(MainActivity.MODE_CONNECTED);
                rConnected = true;
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
                Log.e(TAG, "onCharacteristicChanged: some weird characteristic, ignore");
                return;
            }

            String message;
            message = characteristic.getStringValue(0);
            Log.d(TAG, "onCharacteristicChanged: received message: " + message);
            ((MainActivity) context).parseAndExecute(message);
            Log.d(TAG, "onCharacteristicChanged: completes");
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

            if (device.getName().contains(lName)) {
                Log.d(TAG, "onScanResult: left device found: " + device.getName());
                lDevicesList.add(device);
                mainActivity().updateList();
            } else if (device.getName().contains(rName)) {
                Log.d(TAG, "onScanResult: right device found: " + device.getName());
                rDevicesList.add(device);
            } else {
            }

        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.e(TAG, "onScanFailed: scan failed: " + errorCode);
        }
    };


    private void checkBondedRemoteDevices() {
        for (BluetoothDevice device : adapter.getBondedDevices()) {
            Log.d(TAG, "checkBondedRemoteDevices: " + device.getName() + ' ' + (adapter.getRemoteDevice(device.getAddress()) == null));
        }
    }

    public ComplicatedBluetoothService(Context context_p) {
        Log.d(TAG, "BluetoothService: created");
        setLeftStage(STAGE_DISCONNECTED);
        setRightStage(STAGE_DISCONNECTED);
        context = context_p;
        lName = context.getResources().getString(R.string.leftBootName);
        rName = context.getResources().getString(R.string.rightBootName);
        manager = (BluetoothManager) context.getSystemService(BLUETOOTH_SERVICE);
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
        rConnected = false;
        setup = true;
        pair();
    }

    private void addTask(Task task) {

        tasks.add(task);
        if (tasks.size() == 1) nextTask();
    }

    private String stageToString(int stage) {
        String result;
        switch (stage) {
            case STAGE_DISCONNECTED:
                return "DISCONNECTED";
            case STAGE_FUNCTIONING:
                return "FUNCTIONING";
            case STAGE_CONNECTING:
                return "CONNECTING";
            case STAGE_LOOKING_IN_BONDED:
                return "LOOKING IN BONDED";
            case STAGE_FAILED_TO_CONNECT:
                return  "NOT FOUND";
            case STAGE_SCANNING:
                return "SCANNING";
            default: return "UNKNOWN STAGE";
        }
    }

    private void setLeftStage(int stage) {
        Log.d(TAG, "setLeftStage: " + stageToString(stage));
        leftStage = stage;
    }

    private void setRightStage(int stage) {
        Log.d(TAG, "setRightStage: " + stageToString(stage));
        rightStage = stage;
    }

    public boolean pair() {
        List<BluetoothDevice> lBondedList, rBondedList;
        lBondedList = lookForDeviceInBondedByName(lName);
        rBondedList = lookForDeviceInBondedByName(rName);

        Log.d(TAG, "pair: looking in bonded");

        setLeftStage(STAGE_LOOKING_IN_BONDED);
        setRightStage(STAGE_LOOKING_IN_BONDED);

        for (BluetoothDevice device : lBondedList) {
            addTask(new TaskConnectLeftIfNeeded(device));
        }
        Log.d(TAG, "pair: " + String.format("found %d devices for left", lBondedList.size()));



        for (BluetoothDevice device : rBondedList) {
            addTask(new TaskConnectRightIfNeeded(device));
        }
        Log.d(TAG, "pair: " + String.format("found %d devices for right", rBondedList.size()));

        if (lBondedList.size() == 0 || rBondedList.size() == 0) {
            if (lBondedList.size() == 0) {
                Log.d(TAG, "pair: no devices found for left");
                setLeftStage(STAGE_SCANNING);
            }
            if (rBondedList.size() == 0) {
                Log.d(TAG, "pair: no devices found for right");
                setRightStage(STAGE_SCANNING);
            }
            Log.d(TAG, "pair: adding new scan task");
            addNewScanTask();
        }
        nextTask();
        return true;
    }

    public List<BluetoothDevice> getLeftDeviceList() {
        return lDevicesList;
    }

    public List<BluetoothDevice> getRightDeviceList() {
        return rDevicesList;
    }

    private abstract class Task implements Runnable {
        protected int code;
        protected boolean satisfied;
        protected boolean left;

        public Task() {satisfied = false;}

        public int getCode() {
            return code;
        }

        public boolean getLeft() {return left;}

        public void satisfy() {satisfied = true;}

        abstract public void complete();
    }

    private int getLeftStage() {return leftStage; }

    private int getRightStage() {return rightStage; }

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
            this.left = true;
            setLeftStage(STAGE_CONNECTING);
            Log.d(TAG, "run: connectLeftIfNeeded: connecting to: " + device.getName() + " " + device.getAddress());
            mainActivity().setLeftPairedBtnMode(MainActivity.MODE_CONNECTING);


        }

        @Override
        public void complete() {
            Log.d(TAG, "connect left if needed completing " + (satisfied ? "satisfied" : "unsatisfied"));
            for (Task task : tasks) {
                if (task.getCode() == TASK_CONNECT && task.getLeft()) {
                    if (satisfied) {
                        tasks.remove(task);
                        Log.d(TAG, "complete: removing similar obsolete task");
                    } else {
                        Log.d(TAG, "complete: more connect left tasks found, ok");
                        return;
                    }
                }
            }
            if (satisfied) {
                Log.d(TAG, "complete: all obsolete tasks removed");
                setLeftStage(STAGE_FUNCTIONING);
                mainActivity().pairingFinished();
                return;
            } else {
                Log.d(TAG, "complete: none connect left tasks found");
                Log.d(TAG, "complete: stage: " + stageToString(getLeftStage()));
                if (getLeftStage() == STAGE_LOOKING_IN_BONDED) {
                    Log.d(TAG, "complete: adding scan");
                    setLeftStage(STAGE_SCANNING);
                    addNewScanTask();
                } else {
                    if (getLeftStage() == STAGE_CONNECTING) {
                        setLeftStage(STAGE_FAILED_TO_CONNECT);
                    }
                    Log.d(TAG, "complete: no need to scan now");
                }

            }
        }
    }

    private class TaskConnectRightIfNeeded extends Task {
        private BluetoothDevice device;

        public TaskConnectRightIfNeeded(BluetoothDevice device) {
            this.device = device;
            code = TASK_CONNECT;
            this.left = false;
        }

        @Override
        public void run() {
            if (rBoot != null) {
                Log.d(TAG, "run: connectRightIfNeeded: no need to connect");
                completeTask();
                return;
            }
            setRightStage(STAGE_CONNECTING);
            Log.d(TAG, "run: connectRightIfNeeded: connecting to: " + device.getName() + " " + device.getAddress());
            mainActivity().setRightPairedBtnMode(MainActivity.MODE_CONNECTING);
            rGatt = device.connectGatt(context, true, bluetoothGattCallback);
            handler.postDelayed(new TaskAbortConnectionIfNeeded(device, rGatt), TIME_TO_CONNECT_MS);

        }

        @Override
        public void complete() {
            Log.d(TAG, "connect right if needed completing " + (satisfied ? "satisfied" : "unsatisfied"));
            for (Task task : tasks) {
                if (task.getCode() == TASK_CONNECT && !task.getLeft() && task != this) {
                    if (satisfied) {
                        tasks.remove(task);
                        Log.d(TAG, "complete: removing similar obsolete task");
                    } else {
                        Log.d(TAG, "complete: more connect right tasks found, ok");
                        return;
                    }
                }
            }
            if (satisfied) {
                Log.d(TAG, "complete: all obsolete tasks removed");
                setRightStage(STAGE_FUNCTIONING);
                mainActivity().pairingFinished();
                return;
            } else {
                Log.d(TAG, "complete: none connect right tasks found, adding scan");
                Log.d(TAG, "complete: stage: " + stageToString(getRightStage()));
                if (getRightStage() == STAGE_LOOKING_IN_BONDED) {
                    Log.d(TAG, "complete: adding scan");
                    setRightStage(STAGE_SCANNING);
                    addNewScanTask();
                } else {
                    if (getRightStage() == STAGE_CONNECTING) {
                        setRightStage(STAGE_FAILED_TO_CONNECT);
                    }
                    Log.d(TAG, "complete: no need to scan now");
                }
            }
        }
    }

    private void addNewScanTask() {
        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
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
            mainActivity().setRightPairedBtnMode(MainActivity.MODE_SCANNING);
            mainActivity().setLeftPairedBtnMode(MainActivity.MODE_SCANNING);
            scanner.stopScan(scanCallBack);
            scanner.startScan(null, scanSettings, scanCallBack);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    scanner.stopScan(scanCallBack);
                    if (mainActivity().getLeftPairedBtnMode() == MainActivity.MODE_SCANNING) {
                        mainActivity().setLeftPairedBtnMode(MainActivity.MODE_DISCONNECTED);
                    }
                    if (mainActivity().getRightPairedBtnMode() == MainActivity.MODE_SCANNING) {
                        mainActivity().setRightPairedBtnMode(MainActivity.MODE_DISCONNECTED);
                    }
                    Log.d(TAG, "scanner timed out");
                }
            }, scan_timeout_ms);
            completeTask();
        }

        @Override
        public void complete() {
            Log.d(TAG, "complete: task scan 23");
        }
    }

    private class TaskScan21 extends Task {

        public TaskScan21() {
            code = TASK_SCAN;
        }

        @Override
        public void run() {
            Log.d(TAG, "run: starting scan for device, API 21");
        }

        @Override
        public void complete() {
            Log.d(TAG, "complete: task scan 21");
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
                case WRITE_TYPE_DEFAULT:
                    writeProperty = PROPERTY_WRITE;
                    break;
                case WRITE_TYPE_NO_RESPONSE:
                    writeProperty = PROPERTY_WRITE_NO_RESPONSE;
                    break;
                case WRITE_TYPE_SIGNED:
                    writeProperty = PROPERTY_SIGNED_WRITE;
                    break;
                default:
                    writeProperty = 0;
                    break;
            }
            Log.d(TAG, "run: write property: " + writeProperty);

            if ((lWriteCharacteristic.getProperties() & writeProperty) == 0) {
                Log.e(TAG, String.format(Locale.ENGLISH, "ERROR: Characteristic <%s> does not support writeType '%d'", lWriteCharacteristic.getUuid(), writeProperty));
                completeTask();
                return;
            }
            lWriteCharacteristic.setValue(message);
            // lWriteCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            if (!lGatt.writeCharacteristic(lWriteCharacteristic)) {
                Log.e(TAG, "send: failed to write characteristic: " + lWriteCharacteristic.getUuid());
            } else {
                Log.i(TAG, "send: successfully written");
            }
        }

        @Override
        public void complete() {
            Log.d(TAG, "complete: task send left");
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
                case WRITE_TYPE_DEFAULT:
                    writeProperty = PROPERTY_WRITE;
                    break;
                case WRITE_TYPE_NO_RESPONSE:
                    writeProperty = PROPERTY_WRITE_NO_RESPONSE;
                    break;
                case WRITE_TYPE_SIGNED:
                    writeProperty = PROPERTY_SIGNED_WRITE;
                    break;
                default:
                    writeProperty = 0;
                    break;
            }
            Log.d(TAG, "run: write property: " + writeProperty);

            if ((rWriteCharacteristic.getProperties() & writeProperty) == 0) {
                Log.e(TAG, String.format(Locale.ENGLISH, "ERROR: Characteristic <%s> does not support writeType '%d'", rWriteCharacteristic.getUuid(), writeProperty));
                completeTask();
                return;
            }
            rWriteCharacteristic.setValue(message);
            // lWriteCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            if (!rGatt.writeCharacteristic(rWriteCharacteristic)) {
                Log.e(TAG, "send: failed to write characteristic: " + rWriteCharacteristic.getUuid());
            } else {
                Log.i(TAG, "send: successfully written");
            }
        }

        @Override
        public void complete() {
            Log.d(TAG, "complete: task send right");
        }
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
                mainActivity().setLeftPairedBtnMode(MainActivity.MODE_DISCONNECTED);
                gatt.close();
                completeTask();
                return;
            }

            if (gatt == null) {
                Log.d(TAG, "run: abort connection if needed: gatt is null");
                if (device.getName().contains(lName))
                    mainActivity().setLeftPairedBtnMode(MainActivity.MODE_DISCONNECTED);
                else
                    mainActivity().setRightPairedBtnMode(MainActivity.MODE_DISCONNECTED);
                return;
            }

            if (gatt == lGatt) {
                lConnected = true;
                mainActivity().setLeftPairedBtnMode(MainActivity.MODE_CONNECTED);
            } else {
                rConnected = true;
                mainActivity().setRightPairedBtnMode(MainActivity.MODE_CONNECTED);
            }
            if (lConnected && rConnected) {
                setup = false;
                Log.d(TAG, "run: setup finished");
            }
            Log.d(TAG, "run: no need to abort, device connected");
        }
    }

    private void completeTask() {
        if (tasks.size() > 0) {
            Log.d(TAG, "completeTask: complete() on top task");
            tasks.peek().complete();
            tasks.poll();
            queueBusy = false;
            nextTask();
        }
    }

    private void nextTask(){
        if (queueBusy) return;

        if (tasks.size() > 0) {
            queueBusy = true;
            tasks.peek().run();
        }
    }

    private List<BluetoothDevice> lookForDeviceInBondedByName(String name) {
        List<BluetoothDevice> result = new ArrayList<>();
        for (BluetoothDevice device : adapter.getBondedDevices()) {
            if (device.getName().contains(name)) {
                result.add(device);
            }
        }
        return result;
    }

    public void leftDeviceChosen(BluetoothDevice device) {
        Log.d(TAG, "leftDeviceChosen");
        if (rConnected || !(rScan || lScan)) {
            scanner.stopScan(scanCallBack);
        }
        lScan = false;
        holdOn = true;
        int pos = 0;
        if (queueBusy) pos = 1;
        insertTaskToPosition(new TaskConnectLeftIfNeeded(device), pos);
        if (!queueBusy) nextTask();
    }

    public void rightDeviceChosen(BluetoothDevice device) {
        Log.d(TAG, "rightDeviceChosen");
        if (lConnected || !(rScan || lScan)) {
            scanner.stopScan(scanCallBack);
        }
        rScan = false;
        holdOn = true;
        int pos = 0;
        if (queueBusy) pos = 1;
        insertTaskToPosition(new TaskConnectRightIfNeeded(device), pos);
        if (!queueBusy) nextTask();
    }

    private void insertTaskToPosition(Task toInsert, int position) {
        Log.d(TAG, "insertTaskToPosition: pos: " + position);
        Queue<Task> clone = new ArrayDeque<>();
        int currentPos = 0;
        for (Task task : tasks) {
            if (currentPos == position) {
                clone.add(toInsert);
            }
            clone.add(task);
        }
        if (clone.size() == 0) {
            Log.d(TAG, "insertTaskToPosition: no tasks found, adding in empty");
            clone.add(toInsert);
        }
        tasks = clone;

    }

    private void unpair(BluetoothDevice device) {
        try {
            Method m = device.getClass()
                    .getMethod("removeBond", (Class[]) null);
            m.invoke(device, (Object[]) null);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
    }

}
