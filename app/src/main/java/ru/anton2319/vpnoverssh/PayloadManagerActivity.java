package ru.anton2319.vpnoverssh;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

import ru.anton2319.vpnoverssh.data.PayloadProfile;
import ru.anton2319.vpnoverssh.data.utils.PayloadProfileManager;

/** Payload-only editor. It intentionally exposes only fields supported by the SSH HTTP CONNECT transport. */
public class PayloadManagerActivity extends AppCompatActivity {
    private static final int BG = Color.rgb(29, 32, 37);
    private static final int PANEL = Color.rgb(39, 42, 47);
    private static final int GREEN = Color.rgb(67, 181, 85);
    private static final int MUTED = Color.rgb(177, 184, 193);

    private PayloadProfileManager manager;
    private LinearLayout savedList;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        manager = new PayloadProfileManager(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        TextView toolbar = new TextView(this);
        toolbar.setText("‹    Payload");
        toolbar.setTextColor(Color.WHITE);
        toolbar.setTextSize(21);
        toolbar.setTypeface(null, Typeface.BOLD);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(20), 0, dp(16), 0);
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(58)));
        toolbar.setOnClickListener(v -> finish());

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(12), dp(16), dp(24));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout editor = panel();
        editor.setPadding(dp(18), dp(18), dp(18), dp(18));
        content.addView(editor, matchWrap());

        TextView addIcon = new TextView(this);
        addIcon.setText("⊕");
        addIcon.setTextColor(GREEN);
        addIcon.setTextSize(42);
        addIcon.setGravity(Gravity.CENTER);
        editor.addView(addIcon, matchWrap());

        TextView heading = label("เพิ่มเพย์โหลด", 19, Color.WHITE, true);
        heading.setGravity(Gravity.CENTER);
        editor.addView(heading, matchWrap());

        EditText name = field("ชื่อเพย์โหลด", InputType.TYPE_CLASS_TEXT);
        EditText description = field("รายละเอียดเพย์โหลด", InputType.TYPE_CLASS_TEXT);
        EditText payload = field("GET / HTTP/1.1[crlf]Host: [host_port][crlf]...", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        payload.setMinLines(4);
        payload.setGravity(Gravity.TOP | Gravity.START);
        EditText proxyHost = field("ที่อยู่พร็อกซี (host หรือ [host])", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        EditText proxyPort = field("พอร์ต (เช่น 80 หรือ 8080)", InputType.TYPE_CLASS_NUMBER);

        editor.addView(gap(18));
        editor.addView(name, matchWrap());
        editor.addView(gap(10));
        editor.addView(description, matchWrap());
        editor.addView(gap(14));
        editor.addView(gap(14));
        editor.addView(label("เพย์โหลด (HTTP Custom Payload)", 15, Color.WHITE, true), matchWrap());
        editor.addView(gap(8));
        editor.addView(payload, matchWrap());

        HorizontalScrollView tagScroll = new HorizontalScrollView(this);
        tagScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tagContainer = new LinearLayout(this);
        tagContainer.setOrientation(LinearLayout.HORIZONTAL);
        String[] tags = new String[] {
                "[host_port]", "[host]", "[port]", "[crlf]", "[netData]",
                "[split]", "[delay_split]", "[instant_split]", "[reverse_split]",
                "[ua]", "[protocol]", "[keep]", "[close]", "[raw]", "[real_raw]",
                "[ssh]", "[ip]", "[ip_port]", "[proxy]", "[proxy_host]", "[proxy_port]", "[auth]"
        };
        for (String tag : tags) {
            TextView chip = new TextView(this);
            chip.setText(tag);
            chip.setTextColor(Color.rgb(129, 199, 132));
            chip.setTextSize(12);
            chip.setTypeface(null, Typeface.BOLD);
            GradientDrawable chipBg = new GradientDrawable();
            chipBg.setColor(Color.rgb(36, 43, 53));
            chipBg.setStroke(dp(1), Color.rgb(61, 138, 82));
            chipBg.setCornerRadius(dp(8));
            chip.setBackground(chipBg);
            chip.setPadding(dp(10), dp(6), dp(10), dp(6));
            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(-2, -2);
            chipLp.setMargins(0, 0, dp(6), 0);
            chip.setLayoutParams(chipLp);
            chip.setOnClickListener(v -> {
                int start = Math.max(payload.getSelectionStart(), 0);
                int end = Math.max(payload.getSelectionEnd(), 0);
                int min = Math.min(start, end);
                int max = Math.max(start, end);
                payload.getText().replace(min, max, tag, 0, tag.length());
                payload.setSelection(min + tag.length());
                payload.requestFocus();
            });
            tagContainer.addView(chip);
        }
        tagScroll.addView(tagContainer);
        editor.addView(gap(8));
        editor.addView(tagScroll, matchWrap());

        editor.addView(gap(14));
        editor.addView(label("Remote proxy", 15, Color.WHITE, true), matchWrap());
        editor.addView(gap(8));
        editor.addView(proxyHost, matchWrap());
        editor.addView(gap(8));
        editor.addView(proxyPort, matchWrap());

        TextView note = label("รองรับตัวแปร HTTP Custom เต็มรูปแบบ ([host_port], [host], [port], [netData], [raw], [ua], [keep], [split], [delay_split], [crlf] ฯลฯ) และรองรับทุก HTTP Method", 12, MUTED, false);
        note.setPadding(0, dp(10), 0, dp(8));
        editor.addView(note, matchWrap());

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button clear = actionButton("ล้าง", false);
        Button save = actionButton("บันทึก", true);
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, dp(52), 1);
        half.setMargins(dp(4), dp(8), dp(4), 0);
        actions.addView(clear, half);
        actions.addView(save, half);
        editor.addView(actions, matchWrap());

        TextView savedHeading = label("เพย์โหลดที่บันทึก", 17, Color.WHITE, true);
        savedHeading.setPadding(dp(4), dp(20), 0, dp(8));
        content.addView(savedHeading, matchWrap());
        savedList = new LinearLayout(this);
        savedList.setOrientation(LinearLayout.VERTICAL);
        content.addView(savedList, matchWrap());

        clear.setOnClickListener(v -> {
            name.setText("");
            description.setText("");
            payload.setText("");
            proxyHost.setText("");
            proxyPort.setText("");
        });
        save.setOnClickListener(v -> savePayload(name, description, payload, proxyHost, proxyPort));

        setContentView(root);
        refresh();
    }

    private void savePayload(EditText name, EditText description, EditText payload, EditText host, EditText port) {
        String n = name.getText().toString().trim();
        String h = host.getText().toString().trim();
        String ps = port.getText().toString().trim();
        String body = payload.getText().toString();
        int p;
        try { p = Integer.parseInt(ps); } catch (Exception e) { p = -1; }
        if (n.isEmpty() || h.isEmpty() || p < 1 || p > 65535 || body.trim().isEmpty()) {
            Toast.makeText(this, "กรุณากรอกชื่อ Payload, Payload, Proxy Host และ Port ให้ถูกต้อง", Toast.LENGTH_LONG).show();
            return;
        }
        PayloadProfile profile = new PayloadProfile();
        profile.name = n;
        profile.description = description.getText().toString().trim();
        profile.proxyHost = h;
        profile.proxyPort = p;
        profile.payload = body;
        if (manager.saveProfile(profile)) {
            Toast.makeText(this, "บันทึก Payload แล้ว", Toast.LENGTH_SHORT).show();
            name.setText("");
            description.setText("");
            payload.setText("");
            host.setText("");
            port.setText("");
            refresh();
        } else {
            Toast.makeText(this, "บันทึกไม่สำเร็จ", Toast.LENGTH_LONG).show();
        }
    }

    private void refresh() {
        savedList.removeAllViews();
        List<PayloadProfile> profiles = manager.loadProfiles();
        for (PayloadProfile profile : profiles) {
            LinearLayout row = panel();
            row.setPadding(dp(14), dp(12), dp(10), dp(12));
            TextView info = label(profile.name + "\\n" + profile.proxyHost + ":" + profile.proxyPort, 15, Color.WHITE, true);
            row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
            Button edit = smallButton("แก้ไข");
            Button delete = smallButton("ลบ");
            row.addView(edit);
            row.addView(delete);
            edit.setOnClickListener(v -> editExisting(profile));
            delete.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setMessage("ลบ Payload นี้หรือไม่?")
                    .setNegativeButton("ยกเลิก", null)
                    .setPositiveButton("ลบ", (d, w) -> { manager.deleteProfile(profile.uuid); refresh(); })
                    .show());
            LinearLayout.LayoutParams lp = matchWrap();
            lp.bottomMargin = dp(8);
            savedList.addView(row, lp);
        }
        if (profiles.isEmpty()) {
            TextView empty = label("ยังไม่มี Payload ที่บันทึก", 14, MUTED, false);
            empty.setPadding(dp(8), dp(12), dp(8), dp(12));
            savedList.addView(empty, matchWrap());
        }
    }

    private void editExisting(PayloadProfile profile) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        EditText n = field("ชื่อเพย์โหลด", InputType.TYPE_CLASS_TEXT); n.setText(profile.name);
        EditText h = field("Proxy host", InputType.TYPE_TEXT_VARIATION_URI); h.setText(profile.proxyHost);
        EditText p = field("Proxy port", InputType.TYPE_CLASS_NUMBER); p.setText(String.valueOf(profile.proxyPort));
        EditText body = field("Payload", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE); body.setMinLines(4); body.setText(profile.payload);
        form.addView(n); form.addView(h); form.addView(p); form.addView(body);
        new AlertDialog.Builder(this).setTitle("แก้ไข Payload").setView(form)
                .setNegativeButton("ยกเลิก", null)
                .setPositiveButton("บันทึก", (d, w) -> {
                    int portValue;
                    try { portValue = Integer.parseInt(p.getText().toString().trim()); } catch (Exception e) { portValue = -1; }
                    if (n.getText().toString().trim().isEmpty() || h.getText().toString().trim().isEmpty()
                            || portValue < 1 || portValue > 65535 || body.getText().toString().trim().isEmpty()) {
                        Toast.makeText(this, "ข้อมูลไม่ครบหรือ Port ไม่ถูกต้อง", Toast.LENGTH_LONG).show();
                        return;
                    }
                    profile.name = n.getText().toString().trim();
                    profile.proxyHost = h.getText().toString().trim();
                    profile.proxyPort = portValue;
                    profile.payload = body.getText().toString();
                    manager.saveProfile(profile);
                    refresh();
                }).show();
    }

    private LinearLayout panel() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(PANEL); bg.setCornerRadius(dp(18)); bg.setStroke(dp(1), Color.rgb(85, 91, 99));
        v.setBackground(bg);
        return v;
    }

    private EditText field(String hint, int type) {
        EditText e = new EditText(this);
        e.setSingleLine(false);
        e.setHint(hint);
        e.setHintTextColor(MUTED);
        e.setTextColor(Color.WHITE);
        e.setTextSize(15);
        e.setInputType(type);
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(BG); bg.setCornerRadius(dp(12)); bg.setStroke(dp(1), Color.rgb(100, 107, 116));
        e.setBackground(bg);
        return e;
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private Button actionButton(String text, boolean primary) {
        Button b = new Button(this);
        b.setText(text); b.setAllCaps(false); b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primary ? GREEN : Color.DKGRAY));
        return b;
    }

    private Button smallButton(String text) {
        Button b = new Button(this);
        b.setText(text); b.setAllCaps(false); b.setTextSize(12);
        return b;
    }

    private View gap(int dp) { View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(dp))); return v; }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT); }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
}
