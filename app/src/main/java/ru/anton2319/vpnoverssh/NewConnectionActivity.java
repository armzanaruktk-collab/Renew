package ru.anton2319.vpnoverssh;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import ru.anton2319.vpnoverssh.data.PayloadProfile;
import ru.anton2319.vpnoverssh.data.SSHConnectionProfile;
import ru.anton2319.vpnoverssh.data.utils.PayloadProfileManager;
import ru.anton2319.vpnoverssh.data.utils.SSHConnectionProfileManager;

public class NewConnectionActivity extends AppCompatActivity {
    private View passwordInputLayout, addKeyButton, keyCard, payloadLabel;
    private TextInputEditText serverAddressInput, serverPortInput, usernameInput, passwordInput;
    private FloatingActionButton saveButton;
    private SSHConnectionProfile.AuthenticationType authenticationType = SSHConnectionProfile.AuthenticationType.PASSWORD;
    private String privateKey;
    private TextView keyInfoTextView;
    private static final int READ_REQUEST_CODE = 42;
    private SSHConnectionProfile sshConnectionProfile = new SSHConnectionProfile();
    private SSHConnectionProfileManager sshConnectionProfileManager;
    private PayloadProfileManager payloadManager;
    private UUID profileUuid;
    private Spinner payloadSpinner;
    private List<PayloadProfile> payloadProfiles = new ArrayList<>();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sshConnectionProfileManager = new SSHConnectionProfileManager(this);
        payloadManager = new PayloadProfileManager(this);
        if (getIntent().getStringExtra("uuid") != null) {
            profileUuid = UUID.fromString(getIntent().getStringExtra("uuid"));
        }
        setContentView(R.layout.activity_new_connection);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        serverAddressInput = findViewById(R.id.serverAddressInput);
        serverPortInput = findViewById(R.id.serverPortInput);
        usernameInput = findViewById(R.id.usernameInput);
        passwordInput = findViewById(R.id.passwordInput);
        passwordInputLayout = findViewById(R.id.passwordInputLayout);
        addKeyButton = findViewById(R.id.addKeyButton);
        keyCard = findViewById(R.id.keyCard);
        saveButton = findViewById(R.id.floatingActionButton);
        payloadSpinner = findViewById(R.id.payloadSpinner);
        payloadLabel = findViewById(R.id.payloadLabel);

        AutoCompleteTextView authDropdown = findViewById(R.id.authenticationTypeInput);
        ArrayAdapter<CharSequence> authAdapter = ArrayAdapter.createFromResource(this,
                R.array.authentication_type_array, android.R.layout.simple_dropdown_item_1line);
        authDropdown.setAdapter(authAdapter);

        loadPayloadChoices();
        if (profileUuid != null) {
            SSHConnectionProfile loaded = sshConnectionProfileManager.loadProfileByUUID(profileUuid);
            if (loaded != null) sshConnectionProfile = loaded;
        }
        if (sshConnectionProfile.getServerIP() != null) {
            serverAddressInput.setText(sshConnectionProfile.getServerIP());
            serverPortInput.setText(String.valueOf(sshConnectionProfile.getServerPort()));
            usernameInput.setText(sshConnectionProfile.getUsername());
            passwordInput.setText(sshConnectionProfile.getPassword());
        }
        if (sshConnectionProfile.getAuthenticationType() == SSHConnectionProfile.AuthenticationType.PRIVATE_KEY) {
            authenticationType = SSHConnectionProfile.AuthenticationType.PRIVATE_KEY;
            passwordInputLayout.setVisibility(View.GONE);
            addKeyButton.setVisibility(View.VISIBLE);
            privateKey = sshConnectionProfile.getPrivateKey();
            authDropdown.setText("Private Key", false);
            if (privateKey != null) updateKeyCardData();
        } else {
            authDropdown.setText("Password", false);
        }


        authDropdown.setOnItemClickListener((parent, view, position, id) -> {
            if (position == 0) {
                authenticationType = SSHConnectionProfile.AuthenticationType.PASSWORD;
                passwordInputLayout.setVisibility(View.VISIBLE);
                addKeyButton.setVisibility(View.GONE);
                keyCard.setVisibility(View.GONE);
            } else {
                authenticationType = SSHConnectionProfile.AuthenticationType.PRIVATE_KEY;
                passwordInputLayout.setVisibility(View.GONE);
                addKeyButton.setVisibility(View.VISIBLE);
            }
        });

