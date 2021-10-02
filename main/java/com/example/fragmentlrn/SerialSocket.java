package com.example.fragmentlrn;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.provider.SyncStateContract;
import android.util.Log;

import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Queue;
import java.util.UUID;

/**
 * wrap BLE communication into socket like class
 *   - connect, disconnect and write as methods,
 *   - read + status is returned by SerialListener
 */
class SerialSocket extends BluetoothGattCallback {

    /**
     * delegate device specific behaviour to inner class
     */
    private static class DeviceDelegate {
        boolean connectCharacteristics(BluetoothGattService s) { return true; }
        // following methods only overwritten for Telit devices
        void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int status) { /*nop*/ }
        void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) {/*nop*/ }
        void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c, int status) { /*nop*/ }
        boolean canWrite() { return true; }
        void disconnect() {/*nop*/ }
    }

    private static final UUID BLUETOOTH_LE_CCCD           = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final UUID BLUETOOTH_LE_CC254X_SERVICE = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    private static final UUID BLUETOOTH_LE_CC254X_CHAR_RW = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");

    private static final int MAX_MTU = 512; // BLE standard does not limit, some BLE 4.2 devices support 251, various source say that Android has max 512
    private static final int DEFAULT_MTU = 23;
    private static final String TAG = "SerialSocket";

    private final ArrayList<byte[]> writeBuffer;
    private final IntentFilter pairingIntentFilter;
    private final BroadcastReceiver pairingBroadcastReceiver;
    private final BroadcastReceiver disconnectBroadcastReceiver;

    private final Context context;
    // private SerialListener listener;
    private DeviceDelegate delegate;
    private BluetoothDevice device;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic readCharacteristic, writeCharacteristic;

    private boolean writePending;
    private boolean canceled;
    private boolean connected;
    private int payloadSize = DEFAULT_MTU-3;
    private Context activity;

    SerialSocket(Context context, Context activity, BluetoothDevice device) {
        if(context instanceof Activity)
            throw new InvalidParameterException("expected non UI context");
        this.context = context;
        this.device = device;
        this.activity = activity;
        writeBuffer = new ArrayList<>();
        pairingIntentFilter = new IntentFilter();
        pairingIntentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        pairingIntentFilter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);
        pairingBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                onPairingBroadcastReceive(context, intent);
            }
        };
        disconnectBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.e(TAG, "onReceive: background disconnect");
                disconnect(); // disconnect now, else would be queued until UI re-attached
            }
        };
    }

    String getName() {
        return device.getName() != null ? device.getName() : device.getAddress();
    }

    void disconnect() {
        Log.d(TAG, "disconnect");
        // listener = null; // ignore remaining data and errors
        device = null;
        canceled = true;
        synchronized (writeBuffer) {
            writePending = false;
            writeBuffer.clear();
        }
        readCharacteristic = null;
        writeCharacteristic = null;
        if(delegate != null)
            delegate.disconnect();
        if (gatt != null) {
            Log.d(TAG, "gatt.disconnect");
            gatt.disconnect();
            Log.d(TAG, "gatt.close");
            try {
                gatt.close();
            } catch (Exception ignored) {}
            gatt = null;
            connected = false;
        }
        try {
            context.unregisterReceiver(pairingBroadcastReceiver);
        } catch (Exception ignored) {
        }
        try {
            context.unregisterReceiver(disconnectBroadcastReceiver);
        } catch (Exception ignored) {
        }
    }

    /**
     * connect-success and most connect-errors are returned asynchronously to listener
     */
    void connect() throws IOException {
        if(connected || gatt != null)
            throw new IOException("already connected");
        canceled = false;
        // this.listener = listener;
        context.registerReceiver(disconnectBroadcastReceiver, new IntentFilter(Constants.INTENT_ACTION_DISCONNECT));
        Log.d(TAG, "connect "+device);
        context.registerReceiver(pairingBroadcastReceiver, pairingIntentFilter);
        if (Build.VERSION.SDK_INT < 23) {
            Log.d(TAG, "connectGatt");
            gatt = device.connectGatt(context, false, this);
        } else {
            Log.d(TAG, "connectGatt,LE");
            gatt = device.connectGatt(context, false, this, BluetoothDevice.TRANSPORT_LE);
        }
        if (gatt == null)
            throw new IOException("connectGatt failed");
        // continues asynchronously in onPairingBroadcastReceive() and onConnectionStateChange()
    }

    private void onPairingBroadcastReceive(Context context, Intent intent) {
        // for ARM Mbed, Microbit, ... use pairing from Android bluetooth settings
        // for HM10-clone, ... pairing is initiated here
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        if(device==null || !device.equals(this.device))
            return;
        switch (intent.getAction()) {
            case BluetoothDevice.ACTION_PAIRING_REQUEST:
                final int pairingVariant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, -1);
                Log.d(TAG, "pairing request " + pairingVariant);
                // onSerialConnectError(new IOException(context.getString(R.string.pairing_request)));
                Log.e(TAG, "onPairingBroadcastReceive: pairing request");
                // pairing dialog brings app to background (onPause), but it is still partly visible (no onStop), so there is no automatic disconnect()
                break;
            case BluetoothDevice.ACTION_BOND_STATE_CHANGED:
                final int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);
                final int previousBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1);
                Log.d(TAG, "bond state " + previousBondState + "->" + bondState);
                break;
            default:
                Log.d(TAG, "unknown broadcast " + intent.getAction());
                break;
        }
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        // status directly taken from gat_api.h, e.g. 133=0x85=GATT_ERROR ~= timeout
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            Log.d(TAG,"connect status "+status+", discoverServices");
            if (!gatt.discoverServices())
                Log.d(TAG, "onConnectionStateChange: discoverServices failed");
                // onSerialConnectError(new IOException("discoverServices failed"));
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            if (connected)
                // onSerialIoError     (new IOException("gatt status " + status));
                Log.d(TAG, "onConnectionStateChange: gatt status: " + status);
            else
                Log.d(TAG, "onConnectionStateChange: gatt status: " + status);
                // onSerialConnectError(new IOException("gatt status " + status));
        } else {
            Log.d(TAG, "unknown connect state "+newState+" "+status);
        }
        // continues asynchronously in onServicesDiscovered()
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        Log.d(TAG, "servicesDiscovered, status " + status);
        if (canceled)
            return;
        connectCharacteristics1(gatt);
    }

    private void connectCharacteristics1(BluetoothGatt gatt) {
        boolean sync = true;
        writePending = false;
        for (BluetoothGattService gattService : gatt.getServices()) {
            if (gattService.getUuid().equals(BLUETOOTH_LE_CC254X_SERVICE))
                delegate = new Cc245XDelegate();
            else {
                Log.e(TAG, "connectCharacteristics1: wrong chip, only working with CC254X");
            }

            if(delegate != null) {
                sync = delegate.connectCharacteristics(gattService);
                break;
            }
        }
        if(canceled)
            return;
        if(delegate==null || readCharacteristic==null || writeCharacteristic==null) {
            for (BluetoothGattService gattService : gatt.getServices()) {
                Log.d(TAG, "service "+gattService.getUuid());
                for(BluetoothGattCharacteristic characteristic : gattService.getCharacteristics())
                    Log.d(TAG, "characteristic "+characteristic.getUuid());
            }
            // onSerialConnectError(new IOException("no serial profile found"));
            Log.e(TAG, "connectCharacteristics1: no serial profile found");
            return;
        }
        if(sync)
            connectCharacteristics2(gatt);
    }

    private void connectCharacteristics2(BluetoothGatt gatt) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Log.d(TAG, "request max MTU");
            if (!gatt.requestMtu(MAX_MTU))
                // onSerialConnectError(new IOException("request MTU failed"));
            Log.e(TAG, "connectCharacteristics2: request MTU failed");
            // continues asynchronously in onMtuChanged
        } else {
            connectCharacteristics3(gatt);
        }
    }

    @Override
    public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
        Log.d(TAG,"mtu size "+mtu+", status="+status);
        if(status ==  BluetoothGatt.GATT_SUCCESS) {
            payloadSize = mtu - 3;
            Log.d(TAG, "payload size "+payloadSize);
        }
        connectCharacteristics3(gatt);
    }

    private void connectCharacteristics3(BluetoothGatt gatt) {
        int writeProperties = writeCharacteristic.getProperties();
        if((writeProperties & (BluetoothGattCharacteristic.PROPERTY_WRITE +     // Microbit,HM10-clone have WRITE
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) ==0) { // HM10,TI uart,Telit have only WRITE_NO_RESPONSE
            // onSerialConnectError(new IOException("write characteristic not writable"));
            Log.e(TAG, "write char not writable");
            return;
        }
        if(!gatt.setCharacteristicNotification(readCharacteristic,true)) {
            // onSerialConnectError(new IOException("no notification for read characteristic"));
            Log.e(TAG, "connectCharacteristics3: no notification for read char");
            return;
        }
        BluetoothGattDescriptor readDescriptor = readCharacteristic.getDescriptor(BLUETOOTH_LE_CCCD);
        if(readDescriptor == null) {
            // onSerialConnectError(new IOException("no CCCD descriptor for read characteristic"));
            Log.e(TAG, "no CCCD descriptor for read char");
            return;
        }
        int readProperties = readCharacteristic.getProperties();
        if((readProperties & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
            Log.d(TAG, "enable read indication");
            readDescriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
        }else if((readProperties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
            Log.d(TAG, "enable read notification");
            readDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        } else {
            // onSerialConnectError(new IOException("no indication/notification for read characteristic ("+readProperties+")"));
            Log.e(TAG, "connectCharacteristics3: no indication for read char" );
            return;
        }
        Log.d(TAG,"writing read characteristic descriptor");
        if(!gatt.writeDescriptor(readDescriptor)) {
            // onSerialConnectError(new IOException("read characteristic CCCD descriptor not writable"));
            Log.e(TAG, "connectCharacteristics3: read char CCCD descriptor not writable");
        }
        // continues asynchronously in onDescriptorWrite()
    }

    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        delegate.onDescriptorWrite(gatt, descriptor, status);
        if(canceled)
            return;
        if(descriptor.getCharacteristic() == readCharacteristic) {
            Log.d(TAG,"writing read characteristic descriptor finished, status="+status);
            if (status != BluetoothGatt.GATT_SUCCESS) {
                // onSerialConnectError(new IOException("write descriptor failed"));
                Log.e(TAG, "onDescriptorWrite: gatt failed, status: " + status);
            } else {
                // onCharacteristicChanged with incoming data can happen after writeDescriptor(ENABLE_INDICATION/NOTIFICATION)
                // before confirmed by this method, so receive data can be shown before device is shown as 'Connected'.
                onSerialConnect();
                connected = true;
                Log.d(TAG, "connected");
            }
        }
    }

    /*
     * read
     */
    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        if(canceled)
            return;
        delegate.onCharacteristicChanged(gatt, characteristic);
        if(canceled)
            return;
        if(characteristic == readCharacteristic) { // NOPMD - test object identity
            byte[] data = readCharacteristic.getValue();
            onSerialRead(data);
            Log.d(TAG,"read, len="+data.length);
        }
    }

    /*
     * write
     */
    void write(byte[] data) throws IOException {
        if(canceled || !connected || writeCharacteristic == null)
            throw new IOException("not connected");
        byte[] data0;
        synchronized (writeBuffer) {
            if(data.length <= payloadSize) {
                data0 = data;
            } else {
                data0 = Arrays.copyOfRange(data, 0, payloadSize);
            }
            if(!writePending && writeBuffer.isEmpty() && delegate.canWrite()) {
                writePending = true;
            } else {
                writeBuffer.add(data0);
                Log.d(TAG,"write queued, len="+data0.length);
                data0 = null;
            }
            if(data.length > payloadSize) {
                for(int i=1; i<(data.length+payloadSize-1)/payloadSize; i++) {
                    int from = i*payloadSize;
                    int to = Math.min(from+payloadSize, data.length);
                    writeBuffer.add(Arrays.copyOfRange(data, from, to));
                    Log.d(TAG,"write queued, len="+(to-from));
                }
            }
        }
        if(data0 != null) {
            writeCharacteristic.setValue(data0);
            if (!gatt.writeCharacteristic(writeCharacteristic)) {
                // onSerialIoError(new IOException("write failed"));
                Log.e(TAG, "write: write failed");
            } else {
                Log.d(TAG,"write started, len="+data0.length);
            }
        }
        // continues asynchronously in onCharacteristicWrite()
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        if(canceled || !connected || writeCharacteristic == null)
            return;
        if(status != BluetoothGatt.GATT_SUCCESS) {
            // onSerialIoError(new IOException("write failed"));
            Log.e(TAG, "onCharacteristicWrite: write failed");
            return;
        }
        delegate.onCharacteristicWrite(gatt, characteristic, status);
        if(canceled)
            return;
        if(characteristic == writeCharacteristic) { // NOPMD - test object identity
            Log.d(TAG,"write finished, status="+status);
            writeNext();
        }
    }

    private void writeNext() {
        final byte[] data;
        synchronized (writeBuffer) {
            if (!writeBuffer.isEmpty() && delegate.canWrite()) {
                writePending = true;
                data = writeBuffer.remove(0);
            } else {
                writePending = false;
                data = null;
            }
        }
        if(data != null) {
            writeCharacteristic.setValue(data);
            if (!gatt.writeCharacteristic(writeCharacteristic)) {
                // onSerialIoError(new IOException("write failed"));
                Log.e(TAG, "writeNext: write failed");
            } else {
                Log.d(TAG,"write started, len="+data.length);
            }
        }
    }

    /**
     * SerialListener
     */
    private void onSerialConnect() {
        Log.d(TAG, "onSerialConnect: on serial connect");
        if (device.getAddress().equals(mainActivity().getLMac())) {
            mainActivity().setLeftPairedBtnMode(MainActivity.MODE_CONNECTED);
        } else {
            mainActivity().setRightPairedBtnMode(MainActivity.MODE_CONNECTED);
        }

    }
    
    private void onSerialRead(byte[] data) {
        String messageStr = "";
        for (byte datum : data) {
            messageStr += String.format("%02X ", datum);
        }
        Log.d(TAG, "onSerialRead: on serial read: " + messageStr);
        mainActivity().parseAndExecute(data);
        completeTask();
    }

    /**
     * device delegates
     */

    private class Cc245XDelegate extends DeviceDelegate {
        @Override
        boolean connectCharacteristics(BluetoothGattService gattService) {
            Log.d(TAG, "service cc254x uart");
            readCharacteristic = gattService.getCharacteristic(BLUETOOTH_LE_CC254X_CHAR_RW);
            writeCharacteristic = gattService.getCharacteristic(BLUETOOTH_LE_CC254X_CHAR_RW);
            return true;
        }
    }

    private MainActivity mainActivity() {
        return (MainActivity)activity;
    }

    private abstract class Task implements Runnable {

        public abstract void complete();

    }

    private class TaskSendMessage extends Task {

        private byte[] message;

        public TaskSendMessage(byte[] message) {
            this.message = message;
            Log.d(TAG, "TaskSendMessage: new task created: " + message);
        }

        @Override
        public void run() {
            String messageStr = "";
            for (byte datum : message) {
                messageStr += String.format("%02X ", datum);
            }
            Log.d(TAG, "taskSendMessage: sending " + messageStr);
            try{
                write(message);
            } catch (IOException e) {
                Log.e(TAG, "run: " + e.getMessage());
                completeTask();
            }
        }

        @Override
        public void complete() {
            Log.d(TAG, "complete: task completed");
        }
    }

    private Queue<Task> tasks = new ArrayDeque<>();
    boolean queueBusy = false;

    private void nextTask() {
        if (queueBusy) return;

        if (tasks.size() > 0) {
            queueBusy = true;
            tasks.peek().run();
        }
    }

    private void completeTask() {
        if (tasks.size() > 0) {
            tasks.peek().complete();
            tasks.poll();
            queueBusy = false;
            nextTask();
        }
    }

    public void send(byte[] message) {
        tasks.add(new TaskSendMessage(message));
        nextTask();
    }

}