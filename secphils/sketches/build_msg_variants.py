#!/usr/bin/env python3
"""Composer UI variants for SecPhils Messages — reduce/eliminate accidental
'internal -> client' sends. Real portal palette (teal #29ca8e / emerald-600,
warm grays, slate for internal, Raleway). Four labeled composer designs + a
comparison table. CSS kept out of the f-strings (brace pitfall)."""
import os

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "msg_variants", "index.html")

CSS = r"""
* { box-sizing: border-box; margin: 0; padding: 0; }
body { font-family: 'Raleway', system-ui, sans-serif; background: #f9f9f9; color: #202020; padding: 28px; }
h1 { font-size: 20px; font-weight: 700; margin-bottom: 4px; }
.sub { font-size: 13px; color: #757575; margin-bottom: 20px; max-width: 900px; line-height: 1.5; }
.grid { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; }
@media (max-width: 980px) { .grid { grid-template-columns: 1fr; } }

/* ---- variant card ---- */
.variant { background: #fff; border: 1px solid #e8e8e8; border-radius: 12px; overflow: hidden; display: flex; flex-direction: column; }
.vhead { padding: 10px 14px; border-bottom: 1px solid #e8e8e8; display: flex; align-items: center; gap: 8px; }
.opt { font-size: 11px; font-weight: 700; letter-spacing: .04em; color: #fff; background: #202020; padding: 2px 8px; border-radius: 20px; }
.opt.a { background: #29ca8e; }
.vtitle { font-size: 14px; font-weight: 700; color: #202020; }
.rec { margin-left: auto; font-size: 10px; font-weight: 700; color: #29ca8e; border: 1px solid #29ca8e; padding: 2px 8px; border-radius: 20px; }
.vbody { padding: 14px; flex: 1; }
.vnote { padding: 10px 14px; border-top: 1px dashed #e8e8e8; font-size: 11.5px; color: #757575; line-height: 1.5; background: #fcfcfc; }
.vnote b { color: #4b5563; }

/* ---- shared: thread preview ---- */
.thread { background: #fff; border: 1px solid #e8e8e8; border-radius: 10px; padding: 12px; margin-bottom: 12px; }
.th-head { font-size: 12px; font-weight: 700; color: #202020; margin-bottom: 10px; padding-bottom: 8px; border-bottom: 1px solid #f0f0f0; }
.msg { display: flex; gap: 8px; margin-bottom: 8px; }
.msg.own { flex-direction: row-reverse; }
.av { width: 26px; height: 26px; border-radius: 50%; flex: none; display: flex; align-items: center; justify-content: center; font-size: 10px; font-weight: 700; color: #fff; }
.av.c { background: #059669; }   /* client / own */
.av.s { background: #64748b; }   /* staff */
.bub { max-width: 78%; border-radius: 8px; padding: 7px 10px; font-size: 12px; line-height: 1.4; }
.bub.client { background: #f3f4f6; color: #1f2937; }
.bub.ownclient { background: #059669; color: #fff; }
.bub.internal { background: #f1f5f9; color: #1e293b; box-shadow: inset 0 0 0 1px dashed #94a3b8; }
.bub.owninternal { background: #334155; color: #fff; box-shadow: inset 0 0 0 1px dashed #cbd5e1; }
.ibadge { display: inline-flex; align-items: center; gap: 4px; font-size: 9px; font-weight: 700; letter-spacing: .03em; color: #64748b; text-transform: uppercase; margin-bottom: 3px; }
.owninternal .ibadge { color: #cbd5e1; }
.who { font-size: 9.5px; color: #9ca3af; margin: 1px 0 0 34px; }
.own .who { margin: 1px 34px 0 0; text-align: right; }

/* ---- composer shell ---- */
.composer { border-radius: 10px; border: 1.5px solid #e8e8e8; overflow: hidden; }
.composer.state-internal { border-color: #cbd5e1; }
.composer.state-client { border-color: #29ca8e; }
.cbanner { padding: 8px 12px; font-size: 11.5px; font-weight: 600; display: flex; align-items: center; gap: 7px; }
.cbanner.internal { background: #f1f5f9; color: #475569; }
.cbanner.client { background: #e7faf1; color: #0f7a5b; }
.ctext { margin: 10px 12px 0; }
textarea.ta { width: 100%; border: 1px solid #e5e7eb; border-radius: 8px; padding: 9px 10px; font-family: inherit; font-size: 12.5px; resize: none; background: #fff; color: #202020; }
.cfoot { display: flex; align-items: center; justify-content: space-between; padding: 10px 12px; gap: 10px; }
.cfoot.between { justify-content: space-between; }

/* toggle switch */
.switch { display: inline-flex; align-items: center; gap: 8px; font-size: 12px; color: #4b5563; cursor: pointer; user-select: none; }
.track { width: 34px; height: 19px; border-radius: 20px; background: #d1d5db; position: relative; transition: .15s; }
.track.on { background: #29ca8e; }
.knob { position: absolute; top: 2px; left: 2px; width: 15px; height: 15px; border-radius: 50%; background: #fff; transition: .15s; }
.track.on .knob { left: 17px; }

/* segmented control */
.seg { display: inline-flex; background: #f3f4f6; border: 1px solid #e5e7eb; border-radius: 9px; padding: 3px; gap: 3px; }
.seg button { border: none; background: transparent; font-family: inherit; font-size: 12px; font-weight: 600; color: #6b7280; padding: 6px 12px; border-radius: 7px; cursor: pointer; display: flex; align-items: center; gap: 6px; }
.seg button.sel-client { background: #29ca8e; color: #fff; }
.seg button.sel-internal { background: #334155; color: #fff; }

/* plain checkbox (current design) */
.chk { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; color: #4b5563; cursor: pointer; user-select: none; }
.chk input { width: 15px; height: 15px; accent-color: #334155; }

/* buttons */
.btn { border: none; font-family: inherit; font-size: 12.5px; font-weight: 600; padding: 8px 16px; border-radius: 8px; cursor: pointer; }
.btn.internal { background: #334155; color: #fff; }
.btn.client { background: #059669; color: #fff; }
.btn.ghost { background: #fff; color: #4b5563; border: 1px solid #e5e7eb; }
.btn.amber { background: #f59e0b; color: #fff; }

/* inline confirm strip */
.confirm { display: flex; align-items: center; gap: 8px; margin: 0 12px 12px; padding: 8px 10px; background: #fffbeb; border: 1px solid #fde68a; border-radius: 8px; font-size: 11.5px; color: #92400e; }
.confirm .row { margin-left: auto; display: flex; gap: 6px; }
.confirm button { border: none; font-family: inherit; font-size: 11px; font-weight: 600; padding: 5px 10px; border-radius: 6px; cursor: pointer; }

/* audience chip (variant D) */
.chip { display: inline-flex; align-items: center; gap: 6px; font-size: 11px; font-weight: 700; padding: 4px 10px; border-radius: 20px; }
.chip.client { background: #e7faf1; color: #0f7a5b; }
.chip.internal { background: #f1f5f9; color: #475569; }

/* comparison table */
.tablewrap { margin-top: 24px; background: #fff; border: 1px solid #e8e8e8; border-radius: 12px; overflow: hidden; }
table { width: 100%; border-collapse: collapse; font-size: 12px; }
th { background: #202020; color: #fff; text-align: left; padding: 10px 12px; font-weight: 700; font-size: 11px; letter-spacing: .03em; }
td { padding: 10px 12px; border-top: 1px solid #f0f0f0; vertical-align: top; color: #4b5563; }
tr.recommended td { background: #f2fdf9; }
td .yes { color: #0f7a5b; font-weight: 700; }
td .no { color: #b45309; font-weight: 700; }
.tag { display: inline-block; font-size: 10px; font-weight: 700; padding: 1px 7px; border-radius: 12px; }
.tag.g { background: #dcfce7; color: #166534; }
.tag.y { background: #fef3c7; color: #92400e; }
"""