        addKeyButton.setVisibility(authenticationType == SSHConnectionProfile.AuthenticationType.PRIVATE_KEY ? View.VISIBLE : View.GONE);
        saveButton.setOnClickListener(v -> saveProfile());
        addKeyButton.setOnClickListener(v -> pickFile());
    }

    private void loadPayloadChoices() {
        payloadProfiles = payloadManager.loadProfiles();
        List<String> labels = new ArrayList<>();
        labels.add("Direct SSH (no payload)");
        for (PayloadProfile p : payloadProfiles) labels.add(p.name + " (" + p.proxyHost + ":" + p.proxyPort + ")");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        payloadSpinner.setAdapter(adapter);
    }

    private void saveProfile() {
        String host = serverAddressInput.getText() == null ? "" : serverAddressInput.getText().toString().trim();
        String portText = serverPortInput.getText() == null ? "" : serverPortInput.getText().toString().trim();
        String user = usernameInput.getText() == null ? "" : usernameInput.getText().toString().trim();
        int port;
        try { port = Integer.parseInt(portText); } catch (Exception e) { port = -1; }
        if (host.isEmpty() || user.isEmpty() || port < 1 || port > 65535) {
            Toast.makeText(this, "Enter SSH host, username and a valid port", Toast.LENGTH_LONG).show();
            return;
        }
        int selectedPayloadPosition = payloadSpinner.getSelectedItemPosition();
        boolean usePayload = selectedPayloadPosition > 0;
        if (usePayload && selectedPayloadPosition - 1 >= payloadProfiles.size()) {
            Toast.makeText(this, "Selected payload is unavailable", Toast.LENGTH_LONG).show();
            return;
        }
        sshConnectionProfile.setAuthenticationType(authenticationType);
        sshConnectionProfile.setServerIP(host);
        sshConnectionProfile.setServerPort(port);
        sshConnectionProfile.setUsername(user);
        sshConnectionProfile.setConnectionMode(usePayload ? "PAYLOAD" : "DIRECT");
        if (usePayload) sshConnectionProfile.setPayloadUuid(payloadProfiles.get(selectedPayloadPosition - 1).uuid);
        else sshConnectionProfile.setPayloadUuid(null);
        if (authenticationType == SSHConnectionProfile.AuthenticationType.PASSWORD) {
            sshConnectionProfile.setPassword(passwordInput.getText() == null ? "" : passwordInput.getText().toString());
            sshConnectionProfile.setPrivateKey(null);
        } else {
            if (privateKey == null || privateKey.trim().isEmpty()) {
                Toast.makeText(this, "Select an SSH private key", Toast.LENGTH_LONG).show();
                return;
            }
            sshConnectionProfile.setPrivateKey(privateKey);
            sshConnectionProfile.setPassword(null);
        }
        sshConnectionProfileManager.saveProfile(sshConnectionProfile);
        finish();
    }

    public void pickFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, READ_REQUEST_CODE);
    }

    private String readTextFromUri(Uri uri) {
        StringBuilder result = new StringBuilder();
        try (InputStream input = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line).append("\n");
        } catch (Exception e) {
            Toast.makeText(this, "Unable to read private key", Toast.LENGTH_LONG).show();
        }
        return result.toString();
    }

    @Override public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            privateKey = readTextFromUri(data.getData());
            updateKeyCardData();
        }
    }

    @Override public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void updateKeyCardData() {
        if (privateKey == null) return;
        String preview = privateKey.replace("-----BEGIN OPENSSH PRIVATE KEY-----", "")
                .replace("-----END OPENSSH PRIVATE KEY-----", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "").trim();
        keyInfoTextView = findViewById(R.id.keyInfoTextView);
        if (preview.length() > 50) preview = preview.substring(0, 24) + "•••••\n•••••" + preview.substring(preview.length() - 24);
        keyInfoTextView.setText(preview);
        keyCard.setVisibility(View.VISIBLE);
        MaterialButton deleteButton = findViewById(R.id.deleteKeyButton);
        deleteButton.setOnClickListener(v -> {
            keyInfoTextView.setText("");
            privateKey = null;
            keyCard.setVisibility(View.GONE);
        });
    }
}