package com.example.fragmentlrn;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import static com.example.fragmentlrn.MainActivity.getMainActivity;

public class DialogFound extends DialogFragment {

    private String TAG = "DialogFound";

    private RecyclerView devicesRv = null;
    private Button btn = null;
    private List<BluetoothDevice> deviceList = null;
    private boolean left;

    public DialogFound(List<BluetoothDevice> deviceList) {
        this.deviceList = deviceList;
    }

    public RecyclerAdapter getAdapter() {
        return (RecyclerAdapter) devicesRv.getAdapter();
    }

    public boolean isLeft() {
        return getTag() != null && getTag().equals("left");
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        getDialog().setTitle("FOUND!");
        Log.d(TAG, "onCreateView: start");
        View v = inflater.inflate(R.layout.found_devices, null);
        RecyclerAdapter mAdapter;
        if (devicesRv != null) {
            if (devicesRv.getAdapter() != null) {
                mAdapter = ((RecyclerAdapter)devicesRv.getAdapter());
                // mAdapter = new RecyclerAdapter(getContext(), deviceList, ((RecyclerAdapter)devicesRv.getAdapter()).getSelectedIndex());
                mAdapter.noInit(getContext());
            } else {
                mAdapter = new RecyclerAdapter(getContext(), deviceList);
            }
        } else {
            mAdapter = new RecyclerAdapter(getContext(), deviceList);
        }
            devicesRv = v.findViewById(R.id.devicesRv);

        devicesRv.setLayoutManager(new LinearLayoutManager(getContext()));



        // mAdapter.setClickListener(toMainActivity(getContext()).getListenerFromSetting());
        devicesRv.setAdapter(mAdapter);
        btn = v.findViewById(R.id.connectBtn);
        setNewAdapterForButton(mAdapter);
        if (getTag() == null) {
            Log.e(TAG, "onCreateView: tag is null" );
        } else if (getTag().equals("left")) {
            setDeviceList(getMainActivity(getActivity()).service.getLeftDevicesList(), true);
        } else if (getTag().equals("right")) {
            setDeviceList(getMainActivity(getActivity()).service.getRightDevicesList(), false);
        } else {
            Log.e(TAG, "onCreateView: this is not right nor left");
        }
        return v;
    }

    public void setNewAdapterForButton(RecyclerAdapter adapterForButton) {
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick: device chosen for " + (left ? "left" : "right"));
                // error here because mAdapter was given as a value, not a reference
                Log.d(TAG, "onClick: device " + adapterForButton.getSelectedDevice().getName());
                if (left) {
                    getMainActivity(getActivity()).rememberLeftAddress(adapterForButton.getSelectedDevice().getAddress());
                } else {
                    getMainActivity(getActivity()).rememberRightAddress(adapterForButton.getSelectedDevice().getAddress());
                }
                getMainActivity(getActivity()).connectTo(adapterForButton.getSelectedDevice());
                dismiss();
            }
        });
    }

    public void setDeviceList(List<BluetoothDevice> deviceList, boolean left) {
        Log.d(TAG, "setDeviceList: setting new device list");
        if (devicesRv == null) {
            Log.e(TAG, "setDeviceList: devices rv is null" );
            return;
        }
        this.deviceList = deviceList;
        RecyclerAdapter mAdapter = new RecyclerAdapter(getContext(), deviceList, ((RecyclerAdapter)devicesRv.getAdapter()).getSelectedIndex());
        // mAdapter.setClickListener(toMainActivity(getContext()).getListenerFromSetting());
        devicesRv.setAdapter(mAdapter);
        setNewAdapterForButton(mAdapter);
        this.left = left;
    }
}
