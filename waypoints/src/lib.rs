//! Waypoints: a Xaero-style waypoint system for Horizon.
use horizon_mod_sdk::{
    chat, command, game,
    render::{self, Point, QuadShape, Rect, FillShape, Shape, TextShape},
    storage,
    ui::{self, ButtonWidget, ListWidget, Screen, TextWidget, Widget},
    Mod, Registration,
};

const OVERWORLD: &str = "minecraft:overworld";
const NETHER: &str = "minecraft:the_nether";
const MAX_WP: usize = 100;
const COLORS: [(u32, &str); 16] = [
    (0x000000, "Black"), (0x0000AA, "Dark Blue"), (0x00AA00, "Dark Green"), (0x00AAAA, "Dark Aqua"),
    (0xAA0000, "Dark Red"), (0xAA00AA, "Purple"), (0xFFAA00, "Gold"), (0xAAAAAA, "Gray"),
    (0x555555, "Dark Gray"), (0x5555FF, "Blue"), (0x55FF55, "Green"), (0x55FFFF, "Aqua"),
    (0xFF5555, "Red"), (0xFF55FF, "Light Purple"), (0xFFFF55, "Yellow"), (0xFFFFFF, "White"),
];

#[derive(Clone)]
struct Wp { name: String, x: f64, y: f64, z: f64, dim: String, color: usize, on: bool }

#[derive(PartialEq, Clone, Copy)]
enum View { Main, Detail(usize) }

struct State {
    wps: Vec<Wp>,
    markers: bool,
    compass: bool,
    fov: f32,
    view: View,
    last: Option<(f64, f64, f64, String)>,
    was_dead: bool,
    drawn: bool,
}

const B_ADD: u32 = 1;
const B_MARKERS: u32 = 2;
const B_COMPASS: u32 = 3;
const L_LIST: u32 = 10;
const B_TP: u32 = 20;
const B_TOGGLE: u32 = 21;
const B_COLOR: u32 = 22;
const B_DELETE: u32 = 23;
const B_BACK: u32 = 24;

fn clean_name(s: &str) -> String {
    s.chars().filter(|c| !c.is_control() && *c != '|').take(32).collect::<String>().trim().to_string()
}

fn dim_label(d: &str) -> &str {
    match d { OVERWORLD => "Overworld", NETHER => "Nether", "minecraft:the_end" => "End", o => o }
}

fn convert(wp: &Wp, player_dim: &str) -> Option<(f64, f64, f64)> {
    if wp.dim == player_dim { return Some((wp.x, wp.y, wp.z)); }
    match (wp.dim.as_str(), player_dim) {
        (OVERWORLD, NETHER) => Some((wp.x / 8.0, wp.y, wp.z / 8.0)),
        (NETHER, OVERWORLD) => Some((wp.x * 8.0, wp.y, wp.z * 8.0)),
        _ => None,
    }
}

fn dist_text(d: f64) -> String {
    if d >= 1000.0 { format!("{:.1}km", d / 1000.0) } else { format!("{}m", d.round() as i64) }
}

impl State {
    fn load() -> Self {
        let mut wps = Vec::new();
        if let Some(s) = storage::get("wps").and_then(|b| String::from_utf8(b).ok()) {
            for l in s.lines() {
                let f: Vec<&str> = l.split('|').collect();
                if f.len() == 7 {
                    if let (Ok(x), Ok(y), Ok(z), Ok(c)) = (f[1].parse(), f[2].parse(), f[3].parse(), f[5].parse::<usize>()) {
                        wps.push(Wp { name: f[0].into(), x, y, z, dim: f[4].into(), color: c % 16, on: f[6] == "1" });
                    }
                }
            }
        }
        let flag = |k: &str, d: bool| storage::get(k).map(|v| v == b"1").unwrap_or(d);
        let fov = storage::get("fov").and_then(|v| String::from_utf8(v).ok()).and_then(|s| s.parse().ok()).unwrap_or(70.0);
        State { wps, markers: flag("markers", true), compass: flag("compass", true), fov, view: View::Main, last: None, was_dead: false, drawn: false }
    }

