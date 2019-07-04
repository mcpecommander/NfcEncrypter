package com.nfcencrypter.Fragments;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
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

    private AlertDialog readIndicator, recordAddAlert;
    private MainActivity activity;
    private TagRegistryAdapter adapter;
    private NdefRecord[] ndefRecords;
    static View.OnClickListener add_record_listener;
    private ActionMode.Callback actionModeCallback;
    private SelectionTracker<Long> selectionTracker;
    public ActionMode action_mode;
    private MenuItem selectAll, edit;


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        activity = (MainActivity) requireActivity();
        return inflater.inflate(
                R.layout.writer_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        readIndicator = new AlertDialog.Builder(activity, R.style.CustomAlertDialog).setTitle("Writing...").setMessage("Place an Ndef-supporting tag near the device").create();

        //Check if the tab is changed to another tab and cancel action mode.


        //Add the recycler view adapter and manager
        RecyclerView nfcInput = ((ConstraintLayout)view).findViewById(R.id.records_viewer);
        adapter = new TagRegistryAdapter(new ArrayList<>());
        LinearLayoutManager manager = new LinearLayoutManager(view.getContext());
        nfcInput.setAdapter(adapter);
        nfcInput.setLayoutManager(manager);

        //Add a selection tracker to handle selections.
        selectionTracker = new SelectionTracker.Builder<>("record_selector", nfcInput,
                //Custom stable id provider since google's is broken.
                new TagRegistryAdapter.CustomItemKeyProvider(),
                //Custom detail lookup.
                new TagRegistryAdapter.MyDetailsLookup(nfcInput),
                //Long storage since it is the easiest.
                StorageStrategy.createLongStorage())
                //Selection predicate to exclude the add button from the selection.
                .withSelectionPredicate(new SelectionTracker.SelectionPredicate<Long>() {
            @Override
            public boolean canSetStateForKey(@NonNull Long key, boolean nextState) {
                //Key == position so do not select the last item (the add button).
                return key != adapter.records.size();
            }

            @Override
            public boolean canSetStateAtPosition(int position, boolean nextState) {
                //The predicate should work well enough with just the first method but it seems to be broken so I check both the key and the position.
                return position != adapter.records.size();
            }

            @Override
            public boolean canSelectMultiple() {
                return true;
            }
        }).build();

        //A custom add record alert dialog that accepts text input.
        @SuppressLint("InflateParams")
        //Inflate the edit text view wrapped inside a linear layout for ease of design.
        LinearLayout input = (LinearLayout) LayoutInflater.from(view.getContext()).inflate(R.layout.input_record, null);
        EditText record = input.findViewById(R.id.input_record);
        recordAddAlert = new AlertDialog.Builder(view.getContext(), R.style.CustomAlertDialog).setView(input)
                .setPositiveButton("Ok", (dialog, button) -> {
            //Check if add record or editing record.
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
                //When adding or canceling or dismissing the alert dialog, the edit text view will be emptied.
                .setOnDismissListener(dialog -> record.getText().clear()).create();

        //Add record button should only be pressable when nothing is selected.
        add_record_listener = v -> {
            if(!selectionTracker.hasSelection()) {
                recordAddAlert.show();
            }else{
                Toast.makeText(activity, "Clear the selection first", Toast.LENGTH_SHORT).show();
            }
        };

        //Check for selection saved in after pausing.
        if (savedInstanceState != null) {
            selectionTracker.onRestoreInstanceState(savedInstanceState);
        }

        //Selection listener for each selection or deselection.
        selectionTracker.addObserver(new SelectionTracker.SelectionObserver<Long>() {
            @Override
            public void onItemStateChanged(@NonNull Long key, boolean selected) {
            if(selected) {
                //if first selection and action mode has been started yet so start action mode.
                if (selectionTracker.getSelection().size() == 1) {
                    if (action_mode != null) {
                        //if action mode has already been started, then make sure that edit item is visible since only one record is selected.
                        edit.setVisible(true);
                    } else {
                        // Start the CAB using the ActionMode.Callback defined below
                        action_mode = activity.startSupportActionMode(actionModeCallback);
                    }
                } else {
                    //Set edit item to invisible when more than one records are selected.
                    edit.setVisible(false);
                }

                if (action_mode != null && selectAll != null) {
                    //if every record are selected then change select all icon to deselect all instead.
                    if (selectionTracker.getSelection().size() == adapter.records.size()) {
                        selectAll.setChecked(true);
                        selectAll.setIcon(R.drawable.deselect_all);
                    } else {
                        selectAll.setChecked(false);
                        selectAll.setIcon(R.drawable.select_all);
                    }
                }
            }else{
                if(action_mode != null){
                    //if deselect the last record then finish action mode
                    if(selectionTracker.getSelection().isEmpty()){
                        action_mode.finish();
                    }else{
                        //Set select all back to select all when deselecting a record.
                        if(selectionTracker.getSelection().size() != adapter.records.size()){
                            selectAll.setChecked(false);
                            selectAll.setIcon(R.drawable.select_all);
                        }
                        //Set edit menu item visibility depending on the number of records selected.
                        if (selectionTracker.getSelection().size() != 1){
                            edit.setVisible(false);
                        }else{
                            edit.setVisible(true);
                        }
                    }
                }
            }
            //Add selection tint.
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

        //Action mode callback.
        actionModeCallback = new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                //Create action mode menu and set some fields.
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
                action_mode = null;
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
    static byte[] encrypt(String password, String text) throws NoSuchAlgorithmException {
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

    private static byte[] generateIV() {
        SecureRandom rand = new SecureRandom();
        byte[] iv = new byte[16];
        rand.nextBytes(iv);
        return iv;
    }

    static NdefRecord createExternal(byte[] text){

        byte[] byteDomain = "application://com.nfcencrypter".getBytes(StandardCharsets.UTF_8);
        byte[] byteType = "record".getBytes(StandardCharsets.UTF_8);
        byte[] b = new byte[byteDomain.length + 1 + byteType.length];
        System.arraycopy(byteDomain, 0, b, 0, byteDomain.length);
        b[byteDomain.length] = ':';
        System.arraycopy(byteType, 0, b, byteDomain.length + 1, byteType.length);

        return new NdefRecord(NdefRecord.TNF_EXTERNAL_TYPE, b, MainActivity.TAG_UID, text);
    }


}
