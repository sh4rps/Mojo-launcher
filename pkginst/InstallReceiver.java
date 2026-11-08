package com.pkginst.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

public class InstallReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        int sid = i.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1);
        String msg = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { c.startActivity(confirm); } catch (Throwable t) { MainActivity.result(sid, false, "Cannot open installer: " + t); }
            }
            return;
        }
        MainActivity.result(sid, status == PackageInstaller.STATUS_SUCCESS, status == PackageInstaller.STATUS_SUCCESS ? "Installed" : nice(status, msg));
    }

    static String nice(int status, String msg) {
        switch (status) {
            case PackageInstaller.STATUS_FAILURE_ABORTED: return "Cancelled";
            case PackageInstaller.STATUS_FAILURE_BLOCKED: return "Blocked by system" + (msg != null ? " (" + msg + ")" : "");
            case PackageInstaller.STATUS_FAILURE_CONFLICT: return "Conflict: signature differs from installed app — uninstall the old one first";
            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE: return "Not compatible with this phone" + (msg != null ? " (" + msg + ")" : "");
            case PackageInstaller.STATUS_FAILURE_INVALID: return "Invalid / corrupt APK" + (msg != null ? " (" + msg + ")" : "");
            case PackageInstaller.STATUS_FAILURE_STORAGE: return "Not enough storage";
            default: return "Failed" + (msg != null ? ": " + msg : "");
        }
    }
}
