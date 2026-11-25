package com.robin.cord;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** After a reboot or an app update, start the guard again so a switched-off accessibility service is noticed. */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) { GuardService.start(c); }
}
