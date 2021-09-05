package com.example.fragmentlrn;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TimePicker;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import org.jetbrains.annotations.NotNull;

import static com.example.fragmentlrn.MainActivity.toMainActivity;

public class TimeDialogFragment extends DialogFragment {

    private String TAG = "TimePick";

    View view;

    boolean startTime;
    Button setTime;

    TimePicker picker;

    public void customShow(FragmentManager fragmentManager, String tag) {
        startTime = tag.equals("start");
        this.show(fragmentManager, tag);
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        getDialog().setTitle("TIME!");
        View v = inflater.inflate(R.layout.time_dialog, null);
        setTime = v.findViewById(R.id.setTimeBtn);
        picker = v.findViewById(R.id.timePicker);
        setTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int hours, minutes;
                int currentVersion = android.os.Build.VERSION.SDK_INT;   // this was done in order to differentiate between getHour() and getCurrentHour(), because the latter was deprecated
                hours = currentVersion > android.os.Build.VERSION_CODES.LOLLIPOP_MR1 ? picker.getHour() : picker.getCurrentHour();
                minutes = currentVersion > android.os.Build.VERSION_CODES.LOLLIPOP_MR1 ? picker.getMinute() : picker.getCurrentMinute();
                if (startTime) {
                    toMainActivity(getContext()).setStartTime(hours, minutes);
                } else {
                    toMainActivity(getContext()).setEndTime(hours, minutes);
                }
                dismiss();
            }
        });
        return v;
    }

    @Override
    public void onStart() {
        super.onStart();

    }
}
