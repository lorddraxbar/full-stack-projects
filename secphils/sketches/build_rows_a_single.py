#!/usr/bin/env python3
"""Option A, single-column: one full-width project card per row.
Interior re-flowed for full width — icon left, content middle (name / meta /
update), status pill + dates pinned right. Real SecPhils palette."""
import os

HERE = os.path.dirname(os.path.abspath(__file__))

ROWS = [
    dict(
        name="ECC — Sta. Rita Plant Expansion",
        client="Strategic Engineering Consultancy",
        service="Environmental Compliance Certificate (ECC)",
        status="In Progress", pill="inprogress",
        cost="₱5,500,000",
        created="Aug 28, 2026", completed="—",
        update="Draft ECC submitted to the regional office for review.", updateAt="2d ago",
    ),
    dict(
        name="Hazardous Waste Generator ID — New Assembly Line",
        client="Strategic Engineering Consultancy",
        service="Hazardous Waste Generator ID",
        status="Not Started", pill="notstarted",
        cost="₱1,750,000",
        created="Sep 1, 2026", completed="—",
        update=None, updateAt=None,
    ),
    dict(
        name="Office Building HVAC Retrofit",
        client="Strategic Engineering Consultancy",
        service="Feasibility Studies for Businesses",
        status="Completed", pill="completed",
        cost="—",
        created="Aug 22, 2026", completed="Aug 22, 2026",
        update="Feasibility report delivered; project closed.", updateAt="5d ago",
    ),
    dict(
        name="Discharge Permit — Riverfront Site",
        client="Test Wire Co",
        service="Discharge Permit (DP)",
        status="Archived", pill="archived",
        cost="₱320,000",
        created="Jul 30, 2026", completed="—",
        update="Permit renewed; file archived at client request.", updateAt="3w ago",
    ),
]

PILL = {
    "inprogress": "background:#ccfbf1;color:#155e75;",
    "notstarted": "background:#f3f4f6;color:#1f2937;",
    "completed":  "background:#dcfce7;color:#166534;",
    "archived":   "background:#f3f4f6;color:#6b7280;",
}

def pill(r):
    return f'<span class="pill" style="{PILL[r["pill"]]}">{r["status"]}</span>'

def card(r):
    up = (f'<span class="upd-q">“{r["update"]}”</span> <span class="upd-at">· {r["updateAt"]}</span>'
          if r["update"] else '<span class="upd-none">No updates yet</span>')
    return f'''
      <div class="pcard">
        <div class="p-ic"><i class="fas fa-clipboard-list"></i></div>
        <div class="p-mid">
          <div class="p-name">{r["name"]}</div>
          <div class="p-meta">
            <span><i class="fas fa-building mi"></i>{r["client"]}</span>
            <span class="mdot"></span>
            <span><i class="fas fa-tag mi"></i>{r["service"]}</span>
            <span class="mdot"></span>
            <span class="p-cost"><i class="fas fa-money-bill-wave mi"></i>{r["cost"]}</span>
          </div>
          <div class="p-update">{up}</div>
        </div>
        <div class="p-right">
          {pill(r)}
          <div class="p-dates">
            <span><i class="fas fa-calendar-plus mi"></i>Created {r["created"]}</span>
            <span><i class="fas fa-flag-checkered mi"></i>Completed {r["completed"]}</span>
          </div>
        </div>
      </div>'''

BODY = "".join(card(r) for r in ROWS)

