#!/usr/bin/env python3
"""Build 3 admin-dashboard mockups (A light, B dark, C glass) + render PNGs."""
import os, subprocess

ROOT = "/Users/draxbarroga/Git/full-stack-projects/secphils/sketches/admin_dashboard"
os.makedirs(ROOT, exist_ok=True)

BASE_CSS = """
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:-apple-system,BlinkMacSystemFont,'Inter','Segoe UI',sans-serif;font-size:14px}
.app{display:flex;min-height:100vh}
.side{width:150px;flex-shrink:0;display:flex;flex-direction:column;background:var(--side);border-right:1px solid var(--side-bd)}
.side .logo{padding:16px 14px 14px;display:flex;align-items:center;gap:8px;font-weight:800;font-size:15px;color:var(--side-txt)}
.side .logo i{width:26px;height:26px;border-radius:8px;background:#29ca8e;display:flex;align-items:center;justify-content:center;color:#fff;font-size:12px}
.side .sec{font-size:10px;font-weight:700;letter-spacing:.12em;text-transform:uppercase;color:var(--side-dim);padding:12px 16px 6px}
.side a{display:flex;align-items:center;gap:9px;padding:8px 12px;margin:1px 8px;border-radius:8px;color:var(--side-txt);font-weight:500;font-size:13px;text-decoration:none}
.side a i{width:16px;text-align:center;font-size:13px;color:var(--side-dim)}
.side a.on{background:var(--active-bg);color:var(--active-txt)}
.side a.on i{color:var(--active-ico)}
.side .foot{margin-top:auto;padding:12px;border-top:1px solid var(--side-bd);font-size:11px;color:var(--side-dim)}
.side .user{display:flex;align-items:center;gap:8px;padding:10px 14px;font-weight:600;font-size:12.5px;color:var(--side-txt)}
.side .user .av{width:28px;height:28px;border-radius:50%;background:linear-gradient(135deg,#29ca8e,#0e9f6e);color:#fff;display:flex;align-items:center;justify-content:center;font-weight:700;font-size:11px}
.main{flex:1;background:var(--bg);padding:22px 26px 30px;min-width:0}
.pagehead{display:flex;align-items:flex-end;justify-content:space-between;margin-bottom:18px}
.pagehead h1{font-size:22px;font-weight:800;color:var(--t1);letter-spacing:-.02em}
.pagehead p{font-size:12.5px;color:var(--t3);margin-top:3px}
.card{background:var(--card);border:1px solid var(--card-bd);border-radius:14px;padding:16px;box-shadow:var(--card-sh)}
.card h2{font-size:13.5px;font-weight:700;color:var(--t1);display:flex;align-items:center;gap:8px}
.card h2 i{font-size:13px;color:var(--accent)}
.card h2 .sub{margin-left:auto;font-size:11px;font-weight:500;color:var(--t3)}
.grid{display:grid;grid-template-columns:repeat(12,1fr);gap:16px}
.c3{grid-column:span 3}.c4{grid-column:span 4}.c5{grid-column:span 5}.c6{grid-column:span 6}.c7{grid-column:span 7}.c9{grid-column:span 9}
.tile{display:flex;align-items:center;gap:11px;border:1px solid var(--tile-bd);background:var(--tile-bg);border-radius:12px;padding:13px 14px}
.tile .ic{width:40px;height:40px;border-radius:11px;display:flex;align-items:center;justify-content:center;font-size:16px}
.tile .lbl{font-size:12px;color:var(--t3);font-weight:600}
.tile .val{font-size:19px;font-weight:800;color:var(--t1);margin-top:2px;line-height:1}
.donut{position:relative;width:130px;height:130px}
.donut svg{transform:rotate(-90deg)}
.donut .ctr{position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center}
.donut .ctr b{font-size:26px;font-weight:800;color:var(--t1);line-height:1}
.donut .ctr span{font-size:10.5px;color:var(--t3);margin-top:3px;font-weight:600}
.legend{display:flex;flex-direction:column;gap:9px;justify-content:center}
.legend .row{display:flex;align-items:center;gap:8px;font-size:12px;color:var(--t2)}
.legend .row .dot{width:9px;height:9px;border-radius:3px;flex-shrink:0}
.legend .row .v{margin-left:auto;font-weight:700;color:var(--t1)}
.big{font-size:34px;font-weight:800;color:var(--t1);letter-spacing:-.03em;line-height:1}
.mini{font-size:11.5px;color:var(--t3);margin-top:8px;line-height:1.5}
.mini b{color:var(--t2);font-weight:700}
.sysrow{display:flex;align-items:center;gap:9px;padding:9px 0;border-bottom:1px solid var(--card-bd);font-size:12.5px;color:var(--t2)}
.sysrow:last-child{border-bottom:0}
.sysrow .pd{width:8px;height:8px;border-radius:50%;background:#10b981;box-shadow:0 0 0 3px rgba(16,185,129,.18)}
.sysrow .up{margin-left:auto;font-size:11px;color:var(--t3);font-weight:600}
.timeline{display:flex;flex-direction:column}
.ev{display:flex;gap:11px;padding:9px 0;border-bottom:1px solid var(--card-bd);font-size:12.5px}
.ev:last-child{border-bottom:0}
.ev .ic{width:30px;height:30px;border-radius:9px;flex-shrink:0;display:flex;align-items:center;justify-content:center;font-size:12px}
.ev .t{color:var(--t2);line-height:1.45}
.ev .t b{color:var(--t1);font-weight:700}
.ev .w{margin-left:auto;font-size:11px;color:var(--t3);white-space:nowrap;padding-top:2px}
.pulse{display:inline-block;width:8px;height:8px;border-radius:50%;background:#10b981;animation:pulse 2s infinite}
@keyframes pulse{0%{box-shadow:0 0 0 0 rgba(16,185,129,.5)}70%{box-shadow:0 0 0 8px rgba(16,185,129,0)}100%{box-shadow:0 0 0 0 rgba(16,185,129,0)}}
.livebadge{display:flex;align-items:center;gap:7px;font-size:11.5px;font-weight:700;color:#10b981;background:rgba(16,185,129,.1);border:1px solid rgba(16,185,129,.25);padding:6px 12px;border-radius:999px}
"""

