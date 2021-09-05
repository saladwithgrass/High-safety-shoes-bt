package com.example.fragmentlrn;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class RecyclerAdapter extends RecyclerView.Adapter<RecyclerAdapter.ViewHolder>  {

    private List<String> nameData, macData;
    private List<BluetoothDevice> deviceData;
    private LayoutInflater mInflater;
    private ItemClickListener mClickListener;
    private final String TAG = "RecyclerAdapter";
    private int selected;
    private ViewHolder lastChecked = null;

    public RecyclerAdapter(Context context, List<BluetoothDevice> devices) {
        nameData = new ArrayList<>();
        macData = new ArrayList<>();
        deviceData = devices;
        for (BluetoothDevice device : devices) {
            nameData.add(device.getName());
            macData.add(device.getAddress());
        }
        this.mInflater = LayoutInflater.from(context);
    }

    public ViewHolder onCreateViewHolder( ViewGroup parent, int viewType) {
        View view = mInflater.inflate(R.layout.recycler_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder( RecyclerAdapter.ViewHolder holder, int position) {
        String name = nameData.get(position);
        holder.nameTv.setText(name);
        String mac = macData.get(position);
        // holder.macTv.setText(mac);
    }

    public int getSelected() { return selected; }

    public void select(int position) {

    }

    public void unselectAll() {

    }

    @Override
    public int getItemCount() {
        return nameData.size();
    }

    String getMac (int id) {
        return macData.get(id);
    }

    String getName (int id) {
        return nameData.get(id);
    }

    BluetoothDevice getDevice(int id) { return deviceData.get(id); }

    void setClickListener(ItemClickListener itemClickListener) {
        this.mClickListener = itemClickListener;
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private TextView nameTv, macTv;
        private ImageView selectedIv;


        public ViewHolder(View itemView) {
            super(itemView);
            nameTv = itemView.findViewById(R.id.nameTv);
            selectedIv = itemView.findViewById(R.id.selectedIv);
            itemView.setOnClickListener(this);
        }

        private void setSelected() {
            selectedIv.setImageResource(R.drawable.checked_drawable);
        }

        private void setUnselected() {
            selectedIv.setImageResource(R.drawable.unchecked_drawable);
        }

        @Override
        public void onClick(View v) {
            Log.d(TAG, "onClick: clicked");
            if (getSelected() != getAdapterPosition()) {
                setSelected();
                if (lastChecked != null) lastChecked.setUnselected();
                lastChecked = this;
                selected = getAdapterPosition();
            }
            mClickListener.onItemClick(v, getAdapterPosition());
        }
    }

    public interface ItemClickListener {
        void onItemClick(View view, int position);
    }

}