PAGE = """<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link href="https://fonts.googleapis.com/css2?family=Raleway:wght@400;500;600;700&display=swap" rel="stylesheet">
<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.1/css/all.min.css">
<style>
* { margin:0; padding:0; box-sizing:border-box; }
body { font-family:'Raleway',sans-serif; background:#f9f9f9; color:#202020; }
.wrap { max-width:1140px; margin:0 auto; padding:28px 16px 40px; }
.head { display:flex; align-items:center; justify-content:space-between; margin-bottom:18px; }
.head h1 { font-size:24px; font-weight:700; }
.head p { color:#757575; font-size:14px; margin-top:3px; }
.newbtn { background:#29ca8e; color:#fff; border:none; border-radius:8px; padding:9px 16px;
  font-family:inherit; font-size:14px; font-weight:600; cursor:pointer; }
.filters { background:#fff; border:1px solid #e8e8e8; border-radius:10px; padding:14px 16px;
  display:flex; gap:12px; margin-bottom:16px; }
.filters .search { flex:1; height:40px; border:1px solid #e8e8e8; border-radius:8px;
  padding:0 14px; font-family:inherit; font-size:14px; color:#202020; background:#fff; }
.filters select { height:40px; border:1px solid #e8e8e8; border-radius:8px; padding:0 12px;
  font-family:inherit; font-size:14px; color:#202020; background:#fff; }
.pill { display:inline-block; font-size:11.5px; font-weight:600; padding:3px 10px;
  border-radius:999px; letter-spacing:.01em; white-space:nowrap; }
.mi { color:#a3a3a3; font-size:12px; margin-right:5px; }
.shell { background:#fff; border:1px solid #e8e8e8; border-radius:10px; padding:14px; }
.grid { display:flex; flex-direction:column; gap:12px; }
.pcard { display:grid; grid-template-columns:52px 1fr auto; gap:14px; align-items:center;
  border:1px solid #e8e8e8; border-radius:10px; padding:16px 18px; background:#fff;
  transition:border-color .15s, box-shadow .15s; cursor:pointer; }
.pcard:hover { border-color:#29ca8e; box-shadow:0 4px 14px rgba(41,202,142,.10); }
.p-ic { width:52px; height:52px; border-radius:11px; background:#e9f8f2; color:#1f9d6b;
  display:flex; align-items:center; justify-content:center; font-size:20px; }
.p-name { font-size:16px; font-weight:600; color:#202020; line-height:1.3; }
.p-meta { display:flex; align-items:center; flex-wrap:wrap; gap:9px; margin-top:7px;
  font-size:13.5px; color:#757575; }
.mdot { width:3px; height:3px; border-radius:50%; background:#d1d5db; flex:none; }
.p-cost { font-weight:600; color:#202020; }
.p-update { margin-top:9px; font-size:13px; color:#757575; }
.upd-q { color:#536976; }
.upd-at { color:#a3a3a3; }
.upd-none { color:#a3a3a3; }
.p-right { display:flex; flex-direction:column; align-items:flex-end; gap:9px; }
.p-dates { display:flex; flex-direction:column; align-items:flex-end; gap:3px;
  font-size:12px; color:#a3a3a3; }
@media (max-width:640px) {
  .pcard { grid-template-columns:44px 1fr; grid-template-rows:auto auto; }
  .p-ic { width:44px; height:44px; font-size:17px; grid-row:1; grid-column:1; }
  .p-mid { grid-column:2; grid-row:1; }
  .p-right { grid-column:2; grid-row:2; flex-direction:row; align-items:center;
    gap:12px; flex-wrap:wrap; }
  .p-dates { flex-direction:row; align-items:center; gap:12px; margin-left:auto; }
  .p-meta { gap:6px; font-size:12.5px; }
  .p-name { font-size:15px; }
}
</style>
</head>
<body>
<div class="wrap">
  <div style="font-size:12px; font-weight:700; letter-spacing:.08em; text-transform:uppercase; color:#29ca8e; margin-bottom:6px">Option A — one card per row</div>
  <div class="head">
    <div>
      <h1>All Projects</h1>
      <p>Manage and track all your projects.</p>
    </div>
    <button class="newbtn">+ New Project</button>
  </div>
  <div class="filters">
    <input class="search" placeholder="Search projects..." />
    <select><option>All Status</option></select>
  </div>
  <div class="shell"><div class="grid">__BODY__</div></div>
</div>
</body>
</html>"""

d = os.path.join(HERE, "rows-a-single")
os.makedirs(d, exist_ok=True)
open(os.path.join(d, "index.html"), "w").write(PAGE.replace("__BODY__", BODY))
print("wrote", d)
