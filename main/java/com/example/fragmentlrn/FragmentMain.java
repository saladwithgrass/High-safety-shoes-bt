package com.example.fragmentlrn;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import org.jetbrains.annotations.NotNull;

public class FragmentMain extends Fragment {

    ImageButton lighterBtn, settingsBtn, onOffButton;
    ImageView leftBar, rightBar;
    boolean isOn, lighterIsOn;
    TextView modeTv, rightTv, temperatureTv, leftBatTv, rightBatTv;


    String TAG = "mainFrag";

    @Override
    public View onCreateView (LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_main, container, false);
        lighterBtn = view.findViewById(R.id.lighterButton);
        settingsBtn = view.findViewById(R.id.settingsButton);
        onOffButton = view.findViewById(R.id.onOffBtn);
        leftBar = view.findViewById(R.id.leftBatteryIv);
        rightBar = view.findViewById(R.id.rightBatteryIv);
        temperatureTv = view.findViewById(R.id.temperatureTv);
        leftBatTv = view.findViewById(R.id.leftChargeTv);
        rightBatTv = view.findViewById(R.id.rightChargeTv);
        Log.d(TAG, "onCreateView: main fragment started");

        modeTv = view.findViewById(R.id.modeTv);
        Log.d("tag", "onCreate: textwiew null: " + (modeTv == null));
        String firstString = "РЕЖИМ | ";
        String secondString = "ЭКО";
        SpannableStringBuilder stringBuilder = new SpannableStringBuilder(firstString + secondString);
        stringBuilder.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), firstString.length(),
                firstString.length() + secondString.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);


        rightTv = view.findViewById(R.id.rightTextTv);

        modeTv.setText(stringBuilder);
        settingsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick: settings clicked");
                ((MainActivity)getActivity()).navigateTo(2);
            }
        });
        isOn = lighterIsOn = false;
        onOffButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setOnOff(isOn = !isOn);
            }
        });
        lighterBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setLighter(lighterIsOn = !lighterIsOn);
            }
        });



        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        DisplayMetrics displayMetrics = getContext().getResources().getDisplayMetrics();

        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) rightTv.getLayoutParams();
        int margin = (int)Math.round(0.078125 * (rightBar.getWidth() / (displayMetrics.xdpi/displayMetrics.DENSITY_DEFAULT)));
        params.rightMargin = (int)(0.078125 * rightBar.getWidth());
        rightTv.setLayoutParams(params);
        Log.d(TAG, "onResume: " + margin+" dp, " +  (rightBar.getWidth()) + "/" + (displayMetrics.xdpi/displayMetrics.DENSITY_DEFAULT));
    }

    public void setModeText(String mode) {
        String firstString = "РЕЖИМ | ";
        String secondString = mode;
        SpannableStringBuilder stringBuilder = new SpannableStringBuilder(firstString + secondString);
        stringBuilder.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), firstString.length(),
                firstString.length() + secondString.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        modeTv.setText(stringBuilder);
    }

    public boolean isOn() {
        return isOn;
    }

    private void update() {
        ((MainActivity)getActivity()).sendMessage();
    }

    public void setOnOff(boolean on) {
        ((MainActivity)getActivity()).setOn(on);
        if (on) {
            onOffButton.setImageResource(R.drawable.turn_off_drawable);
        } else {
            onOffButton.setImageResource(R.drawable.turn_on_drawable);
        }
        update();
    }

    private void setLighter(boolean on) {
        ((MainActivity)getActivity()).setLighterOn(on);
        if (on) {
            lighterBtn.setImageResource(R.drawable.lighter_on_drawable);
        } else {
            lighterBtn.setImageResource(R.drawable.lighter_off_drawable);
        }
        update();
    }

    public void showToast(String msg) {
        Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT);
    }

    public void setTemperature(String text) {
        temperatureTv.setText(text);
    }

    public void setLeftBatteryPerCent(int percent) {
        if (percent < 0 || percent > 100) {
            Log.e(TAG, "setLeftBatteryPerCent: wrong percentage " + String.valueOf(percent));
            return;
        }

        Log.d(TAG, "setLeftBatteryPerCent: " + String.valueOf(percent));

        leftBatTv.setText(percent + "%");

        if (percent < 20) {
            leftBar.setImageResource(R.drawable._20_battery_drawable);
        } else if (percent < 40) {
            leftBar.setImageResource(R.drawable._40_battery_drawable);
        } else if (percent < 60) {
            leftBar.setImageResource(R.drawable._60_battery_drawable);
        } else if (percent < 80) {
            leftBar.setImageResource(R.drawable._80_battery_drawable);
        } else {
            leftBar.setImageResource(R.drawable._100_battery_drawable);
        }
    }

    public void setRightBatteryPerCent(int percent) {
        if (percent < 0 || percent > 100) {
            Log.e(TAG, "setRightBatteryPerCent: wrong percentage " + String.valueOf(percent));
            return;
        }

        Log.d(TAG, "setRightBatteryPerCent: " + String.valueOf(percent));

        rightBatTv.setText(percent + "%");

        if (percent < 20) {
            rightBar.setImageResource(R.drawable._20_battery_drawable);
        } else if (percent < 40) {
            rightBar.setImageResource(R.drawable._40_battery_drawable);
        } else if (percent < 60) {
            rightBar.setImageResource(R.drawable._60_battery_drawable);
        } else if (percent < 80) {
            rightBar.setImageResource(R.drawable._80_battery_drawable);
        } else {
            rightBar.setImageResource(R.drawable._100_battery_drawable);
        }
    }

}
