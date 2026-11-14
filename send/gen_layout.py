# generates res/fragment_launcher_land.xml (Send Launcher home, modelled on the reference mockup)
import os
HERE = os.path.dirname(os.path.abspath(__file__))
W = 'android:layout_width="%s" android:layout_height="%s"'

def nav(tag, icon, label, on=False):
    bg = 'send_nav_on' if on else 'send_pill'
    return f'''            <LinearLayout android:tag="{tag}" android:orientation="vertical" android:gravity="center" android:clickable="true" android:focusable="true" android:background="@drawable/{bg}" android:layout_width="fill_parent" android:layout_height="54dp" android:layout_marginBottom="3dp">
                <ImageView android:src="@drawable/{icon}" android:layout_width="22dp" android:layout_height="22dp" />
                <TextView android:text="{label}" android:textColor="#fff5f3ff" android:textSize="10sp" android:maxLines="1" android:layout_marginTop="2dp" android:layout_width="wrap_content" android:layout_height="wrap_content" />
            </LinearLayout>
'''

def quick(tag, icon, title, sub):
    return f'''            <LinearLayout android:tag="{tag}" android:orientation="horizontal" android:gravity="center_vertical" android:clickable="true" android:focusable="true" android:background="@drawable/send_card" android:padding="8dp" android:layout_width="0dp" android:layout_weight="1" android:layout_height="fill_parent" android:layout_marginStart="6dp">
                <ImageView android:src="@drawable/{icon}" android:background="@drawable/send_chip" android:padding="7dp" android:layout_width="34dp" android:layout_height="34dp" />
                <LinearLayout android:orientation="vertical" android:layout_marginStart="8dp" android:layout_width="0dp" android:layout_weight="1" android:layout_height="wrap_content">
                    <TextView android:text="{title}" android:textColor="#fff5f3ff" android:textStyle="bold" android:textSize="12sp" android:maxLines="1" android:layout_width="wrap_content" android:layout_height="wrap_content" />
                    <TextView android:text="{sub}" android:textColor="#ffc4b5fd" android:textSize="10sp" android:maxLines="1" android:layout_width="wrap_content" android:layout_height="wrap_content" />
                </LinearLayout>
            </LinearLayout>
'''

def hidden(tag, cid, text, icon):
    idattr = f'android:id="@id/{cid}" ' if cid else ''
    return f'            <com.kdt.mcgui.LauncherMenuButton {idattr}android:tag="{tag}" android:text="{text}" android:drawableStart="@drawable/{icon}" android:layout_width="fill_parent" style="@style/LauncherMenuButton.Universal" />\n'

