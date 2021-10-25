package com.example.fragmentlrn;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class RecyclerAdapter extends RecyclerView.Adapter<RecyclerAdapter.ViewHolder>  {

    private List<String> nameData, macData;
    private List<BluetoothDevice> deviceData;
    private LayoutInflater mInflater;
    private ItemClickListener mClickListener;
    private final String TAG = "RecyclerAdapter";
    private int selectedIndex = -1;
    private ViewHolder lastChecked = null;
    private DialogFound parent;

    public RecyclerAdapter(Context context, List<BluetoothDevice> devices, DialogFound parent) {
        // Log.d(TAG, "RecyclerAdapter: checking selected index " + selectedIndex);
        this.parent = parent;
        nameData = new ArrayList<>();
        macData = new ArrayList<>();
        deviceData = devices;
        if (selectedIndex == -1) {
            parent.setConnectEnabled(false);
        } else {parent.setConnectEnabled(true);}
        // selectedIndex = -1;
        for (BluetoothDevice device : devices) {
            nameData.add(device.getName());
            macData.add(device.getAddress());
        }
        this.mInflater = LayoutInflater.from(context);
    }

    public RecyclerAdapter(Context context, List<BluetoothDevice> devices, int selected, DialogFound parent) {
        // Log.d(TAG, "RecyclerAdapter: checking selected index " + selectedIndex + " selected :" + selected);
        this.parent = parent;
        nameData = new ArrayList<>();
        macData = new ArrayList<>();
        deviceData = devices;
        // this.selectedIndex = selected;
        for (BluetoothDevice device : devices) {
            nameData.add(device.getName());
            macData.add(device.getAddress());
        }
        if (lastChecked != null) {
            lastChecked.setSelected();
        }
        this.mInflater = LayoutInflater.from(context);
    }

    public void noInit(Context context) {
        if (lastChecked != null) lastChecked.setSelected();
        this.mInflater = LayoutInflater.from(context);
    }

    public ViewHolder onCreateViewHolder( ViewGroup parent, int viewType) {
        // Log.d(TAG, "onCreateViewHolder: checking selected index " + selectedIndex);
        View view = mInflater.inflate(R.layout.recycler_item, parent, false);
        return new ViewHolder(view);
    }

    public void setSelectedIndex(int index) {
        selectedIndex = index;
    }

    public void unselectLastChecked() {
        if (lastChecked == null) return;
        lastChecked.setUnselected();
    }

    public void setLastChecked(ViewHolder holder) {
        lastChecked = holder;
    }

    @Override
    public void onBindViewHolder( RecyclerAdapter.ViewHolder holder, int position) {
        // Log.d(TAG, "onBindViewHolder: checking selected index " + selectedIndex);
        String name = nameData.get(position);
        holder.nameTv.setText(name);
        String mac = macData.get(position);
        holder.device = deviceData.get(position);
        if (deviceData.size() > 0 && selectedIndex >=0 && selectedIndex < deviceData.size())
            if (deviceData.get(selectedIndex).equals(deviceData.get(position))) holder.setSelected();
        // holder.macTv.setText(mac);
    }

    public int getSelectedIndex() {
        // Log.d(TAG, "getSelectedIndex: checking selected index "  +selectedIndex);
        return selectedIndex;
    }

    public BluetoothDevice getSelectedDevice() {
        Log.d(TAG, "getSelectedDevice: ");
        // Log.d(TAG, "getSelectedDevice: checking selected index " + selectedIndex);
        return getDevice(getSelectedIndex());
    }

    @Override
    public int getItemCount() {
        // Log.d(TAG, "getItemCount: checking selected index " + selectedIndex);
        return nameData.size();
    }

    String getMac (int id) {
        return macData.get(id);
    }

    String getName (int id) {
        return nameData.get(id);
    }

    BluetoothDevice getDevice(int id) {
        // Log.d(TAG, "getDevice: checking selected index " + selectedIndex);
        if (id >= deviceData.size() || id < 0) return null;
        return deviceData.get(id);
    }

    void setClickListener(ItemClickListener itemClickListener) {
        // Log.d(TAG, "setClickListener: checking selected index " + selectedIndex);
        this.mClickListener = itemClickListener;
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private TextView nameTv, macTv;
        private ImageView selectedIv;
        private BluetoothDevice device;

        public ViewHolder(View itemView) {
            super(itemView);
            // Log.d(TAG, "ViewHolder: checking selected index " + selectedIndex);
            nameTv = itemView.findViewById(R.id.nameTv);
            selectedIv = itemView.findViewById(R.id.selectedIv);
            if (selectedIndex >= 0 && selectedIndex < deviceData.size() &&
                    getAdapterPosition() == selectedIndex) setSelected();
            itemView.setOnClickListener(this);
        }

        public void setSelected() {
            // Log.d(TAG, "setSelected: checking selected index " + selectedIndex);
            selectedIv.setImageResource(R.drawable.checked_drawable);
        }

        public void setUnselected() {
            // Log.d(TAG, "setUnselected: checking selected index " + selectedIndex);
            selectedIv.setImageResource(R.drawable.unchecked_drawable);
        }

        @Override
        public void onClick(View v) {
            // Log.d(TAG, "onClick: checking selected index " + selectedIndex);
            Log.d(TAG, "onClick: clicked");
            if (getSelectedIndex() != getAdapterPosition()) {
                setSelected();
                if (lastChecked != null) lastChecked.setUnselected();
                lastChecked = this;
                selectedIndex = getAdapterPosition();
            }
            // mClickListener.onItemClick(v, getAdapterPosition());
        }
    }

    public interface ItemClickListener {
        void onItemClick(View view, int position);
    }

    public List<BluetoothDevice> getDeviceData() {
        return deviceData;
    }

}