    fn save(&self) {
        let s: Vec<String> = self.wps.iter().map(|w| format!("{}|{}|{}|{}|{}|{}|{}", w.name, w.x, w.y, w.z, w.dim, w.color, if w.on { 1 } else { 0 })).collect();
        let _ = storage::set("wps", s.join("\n").as_bytes());
    }
    fn save_flags(&self) {
        let _ = storage::set("markers", if self.markers { b"1" } else { b"0" });
        let _ = storage::set("compass", if self.compass { b"1" } else { b"0" });
        let _ = storage::set("fov", self.fov.to_string().as_bytes());
    }

    fn find(&self, name: &str) -> Option<usize> {
        let n = name.trim().to_lowercase();
        self.wps.iter().position(|w| w.name.to_lowercase() == n)
    }

    fn add(&mut self, name: &str, pos: Option<(f64, f64, f64)>, dim: Option<String>) -> Result<String, String> {
        if self.wps.len() >= MAX_WP { return Err("Too many waypoints (100)".into()); }
        let p = game::get_player();
        let (x, y, z) = match pos { Some(v) => v, None => { let p = p.as_ref().ok_or("Not in a world")?; (p.x.floor() + 0.5, p.y.floor(), p.z.floor() + 0.5) } };
        let dim = dim.or(p.map(|p| p.dimension)).ok_or("Not in a world")?;
        let mut name = clean_name(name);
        if name.is_empty() { let mut i = 1; while self.find(&format!("Waypoint {i}")).is_some() { i += 1; } name = format!("Waypoint {i}"); }
        if self.find(&name).is_some() { return Err(format!("\"{name}\" already exists")); }
        let color = self.wps.len() % 16;
        let color = if color == 0 { 12 } else { color };
        self.wps.push(Wp { name: name.clone(), x, y, z, dim, color, on: true });
        self.save();
        Ok(format!("Added \"{name}\" at {} {} {}", x.floor(), y.floor(), z.floor()))
    }

    fn teleport(&self, i: usize) -> Result<String, String> {
        let p = game::get_player().ok_or("Not in a world")?;
        let w = self.wps.get(i).ok_or("No such waypoint")?;
        if w.dim != p.dimension { return Err(format!("\"{}\" is in the {}", w.name, dim_label(&w.dim))); }
        command::run(&format!("tp @s {:.2} {:.2} {:.2}", w.x, w.y, w.z));
        Ok(format!("Teleporting to \"{}\" (needs cheats)", w.name))
    }

    fn widgets(&self) -> Vec<Widget> {
        match self.view {
            View::Main => {
                let mut w = vec![
                    Widget::Button(ButtonWidget { id: B_ADD, label: "Add waypoint here".into() }),
                    Widget::Button(ButtonWidget { id: B_MARKERS, label: format!("In-world markers: {}", if self.markers { "ON" } else { "OFF" }) }),
                    Widget::Button(ButtonWidget { id: B_COMPASS, label: format!("Compass strip: {}", if self.compass { "ON" } else { "OFF" }) }),
                    Widget::Text(TextWidget { id: 30, text: "Pick a waypoint for options. Name one: /wp add <name> [x y z]".into(), color: 0 }),
                ];
                w.push(Widget::List(ListWidget {
                    id: L_LIST,
                    rows: self.wps.iter().map(|p| format!("{}{}  {} {} {}  ({})", if p.on { "" } else { "[hidden] " }, p.name, p.x.floor(), p.y.floor(), p.z.floor(), dim_label(&p.dim))).collect(),
                    selected: None,
                }));
                w
            }
            View::Detail(i) => match self.wps.get(i) {
                Some(p) => vec![
                    Widget::Text(TextWidget { id: 31, text: format!("{}  {} {} {}  ({})", p.name, p.x.floor(), p.y.floor(), p.z.floor(), dim_label(&p.dim)), color: COLORS[p.color].0 }),
                    Widget::Button(ButtonWidget { id: B_TP, label: "Teleport".into() }),
                    Widget::Button(ButtonWidget { id: B_TOGGLE, label: if p.on { "Hide".into() } else { "Show".into() } }),
                    Widget::Button(ButtonWidget { id: B_COLOR, label: format!("Colour: {}", COLORS[p.color].1) }),
                    Widget::Button(ButtonWidget { id: B_DELETE, label: "Delete".into() }),
                    Widget::Button(ButtonWidget { id: B_BACK, label: "Back".into() }),
                ],
                None => vec![Widget::Button(ButtonWidget { id: B_BACK, label: "Back".into() })],
            },
        }
    }
    fn open(&self) { ui::open_screen(&Screen { title: "Waypoints".into(), widgets: self.widgets() }); }
    fn refresh(&self) { ui::open_screen(&Screen { title: "Waypoints".into(), widgets: self.widgets() }); ui::set_settings(&self.widgets()); }

