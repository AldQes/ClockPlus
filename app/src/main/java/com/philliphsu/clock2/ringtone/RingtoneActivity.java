package com.philliphsu.clock2.ringtone;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import com.philliphsu.clock2.Alarm;
import com.philliphsu.clock2.R;
import com.philliphsu.clock2.model.AlarmsRepository;
import com.philliphsu.clock2.util.AlarmUtils;

import static com.philliphsu.clock2.util.DateFormatUtils.formatTime;
import static com.philliphsu.clock2.util.Preconditions.checkNotNull;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 *
 * TODO: Make this abstract and make appropriate subclasses for Alarms and Timers.
 * TODO: Implement dismiss and extend logic here.
 */
public class RingtoneActivity extends AppCompatActivity implements RingtoneService.RingtoneCallback {
    private static final String TAG = "RingtoneActivity";

    // Shared with RingtoneService
    public static final String EXTRA_ITEM_ID = "com.philliphsu.clock2.ringtone.extra.ITEM_ID";

    private Alarm mAlarm;
    private boolean mBound = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ringtone);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        long id = getIntent().getLongExtra(EXTRA_ITEM_ID, -1);
        if (id < 0) {
            throw new IllegalStateException("Cannot start RingtoneActivity without item's id");
        }
        mAlarm = checkNotNull(AlarmsRepository.getInstance(this).getItem(id));
        Log.d(TAG, "Ringing alarm " + mAlarm);

        // TODO: If the upcoming alarm notification isn't present, verify other notifications aren't affected.
        // This could be the case if we're starting a new instance of this activity after leaving the first launch.
        AlarmUtils.removeUpcomingAlarmNotification(this, mAlarm);

        Intent intent = new Intent(this, RingtoneService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        Button snooze = (Button) findViewById(R.id.btn_snooze);
        snooze.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                snooze();
            }
        });
        Button dismiss = (Button) findViewById(R.id.btn_dismiss);
        dismiss.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        //super.onNewIntent(intent); // Not needed since no fragments hosted?
        if (mBound) {
            mBoundService.interrupt(); // prepare to notify the alarm was missed
            // Cannot rely on finish() to call onDestroy() on time before the activity is restarted.
            unbindService();
            // Calling recreate() would recreate this with its current intent, not the new intent passed in here.
            finish();
            startActivity(intent);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            // Set the content to appear under the system bars so that the
            // content doesn't resize when the system bars hide and show.
            // The system bars will remain hidden on user interaction;
            // however, they can be revealed using swipe gestures along
            // the region where they normally appear.
            int uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                    | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                    | View.SYSTEM_UI_FLAG_IMMERSIVE;

            // Make status bar translucent, which automatically adds
            // SYSTEM_UI_FLAG_LAYOUT_STABLE and SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            // Looks too light on the current background..
            //getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            getWindow().getDecorView().setSystemUiVisibility(uiOptions);
        }
    }

    @Override
    public void onBackPressed() {
        // Capture the back press and return. We want to limit the user's options for leaving
        // this activity as much as possible.
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService();
    }

    @Override
    public void onServiceFinish() {
        dismiss();
    }

    private void snooze() {
        int snoozeMins = AlarmUtils.snoozeDuration(this);
        mAlarm.snooze(snoozeMins);
        AlarmUtils.scheduleAlarm(this, mAlarm);
        AlarmsRepository.getInstance(this).saveItems();
        // Display toast
        String message = getString(R.string.title_snoozing_until, formatTime(this, mAlarm.snoozingUntil()));
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        dismiss();
    }

    private void dismiss() {
        // TODO: Do we need to cancel the PendingIntent and the alarm in AlarmManager?
        unbindService(); // don't wait for finish() to call onDestroy()
        finish();
    }

    private void unbindService() {
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }

    private RingtoneService mBoundService;

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mBoundService = ((RingtoneService.RingtoneBinder) service).getService();
            mBoundService.playRingtone(mAlarm);
            mBoundService.setRingtoneCallback(RingtoneActivity.this);
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBoundService = null;
            mBound = false;
        }
    };
}