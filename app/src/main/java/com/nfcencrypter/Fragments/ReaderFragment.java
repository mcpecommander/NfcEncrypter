package com.nfcencrypter.Fragments;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nfcencrypter.MainActivity;
import com.nfcencrypter.R;
import com.nfcencrypter.TagInfoAdapter;
import com.scottyab.aescrypt.AESCrypt;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import javax.crypto.spec.SecretKeySpec;

public class ReaderFragment extends Fragment {

    private List<NdefRecord> encryptedRecords;
    private List<String> records;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        encryptedRecords = new ArrayList<>();
        records = new ArrayList<>();
        return inflater.inflate(
                R.layout.reader_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ((OnFragmentFinishLoad) Objects.requireNonNull(getActivity())).onFinishLoading(((ConstraintLayout)view).findViewById(R.id.record_view));
    }

    public interface OnFragmentFinishLoad {
        void onFinishLoading(RecyclerView view);
    }

    public void readTag(Parcelable[] parcelables){
        MainActivity activity = (MainActivity) getActivity();
        if(activity == null) return;
        encryptedRecords.clear();
        if (parcelables != null) {
            NdefMessage[] tagMessages = new NdefMessage[parcelables.length];
            for(int i = 0; i < parcelables.length; i++){
                tagMessages[i] = (NdefMessage) parcelables[i];
            }
            records.clear();
            boolean requirePassword = false;
            for(NdefMessage message : tagMessages){
                for(NdefRecord record : message.getRecords()){
                    switch (record.getTnf()){
                        case NdefRecord.TNF_EMPTY:
                            records.add("Empty Record");
                            break;
                        case NdefRecord.TNF_WELL_KNOWN:
                            if(Arrays.equals(record.getType(), NdefRecord.RTD_TEXT)){
                                try {
                                    records.add(readText(record.getPayload()));
                                } catch (UnsupportedEncodingException e) {
                                    records.add("Record corrupted.");
                                }
                            }
                            break;
                        case NdefRecord.TNF_EXTERNAL_TYPE:
                            if(record.getId() == null || record.getId().length == 0){
                                try {
                                    records.add(readText(record.getPayload()));
                                } catch (Exception e) {
                                    records.add("Non parsable record");
                                }
                            }else if (Arrays.equals(record.getId(), MainActivity.TAG_UID )){
                                requirePassword = true;
                                encryptedRecords.add(record);
                            }else if (Arrays.equals(record.getId(), MainActivity.OLD_TAG_UID )){
                                requirePassword = true;
                                encryptedRecords.add(record);
                            }
                            break;

                    }
                }
            }
            if(requirePassword){
                @SuppressLint("InflateParams")
                LinearLayout rootPrompt = (LinearLayout) getLayoutInflater().inflate(R.layout.password_prompt, null);
                EditText password = rootPrompt.findViewById(R.id.password_prompt);
                new AlertDialog.Builder(activity, R.style.CustomAlertDialog).setView(rootPrompt).setPositiveButton("Decrypt", (dialog, button) ->{
                    if(!password.getText().toString().isEmpty()){
                        for(NdefRecord encrypted : encryptedRecords){
                            byte[] iv = new byte[16];
                            System.arraycopy(encrypted.getPayload(), 0, iv, 0, 16);
                            byte[] payLoad = new byte[encrypted.getPayload().length - 16];
                            System.arraycopy(encrypted.getPayload(), 16, payLoad, 0, payLoad.length);
                            try {
                                records.add(decrypt(password.getText().toString(), iv,  payLoad));
                            } catch (NoSuchAlgorithmException e) {
                                e.printStackTrace();
                            }
                        }
                        updateDataset(activity);
                    }
                }).setNegativeButton("Cancel", null).show();
            }else{
                if(records.isEmpty()){
                    records.add("Couldn't read Tag");
                }
                updateDataset(activity);
            }
        }else{
            records.clear();
            records.add("Empty Tag");
            updateDataset(activity);
        }
    }

    static String decrypt(String password, byte[] iv, byte[] text) throws NoSuchAlgorithmException {
        SecretKeySpec spec = MainActivity.generateKey(password);
        try {
            return new String(AESCrypt.decrypt(spec, iv, text), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            return "Wrong password";
        } catch (IllegalArgumentException e){
            return " ";
        }

    }

    private void updateDataset(MainActivity activity) {
        RecyclerView infoHolder = Objects.requireNonNull(getView()).findViewById(R.id.record_view);
        if(infoHolder != null){

            if(infoHolder.getAdapter() != null){
                ((TagInfoAdapter)infoHolder.getAdapter()).setRecords(records);
                infoHolder.getAdapter().notifyDataSetChanged();
            }else{
                infoHolder.setAdapter(new TagInfoAdapter(records));
                infoHolder.setLayoutManager(new LinearLayoutManager(activity));
            }

        }
    }



    private String readText(byte[] payload) throws UnsupportedEncodingException {
        String textEncoding = ((payload[0] & 128) == 0) ? "UTF-8" : "UTF-16";

        //Get the Language Code
        int languageCodeLength = payload[0] & 63;

        //Get the Text
        return new String(payload, languageCodeLength + 1, payload.length - languageCodeLength - 1, textEncoding);

    }
}