    fn draw(&mut self) {
        let Some(p) = game::get_player() else {
            if self.drawn { render::set_hud(&[]); self.drawn = false; }
            return;
        };
        let (w, h) = render::gui_size();
        let mut shapes: Vec<Shape> = Vec::new();
        let yaw = (p.yaw as f64).to_radians();
        let pitch = (p.pitch as f64).to_radians();
        let f = (-yaw.sin() * pitch.cos(), -pitch.sin(), yaw.cos() * pitch.cos());
        let r = (-yaw.cos(), 0.0, -yaw.sin());
        let u = (r.1 * f.2 - r.2 * f.1, r.2 * f.0 - r.0 * f.2, r.0 * f.1 - r.1 * f.0);
        let tv = ((self.fov as f64).to_radians() / 2.0).tan();
        let th = tv * (w as f64 / h as f64);
        let eye = (p.x, p.y + 1.62, p.z);
        let mut marks: Vec<(f64, f32, f32, usize, String)> = Vec::new();
        let cx = (w / 2.0).round();
        if self.compass {
            shapes.push(Shape::Fill(FillShape { rect: Rect { x: cx - 121.0, y: 3.0, w: 242.0, h: 13.0 }, color: 0x60000000 }));
            shapes.push(Shape::Fill(FillShape { rect: Rect { x: cx - 0.5, y: 3.0, w: 1.0, h: 13.0 }, color: 0xA0FFFFFF }));
        }
        for wp in self.wps.iter().filter(|w| w.on) {
            let Some((x, y, z)) = convert(wp, &p.dimension) else { continue };
            let (dx, dy, dz) = (x - eye.0, y + 0.5 - eye.1, z - eye.2);
            let dist = (dx * dx + dy * dy + dz * dz).sqrt();
            let color = COLORS[wp.color].0;
            if self.compass {
                let to = (-dx).atan2(dz).to_degrees();
                let mut d = (to - p.yaw as f64) % 360.0;
                if d > 180.0 { d -= 360.0 } else if d < -180.0 { d += 360.0 }
                if d.abs() < 90.0 {
                    let px = cx + (d as f32) * (120.0 / 90.0);
                    shapes.push(Shape::Fill(FillShape { rect: Rect { x: px - 3.0, y: 5.0, w: 6.0, h: 6.0 }, color: 0xFF000000 | color }));
                    let ini: String = wp.name.chars().next().map(|c| c.to_uppercase().to_string()).unwrap_or_default();
                    shapes.push(Shape::Text(TextShape { text: ini, at: Point { x: px, y: 17.0 }, color: 0xFFFFFF, centered: true, shadow: true }));
                }
            }
            if self.markers {
                let depth = dx * f.0 + dy * f.1 + dz * f.2;
                if depth > 0.5 {
                    let nx = (dx * r.0 + dy * r.1 + dz * r.2) / depth / th;
                    let ny = (dx * u.0 + dy * u.1 + dz * u.2) / depth / tv;
                    if nx.abs() < 1.0 && ny.abs() < 1.0 {
                        marks.push((dist, ((1.0 + nx) as f32) * w / 2.0, ((1.0 - ny) as f32) * h / 2.0, wp.color, format!("{} {}", wp.name, dist_text(dist))));
                    }
                }
            }
        }
        marks.sort_by(|a, b| b.0.partial_cmp(&a.0).unwrap_or(std::cmp::Ordering::Equal));
        for (_, x, y, c, label) in marks {
            let col = 0xFF000000 | COLORS[c].0;
            let s = 5.0;
            shapes.push(Shape::Quad(QuadShape { corners: (Point { x, y: y - s }, Point { x: x + s, y }, Point { x, y: y + s }, Point { x: x - s, y }), color: col }));
            shapes.push(Shape::Text(TextShape { text: label, at: Point { x, y: y + 8.0 }, color: COLORS[c].0.max(0x303030), centered: true, shadow: true }));
        }
        if shapes.is_empty() && !self.drawn { return; }
        self.drawn = !shapes.is_empty();
        shapes.truncate(1024);
        render::set_hud(&shapes);
    }

