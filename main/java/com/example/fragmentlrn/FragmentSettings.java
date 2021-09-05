package com.example.fragmentlrn;

import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.example.fragmentlrn.MainActivity.getMainActivity;

public class FragmentSettings extends Fragment {

    private String TAG = "SettingsFrag";

    private final int MODE_ECO = 0, MODE_NORM = 1, MODE_MAX = 2;
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
        lDevices.addAll(getMainActivity(getActivity()).adapter.getBondedDevices());
        dialogFound = new DialogFound(lDevices);

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
                dialogFound.show(getActivity().getSupportFragmentManager(), "left");
            }
        });

        rightConnectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialogFound.show(getActivity().getSupportFragmentManager(), "right");
            }
        });

        listener = new RecyclerAdapter.ItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {

            }
        };
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
        FragmentMain mainFragment = (FragmentMain)(getMainActivity(getActivity()).getFragment(1));
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
        if (used) {
            startTimeBtn.setBackgroundResource(R.drawable.timer_on_button_drawable);
            endTimeBtn.setBackgroundResource(R.drawable.timer_on_button_drawable);
        } else {
            startTimeBtn.setBackgroundResource(R.drawable.timer_off_button_drawable);
            endTimeBtn.setBackgroundResource(R.drawable.timer_off_button_drawable);
        }
    }

}
