package com.example.fragmentlrn;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.example.fragmentlrn.MainActivity.getMainActivity;

public class DialogFound extends DialogFragment {

    private String TAG = "DialogFound";

    private RecyclerView devicesRv = null;
    private Button btn = null;
    private List<BluetoothDevice> deviceList = null;
    private boolean left;
    private Context mContext;
    private TextDrawable searchInProgress = new TextDrawable("Идет поиск...");

    public DialogFound(List<BluetoothDevice> deviceList) {
        this.deviceList = deviceList;
    }

    public RecyclerAdapter getAdapter() {
        return (RecyclerAdapter) devicesRv.getAdapter();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mContext = context;
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
                mAdapter = new RecyclerAdapter(getContext(), deviceList, this);
            }
        } else {
            mAdapter = new RecyclerAdapter(getContext(), deviceList, this);
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
            /*setDeviceList(getMainActivity(getActivity()).service.getLeftDevicesList(), true);*/
            setDeviceList(getMainActivity(getActivity()).btScanner.getLeftDevicesList(), true);
            setConnectedSelected(getMainActivity(getActivity()).getLeftBoot());
        } else if (getTag().equals("right")) {
            /*setDeviceList(getMainActivity(getActivity()).service.getRightDevicesList(), false);*/
            setDeviceList(getMainActivity(getActivity()).btScanner.getRightDevicesList(), false);
            setConnectedSelected(getMainActivity(getActivity()).getRightBoot());
        } else {
            Log.e(TAG, "onCreateView: this is not right nor left");
        }
        return v;
    }

    public void setNewAdapterForButton(RecyclerAdapter adapterForButton) {
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (adapterForButton.getSelectedDevice() == null) {
                    btn.setEnabled(false);
                    Log.e(TAG, "onClick: device not selected" );
                    return;
                }
                btn.setEnabled(true);
                Log.d(TAG, "onClick: device chosen for " + (left ? "left" : "right"));
                // error here because mAdapter was given as a value, not a reference
                Log.d(TAG, "onClick: device " + adapterForButton.getSelectedDevice().getName());
                if (left) {
                    getMainActivity(getActivity()).rememberLeftAddress(adapterForButton.getSelectedDevice().getAddress());
                    getMainActivity(getActivity()).serialSocketConnectLeft(adapterForButton.getSelectedDevice());
                } else {
                    getMainActivity(getActivity()).rememberRightAddress(adapterForButton.getSelectedDevice().getAddress());
                    getMainActivity(getActivity()).serialSocketConnectRight(adapterForButton.getSelectedDevice());
                }
                // getMainActivity(getActivity()).connectTo(adapterForButton.getSelectedDevice());
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
        if (deviceList.size() != 0) {
            devicesRv.setBackground(null);
        } else {
            devicesRv.setBackground(searchInProgress);
            btn.setEnabled(false);
        }
        this.deviceList = deviceList;
        RecyclerAdapter mAdapter = new RecyclerAdapter(mContext, deviceList, ((RecyclerAdapter)devicesRv.getAdapter()).getSelectedIndex(), this);
        // mAdapter.setClickListener(toMainActivity(getContext()).getListenerFromSetting());
        devicesRv.setAdapter(mAdapter);
        setNewAdapterForButton(mAdapter);
        this.left = left;
    }

    public void setConnectEnabled(boolean enabled){
        if (btn == null) {
            Log.e(TAG, "setConnectEnabled: connect button is null");
            return;
        }
        btn.setEnabled(enabled);
    }

    public void setConnectedSelected(BluetoothDevice connectedDevice){
        if(devicesRv == null) {
            Log.e(TAG, "setConnectedSelected: devicesRv is null");
            return;
        }
        RecyclerAdapter adapter = (RecyclerAdapter)devicesRv.getAdapter();
        if (adapter == null) {
            Log.e(TAG, "setConnectedSelected: dadpter is null");
            return;
        }
        int index = -1;
        List<BluetoothDevice> deviceData = adapter.getDeviceData();
        for (int co = 0; co < deviceData.size(); ++co) {
            if (deviceData.get(co).equals(connectedDevice)) {
                index = co;
            }
        }
        if (index == -1) {
            Log.e(TAG, "setSelectedDevice: not found in device data");
            return;
        } else {
            adapter.setSelectedIndex(index);
            adapter.unselectLastChecked();
            RecyclerAdapter.ViewHolder holder = (RecyclerAdapter.ViewHolder) devicesRv.findViewHolderForAdapterPosition(index);
            if (holder != null) {
                holder.setSelected();
                adapter.setLastChecked(holder
                );
            }
            else Log.e(TAG, "setConnectedSelected: holder is null");
        }
    }
}
