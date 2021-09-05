package com.example.fragmentlrn;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import static com.example.fragmentlrn.MainActivity.toMainActivity;

public class DialogFound extends DialogFragment {

    private RecyclerView devicesRv;
    private List<BluetoothDevice> deviceList;

    public DialogFound(List<BluetoothDevice> deviceList) {
        this.deviceList = deviceList;
    }

    public RecyclerAdapter getAdapter() {
        return (RecyclerAdapter) devicesRv.getAdapter();
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        getDialog().setTitle("FOUND!");
        View v = inflater.inflate(R.layout.found_devices, null);
        devicesRv = v.findViewById(R.id.devicesRv);
        devicesRv.setLayoutManager(new LinearLayoutManager(getContext()));
        RecyclerAdapter mAdapter = new RecyclerAdapter(getContext(), deviceList);
        mAdapter.setClickListener(toMainActivity(getContext()).getListenerFromSetting());
        devicesRv.setAdapter(mAdapter);
        return v;
    }


}
