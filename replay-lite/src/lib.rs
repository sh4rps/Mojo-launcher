//! Replay Lite: records the local player's path and plays it back with /tp.
use horizon_mod_sdk::{
    chat, command,
    game::{self},
    hud::{self, Corner, Line},
    storage,
    ui::{self, ButtonWidget, ListWidget, Screen, TextWidget, Widget},
    Mod, Registration,
};

const STEP: u64 = 2; // one sample every 2 ticks = 10 per second
const MAX_SAMPLES: usize = 12_000; // 20 minutes, 240 KB
const MAGIC: &[u8; 3] = b"RL1";

const B_REC: u32 = 1;
const B_PAUSE: u32 = 2;
const B_SPEED: u32 = 3;
const B_STOP: u32 = 4;
const L_LIST: u32 = 10;
const T_STATUS: u32 = 20;
const SPEEDS: [f64; 4] = [0.5, 1.0, 2.0, 4.0];

#[derive(Clone, Copy)]
struct Sample { x: f32, y: f32, z: f32, yaw: f32, pitch: f32 }

struct Clip { name: String, dim: String, samples: Vec<Sample> }

struct Play { clip: Clip, cursor: f64, paused: bool }

struct Rec { dim: String, samples: Vec<Sample>, ticks: u64 }

struct Replay {
    rec: Option<Rec>,
    play: Option<Play>,
    speed: usize,
    names: Vec<String>,
    last_hud: String,
}

fn clean(name: &str) -> String {
    let s: String = name.trim().to_lowercase().chars()
        .map(|c| if c.is_ascii_alphanumeric() || c == '-' || c == '_' { c } else { '_' })
        .take(40).collect();
    s
}

fn key(name: &str) -> String { format!("r_{name}") }

fn encode(dim: &str, samples: &[Sample]) -> Vec<u8> {
    let mut b = Vec::with_capacity(16 + dim.len() + samples.len() * 20);
    b.extend_from_slice(MAGIC);
    b.push(dim.len().min(255) as u8);
    b.extend_from_slice(&dim.as_bytes()[..dim.len().min(255)]);
    b.extend_from_slice(&(samples.len() as u32).to_le_bytes());
    for s in samples {
        for v in [s.x, s.y, s.z, s.yaw, s.pitch] { b.extend_from_slice(&v.to_le_bytes()); }
    }
    b
}

fn decode(name: &str, b: &[u8]) -> Option<Clip> {
    if b.len() < 8 || &b[..3] != MAGIC { return None; }
    let dl = b[3] as usize;
    let dim = String::from_utf8(b.get(4..4 + dl)?.to_vec()).ok()?;
    let p = 4 + dl;
    let n = u32::from_le_bytes(b.get(p..p + 4)?.try_into().ok()?) as usize;
    let mut o = p + 4;
    if b.len() < o + n * 20 { return None; }
    let mut samples = Vec::with_capacity(n);
    let mut f = || { let v = f32::from_le_bytes(b[o..o + 4].try_into().unwrap()); o += 4; v };
    for _ in 0..n { samples.push(Sample { x: f(), y: f(), z: f(), yaw: f(), pitch: f() }); }
    Some(Clip { name: name.to_string(), dim, samples })
}

fn mmss(samples: usize) -> String {
    let s = samples as u64 * STEP / 20;
    format!("{}:{:02}", s / 60, s % 60)
}

/// Shortest-way interpolation of an angle in degrees.
fn lerp_angle(a: f32, b: f32, t: f32) -> f32 {
    let mut d = (b - a) % 360.0;
    if d > 180.0 { d -= 360.0 } else if d < -180.0 { d += 360.0 }
    a + d * t
}

impl Replay {
    fn save_index(&self) { let _ = storage::set("index", self.names.join("\n").as_bytes()); }

    fn load(&self, name: &str) -> Option<Clip> {
        decode(name, &storage::get(&key(name))?)
    }

    fn start_rec(&mut self) -> Result<String, String> {
        if self.play.is_some() { return Err("Stop the playback first".into()); }
        let p = game::get_player().ok_or("Not in a world")?;
        self.rec = Some(Rec { dim: p.dimension, samples: Vec::new(), ticks: 0 });
        Ok("Recording started. /replay stop to save.".into())
    }

