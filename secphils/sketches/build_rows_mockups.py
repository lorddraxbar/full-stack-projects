#!/usr/bin/env python3
"""Generate 3 project-row design variants as self-contained HTML mockups.
Real SecPhils palette (teal #29ca8e, warm greys, Raleway) + realistic sample
rows drawn from live service/company names. Used for Jaybar's design choice."""
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

PILL_CSS = {
    "inprogress": "background:#ccfbf1;color:#155e75;",
    "notstarted": "background:#f3f4f6;color:#1f2937;",
    "completed":  "background:#dcfce7;color:#166534;",
    "archived":   "background:#f3f4f6;color:#6b7280;",
}

BASE_CSS = """
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
.card-shell { background:#fff; border:1px solid #e8e8e8; border-radius:10px; overflow:hidden; }
.meta-ic { color:#a3a3a3; font-size:12px; margin-right:5px; }
"""

def pill(r):
    return f'<span class="pill" style="{PILL_CSS[r["pill"]]}">{r["status"]}</span>'

# ---------------- Option A: Card grid ----------------
def rows_a():
    out = []
    for r in ROWS:
        up = (f'{r["update"]} <span style="color:#a3a3a3">· {r["updateAt"]}</span>'
              if r["update"] else '<span style="color:#a3a3a3">No updates yet</span>')
        out.append(f'''
      <div class="prow-a">
        <div class="a-top">
          <div class="a-chip"><i class="fas fa-clipboard-list"></i></div>
          <div style="flex:1;min-width:0">
            <div class="a-name">{r["name"]}</div>
          </div>
          {pill(r)}
        </div>
        <div class="a-meta">
          <span><i class="fas fa-building meta-ic"></i>{r["client"]}</span>
          <span><i class="fas fa-tag meta-ic"></i>{r["service"]}</span>
        </div>
        <div class="a-foot">
          <div class="a-cost">{r["cost"]}</div>
          <div class="a-dates">
            <span><i class="fas fa-calendar-plus meta-ic"></i>{r["created"]}</span>
            <span><i class="fas fa-flag-checkered meta-ic"></i>{r["completed"]}</span>
          </div>
        </div>
        <div class="a-update">{up}</div>
      </div>''')
    return f'''
  <div class="card-shell" style="padding:16px; border-radius:10px;">
    <div class="grid-a">{"".join(out)}</div>
  </div>
  <style>
  .grid-a {{ display:grid; grid-template-columns:repeat(2,1fr); gap:14px; }}
  .prow-a {{ border:1px solid #e8e8e8; border-radius:10px; padding:16px; background:#fff;
    transition: border-color .15s, box-shadow .15s; cursor:pointer; }}
  .prow-a:hover {{ border-color:#29ca8e; box-shadow:0 4px 14px rgba(41,202,142,.10); }}
  .a-top {{ display:flex; align-items:center; gap:10px; margin-bottom:10px; }}
  .a-chip {{ width:38px; height:38px; border-radius:9px; background:#e9f8f2; color:#1f9d6b;
    display:flex; align-items:center; justify-content:center; font-size:15px; flex:none; }}
  .a-name {{ font-size:15px; font-weight:600; color:#202020; line-height:1.35; }}
  .a-meta {{ display:flex; flex-direction:column; gap:4px; font-size:13px; color:#757575;
    margin-bottom:12px; }}
  .a-foot {{ display:flex; align-items:center; justify-content:space-between; gap:10px;
    background:#f9f9f9; border-radius:8px; padding:10px 12px; margin-bottom:10px; }}
  .a-cost {{ font-size:14px; font-weight:700; color:#202020; }}
  .a-dates {{ display:flex; gap:12px; font-size:12px; color:#757575; }}
  .a-update {{ font-size:12.5px; color:#757575; line-height:1.4; }}
  </style>'''

# ---------------- Option B: Dense table ----------------
def rows_b():
    body = []
    for r in ROWS:
        up = (f'{r["update"]}' if r["update"] else 'No updates yet')
        upat = f'<div class="b-upat">{r["updateAt"]}</div>' if r["updateAt"] else ''
        body.append(f'''
        <tr>
          <td class="b-cell b-proj">
            <div class="b-rail" style="background:{ {"inprogress":"#29ca8e","notstarted":"#d1d5db","completed":"#22c55e","archived":"#a3a3a3"}[r["pill"]] }"></div>
            <div style="min-width:0">
              <div class="b-name">{r["name"]}</div>
              <div class="b-client"><i class="fas fa-building meta-ic"></i>{r["client"]}</div>
            </div>
          </td>
          <td class="b-cell b-svc">{r["service"]}</td>
          <td class="b-cell b-cost">{r["cost"]}</td>
          <td class="b-cell b-created">{r["created"]}</td>
          <td class="b-cell b-update"><div class="b-upbody">{up}</div>{upat}</td>
          <td class="b-cell b-status">{pill(r)}</td>
        </tr>''')
    return f'''
  <div class="card-shell">
    <table class="tbl-b">
      <thead>
        <tr>
          <th style="width:34%">Project</th>
          <th style="width:21%">Service</th>
          <th style="width:12%; text-align:right">Cost</th>
          <th style="width:12%">Created</th>
          <th style="width:15%">Latest Update</th>
          <th style="width:6%">Status</th>
        </tr>
      </thead>
      <tbody>{"".join(body)}</tbody>
    </table>
  </div>
  <style>
  .tbl-b {{ width:100%; border-collapse:collapse; }}
  .tbl-b thead th {{ font-size:11px; font-weight:600; text-transform:uppercase; letter-spacing:.06em;
    color:#a3a3a3; text-align:left; padding:11px 14px; border-bottom:1px solid #e8e8e8; background:#fff; }}
  .tbl-b tbody tr {{ transition:background .12s; cursor:pointer; }}
  .tbl-b tbody tr:hover {{ background:#f9f9f9; }}
  .tbl-b tbody tr + tr td {{ border-top:1px solid #ececec; }}
  .b-cell {{ padding:14px; font-size:13px; color:#757575; vertical-align:middle; }}
  .b-proj {{ display:table-cell; position:relative; padding-left:0 !important; }}
  .b-rail {{ display:inline-block; width:4px; height:38px; border-radius:4px; margin:0 12px 0 14px;
    vertical-align:middle; }}
  .b-name {{ font-size:14.5px; font-weight:600; color:#202020; line-height:1.3; }}
  .b-client {{ font-size:12.5px; color:#757575; margin-top:3px; }}
  .b-svc {{ line-height:1.4; }}
  .b-cost {{ text-align:right; font-weight:600; color:#202020; font-variant-numeric:tabular-nums; }}
  .b-update {{ max-width:210px; }}
  .b-upbody {{ white-space:nowrap; overflow:hidden; text-overflow:ellipsis; line-height:1.4; }}
  .b-upat {{ font-size:11.5px; color:#a3a3a3; margin-top:2px; }}
  .b-status {{ white-space:nowrap; }}
  </style>'''