def thread_preview():
    """Same 3-bubble preview in every variant: client msg, staff msg visible to
    client (teal), staff INTERNAL msg (slate, dashed, lock)."""
    return '''
      <div class="thread">
        <div class="th-head">Messages — ECC: Sta. Rita Plant Expansion</div>
        <div class="msg">
          <div class="av c">RC</div>
          <div class="bub client">Received the draft ECC. Two items to confirm before we submit.</div>
        </div>
        <div class="msg own">
          <div class="av c">JL</div>
          <div class="bub ownclient">Sure — I'll send the updated effluent data sheet today.</div>
        </div>
        <div class="msg own">
          <div class="av s">MK</div>
          <div class="bub owninternal"><span class="ibadge">&#128274; Internal</span>Let's hold the submission until Legal clears the permit wording.</div>
        </div>
        <div class="who own">Marites K. — staff only</div>
      </div>
    '''

# ---------- VARIANT A: safe default, staff-only, opt-in to client ----------
def variant_a():
    return '''
    <div class="variant">
      <div class="vhead"><span class="opt a">A</span><span class="vtitle">Safe by default — staff-only, opt in to client</span><span class="rec">RECOMMENDED</span></div>
      <div class="vbody">
        ''' + thread_preview() + '''
        <div class="composer state-internal">
          <div class="cbanner internal">&#128274; Staff only &mdash; your client can&rsquo;t see this message</div>
          <textarea class="ta" rows="2" placeholder="Type an internal note&hellip;"></textarea>
          <div class="cfoot">
            <label class="switch">
              <span class="track"><span class="knob"></span></span>
              Visible to client
            </label>
            <button class="btn internal">Send to staff</button>
          </div>
        </div>
      </div>
      <div class="vnote"><b>Forgetting is now safe.</b> The composer defaults to staff-only. To share with the client you flip &ldquo;Visible to client&rdquo; &mdash; the banner turns teal, and the button becomes <b>&ldquo;Send to client&rdquo;</b>. A missed toggle means the client simply doesn&rsquo;t get it (under-share), never a leak.</div>
    </div>
    '''

