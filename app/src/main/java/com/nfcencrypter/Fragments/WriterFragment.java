package com.nfcencrypter.Fragments;

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
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
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

    private AlertDialog readIndicator;
    private MainActivity activity;
    private TagRegistryAdapter adapter;
    private NdefRecord[] ndefRecords;


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
        RecyclerView nfcInput = ((ConstraintLayout)view).findViewById(R.id.records_viewer);
        adapter = new TagRegistryAdapter(new ArrayList<>());
        nfcInput.setAdapter(adapter);
        nfcInput.setLayoutManager(new LinearLayoutManager(view.getContext()));
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

    public void write(Intent intent){
        if(ndefRecords != null ){
            if(!readIndicator.isShowing()){
                Toast.makeText(activity, "Press the Write button", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
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
            } catch (IOException e) {
                e.printStackTrace();
            } catch (FormatException e) {
                e.printStackTrace();
            }
        }
    }
    private boolean write(NdefRecord[] records, Tag tag) throws IOException, FormatException {
        NdefMessage message = new NdefMessage(records);
        if(MainActivity.checkTech(tag)) {
            Ndef ndef = Ndef.get(tag);
            if (ndef != null) {
                ndef.connect();
                if (ndef.isWritable()) {
                    ndef.writeNdefMessage(message);
                } else {
                    ndef.close();
                    return false;
                }
                ndef.close();
                return true;
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