SIDEBAR = """
<aside class="side">
  <div class="logo"><i><svg width="13" height="13" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2l8.5 5v10L12 22l-8.5-5V7L12 2z"/></svg></i>SECPhils</div>
  <div class="sec">Workspace</div>
  <a href="#"><i class="fa-solid fa-folder-tree"></i>Projects</a>
  <a href="#"><i class="fa-solid fa-file-lines"></i>Documents</a>
  <a href="#"><i class="fa-regular fa-message"></i>Messages</a>
  <a href="#"><i class="fa-solid fa-users"></i>Teams &amp; Roles</a>
  <div class="sec">Configure</div>
  <a href="#"><i class="fa-solid fa-tags"></i>Service Catalog</a>
  <a href="#" class="on"><i class="fa-solid fa-sliders"></i>Admin Settings</a>
  <a href="#"><i class="fa-regular fa-circle-user"></i>Profile</a>
  <div class="user"><span class="av">JB</span>Jayson Barroga</div>
  <div class="foot">v2.4.1 &middot; All systems&nbsp;go</div>
</aside>"""

HEALTH = """
<div class="card c3">
  <h2><i class="fa-solid fa-heart-pulse"></i>System Health<span class="livebadge" style="padding:3px 9px;font-size:10px">LIVE</span></h2>
  <div style="margin-top:8px">
    <div class="sysrow"><span class="pd"></span>API Server<span class="up">up 5h</span></div>
    <div class="sysrow"><span class="pd"></span>Web Portal<span class="up">up 31m</span></div>
    <div class="sysrow"><span class="pd"></span>Database<span class="up">up 12d</span></div>
  </div>
</div>"""

DOCS = """
<div class="card c3">
  <h2><i class="fa-solid fa-file-lines"></i>Documents</h2>
  <div class="tile" style="margin-top:12px;background:none;border-color:var(--tile-bd)">
    <span class="ic" style="background:#ecfdf5;color:#059669"><i class="fa-solid fa-file-arrow-up"></i></span>
    <div><div class="lbl">Total files</div><div class="val">1</div></div>
  </div>
  <div class="tile" style="margin-top:8px">
    <span class="ic" style="background:#fef3c7;color:#b45309"><i class="fa-solid fa-trash-can"></i></span>
    <div><div class="lbl">In trash</div><div class="val">0</div></div>
  </div>
  <div class="tile" style="margin-top:8px">
    <span class="ic" style="background:#eef2ff;color:#4f46e5"><i class="fa-solid fa-database"></i></span>
    <div><div class="lbl">Storage used</div><div class="val">0 KB</div></div>
  </div>
</div>"""

