package com.nfcencrypter.Fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
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
import android.os.Handler;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
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
import java.util.Objects;

public class CopyFragment extends Fragment {

    public static AlertDialog erase_alert, copy_alert, remove_password_alert, paste_alert, erase_confirmation;
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

        erase_alert = new AlertDialog.Builder(view.getContext()).setTitle("Erasing...").setMessage("Place an Ndef-supporting tag to erase").create();
        copy_alert = new AlertDialog.Builder(view.getContext()).setTitle("Copying...").setMessage("Place an encrypted tag to copy its content.").create();
        remove_password_alert = new AlertDialog.Builder(view.getContext()).setTitle("Removing...").setMessage("Place an encrypted tag to remove its password.").create();
        paste_alert = new AlertDialog.Builder(view.getContext()).setTitle("Pasting").setMessage("Place a tag to paste the data to").create();
        erase_confirmation = new AlertDialog.Builder(view.getContext()).setTitle("Are you sure?")
                .setIcon(android.R.drawable.ic_dialog_alert).setMessage("This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> erase_alert.show()).setNegativeButton("Cancel", null).create();

        Button erase = view.findViewById(R.id.erase_tag);
        Button copy = view.findViewById(R.id.copy_button);
        Button removePassword = view.findViewById(R.id.remove_password);

        copy.setOnClickListener(v -> copy_alert.show());
        erase.setOnClickListener(v -> erase_confirmation.show());
        removePassword.setOnClickListener(v -> remove_password_alert.show());


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
            }
            ndef.close();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (FormatException e) {
            e.printStackTrace();
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

    public void removePassword(Parcelable[] rawMessages){
        remove_password_alert.dismiss();
        NdefMessage[] messages = new NdefMessage[rawMessages.length];
        for(int i = 0; i < rawMessages.length; i++){
            messages[i] = (NdefMessage) rawMessages[i];
        }
        MainActivity activity = (MainActivity) getActivity();
        @SuppressLint("InflateParams")
        LinearLayout rootPrompt = (LinearLayout) getLayoutInflater().inflate(R.layout.password_prompt, null);
        EditText password = rootPrompt.findViewById(R.id.password_prompt);
        decrypted_records = new ArrayList<>();
        boolean requirePassword = false;
        for(NdefMessage message: messages) {
            for (NdefRecord encrypted : message.getRecords()) {
                if(Arrays.equals(encrypted.getId(), MainActivity.TAG_UID) && encrypted.getTnf() == NdefRecord.TNF_EXTERNAL_TYPE){
                    requirePassword = true;
                }

            }
        }
        if(requirePassword) {
            new AlertDialog.Builder(activity).setView(rootPrompt).setPositiveButton("Decrypt", (dialog, button) -> {
                if (!password.getText().toString().isEmpty()) {
                    for (NdefMessage message : messages) {
                        for (NdefRecord encrypted : message.getRecords()) {
                            if (!Arrays.equals(encrypted.getId(), MainActivity.TAG_UID) || encrypted.getTnf() != NdefRecord.TNF_EXTERNAL_TYPE)
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
                    Context context = getContext();
                    System.out.println(context);
                    if (context != null) {
                        hideKeyboardFrom(context, Objects.requireNonNull(getView()));
                    }

                    new Handler().postDelayed(() -> paste_alert.show(), 1);
                }
            }).setNegativeButton("Cancel", null).show();
        }else{
            Toast.makeText(activity, "Tag does not contain any password protected content.", Toast.LENGTH_SHORT).show();
        }

    }

    private static void hideKeyboardFrom(Context context, View view) {
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Activity.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
}
