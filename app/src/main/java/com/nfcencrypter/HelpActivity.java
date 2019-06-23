package com.nfcencrypter;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.widget.ScrollView;

import java.util.Objects;

public class HelpActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);
        Toolbar myChildToolbar = findViewById(R.id.help_toolbar);
        setSupportActionBar(myChildToolbar);

        ActionBar ab = getSupportActionBar();

        Objects.requireNonNull(ab).setDisplayHomeAsUpEnabled(true);


        ScrollView scroll = findViewById(R.id.scroll);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.help_options, menu);
        return true;
    }
}