ACTIVITY = """
<div class="card c9">
  <h2><i class="fa-solid fa-clock-rotate-left"></i>Recent Activity<span class="sub">last 30 min</span></h2>
  <div class="timeline" style="margin-top:4px">
    <div class="ev"><span class="ic" style="background:#f1f5f9;color:#64748b"><i class="fa-solid fa-right-to-bracket"></i></span><div class="t"><b>User login</b> &mdash; jayson@secphils.com</div><div class="w">1:30 PM</div></div>
    <div class="ev"><span class="ic" style="background:#f1f5f9;color:#64748b"><i class="fa-solid fa-right-to-bracket"></i></span><div class="t"><b>User login</b> &mdash; test@test.com</div><div class="w">1:29 PM</div></div>
    <div class="ev"><span class="ic" style="background:#fef3c7;color:#b45309"><i class="fa-solid fa-sliders"></i></span><div class="t"><b>Jayson Barroga</b> updated system settings</div><div class="w">12:34 PM</div></div>
    <div class="ev"><span class="ic" style="background:#ffe4e6;color:#be123c"><i class="fa-solid fa-trash-restore"></i></span><div class="t"><b>Document purged</b> from trash &mdash; &ldquo;RW old doc&rdquo;</div><div class="w">12:34 PM</div></div>
    <div class="ev"><span class="ic" style="background:#fef3c7;color:#b45309"><i class="fa-solid fa-sliders"></i></span><div class="t"><b>Jayson Barroga</b> updated system settings</div><div class="w">12:32 PM</div></div>
  </div>
</div>"""

def donut(r, segs, track, w=15):
    """segs: list of (length, color); circle length L=2πr"""
    L = 2 * 3.141592653589793 * r
    parts = []
    off = 0
    for ln, col in segs:
        parts.append(f'<circle r="{r}" cx="65" cy="65" fill="none" stroke="{col}" stroke-width="{w}" '
                     f'stroke-dasharray="{ln} {L-ln}" stroke-dashoffset="{-off}"/>')
        off += ln
    return (f'<div class="donut"><svg width="130" height="130" viewBox="0 0 130 130">'
            f'<circle r="{r}" cx="65" cy="65" fill="none" stroke="{track}" stroke-width="{w}"/>'
            + "".join(parts) + f'</svg><div class="ctr"><b>130</b><span>total projects</span></div></div>')

CSS_A = BASE_CSS + """
body{--bg:#f6f8fb;--side:#ffffff;--side-bd:#eef1f5;--side-txt:#374151;--side-dim:#94a3b8;--active-bg:#e6f9f1;--active-txt:#047857;--active-ico:#10b981;--card:#ffffff;--card-bd:#edf0f4;--card-sh:0 1px 2px rgba(16,24,40,.04);--tile-bg:#fbfcfd;--tile-bd:#eef1f5;--t1:#111827;--t2:#4b5563;--t3:#94a3b8;--accent:#059669}
.projrow{display:flex;align-items:center;gap:18px;margin-top:10px}
.comp{display:flex;align-items:center;gap:16px;margin-top:14px}
.comp .av2{width:46px;height:46px;border-radius:13px;background:linear-gradient(135deg,#29ca8e,#0e9f6e);color:#fff;display:flex;align-items:center;justify-content:center;font-weight:800;font-size:15px}
"""

