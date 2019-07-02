package com.nfcencrypter.Fragments;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.ActionMode;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import com.nfcencrypter.MainActivity;
import com.nfcencrypter.R;
import com.scottyab.aescrypt.AESCrypt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;

import javax.crypto.spec.SecretKeySpec;

public class WriterFragment extends Fragment {

    private AlertDialog readIndicator;
    private MainActivity activity;
    private TagRegistryAdapter adapter;
    private NdefRecord[] ndefRecords;
    static View.OnClickListener add_record_listener;
    private AlertDialog recordAddAlert;
    private ActionMode.Callback actionModeCallback;
    SelectionTracker<Long> selectionTracker;
    private ActionMode actionMode;
    private MenuItem selectAll, edit;
    public ViewPager.OnPageChangeListener pageChangeListener;


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        return inflater.inflate(
                R.layout.writer_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        readIndicator = new AlertDialog.Builder(activity).setTitle("Writing...").setMessage("Place an Ndef-supporting tag near the device").create();

        pageChangeListener = new ViewPager.OnPageChangeListener(){

            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                if(position != 1 && actionMode != null){
                    actionMode.finish();
                }
            }

            @Override
            public void onPageSelected(int position) {

            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        };

        RecyclerView nfcInput = ((ConstraintLayout)view).findViewById(R.id.records_viewer);
        adapter = new TagRegistryAdapter(new ArrayList<>());

        LinearLayoutManager manager = new LinearLayoutManager(view.getContext());

        nfcInput.setAdapter(adapter);
        nfcInput.setLayoutManager(manager);



        selectionTracker = new SelectionTracker.Builder<>("record_selector", nfcInput,
                new TagRegistryAdapter.CustomItemKeyProvider(), new TagRegistryAdapter.MyDetailsLookup(nfcInput),
                StorageStrategy.createLongStorage()).withSelectionPredicate(new SelectionTracker.SelectionPredicate<Long>() {
            @Override
            public boolean canSetStateForKey(@NonNull Long key, boolean nextState) {
                return key != adapter.records.size();
            }

            @Override
            public boolean canSetStateAtPosition(int position, boolean nextState) {
                return position != adapter.records.size();
            }

            @Override
            public boolean canSelectMultiple() {
                return true;
            }
        }).build();



        @SuppressLint("InflateParams")
        LinearLayout input = (LinearLayout) LayoutInflater.from(view.getContext()).inflate(R.layout.input_record, null);
        EditText record = input.findViewById(R.id.input_record);


        recordAddAlert = new AlertDialog.Builder(view.getContext()).setView(input)
                .setPositiveButton("Ok", (dialog, button) -> {
            if (!record.getText().toString().isEmpty() && !selectionTracker.hasSelection()) {
                adapter.records.add(record.getText().toString());
                adapter.notifyDataSetChanged();
            } else if(!record.getText().toString().isEmpty() && selectionTracker.getSelection().size() == 1){
                for(Long key : selectionTracker.getSelection()){
                    adapter.records.set(Math.toIntExact(key), record.getText().toString());
                    adapter.notifyItemChanged(Math.toIntExact(key));
                }
                selectionTracker.clearSelection();
            }
        }).setNegativeButton("Cancel", null)
                .setOnDismissListener(dialog -> record.getText().clear()).create();

        add_record_listener = v -> {
            if(!selectionTracker.hasSelection()) {
                recordAddAlert.show();
            }else{
                Toast.makeText(activity, "Clear the selection first", Toast.LENGTH_SHORT).show();
            }
        };

        if (savedInstanceState != null) {
            selectionTracker.onRestoreInstanceState(savedInstanceState);
        }

        selectionTracker.addObserver(new SelectionTracker.SelectionObserver<Long>() {
            @Override
            public void onItemStateChanged(@NonNull Long key, boolean selected) {
            if(selected){
                if(selectionTracker.getSelection().size() == 1){
                    if (actionMode != null ) {
                        return;
                    }

                    // Start the CAB using the ActionMode.Callback defined below
                    if (activity != null) {
                        actionMode = activity.startSupportActionMode(actionModeCallback);

                    }
                }
                if (selectionTracker.getSelection().size() != 1){
                    edit.setVisible(false);
                }else{
                    edit.setVisible(true);
                }
                if(selectionTracker.getSelection().size() == adapter.records.size()){
                    if(actionMode != null && selectAll != null){
                        selectAll.setChecked(true);
                        selectAll.setIcon(R.drawable.deselect_all);
                    }
                }else{
                    if(actionMode != null && selectAll != null){
                        if(selectAll.isChecked()){
                            selectAll.setChecked(false);
                            selectAll.setIcon(R.drawable.select_all);
                        }
                    }
                }
            }else{
                if(actionMode != null){
                    if(selectionTracker.getSelection().isEmpty()){
                        actionMode.finish();
                    }else{
                        if(selectionTracker.getSelection().size() != adapter.records.size() && selectAll.isChecked()){
                            selectAll.setChecked(false);
                            selectAll.setIcon(R.drawable.select_all);
                        }
                        if (selectionTracker.getSelection().size() != 1){
                            edit.setVisible(false);
                        }else{
                            edit.setVisible(true);
                        }
                    }
                }
            }
            ConstraintLayout recordView = (ConstraintLayout) manager.findViewByPosition(Math.toIntExact(key));
            if(recordView != null) {
                if (selected) {
                    recordView.setBackgroundColor(getResources().getColor(android.R.color.darker_gray, null));
                } else {
                    recordView.setBackgroundResource(0);
                }
            }
            }
        });

        actionModeCallback = new ActionMode.Callback() {


            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                MenuInflater inflater = mode.getMenuInflater();
                inflater.inflate(R.menu.action_menu, menu);
                selectAll = menu.findItem(R.id.select_all);
                edit = menu.findItem(R.id.edit);
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                switch (item.getItemId()) {
                    case  R.id.delete:
                        ArrayList<String> toBeRemoved = new ArrayList<>();
                        for (Long key : selectionTracker.getSelection()) {
                           toBeRemoved.add(adapter.records.get(Math.toIntExact(key)));
                        }
                        adapter.records.removeAll(toBeRemoved);
                        selectionTracker.clearSelection();
                        adapter.notifyDataSetChanged();
                        mode.finish();

                        return true;
                    case R.id.select_all:
                        if(!item.isChecked()) {
                            for (int i = 0; i < adapter.records.size(); i++) {
                                selectionTracker.select((long) i);
                            }
                            item.setChecked(true);
                            item.setIcon(R.drawable.deselect_all);
                        }else {
                            selectionTracker.clearSelection();
                            item.setChecked(false);
                        }
                        return true;
                    case R.id.edit:
                        if(selectionTracker.getSelection().size() == 1){
                            EditText record = recordAddAlert.findViewById(R.id.input_record);
                            for(Long key : selectionTracker.getSelection()){
                                record.setText(adapter.records.get(Math.toIntExact(key)));
                                record.setSelection(record.getText().length());
                                record.requestFocus();
                                recordAddAlert.show();
                            }

                        }
                    default:
                        return false;
                }
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                selectionTracker.clearSelection();
                actionMode = null;
            }
        };