# ---------------- Option C: Refined list rows ----------------
def rows_c():
    out = []
    for r in ROWS:
        up = (f'<i class="fas fa-quote-left" style="color:#d1d5db; font-size:10px;"></i> {r["update"]} '
              f'<span style="color:#a3a3a3">· {r["updateAt"]}</span>'
              if r["update"] else '<span style="color:#a3a3a3">No updates yet</span>')
        out.append(f'''
      <div class="prow-c">
        <div class="c-main">
          <div class="c-row1">
            <div class="c-name">{r["name"]}</div>
            {pill(r)}
          </div>
          <div class="c-row2">
            <span><i class="fas fa-building meta-ic"></i>{r["client"]}</span>
            <span class="c-dot"></span>
            <span><i class="fas fa-tag meta-ic"></i>{r["service"]}</span>
            <span class="c-dot"></span>
            <span><i class="fas fa-money-bill-wave meta-ic"></i><b class="c-cost">{r["cost"]}</b></span>
          </div>
          <div class="c-row3">
            <span class="c-update">{up}</span>
            <span class="c-dates"><i class="fas fa-calendar-plus meta-ic"></i>Created {r["created"]}
              <i class="fas fa-flag-checkered meta-ic" style="margin-left:10px"></i>{r["completed"]}</span>
          </div>
        </div>
        <div class="c-arrow"><i class="fas fa-chevron-right"></i></div>
      </div>''')
    return f'''
  <div class="card-shell" style="padding:0 18px;">{"".join(out)}
  </div>
  <style>
  .prow-c {{ display:flex; align-items:center; gap:12px; padding:18px 0; cursor:pointer; }}
  .prow-c + .prow-c {{ border-top:1px solid #ececec; }}
  .prow-c .c-main {{ flex:1; min-width:0; }}
  .c-row1 {{ display:flex; align-items:center; justify-content:space-between; gap:10px; }}
  .c-name {{ font-size:15px; font-weight:600; color:#202020; }}
  .c-row2 {{ display:flex; align-items:center; flex-wrap:wrap; gap:8px; margin-top:6px;
    font-size:13px; color:#757575; }}
  .c-dot {{ width:3px; height:3px; border-radius:50%; background:#d1d5db; flex:none; }}
  .c-cost {{ font-weight:600; color:#202020; }}
  .c-row3 {{ display:flex; align-items:center; justify-content:space-between; gap:12px;
    margin-top:8px; }}
  .c-update {{ font-size:12.5px; color:#757575; white-space:nowrap; overflow:hidden;
    text-overflow:ellipsis; min-width:0; }}
  .c-dates {{ font-size:12px; color:#a3a3a3; white-space:nowrap; flex:none; }}
  .c-arrow {{ color:#d1d5db; font-size:13px; opacity:0; transition:opacity .15s, transform .15s;
    flex:none; }}
  .prow-c:hover .c-arrow {{ opacity:1; transform:translateX(2px); }}
  </style>'''

HEAD = '''
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
'''

def page(title, body):
    return f'''<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link href="https://fonts.googleapis.com/css2?family=Raleway:wght@400;500;600;700&display=swap" rel="stylesheet">
<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.1/css/all.min.css">
<style>{BASE_CSS}</style>
</head>
<body>
<div class="wrap">
  <div style="font-size:12px; font-weight:700; letter-spacing:.08em; text-transform:uppercase; color:#29ca8e; margin-bottom:6px">{title}</div>
  {HEAD}
  {body}
</div>
</body>
</html>'''

VARIANTS = [
    ("rows-a-grid",  "Option A — Card grid",  rows_a()),
    ("rows-b-table", "Option B — Status-rail table", rows_b()),
    ("rows-c-list",  "Option C — Refined list rows", rows_c()),
]

for slug, title, body in VARIANTS:
    d = os.path.join(HERE, slug)
    os.makedirs(d, exist_ok=True)
    with open(os.path.join(d, "index.html"), "w") as f:
        f.write(page(title, body))
    print("wrote", d)