# ---------- VARIANT B: explicit audience segmented control ----------
def variant_b():
    return '''
    <div class="variant">
      <div class="vhead"><span class="opt">B</span><span class="vtitle">Pick the audience &mdash; segmented control</span></div>
      <div class="vbody">
        ''' + thread_preview() + '''
        <div class="composer state-internal">
          <div class="cfoot" style="padding:10px 12px 0;">
            <div class="seg">
              <button>&#128225; Client</button>
              <button class="sel-internal">&#128274; Staff only</button>
            </div>
          </div>
          <textarea class="ta" rows="2" placeholder="Type your message&hellip;"></textarea>
          <div class="cfoot" style="padding-top:10px;">
            <span style="font-size:11px;color:#64748b;">Sending to <b>staff only</b></span>
            <button class="btn internal">Send to staff</button>
          </div>
        </div>
      </div>
      <div class="vnote"><b>A required, visible choice.</b> The audience is a two-state control on top of the composer, defaulting to <b>Staff only</b>; the composer border and Send button recolor to match. Nothing leaves without a deliberate audience, and a missed change stays internal &mdash; safe.</div>
    </div>
    '''

# ---------- VARIANT C: keep current default, add confirm-on-client-send ----------
def variant_c():
    return '''
    <div class="variant">
      <div class="vhead"><span class="opt">C</span><span class="vtitle">Confirm before it reaches the client</span></div>
      <div class="vbody">
        ''' + thread_preview() + '''
        <div class="composer">
          <textarea class="ta" rows="2" placeholder="Type a message&hellip;"></textarea>
          <div class="cfoot">
            <label class="chk"><input type="checkbox" /> &#128274; Internal (staff only)</label>
            <button class="btn client">Send</button>
          </div>
          <div class="confirm">
            This will be sent to <b>your client</b>.
            <span class="row">
              <button class="ghost" style="background:#fff;border:1px solid #e5e7eb;color:#4b5563;">Make it internal</button>
              <button class="amber">Send anyway</button>
            </span>
          </div>
        </div>
      </div>
      <div class="vnote"><b>Smallest change, a safety net.</b> Keeps today&rsquo;s public default, but intercepts a client-bound message with a confirm strip and a one-click &ldquo;Make it internal&rdquo; escape. Catches most accidents &mdash; but the default is still client-visible, so a hasty &ldquo;Send anyway&rdquo; can still leak.</div>
    </div>
    '''