        Button deleteAll = view.findViewById(R.id.delete_all);
        deleteAll.setOnClickListener(btn ->{
            adapter.records.clear();
            adapter.notifyDataSetChanged();
        });

        Button writeToTag = view.findViewById(R.id.write);

        writeToTag.setOnClickListener(v ->{
            activity.hideKeyboard();
            EditText password = activity.findViewById(R.id.password);
            EditText confirmation = activity.findViewById(R.id.password_confirmation);
            String pass = password.getText().toString();
            if(pass.equals(confirmation.getText().toString()) && !pass.isEmpty()){
                ndefRecords = new NdefRecord[adapter.records.size()];
                for (int i = 0; i < adapter.records.size(); i++){
                    byte[] encryptedVersion;
                    try {
                        encryptedVersion = encrypt(pass, adapter.records.get(i));
                        ndefRecords[i] = createExternal(encryptedVersion);
                    }catch (NoSuchAlgorithmException e) {
                        e.printStackTrace();
                    }

                }
                readIndicator.show();
            }else if (pass.isEmpty()){
                Toast.makeText(activity, "Password can not be empty", Toast.LENGTH_LONG).show();
            }else{
                Toast.makeText(activity, "Passwords don't match", Toast.LENGTH_SHORT).show();
            }
        });

    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        selectionTracker.onSaveInstanceState(outState);
    }

    public void write(Intent intent){
        if(ndefRecords != null ){
            if(!readIndicator.isShowing()){
                Toast.makeText(activity, "Press the Write button", Toast.LENGTH_SHORT).show();
                return;
            }
            if(write(ndefRecords, intent.getParcelableExtra(NfcAdapter.EXTRA_TAG))) {
                readIndicator.dismiss();
                MainActivity.writing_successful.show();
                EditText password = activity.findViewById(R.id.password);
                EditText confirmation = activity.findViewById(R.id.password_confirmation);
                password.getText().clear();
                confirmation.getText().clear();
            }else{
                MainActivity.writing_failed.show();
            }
        }
    }
    private boolean write(NdefRecord[] records, Tag tag) {
        NdefMessage message = new NdefMessage(records);
        if(MainActivity.checkTech(tag)) {
            Ndef ndef = Ndef.get(tag);
            if (ndef != null) {
                try {
                    ndef.connect();
                if (ndef.isWritable()) {
                    ndef.writeNdefMessage(message);
                } else {
                    ndef.close();
                    return false;
                }
                ndef.close();
                return true;
                } catch ( FormatException e) {
                    Toast.makeText(activity, "Formatting has gone wrong.", Toast.LENGTH_SHORT).show();
                } catch (IOException e) {
                    Toast.makeText(activity, "Records size has probably exceeded the tag size, delete some records and try again..", Toast.LENGTH_LONG).show();
                }
            }
        }
        return false;
    }

    @NonNull
    private byte[] encrypt(String password, String text) throws NoSuchAlgorithmException {
        SecretKeySpec spec = MainActivity.generateKey(password);
        byte[] iv = generateIV();
        byte[] payLoad = new byte[0];
        try {
            byte[] encrypted = AESCrypt.encrypt(spec, iv, text.getBytes(StandardCharsets.UTF_8));
            payLoad = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payLoad, 0, 16);
            System.arraycopy(encrypted, 0, payLoad, 16, encrypted.length);
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
        }
        return payLoad;

    }

    private byte[] generateIV() {
        SecureRandom rand = new SecureRandom();
        byte[] iv = new byte[16];
        rand.nextBytes(iv);
        return iv;
    }

    private static NdefRecord createExternal(byte[] text){

        byte[] byteDomain = "application://com.nfcencrypter".getBytes(StandardCharsets.UTF_8);
        byte[] byteType = "record".getBytes(StandardCharsets.UTF_8);
        byte[] b = new byte[byteDomain.length + 1 + byteType.length];
        System.arraycopy(byteDomain, 0, b, 0, byteDomain.length);
        b[byteDomain.length] = ':';
        System.arraycopy(byteType, 0, b, byteDomain.length + 1, byteType.length);

        return new NdefRecord(NdefRecord.TNF_EXTERNAL_TYPE, b, MainActivity.TAG_UID, text);
    }


}