x = '''<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@id/fragment_menu_main"
    android:background="#ff0e0b1f"
    android:layout_width="fill_parent"
    android:layout_height="fill_parent">

    <rl.ForestView android:layout_width="0dp" android:layout_height="0dp"
        app:layout_constraintStart_toStartOf="parent" app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintTop_toTopOf="parent" app:layout_constraintBottom_toBottomOf="parent" />

    <androidx.constraintlayout.widget.Guideline android:id="@+id/send_g_stage" android:orientation="vertical" android:layout_width="0dp" android:layout_height="0dp" app:layout_constraintGuide_percent="0.60" />

    <!-- sidebar -->
    <ScrollView android:id="@+id/send_sidebar" android:background="@drawable/send_side_bg" android:scrollbars="none"
        android:layout_width="80dp" android:layout_height="0dp"
        app:layout_constraintStart_toStartOf="parent" app:layout_constraintTop_toTopOf="parent" app:layout_constraintBottom_toBottomOf="parent">
        <LinearLayout android:orientation="vertical" android:padding="6dp" android:layout_width="fill_parent" android:layout_height="wrap_content">
''' + nav('h_home', 'send_i_home', 'Home', True) + nav('h_versions', 'send_i_versions', 'Versions') + nav('h_mods', 'send_i_mods', 'Mods') \
  + nav('h_packs', 'send_i_packs', 'Textures') + nav('h_shaders', 'send_i_shaders', 'Shaders') + nav('h_settings', 'send_i_gear', 'Settings') + nav('h_about', 'send_i_info', 'About') + '''            <TextView android:id="@+id/send_online" android:tag="h_online" android:text="●  Online" android:textColor="#ff4ade80" android:textSize="11sp" android:textStyle="bold" android:gravity="center" android:background="@drawable/send_card" android:layout_marginTop="6dp" android:layout_width="fill_parent" android:layout_height="30dp" />
        </LinearLayout>
    </ScrollView>

    <!-- original Mojo buttons: kept (hidden) so every existing action still works; the new UI clicks them -->
    <LinearLayout android:id="@+id/send_hidden" android:visibility="gone" android:orientation="vertical" android:layout_width="1dp" android:layout_height="1dp"
        app:layout_constraintStart_toStartOf="parent" app:layout_constraintTop_toTopOf="parent">
''' + hidden('h_o_news', 'news_button', '@string/mcl_tab_wiki', 'ic_px_book') \
  + '            <View android:id="@id/social_divider" android:layout_width="1dp" android:layout_height="1dp" />\n' \
  + hidden('h_o_social', 'social_media_button', '@string/mcl_button_social_media', 'ic_social_media') \
  + hidden('robin_content', None, '@string/robin_content_title', 'ic_px_file_dl') \
  + hidden('h_o_controls', 'custom_control_button', '@string/mcl_option_customcontrol', 'ic_px_gamepad') \
  + hidden('h_o_jar', 'install_jar_button', '@string/main_install_jar_file', 'ic_px_java_run') \
  + hidden('h_o_files', 'open_files_button', '@string/mcl_button_open_directory', 'ic_px_folder') \
  + hidden('h_o_logs', 'share_logs_button', '@string/main_share_logs', 'ic_px_sharelog') + '''    </LinearLayout>

    <!-- bottom strip: installed versions + quick actions -->
    <LinearLayout android:id="@+id/send_bottom" android:orientation="horizontal"
        android:layout_width="0dp" android:layout_height="84dp" android:layout_marginStart="10dp" android:layout_marginEnd="10dp" android:layout_marginBottom="8dp"
        app:layout_constraintStart_toEndOf="@id/send_sidebar" app:layout_constraintEnd_toEndOf="parent" app:layout_constraintBottom_toBottomOf="parent">
        <LinearLayout android:orientation="vertical" android:layout_width="0dp" android:layout_weight="1.15" android:layout_height="fill_parent">
            <LinearLayout android:orientation="horizontal" android:layout_width="fill_parent" android:layout_height="wrap_content" android:layout_marginBottom="3dp">
                <TextView android:text="Installed Versions" android:textColor="#fff5f3ff" android:textStyle="bold" android:textSize="12sp" android:layout_width="0dp" android:layout_weight="1" android:layout_height="wrap_content" />
                <TextView android:tag="h_viewall" android:text="View All  ›" android:textColor="#ffa78bfa" android:textSize="11sp" android:clickable="true" android:layout_width="wrap_content" android:layout_height="wrap_content" />
            </LinearLayout>
            <LinearLayout android:id="@+id/send_vers" android:tag="h_vers" android:orientation="horizontal" android:layout_width="fill_parent" android:layout_height="0dp" android:layout_weight="1" />
        </LinearLayout>
        <LinearLayout android:orientation="horizontal" android:layout_width="0dp" android:layout_weight="1.35" android:layout_height="fill_parent" android:layout_marginStart="8dp">
''' + quick('h_q_mods', 'send_i_plus', 'Add Mods', 'Browse &amp; install') + quick('h_q_packs', 'send_i_packs', 'Texture Packs', 'Find &amp; apply') + quick('h_q_shaders', 'send_i_shaders', 'Shaders', 'Better visuals') + '''        </LinearLayout>
    </LinearLayout>

    <!-- 3D stage -->
    <rl.HomeStage android:id="@+id/send_stage" android:layout_width="0dp" android:layout_height="0dp"
        app:layout_constraintStart_toEndOf="@id/send_sidebar" app:layout_constraintEnd_toStartOf="@id/send_g_stage"
        app:layout_constraintTop_toTopOf="parent" app:layout_constraintBottom_toTopOf="@id/send_bottom" />

    <!-- right column: profile, version, play -->
    <LinearLayout android:id="@+id/send_right" android:orientation="vertical" android:gravity="center_vertical"
        android:layout_width="0dp" android:layout_height="0dp" android:layout_marginStart="2dp" android:layout_marginEnd="12dp" android:layout_marginBottom="8dp"
        app:layout_constraintStart_toEndOf="@id/send_g_stage" app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintTop_toTopOf="parent" app:layout_constraintBottom_toTopOf="@id/send_bottom">

        <LinearLayout android:orientation="vertical" android:background="@drawable/send_card" android:padding="10dp"
            android:layout_width="fill_parent" android:layout_height="wrap_content" android:layout_marginBottom="8dp">
            <TextView android:text="Player Profile" android:textColor="#ffc4b5fd" android:textSize="11sp" android:textStyle="bold" android:layout_width="wrap_content" android:layout_height="wrap_content" />
            <LinearLayout android:orientation="horizontal" android:gravity="center_vertical" android:layout_width="fill_parent" android:layout_height="wrap_content" android:layout_marginTop="4dp">
                <ImageView android:tag="h_avatar" android:background="@drawable/send_chip" android:padding="2dp" android:layout_width="44dp" android:layout_height="44dp" />
                <LinearLayout android:orientation="vertical" android:layout_marginStart="10dp" android:layout_width="0dp" android:layout_weight="1" android:layout_height="wrap_content">
                    <TextView android:tag="h_name" android:text="Player" android:textColor="#ffffffff" android:textStyle="bold" android:textSize="15sp" android:maxLines="1" android:layout_width="wrap_content" android:layout_height="wrap_content" />
                    <TextView android:tag="h_sub" android:text="Default Skin" android:textColor="#ffc4b5fd" android:textSize="11sp" android:layout_width="wrap_content" android:layout_height="wrap_content" />
                </LinearLayout>
                <ImageView android:tag="h_pencil" android:src="@drawable/send_i_pencil" android:padding="9dp" android:clickable="true" android:background="@drawable/send_pill" android:layout_width="38dp" android:layout_height="38dp" />
            </LinearLayout>
            <TextView android:id="@+id/send_skin_btn" android:tag="robin_skins" android:text="Change Skin" android:textColor="#ffffffff" android:textStyle="bold" android:textSize="13sp"
                android:gravity="center" android:background="@drawable/send_btn2" android:clickable="true" android:focusable="true"
                android:layout_width="fill_parent" android:layout_height="38dp" android:layout_marginTop="8dp" />
        </LinearLayout>

        <LinearLayout android:orientation="vertical" android:background="@drawable/send_card" android:padding="8dp"
            android:layout_width="fill_parent" android:layout_height="wrap_content" android:layout_marginBottom="8dp">
            <TextView android:text="Game Version" android:textColor="#ffc4b5fd" android:textSize="11sp" android:textStyle="bold" android:layout_width="wrap_content" android:layout_height="wrap_content" />
            <LinearLayout android:orientation="horizontal" android:gravity="center_vertical" android:layout_width="fill_parent" android:layout_height="wrap_content">
                <com.kdt.mcgui.mcVersionSpinner android:id="@id/mc_version_spinner" android:tag="h_spinner" android:background="@android:color/transparent"
                    android:layout_width="0dp" android:layout_weight="1" android:layout_height="@dimen/_30sdp"
                    android:drawableEnd="@drawable/spinner_arrow"
                    app:drawableEndPadding="@dimen/_1sdp" app:drawableEndSize="@dimen/_12sdp"
                    app:drawableStartIntegerScaling="true" app:drawableStartSize="@dimen/_30sdp" />
                <ImageButton android:id="@id/edit_profile_button" android:background="?android:selectableItemBackground"
                    android:layout_width="36dp" android:layout_height="36dp" android:src="@drawable/ic_px_edit" />
            </LinearLayout>
        </LinearLayout>

        <com.kdt.mcgui.MineButton android:id="@id/play_button" android:text="@string/main_play" android:background="@drawable/send_play"
            android:textColor="#ffffffff" android:textAllCaps="true" android:textSize="20sp"
            android:layout_width="fill_parent" android:layout_height="52dp" />
        <TextView android:text="Start Minecraft" android:textColor="#ffc4b5fd" android:textSize="10sp" android:gravity="center" android:layout_marginTop="2dp" android:layout_width="fill_parent" android:layout_height="wrap_content" />
    </LinearLayout>

    <View android:id="@id/_background_display_view" android:visibility="gone" android:layout_width="1dp" android:layout_height="1dp" app:layout_constraintTop_toTopOf="parent" app:layout_constraintStart_toStartOf="parent" />
    <androidx.constraintlayout.widget.Guideline android:id="@id/center_guideline" android:orientation="vertical" android:layout_width="0dp" android:layout_height="0dp" app:layout_constraintGuide_percent="0.5" />
</androidx.constraintlayout.widget.ConstraintLayout>
'''
open(f'{HERE}/res/fragment_launcher_land.xml', 'w').write(x)
print('layout ok')