BODY_A = f"""
<div class="app">{SIDEBAR}
<div class="main">
  <div class="pagehead"><div><h1>Good afternoon, Jayson</h1><p>Thursday, September 3 &middot; here's what's moving across the platform.</p></div>
  <span class="livebadge"><span class="pulse"></span>All systems operational</span></div>
  <div class="grid">
    <div class="card c5">
      <h2><i class="fa-solid fa-diagram-project"></i>Projects</h2>
      <div class="projrow">
        {donut(45,[(76.4,"#10b981"),(186.8,"#cbd5e1"),(4.4,"#5eead4"),(2.2,"#f43f5e")],"#f1f5f9")}
        <div class="legend" style="flex:1">
          <div class="row"><span class="dot" style="background:#10b981"></span>In progress<span class="v">35</span></div>
          <div class="row"><span class="dot" style="background:#cbd5e1"></span>Not started<span class="v">92</span></div>
          <div class="row"><span class="dot" style="background:#5eead4"></span>Completed<span class="v">2</span></div>
          <div class="row"><span class="dot" style="background:#f43f5e"></span>Archived<span class="v">1</span></div>
        </div>
      </div>
    </div>
    <div class="card c3">
      <h2><i class="fa-solid fa-building"></i>Client Companies</h2>
      <div class="comp"><span class="av2">27</span>
        <div><div class="big" style="font-size:30px">27</div>
        <div class="mini">client companies served<br><b>12</b> accounts &middot; <b>8</b> active</div></div>
      </div>
    </div>
    <div class="card c4">
      <h2><i class="fa-solid fa-users"></i>Users</h2>
      <div class="projrow">
        {donut(45,[(188.5,"#10b981"),(94.3,"#e5e7eb")],"#f1f5f9")}
        <div class="legend" style="flex:1">
          <div class="row"><span class="dot" style="background:#10b981"></span>Active<span class="v">8</span></div>
          <div class="row"><span class="dot" style="background:#e5e7eb"></span>Inactive<span class="v">4</span></div>
          <div class="row" style="color:var(--t3);font-size:11px;margin-top:4px">12 total accounts</div>
        </div>
      </div>
    </div>
    {HEALTH}
    {DOCS}
    <div class="card c4" style="margin-top:16px">
      <h2><i class="fa-solid fa-chart-simple"></i>Service Catalog</h2>
      <div class="mini" style="font-size:12.5px"><b>12 services</b> active &middot; 1 archived</div>
    </div>
    <div style="grid-column:span 12;margin-top:16px"></div>
    {ACTIVITY}
  </div>
</div></div>"""

CSS_B = BASE_CSS + """
body{--bg:#0b1220;--side:#0e1526;--side-bd:rgba(255,255,255,.07);--side-txt:#cbd5e1;--side-dim:#5b6b85;--active-bg:rgba(16,185,129,.14);--active-txt:#34d399;--active-ico:#34d399;--card:#101a2e;--card-bd:rgba(255,255,255,.08);--card-sh:none;--tile-bg:rgba(255,255,255,.04);--tile-bd:rgba(255,255,255,.09);--t1:#f1f5f9;--t2:#94a3b8;--t3:#5b6b85;--accent:#34d399}
body{background:#0b1220}
.big2{background:linear-gradient(135deg,#34d399,#22d3ee);-webkit-background-clip:text;background-clip:text;color:transparent}
.ktile{background:var(--tile-bg);border:1px solid var(--tile-bd);border-radius:12px;padding:14px}
.ktile .lbl{font-size:11px;font-weight:700;letter-spacing:.08em;text-transform:uppercase;color:var(--t3)}
.ktile .val{font-size:26px;font-weight:800;margin-top:5px;color:var(--t1)}
.ktile .val.g{background:linear-gradient(135deg,#34d399,#22d3ee);-webkit-background-clip:text;background-clip:text;color:transparent}
.k3{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin-top:12px}
.donut .ctr b{color:#f1f5f9}
.ev .t b{color:#f1f5f9}
.legend .row{color:#94a3b8}
"""

def donutB(r, segs, w=15):
    L = 2 * 3.141592653589793 * r
    parts, off = [], 0
    for ln, col in segs:
        parts.append(f'<circle r="{r}" cx="65" cy="65" fill="none" stroke="{col}" stroke-width="{w}" '
                     f'stroke-dasharray="{ln} {L-ln}" stroke-dashoffset="{-off}"/>')
        off += ln
    return (f'<div class="donut"><svg width="130" height="130" viewBox="0 0 130 130">'
            f'<defs><linearGradient id="eg" x1="0" y1="0" x2="1" y2="1">'
            f'<stop offset="0%" stop-color="#34d399"/><stop offset="100%" stop-color="#22d3ee"/></linearGradient></defs>'
            f'<circle r="{r}" cx="65" cy="65" fill="none" stroke="rgba(255,255,255,.08)" stroke-width="{w}"/>'
            + "".join(parts) + f'</svg><div class="ctr"><b>130</b><span>total projects</span></div></div>')

