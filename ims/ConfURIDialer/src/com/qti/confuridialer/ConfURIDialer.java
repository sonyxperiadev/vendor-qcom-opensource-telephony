/*
 * Copyright (c) 2016-2017, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *   * Neither the name of The Linux Foundation nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.qti.confuridialer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.telecom.PhoneAccount;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telephony.PhoneNumberUtils;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.inputmethod.InputMethodManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Toast;
import android.util.Log;

/**
 * This class gives a Conference URI dialer UI and provide
 * user to dial Conference URI Call, dial Empty URI List, add
 * participant etc.
 */
public class ConfURIDialer extends Activity {

    private EditText mEditText;
    private Button mStartCallButton;
    private Button mVideoCallButton;
    private ImageButton mContactButton;
    private ImageButton mCallLogButton;
    private Button mCancelButton;
    private Cursor mCursor;
    private ListView mListView;
    private String mEditNumber;
    private static final int ACTIVITY_REQUEST_CONTACT_PICK = 100;
    private static final String INTENT_PICK_ACTION = "android.intent.action.PICK";
    public static final String INTENT_ACTION_DISMISS_CONF_URI_DIALER =
            "org.codeaurora.confuridialer.ACTION_DISMISS_CONF_URI_DIALER";
    private static final String TAG = "ConfURIDialer";
    public static final String EXTRA_DIAL_CONFERENCE_URI =
            "org.codeaurora.extra.DIAL_CONFERENCE_URI";
    public static final String ADD_PARTICIPANT_KEY = "add_participant";
    private static final String KEY_IS_CALL_LOG_PICKER_SHOWN = "is_call_log_picker_shown";
    private static final String KEY_EXISTING_EDIT_TEXT = "existing_edit_text";
    private boolean mIsAddParticipants = false;
    private boolean mIsInCall;
    private ConfURIDialerPhoneStateListener mPhoneStateListener;
    private Context mContext;
    private AlertDialog mAlertDialog = null;

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null &&
                    intent.getAction().equals(INTENT_ACTION_DISMISS_CONF_URI_DIALER)) {
                finishActivity(ACTIVITY_REQUEST_CONTACT_PICK);
                finish();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.conf_uri_dialer);
        mContext = this;
        mEditText = (EditText) findViewById(R.id.edit_number);
        mContactButton = (ImageButton) findViewById(R.id.contactlist);
        mCallLogButton = (ImageButton) findViewById(R.id.btn_pick_callLog);
        mStartCallButton = (Button) findViewById(R.id.btn_start_call);
        mVideoCallButton = (Button) findViewById(R.id.btn_video_call);
        mCancelButton = (Button) findViewById(R.id.btn_cancel);
        mIsAddParticipants = getIntent().getBooleanExtra(ADD_PARTICIPANT_KEY, false);

        mPhoneStateListener = new ConfURIDialerPhoneStateListener(this);
        registerCallStateListener();
        mIsInCall = isInCall(mContext);
        if (mIsAddParticipants && mIsInCall) {
            mStartCallButton.setText(R.string.button_add_participant);
            mEditText.setHint(R.string.add_recipient_to_add_participant);
            setTitle(R.string.applicationLabel_add_participant);
            mStartCallButton.setEnabled(false);
            mStartCallButton.setBackgroundColor(Color.parseColor("#C1C1C1"));
            mStartCallButton.setVisibility(View.VISIBLE);
        } else {
            mStartCallButton.setEnabled(true);
            mStartCallButton.setVisibility(View.VISIBLE);
        }

        // register for broadcast
        IntentFilter filter = new IntentFilter();
        filter.addAction(INTENT_ACTION_DISMISS_CONF_URI_DIALER);
        this.registerReceiver(mReceiver, filter);

        // Allow the title to be set to a custom String using an extra on the intent
        String title = getIntent().getStringExtra("Title");
        if (title != null) {
            setTitle(title);
        }

        final boolean isVideoConfUriDialEnabled = this.getResources().getBoolean(
                R.bool.video_conference_uri_call_enabled);
        // Show video call buttton if video calling is enabled and not in add participant mode.
        if (isVideoConfUriDialEnabled && isVideoTelephonyAvailable() &&
                !(mIsAddParticipants && mIsInCall)) {
            mVideoCallButton.setEnabled(true);
            mVideoCallButton.setVisibility(View.VISIBLE);
        }
        if (savedInstanceState != null) {
            mEditText.setText(savedInstanceState.getString(KEY_EXISTING_EDIT_TEXT));
            if (savedInstanceState.getBoolean(KEY_IS_CALL_LOG_PICKER_SHOWN)) {
                getCallLog();
            }
        }

        mEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (mIsAddParticipants && mIsInCall) {
                    if (isDigitsEmpty()) {
                        mStartCallButton.setBackgroundColor(Color.parseColor("#C1C1C1"));
                        mStartCallButton.setEnabled(false);
                        mStartCallButton.setVisibility(View.VISIBLE);
                    } else {
                        mStartCallButton.setBackgroundColor(Color.parseColor("#00C853"));
                        mStartCallButton.setEnabled(true);
                        mStartCallButton.setVisibility(View.VISIBLE);
                    }
                }
            }
        });

        mCallLogButton.setOnClickListener(new ImageButton.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                hideInputMethod(getCurrentFocus());
                getCallLog();
            }
        });

        mContactButton.setOnClickListener(new ImageButton.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                Intent intent = new Intent(INTENT_PICK_ACTION, Contacts.CONTENT_URI);
                startActivityForResult(intent,ACTIVITY_REQUEST_CONTACT_PICK);
            }
        });

        mStartCallButton.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                //Disable call buttons to prevent multiple clicks.
                mStartCallButton.setEnabled(false);
                mVideoCallButton.setEnabled(false);
                mEditNumber = mEditText.getText().toString();
                Log.d(TAG, "onClick of CallButton number = " + mEditNumber);
                startButtonPressed(mEditNumber, false /*isVideoCall*/);
            }
        });

        mVideoCallButton.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                //Disable call buttons to prevent multiple clicks.
                mVideoCallButton.setEnabled(false);
                mStartCallButton.setEnabled(false);
                mEditNumber = mEditText.getText().toString();
                Log.d(TAG, "onClick of VideoCallButton number = " + mEditNumber);
                startButtonPressed(mEditNumber, true /*isVideoCall*/);
            }
        });

        mCancelButton.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                finish();
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
        mIsInCall = isInCall(mContext);
    }

    @Override
    protected void onPause() {
        super.onPause();
        hideInputMethod(getCurrentFocus());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_EXISTING_EDIT_TEXT, mEditText.getText().toString());
        outState.putBoolean(KEY_IS_CALL_LOG_PICKER_SHOWN,
                mAlertDialog != null && mAlertDialog.isShowing());
    }

    /**
    * Registers a call state listener.
    */
    public void registerCallStateListener() {
        Log.v(TAG, "registerCallStateListener() invoked.");
        TelephonyManager tm =
                (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        tm.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    /**
    * Unregisters a call state listener.
    */
    public void unRegisterCallStateListener() {
        Log.v(TAG, "unRegisterCallStateListener() invoked.");
        TelephonyManager tm =
                (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        tm.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
    }

    private Intent buildDialIntent(Uri uri, boolean isVideoCall) {
        Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED, uri);
        intent.putExtra(
                TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE,
                isVideoCall ? VideoProfile.STATE_BIDIRECTIONAL : VideoProfile.STATE_AUDIO_ONLY);
        return intent;
    }

    /* Return Uri with an appropriate scheme, accepting both SIP and usual phone call numbers. */
    private static Uri getCallUri(String number) {
        return Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null);
    }

    private Intent getDialConferenceCallIntent(String numbers, boolean isVideoCall) {
        Log.d(TAG, "Dial ConferenceCall numbers: " + numbers);
        Intent dialintent = buildDialIntent(getCallUri(numbers), isVideoCall);
        //Put conference uri extra
        dialintent.putExtra(EXTRA_DIAL_CONFERENCE_URI, true);
        return dialintent;
    }

    /** @return true if the EditText of phone number or uri digit is empty. */
    private boolean isDigitsEmpty() {
        return mEditText.length() == 0;
    }

    private boolean isVideoTelephonyAvailable() {
        TelephonyManager telephonymanager =
                (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        return telephonymanager.isVideoTelephonyAvailable();
    }

    private Intent getAddParticipantsCallIntent(String numbers){
        Log.d(TAG, "Add Participants number: " + numbers);
        if (numbers == null || numbers.isEmpty()) {
            Toast.makeText(this, R.string.add_participant_imposible,
                    Toast.LENGTH_LONG).show();
            return null;
        } else {
            Intent addParticipantIntent = buildDialIntent(getCallUri(numbers), false);
            addParticipantIntent.putExtra(ADD_PARTICIPANT_KEY, true);
            return addParticipantIntent;
        }
    }

    private static TelecomManager getTelecomManager(Context context) {
        return (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
    }

    private static boolean isInCall(Context context) {
        return getTelecomManager(context).isInCall();
    }

    private void startButtonPressed(String number, boolean isVideoCall) {

        Log.d(TAG, "startButtonPressed, number: " + number);
        Intent intent;
        if (mIsAddParticipants && isInCall(this)) {
            intent = getAddParticipantsCallIntent(number);
        } else {
            intent = getDialConferenceCallIntent(number, isVideoCall);
        }
        if (intent != null) {
            startActivity(intent);
            finish();
        } else {
            //Enable call buttons if process button click failed.
            mStartCallButton.setEnabled(true);
            mVideoCallButton.setEnabled(true);
        }
    }

    private void getCallLog() {
        String[] strFields = { android.provider.CallLog.Calls._ID,
                android.provider.CallLog.Calls.NUMBER,
                android.provider.CallLog.Calls.CACHED_NAME, };
        String strOrder = android.provider.CallLog.Calls.DATE + " DESC";
        final Cursor cursorCall = getContentResolver().query(
                android.provider.CallLog.Calls.CONTENT_URI, strFields,
                null, null, strOrder);
        AlertDialog.Builder dialog = new AlertDialog.Builder(mContext);
        dialog.setTitle(R.string.select_from_call_log);
        android.content.DialogInterface.OnClickListener listener =
                new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int item) {
                cursorCall.moveToPosition(item);
                String callLogNumber = cursorCall.getString(
                        cursorCall.getColumnIndex(android.provider.CallLog.Calls.NUMBER));
                String existingNumber = mEditText.getText().toString();
                existingNumber = (existingNumber == null || mEditText.length() == 0 ||
                        existingNumber.endsWith(";") || existingNumber.endsWith(",")) ?
                        existingNumber : existingNumber + ";";
                mEditText.setText(existingNumber +
                        PhoneNumberUtils.stripSeparators(callLogNumber));
                mEditText.setSelection(mEditText.getText().length());
                cursorCall.close();
                return;
            }
        };
        dialog.setCursor(cursorCall, listener, android.provider.CallLog.Calls.NUMBER);
        mAlertDialog =  dialog.create();
        mAlertDialog.setCanceledOnTouchOutside(false);
        mAlertDialog.show();
    }

    private void hideInputMethod(View view) {
        final InputMethodManager imm = (InputMethodManager) getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (imm != null && view != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (data == null) {
            Log.w(TAG, "Data is null from intent" );
            return;
        }

        if (resultCode == Activity.RESULT_OK) {
            Uri contactData = data.getData();
            Cursor c = managedQuery(contactData, null, null, null, null);
            if (c.moveToFirst()) {
                final String id = c.getString(c.getColumnIndexOrThrow(
                        ContactsContract.Contacts._ID));
                final String hasPhone =
                        c.getString(c.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER));
                String number = "";
                if (hasPhone.equalsIgnoreCase("1")) {
                    Cursor phones = getContentResolver().query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = " + id,
                            null, null);
                    phones.moveToFirst();
                    number = phones.getString(phones.getColumnIndex("data1"));
                    String existingNumber = mEditText.getText().toString();
                    existingNumber = (existingNumber == null || mEditText.length() == 0 ||
                            existingNumber.endsWith(";") || existingNumber.endsWith(",")) ?
                            existingNumber : existingNumber + ";";
                    mEditText.setText(existingNumber + PhoneNumberUtils.stripSeparators(number));
                    mEditText.setSelection(mEditText.getText().length());
                }
            }
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                hideInputMethod(getCurrentFocus());
                finishActivity(ACTIVITY_REQUEST_CONTACT_PICK);
                finish();
                return true;
        }
        return false;
    }

    @Override
    public void onDestroy() {
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
            mReceiver = null;
        }
        Log.v(TAG, "onDestroy(): Receiver = " + mReceiver);
        hideInputMethod(getCurrentFocus());
        unRegisterCallStateListener();
        super.onDestroy();
    }

    private final class ConfURIDialerPhoneStateListener extends PhoneStateListener {

        private int mPhoneCallState;

        public ConfURIDialerPhoneStateListener(ConfURIDialer confURIDialer) {
            Log.v(TAG, "ConfURIDialerPhoneStateListener() invoked.");
            mPhoneCallState = -1;
        }

        @Override
        public void onCallStateChanged(int state, String ignored) {
            if (mPhoneCallState == -1) {
                mPhoneCallState = state;
            }
            Log.d(TAG, "PhoneStateListener new state: " + state + " old state: " + mPhoneCallState
                    + " isAddParticipant: " + mIsAddParticipants);
            if (state == TelephonyManager.CALL_STATE_IDLE && state != mPhoneCallState) {
                mPhoneCallState = state;
                //PhoneState idle, clear ConfURIDialer if it's for add participant.
                if (mIsAddParticipants) {
                    mStartCallButton.setText(R.string.button_start_conference_call);
                    mEditText.setHint(R.string.add_uri_list_to_dial);
                    hideInputMethod(getCurrentFocus());
                    setTitle(R.string.applicationLabel);
                    mIsAddParticipants = false;
                    mIsInCall = false;
                    finishActivity(ACTIVITY_REQUEST_CONTACT_PICK);
                    finish();
                }
            }
        }
    }
}