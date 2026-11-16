package com.rclient.ui;

import net.minecraft.client.gui.screen.Screen;

public class AccountsScreen extends SimplePage {
    public AccountsScreen(Screen parent) { super("Accounts", parent); }
    @Override protected void build() {
        var s = mc.getSession();
        lines.add("Current account: " + s.getUsername());
        lines.add("UUID: " + (s.getUuidOrNull() == null ? "offline" : s.getUuidOrNull().toString()));
        lines.add("");
        lines.add("Account switching happens in your launcher (the");
        lines.add("launcher owns the login token). RClient only shows");
        lines.add("the active account and never stores credentials.");
    }
}
