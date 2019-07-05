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
import android.os.Handler;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.nfcencrypter.MainActivity;
import com.nfcencrypter.R;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class CopyFragment extends Fragment {

    public static AlertDialog erase_alert, copy_alert, remove_password_alert,
            paste_alert, erase_confirmation, erase_success, read_encrypted;
    private NdefMessage[] copiedData;
    private List<NdefRecord> decrypted_records;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        return inflater.inflate(R.layout.copy_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        erase_alert = new AlertDialog.Builder(view.getContext(), R.style.CustomAlertDialog).setTitle("Erasing...").setMessage("Place an Ndef-supporting tag to erase").create();
        copy_alert = new AlertDialog.Builder(view.getContext(), R.style.CustomAlertDialog).setTitle("Copying...").setMessage("Place an encrypted tag to copy its content.").create();
        remove_password_alert = new AlertDialog.Builder(view.getContext(), R.style.CustomAlertDialog).setTitle("Removing...").setMessage("Place an encrypted tag to remove its password.").create();
        paste_alert = new AlertDialog.Builder(view.getContext(), R.style.CustomAlertDialog).setTitle("Pasting").setMessage("Place a tag to paste the data to").create();
        erase_confirmation = new AlertDialog.Builder(view.getContext(), R.style.CustomAlertDialog).setTitle("Are you sure?")
                .setIcon(android.R.drawable.ic_dialog_alert).setMessage("This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> erase_alert.show()).setNegativeButton("Cancel", null).create();
        erase_success = new AlertDialog.Builder(view.getContext(), R.style.CustomAlertDialog).setTitle("Erasure is done")
                .setMessage("The tag was successfully deleted").setPositiveButton("Ok", null).create();
        read_encrypted = new AlertDialog.Builder(view.getContext(), R.style.CustomAlertDialog).setTitle("Reading...")
                .setMessage("Place an encrypted tag to change its password").create();


        Button erase = view.findViewById(R.id.erase_tag);
        Button copy = view.findViewById(R.id.copy_button);
        Button removePassword = view.findViewById(R.id.remove_password);
        Button changePassword = view.findViewById(R.id.change_password);

        copy.setOnClickListener(v -> copy_alert.show());
        erase.setOnClickListener(v -> erase_confirmation.show());
        removePassword.setOnClickListener(v -> remove_password_alert.show());
        changePassword.setOnClickListener(v -> read_encrypted.show());

    }

    public void eraseTag(Intent intent){
        NdefMessage msg = new NdefMessage(new NdefRecord[] {
                new NdefRecord(NdefRecord.TNF_EMPTY, null, null, null)
        });
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        Ndef ndef = Ndef.get(tag);
        try {
            ndef.connect();
            if(ndef.isWritable()){
                ndef.writeNdefMessage(msg);
            }else{
                new AlertDialog.Builder(getContext(), R.style.CustomAlertDialog).setTitle("Failed")
                        .setMessage("Erasure failed. The tag is read only").setIcon(android.R.drawable.ic_dialog_alert).show();
            }
            ndef.close();
            erase_success.show();
        } catch (IOException | FormatException e) {
            new AlertDialog.Builder(getContext(), R.style.CustomAlertDialog).setTitle("Failed")
                    .setMessage("Erasure failed. The tag was moved too quickly away from the device").setIcon(android.R.drawable.ic_dialog_alert).show();
        }


    }

    public void copyTag(Intent intent) {
        Parcelable[] rawMessages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
        if(rawMessages != null && rawMessages.length > 0){
            copiedData = new NdefMessage[rawMessages.length];
            for(int i = 0; i < rawMessages.length; i++){
                copiedData[i] = (NdefMessage) rawMessages[i];
            }
            copy_alert.dismiss();
            paste_alert.show();
        }
    }

    public void pasteTag(Intent intent) {
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        if(MainActivity.checkTech(tag) ) {
            try {
                Ndef ndef = Ndef.get(tag);
                if (ndef != null) {
                    ndef.connect();
                    if (ndef.isWritable()) {
                        if (copiedData != null){
                            for (NdefMessage message : copiedData) {
                                ndef.writeNdefMessage(message);
                            }
                        }else if(decrypted_records != null && !decrypted_records.isEmpty()){
                            NdefMessage message = new NdefMessage(decrypted_records.toArray(new NdefRecord[0]));
                            ndef.writeNdefMessage(message);
                        }
                    }
                    ndef.close();
                    copiedData = null;
                    decrypted_records = null;
                    MainActivity.writing_successful.show();
                }
            }catch (Exception e){
                e.printStackTrace();
                MainActivity.writing_failed.show();
            }
        }
    }

    public void readEncrypted(Parcelable[] rawMessages){
        MainActivity activity = (MainActivity) requireActivity();
        read_encrypted.dismiss();
        NdefMessage[] messages = new NdefMessage[rawMessages.length];
        for(int i = 0; i < rawMessages.length; i++){
            messages[i] = (NdefMessage) rawMessages[i];
        }
        if(requiresPassword(messages)) {
            @SuppressLint("InflateParams")
            LinearLayout rootChange = (LinearLayout) getLayoutInflater().inflate(R.layout.change_password, null);
            EditText oldPassword = rootChange.findViewById(R.id.old_password);
            EditText newPassword = rootChange.findViewById(R.id.new_password);
            EditText confirmPassword = rootChange.findViewById(R.id.new_password_confirmation);
            new AlertDialog.Builder(activity, R.style.CustomAlertDialog).setView(rootChange).setTitle("Change Password")
                    .setPositiveButton("Change Password", (dialog, which) -> {
                if(!oldPassword.getText().toString().isEmpty() && !newPassword.getText().toString().isEmpty()
                && newPassword.getText().toString().equals(confirmPassword.getText().toString())){
                    copiedData = new NdefMessage[messages.length];
                    for (int i = 0; i < messages.length; i++ ) {
                        NdefMessage message = messages[i];
                        NdefRecord[] temp = new NdefRecord[message.getRecords().length];
                        for (int j = 0; j < message.getRecords().length; j++) {
                            NdefRecord encrypted = message.getRecords()[j];
                            if (!(Arrays.equals(encrypted.getId(), MainActivity.TAG_UID) || Arrays.equals(encrypted.getId(), MainActivity.OLD_TAG_UID)) || encrypted.getTnf() != NdefRecord.TNF_EXTERNAL_TYPE)
                                continue;
                            byte[] iv = new byte[16];
                            System.arraycopy(encrypted.getPayload(), 0, iv, 0, 16);
                            byte[] payLoad = new byte[encrypted.getPayload().length - 16];
                            System.arraycopy(encrypted.getPayload(), 16, payLoad, 0, payLoad.length);
                            try {
                                String decryptedText = ReaderFragment.decrypt(oldPassword.getText().toString(), iv, payLoad);
                                if(decryptedText.equals("Wrong password")){
                                    Toast.makeText(activity, "Wrong password", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                temp[j] = WriterFragment.createExternal(WriterFragment.encrypt(newPassword.getText().toString(), decryptedText));

                            } catch (NoSuchAlgorithmException e) {
                                e.printStackTrace();
                            }
                        }
                        copiedData[i] = new NdefMessage(temp);
                    }
                    new Handler().postDelayed(() -> paste_alert.show(), 1);
                }
            }).setNegativeButton("Cancel", (dialog, which) -> copiedData = null).show();

        }else{
            Toast.makeText(activity, "This tag doesn't have any password-encrypted data", Toast.LENGTH_SHORT).show();
        }

    }

    public void removePassword(Parcelable[] rawMessages){
        remove_password_alert.dismiss();
        NdefMessage[] messages = new NdefMessage[rawMessages.length];
        for(int i = 0; i < rawMessages.length; i++){
            messages[i] = (NdefMessage) rawMessages[i];
        }
        MainActivity activity = (MainActivity) requireActivity();
        @SuppressLint("InflateParams")
        LinearLayout rootPrompt = (LinearLayout) getLayoutInflater().inflate(R.layout.password_prompt, null);
        EditText password = rootPrompt.findViewById(R.id.password_prompt);
        decrypted_records = new ArrayList<>();
        if(requiresPassword(messages)) {
            new AlertDialog.Builder(activity, R.style.CustomAlertDialog).setView(rootPrompt).setPositiveButton("Decrypt", (dialog, button) -> {
                if (!password.getText().toString().isEmpty()) {
                    for (NdefMessage message : messages) {
                        for (NdefRecord encrypted : message.getRecords()) {
                            if (!(Arrays.equals(encrypted.getId(), MainActivity.TAG_UID) || Arrays.equals(encrypted.getId(), MainActivity.OLD_TAG_UID)) || encrypted.getTnf() != NdefRecord.TNF_EXTERNAL_TYPE)
                                continue;
                            byte[] iv = new byte[16];
                            System.arraycopy(encrypted.getPayload(), 0, iv, 0, 16);
                            byte[] payLoad = new byte[encrypted.getPayload().length - 16];
                            System.arraycopy(encrypted.getPayload(), 16, payLoad, 0, payLoad.length);
                            try {
                                String decryptedText = ReaderFragment.decrypt(password.getText().toString(), iv, payLoad);
                                if(decryptedText.equals("Wrong password")){
                                    Toast.makeText(activity, "Wrong password", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                decrypted_records.add(NdefRecord.createTextRecord(Locale.ENGLISH.getLanguage(),
                                        decryptedText));
                            } catch (NoSuchAlgorithmException e) {
                                e.printStackTrace();
                            }
                        }
                    }

                    //Need the delay for the password view to lose focus.
                    new Handler().postDelayed(() -> paste_alert.show(), 1);
                }
            }).setNegativeButton("Cancel", (dialog, which) -> decrypted_records = null).show();
        }else{
            Toast.makeText(activity, "Tag does not contain any password protected content.", Toast.LENGTH_SHORT).show();
        }

    }

    private static boolean requiresPassword(NdefMessage... messages){
        for(NdefMessage message: messages) {
            for (NdefRecord encrypted : message.getRecords()) {
                if((Arrays.equals(encrypted.getId(), MainActivity.TAG_UID) || Arrays.equals(encrypted.getId(), MainActivity.OLD_TAG_UID))
                        && encrypted.getTnf() == NdefRecord.TNF_EXTERNAL_TYPE){
                    return true;
                }

            }
        }
        return false;
    }
}