    fn stop_rec(&mut self, name: Option<&str>) -> Result<String, String> {
        let r = self.rec.take().ok_or("Not recording")?;
        if r.samples.len() < 2 { return Err("Too short, nothing saved".into()); }
        let mut n = name.map(clean).filter(|s| !s.is_empty()).unwrap_or_default();
        if n.is_empty() {
            let mut i = 1;
            while self.names.contains(&format!("rec{i}")) { i += 1; }
            n = format!("rec{i}");
        }
        if !self.names.contains(&n) && self.names.len() >= 60 { return Err("Too many recordings, delete one".into()); }
        storage::set(&key(&n), &encode(&r.dim, &r.samples))?;
        if !self.names.contains(&n) { self.names.push(n.clone()); self.save_index(); }
        Ok(format!("Saved \"{n}\" ({})", mmss(r.samples.len())))
    }

    fn start_play(&mut self, name: &str) -> Result<String, String> {
        if self.rec.is_some() { return Err("Stop the recording first".into()); }
        let name = clean(name);
        let clip = self.load(&name).ok_or("No such recording")?;
        let p = game::get_player().ok_or("Not in a world")?;
        if p.dimension != clip.dim { return Err(format!("Recorded in {}; go there first", clip.dim)); }
        let first = clip.samples[0];
        command::run_quiet(&[tp_line(first)]);
        self.play = Some(Play { clip, cursor: 0.0, paused: false });
        Ok(format!("Playing \"{name}\". Needs cheats; /replay stop to end."))
    }

    fn delete(&mut self, name: &str) -> Result<String, String> {
        let name = clean(name);
        if !self.names.contains(&name) { return Err("No such recording".into()); }
        storage::delete(&key(&name));
        self.names.retain(|n| *n != name);
        self.save_index();
        Ok(format!("Deleted \"{name}\""))
    }

    fn widgets(&self) -> Vec<Widget> {
        let status = if let Some(r) = &self.rec { format!("Recording {}", mmss(r.samples.len())) }
        else if let Some(p) = &self.play { format!("{} \"{}\" {} / {}", if p.paused { "Paused" } else { "Playing" }, p.clip.name, mmss(p.cursor as usize), mmss(p.clip.samples.len())) }
        else { "Idle".to_string() };
        let mut w = vec![
            Widget::Text(TextWidget { id: T_STATUS, text: status, color: 0xFFB070 }),
            Widget::Button(ButtonWidget { id: B_REC, label: if self.rec.is_some() { "Stop and save recording".into() } else { "Start recording".into() } }),
        ];
        if self.play.is_some() {
            w.push(Widget::Button(ButtonWidget { id: B_PAUSE, label: if self.play.as_ref().is_some_and(|p| p.paused) { "Resume".into() } else { "Pause".into() } }));
            w.push(Widget::Button(ButtonWidget { id: B_STOP, label: "Stop playback".into() }));
        }
        w.push(Widget::Button(ButtonWidget { id: B_SPEED, label: format!("Speed: {}x", SPEEDS[self.speed]) }));
        w.push(Widget::Text(TextWidget { id: 21, text: "Pick a recording to play it. Delete: /replay delete <name>".into(), color: 0 }));
        w.push(Widget::List(ListWidget {
            id: L_LIST,
            rows: self.names.iter().map(|n| n.clone()).collect(),
            selected: None,
        }));
        w
    }

    fn refresh(&self) { ui::set_settings(&self.widgets()); }

    fn say(&self, r: Result<String, String>) {
        match r { Ok(m) => chat::show(&m), Err(e) => chat::show(&format!("Replay: {e}")) }
        self.refresh();
    }
}

fn tp_line(s: Sample) -> String {
    format!("tp @s {:.3} {:.3} {:.3} {:.2} {:.2}", s.x, s.y, s.z, s.yaw, s.pitch)
}

impl Mod for Replay {
    fn init() -> Result<(Self, Registration), String> {
        let names: Vec<String> = storage::get("index")
            .and_then(|b| String::from_utf8(b).ok())
            .map(|s| s.lines().filter(|l| !l.is_empty()).map(String::from).collect())
            .unwrap_or_default();
        let me = Replay { rec: None, play: None, speed: 1, names, last_hud: String::new() };
        Ok((me, Registration::new().command("replay").on_tick()))
    }