    fn death_check(&mut self) {
        match game::get_player() {
            Some(p) => {
                if p.health <= 0.0 {
                    if !self.was_dead {
                        self.was_dead = true;
                        if let Some((x, y, z, dim)) = self.last.clone() {
                            if let Some(i) = self.find("Death") { self.wps.remove(i); }
                            if self.wps.len() < MAX_WP {
                                self.wps.push(Wp { name: "Death".into(), x, y, z, dim, color: 4, on: true });
                                self.save();
                                chat::show(&format!("Waypoints: death point saved at {} {} {}", x.floor(), y.floor(), z.floor()));
                            }
                        }
                    }
                } else {
                    self.was_dead = false;
                    self.last = Some((p.x, p.y, p.z, p.dimension));
                }
            }
            None => {}
        }
    }
}

fn parse_name_pos(args: &[&str]) -> (String, Option<(f64, f64, f64)>) {
    if args.len() >= 3 {
        let n = args.len();
        if let (Ok(x), Ok(y), Ok(z)) = (args[n - 3].parse::<f64>(), args[n - 2].parse::<f64>(), args[n - 1].parse::<f64>()) {
            return (args[..n - 3].join(" "), Some((x + 0.5, y, z + 0.5)));
        }
    }
    (args.join(" "), None)
}

impl Mod for State {
    fn init() -> Result<(Self, Registration), String> {
        Ok((State::load(), Registration::new().command("wp").command("waypoint").on_tick()))
    }

    fn tick(&mut self, _t: u64) {
        self.death_check();
        self.draw();
    }

