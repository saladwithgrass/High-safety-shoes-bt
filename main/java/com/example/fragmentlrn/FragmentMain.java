package com.example.fragmentlrn;

import android.graphics.Color;
import android.os.Bundle;
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
    TextView textView, rightTv;


    String TAG = "mainFrag";

    @Override
    public View onCreateView (LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_main, container, false);
        lighterBtn = view.findViewById(R.id.lighterButton);
        settingsBtn = view.findViewById(R.id.settingsButton);
        onOffButton = view.findViewById(R.id.onOffBtn);
        leftBar = view.findViewById(R.id.leftBatteryIv);
        rightBar = view.findViewById(R.id.rightBatteryIv);
        Log.d(TAG, "onCreateView: main fragment started");

        textView = view.findViewById(R.id.modeTv);
        Log.d("tag", "onCreate: textwiew null: " + (textView == null));
        String firstString = "РЕЖИМ | ";
        String secondString = "ЭКО";
        SpannableStringBuilder stringBuilder = new SpannableStringBuilder(firstString + secondString);
        stringBuilder.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), firstString.length(),
                firstString.length() + secondString.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);


        rightTv = view.findViewById(R.id.rightTextTv);

        textView.setText(stringBuilder);
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
        textView.setText(stringBuilder);
    }

    private void setOnOff(boolean on) {
        if (on) {
            onOffButton.setImageResource(R.drawable.turn_off_drawable);
        } else {
            onOffButton.setImageResource(R.drawable.turn_on_drawable);
        }
    }

    private void setLighter(boolean on) {
        if (on) {
            lighterBtn.setImageResource(R.drawable.lighter_on_drawable);
        } else {
            lighterBtn.setImageResource(R.drawable.lighter_off_drawable);
        }
    }
}
