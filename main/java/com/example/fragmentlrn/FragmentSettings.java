package com.example.fragmentlrn;

import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;

import static android.util.TypedValue.COMPLEX_UNIT_PX;
import static com.example.fragmentlrn.MainActivity.MODE_CONNECTED;
import static com.example.fragmentlrn.MainActivity.MODE_CONNECTING;
import static com.example.fragmentlrn.MainActivity.MODE_DISCONNECTED;
import static com.example.fragmentlrn.MainActivity.getMainActivity;
import static com.example.fragmentlrn.MainActivity.pxToSp;

public class FragmentSettings extends Fragment {

    private String TAG = "SettingsFrag";

    private final int MODE_ECO = 1, MODE_NORM = 2, MODE_MAX = 3;
    private List<BluetoothDevice> lDevices, rDevices;
    private ImageButton backBtn, leftConnectBtn, rightConnectBtn;
    private Button ecoBtn, normBtn, maxBtn, startTimeBtn, endTimeBtn;
    private SwitchCompat timerSw;
    boolean timerUsed;
    private TimeDialogFragment timeDialogFragment;
    private DialogFound dialogFound;

    public RecyclerAdapter.ItemClickListener listener;

    public void setStartTime(String text) {
        startTimeBtn.setText(text);
    }

    public void setEndTime(String text) {
        endTimeBtn.setText(text);
    }

    @Override
    public View onCreateView( LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);
        backBtn = view.findViewById(R.id.backButton);
        ecoBtn = view.findViewById(R.id.ecoButton);
        normBtn = view.findViewById(R.id.normButton);
        maxBtn = view.findViewById(R.id.maxButton);
        timerSw = view.findViewById(R.id.timerSw);
        startTimeBtn = view.findViewById(R.id.startTimeBtn);
        endTimeBtn = view.findViewById(R.id.finishTimeBtn);
        leftConnectBtn = view.findViewById(R.id.leftConnectionStateBtn);
        rightConnectBtn = view.findViewById(R.id.rightConnectionStateBtn);
        timeDialogFragment = new TimeDialogFragment();
        lDevices = new ArrayList<>();
        rDevices = new ArrayList<>();
        lDevices.addAll(mainActivity().adapter.getBondedDevices());
        dialogFound = new DialogFound(lDevices);

        setLeftButtonMode(MODE_CONNECTING);
        setRightButtonMode(MODE_CONNECTING);

        timerUsed = false;

