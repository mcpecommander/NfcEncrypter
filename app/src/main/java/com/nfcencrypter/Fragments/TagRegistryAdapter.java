package com.nfcencrypter.Fragments;

import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.selection.ItemKeyProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.nfcencrypter.R;

import java.util.List;

public class TagRegistryAdapter extends RecyclerView.Adapter<TagRegistryAdapter.CustomRecordHolder> {

    List<String> records;

    TagRegistryAdapter(List<String> records){
        this.records = records;
        setHasStableIds(true);
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
        addRecord.setOnClickListener(WriterFragment.add_record_listener);
        if(position >= records.size()){
            addRecord.setVisibility(View.VISIBLE);
            addRecord.setLongClickable(false);
            holder.root.setLongClickable(false);
            info.setVisibility(View.GONE);
        }else{
            holder.root.setLongClickable(true);
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

        RecordItemDetails getItemDetails(){
            return new RecordItemDetails(getAdapterPosition(), getItemId());
        }
    }

    static final class MyDetailsLookup extends ItemDetailsLookup<Long> {

        private final RecyclerView mRecyclerView;

        MyDetailsLookup(RecyclerView recyclerView) {
            mRecyclerView = recyclerView;
        }

        public ItemDetails<Long> getItemDetails(MotionEvent e) {
            View view = mRecyclerView.findChildViewUnder(e.getX(), e.getY());
            if (view != null) {
                RecyclerView.ViewHolder holder = mRecyclerView.getChildViewHolder(view);
                if (holder instanceof CustomRecordHolder) {
                    return ((CustomRecordHolder) holder).getItemDetails();
                }
            }
            return null;
        }
    }

    static class RecordItemDetails extends ItemDetailsLookup.ItemDetails<Long> {

        private int position;
        private Long key;

        RecordItemDetails(int position, Long key) {
            this.position = position;
            this.key = key;
        }

        @Override
        public int getPosition() {
            return position;
        }

        @Nullable
        @Override
        public Long getSelectionKey() {
            return key;
        }
    }

    static class CustomItemKeyProvider extends ItemKeyProvider<Long>{

        protected CustomItemKeyProvider() {
            super(SCOPE_CACHED);

        }

        @Nullable
        @Override
        public Long getKey(int position) {
            return (long) position;
        }

        @Override
        public int getPosition(@NonNull Long key) {
            return Math.toIntExact(key);
        }
    }

}
