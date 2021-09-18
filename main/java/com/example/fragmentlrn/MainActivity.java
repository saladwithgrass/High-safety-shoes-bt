package com.example.fragmentlrn;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.example.fragmentlrn.databinding.ActivityMainBinding;

import java.util.List;


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

    private ActivityMainBinding binding;

    private PagerAdapter mPagerAdapter;
    private ViewPager2 mViewPager;

    private FragmentMain fragmentMain;
    private FragmentSettings fragmentSettings;
    private FragmentLogo fragmentLogo;
    private int selected;
    private BluetoothDevice lastChecked;

    public SimpleBluetoothService service;
    public BluetoothAdapter adapter;

    private int leftConnectBtnMode, rightConnectBtnMode;
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
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                navigateTo(1);
            }
        }, 1000);
        adapter = BluetoothAdapter.getDefaultAdapter();
        service = new SimpleBluetoothService(this);
        if (!checkPermissions()) Log.e(TAG, "onCreate: required permissions not granted", new Exception("required permissions not granted"));

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

    public void setStartTime(int hours, int minutes) {
        timeOn = hours * 60 + minutes;
        fragmentSettings.setStartTime(timeToStr(hours, minutes));
    }

    public void setEndTime(int hours, int minutes) {
        timeOff = hours * 60 + minutes;
        fragmentSettings.setEndTime(timeToStr(hours, minutes));
    }

    public void setLeftPairedBtnMode(int mode) {
        Log.d(TAG, "setLeftPairedBtnMode: " + mode);
        if (mode == MODE_CONNECTED) fragmentSettings.setLeftButtonMode(mode);
        if (mode == MODE_DISCONNECTED) fragmentSettings.setLeftButtonMode(mode);
        switch (mode) {
            case MODE_CONNECTED: fragmentSettings.setLeftButtonMode(mode); break;
            case MODE_CONNECTING: fragmentSettings.setLeftButtonMode(mode); break;
            case MODE_DISCONNECTED: fragmentSettings.setLeftButtonMode(mode); break;
        }
    }

    public int getLeftPairedBtnMode() {
        return leftConnectBtnMode;
    }

    public void setRightPairedBtnMode(int mode) {
    }

    public int getRightPairedBtnMode() {
        return rightConnectBtnMode;
    }

    public void pairingFinished() {
    }

    public void parseAndExecute(String message) {
    }

    public void parseAndExecute(byte[] message) {}

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
        service.setlMac(leftMac);
    }

    public void rememberRightAddress(String rightMac) {
        SharedPreferences preferences = this.getSharedPreferences("my_prefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("rightmac", rightMac);
        editor.apply();
        service.setrMac(rightMac);
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
            fragmentSettings.updateList();
        }
    }

    public void scanForDevices() {
        service.startScan();
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
        byte xor = message[0];
        for (int co = 1; co < message.length; ++co) {
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
        byte xor = message[0];
        for (int co = 1; co < message.length; ++co) {
            xor = (byte)(xor ^ message[co]);
        }
        message[19] = xor;
        return message;
    }

    private int powerMode, timeOn, timeOff;
    private boolean isOn, lighterIsOn;

    public void setOn(boolean on) {isOn = on;}
    public void setLighterOn(boolean on) {lighterIsOn = on;}
    public void setPowerMode(int mode) {powerMode = mode;}
    public void setTimeOn(int time) {timeOn = time;}
    public void setTimeOff(int time) {timeOff = time;}

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
        byte xor = message[0];
        for (int co = 1; co < message.length; ++co) {
            xor = (byte)(xor ^ message[co]);
        }
        message[19] = xor;
        return message;
    }

    public void sendMessage() {
        byte pwm = (byte)((isOn ? 1 : 0) * powerMode);
        byte lighter = (byte)(lighterIsOn ? 1 : 0);
        byte[] message = createMessage(pwm, lighter);
        service.send(message);
    }

}