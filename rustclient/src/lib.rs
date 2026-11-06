//! Rust Client: a launcher written in Rust. It draws its own UI on the native
//! window and starts the bundled Horizon (Rust) Minecraft engine inside this app.

use android_activity::{input::{InputEvent, MotionAction}, AndroidApp, InputStatus, MainEvent, PollEvent};
use font8x8::legacy::BASIC_LEGACY;
use jni::{objects::{JObject, JValue}, JavaVM};
use std::time::Duration;

const BG: u32 = 0xFF14110A;
const PANEL: u32 = 0xFF211C12;
const ORANGE: u32 = 0xFFDE5F14;
const GOLD: u32 = 0xFFD4AF37;
const WHITE: u32 = 0xFFF5EFE6;
const GREY: u32 = 0xFF6E675B;

struct Frame<'a> { px: &'a mut [u32], w: i32, h: i32, stride: i32 }

impl Frame<'_> {
    fn rect(&mut self, x: i32, y: i32, w: i32, h: i32, c: u32) {
        for yy in y.max(0)..(y + h).min(self.h) {
            for xx in x.max(0)..(x + w).min(self.w) {
                self.px[(yy * self.stride + xx) as usize] = c;
            }
        }
    }
    /// Rounded rectangle (corner radius r).
    fn round(&mut self, x: i32, y: i32, w: i32, h: i32, r: i32, c: u32) {
        for yy in y.max(0)..(y + h).min(self.h) {
            for xx in x.max(0)..(x + w).min(self.w) {
                let dx = (r - (xx - x)).max(xx - (x + w - 1 - r)).max(0);
                let dy = (r - (yy - y)).max(yy - (y + h - 1 - r)).max(0);
                if dx * dx + dy * dy <= r * r {
                    self.px[(yy * self.stride + xx) as usize] = c;
                }
            }
        }
    }
    fn text(&mut self, s: &str, x: i32, y: i32, scale: i32, c: u32) {
        let mut cx = x;
        for ch in s.chars() {
            let idx = if (ch as usize) < 128 { ch as usize } else { b'?' as usize };
            let g = BASIC_LEGACY[idx];
            for (row, bits) in g.iter().enumerate() {
                for col in 0..8 {
                    if bits >> col & 1 == 1 {
                        self.rect(cx + col * scale, y + row as i32 * scale, scale, scale, c);
                    }
                }
            }
            cx += 8 * scale;
        }
    }
    fn text_w(s: &str, scale: i32) -> i32 { s.chars().count() as i32 * 8 * scale }
}

#[derive(Clone, Copy)]
struct Button { x: i32, y: i32, w: i32, h: i32 }
impl Button { fn hit(&self, px: f32, py: f32) -> bool { px >= self.x as f32 && px <= (self.x + self.w) as f32 && py >= self.y as f32 && py <= (self.y + self.h) as f32 } }

struct Layout { play: Button, scale: i32 }

fn layout(w: i32, h: i32) -> Layout {
    let scale = (h / 150).clamp(2, 5);
    let cw = (w * 3 / 5).min(900);
    let x = (w - cw) / 2;
    let play = Button { x: x + 20 * scale / 2, y: h / 2 + 8 * scale, w: cw - 20 * scale, h: 16 * scale };
    Layout { play, scale }
}

fn draw(f: &mut Frame, pressed: bool, status: &str) {
    let (w, h) = (f.w, f.h);
    f.rect(0, 0, w, h, BG);
    let l = layout(w, h);
    let s = l.scale;
    let cw = (w * 3 / 5).min(900);
    let x = (w - cw) / 2;
    // logo and title
    let title = "RUST CLIENT";
    let ts = s * 2;
    f.text(title, (w - Frame::text_w(title, ts)) / 2, h / 12, ts, ORANGE);
    let sub = "Minecraft on the Rust engine";
    f.text(sub, (w - Frame::text_w(sub, s)) / 2, h / 12 + 20 * s, s, GREY);
    // version card
    let cy = h / 2 - 30 * s;
    f.round(x, cy, cw, 60 * s + 0, 6 * s, PANEL);
    f.text("Minecraft 26.3", x + 10 * s, cy + 6 * s, s * 2, WHITE);
    f.text("Rust (Horizon) engine, runs inside this app", x + 10 * s, cy + 26 * s, s, GOLD);
    f.text("Downloads game files on first start", x + 10 * s, cy + 38 * s, s, GREY);
    // play button
    let pc = if pressed { GOLD } else { ORANGE };
    f.round(l.play.x, l.play.y, l.play.w, l.play.h, 8 * s, pc);
    let pt = "PLAY 26.3";
    f.text(pt, l.play.x + (l.play.w - Frame::text_w(pt, s * 2)) / 2, l.play.y + (l.play.h - 16 * s) / 2, s * 2, 0xFF14110A);
    // unsupported card
    let uy = l.play.y + l.play.h + 12 * s;
    f.round(x, uy, cw, 30 * s, 6 * s, 0xFF1A160E);
    f.text("Minecraft 1.21.11", x + 10 * s, uy + 5 * s, s * 2, GREY);
    f.text("Not available on the Rust engine (26.3 only)", x + 10 * s, uy + 21 * s, s, GREY);
    if !status.is_empty() {
        f.text(status, (w - Frame::text_w(status, s)) / 2, h - 14 * s, s, 0xFFFF6B6B);
    }
}