# ---------- VARIANT D: color-coded composer, visual only ----------
def variant_d():
    return '''
    <div class="variant">
      <div class="vhead"><span class="opt">D</span><span class="vtitle">Color-coded composer &mdash; always see who gets it</span></div>
      <div class="vbody">
        ''' + thread_preview() + '''
        <div class="composer state-client">
          <div class="cbanner client">&#128065; Visible to client
            <span class="chip internal" style="margin-left:auto;">or &#128274; Staff only when toggled</span>
          </div>
          <textarea class="ta" rows="2" placeholder="Type a message&hellip;"></textarea>
          <div class="cfoot">
            <label class="chk"><input type="checkbox" /> &#128274; Internal (staff only)</label>
            <button class="btn client">Send</button>
          </div>
        </div>
      </div>
      <div class="vnote"><b>Pure visual cue, same default.</b> The whole composer is tinted by audience (teal = client, slate = staff) with a persistent chip, so the recipient is always obvious. Low friction, but the default is still client-visible &mdash; it <i>reduces</i> risk rather than eliminating it.</div>
    </div>
    '''

table = '''
  <div class="tablewrap">
    <table>
      <thead>
        <tr><th>Option</th><th>Default audience</th><th>If you forget, the result is&hellip;</th><th>Leak eliminated?</th><th>Friction</th><th>Change size</th></tr>
      </thead>
      <tbody>
        <tr class="recommended">
          <td><b>A</b> &mdash; Safe by default</td>
          <td><span class="tag g">Staff only</span></td>
          <td>Under-share: client just doesn&rsquo;t see it <span class="yes">&larr; safe</span></td>
          <td><span class="yes">Yes &mdash; yes</span></td>
          <td>Low (one toggle)</td><td>Medium</td>
        </tr>
        <tr>
          <td><b>B</b> &mdash; Segmented audience</td>
          <td><span class="tag g">Staff only</span> (explicit)</td>
          <td>Stays staff-only <span class="yes">&larr; safe</span></td>
          <td><span class="yes">Yes &mdash; yes</span></td>
          <td>Medium (must pick)</td><td>Medium</td>
        </tr>
        <tr>
          <td><b>C</b> &mdash; Confirm on client-send</td>
          <td><span class="tag y">Client visible</span></td>
          <td>Caught by a confirm, but a hasty &ldquo;Send anyway&rdquo; can still leak <span class="no">&rarr; risk</span></td>
          <td><span class="no">Reduces only</span></td>
          <td>Low</td><td>Small</td>
        </tr>
        <tr>
          <td><b>D</b> &mdash; Color-coded composer</td>
          <td><span class="tag y">Client visible</span></td>
          <td>Still client-visible &mdash; visual reminder only <span class="no">&rarr; risk</span></td>
          <td><span class="no">Reduces only</span></td>
          <td>Low</td><td>Small</td>
        </tr>
      </tbody>
    </table>
  </div>
'''

html = f'''<!DOCTYPE html>
<html lang="en"><head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>Messages composer — internal vs client</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link href="https://fonts.googleapis.com/css2?family=Raleway:wght@400;500;600;700&display=swap" rel="stylesheet">
<style>{CSS}</style>
</head>
<body>
  <h1>Messages &mdash; making &ldquo;internal to client&rdquo; a non-event</h1>
  <p class="sub">The current composer defaults to <b>client-visible</b> with a tiny &ldquo;Internal&rdquo; checkbox below the Send button &mdash; so the mistake that hurts is <i>forgetting the box</i>. The options below change where that mistake lands. A and B make the default <b>staff-only</b>, so a slip can only under-share (safe); C and D keep today&rsquo;s default but add a guard. Pick one and I&rsquo;ll wire it into the Messages inbox and the Project Detail Messages tab.</p>
  <div class="grid">
    {variant_a()}
    {variant_b()}
    {variant_c()}
    {variant_d()}
  </div>
  {table}
</body></html>
'''

os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "w") as f:
    f.write(html)
print("wrote", OUT, len(html), "bytes")