BODY_B = f"""
<div class="app">{SIDEBAR}
<div class="main">
  <div class="pagehead"><div><h1>Good afternoon, Jayson</h1><p>Thursday, September 3 &middot; live platform overview</p></div>
  <span class="livebadge"><span class="pulse"></span>Live &middot; 12d 5h uptime</span></div>
  <div class="grid">
    <div class="card c5">
      <h2><i class="fa-solid fa-diagram-project"></i>Projects</h2>
      <div class="projrow" style="display:flex;align-items:center;gap:18px;margin-top:10px">
        {donutB(45,[(76.4,"url(#eg)"),(186.8,"#334155"),(4.4,"#22d3ee"),(2.2,"#f43f5e")])}
        <div class="legend" style="flex:1">
          <div class="row"><span class="dot" style="background:linear-gradient(135deg,#34d399,#22d3ee)"></span>In progress<span class="v">35</span></div>
          <div class="row"><span class="dot" style="background:#334155"></span>Not started<span class="v">92</span></div>
          <div class="row"><span class="dot" style="background:#22d3ee"></span>Completed<span class="v">2</span></div>
          <div class="row"><span class="dot" style="background:#f43f5e"></span>Archived<span class="v">1</span></div>
        </div>
      </div>
    </div>
    <div class="card c3">
      <h2><i class="fa-solid fa-building"></i>Client Companies</h2>
      <div class="big2 big" style="margin-top:14px">27</div>
      <div class="mini">served to date<br><b>12</b> accounts &middot; <b>8</b> active</div>
    </div>
    <div class="card c4">
      <h2><i class="fa-solid fa-users"></i>Users</h2>
      <div class="k3">
        <div class="ktile"><div class="lbl">Total</div><div class="val">12</div></div>
        <div class="ktile"><div class="lbl">Active</div><div class="val g">8</div></div>
        <div class="ktile"><div class="lbl">Inactive</div><div class="val" style="color:#64748b">4</div></div>
      </div>
    </div>
    {HEALTH.replace("var(--t2)","")}
    {DOCS}
    <div class="card c4" style="margin-top:16px">
      <h2><i class="fa-solid fa-chart-simple"></i>Service Catalog</h2>
      <div class="mini" style="font-size:12.5px"><b>12 services</b> active &middot; 1 archived</div>
    </div>
    <div style="grid-column:span 12;margin-top:16px"></div>
    {ACTIVITY}
  </div>
</div></div>"""

CSS_C = BASE_CSS + """
body{--bg:#eef4f1;--side:rgba(255,255,255,.72);--side-bd:rgba(16,185,129,.15);--side-txt:#1f2937;--side-dim:#8aa09a;--active-bg:rgba(16,185,129,.12);--active-txt:#047857;--active-ico:#10b981;--card:rgba(255,255,255,.66);--card-bd:rgba(255,255,255,.75);--card-sh:0 12px 32px rgba(6,78,59,.10);--tile-bg:rgba(255,255,255,.8);--tile-bd:rgba(16,185,129,.16);--t1:#0f172a;--t2:#334155;--t3:#7c8b92;--accent:#059669}
body{background:linear-gradient(160deg,#e8f5ef 0%,#f2f6f5 45%,#eaf1f6 100%);min-height:100vh}
body::before{content:"";position:fixed;top:-140px;right:-120px;width:480px;height:480px;border-radius:50%;background:radial-gradient(circle,rgba(41,202,142,.28),transparent 70%);z-index:0}
body::after{content:"";position:fixed;bottom:-180px;left:200px;width:520px;height:520px;border-radius:50%;background:radial-gradient(circle,rgba(34,211,238,.18),transparent 70%);z-index:0}
.app{position:relative;z-index:1}
.card{backdrop-filter:blur(14px);-webkit-backdrop-filter:blur(14px)}
.gauge{position:relative;width:190px;height:100px;margin:6px auto 0}
.gauge .gv{position:absolute;left:0;right:0;bottom:0;text-align:center}
.gauge .gv b{font-size:24px;font-weight:800;color:var(--t1)}
.gauge .gv span{display:block;font-size:10.5px;color:var(--t3);margin-top:2px;font-weight:600}
"""