        timerSw.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setTimerUsed(timerUsed = !timerUsed);
            }
        });

        ecoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setMode(MODE_ECO);
            }
        });

        normBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setMode(MODE_NORM);
            }
        });

        maxBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setMode(MODE_MAX);
            }
        });

        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((MainActivity)getActivity()).navigateTo(1);
            }
        });

        startTimeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                timeDialogFragment.customShow(getActivity().getSupportFragmentManager(), "start");
            }
        });

        endTimeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                timeDialogFragment.customShow(getActivity().getSupportFragmentManager(), "end");
            }
        });

        leftConnectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mainActivity().scanForDevices();
                showListOfDevices("left");
            }
        });

        rightConnectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mainActivity().scanForDevices();
                showListOfDevices("right");
            }
        });

        try {
            Log.d(TAG, "onCreateView: " + getContext().getPackageManager().getActivityInfo(getActivity().getComponentName(), 0).getThemeResource());
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "onCreateView: " + e.getMessage() );
            Toast.makeText(getContext(), "error", Toast.LENGTH_LONG).show();
        }
        /*Log.d(TAG, "onCreateView: " + endTimeBtn.getBackground());*/
        setEcoBtn(true);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: " + ecoBtn.getTextSize());

        if (ecoBtn.getHeight() != normBtn.getHeight()) {
            Log.d(TAG, "onResume: heights are different");
            Log.d(TAG, "onResume: eco: " + ecoBtn.getHeight() + " norm: " + normBtn.getHeight());
            Log.d(TAG, "onResume: text size: " + ecoBtn.getTextSize());
            ecoBtn.setTextSize(COMPLEX_UNIT_PX, ecoBtn.getTextSize() - 10);
            normBtn.setTextSize(COMPLEX_UNIT_PX,normBtn.getTextSize() - 10);
            maxBtn.setTextSize(COMPLEX_UNIT_PX,maxBtn.getTextSize() - 10);

        }
        // checkModeButtonHeight();
    }

    private void checkModeButtonHeight() {
        if (ecoBtn.getHeight() != normBtn.getHeight() && (ecoBtn.getTextSize() > 30)) {
            Log.d(TAG, "checkModeButtonHeight: eco and norm buttons have unequal height");
            Log.d(TAG, "checkModeButtonHeight: scaling font down to " + (ecoBtn.getTextSize() - 1));
            ecoBtn.setTextSize(pxToSp(ecoBtn.getTextSize() - 1, getContext()));
            normBtn.setTextSize( pxToSp(normBtn.getTextSize() - 1, getContext()));
            maxBtn.setTextSize(pxToSp(maxBtn.getTextSize() - 1, getContext()));
            checkModeButtonHeight();
        }
        Log.d(TAG, "checkModeButtonHeight: all ok");
        normBtn.setHeight(ecoBtn.getHeight());
        Log.d(TAG, "checkModeButtonHeight: eco: " + ecoBtn.getHeight() + " norm: " + normBtn.getHeight());
    }

    private MainActivity mainActivity() {
        return getMainActivity(getActivity());
    }

    private void update() {
        mainActivity().sendMessage();
    }

    private void showListOfDevices(String tag) {
        if (!tag.equals("right") && !tag.equals("left")) {
            Log.e(TAG, "showListOfDevices: wrong tag: " + tag);
            return;
        }
        Log.d(TAG, "showListOfDevices: showing " + tag + " devices");
        // dialogFound.setDeviceList(tag.equals("right") ? mainActivity().service.getRightDevicesList() : mainActivity().service.getLeftDevicesList(), tag.equals("left"));
        dialogFound.show(getActivity().getSupportFragmentManager(), tag);
    }

    private void setEcoBtn(boolean on) {
        Drawable d;
        int color;
        if (on) {
            d = ResourcesCompat.getDrawable(getResources(), R.drawable.button_eco_on_drawable, getActivity().getTheme());
            color = getResources().getColor(R.color.black);
        } else {
            d = ResourcesCompat.getDrawable(getResources(), R.drawable.button_eco_off_drawable, getActivity().getTheme());
            color = getResources().getColor(R.color.techOrange);
        }
        ecoBtn.setBackground(d);
        ecoBtn.setTextColor(color);
    }

    private void setNormBtn(boolean on) {
        Drawable d;
        int color;
        if (on) {
            d = ResourcesCompat.getDrawable(getResources(), R.drawable.button_norm_on_drawable, getActivity().getTheme());
            color = getResources().getColor(R.color.black);
        } else {
            d = ResourcesCompat.getDrawable(getResources(), R.drawable.button_norm_off_drawable, getActivity().getTheme());
            color = getResources().getColor(R.color.techOrange);
        }
        normBtn.setBackground(d);
        normBtn.setTextColor(color);
    }

    private void setMaxBtn(boolean on) {
        Drawable d;
        int color;
        if (on) {
            d = ResourcesCompat.getDrawable(getResources(), R.drawable.button_max_on_drawable, getActivity().getTheme());
            color = getResources().getColor(R.color.black);
        } else {
            d = ResourcesCompat.getDrawable(getResources(), R.drawable.button_max_off_drawable, getActivity().getTheme());
            color = getResources().getColor(R.color.techOrange);
        }
        maxBtn.setBackground(d);
        maxBtn.setTextColor(color);
    }

    private void setMode(int mode) {
        Log.d(TAG, "setMode: " + endTimeBtn.getBackground());
        FragmentMain mainFragment = (FragmentMain)(mainActivity().getFragment(1));
        mainActivity().setPowerMode(mode);
        if (mode == MODE_ECO) {
            setEcoBtn(true);
            setNormBtn(false);
            setMaxBtn(false);
            mainFragment.setModeText("ЭКО");
        } else if(mode == MODE_NORM) {
            setEcoBtn(false);
            setNormBtn(true);
            setMaxBtn(false);
            mainFragment.setModeText("НОРМ");
        } else {
            setEcoBtn(false);
            setNormBtn(false);
            setMaxBtn(true);
            mainFragment.setModeText("МАКС");
        }
        update();
    }

    public void setModeDontUpdate(int mode) {
        Log.d(TAG, "setMode: " + endTimeBtn.getBackground());
        FragmentMain mainFragment = (FragmentMain)(mainActivity().getFragment(1));
        if (mode > 0) mainActivity().setPowerMode(mode);
        if (mode == MODE_ECO) {
            setEcoBtn(true);
            setNormBtn(false);
            setMaxBtn(false);
            mainFragment.setModeText("ЭКО");
        } else if(mode == MODE_NORM) {
            setEcoBtn(false);
            setNormBtn(true);
            setMaxBtn(false);
            mainFragment.setModeText("НОРМ");
        } else {
            setEcoBtn(false);
            setNormBtn(false);
            setMaxBtn(true);
            mainFragment.setModeText("МАКС");
        }
    }

    private void setTimerUsed(boolean used) {
        /*Toast.makeText(getContext(), "" + endTimeBtn.getBackground(), Toast.LENGTH_LONG);*/
        mainActivity().setTimerUsed(used);
        if (used) {
            startTimeBtn.setBackgroundResource(R.drawable.timer_on_button_drawable);
            endTimeBtn.setBackgroundResource(R.drawable.timer_on_button_drawable);
        } else {
            startTimeBtn.setBackgroundResource(R.drawable.timer_off_button_drawable);
            endTimeBtn.setBackgroundResource(R.drawable.timer_off_button_drawable);
        }
    }

    public void setLeftButtonMode(int mode) {
        Log.d(TAG, "setLeftButtonMode: " + mode);
        switch (mode) {
            case MODE_CONNECTED: setLeftButtonConnected(); break;
            case MODE_DISCONNECTED: setLeftButtonDisconnected(); break;
            case MODE_CONNECTING: setLeftButtonYellow(); break;
        }
    }

    public void setRightButtonMode(int mode) {
        Log.d(TAG, "setRightButtonMode: " + mode);
        switch (mode) {
            case MODE_CONNECTED: setRightButtonConnected(); break;
            case MODE_DISCONNECTED: setRightButtonDisconnected(); break;
            case MODE_CONNECTING: setRightButtonYellow(); break;

        }
    }

    public void showToast(String msg) {
        Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT);
    }

    private void setLeftButtonConnected() {
        Log.d(TAG, "setLeftButtonConnected: ");
        leftConnectBtn.setImageResource(R.drawable.left_on_drawable);
    }

    private void setLeftButtonDisconnected() {
        Log.d(TAG, "setLeftButtonDisconnected: ");
        leftConnectBtn.setImageResource(R.drawable.left_off_drawable);
    }

    private void setLeftButtonYellow() {
        Log.d(TAG, "setLeftButtonYellow: ");
        leftConnectBtn.setImageResource(R.drawable.left_yellow_drawable);
    }

    private void setRightButtonConnected() {
        Log.d(TAG, "setRightButtonConnected: ");
        rightConnectBtn.setImageResource(R.drawable.right_on_drawable);
    }

    private void setRightButtonDisconnected() {
        Log.d(TAG, "setRightButtonDisconnected: ");
        rightConnectBtn.setImageResource(R.drawable.right_off_drawable);
    }

    private void setRightButtonYellow() {
        Log.d(TAG, "setRightButtonYellow: ");
        rightConnectBtn.setImageResource(R.drawable.right_yellow_drawable);
    }

    public void updateList() {
        if (dialogFound == null) {
            Log.e(TAG, "updateList: dialog found is null");
            return;
        }

        Log.d(TAG, "updateList: updating list");
            if (dialogFound.isLeft()) {
                Log.d(TAG, "updateList: setting new left device list");
                dialogFound.setDeviceList(mainActivity().service.getLeftDevicesList(), true);
            } else {
                Log.d(TAG, "updateList: setting new right device list");
                dialogFound.setDeviceList(mainActivity().service.getRightDevicesList(), false);
            }
    }
    
}
