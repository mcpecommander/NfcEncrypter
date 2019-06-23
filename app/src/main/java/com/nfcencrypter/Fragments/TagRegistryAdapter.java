package com.nfcencrypter.Fragments;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.nfcencrypter.R;

import java.util.List;

public class TagRegistryAdapter extends RecyclerView.Adapter<TagRegistryAdapter.CustomRecordHolder> {

    List<String> records;

    TagRegistryAdapter(List<String> records){
        this.records = records;
    }

    @NonNull
    @Override
    public CustomRecordHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ConstraintLayout recordRoot = (ConstraintLayout) LayoutInflater.from(parent.getContext()).inflate(R.layout.custom_record, parent, false);
        return new CustomRecordHolder(recordRoot);
    }

    @Override
    public void onBindViewHolder(@NonNull CustomRecordHolder holder, int position) {
        TextView info = (TextView) holder.root.getViewById(R.id.registry_record);
        Button addRecord = (Button) holder.root.getViewById(R.id.add_record);
        addRecord.setOnClickListener(btn ->{
            @SuppressLint("InflateParams")
            LinearLayout input = (LinearLayout) LayoutInflater.from(holder.root.getContext()).inflate(R.layout.input_record, null);
            EditText record = input.findViewById(R.id.input_record);
            new AlertDialog.Builder(holder.root.getContext()).setView(input).setPositiveButton("Add Record", (dialog, button) ->{
                if (!record.getText().toString().isEmpty()){
                    this.records.add(record.getText().toString());
                    notifyDataSetChanged();
                }
            }).setNegativeButton("Cancel", null).show();

        });
        if(position >= records.size()){
            addRecord.setVisibility(View.VISIBLE);
            info.setVisibility(View.GONE);
        }else{
            info.setText(records.get(position));
            info.setVisibility(View.VISIBLE);
            addRecord.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return records.size() + 1;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemViewType(int position) {
        return position;
    }

    static class CustomRecordHolder extends RecyclerView.ViewHolder{

        ConstraintLayout root;
        CustomRecordHolder(@NonNull ConstraintLayout itemView) {
            super(itemView);
            this.root = itemView;
        }
    }

}