BODY_C = f"""
<div class="app">{SIDEBAR}
<div class="main">
  <div class="pagehead"><div><h1>Good afternoon, Jayson</h1><p>Thursday, September 3 &middot; platform at a glance</p></div>
  <span class="livebadge"><span class="pulse"></span>All systems operational</span></div>
  <div class="grid">
    <div class="card c5">
      <h2><i class="fa-solid fa-diagram-project"></i>Projects</h2>
      <div style="display:flex;align-items:center;gap:20px;margin-top:10px">
        {donut(45,[(76.4,"#10b981"),(186.8,"#d7e2dc"),(4.4,"#22d3ee"),(2.2,"#f43f5e")],"#eef3f0")}
        <div class="legend" style="flex:1">
          <div class="row"><span class="dot" style="background:#10b981"></span>In progress<span class="v">35</span></div>
          <div class="row"><span class="dot" style="background:#d7e2dc"></span>Not started<span class="v">92</span></div>
          <div class="row"><span class="dot" style="background:#22d3ee"></span>Completed<span class="v">2</span></div>
          <div class="row"><span class="dot" style="background:#f43f5e"></span>Archived<span class="v">1</span></div>
        </div>
      </div>
    </div>
    <div class="card c3">
      <h2><i class="fa-solid fa-building"></i>Client Companies</h2>
      <div class="big" style="margin-top:14px">27</div>
      <div class="mini">client companies served<br><b>12</b> accounts &middot; <b>8</b> active</div>
    </div>
    <div class="card c4">
      <h2><i class="fa-solid fa-users"></i>Users</h2>
      <div class="gauge">
        <svg width="190" height="100" viewBox="0 0 190 100">
          <defs><linearGradient id="gg" x1="0" y1="1" x2="1" y2="0">
            <stop offset="0%" stop-color="#29ca8e"/><stop offset="100%" stop-color="#22d3ee"/></linearGradient></defs>
          <path d="M 20 100 A 75 75 0 0 1 170 100" fill="none" stroke="#e3ece7" stroke-width="14" stroke-linecap="round"/>
          <path d="M 20 100 A 75 75 0 0 1 170 100" fill="none" stroke="url(#gg)" stroke-width="14" stroke-linecap="round" stroke-dasharray="147.3 235.6"/>
        </svg>
        <div class="gv"><b>67%</b><span>8 of 12 accounts active</span></div>
      </div>
      <div style="display:flex;justify-content:space-around;margin-top:10px;font-size:12px;color:var(--t3)">
        <div><b style="color:var(--t1);font-size:15px">12</b> total</div>
        <div><b style="color:#059669;font-size:15px">8</b> active</div>
        <div><b style="color:var(--t3);font-size:15px">4</b> inactive</div>
      </div>
    </div>
    {HEALTH}
    {DOCS}
    <div class="card c4" style="margin-top:16px">
      <h2><i class="fa-solid fa-chart-simple"></i>Service Catalog</h2>
      <div class="mini" style="font-size:12.5px"><b>12 services</b> active &middot; 1 archived</div>
    </div>
    <div style="grid-column:span 12;margin-top:16px"></div>
    {ACTIVITY}
  </div>
</div></div>"""

FILES = {"a": (CSS_A, BODY_A), "b": (CSS_B, BODY_B), "c": (CSS_C, BODY_C)}
for k, (css, body) in FILES.items():
    html = ("<!DOCTYPE html><html><head><meta charset='utf-8'>"
            "<link rel='stylesheet' href='https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css'>"
            f"<style>{css}</style></head><body>{body}</body></html>")
    with open(f"{ROOT}/dash_{k}.html", "w") as f:
        f.write(html)
    print(f"wrote dash_{k}.html")

# render
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
for k in "abc":
    out = f"{ROOT}/dash_{k}.png"
    subprocess.run([CHROME, "--headless=new", "--no-sandbox", "--hide-scrollbars",
                    f"--screenshot={out}", "--window-size=1600,1150", "--force-device-scale-factor=2",
                    "--virtual-time-budget=6000", f"file://{ROOT}/dash_{k}.html"],
                   capture_output=True, timeout=120)
    from PIL import Image
    im = Image.open(out)
    print(f"{k}: {im.size[0]}x{im.size[1]}")
print("DONE")
