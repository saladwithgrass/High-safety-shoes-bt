package com.example.fragmentlrn;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.example.fragmentlrn.databinding.ActivityMainBinding;

/*
* So, if you happen to be working with this app, first of all, good luck,
* you'll need it,
* second - try to find SimpleBleTerminal by kai-morich on github,
* it will help you understand some of my code better
* and i'm sorry that you have to work with this ruptured version of bluetooth terminal glued to
* that UI
* */

public class MainActivity extends AppCompatActivity {

    public static final int LIGHTER_MODE_0 = 0;
    public static final int LIGHTER_MODE_1 = 1;
    public static final int LIGHTER_MODE_2 = 2;
    public static final int TEMP_MODE_OFF = 0;
    public static final int TEMP_MODE_ECO = 1;
    public static final int TEMP_MODE_NORM = 2;
    public static final int TEMP_MODE_MAX = 3;

    static public final int MODE_CONNECTING = 0;
    static public final int MODE_SCANNING = 1;
    static public final int MODE_CONNECTED = 2;
    static public final int MODE_SCANNED = 3;
    static public final int MODE_DISCONNECTED = 4;
    static public final int MODE_NOT_FOUND = 5;

    static public final int DELAY_BETWEEN_REQUESTS_MILLIS = 30000;

    public String lName, rName;

    private ActivityMainBinding binding;

    private PagerAdapter mPagerAdapter;
    private ViewPager2 mViewPager;

    private FragmentMain fragmentMain;
    private FragmentSettings fragmentSettings;
    private FragmentLogo fragmentLogo;
    private int selected;
    private BluetoothDevice lastChecked;
    private SerialSocket lSocket, rSocket;
    public BluetoothScanner btScanner;


    public SimpleBluetoothService service;
    public BluetoothAdapter adapter;

    private Handler timerHandler = new Handler(), requestHandler = new Handler();

    private int leftConnectBtnMode, rightConnectBtnMode;
    private int currentFragment = 0;
    private String TAG = "MainActivity";


    static public MainActivity toMainActivity(Context context) {
        return (MainActivity)context;
    }

    public static int pxToSp(float px, Context context) {
        return (int)(px / context.getResources().getDisplayMetrics().scaledDensity);
    }

    public RecyclerAdapter.ItemClickListener getListenerFromSetting() {
        return  fragmentSettings.listener;
    }


    public void setSelected(int selected) {
        this.selected = selected;
    }

    public void setLastChecked(BluetoothDevice device) {
        lastChecked = device;
    }

