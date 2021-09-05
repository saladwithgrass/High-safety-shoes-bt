package com.example.fragmentlrn;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.viewpager2.widget.ViewPager2;

import com.example.fragmentlrn.databinding.ActivityMainBinding;


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

    private ActivityMainBinding binding;

    private PagerAdapter mPagerAdapter;
    private ViewPager2 mViewPager;

    private FragmentMain fragmentMain;
    private FragmentSettings fragmentSettings;
    private FragmentLogo fragmentLogo;

    public BluetoothAdapter adapter;


    static public MainActivity toMainActivity(Context context) {
        return (MainActivity)context;
    }

    public RecyclerAdapter.ItemClickListener getListenerFromSetting() {
        return  fragmentSettings.listener;
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

    private String timeToStr(int h, int m) {
        String time = "";
        if ((h / 10) == 0) time += "0";
        time += String.valueOf(h) + ":";
        if ((m / 10) == 0) time += "0";
        time += String.valueOf(m);
        return time;
    }

    public void setStartTime(int hours, int minutes) {

        fragmentSettings.setStartTime(timeToStr(hours, minutes));
    }

    public void setEndTime(int hours, int minutes) {
        fragmentSettings.setEndTime(timeToStr(hours, minutes));
    }

    public void setLeftPairedBtnMode(int modeConnected) {
    }

    public void pairingFinished() {
    }

    public void setRightPairedBtnMode(int modeConnected) {
    }

    public void parseAndExecute(String message) {
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
}