package com.nfcencrypter;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class TagInfoAdapter extends RecyclerView.Adapter<TagInfoAdapter.TextViewHolder> {

    private List<String> records;

    public TagInfoAdapter(List<String> records){
        this.records = records;
    }

    @NonNull
    @Override
    public TextViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        TextView view = (TextView) LayoutInflater.from(parent.getContext()).inflate(R.layout.record, parent, false);
        return new TextViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TextViewHolder holder, int position) {
        holder.view.setText(records.get(position));
    }

    public void setRecords(List<String> records) {
        this.records = records;
    }

    @Override
    public int getItemCount() {
        return records.size();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemViewType(int position) {
        return position;
    }

    public static class TextViewHolder extends RecyclerView.ViewHolder{

        TextView view;

        public TextViewHolder(@NonNull TextView itemView) {
            super(itemView);
            this.view = itemView;
        }
    }
}