    private MainActivity mainActivity() { return this;}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        mPagerAdapter = new PagerAdapter(this);
        mViewPager = (ViewPager2) findViewById(R.id.container);
        mViewPager.setUserInputEnabled(false);
        setupViewPager(mViewPager);
        navigateTo(0);
        lName = getString(R.string.leftBootName);
        rName = getString(R.string.rightBootName);
        forgetRightAddress();
        forgetLeftAddress();
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                navigateTo(1);
            }
        }, 1000);
        // forgetLeftAddress();
        // forgetRightAddress();
        adapter = BluetoothAdapter.getDefaultAdapter();
        if (!adapter.isEnabled()) {
            Log.d(TAG, "onCreate: bluetooth is not enabled, enabling");
            Intent enable = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            openSomeActivityForResult(enable);
        } else {
            btScanner = new BluetoothScanner(this, adapter);
            btScanner.startScan();
            startRequestCycle();
            // service = new SimpleBluetoothService(this);
            if (!getLMac().equals("errL")) {
                serialSocketConnectLeft(adapter.getRemoteDevice(getLMac()));
            } else {
                Log.d(TAG, "onCreate: ");
            }
        }

        if (!checkPermissions() || !adapter.isEnabled())
            Log.e(TAG, "onCreate: required permissions not granted", new Exception("required permissions not granted"));

    }

    public ActivityResultLauncher<Intent> someActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Log.d(TAG, "onActivityResult: bt enabled, everything is cool");
                        // service = new SimpleBluetoothService(getMainActivity(mainActivity()));
                        btScanner = new BluetoothScanner(mainActivity(), adapter);
                        btScanner.startScan();
                        startRequestCycle();
                        // Intent data = result.getData();
                    } else if (result.getResultCode() == RESULT_CANCELED) {
                        Log.e(TAG, "onActivityResult: bluetooth not enabled" );
                        android.os.Process.killProcess(android.os.Process.myPid());
                    }
                }
            });

    public void openSomeActivityForResult(Intent intent) {
        someActivityResultLauncher.launch(intent);
    }

    @Override
    public void onBackPressed() {
        if (currentFragment == 2) {
            navigateTo(1);
        } else {
            super.onBackPressed();
        }
    }

    private void setupViewPager(ViewPager2 viewPager) {
        PagerAdapter adapter = new PagerAdapter(this);
        adapter.addFragment(new FragmentLogo());
        adapter.addFragment(new FragmentMain());
        adapter.addFragment( new FragmentSettings());
        fragmentMain = (FragmentMain)adapter.createFragment(1);
        fragmentSettings = (FragmentSettings)adapter.createFragment(2);
        fragmentLogo = (FragmentLogo)adapter.createFragment(0);
        viewPager.setAdapter(adapter);
    }

    public void navigateTo(int id) {
        currentFragment = id;
        mViewPager.setCurrentItem(id);
    }

    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (getApplicationContext().checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
                return false;
            }
        }
        return true;
    }

    private String timeToStr(int h, int m) {
        String time = "";
        if ((h / 10) == 0) time += "0";
        time += String.valueOf(h) + ":";
        if ((m / 10) == 0) time += "0";
        time += String.valueOf(m);
        return time;
    }

    boolean timerIsUsed = false;

    Runnable onTimerEnd = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "run: time to stop heating has come");
            fragmentMain.setOnOff(false);
            timerHandler.postDelayed(this, 24 * 60 * 60 * 1000);
        }
    },
    onTimerStart = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "run: time to start heating has come");
            fragmentMain.setOnOff(true);
            timerHandler.postDelayed(this, 24 * 60 * 60 * 1000);
        }
    };

    public void setStartTime(int hours, int minutes) {
        timeOnMinutes = hours * 60 + minutes;
        fragmentSettings.setStartTime(timeToStr(hours, minutes));
        reScheduleTimerStart(timeOnMinutes);
    }

    public void setEndTime(int hours, int minutes) {
        timeOffMinutes = hours * 60 + minutes;
        fragmentSettings.setEndTime(timeToStr(hours, minutes));
        reScheduleTimerEnd(timeOffMinutes);
    }

    private void reScheduleTimerStart(int timeInMinutes) {
        long timeToStartMillis = timeInMinutes * 60 * 1000;
        long currentMillis = System.currentTimeMillis();
        timerHandler.removeCallbacks(onTimerStart);
        if (currentMillis > timeToStartMillis) timeToStartMillis += 24 * 60 * 60 * 1000;
        timerHandler.postDelayed(onTimerStart, timeToStartMillis - currentMillis);
    }

    private void reScheduleTimerEnd(int timeInMinutes) {
        Log.d(TAG, "reScheduleTimerEnd: starting");
        long timeToEndMillis = timeInMinutes * 60 * 1000;
        long currentMillis = System.currentTimeMillis();
        Log.d(TAG, "reScheduleTimerEnd: now: " + currentMillis / 1000 / 3600 + ':' + (currentMillis / 1000 / 60) % 60);
        Log.d(TAG, "reScheduleTimerEnd: time to end: " + timeToEndMillis / 3600000 + ":" + (timeToEndMillis / 1000 / 60) % 60);
        timerHandler.removeCallbacks(onTimerEnd);
        if (currentMillis > timeToEndMillis) timeToEndMillis += 24 * 60 * 60 * 1000;
        timerHandler.postDelayed(onTimerEnd, timeToEndMillis - currentMillis);
    }

    public void setTimerUsed(boolean used) {
        timerIsUsed = used;
        if (used) {
            setStartTime(timeOnMinutes / 60, timeOnMinutes % 60);
            setEndTime(timeOffMinutes / 60, timeOffMinutes % 60);
        } else {
            timerHandler.removeCallbacks(onTimerEnd);
            timerHandler.removeCallbacks(onTimerStart);
        }
    }

    public void startRequestCycle() {
        requestHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "run: delayed request ");
                sendMessage();
                requestHandler.postDelayed(this, DELAY_BETWEEN_REQUESTS_MILLIS);
            }
        }, DELAY_BETWEEN_REQUESTS_MILLIS);
    }

    public void setLeftPairedBtnMode(int mode) {
        Log.d(TAG, "setLeftPairedBtnMode: " + mode);
        /*if (mode == MODE_CONNECTED) fragmentSettings.setLeftButtonMode(mode);
        if (mode == MODE_DISCONNECTED) fragmentSettings.setLeftButtonMode(mode);
        switch (mode) {
            case MODE_CONNECTED: fragmentSettings.setLeftButtonMode(mode); break;
            case MODE_CONNECTING: fragmentSettings.setLeftButtonMode(mode); break;
            case MODE_DISCONNECTED: fragmentSettings.setLeftButtonMode(mode); break;
        }*/
        fragmentSettings.setLeftButtonMode(mode);
    }

    public int getLeftPairedBtnMode() {
        return leftConnectBtnMode;
    }

    public void setRightPairedBtnMode(int mode) {
        Log.d(TAG, "setRightPairedBtnMode: " + mode);
        /*if (mode == MODE_CONNECTED) fragmentSettings.setRightButtonMode(mode);
        if (mode == MODE_DISCONNECTED) fragmentSettings.setRightButtonMode(mode);
        switch (mode) {
            case MODE_CONNECTED: fragmentSettings.setRightButtonMode(mode); break;
            case MODE_CONNECTING: fragmentSettings.setRightButtonMode(mode); break;
            case MODE_DISCONNECTED: fragmentSettings.setRightButtonMode(mode); break;
        }*/
        fragmentSettings.setRightButtonMode(mode);
    }

    public int getRightPairedBtnMode() {
        return rightConnectBtnMode;
    }

    public void pairingFinished() {
        // message might be sent to nothing, but expect answer
    }

    public void parseAndExecute(String message) {
    }

    public void parseAndExecute(byte[] message) {
        if (message.length < 20) {
            Log.d(TAG, "parseAndExecute: that's no reply");
            return;
        }
        int pwm = (int)message[4], temp = (int)message[2] / 2,
                batPercent = (int)message[6], batV = (int)message[7],
                lighter = (int)message[5], side = (int)message[1];
        Runnable execute = new Runnable() {
            @Override
            public void run() {
                fragmentSettings.setModeDontUpdate(pwm);
                fragmentMain.setTemperature(String.valueOf(temp) + "°");
                if (side == 1) {
                    // left
                    fragmentMain.setLeftBatteryPerCent(batPercent);
                } else if (side == 2) {
                    // right
                    fragmentMain.setRightBatteryPerCent(batPercent);
                } else {
                    // service
                    Log.d(TAG, "parseAndExecute: service message, ignoring");
                }
            }
        };

        runOnUiThread(execute);

    }

    public Fragment getFragment(int index) {
        switch (index) {
            case 0: return fragmentLogo;
            case 1: return fragmentMain;
            case 2: return fragmentSettings;
        }
        return null;
    }

    public static MainActivity getMainActivity(Activity activity){
        return (MainActivity)activity;
    }

    public void rememberLeftAddress(String leftMac) {
        SharedPreferences preferences = this.getSharedPreferences("my_prefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("leftmac", leftMac);
        editor.apply();
        // service.setlMac(leftMac);
    }

    public void rememberRightAddress(String rightMac) {
        SharedPreferences preferences = this.getSharedPreferences("my_prefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("rightmac", rightMac);
        editor.apply();
        // service.setrMac(rightMac);
    }

    private void forgetLeftAddress() {
        SharedPreferences preferences = this.getSharedPreferences("my_prefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.remove("leftmac");
        editor.apply();
    }

    private void forgetRightAddress() {
        SharedPreferences preferences = this.getSharedPreferences("my_prefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.remove("rightmac");
        editor.apply();
    }

    public String getLMac() {
        SharedPreferences preferences = this.getSharedPreferences("my_prefs", Context.MODE_PRIVATE);
        return preferences.getString("leftmac", "errL");
    }

    public String getRMac() {
        SharedPreferences preferences = this.getSharedPreferences("my_prefs", Context.MODE_PRIVATE);
        return preferences.getString("rightmac", "errR");
    }

    public void updateList() {
        Log.d(TAG, "updateList: updating list");
        if (fragmentSettings.isVisible()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    fragmentSettings.updateList();
                }
            });
        }
    }

    public void scanForDevices() {
        // service.startScanNow();
        btScanner.startScan();
    }

    public void connectTo(BluetoothDevice device) {
        if (device == null) {
            Log.e(TAG, "connectTo: device is null");
            return;
        }

        if (device.getName() == null) {
            Log.e(TAG, "connectTo: device name is null");
            return;
        }

        if (device.getName().contains(getString(R.string.leftBootName))) {
            Log.d(TAG, "connectTo: left device");
            service.connectLeft(device);
        } else if (device.getName().contains(getString(R.string.rightBootName))) {
            Log.d(TAG, "connectTo: right device");
            service.connectRight(device);
        } else {
            Log.e(TAG, "connectTo: device is neither left nor right");
        }
    }

    private static byte[] createMessage(byte setTemp, byte pwm, byte lightMode) {
        byte[] message = new byte[20];
        for (byte byt : message) {
            byt = 0x00;
        }
        message[0] = (byte) 0x24;
        message[1] = (byte) 0x00;
        message[3] = setTemp;
        message[4] = pwm;
        message[5] = lightMode;
        byte xor = message[1];
        for (int co = 2; co < message.length; ++co) {
            xor = (byte)(xor ^ message[co]);
        }
        message[19] = xor;
        return message;
    }

    public static byte[] createMessage(byte pwm, byte lightMode) {
        byte[] message = new byte[20];
        for (byte byt : message) {
            byt = 0x00;
        }
        message[0] = (byte) 0x24;
        message[1] = (byte) 0x00;
        // message[3] = setTemp;
        message[4] = pwm;
        message[5] = lightMode;
        byte xor = message[1];
        for (int co = 2; co < message.length; ++co) {
            xor = (byte)(xor ^ message[co]);
        }
        message[19] = xor;
        return message;
    }

    private int powerMode = 1, timeOnMinutes, timeOffMinutes;
    private boolean isOn = false, lighterIsOn = false;

    public void setOn(boolean on) { isOn = on; }
    public void setLighterOn(boolean on) {lighterIsOn = on;}
    public void setPowerMode(int mode) {
        powerMode = mode;
    }
    public void setTimeOn(int timeInMinutes) {timeOnMinutes = timeInMinutes;}
    public void setTimeOff(int timeInMinutes) {
        timeOffMinutes = timeInMinutes;}

    public byte[] getDefaultMessage() {
        byte[] message = new byte[20];
        for (byte byt : message) {
            byt = 0x00;
        }
        message[0] = (byte) 0x24;
        message[1] = (byte) 0x00;
        // message[3] = setTemp;
        message[4] = (byte)((isOn ? 1 : 0) * powerMode);
        message[5] = (byte)(lighterIsOn ? 1 : 0);
        byte xor = message[1];
        for (int co = 2; co < message.length; ++co) {
            xor = (byte)(xor ^ message[co]);
        }
        message[19] = xor;
        return message;
    }

    public void sendMessage() {
        byte pwm = (byte)((isOn ? 1 : 0) * powerMode);
        byte lighter = (byte)(lighterIsOn ? 1 : 0);
        byte[] message = createMessage(pwm, lighter);
        // Log.d(TAG, "sendMessage: is on: " + isOn);
        // Log.d(TAG, "sendMessage: pwm: " + powerMode);
        // Log.d(TAG, "sendMessage: lighter: " + lighterIsOn);
        // Log.d(TAG, "sendMessage: pwm: " + pwm);
        if (lSocket != null) lSocket.send(message);
        if (rSocket != null) rSocket.send(message);
    }

    public void toast(String msg) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(mainActivity(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void setTemperature(int temperature) {

    }

    public void setTextInTemperature(String text) {
        fragmentMain.setTemperature(text);
    }

    public void serialSocketConnectLeft(BluetoothDevice device) {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            // BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            // status("connecting...");
            // connected = Connected.Pending;
            lSocket = new SerialSocket(mainActivity().getApplicationContext(), this,  device, true);
            lSocket.connect();
        } catch (Exception e) {
            //onSerialConnectError(e);
        }
    }

    public void serialSocketConnectRight(BluetoothDevice device) {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            // BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            // status("connecting...");
            // connected = Connected.Pending;
            rSocket = new SerialSocket(mainActivity().getApplicationContext(), this, device, false);
            rSocket.connect();
        } catch (Exception e) {
            //onSerialConnectError(e);
        }
    }

    public void updateSelected(BluetoothDevice device) {
        if (fragmentSettings.isVisible()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    fragmentSettings.updateListWithConnected(device);
                }
            });
        }

    }

    public BluetoothDevice getLeftBoot() {
        if (lSocket == null) return null;
        return lSocket.getDevice();
    }

    public BluetoothDevice getRightBoot() {
        if (rSocket == null) return null;

        return rSocket.getDevice();
    }

}