    fn command(&mut self, _n: &str, args: &str) -> Result<Option<String>, String> {
        let toks: Vec<&str> = args.split_whitespace().collect();
        let sub = toks.first().copied().unwrap_or("");
        let rest = &toks[toks.len().min(1)..];
        let name = rest.join(" ");
        let idx = |s: &State, n: &str| s.find(n).ok_or_else(|| format!("No waypoint \"{n}\""));
        match sub {
            "" | "menu" => { self.view = View::Main; self.open(); Ok(None) }
            "add" | "new" | "set" => { let (n, pos) = parse_name_pos(rest); Ok(Some(self.add(&n, pos, None)?)) }
            "remove" | "delete" | "del" => { let i = idx(self, &name)?; let w = self.wps.remove(i); self.save(); Ok(Some(format!("Removed \"{}\"", w.name))) }
            "list" => Ok(Some(if self.wps.is_empty() { "No waypoints".into() } else { self.wps.iter().map(|w| format!("{} ({} {} {})", w.name, w.x.floor(), w.y.floor(), w.z.floor())).collect::<Vec<_>>().join(", ") })),
            "tp" | "go" => { let i = idx(self, &name)?; Ok(Some(self.teleport(i)?)) }
            "hide" | "show" | "toggle" => { let i = idx(self, &name)?; self.wps[i].on = !self.wps[i].on; self.save(); Ok(Some(format!("\"{}\" {}", self.wps[i].name, if self.wps[i].on { "shown" } else { "hidden" }))) }
            "color" | "colour" => {
                let (n, c) = rest.split_last().ok_or("/wp color <name> <0-15>")?;
                let c: usize = c.parse().map_err(|_| "Colour is a number 0-15")?;
                let _ = n; // name is everything before the number
                let nm = rest[..rest.len() - 1].join(" ");
                let i = idx(self, &nm)?;
                if c > 15 { return Err("Colour is a number 0-15".into()); }
                self.wps[i].color = c; self.save();
                Ok(Some(format!("\"{}\" is now {}", self.wps[i].name, COLORS[c].1)))
            }
            "rename" => {
                let (a, b) = name.split_once('>').ok_or("/wp rename <old> > <new>")?;
                let i = idx(self, a)?;
                let nn = clean_name(b);
                if nn.is_empty() || (self.find(&nn).is_some() && self.find(&nn) != Some(i)) { return Err("Bad or taken name".into()); }
                self.wps[i].name = nn.clone(); self.save();
                Ok(Some(format!("Renamed to \"{nn}\"")))
            }
            "share" => {
                let i = idx(self, &name)?; let w = &self.wps[i];
                command::run(&format!("say [Waypoint] {} {} {} {} ({})", w.name, w.x.floor(), w.y.floor(), w.z.floor(), dim_label(&w.dim)));
                Ok(None)
            }
            "markers" => { self.markers = !self.markers; self.save_flags(); Ok(Some(format!("In-world markers {}", if self.markers { "on" } else { "off" }))) }
            "compass" => { self.compass = !self.compass; self.save_flags(); Ok(Some(format!("Compass strip {}", if self.compass { "on" } else { "off" }))) }
            "fov" => {
                let v: f32 = name.parse().map_err(|_| "/wp fov <30-110>: your game's field of view")?;
                if !(30.0..=110.0).contains(&v) { return Err("FOV must be 30-110".into()); }
                self.fov = v; self.save_flags(); Ok(Some(format!("FOV set to {v}")))
            }
            _ => Err("/wp [add <name> [x y z] | remove | list | tp | hide | color <name> <0-15> | rename a > b | share | markers | compass | fov <n>]".into()),
        }
    }

    fn ui_select(&mut self, id: u32, row: u32) {
        let mut close = false;
        match id {
            L_LIST => { if (row as usize) < self.wps.len() { self.view = View::Detail(row as usize); } }
            B_ADD => { match self.add("", None, None) { Ok(m) => chat::show(&m), Err(e) => chat::show(&format!("Waypoints: {e}")) } }
            B_MARKERS => { self.markers = !self.markers; self.save_flags(); }
            B_COMPASS => { self.compass = !self.compass; self.save_flags(); }
            B_BACK => self.view = View::Main,
            B_TP => { if let View::Detail(i) = self.view { match self.teleport(i) { Ok(m) => chat::show(&m), Err(e) => chat::show(&format!("Waypoints: {e}")) } close = true; } }
            B_TOGGLE => { if let View::Detail(i) = self.view { if let Some(w) = self.wps.get_mut(i) { w.on = !w.on; } self.save(); } }
            B_COLOR => { if let View::Detail(i) = self.view { if let Some(w) = self.wps.get_mut(i) { w.color = (w.color + 1) % 16; } self.save(); } }
            B_DELETE => { if let View::Detail(i) = self.view { if i < self.wps.len() { self.wps.remove(i); self.save(); } self.view = View::Main; } }
            _ => {}
        }
        if close { ui::close_screen(); } else { self.refresh(); }
    }

    fn ui_settings_opened(&mut self) { self.view = View::Main; ui::set_settings(&self.widgets()); }
}

horizon_mod_sdk::export_mod!(State);
