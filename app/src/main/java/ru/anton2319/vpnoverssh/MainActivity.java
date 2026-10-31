package ru.anton2319.vpnoverssh;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.preference.PreferenceManager;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import ru.anton2319.vpnoverssh.data.PayloadProfile;
import ru.anton2319.vpnoverssh.data.SSHConnectionProfile;
import ru.anton2319.vpnoverssh.data.singleton.PortForward;
import ru.anton2319.vpnoverssh.data.singleton.SocksPersistent;
import ru.anton2319.vpnoverssh.data.singleton.StatusInfo;
import ru.anton2319.vpnoverssh.data.utils.PayloadProfileManager;
import ru.anton2319.vpnoverssh.data.utils.SSHConnectionProfileManager;
import ru.anton2319.vpnoverssh.services.SocksProxyService;
import ru.anton2319.vpnoverssh.services.SshService;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private DrawerLayout drawerLayout;
    private View viewScreenHome;
    private View viewScreenSsh;
    private View viewScreenPayloadList;
    private View viewScreenPayloadEdit;
    private View viewScreenConnecting;
    private View viewScreenSettings;
    private View viewScreenLogs;

    private ImageView btnHeaderLeft;
    private TextView tvHeaderTitle;
    private ImageView btnHeaderShare;
    private ImageView btnHeaderTheme;
    private ImageView btnHeaderLog;
    private ImageView btnHeaderAdd;
    private ImageView btnHeaderSave;

    // Logging console views
    private TextView tvLogsCountBadge;
    private Button btnLogCopy;
    private Button btnLogClear;
    private android.widget.ScrollView scrollLogsConsole;
    private TextView tvLogsTerminalContent;
    private Button btnConnectingViewLog;

    // Bottom Navigation
    private LinearLayout navItemHome;
    private LinearLayout navItemPayload;
    private LinearLayout navItemSettings;
    private LinearLayout navItemMenu;
    private ImageView ivNavHome, ivNavPayload, ivNavSettings, ivNavMenu;
    private TextView tvNavHome, tvNavPayload, tvNavSettings, tvNavMenu;

    // Home views
    private TextView tvHomeServerName;
    private TextView tvHomePayloadName;
    private ImageView ivStatusIcon;
    private TextView tvStatusTitle;
    private TextView tvStatusDesc;
    private Button btnHomeConnect;

    // SSH screen views
    private TextView tvSshServerName;
    private TextView tvSshPayloadName;
    private TextView tvSshPayloadProxyDesc;
    private ImageView ivDirectSshRadio;
    private Button btnSshConnect;
    private boolean isDirectSsh = false;

    // Payload manager views
    private LinearLayout layoutPayloadItemsContainer;

    // Payload edit views
    private EditText etPayloadName;
    private EditText etPayloadDesc;
    private EditText etPayloadBody;
    private EditText etProxyHost;
    private EditText etProxyPort;
    private Button btnPayloadClear;
    private Button btnPayloadSave;
    private LinearLayout layoutPayloadTagChips;
    private TextView btnPayloadPresets;
    private PayloadProfile currentEditingProfile = null;

    // Server edit views
    private View viewScreenServerEdit;
    private EditText etServerName;
    private EditText etServerHost;
    private EditText etServerPort;
    private EditText etServerUser;
    private EditText etServerPassword;
    private LinearLayout rowServerModeDirect;
    private LinearLayout rowServerModePayload;
    private ImageView ivModeDirectRadio;
    private ImageView ivModePayloadRadio;
    private android.widget.Spinner spinnerServerPayloadChoice;
    private Button btnServerSave;
    private Button btnServerSaveAsNew;
    private Button btnServerClear;
    private Button btnServerDelete;
    private SSHConnectionProfile currentEditingServer = null;
    private boolean serverEditIsDirect = true;

    // Connecting views
    private TextView tvConnectingHeading;
    private ImageView ivStepProxyStatus;
    private TextView tvStepProxyText;
    private ImageView ivStepSshStatus;
    private TextView tvStepSshText;
    private ImageView ivStepRoutingStatus;
    private TextView tvStepRoutingText;
    private Button btnConnectingCancel;

    // Settings views
    private EditText etSettingsDns;
    private EditText etSettingsForwarderPort;
    private Button btnSettingsApps;
    private Button btnSettingsSave;

    // Managers & State
    private SSHConnectionProfileManager sshManager;
    private PayloadProfileManager payloadManager;
    private List<SSHConnectionProfile> sshProfileList = new ArrayList<>();
    private List<PayloadProfile> payloadProfileList = new ArrayList<>();
    private SSHConnectionProfile selectedSshProfile;
    private PayloadProfile selectedPayloadProfile;

    private SharedPreferences sharedPreferences;
    private Intent vpnIntent;
    private Intent sshIntent;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int currentTab = 0; // 0: Home, 1: Payload, 2: Settings

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        sshManager = new SSHConnectionProfileManager(this);
        payloadManager = new PayloadProfileManager(this);

        if (StatusInfo.getInstance().getVpnIntent() == null) {
            StatusInfo.getInstance().setVpnIntent(new Intent(this, SocksProxyService.class));
        }
        if (StatusInfo.getInstance().getSshIntent() == null) {
            StatusInfo.getInstance().setSshIntent(new Intent(this, SshService.class));
        }
        vpnIntent = StatusInfo.getInstance().getVpnIntent();
        sshIntent = StatusInfo.getInstance().getSshIntent();

        initViews();
        loadData();
        setupNavigation();
        setupHomeEvents();
        setupSshEvents();
        setupPayloadEditEvents();
        setupServerEditEvents();
        setupConnectingEvents();
        setupSettingsEvents();
        setupDrawerEvents();
        setupLogEvents();
    }

    private void initViews() {
        drawerLayout = findViewById(R.id.drawer_layout);

        viewScreenHome = findViewById(R.id.view_screen_home);
        viewScreenSsh = findViewById(R.id.view_screen_ssh);
        viewScreenPayloadList = findViewById(R.id.view_screen_payload_list);
        viewScreenPayloadEdit = findViewById(R.id.view_screen_payload_edit);
        viewScreenServerEdit = findViewById(R.id.view_screen_server_edit);
        viewScreenConnecting = findViewById(R.id.view_screen_connecting);
        viewScreenSettings = findViewById(R.id.view_screen_settings);
        viewScreenLogs = findViewById(R.id.view_screen_logs);

        btnHeaderLeft = findViewById(R.id.btn_header_left);
        tvHeaderTitle = findViewById(R.id.tv_header_title);
        btnHeaderShare = findViewById(R.id.btn_header_share);
        btnHeaderTheme = findViewById(R.id.btn_header_theme);
        btnHeaderLog = findViewById(R.id.btn_header_log);
        btnHeaderAdd = findViewById(R.id.btn_header_add);
        btnHeaderSave = findViewById(R.id.btn_header_save);

        // Logging console
        tvLogsCountBadge = findViewById(R.id.tv_logs_count_badge);
        btnLogCopy = findViewById(R.id.btn_log_copy);
        btnLogClear = findViewById(R.id.btn_log_clear);
        scrollLogsConsole = findViewById(R.id.scroll_logs_console);
        tvLogsTerminalContent = findViewById(R.id.tv_logs_terminal_content);
        btnConnectingViewLog = findViewById(R.id.btn_connecting_view_log);

        navItemHome = findViewById(R.id.nav_item_home);
        navItemPayload = findViewById(R.id.nav_item_payload);
        navItemSettings = findViewById(R.id.nav_item_settings);
        navItemMenu = findViewById(R.id.nav_item_menu);

        ivNavHome = findViewById(R.id.iv_nav_home);
        ivNavPayload = findViewById(R.id.iv_nav_payload);
        ivNavSettings = findViewById(R.id.iv_nav_settings);
        ivNavMenu = findViewById(R.id.iv_nav_menu);

        tvNavHome = findViewById(R.id.tv_nav_home);
        tvNavPayload = findViewById(R.id.tv_nav_payload);
        tvNavSettings = findViewById(R.id.tv_nav_settings);
        tvNavMenu = findViewById(R.id.tv_nav_menu);

        // Home
        tvHomeServerName = findViewById(R.id.tv_home_server_name);
        tvHomePayloadName = findViewById(R.id.tv_home_payload_name);
        ivStatusIcon = findViewById(R.id.iv_status_icon);
        tvStatusTitle = findViewById(R.id.tv_status_title);
        tvStatusDesc = findViewById(R.id.tv_status_desc);
        btnHomeConnect = findViewById(R.id.btn_home_connect);

        // SSH
        tvSshServerName = findViewById(R.id.tv_ssh_server_name);
        tvSshPayloadName = findViewById(R.id.tv_ssh_payload_name);
        tvSshPayloadProxyDesc = findViewById(R.id.tv_ssh_payload_proxy_desc);
        ivDirectSshRadio = findViewById(R.id.iv_direct_ssh_radio);
        btnSshConnect = findViewById(R.id.btn_ssh_connect);

        // Payload
        layoutPayloadItemsContainer = findViewById(R.id.layout_payload_items_container);
        etPayloadName = findViewById(R.id.et_payload_name);
        etPayloadDesc = findViewById(R.id.et_payload_desc);
        etPayloadBody = findViewById(R.id.et_payload_body);
        etProxyHost = findViewById(R.id.et_proxy_host);
        etProxyPort = findViewById(R.id.et_proxy_port);
        btnPayloadClear = findViewById(R.id.btn_payload_clear);
        btnPayloadSave = findViewById(R.id.btn_payload_save);
        layoutPayloadTagChips = findViewById(R.id.layout_payload_tag_chips);
        btnPayloadPresets = findViewById(R.id.btn_payload_presets);

        // Server edit views
        etServerName = findViewById(R.id.et_server_name);
        etServerHost = findViewById(R.id.et_server_host);
        etServerPort = findViewById(R.id.et_server_port);
        etServerUser = findViewById(R.id.et_server_user);
        etServerPassword = findViewById(R.id.et_server_password);
        rowServerModeDirect = findViewById(R.id.row_server_mode_direct);
        rowServerModePayload = findViewById(R.id.row_server_mode_payload);
        ivModeDirectRadio = findViewById(R.id.iv_mode_direct_radio);
        ivModePayloadRadio = findViewById(R.id.iv_mode_payload_radio);
        spinnerServerPayloadChoice = findViewById(R.id.spinner_server_payload_choice);
        btnServerSave = findViewById(R.id.btn_server_save);
        btnServerSaveAsNew = findViewById(R.id.btn_server_save_as_new);
        btnServerClear = findViewById(R.id.btn_server_clear);
        btnServerDelete = findViewById(R.id.btn_server_delete);

        // Connecting
        tvConnectingHeading = findViewById(R.id.tv_connecting_heading);
        ivStepProxyStatus = findViewById(R.id.iv_step_proxy_status);
        tvStepProxyText = findViewById(R.id.tv_step_proxy_text);
        ivStepSshStatus = findViewById(R.id.iv_step_ssh_status);
        tvStepSshText = findViewById(R.id.tv_step_ssh_text);
        ivStepRoutingStatus = findViewById(R.id.iv_step_routing_status);
        tvStepRoutingText = findViewById(R.id.tv_step_routing_text);
        btnConnectingCancel = findViewById(R.id.btn_connecting_cancel);

        // Settings
        etSettingsDns = findViewById(R.id.et_settings_dns);
        etSettingsForwarderPort = findViewById(R.id.et_settings_forwarder_port);
        btnSettingsApps = findViewById(R.id.btn_settings_apps);
        btnSettingsSave = findViewById(R.id.btn_settings_save);
    }

    private void loadData() {
        sshProfileList = sshManager.loadProfiles();
        payloadProfileList = payloadManager.loadProfiles();

        if (!sshProfileList.isEmpty()) {
            selectedSshProfile = sshProfileList.get(0);
        }
        if (!payloadProfileList.isEmpty()) {
            selectedPayloadProfile = payloadProfileList.get(0);
        }

        updateHomeDisplays();
        updateSshDisplays();
        populatePayloadList();
        loadSettingsValues();
    }

    private void updateHomeDisplays() {
        if (selectedSshProfile != null) {
            tvHomeServerName.setText(selectedSshProfile.getProfileName());
            tvSshServerName.setText(selectedSshProfile.getProfileName());
        }
        if (selectedPayloadProfile != null) {
            tvHomePayloadName.setText(selectedPayloadProfile.name);
            tvSshPayloadName.setText(selectedPayloadProfile.name);
            tvSshPayloadProxyDesc.setText("Proxy: " + selectedPayloadProfile.proxyHost + ":" + selectedPayloadProfile.proxyPort);
        }

        if (StatusInfo.getInstance().isActive()) {
            ivStatusIcon.setImageResource(R.drawable.check_circle_fill0_wght400_grad0_opsz24);
            ivStatusIcon.setColorFilter(getColor(R.color.neon_green));
            tvStatusTitle.setText(R.string.status_connected);
            tvStatusTitle.setTextColor(getColor(R.color.neon_green));
            tvStatusDesc.setText(R.string.status_connected_desc);
            btnHomeConnect.setText(R.string.btn_disconnect);
            btnSshConnect.setText(R.string.btn_disconnect);
        } else {
            ivStatusIcon.setImageResource(R.drawable.check_circle_fill0_wght400_grad0_opsz24);
            ivStatusIcon.setColorFilter(getColor(R.color.neon_green));
            tvStatusTitle.setText(R.string.status_ready);
            tvStatusTitle.setTextColor(getColor(R.color.neon_green));
            tvStatusDesc.setText(R.string.status_ready_desc);
            btnHomeConnect.setText(R.string.btn_connect);
            btnSshConnect.setText(R.string.btn_connect);
        }
    }

    private void updateSshDisplays() {
        if (selectedSshProfile != null) {
            isDirectSsh = "DIRECT".equalsIgnoreCase(selectedSshProfile.getConnectionMode());
            ivDirectSshRadio.setImageResource(isDirectSsh ? R.drawable.bg_radio_checked : R.drawable.bg_radio_unchecked);
        }
    }

    private void setupNavigation() {
        navItemHome.setOnClickListener(v -> showScreen(0));
        navItemPayload.setOnClickListener(v -> showScreen(1));
        navItemSettings.setOnClickListener(v -> showScreen(2));
        navItemMenu.setOnClickListener(v -> {
            if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
                drawerLayout.closeDrawer(GravityCompat.END);
            } else {
                drawerLayout.openDrawer(GravityCompat.END);
            }
        });

        btnHeaderLeft.setOnClickListener(v -> {
            if (viewScreenServerEdit.getVisibility() == View.VISIBLE) {
                showScreen(0); // Return to Home
            } else if (viewScreenPayloadEdit.getVisibility() == View.VISIBLE) {
                showScreen(1); // Return to Payload list
            } else if (viewScreenSsh.getVisibility() == View.VISIBLE || viewScreenConnecting.getVisibility() == View.VISIBLE) {
                showScreen(0); // Return to Home
            } else {
                showScreen(0);
            }
        });

        btnHeaderShare.setOnClickListener(v -> {
            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_TEXT, "godarmxvpn - High Performance VPN over SSH Tunnel & HTTP Payload Proxy Client");
            sendIntent.setType("text/plain");
            startActivity(Intent.createChooser(sendIntent, "แชร์ godarmxvpn"));
        });

        btnHeaderTheme.setOnClickListener(v -> {
            Toast.makeText(this, "ธีม Cyber Neon ปรับปรุงสีอัตโนมัติแล้ว", Toast.LENGTH_SHORT).show();
        });

        btnHeaderLog.setOnClickListener(v -> showScreen(6));

        btnHeaderAdd.setOnClickListener(v -> openPayloadEditor(null));
        btnHeaderSave.setOnClickListener(v -> {
            if (currentTab == 7) {
                saveServer(false);
            } else if (currentTab == 4) {
                saveCurrentPayload();
            }
        });
    }

    private void showScreen(int screenIndex) {
        currentTab = screenIndex;
        viewScreenHome.setVisibility(screenIndex == 0 ? View.VISIBLE : View.GONE);
        viewScreenSsh.setVisibility(screenIndex == 3 ? View.VISIBLE : View.GONE);
        viewScreenPayloadList.setVisibility(screenIndex == 1 ? View.VISIBLE : View.GONE);
        viewScreenPayloadEdit.setVisibility(screenIndex == 4 ? View.VISIBLE : View.GONE);
        viewScreenConnecting.setVisibility(screenIndex == 5 ? View.VISIBLE : View.GONE);
        viewScreenSettings.setVisibility(screenIndex == 2 ? View.VISIBLE : View.GONE);
        viewScreenLogs.setVisibility(screenIndex == 6 ? View.VISIBLE : View.GONE);
        viewScreenServerEdit.setVisibility(screenIndex == 7 ? View.VISIBLE : View.GONE);

        // Header actions visibility
        btnHeaderShare.setVisibility(screenIndex == 0 || screenIndex == 3 ? View.VISIBLE : View.GONE);
        btnHeaderTheme.setVisibility(screenIndex == 0 || screenIndex == 3 ? View.VISIBLE : View.GONE);
        btnHeaderLog.setVisibility(screenIndex == 6 ? View.GONE : View.VISIBLE);
        btnHeaderAdd.setVisibility(screenIndex == 1 ? View.VISIBLE : View.GONE);
        btnHeaderSave.setVisibility((screenIndex == 4 || screenIndex == 7) ? View.VISIBLE : View.GONE);

        // Reset bottom nav highlights
        int activeColor = getColor(R.color.neon_green);
        int inactiveColor = getColor(R.color.text_muted);

        ivNavHome.setColorFilter(screenIndex == 0 ? activeColor : inactiveColor);
        tvNavHome.setTextColor(screenIndex == 0 ? activeColor : inactiveColor);

        ivNavPayload.setColorFilter((screenIndex == 1 || screenIndex == 4) ? activeColor : inactiveColor);
        tvNavPayload.setTextColor((screenIndex == 1 || screenIndex == 4) ? activeColor : inactiveColor);

        ivNavSettings.setColorFilter(screenIndex == 2 ? activeColor : inactiveColor);
        tvNavSettings.setTextColor(screenIndex == 2 ? activeColor : inactiveColor);

        ivNavMenu.setColorFilter(inactiveColor);
        tvNavMenu.setTextColor(inactiveColor);

        switch (screenIndex) {
            case 0:
                tvHeaderTitle.setText(R.string.nav_home);
                btnHeaderLeft.setImageResource(R.drawable.ic_home_nav);
                updateHomeDisplays();
                break;
            case 1:
                tvHeaderTitle.setText(R.string.payload_manage_title);
                btnHeaderLeft.setImageResource(R.drawable.ic_home_nav);
                populatePayloadList();
                break;
            case 2:
                tvHeaderTitle.setText(R.string.nav_settings);
                btnHeaderLeft.setImageResource(R.drawable.ic_home_nav);
                break;
            case 3:
                tvHeaderTitle.setText(R.string.ssh_header_title);
                btnHeaderLeft.setImageResource(R.drawable.ic_home_nav);
                updateSshDisplays();
                break;
            case 4:
                tvHeaderTitle.setText(currentEditingProfile != null ? R.string.payload_edit_title : R.string.payload_add_title);
                btnHeaderLeft.setImageResource(R.drawable.ic_home_nav);
                break;
            case 5:
                tvHeaderTitle.setText(R.string.connecting_title);
                btnHeaderLeft.setImageResource(R.drawable.ic_home_nav);
                break;
            case 6:
                tvHeaderTitle.setText(R.string.log_view_title);
                btnHeaderLeft.setImageResource(R.drawable.ic_home_nav);
                renderAllLogs();
                break;
            case 7:
                tvHeaderTitle.setText(currentEditingServer != null ? R.string.server_edit_title : R.string.server_add_title);
                btnHeaderLeft.setImageResource(R.drawable.ic_home_nav);
                break;
        }
    }

    private void setupHomeEvents() {
        findViewById(R.id.card_home_server).setOnClickListener(v -> showServerPicker());
        findViewById(R.id.card_home_payload).setOnClickListener(v -> showPayloadPicker());

        btnHomeConnect.setOnClickListener(v -> {
            if (StatusInfo.getInstance().isActive()) {
                disconnectVpn();
            } else {
                startConnectSequence();
            }
        });
    }

    private void setupSshEvents() {
        findViewById(R.id.card_ssh_server).setOnClickListener(v -> showServerPicker());
        findViewById(R.id.card_ssh_payload).setOnClickListener(v -> showPayloadPicker());

        findViewById(R.id.row_direct_ssh_option).setOnClickListener(v -> {
            isDirectSsh = !isDirectSsh;
            ivDirectSshRadio.setImageResource(isDirectSsh ? R.drawable.bg_radio_checked : R.drawable.bg_radio_unchecked);
            if (selectedSshProfile != null) {
                selectedSshProfile.setConnectionMode(isDirectSsh ? "DIRECT" : "PAYLOAD");
                sshManager.saveProfile(selectedSshProfile);
            }
        });

        btnSshConnect.setOnClickListener(v -> {
            if (StatusInfo.getInstance().isActive()) {
                disconnectVpn();
            } else {
                startConnectSequence();
            }
        });
    }

    private void showServerPicker() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_select_server, null);
        dialog.setContentView(dialogView);

        LinearLayout itemsContainer = dialogView.findViewById(R.id.server_dialog_items_container);
        Button btnAddServer = dialogView.findViewById(R.id.btn_dialog_add_server);

        btnAddServer.setOnClickListener(v -> {
            dialog.dismiss();
            openServerEditor(null);
        });

        itemsContainer.removeAllViews();
        for (SSHConnectionProfile profile : sshProfileList) {
            View itemView = LayoutInflater.from(this).inflate(R.layout.item_server_card, itemsContainer, false);
            ImageView ivRadio = itemView.findViewById(R.id.iv_server_radio);
            ImageView ivFlag = itemView.findViewById(R.id.iv_server_flag);
            TextView tvName = itemView.findViewById(R.id.tv_server_item_name);
            TextView tvDetails = itemView.findViewById(R.id.tv_server_item_details);
            ImageView btnEdit = itemView.findViewById(R.id.btn_server_item_edit);
            ImageView btnDelete = itemView.findViewById(R.id.btn_server_item_delete);

            tvName.setText(profile.getProfileName());
            tvDetails.setText(profile.getServerIP() + ":" + profile.getServerPort() + " (" + profile.getUsername() + ")");

            boolean isSelected = selectedSshProfile != null && profile.uuid.equals(selectedSshProfile.uuid);
            ivRadio.setImageResource(isSelected ? R.drawable.bg_radio_checked : R.drawable.bg_radio_unchecked);

            itemView.setOnClickListener(v -> {
                selectedSshProfile = profile;
                updateHomeDisplays();
                updateSshDisplays();
                dialog.dismiss();
                Toast.makeText(MainActivity.this, "เลือกเซิร์ฟเวอร์: " + profile.getProfileName(), Toast.LENGTH_SHORT).show();
            });

            btnEdit.setOnClickListener(v -> {
                dialog.dismiss();
                openServerEditor(profile);
            });

            btnDelete.setOnClickListener(v -> {
                confirmDeleteServer(profile, dialog);
            });

            itemsContainer.addView(itemView);
        }

        dialog.show();
    }

    private void setupServerEditEvents() {
        rowServerModeDirect.setOnClickListener(v -> {
            serverEditIsDirect = true;
            ivModeDirectRadio.setImageResource(R.drawable.bg_radio_checked);
            ivModePayloadRadio.setImageResource(R.drawable.bg_radio_unchecked);
            spinnerServerPayloadChoice.setVisibility(View.GONE);
        });

        rowServerModePayload.setOnClickListener(v -> {
            serverEditIsDirect = false;
            ivModeDirectRadio.setImageResource(R.drawable.bg_radio_unchecked);
            ivModePayloadRadio.setImageResource(R.drawable.bg_radio_checked);
            spinnerServerPayloadChoice.setVisibility(View.VISIBLE);
        });

        btnServerSave.setOnClickListener(v -> saveServer(false));
        btnServerSaveAsNew.setOnClickListener(v -> saveServer(true));

        btnServerClear.setOnClickListener(v -> {
            etServerName.setText("");
            etServerHost.setText("");
            etServerPort.setText("22");
            etServerUser.setText("");
            etServerPassword.setText("");
        });

        btnServerDelete.setOnClickListener(v -> {
            if (currentEditingServer != null) {
                confirmDeleteServer(currentEditingServer, null);
            }
        });
    }

    private void openServerEditor(SSHConnectionProfile profile) {
        currentEditingServer = profile;
        payloadProfileList = payloadManager.loadProfiles();

        // Setup payload choice options
        List<String> payloadNames = new ArrayList<>();
        int selectedPayloadIndex = 0;
        for (int i = 0; i < payloadProfileList.size(); i++) {
            PayloadProfile p = payloadProfileList.get(i);
            payloadNames.add(p.name + " (" + p.proxyHost + ":" + p.proxyPort + ")");
            if (profile != null && profile.getPayloadUuid() != null && profile.getPayloadUuid().equals(p.uuid)) {
                selectedPayloadIndex = i;
            }
        }
        if (payloadNames.isEmpty()) {
            payloadNames.add("ไม่มี Payload (กรุณาสร้างในแท็บ Payload)");
        }
        android.widget.ArrayAdapter<String> spinnerAdapter = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, payloadNames
        );
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerServerPayloadChoice.setAdapter(spinnerAdapter);
        if (selectedPayloadIndex < payloadNames.size()) {
            spinnerServerPayloadChoice.setSelection(selectedPayloadIndex);
        }

        if (profile != null) {
            etServerName.setText(profile.getProfileName());
            etServerHost.setText(profile.getServerIP());
            etServerPort.setText(String.valueOf(profile.getServerPort()));
            etServerUser.setText(profile.getUsername());
            etServerPassword.setText(profile.getPassword() != null ? profile.getPassword() : "");

            serverEditIsDirect = "DIRECT".equalsIgnoreCase(profile.getConnectionMode());
            ivModeDirectRadio.setImageResource(serverEditIsDirect ? R.drawable.bg_radio_checked : R.drawable.bg_radio_unchecked);
            ivModePayloadRadio.setImageResource(serverEditIsDirect ? R.drawable.bg_radio_unchecked : R.drawable.bg_radio_checked);
            spinnerServerPayloadChoice.setVisibility(serverEditIsDirect ? View.GONE : View.VISIBLE);

            btnServerDelete.setVisibility(View.VISIBLE);
            btnServerSave.setText(R.string.btn_save_changes);
        } else {
            etServerName.setText("");
            etServerHost.setText("");
            etServerPort.setText("22");
            etServerUser.setText("");
            etServerPassword.setText("");

            serverEditIsDirect = true;
            ivModeDirectRadio.setImageResource(R.drawable.bg_radio_checked);
            ivModePayloadRadio.setImageResource(R.drawable.bg_radio_unchecked);
            spinnerServerPayloadChoice.setVisibility(View.GONE);

            btnServerDelete.setVisibility(View.GONE);
            btnServerSave.setText(R.string.btn_save);
        }

        showScreen(7);
    }

    private void saveServer(boolean saveAsNew) {
        String name = etServerName.getText().toString().trim();
        String host = etServerHost.getText().toString().trim();
        String portStr = etServerPort.getText().toString().trim();
        String user = etServerUser.getText().toString().trim();
        String password = etServerPassword.getText().toString().trim();

        if (host.isEmpty()) {
            etServerHost.setError("กรุณากรอก SSH Host / IP");
            etServerHost.requestFocus();
            return;
        }

        if (user.isEmpty()) {
            etServerUser.setError("กรุณากรอก Username");
            etServerUser.requestFocus();
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port < 1 || port > 65535) port = 22;
        } catch (Exception e) {
            port = 22;
        }

        if (name.isEmpty()) {
            name = host;
        }

        String payloadUuid = null;
        if (!serverEditIsDirect && !payloadProfileList.isEmpty()) {
            int selectedIdx = spinnerServerPayloadChoice.getSelectedItemPosition();
            if (selectedIdx >= 0 && selectedIdx < payloadProfileList.size()) {
                payloadUuid = payloadProfileList.get(selectedIdx).uuid;
            }
        }

        SSHConnectionProfile target;
        if (saveAsNew || currentEditingServer == null) {
            target = new SSHConnectionProfile();
            target.uuid = UUID.randomUUID();
            if (saveAsNew && currentEditingServer != null && name.equals(currentEditingServer.getProfileName())) {
                name = name + " (ใหม่)";
            }
            target.setProfileName(name);
            target.setServerIP(host);
            target.setServerPort(port);
            target.setUsername(user);
            target.setPassword(password);
            target.setConnectionMode(serverEditIsDirect ? "DIRECT" : "PAYLOAD");
            target.setPayloadUuid(payloadUuid);
            target.setAuthenticationType(SSHConnectionProfile.AuthenticationType.PASSWORD);

            sshManager.saveProfile(target);
            selectedSshProfile = target;
            Toast.makeText(this, R.string.server_saved_as_new_toast, Toast.LENGTH_SHORT).show();
        } else {
            target = currentEditingServer;
            target.setProfileName(name);
            target.setServerIP(host);
            target.setServerPort(port);
            target.setUsername(user);
            target.setPassword(password);
            target.setConnectionMode(serverEditIsDirect ? "DIRECT" : "PAYLOAD");
            target.setPayloadUuid(payloadUuid);
            target.setAuthenticationType(SSHConnectionProfile.AuthenticationType.PASSWORD);

            sshManager.saveProfile(target);
            selectedSshProfile = target;
            Toast.makeText(this, R.string.server_saved_toast, Toast.LENGTH_SHORT).show();
        }

        sshProfileList = sshManager.loadProfiles();
        updateHomeDisplays();
        updateSshDisplays();
        showScreen(0);
    }

    private void confirmDeleteServer(SSHConnectionProfile profile, BottomSheetDialog parentDialog) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.btn_delete_server)
                .setMessage("ต้องการลบ \"" + profile.getProfileName() + "\" หรือไม่?")
                .setNegativeButton("ยกเลิก", null)
                .setPositiveButton("ลบ", (dialog, which) -> {
                    sshManager.deleteProfile(profile.uuid);
                    sshProfileList = sshManager.loadProfiles();

                    if (selectedSshProfile != null && profile.uuid.equals(selectedSshProfile.uuid)) {
                        selectedSshProfile = !sshProfileList.isEmpty() ? sshProfileList.get(0) : null;
                    }

                    updateHomeDisplays();
                    updateSshDisplays();

                    if (parentDialog != null) {
                        parentDialog.dismiss();
                        showServerPicker();
                    }

                    if (currentTab == 7) {
                        showScreen(0);
                    }

                    Toast.makeText(this, R.string.server_deleted_toast, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showPayloadPicker() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(0xFF0F1B13);
        content.setPadding(dp(20), dp(20), dp(20), dp(24));

        RelativeLayout header = new RelativeLayout(this);
        TextView title = new TextView(this);
        title.setText(R.string.payload_list);
        title.setTextColor(getColor(R.color.white));
        title.setTextSize(18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(title);

        Button btnManage = new Button(this);
        btnManage.setText("จัดการ Payload");
        btnManage.setBackgroundResource(R.drawable.bg_button_connect);
        btnManage.setTextColor(getColor(R.color.white));
        btnManage.setTextSize(13);
        btnManage.setAllCaps(false);
        RelativeLayout.LayoutParams btnLp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        btnLp.addRule(RelativeLayout.ALIGN_PARENT_END);
        btnManage.setLayoutParams(btnLp);
        btnManage.setOnClickListener(v -> {
            dialog.dismiss();
            showScreen(1);
        });
        header.addView(btnManage);
        content.addView(header);

        View gap = new View(this);
        gap.setLayoutParams(new LinearLayout.LayoutParams(1, dp(16)));
        content.addView(gap);

        for (PayloadProfile profile : payloadProfileList) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dp(16), dp(14), dp(16), dp(14));
            row.setBackgroundResource(R.drawable.bg_neon_card_subtle);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(10);
            row.setLayoutParams(lp);

            ImageView cube = new ImageView(this);
            cube.setImageResource(R.drawable.ic_cube_3d);
            cube.setLayoutParams(new LinearLayout.LayoutParams(dp(24), dp(24)));
            row.addView(cube);

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            infoLp.setMarginStart(dp(14));
            info.setLayoutParams(infoLp);

            TextView name = new TextView(this);
            name.setText(profile.name);
            name.setTextColor(getColor(R.color.white));
            name.setTextSize(16);
            name.setTypeface(null, android.graphics.Typeface.BOLD);
            info.addView(name);

            TextView proxy = new TextView(this);
            proxy.setText("Proxy: " + profile.proxyHost + ":" + profile.proxyPort);
            proxy.setTextColor(getColor(R.color.text_muted));
            proxy.setTextSize(13);
            info.addView(proxy);

            row.addView(info);

            ImageView radio = new ImageView(this);
            boolean isSelected = selectedPayloadProfile != null && profile.uuid.equals(selectedPayloadProfile.uuid);
            radio.setImageResource(isSelected ? R.drawable.bg_radio_checked : R.drawable.bg_radio_unchecked);
            radio.setLayoutParams(new LinearLayout.LayoutParams(dp(24), dp(24)));
            row.addView(radio);

            row.setOnClickListener(v -> {
                selectedPayloadProfile = profile;
                if (selectedSshProfile != null) {
                    selectedSshProfile.setPayloadUuid(profile.uuid);
                    sshManager.saveProfile(selectedSshProfile);
                }
                updateHomeDisplays();
                updateSshDisplays();
                dialog.dismiss();
            });

            content.addView(row);
        }

        dialog.setContentView(content);
        dialog.show();
    }

    private void populatePayloadList() {
        layoutPayloadItemsContainer.removeAllViews();
        payloadProfileList = payloadManager.loadProfiles();

        for (PayloadProfile profile : payloadProfileList) {
            View itemView = LayoutInflater.from(this).inflate(R.layout.item_payload_card, layoutPayloadItemsContainer, false);
            ImageView ivRadio = itemView.findViewById(R.id.iv_payload_radio);
            TextView tvName = itemView.findViewById(R.id.tv_payload_item_name);
            TextView tvProxy = itemView.findViewById(R.id.tv_payload_item_proxy);
            ImageView btnEdit = itemView.findViewById(R.id.btn_payload_item_edit);
            ImageView btnDelete = itemView.findViewById(R.id.btn_payload_item_delete);

            tvName.setText(profile.name);
            tvProxy.setText("Proxy: " + profile.proxyHost + ":" + profile.proxyPort);

            boolean isSelected = selectedPayloadProfile != null && profile.uuid.equals(selectedPayloadProfile.uuid);
            ivRadio.setImageResource(isSelected ? R.drawable.bg_radio_checked : R.drawable.bg_radio_unchecked);

            itemView.setOnClickListener(v -> {
                selectedPayloadProfile = profile;
                if (selectedSshProfile != null) {
                    selectedSshProfile.setPayloadUuid(profile.uuid);
                    sshManager.saveProfile(selectedSshProfile);
                }
                updateHomeDisplays();
                populatePayloadList();
            });

            btnEdit.setOnClickListener(v -> openPayloadEditor(profile));

            btnDelete.setOnClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle("ลบ Payload")
                        .setMessage("ต้องการลบ \"" + profile.name + "\" หรือไม่?")
                        .setNegativeButton("ยกเลิก", null)
                        .setPositiveButton("ลบ", (dialog, which) -> {
                            payloadManager.deleteProfile(profile.uuid);
                            if (selectedPayloadProfile != null && profile.uuid.equals(selectedPayloadProfile.uuid)) {
                                selectedPayloadProfile = null;
                            }
                            populatePayloadList();
                            updateHomeDisplays();
                        })
                        .show();
            });

            layoutPayloadItemsContainer.addView(itemView);
        }
    }

    private void openPayloadEditor(PayloadProfile profile) {
        currentEditingProfile = profile;
        if (profile != null) {
            etPayloadName.setText(profile.name);
            etPayloadDesc.setText(profile.description);
            etPayloadBody.setText(profile.payload);
            etProxyHost.setText(profile.proxyHost);
            etProxyPort.setText(String.valueOf(profile.proxyPort));
        } else {
            etPayloadName.setText("");
            etPayloadDesc.setText("");
            etPayloadBody.setText("CONNECT [host_port] [protocol][crlf]Host: [host][crlf]X-Online-Host: [host][crlf]User-Agent: [ua][crlf][keep][crlf][crlf]");
            etProxyHost.setText("");
            etProxyPort.setText("8080");
        }
        showScreen(4);
    }

    private void setupPayloadEditEvents() {
        btnPayloadClear.setOnClickListener(v -> {
            etPayloadName.setText("");
            etPayloadDesc.setText("");
            etPayloadBody.setText("");
            etProxyHost.setText("");
            etProxyPort.setText("");
        });

        btnPayloadSave.setOnClickListener(v -> saveCurrentPayload());

        initPayloadTagChips();

        if (btnPayloadPresets != null) {
            btnPayloadPresets.setOnClickListener(v -> showPayloadPresetsDialog());
        }
    }

    private static final String[] HTTP_CUSTOM_TAGS = new String[] {
            "[host_port]", "[host]", "[port]", "[crlf]", "[netData]",
            "[split]", "[delay_split]", "[instant_split]", "[reverse_split]",
            "[ua]", "[protocol]", "[keep]", "[close]", "[raw]", "[real_raw]",
            "[ssh]", "[ip]", "[ip_port]", "[proxy]", "[proxy_host]", "[proxy_port]", "[auth]"
    };

    private void initPayloadTagChips() {
        if (layoutPayloadTagChips == null) return;
        layoutPayloadTagChips.removeAllViews();

        int padH = (int) (10 * getResources().getDisplayMetrics().density + 0.5f);
        int padV = (int) (6 * getResources().getDisplayMetrics().density + 0.5f);
        int marginR = (int) (8 * getResources().getDisplayMetrics().density + 0.5f);

        for (String tag : HTTP_CUSTOM_TAGS) {
            TextView chip = new TextView(this);
            chip.setText(tag);
            chip.setTextColor(android.graphics.Color.parseColor("#81C784"));
            chip.setTextSize(12);
            chip.setTypeface(null, android.graphics.Typeface.BOLD);
            chip.setBackgroundResource(R.drawable.bg_payload_tag_chip);
            chip.setPadding(padH, padV, padH, padV);
            chip.setClickable(true);
            chip.setFocusable(true);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(0, 0, marginR, 0);
            chip.setLayoutParams(lp);

            chip.setOnClickListener(v -> insertTagIntoPayload(tag));
            layoutPayloadTagChips.addView(chip);
        }
    }

    private void insertTagIntoPayload(String tag) {
        if (etPayloadBody == null) return;
        int start = Math.max(etPayloadBody.getSelectionStart(), 0);
        int end = Math.max(etPayloadBody.getSelectionEnd(), 0);
        int min = Math.min(start, end);
        int max = Math.max(start, end);
        etPayloadBody.getText().replace(min, max, tag, 0, tag.length());
        etPayloadBody.setSelection(min + tag.length());
        etPayloadBody.requestFocus();
    }

    private void showPayloadPresetsDialog() {
        String[] presetTitles = new String[] {
                "HTTP Custom CONNECT (Standard)",
                "HTTP Custom GET [split] CONNECT",
                "HTTP Custom [delay_split] 1s",
                "HTTP Custom [instant_split]",
                "HTTP Custom [reverse_split]",
                "HTTP Custom Cloudflare / CDN (WebSocket)"
        };

        String[] presetPayloads = new String[] {
                "CONNECT [host_port] [protocol][crlf]Host: [host][crlf]X-Online-Host: [host][crlf]User-Agent: [ua][crlf][keep][crlf][crlf]",
                "GET / [protocol][crlf]Host: [host][crlf][split]CONNECT [host_port] [protocol][crlf]Host: [host_port][crlf][crlf]",
                "[raw][crlf]Host: [host][crlf][delay_split]Connection: Keep-Alive[crlf][crlf]",
                "CONNECT [host_port] [protocol][crlf]Host: [host][crlf][instant_split]User-Agent: [ua][crlf][keep][crlf][crlf]",
                "CONNECT [host_port] [protocol][crlf]Host: [host_port][crlf][reverse_split]GET / HTTP/1.1[crlf]Host: [host][crlf][crlf]",
                "GET-CDN wss://[host] [protocol][crlf]Host: [host][crlf]Upgrade: websocket[crlf]Connection: Upgrade[crlf][crlf]"
        };

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("เลือกแม่แบบ HTTP Custom")
                .setItems(presetTitles, (dialog, which) -> {
                    if (which >= 0 && which < presetPayloads.length) {
                        etPayloadBody.setText(presetPayloads[which]);
                        Toast.makeText(this, "นำเข้าแม่แบบ " + presetTitles[which] + " แล้ว", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("ปิด", null)
                .show();
    }

    private void saveCurrentPayload() {
        String name = etPayloadName.getText().toString().trim();
        String desc = etPayloadDesc.getText().toString().trim();
        String body = etPayloadBody.getText().toString().trim();
        String host = etProxyHost.getText().toString().trim();
        String portStr = etProxyPort.getText().toString().trim();

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (Exception e) {
            port = -1;
        }

        if (name.isEmpty() || body.isEmpty() || host.isEmpty() || port < 1 || port > 65535) {
            Toast.makeText(this, "กรุณากรอกชื่อ Payload, Body, Proxy Host และ Port ให้ถูกต้อง", Toast.LENGTH_LONG).show();
            return;
        }

        PayloadProfile profile = currentEditingProfile != null ? currentEditingProfile : new PayloadProfile();
        profile.name = name;
        profile.description = desc;
        profile.payload = body;
        profile.proxyHost = host;
        profile.proxyPort = port;

        if (payloadManager.saveProfile(profile)) {
            selectedPayloadProfile = profile;
            Toast.makeText(this, "บันทึก Payload สำเร็จแล้ว", Toast.LENGTH_SHORT).show();
            showScreen(1); // Return to Payload list
        } else {
            Toast.makeText(this, "บันทึกไม่สำเร็จ", Toast.LENGTH_LONG).show();
        }
    }

    private void setupConnectingEvents() {
        btnConnectingCancel.setOnClickListener(v -> {
            disconnectVpn();
            showScreen(0);
        });
        if (btnConnectingViewLog != null) {
            btnConnectingViewLog.setOnClickListener(v -> showScreen(6));
        }
    }

    private void startConnectSequence() {
        if (selectedSshProfile == null) {
            Toast.makeText(this, "กรุณาเลือกเซิร์ฟเวอร์ SSH", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, NewConnectionActivity.class));
            return;
        }

        // Prepare VPN
        Intent intentPrepare = VpnService.prepare(this);
        if (intentPrepare != null) {
            startActivityForResult(intentPrepare, 0);
            return;
        }

        // Show Screen 5 Connecting UI
        showScreen(5);

        // Reset steps
        ivStepProxyStatus.setImageResource(R.drawable.bg_radio_unchecked);
        ivStepProxyStatus.setColorFilter(getColor(R.color.text_muted));
        tvStepProxyText.setTextColor(getColor(R.color.text_muted));

        ivStepSshStatus.setImageResource(R.drawable.bg_radio_unchecked);
        ivStepSshStatus.setColorFilter(getColor(R.color.text_muted));
        tvStepSshText.setTextColor(getColor(R.color.text_muted));

        ivStepRoutingStatus.setImageResource(R.drawable.bg_radio_unchecked);
        ivStepRoutingStatus.setColorFilter(getColor(R.color.text_muted));
        tvStepRoutingText.setTextColor(getColor(R.color.text_muted));

        // Animated progression sequence
        handler.postDelayed(() -> {
            ivStepProxyStatus.setImageResource(R.drawable.check_circle_fill0_wght400_grad0_opsz24);
            ivStepProxyStatus.setColorFilter(getColor(R.color.neon_green));
            tvStepProxyText.setTextColor(getColor(R.color.white));
        }, 600);

        handler.postDelayed(() -> {
            ivStepSshStatus.setImageResource(R.drawable.check_circle_fill0_wght400_grad0_opsz24);
            ivStepSshStatus.setColorFilter(getColor(R.color.neon_green));
            tvStepSshText.setTextColor(getColor(R.color.white));
        }, 1300);

        handler.postDelayed(() -> {
            ivStepRoutingStatus.setImageResource(R.drawable.check_circle_fill0_wght400_grad0_opsz24);
            ivStepRoutingStatus.setColorFilter(getColor(R.color.neon_green));
            tvStepRoutingText.setTextColor(getColor(R.color.white));

            // Launch backend services
            launchBackendVpn();

            // After completion, transition back to Home in connected state
            handler.postDelayed(() -> {
                showScreen(0);
                Toast.makeText(MainActivity.this, "เชื่อมต่อ SSH สำเร็จแล้ว!", Toast.LENGTH_SHORT).show();
            }, 600);
        }, 2000);
    }

    private void launchBackendVpn() {
        try {
            StatusInfo.getInstance().setActive(true);
            sshIntent.putExtra("user", selectedSshProfile.getUsername());
            sshIntent.putExtra("password", selectedSshProfile.getPassword());
            if (selectedSshProfile.getPrivateKey() != null && !selectedSshProfile.getPrivateKey().isEmpty()) {
                sshIntent.putExtra("privateKey", selectedSshProfile.getPrivateKey());
            }
            sshIntent.putExtra("connectionMode", isDirectSsh ? "DIRECT" : "PAYLOAD");
            sshIntent.putExtra("payloadUuid", selectedPayloadProfile != null ? selectedPayloadProfile.uuid : null);
            sshIntent.putExtra("hostname", selectedSshProfile.getServerIP());
            sshIntent.putExtra("port", String.valueOf(selectedSshProfile.getServerPort()));
            startService(sshIntent);

            vpnIntent.putExtra("socksPort", 1080);
            startService(vpnIntent);

            updateHomeDisplays();
        } catch (Exception e) {
            Log.e(TAG, "Error starting VPN: ", e);
            StatusInfo.getInstance().setActive(false);
            updateHomeDisplays();
        }
    }

    private void disconnectVpn() {
        StatusInfo.getInstance().setActive(false);
        Thread vpnThread = SocksPersistent.getInstance().getVpnThread();
        if (vpnThread != null) {
            vpnThread.interrupt();
        }
        stopService(sshIntent);
        updateHomeDisplays();
        Toast.makeText(this, "ยกเลิกการเชื่อมต่อแล้ว", Toast.LENGTH_SHORT).show();
    }

    private void setupSettingsEvents() {
        btnSettingsApps.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
        });

        btnSettingsSave.setOnClickListener(v -> {
            String dns = etSettingsDns.getText().toString().trim();
            String port = etSettingsForwarderPort.getText().toString().trim();

            sharedPreferences.edit()
                    .putString("dns_resolver_ip", dns.isEmpty() ? "1.1.1.1" : dns)
                    .putString("forwarder_port", port.isEmpty() ? "1080" : port)
                    .apply();

            Toast.makeText(this, "บันทึกการตั้งค่าแล้ว", Toast.LENGTH_SHORT).show();
            showScreen(0);
        });
    }

    private void loadSettingsValues() {
        etSettingsDns.setText(sharedPreferences.getString("dns_resolver_ip", "1.1.1.1"));
        etSettingsForwarderPort.setText(sharedPreferences.getString("forwarder_port", "1080"));
    }

    private void setupDrawerEvents() {
        findViewById(R.id.drawer_item_home).setOnClickListener(v -> {
            drawerLayout.closeDrawer(GravityCompat.END);
            showScreen(0);
        });

        findViewById(R.id.drawer_item_servers).setOnClickListener(v -> {
            drawerLayout.closeDrawer(GravityCompat.END);
            showServerPicker();
        });

        findViewById(R.id.drawer_item_payload).setOnClickListener(v -> {
            drawerLayout.closeDrawer(GravityCompat.END);
            showScreen(1);
        });

        findViewById(R.id.drawer_item_settings).setOnClickListener(v -> {
            drawerLayout.closeDrawer(GravityCompat.END);
            showScreen(2);
        });

        findViewById(R.id.drawer_item_logs).setOnClickListener(v -> {
            drawerLayout.closeDrawer(GravityCompat.END);
            showScreen(6);
        });

        findViewById(R.id.drawer_item_guide).setOnClickListener(v -> {
            drawerLayout.closeDrawer(GravityCompat.END);
            showUserGuideDialog();
        });

        findViewById(R.id.drawer_item_about).setOnClickListener(v -> {
            drawerLayout.closeDrawer(GravityCompat.END);
            showAboutDialog();
        });

        findViewById(R.id.drawer_item_exit).setOnClickListener(v -> {
            drawerLayout.closeDrawer(GravityCompat.END);
            if (StatusInfo.getInstance().isActive()) {
                disconnectVpn();
            }
            finishAffinity();
        });
    }

    private void setupLogEvents() {
        btnLogCopy.setOnClickListener(v -> {
            String all = ru.anton2319.vpnoverssh.utils.ConnectionLogger.getInstance().getAllLogsAsText();
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("godarmxvpn Logs", all);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, R.string.log_copied_toast, Toast.LENGTH_SHORT).show();
            }
        });

        btnLogClear.setOnClickListener(v -> {
            ru.anton2319.vpnoverssh.utils.ConnectionLogger.getInstance().clear();
        });

        ru.anton2319.vpnoverssh.utils.ConnectionLogger.getInstance().addListener(new ru.anton2319.vpnoverssh.utils.ConnectionLogger.LogListener() {
            @Override
            public void onNewLog(ru.anton2319.vpnoverssh.utils.ConnectionLogger.LogEntry entry) {
                if (viewScreenLogs != null && viewScreenLogs.getVisibility() == View.VISIBLE) {
                    appendLogEntry(entry);
                }
            }

            @Override
            public void onLogsCleared() {
                if (viewScreenLogs != null && viewScreenLogs.getVisibility() == View.VISIBLE) {
                    renderAllLogs();
                }
            }
        });
    }

    private void renderAllLogs() {
        List<ru.anton2319.vpnoverssh.utils.ConnectionLogger.LogEntry> list = ru.anton2319.vpnoverssh.utils.ConnectionLogger.getInstance().getLogs();
        tvLogsCountBadge.setText(list.size() + " รายการ");
        if (list.isEmpty()) {
            tvLogsTerminalContent.setText(R.string.log_empty);
            return;
        }

        android.text.SpannableStringBuilder ssb = new android.text.SpannableStringBuilder();
        for (ru.anton2319.vpnoverssh.utils.ConnectionLogger.LogEntry e : list) {
            formatLogEntry(ssb, e);
        }
        tvLogsTerminalContent.setText(ssb);
        scrollLogsConsole.post(() -> scrollLogsConsole.fullScroll(View.FOCUS_DOWN));
    }

    private void appendLogEntry(ru.anton2319.vpnoverssh.utils.ConnectionLogger.LogEntry entry) {
        CharSequence current = tvLogsTerminalContent.getText();
        android.text.SpannableStringBuilder ssb;
        if (current.toString().equals(getString(R.string.log_empty))) {
            ssb = new android.text.SpannableStringBuilder();
        } else {
            ssb = new android.text.SpannableStringBuilder(current);
        }
        formatLogEntry(ssb, entry);
        tvLogsTerminalContent.setText(ssb);
        tvLogsCountBadge.setText(ru.anton2319.vpnoverssh.utils.ConnectionLogger.getInstance().getLogs().size() + " รายการ");
        scrollLogsConsole.post(() -> scrollLogsConsole.fullScroll(View.FOCUS_DOWN));
    }

    private void formatLogEntry(android.text.SpannableStringBuilder ssb, ru.anton2319.vpnoverssh.utils.ConnectionLogger.LogEntry entry) {
        int start = ssb.length();
        ssb.append("[").append(entry.timeString).append("] ");
        ssb.setSpan(new android.text.style.ForegroundColorSpan(getColor(R.color.text_muted)), start, ssb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        int tagStart = ssb.length();
        ssb.append("[").append(entry.level.name()).append("] ");
        int tagColor;
        switch (entry.level) {
            case SUCCESS:
                tagColor = getColor(R.color.neon_green);
                break;
            case WARN:
                tagColor = getColor(R.color.logo_gold);
                break;
            case ERROR:
                tagColor = 0xFFFF5252;
                break;
            case INFO:
            default:
                tagColor = 0xFF38BDF8;
                break;
        }
        ssb.setSpan(new android.text.style.ForegroundColorSpan(tagColor), tagStart, ssb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), tagStart, ssb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        int msgStart = ssb.length();
        ssb.append(entry.message).append("\n");
        int msgColor = (entry.level == ru.anton2319.vpnoverssh.utils.ConnectionLogger.Level.ERROR) ? 0xFFFF7070 : getColor(R.color.white);
        ssb.setSpan(new android.text.style.ForegroundColorSpan(msgColor), msgStart, ssb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void showUserGuideDialog() {
        new AlertDialog.Builder(this)
                .setTitle("คู่มือการใช้งาน godarmxvpn")
                .setMessage("1. เซิร์ฟเวอร์ SSH:\n- เลือกเซิร์ฟเวอร์ที่ต้องการเชื่อมต่อ หรือกด + เพื่อเพิ่มโฮสต์และพอร์ต SSH\n\n"
                        + "2. HTTP Custom Payload Injection:\n- รองรับตัวแปร HTTP Custom ครบถ้วน: [host_port], [host], [port], [ip], [ip_port], [ssh], [real_host], [real_port], [proxy], [proxy_host], [proxy_port], [protocol], [netData], [raw], [real_raw], [ua], [keep], [close], [auth], [rotate=...], [crlf], [cr], [lf], [lfcr], [tab]\n"
                        + "- รองรับโหมด Split Packet: [split], [delay_split], [instant_split], [reverse_split]\n"
                        + "- รองรับทุก HTTP Method (CONNECT, GET, POST, HEAD, Upgrade ฯลฯ)\n"
                        + "- มีปุ่มแตะเพื่อแทรกตัวแปรและปุ่มเลือกแม่แบบ (Presets) ในหน้าแก้ไข Payload\n\n"
                        + "3. Direct SSH:\n- หากไม่ต้องการใช้งาน Payload Proxy ให้เลือก Direct SSH\n\n"
                        + "4. การข้ามแอป:\n- เข้าไปที่ ตั้งค่า > Included Apps เพื่อเลือกแอปที่ต้องการให้ผ่าน VPN\n\n"
                        + "5. ตรวจสอบปัญหาการเชื่อมต่อ (Log):\n- กดปุ่มไอคอน Terminal บนมุมขวาบน หรือกดเมนู 'บันทึกการทำงาน' เพื่อดูสถานะการเชื่อมต่อและข้อผิดพลาดแบบสดๆ")
                .setPositiveButton("เข้าใจแล้ว", null)
                .show();
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("เกี่ยวกับ godarmxvpn")
                .setMessage("godarmxvpn v1.0.0\n\n"
                        + "แอปพลิเคชัน VPN over SSH Tunnel & HTTP Proxy Payload Client ที่มีความเร็วสูงและเสถียร\n\n"
                        + "• รองรับ HTTP Custom Payload Injection เต็มรูปแบบ (All Variables, Methods & Split Rules)\n"
                        + "• รองรับ SSH Password & Private Key Authentication\n"
                        + "• รองรับ VpnService พร้อม Tun2Socks In-Memory Routing\n"
                        + "• มี Live Terminal Logging Console สำหรับตรวจสอบสถานะและหาสาเหตุข้อผิดพลาด\n"
                        + "• ดีไซน์ Cyber Neon ล่าสุด")
                .setPositiveButton("ปิด", null)
                .show();
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
            drawerLayout.closeDrawer(GravityCompat.END);
        } else if (viewScreenPayloadEdit.getVisibility() == View.VISIBLE) {
            showScreen(1);
        } else if (viewScreenLogs.getVisibility() == View.VISIBLE) {
            showScreen(0);
        } else if (viewScreenSsh.getVisibility() == View.VISIBLE || viewScreenConnecting.getVisibility() == View.VISIBLE
                || viewScreenPayloadList.getVisibility() == View.VISIBLE || viewScreenSettings.getVisibility() == View.VISIBLE) {
            showScreen(0);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}