    fn tick(&mut self, tick: u64) {
        if let Some(r) = &mut self.rec {
            r.ticks += 1;
            if tick % STEP == 0 {
                match game::get_player() {
                    Some(p) if p.dimension == r.dim => {
                        r.samples.push(Sample { x: p.x as f32, y: p.y as f32, z: p.z as f32, yaw: p.yaw, pitch: p.pitch });
                    }
                    _ => {}
                }
                if r.samples.len() >= MAX_SAMPLES {
                    let res = self.stop_rec(None);
                    self.say(res);
                }
            }
        }
        if let Some(p) = &mut self.play {
            if !p.paused {
                p.cursor += SPEEDS[self.speed] / STEP as f64;
                let last = (p.clip.samples.len() - 1) as f64;
                if p.cursor >= last {
                    command::run_quiet(&[tp_line(p.clip.samples[last as usize])]);
                    let name = p.clip.name.clone();
                    self.play = None;
                    chat::show(&format!("Replay: finished \"{name}\""));
                } else {
                    let i = p.cursor.floor() as usize;
                    let t = (p.cursor - i as f64) as f32;
                    let (a, b) = (p.clip.samples[i], p.clip.samples[i + 1]);
                    let s = Sample {
                        x: a.x + (b.x - a.x) * t, y: a.y + (b.y - a.y) * t, z: a.z + (b.z - a.z) * t,
                        yaw: lerp_angle(a.yaw, b.yaw, t), pitch: a.pitch + (b.pitch - a.pitch) * t,
                    };
                    command::run_quiet(&[tp_line(s)]);
                }
            }
        }
        // HUD, updated once a second.
        if tick % 20 == 0 {
            let text = if let Some(r) = &self.rec { Some((format!("REC {}", mmss(r.samples.len())), 0xFF4040)) }
            else if let Some(p) = &self.play { Some((format!("PLAY {} {} / {}", p.clip.name, mmss(p.cursor as usize), mmss(p.clip.samples.len())), 0x60FF80)) }
            else { None };
            match text {
                Some((t, c)) => { if t != self.last_hud { hud::set_panel(Corner::TopRight, &[Line { text: t.clone(), color: c }]); self.last_hud = t; } }
                None => { if !self.last_hud.is_empty() { hud::clear(); self.last_hud.clear(); } }
            }
        }
    }

    fn command(&mut self, _name: &str, args: &str) -> Result<Option<String>, String> {
        let mut it = args.split_whitespace();
        let sub = it.next().unwrap_or("");
        let rest: String = it.collect::<Vec<_>>().join("_");
        match sub {
            "" | "menu" => { ui::open_screen(&Screen { title: "Replay Lite".into(), widgets: self.widgets() }); Ok(None) }
            "rec" | "record" => { let r = self.start_rec(); self.say(r); Ok(None) }
            "stop" => {
                if self.rec.is_some() { let r = self.stop_rec(if rest.is_empty() { None } else { Some(&rest) }); self.say(r); }
                else if self.play.is_some() { self.play = None; self.say(Ok("Playback stopped".into())); }
                else { return Err("Nothing to stop".into()); }
                Ok(None)
            }
            "play" => { let r = self.start_play(&rest); self.say(r); Ok(None) }
            "pause" => { if let Some(p) = &mut self.play { p.paused = !p.paused; self.refresh(); Ok(Some(if p.paused { "Paused".into() } else { "Resumed".into() })) } else { Err("Not playing".into()) } }
            "speed" => { self.speed = (self.speed + 1) % SPEEDS.len(); self.refresh(); Ok(Some(format!("Speed {}x", SPEEDS[self.speed]))) }
            "delete" => { let r = self.delete(&rest); self.say(r); Ok(None) }
            "list" => Ok(Some(if self.names.is_empty() { "No recordings".into() } else { self.names.join(", ") })),
            _ => Err("/replay [rec | stop [name] | play <name> | pause | speed | delete <name> | list]".into()),
        }
    }

    fn ui_select(&mut self, id: u32, row: u32) {
        match id {
            L_LIST => { if let Some(n) = self.names.get(row as usize).cloned() { let r = self.start_play(&n); self.say(r); } }
            B_REC => { let r = if self.rec.is_some() { self.stop_rec(None) } else { self.start_rec() }; self.say(r); }
            B_PAUSE => { if let Some(p) = &mut self.play { p.paused = !p.paused; } self.refresh(); }
            B_STOP => { self.play = None; self.say(Ok("Playback stopped".into())); }
            B_SPEED => { self.speed = (self.speed + 1) % SPEEDS.len(); self.refresh(); }
            _ => {}
        }
    }

    fn ui_settings_opened(&mut self) { self.refresh(); }
}

horizon_mod_sdk::export_mod!(Replay);
