package com.nfcencrypter;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcB;
import android.nfc.tech.NfcBarcode;
import android.nfc.tech.NfcF;
import android.nfc.tech.NfcV;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;
import com.nfcencrypter.Fragments.CopyFragment;
import com.nfcencrypter.Fragments.MainPageFragmentAdapter;
import com.nfcencrypter.Fragments.ReaderFragment;
import com.nfcencrypter.Fragments.WriterFragment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends AppCompatActivity implements ReaderFragment.OnFragmentFinishLoad{

    public static final String MIME_TEXT_PLAIN = "text/plain";
    public static final byte[] TAG_UID = { 71, 10, 119, 7, 87, 121, 2, 9, 127, 1, 67, 78, 2, 68, 48, 44};
    //Used for backwards compatibility.
    public static final byte[] OLD_TAG_UID = { 71, 10, 119, 7, 87, 121, 37, 33, 39, 117, 127, 80, 111, 102, 78, 2, 100, 77, 57, 124, 65, 39, 99, 46, 127, 23, 66, 20, 127, 27, 62, 27, 106, 66, 106, 127, 23, 63, 43, 82, 8, 54, 121, 69, 40, 79, 78, 1, 84, 50, 64, 110, 46, 92, 84, 114, 121, 92, 15, 59, 88, 99, 87, 75, 94, 57, 46, 50, 8, 46, 74, 83, 57, 7, 36, 32, 95, 85, 89, 64, 127, 1, 27, 122, 42, 20, 122, 46, 32, 56, 2, 22, 67, 0, 97, 105, 109, 14, 89, 67, 78, 2, 115, 37, 2, 21, 39, 22, 60, 14, 27, 111, 116, 103, 67, 118, 124, 6, 65, 79, 78, 7, 48, 20, 95, 56, 18, 68, 48, 44};


    ViewPager mainPage;
    MainPageFragmentAdapter adapter;
    Drawable eye, eyeChecked;
    private NfcAdapter mNfcAdapter;
    boolean hasData;
    Parcelable[] data;
    public static AlertDialog writing_successful, writing_failed;

    public static void setupForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        final Intent intent = new Intent(activity.getApplicationContext(), activity.getClass());
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        final PendingIntent pendingIntent = PendingIntent.getActivity(activity.getApplicationContext(), 0, intent, 0);

        IntentFilter[] filters = new IntentFilter[2];
        String[][] techList = new String[][] {
                new String[] { NfcA.class.getName() },
                new String[] { NfcB.class.getName() },
                new String[] { NfcF.class.getName() },
                new String[] { NfcV.class.getName() },
                new String[] { NfcBarcode.class.getName() },
        };

        // Notice that this is the same filter as in our manifest.
        filters[0] = new IntentFilter();
        filters[0].addAction(NfcAdapter.ACTION_NDEF_DISCOVERED);
        filters[0].addCategory(Intent.CATEGORY_DEFAULT);

        filters[1] = new IntentFilter();
        filters[1].addAction(NfcAdapter.ACTION_TECH_DISCOVERED);
        filters[1].addCategory(Intent.CATEGORY_DEFAULT);
        try {
            filters[0].addDataType(MIME_TEXT_PLAIN);
            filters[1].addDataType("*/*");
        } catch (IntentFilter.MalformedMimeTypeException e) {
            throw new RuntimeException("Check your mime type.");
        }

        adapter.enableForegroundDispatch(activity, pendingIntent, filters, techList);
    }

    public static void stopForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        adapter.disableForegroundDispatch(activity);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        eye = getResources().getDrawable(R.drawable.show_icon, null);
        eyeChecked = getResources().getDrawable(R.drawable.hide_icon, null);
        writing_successful = new AlertDialog.Builder(this).setTitle("Writing successful.").setPositiveButton("Ok", null).create();
        writing_failed = new AlertDialog.Builder(this).setTitle("Writing failed.").setPositiveButton("Ok", null).create();

        mainPage = findViewById(R.id.mainPager);
        adapter = new MainPageFragmentAdapter(getSupportFragmentManager());
        mainPage.setAdapter(adapter);
        TabLayout tabLayout = findViewById(R.id.tab_layout);
        tabLayout.setupWithViewPager(mainPage);

        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);

        if (mNfcAdapter == null) {
            Toast.makeText(this, "This device doesn't support NFC.", Toast.LENGTH_LONG).show();
            finish();

        }
        mainPage.setCurrentItem(0);
        handleIntent(getIntent());
    }

    @Override
    public void onFinishLoading(RecyclerView view) {
        view.setAdapter(new TagInfoAdapter(new ArrayList<>()));
        view.setLayoutManager(new LinearLayoutManager(this));
        if(hasData){
            ReaderFragment readerFragment = (ReaderFragment) adapter.getRegisteredFragment(0);
            if (readerFragment != null){
                readerFragment.readTag(data);
            }
            hasData = false;
        }
    }

    @Override
    public void onBackPressed() {
        if (mainPage.getCurrentItem() == 0) {
            // If the user is currently looking at the first step, allow the system to handle the
            // Back button. This calls finish() on this activity and pops the back stack.
            super.onBackPressed();
        } else {
            // Otherwise, select the previous step.
            mainPage.setCurrentItem(mainPage.getCurrentItem() - 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        setupForegroundDispatch(this, mNfcAdapter);
        if(NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction()) || NfcAdapter.ACTION_TECH_DISCOVERED.equals(getIntent().getAction()))
        mainPage.setCurrentItem(0);
    }

    @Override
    protected void onPause() {

        stopForegroundDispatch(this, mNfcAdapter);

        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    public void handleIntent(Intent intent) {
        if(intent.getAction() == null) return;
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction()) || NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction())) {
            switch (mainPage.getCurrentItem()) {
                case 0:
                    ReaderFragment readerFragment = (ReaderFragment) adapter.getRegisteredFragment(0);
                    if(readerFragment == null){
                        data = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
                        hasData = true;
                    }else{
                        readerFragment.readTag(intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES));
                    }
                    break;
                case 1:
                    WriterFragment writerFragment = (WriterFragment) adapter.getRegisteredFragment(1);
                    writerFragment.write(intent);

                    break;
                case 2:
                    CopyFragment copy = (CopyFragment) adapter.getRegisteredFragment(2);
                    if(CopyFragment.erase_alert != null && CopyFragment.erase_alert.isShowing()){
                        copy.eraseTag(intent);
                        CopyFragment.erase_alert.dismiss();
                    }else if(CopyFragment.copy_alert != null && CopyFragment.copy_alert.isShowing()){
                        copy.copyTag(intent);
                    }else if(CopyFragment.paste_alert != null && CopyFragment.paste_alert.isShowing()){
                        copy.pasteTag(intent);
                        CopyFragment.paste_alert.dismiss();
                    }else if(CopyFragment.remove_password_alert != null && CopyFragment.remove_password_alert.isShowing()){
                        copy.removePassword(intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES));
                    }

            }
        }
    }

    public void showPassword(View view){
        EditText password = findViewById(R.id.password);
        EditText confirmation = findViewById(R.id.password_confirmation);
        ImageButton imageButton = (ImageButton) view;
        if(imageButton.getTag() == null){
            password.setInputType( InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            confirmation.setInputType( InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            imageButton.setTag(" ");
            imageButton.setImageDrawable(eyeChecked);
        }else{
            password.setInputType( InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            confirmation.setInputType( InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            imageButton.setTag(null);
            imageButton.setImageDrawable(eye);
        }


    }

    public void hideKeyboard(){
        InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
        //Find the currently focused view, so we can grab the correct window token from it.
        View focus = getCurrentFocus();
        //If no view currently has focus, create a new one, just so we can grab a window token from it
        if (focus == null) {
            focus = new View(this);
        }
        imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
    }

    public static SecretKeySpec generateKey(final String password) throws NoSuchAlgorithmException {
        final MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = password.getBytes(StandardCharsets.UTF_8);
        digest.update(bytes, 0, bytes.length);
        byte[] key = digest.digest();
        return new SecretKeySpec(key, "AES");
    }

    public static boolean checkTech(Tag tag){
        for(String tech : tag.getTechList()){
            if(Ndef.class.getName().equals(tech)){
                return true;
            }
        }
        return false;
    }


}


