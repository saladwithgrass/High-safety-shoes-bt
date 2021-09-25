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
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
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
import static android.bluetooth.BluetoothProfile.STATE_CONNECTING;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTED;
import static android.content.Context.BLUETOOTH_SERVICE;

public class SimpleBluetoothService {

    private final String TAG = "BluetoothService";
    private List<String> targetNames = new ArrayList<>();
    private String lName, rName, lMac, rMac, leftSavedMac, rightSavedMac;

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

    private Queue<SimpleBluetoothService.Task> tasks = new ArrayDeque<SimpleBluetoothService.Task>();

    private BluetoothAdapter adapter;
    private BluetoothGatt lGatt, rGatt;
    private BluetoothDevice lBoot, rBoot;
    private BluetoothGattCharacteristic lWriteCharacteristic, lReadCharacteristic, rWriteCharacteristic, rReadCharacteristic;
    private BluetoothLeScanner scanner;
    private BluetoothManager manager;
    private BluetoothGattService lGattService, rGattService;
    public List<BluetoothDevice> lDevicesList, rDevicesList, scannedDevices = new ArrayList<>();
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

    public SimpleBluetoothService(Context context) {
        Log.d(TAG, "SimpleBluetoothService: starting");
        this.context = context;
        manager = (BluetoothManager) context.getSystemService(BLUETOOTH_SERVICE);
        adapter = BluetoothAdapter.getDefaultAdapter();
        handler = new Handler(Looper.getMainLooper());
        scanner = adapter.getBluetoothLeScanner();
        // Log.d(TAG, "BluetoothService: scanner null: " + (scanner == null));
        lDevicesList = new ArrayList<>();
        rDevicesList = new ArrayList<>();
        lName = context.getString(R.string.leftBootName);
        rName = context.getString(R.string.rightBootName);
        leftSavedMac = mainActivity().getLMac();
        rightSavedMac = mainActivity().getRMac();
        Log.d(TAG, "SimpleBluetoothService: left saved mac: " + leftSavedMac);
        Log.d(TAG, "SimpleBluetoothService: right saved mac: " + rightSavedMac);
        startScanNow();


        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "run: scanner timed out");
                scanner.stopScan(scanCallBack);
            }
        }, scan_timeout_ms);
    }

    private BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            int bondState = gatt.getDevice().getBondState();

            if (gatt == null) {
                Log.e(TAG, "onConnectionStateChange: gatt is null" );
                return;
            }

            Log.d(TAG, "onConnectionStateChange: connection state changed");
            
            if (status == GATT_SUCCESS) {
                if (newState == STATE_CONNECTED) {
                    Log.d(TAG, "onConnectionStateChange: connected, processing bonding");
                    if (bondState == BOND_BONDED || bondState == BOND_NONE) {
                        Log.i(TAG, "onConnectionStateChange: successfully connected && bonded, proceeding to process");

                        int delayBonded = 1000;

                        if (gatt == lGatt && leftConnecting) {
                            lBoot = gatt.getDevice();
                            lScan = false;
                            setLeftConnecting(false);
                            Log.d(TAG, "onConnectionStateChange: no longer connecting left");
                            mainActivity().toast("left almost connected");
                        } else if (gatt == rGatt && rightConnecting) {
                            rBoot = gatt.getDevice();
                            rScan = false;
                            setRightConnecting(false);
                            Log.d(TAG, "onConnectionStateChange: no longer connecting right");
                            mainActivity().toast("right almost connected");
                        }

                        discoverServicesRunnable = new Runnable() {
                            @Override
                            public void run() {
                                Log.d(TAG, "run: starting service discovery after delay");
                                boolean gattResult = gatt.discoverServices();

                                if (!gattResult) {
                                    Log.e(TAG, "run: service discovery failed");
                                    mainActivity().toast("failed to connect");
                                }
                                if (gatt == lGatt) {
                                    lConnected = true;
                                    mainActivity().setLeftPairedBtnMode(MainActivity.MODE_CONNECTED);
                                    mainActivity().toast("left connected");
                                    if (rConnected) {
                                        ((MainActivity) context).pairingFinished();
                                    }
                                } else {
                                    rConnected = true;
                                    mainActivity().setRightPairedBtnMode(MainActivity.MODE_CONNECTED);
                                    mainActivity().toast("right connected");
                                    if (lConnected) {
                                        ((MainActivity) context).pairingFinished();
                                    }
                                }
                                discoverServicesRunnable = null;
                            }
                        };
                        handler.postDelayed(discoverServicesRunnable, delayBonded);
                        // tasks.peek().satisfy();
                        // Log.d(TAG, "onConnectionStateChange: callback completes task");
                        // completeTask();

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
                    if (gatt == lGatt) {
                        Log.d(TAG, "onConnectionStateChange: left gatt disconnected");
                        mainActivity().setLeftPairedBtnMode(MainActivity.MODE_DISCONNECTED);
                    } else if (gatt == rGatt) {
                        Log.d(TAG, "onConnectionStateChange: right gatt disconnected");
                        mainActivity().setRightPairedBtnMode(MainActivity.MODE_DISCONNECTED);
                    } else Log.d(TAG, "onConnectionStateChange: gatt is not left nor right");
                    gatt.close();
                } else {
                    Log.d(TAG, "onConnectionStateChange: disconnection or connection in process");
                }
            } else {
                if (status == 8) {
                    Log.d(TAG, "onConnectionStateChange: status: 8");
                    if (gatt == lGatt) {
                        Log.d(TAG, "onConnectionStateChange: left gatt disconnected");
                        mainActivity().setLeftPairedBtnMode(MainActivity.MODE_DISCONNECTED);
                    } else if (gatt == rGatt) {
                        Log.d(TAG, "onConnectionStateChange: right gatt disconnected");
                        mainActivity().setRightPairedBtnMode(MainActivity.MODE_DISCONNECTED);
                    }
                } else {
                    Log.e(TAG, "onConnectionStateChange: gatt failure, something went wrong, code: " + status);
                    gatt.close();
                }
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
            onConnect(gatt.getDevice());
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            Log.d(TAG, "onCharacteristicRead: called");
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            Log.d(TAG, "onCharacteristicWrite: called");
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
            byte[] messageByte = characteristic.getValue();
            Log.d(TAG, "onCharacteristicChanged: received message: " + message);
            mainActivity().toast(message);
            mainActivity().setTextInTemperature(message);
            // ((MainActivity) context).parseAndExecute(message);
            Log.d(TAG, "onCharacteristicChanged: completes");
            mainActivity().parseAndExecute(messageByte);
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

            if (lDevicesList.contains(device) || rDevicesList.contains(device) || scannedDevices.contains(device)) return;

            scannedDevices.add(device);

            Log.d(TAG, "onScanResult: device found " + device.getName() + " " + device.getAddress());

            if (device.getName() == null) {
                Log.e(TAG, "onScanResult: device name is null");
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
            if (errorCode == SCAN_FAILED_ALREADY_STARTED) {
                Log.d(TAG, "onScanFailed: already started");
                return;
            }
            mainActivity().toast("scan failed, you'd better reload the app");
            Log.e(TAG, "onScanFailed: scan failed: " + errorCode);
        }

    };

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

    private class TaskConnectLeftIfNeeded extends SimpleBluetoothService.Task {
        private BluetoothDevice device;

        public TaskConnectLeftIfNeeded(BluetoothDevice device) {
            code = TASK_CONNECT;
            this.device = device;
        }

        @Override
        public void run() {
            this.left = true;
            // setLeftStage(STAGE_CONNECTING);
            Log.d(TAG, "run: connectLeftIfNeeded: connecting to: " + device.getName() + " " + device.getAddress());
            mainActivity().setLeftPairedBtnMode(MainActivity.MODE_CONNECTING);
            lGatt = device.connectGatt(context, true, bluetoothGattCallback);
            handler.postDelayed(new SimpleBluetoothService.TaskAbortConnectionIfNeeded(device, lGatt), TIME_TO_CONNECT_MS);

        }

        @Override
        public void complete() {
            Log.d(TAG, "connect left if needed completing " + (satisfied ? "satisfied" : "unsatisfied"));
        }
    }

    private class TaskConnectRightIfNeeded extends SimpleBluetoothService.Task {
        private BluetoothDevice device;

        public TaskConnectRightIfNeeded(BluetoothDevice device) {
            this.device = device;
            code = TASK_CONNECT;
            this.left = false;
        }

        @Override
        public void run() {

            // setRightStage(STAGE_CONNECTING);
            Log.d(TAG, "run: connectRightIfNeeded: connecting to: " + device.getName() + " " + device.getAddress());
            mainActivity().setRightPairedBtnMode(MainActivity.MODE_CONNECTING);
            rGatt = device.connectGatt(context, true, bluetoothGattCallback);
            handler.postDelayed(new SimpleBluetoothService.TaskAbortConnectionIfNeeded(device, rGatt), TIME_TO_CONNECT_MS);

        }

        @Override
        public void complete() {
            Log.d(TAG, "connect right if needed completing " + (satisfied ? "satisfied" : "unsatisfied"));
        }
    }

    private void addNewScanTask() {
        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            addTask(new SimpleBluetoothService.TaskScan21());
        } else {
            addTask(new SimpleBluetoothService.TaskScan23());
        }
    }

    private void addTask(SimpleBluetoothService.Task task) {

        tasks.add(task);
        if (tasks.size() == 1) nextTask();
    }

    private class TaskScan23 extends SimpleBluetoothService.Task {

        public TaskScan23() {
            code = TASK_SCAN;
        }

        @Override
        public void run() {
            Log.d(TAG, "run: starting scan for device, API 23");
            mainActivity().setRightPairedBtnMode(MainActivity.MODE_SCANNING);
            mainActivity().setLeftPairedBtnMode(MainActivity.MODE_SCANNING);
            scanner.stopScan(scanCallBack);
            startScanNow();
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

    private class TaskScan21 extends SimpleBluetoothService.Task {

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

    private class TaskSendLeft extends SimpleBluetoothService.Task {

        private byte[] message;

        public TaskSendLeft(String msg) {
            code = TASK_SEND;
            message = msg.getBytes();
        }

        public TaskSendLeft(byte[] msg) {
            code = TASK_SEND;
            message = msg;
        }

        @Override
        public void run() {
            if (!lConnected) {
                Log.e(TAG, "run: left not connected, not sending");
                completeTask();
                return;
            }

            if (lGatt == null) {
                Log.e(TAG, "run: lgatt is null, not sending");
                completeTask();
                return;
            }

            if (lWriteCharacteristic == null) {
                Log.e(TAG, "run: lcharacteristic is null" );
                completeTask();
                return;
            }

            Log.d(TAG, "TaskSendLeft: sending " + message.toString());
            String bytes = "";
            for (byte byt : message) {
                bytes += String.format("%02X ", byt);
            }
            Log.d(TAG, "TaskSendLeft: sending byte " + bytes);
            if (manager.getConnectionState(lBoot, GATT) != STATE_CONNECTED) {
                Log.e(TAG, "TaskSendLeft: left device not connected, cant send");
                completeTask();
                return;
            }
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
                completeTask();
            } else {
                Log.i(TAG, "send: successfully written");
                mainActivity().toast("successfully sent");
            }
            // completeTask();
        }

        @Override
        public void complete() {
            Log.d(TAG, "complete: task send left");
        }
    }

    private class TaskSendRight extends SimpleBluetoothService.Task {
        private byte[] message;

        public TaskSendRight(String msg) {
            code = TASK_SEND;
            message = msg.getBytes();
        }

        public TaskSendRight(byte[] msg) {
            code = TASK_SEND;
            message = msg;
        }

        @Override
        public void run() {
            if (!rConnected) {
                Log.e(TAG, "run: right not connected, not sending");
                completeTask();
                return;
            }
            if (rGatt == null) {
                Log.e(TAG, "run: rgatt is null, not sending");
                completeTask();
                return;
            }

            Log.d(TAG, "TaskSendRight: sending " + message);
            if (manager.getConnectionState(rBoot, GATT) != STATE_CONNECTED) {
                Log.e(TAG, "TaskSendRight: right not connected, not sending");
                completeTask();
                return;
            }

            if (rWriteCharacteristic == null) {
                Log.e(TAG, "run: rcharacteristic is null" );
                completeTask();
                return;
            }

            int writeProperty, writeType = rWriteCharacteristic.getWriteType();
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
                completeTask();
            } else {
                Log.i(TAG, "send: successfully written");
                mainActivity().toast("successfully sent");
            }
            // completeTask();
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
                if (device.getName().contains(lName))
                    mainActivity().setLeftPairedBtnMode(MainActivity.MODE_DISCONNECTED);
                else
                    mainActivity().setRightPairedBtnMode(MainActivity.MODE_DISCONNECTED);
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

    private void pair() {
        Set<BluetoothDevice> bonded;
        bonded = adapter.getBondedDevices();
        BluetoothDevice lDevice = null, rDevice = null;
        for (BluetoothDevice device : bonded) {
            if (device.getAddress().equals(lMac)) {
                lDevice = device;
                Log.d(TAG, "pair: left device found in bonded");
                continue;
            }
            if (device.getAddress().equals(rMac)) {
                rDevice = device;
                Log.d(TAG, "pair: right device found in bonded");
                continue;
            }
        }

        if (lDevice == null || rDevice == (null)) {
            Log.e(TAG, "pair: unexpected situation, one or both of deivce(s) is(are) null");
            return;
        }

        Log.d(TAG, "pair: devices found, trying to connect");
        addTask(new TaskConnectLeftIfNeeded(lDevice));
        addTask(new TaskConnectRightIfNeeded(rDevice));
        nextTask();
    }

    private void onConnect(BluetoothDevice device) {
        if (device.getName().contains(lName)) {
            Log.d(TAG, "onConnect: left connected, sending settings");
            addTask(new TaskSendLeft(mainActivity().getDefaultMessage()));
        } else {
            Log.d(TAG, "onConnect: right connected, sending setting");
            addTask(new TaskSendRight(mainActivity().getDefaultMessage()));
        }
        if (!queueBusy) nextTask();
    }

    public List<BluetoothDevice> getLeftDevicesList() {
        lDevicesList  = lDevicesList;
        return lDevicesList;
    }

    public List<BluetoothDevice> getRightDevicesList() {
        rDevicesList = rDevicesList;
        return rDevicesList;
    }

    public void startScan() {
        addNewScanTask();
    }

    public void setlMac(String lMac_p) {
        lMac = lMac_p;
    }

    public void setrMac(String rMac_p) {
        rMac = rMac_p;
    }

    private BluetoothGatt getLGatt() {return lGatt;}

    private BluetoothGatt getRGatt() {return rGatt;}

    boolean leftConnecting, rightConnecting;

    private boolean getLeftConnecting() {return leftConnecting;}
    private boolean getRightConnecting() {return rightConnecting;}

    private void setLeftConnecting(boolean leftConnecting_p) {leftConnecting = leftConnecting_p;}
    private void setRightConnecting(boolean rightConnecting_p) {rightConnecting = rightConnecting_p;}


    public void connectLeft(BluetoothDevice device) {
        Log.d(TAG, "connectLeft: " + device.getName() + " " + device.getAddress());
        if(lGatt !=  null) {
            Log.d(TAG, "connectLeft: closing left gatt");
            lGatt.disconnect();
            lGatt.close();
        }
        Log.d(TAG, "connectLeft: lgatt is null");
        Log.d(TAG, "connectLeft: setting booleans to false");
        lConnected = lScan = false;
        setLeftConnecting(true);
        // tells whether service is trying to connect to
        breakTask();
        unpair(lBoot);
        Log.d(TAG, "connectLeft: connecting gatt");
        lGatt = device.connectGatt(context, true, bluetoothGattCallback);
        Runnable abort = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "run: abort if needed left device: " + device.getName() + ", aborting");
                if (!getLeftConnecting()) {
                    Log.d(TAG, "run: connection finished, no need to abort");
                    return;
                }
                Log.d(TAG, "run: " + manager.getConnectionState(device, GATT));
                if (manager.getConnectionState(device, GATT) == STATE_DISCONNECTED ||
                    manager.getConnectionState(device, GATT) == STATE_CONNECTING) {
                    Log.d(TAG, "run: aborting connection to device: " + device.getName());
                    mainActivity().setLeftPairedBtnMode(MainActivity.MODE_DISCONNECTED);
                    getLGatt().close();
                    // completeTask();
                    setLeftConnecting(false);
                    return;
                }

                if (getLGatt() == null) {
                    Log.e(TAG, "run: lgatt is null, abort" );
                    setLeftConnecting(false);
                    mainActivity().setLeftPairedBtnMode(MainActivity.MODE_DISCONNECTED);
                    return;
                }

                Log.d(TAG, "run: no need to abort");

            }
        };
        handler.postDelayed(abort, TIME_TO_CONNECT_MS);
    }

    public void connectRight(BluetoothDevice device) {
        Log.d(TAG, "connectRight: " + device.getName() + " " + device.getAddress());
        if (rGatt != null) {
            Log.d(TAG, "connectRight: closing right gatt");
            rGatt.disconnect();
            rGatt.close();
        }
        Log.d(TAG, "connectRight: rgatt is null");
        Log.d(TAG, "connectRight: setting booleans to false");
        rConnected = rScan = false;
        breakTask();
        unpair(rBoot);
        lConnected = lScan = false;
        setRightConnecting(true);
        // tells whether service is trying to connect to
        Log.d(TAG, "connectLeft: connecting gatt");
        rGatt = device.connectGatt(context, true, bluetoothGattCallback);
        Runnable abort = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "run: abort if needed right device: " + device.getName() + ", aborting");
                if (!getRightConnecting()) {
                    Log.d(TAG, "run: connection finished, no need to abort");
                    return;
                }
                if (manager.getConnectionState(device, GATT) == STATE_CONNECTING ||
                        manager.getConnectionState(device, GATT) == STATE_DISCONNECTED) {
                    Log.d(TAG, "run: aborting connection to device: " + device.getName());
                    mainActivity().setLeftPairedBtnMode(MainActivity.MODE_DISCONNECTED);
                    getRGatt().close();
                    // completeTask();
                    setRightConnecting(false);
                    return;
                }

                if (getRGatt() == null) {
                    Log.e(TAG, "run: rgatt is null, abort" );
                    setRightConnecting(false);
                    return;
                }

                Log.d(TAG, "run: no need to abort");

            }
        };
        handler.postDelayed(abort, TIME_TO_CONNECT_MS);

    }

    private void breakTask() {
        Log.d(TAG, "break task: ");
        if (tasks.size() > 0 && queueBusy) {
            tasks.peek().complete();
        }
        queueBusy = false;
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

    boolean leftAnswering, rightAnswering;

    public void send(byte[] message) {

        String strMessage = "";
        for (byte byt : message) {
            strMessage += String.format("%02X ", byt);
        }
        Log.d(TAG, "send: sending " + strMessage);
        Log.d(TAG, "send: " + message);
        tasks.add(new TaskSendLeft(message));
        tasks.add(new TaskSendRight(message));
        if (!queueBusy) nextTask();

    }

    private void sendLeft(byte[] message) {
        Log.d(TAG, "sendLeft: sending " + message.toString());
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
            // completeTask();
            return;
        }
        lWriteCharacteristic.setValue(message);
        // lWriteCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        if (!lGatt.writeCharacteristic(lWriteCharacteristic)) {
            Log.e(TAG, "send: failed to write characteristic: " + lWriteCharacteristic.getUuid());
        } else {
            Log.i(TAG, "send: successfully written");
            leftAnswering = true;
        }
    }

    private void sendRight(byte[] message) {}

    void startScanNow(){
        Log.d(TAG, "startScanNow");
        scanner.startScan(null, scanSettings, scanCallBack);
    }

}