fn launch_horizon(app: &AndroidApp) -> Result<(), String> {
    let vm = unsafe { JavaVM::from_raw(app.vm_as_ptr() as *mut _) }.map_err(|e| e.to_string())?;
    let mut env = vm.attach_current_thread().map_err(|e| e.to_string())?;
    let activity = unsafe { JObject::from_raw(app.activity_as_ptr() as jni::sys::jobject) };
    let pkg = env.call_method(&activity, "getPackageName", "()Ljava/lang/String;", &[]).map_err(|e| e.to_string())?.l().map_err(|e| e.to_string())?;
    let intent = env.new_object("android/content/Intent", "()V", &[]).map_err(|e| e.to_string())?;
    let cls = env.new_string("dev.horizon.android.HorizonActivity").map_err(|e| e.to_string())?;
    env.call_method(&intent, "setClassName", "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;", &[JValue::Object(&pkg), JValue::Object(&cls)]).map_err(|e| e.to_string())?;
    env.call_method(&activity, "startActivity", "(Landroid/content/Intent;)V", &[JValue::Object(&intent)]).map_err(|e| e.to_string())?;
    if env.exception_check().unwrap_or(false) { env.exception_clear().ok(); return Err("could not start engine".into()); }
    Ok(())
}

#[no_mangle]
fn android_main(app: AndroidApp) {
    android_logger::init_once(android_logger::Config::default().with_max_level(log::LevelFilter::Info));
    let mut has_window = false;
    let mut dirty = true;
    let mut pressed = false;
    let mut status = String::new();
    let mut quit = false;
    while !quit {
        app.poll_events(Some(Duration::from_millis(50)), |event| {
            if let PollEvent::Main(m) = event {
                match m {
                    MainEvent::InitWindow { .. } | MainEvent::WindowResized { .. } | MainEvent::RedrawNeeded { .. } | MainEvent::GainedFocus => { has_window = true; dirty = true; }
                    MainEvent::TerminateWindow { .. } => has_window = false,
                    MainEvent::Destroy => quit = true,
                    _ => {}
                }
            }
        });
        if let Ok(mut it) = app.input_events_iter() {
            let mut go = false;
            loop {
                let more = it.next(|ev| {
                    if let InputEvent::MotionEvent(m) = ev {
                        let p = m.pointer_at_index(0);
                        let (x, y) = (p.x(), p.y());
                        if let Some(win) = app.native_window() {
                            let l = layout(win.width(), win.height());
                            match m.action() {
                                MotionAction::Down => { pressed = l.play.hit(x, y); dirty = true; }
                                MotionAction::Up => { if pressed && l.play.hit(x, y) { go = true; } pressed = false; dirty = true; }
                                MotionAction::Cancel => { pressed = false; dirty = true; }
                                _ => {}
                            }
                        }
                    }
                    InputStatus::Handled
                });
                if !more { break; }
            }
            if go {
                status = match launch_horizon(&app) { Ok(()) => String::new(), Err(e) => format!("Error: {e}") };
            }
        }
        if has_window && dirty {
            if let Some(win) = app.native_window() {
                unsafe {
                    let wp = win.ptr().as_ptr();
                    let mut buf: ndk_sys::ANativeWindow_Buffer = std::mem::zeroed();
                    if ndk_sys::ANativeWindow_lock(wp, &mut buf, std::ptr::null_mut()) == 0 {
                        // 32-bit formats only (RGBA_8888 / RGBX_8888); BGR order differences are cosmetic for this palette.
                        let n = (buf.stride * buf.height) as usize;
                        let px = std::slice::from_raw_parts_mut(buf.bits as *mut u32, n);
                        let mut f = Frame { px, w: buf.width, h: buf.height, stride: buf.stride };
                        draw(&mut f, pressed, &status);
                        ndk_sys::ANativeWindow_unlockAndPost(wp);
                    }
                }
                dirty = false;
            }
        }
    }
}
