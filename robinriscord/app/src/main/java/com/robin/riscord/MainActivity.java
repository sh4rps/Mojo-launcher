package com.robin.riscord;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int RC_SIGN = 11, RC_MIC = 12;
    private static final int BG = 0xFF1E1F2B, CARD = 0xFF2B2D3E, ACC = 0xFF5865F2, TXT = 0xFFF2F3F7, MUT = 0xFF9EA3B8, RED = 0xFFED4245, GRN = 0xFF3BA55D;

    private static class F { String uid, name, username; }
    private static class Req { String id, from, name, username; }

    private FrameLayout root;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private GoogleSignInClient gsc;
    private String me, myName, myUsername;
    private final List<F> friends = new ArrayList<>();
    private final List<Req> reqs = new ArrayList<>();
    private final Set<String> selected = new HashSet<>();
    private final Set<String> seenCalls = new HashSet<>();
    private ListenerRegistration friendsReg, reqReg, callsReg;
    private Call call;
    private TextView callNames, callState;
    private Runnable afterMic;
    private boolean inCall;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        setContentView(root);
        if (FirebaseApp.getApps(this).isEmpty()) { showSetup(); return; }
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        int wid = getResources().getIdentifier("default_web_client_id", "string", getPackageName());
        if (wid == 0) { showSetup(); return; }
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(wid)).requestEmail().build();
        gsc = GoogleSignIn.getClient(this, gso);
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) showLogin(); else afterLogin(u);
    }

    // ---------------------------------------------------------- helpers
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private TextView text(String s, int sp, int c) { TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(c); return t; }

    private TextView btn(String s, int color, Runnable r) {
        TextView t = text(s, 14, 0xFFFFFFFF);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(10));
        t.setBackground(g);
        t.setOnClickListener(v -> r.run());
        return t;
    }

    private LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int bt) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(l), dp(t), dp(r), dp(bt));
        return p;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(28), dp(16), dp(16));
        return l;
    }

    private void screen(View v) { root.removeAllViews(); root.addView(v, new FrameLayout.LayoutParams(-1, -1)); }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable g = new GradientDrawable(); g.setColor(CARD); g.setCornerRadius(dp(12));
        c.setBackground(g);
        return c;
    }

    // ----------------------------------------------------------- screens
    private void showSetup() {
        LinearLayout c = column();
        c.addView(text("Riscord", 26, TXT));
        c.addView(text("Setup needed: this build has no Firebase config (google-services.json). Add it to the project and rebuild, then Google login, friends and calls will work.", 14, MUT), lp(-1, -2, 0, 12, 0, 0));
        screen(c);
    }

    private void showLogin() {
        LinearLayout c = column();
        c.setGravity(Gravity.CENTER);
        c.addView(text("Riscord", 32, TXT));
        c.addView(text("Voice calls with friends", 14, MUT), lp(-2, -2, 0, 4, 0, 24));
        c.addView(btn("Sign in with Google", ACC, () -> startActivityForResult(gsc.getSignInIntent(), RC_SIGN)), lp(-1, -2, 0, 0, 0, 0));
        screen(c);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != RC_SIGN) return;
        try {
            GoogleSignInAccount acc = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(com.google.android.gms.common.api.ApiException.class);
            AuthCredential cred = GoogleAuthProvider.getCredential(acc.getIdToken(), null);
            auth.signInWithCredential(cred).addOnSuccessListener(r -> afterLogin(r.getUser()))
                    .addOnFailureListener(e -> toast("Sign-in failed: " + e.getMessage()));
        } catch (Exception e) {
            toast("Google sign-in failed: " + e.getMessage() + " (check SHA-1 / google-services.json)");
        }
    }

    private void afterLogin(FirebaseUser u) {
        me = u.getUid();
        myName = u.getDisplayName() == null ? "User" : u.getDisplayName();
        db.collection("users").document(me).get().addOnSuccessListener(d -> {
            if (d.exists() && d.getString("username") != null) { myUsername = d.getString("username"); startHome(); }
            else showUsername();
        }).addOnFailureListener(e -> toast("Database error: " + e.getMessage() + " (create Firestore + rules)"));
    }

    private void showUsername() {
        LinearLayout c = column();
        c.addView(text("Choose a username", 22, TXT));
        c.addView(text("3-20 letters, numbers or underscore. Friends add you with this.", 13, MUT), lp(-1, -2, 0, 6, 0, 12));
        EditText e = new EditText(this);
        e.setTextColor(TXT); e.setHintTextColor(MUT); e.setHint("username"); e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        c.addView(e);
        c.addView(btn("Continue", ACC, () -> claimUsername(e.getText().toString().trim().toLowerCase())), lp(-1, -2, 0, 14, 0, 0));
        c.addView(btn("Sign out", CARD, this::signOut), lp(-1, -2, 0, 10, 0, 0));
        screen(c);
    }

    private void claimUsername(String un) {
        if (!un.matches("[a-z0-9_]{3,20}")) { toast("Use 3-20 letters, numbers or _"); return; }
        DocumentReference uref = db.collection("usernames").document(un), me_ = db.collection("users").document(me);
        db.runTransaction(tr -> {
            if (tr.get(uref).exists()) throw new IllegalStateException("Username taken");
            Map<String, Object> a = new HashMap<>(); a.put("uid", me);
            tr.set(uref, a);
            Map<String, Object> u = new HashMap<>();
            u.put("name", myName); u.put("username", un);
            tr.set(me_, u);
            return null;
        }).addOnSuccessListener(x -> { myUsername = un; startHome(); })
                .addOnFailureListener(e -> toast(e.getMessage()));
    }

    private void signOut() {
        stopListeners();
        auth.signOut();
        gsc.signOut();
        showLogin();
    }

    // -------------------------------------------------------------- home
    private void startHome() {
        stopListeners();
        friendsReg = db.collection("users").document(me).collection("friends").addSnapshotListener((s, e) -> {
            if (s == null) return;
            friends.clear();
            for (DocumentSnapshot d : s.getDocuments()) {
                F f = new F(); f.uid = d.getId(); f.name = d.getString("name"); f.username = d.getString("username");
                friends.add(f);
            }
            if (!inCall) renderHome();
        });
        reqReg = db.collection("requests").whereEqualTo("to", me).addSnapshotListener((s, e) -> {
            if (s == null) return;
            reqs.clear();
            for (DocumentSnapshot d : s.getDocuments()) {
                Req r = new Req(); r.id = d.getId(); r.from = d.getString("from"); r.name = d.getString("fromName"); r.username = d.getString("fromUsername");
                reqs.add(r);
            }
            if (!inCall) renderHome();
        });
        callsReg = db.collection("calls").whereArrayContains("invited", me).addSnapshotListener((s, e) -> {
            if (s == null) return;
            for (DocumentChange ch : s.getDocumentChanges()) {
                if (ch.getType() != DocumentChange.Type.ADDED) continue;
                DocumentSnapshot d = ch.getDocument();
                Long ts = d.getLong("ts");
                Boolean act = d.getBoolean("active");
                if (act == null || !act || ts == null || Math.abs(System.currentTimeMillis() - ts) > 60000) continue;
                if (inCall || !seenCalls.add(d.getId())) continue;
                incoming(d.getId(), d.getString("callerName"));
            }
        });
        renderHome();
    }

    private void stopListeners() {
        if (friendsReg != null) friendsReg.remove();
        if (reqReg != null) reqReg.remove();
        if (callsReg != null) callsReg.remove();
        friendsReg = reqReg = callsReg = null;
    }

    private void renderHome() {
        LinearLayout c = column();
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout who = new LinearLayout(this);
        who.setOrientation(LinearLayout.VERTICAL);
        who.addView(text("Riscord", 22, TXT));
        who.addView(text(myName + "  @" + myUsername, 12, MUT));
        top.addView(who, new LinearLayout.LayoutParams(0, -2, 1f));
        top.addView(btn("Sign out", CARD, this::signOut));
        c.addView(top);

        LinearLayout add = new LinearLayout(this);
        EditText e = new EditText(this);
        e.setTextColor(TXT); e.setHintTextColor(MUT); e.setHint("Friend's username"); e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        add.addView(e, new LinearLayout.LayoutParams(0, -2, 1f));
        add.addView(btn("Add friend", ACC, () -> addFriend(e.getText().toString().trim().toLowerCase())), lp(-2, -2, 8, 0, 0, 0));
        c.addView(add, lp(-1, -2, 0, 14, 0, 0));

        ScrollView sv = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        c.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        if (!reqs.isEmpty()) {
            list.addView(text("Friend requests", 13, MUT), lp(-2, -2, 0, 10, 0, 6));
            for (Req r : reqs) {
                LinearLayout row = card();
                row.addView(text(r.name + "  @" + r.username, 14, TXT), new LinearLayout.LayoutParams(0, -2, 1f));
                row.addView(btn("Accept", GRN, () -> accept(r)), lp(-2, -2, 0, 0, 6, 0));
                row.addView(btn("Decline", RED, () -> db.collection("requests").document(r.id).delete()));
                list.addView(row, lp(-1, -2, 0, 0, 0, 8));
            }
        }
        list.addView(text("Friends", 13, MUT), lp(-2, -2, 0, 10, 0, 6));
        if (friends.isEmpty()) list.addView(text("No friends yet. Add one by username.", 13, MUT));
        for (F f : friends) {
            LinearLayout row = card();
            CheckBox cb = new CheckBox(this);
            cb.setChecked(selected.contains(f.uid));
            cb.setOnCheckedChangeListener((v, on) -> { if (on) selected.add(f.uid); else selected.remove(f.uid); });
            row.addView(cb);
            row.addView(text(f.name + "  @" + f.username, 14, TXT), new LinearLayout.LayoutParams(0, -2, 1f));
            row.addView(btn("Call", GRN, () -> startCall(listOf(f))));
            list.addView(row, lp(-1, -2, 0, 0, 0, 8));
        }
        c.addView(btn("Group call with checked friends", ACC, () -> {
            List<F> l = new ArrayList<>();
            for (F f : friends) if (selected.contains(f.uid)) l.add(f);
            if (l.isEmpty()) { toast("Tick at least one friend"); return; }
            startCall(l);
        }), lp(-1, -2, 0, 8, 0, 0));
        screen(c);
    }

    private List<F> listOf(F f) { List<F> l = new ArrayList<>(); l.add(f); return l; }

    private void addFriend(String un) {
        if (un.isEmpty()) return;
        if (un.equals(myUsername)) { toast("That's you"); return; }
        db.collection("usernames").document(un).get().addOnSuccessListener(d -> {
            if (!d.exists()) { toast("No user with that username"); return; }
            String uid = d.getString("uid");
            for (F f : friends) if (f.uid.equals(uid)) { toast("Already friends"); return; }
            Map<String, Object> m = new HashMap<>();
            m.put("from", me); m.put("to", uid); m.put("fromName", myName); m.put("fromUsername", myUsername);
            db.collection("requests").document(me + "_" + uid).set(m)
                    .addOnSuccessListener(x -> toast("Request sent to @" + un))
                    .addOnFailureListener(e -> toast("Failed: " + e.getMessage()));
        }).addOnFailureListener(e -> toast("Failed: " + e.getMessage()));
    }

    private void accept(Req r) {
        WriteBatch b = db.batch();
        Map<String, Object> a = new HashMap<>(); a.put("name", r.name); a.put("username", r.username);
        Map<String, Object> m = new HashMap<>(); m.put("name", myName); m.put("username", myUsername);
        b.set(db.collection("users").document(me).collection("friends").document(r.from), a);
        b.set(db.collection("users").document(r.from).collection("friends").document(me), m);
        b.delete(db.collection("requests").document(r.id));
        b.commit().addOnFailureListener(e -> toast("Failed: " + e.getMessage()));
    }

    // -------------------------------------------------------------- calls
    private void withMic(Runnable r) {
        if (Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { r.run(); return; }
        afterMic = r;
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, RC_MIC);
    }

    @Override public void onRequestPermissionsResult(int req, String[] p, int[] g) {
        super.onRequestPermissionsResult(req, p, g);
        if (req == RC_MIC && g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED && afterMic != null) afterMic.run();
        else if (req == RC_MIC) toast("Microphone permission is needed for calls");
        afterMic = null;
    }

    private void startCall(List<F> who) {
        withMic(() -> {
            DocumentReference ref = db.collection("calls").document();
            List<String> inv = new ArrayList<>();
            for (F f : who) inv.add(f.uid);
            Map<String, Object> m = new HashMap<>();
            m.put("callerUid", me); m.put("callerName", myName); m.put("invited", inv);
            m.put("active", true); m.put("ts", System.currentTimeMillis());
            ref.set(m).addOnSuccessListener(x -> { seenCalls.add(ref.getId()); enterCall(ref.getId(), who.size() == 1 ? who.get(0).name : who.size() + " friends"); })
                    .addOnFailureListener(e -> toast("Failed: " + e.getMessage()));
        });
    }

    private void incoming(String id, String caller) {
        try { Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE); if (v != null) v.vibrate(600); } catch (Exception ignored) { }
        new AlertDialog.Builder(this).setTitle("Incoming call").setMessage((caller == null ? "Someone" : caller) + " is calling you")
                .setCancelable(false)
                .setPositiveButton("Answer", (d, w) -> withMic(() -> enterCall(id, caller)))
                .setNegativeButton("Decline", null).show();
    }

    private void enterCall(String id, String title) {
        inCall = true;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout c = column();
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.addView(text("Call: " + title, 20, TXT));
        callState = text("Connecting...", 13, MUT);
        c.addView(callState, lp(-2, -2, 0, 6, 0, 18));
        callNames = text("", 16, TXT);
        callNames.setGravity(Gravity.CENTER);
        c.addView(callNames, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout row = new LinearLayout(this);
        boolean[] muted = {false}, spk = {false};
        call = new Call(this, me, myName, id, new Call.Ui() {
            @Override public void onPeers(List<String> names) { runOnUiThread(() -> callNames.setText(String.join("\n", names))); }
            @Override public void onState(String s) { runOnUiThread(() -> callState.setText(s)); }
        });
        TextView mute = btn("Mute", CARD, () -> { });
        mute.setOnClickListener(v -> { muted[0] = !muted[0]; call.setMuted(muted[0]); mute.setText(muted[0] ? "Unmute" : "Mute"); });
        TextView sp = btn("Speaker", CARD, () -> { });
        sp.setOnClickListener(v -> { spk[0] = !spk[0]; call.setSpeaker(spk[0]); sp.setText(spk[0] ? "Earpiece" : "Speaker"); });
        row.addView(mute, lp(0, -2, 0, 0, 8, 0)); row.addView(sp, lp(0, -2, 0, 0, 8, 0));
        ((LinearLayout.LayoutParams) mute.getLayoutParams()).weight = 1f; ((LinearLayout.LayoutParams) sp.getLayoutParams()).weight = 1f;
        row.addView(btn("Hang up", RED, this::hangUp), lp(0, -2, 0, 0, 0, 0));
        ((LinearLayout.LayoutParams) row.getChildAt(2).getLayoutParams()).weight = 1f;
        c.addView(row, lp(-1, -2, 0, 12, 0, 12));
        screen(c);
        call.join();
    }

    private void hangUp() {
        if (call != null) call.leave();
        call = null; inCall = false;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        renderHome();
    }

    @Override public void onBackPressed() { if (inCall) hangUp(); else super.onBackPressed(); }

    @Override protected void onDestroy() {
        if (call != null) call.leave();
        stopListeners();
        super.onDestroy();
    }
}
