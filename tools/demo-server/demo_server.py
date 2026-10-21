#!/usr/bin/env python3
"""
A fake Paperclip server with a made-up company, for screenshots and trying the app without a real
instance. Standard library only.

    python3 tools/demo-server/demo_server.py --port 3100

Then point the app at http://<host>:3100 (on an emulator: `adb reverse tcp:3100 tcp:3100` and use
http://localhost:3100). Any email and password signs in. Writes are accepted and kept in memory.

Agent avatars are Paperclip's own artwork, so they aren't bundled. Pass --avatars-from with a real
Paperclip URL to proxy them; otherwise agents show initials.
"""
import argparse
import base64
import hashlib
import json
import re
import socket
import struct
import threading
import time
import urllib.parse
import urllib.request
import uuid
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

NOW = datetime.now(timezone.utc)


def ago(minutes=0, hours=0, days=0):
    t = NOW - timedelta(minutes=minutes, hours=hours, days=days)
    return t.strftime("%Y-%m-%dT%H:%M:%S.") + f"{t.microsecond // 1000:03d}Z"


def ahead(minutes=0, hours=0, days=0):
    return ago(minutes=-minutes, hours=-hours, days=-days)


def uid(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "paperclip-demo/" + name))


CID = uid("company")
ME = "user-alex"
COMPANY = {
    "id": CID, "name": "Lumen Labs", "description": "Makers of Orbit, a shared calendar for small teams.",
    "status": "active", "issuePrefix": "LUM", "budgetMonthlyCents": 60000, "spentMonthlyCents": 21840,
}
USER = {"id": ME, "email": "alex@lumen.example", "name": "Alex Rivera", "image": None}
USERS = {"users": [
    {"principalId": ME, "status": "active", "user": USER},
    {"principalId": "user-sam", "status": "active", "user": {"id": "user-sam", "email": "sam@lumen.example", "name": "Sam Okafor", "image": None}},
]}

# ---------------------------------------------------------------- agents

AGENT_SPECS = [
    # key, name, role, title, adapter, model, palette, status, reportsTo
    ("atlas", "Atlas Opus", "cto", "Tech Lead (orchestrator)", "claude_local", "claude-opus-5-5", "orchid-peach", "running", None),
    ("nova", "Nova Opus", "security", "Security & Ops lead", "claude_local", "claude-opus-5-5", "deep-tide", "idle", "atlas"),
    ("kai", "Kai Codex", "engineer", "Backend engineer", "codex_local", "gpt-5.5-codex", "electric-grove", "running", "atlas"),
    ("mira", "Mira Sonnet", "engineer", "Frontend engineer", "claude_local", "claude-sonnet-5-5", "solar-flare", "running", "atlas"),
    ("juno", "Juno Gemini", "qa", "QA engineer", "antigravity_local", "gemini-3-pro", "lime-lagoon", "idle", "atlas"),
    ("rex", "Rex Codex", "devops", "Release engineer", "codex_local", "gpt-5.5-codex", "violet-ember", "idle", "nova"),
    ("iris", "Iris Haiku", "designer", "Docs & design", "claude_local", "claude-haiku-5-5", "orchid-peach", "idle", "atlas"),
    ("sol", "Sol Fable", "researcher", "Research", "claude_local", "claude-fable-5-1", "deep-tide", "paused", "atlas"),
]
A = {k: uid("agent/" + k) for k, *_ in AGENT_SPECS}
AGENTS = []
for i, (k, name, role, title, adapter, model, palette, status, boss) in enumerate(AGENT_SPECS):
    AGENTS.append({
        "id": A[k], "companyId": CID, "name": name, "urlKey": k, "role": role, "title": title, "icon": None,
        "avatarUrl": f"/api/agent-avatars/cap-v1/{palette}/rest.png?size=512&scale=1",
        "status": status, "reportsTo": A[boss] if boss else None, "adapterType": adapter,
        "adapterConfig": {"model": model, "cwd": "/srv/orbit"},
        "budgetMonthlyCents": 0, "spentMonthlyCents": 0,
        "pauseReason": "manual" if status == "paused" else None, "pausedAt": ago(hours=5) if status == "paused" else None,
        "errorReason": None, "lastHeartbeatAt": ago(minutes=2 + i * 7), "createdAt": ago(days=40 - i),
    })
AGENT_BY_ID = {a["id"]: a for a in AGENTS}

# ---------------------------------------------------------------- projects, goals, labels

P = {k: uid("project/" + k) for k in ["web", "api", "mobile", "ops"]}
PROJECTS = [
    {"id": P["web"], "name": "orbit-web", "description": "The Orbit web app (React).", "status": "in_progress", "color": "#7c3aed", "leadAgentId": A["mira"], "targetDate": ahead(days=24)[:10]},
    {"id": P["api"], "name": "orbit-api", "description": "Sync, sharing and billing services.", "status": "in_progress", "color": "#0ea5e9", "leadAgentId": A["kai"], "targetDate": ahead(days=24)[:10]},
    {"id": P["mobile"], "name": "orbit-mobile", "description": "iOS and Android apps.", "status": "planned", "color": "#22c55e", "leadAgentId": A["atlas"], "targetDate": None},
    {"id": P["ops"], "name": "Infrastructure", "description": "Hosting, CI, backups and on-call.", "status": "in_progress", "color": "#f59e0b", "leadAgentId": A["nova"], "targetDate": None},
]
PROJECT_BY_ID = {p["id"]: p for p in PROJECTS}
for p in PROJECTS:
    p.update({"companyId": CID, "archivedAt": None, "updatedAt": ago(hours=3)})

GOALS = [
    {"id": uid("goal/launch"), "title": "Ship Orbit 2.0 by November", "description": "Offline mode, team billing and calendar sharing.", "level": "company", "status": "active", "parentId": None, "ownerAgentId": A["atlas"]},
    {"id": uid("goal/reliability"), "title": "99.9% sync availability", "description": None, "level": "team", "status": "active", "parentId": uid("goal/launch"), "ownerAgentId": A["nova"]},
]

L = {k: uid("label/" + k) for k in ["bug", "security", "ux", "perf"]}
LABELS = [
    {"id": L["bug"], "name": "bug", "color": "#ef4444"},
    {"id": L["security"], "name": "security", "color": "#f97316"},
    {"id": L["ux"], "name": "ux", "color": "#8b5cf6"},
    {"id": L["perf"], "name": "performance", "color": "#0ea5e9"},
]

# ---------------------------------------------------------------- issues

ISSUES = {}
BY_NUM = {}


def issue(n, title, status, priority, assignee=None, parent=None, project="web", blocked_by=(), labels=(),
          mins_ago=60, user=None, desc=None, origin=None, created_days=None):
    iid = uid(f"issue/{n}")
    created = ago(days=created_days if created_days is not None else max(1, (170 - n) // 3))
    upd = ago(minutes=mins_ago)
    i = {
        "id": iid, "companyId": CID, "identifier": f"LUM-{n}", "issueNumber": n, "title": title,
        "description": desc, "status": status, "priority": priority,
        "projectId": P[project] if project else None, "goalId": GOALS[0]["id"],
        "parentId": BY_NUM[parent]["id"] if parent else None,
        "assigneeAgentId": A[assignee] if assignee else None, "assigneeUserId": user,
        "createdByAgentId": A["atlas"], "createdByUserId": None,
        "startedAt": created if status not in ("todo", "backlog") else None,
        "completedAt": upd if status == "done" else None,
        "createdAt": created, "updatedAt": upd, "lastActivityAt": upd,
        "labelIds": [L[x] for x in labels], "labels": [lb for lb in LABELS if lb["id"] in [L[x] for x in labels]],
        "blockedBy": [], "originKind": origin, "isUnreadForMe": False,
    }
    if project:
        p = PROJECT_BY_ID[P[project]]
        i["project"] = {"id": p["id"], "name": p["name"], "color": p["color"]}
    ISSUES[iid] = i
    BY_NUM[n] = i
    i["_blockedByNums"] = list(blocked_by)
    return i


# Older finished work.
done_titles = [
    ("Set up CI with preview deploys", "rex", "ops"), ("Postgres connection pooling", "kai", "api"),
    ("Week view drag-to-reschedule", "mira", "web"), ("Fix timezone drift on recurring events", "kai", "api"),
    ("Accessibility audit of the event editor", "juno", "web"), ("Rate-limit the public booking endpoint", "nova", "api"),
    ("Brand refresh for marketing site", "iris", "web"), ("Migrate file uploads to object storage", "rex", "ops"),
    ("Keyboard shortcuts cheat sheet", "iris", "web"), ("Weekly digest email", "kai", "api"),
    ("Session expiry handling in the web app", "mira", "web"), ("SSO with Google Workspace", "nova", "api"),
    ("Snapshot tests for calendar grid", "juno", "web"), ("Status page", "rex", "ops"),
    ("Speed up first paint on the agenda view", "mira", "web"), ("Interviews: how teams share calendars", "sol", "web"),
    ("Remove legacy v1 sync endpoints", "kai", "api"), ("Backup restore drill", "nova", "ops"),
]
for k, (t, who, proj) in enumerate(done_titles):
    issue(100 + k, t, "done", ["medium", "low", "high", "medium"][k % 4], who, project=proj, mins_ago=60 * 24 * (12 - k // 2))
issue(118, "Pricing experiment write-up", "cancelled", "low", "sol", project="web", mins_ago=60 * 24 * 6)

# Performance pass (nearly finished).
issue(120, "Performance pass on calendar queries", "in_review", "high", "atlas", project="api", labels=["perf"], mins_ago=50,
      desc="Calendar loads for teams over 50 people take 2–4 s. Target: p95 under 400 ms.")
issue(121, "Profile slow calendar queries", "done", "high", "kai", 120, "api", labels=["perf"], mins_ago=60 * 30)
issue(122, "Composite index on events(user_id, starts_at)", "done", "high", "kai", 120, "api", labels=["perf"], mins_ago=60 * 26)
issue(123, "Cache free/busy lookups", "in_review", "medium", "kai", 120, "api", labels=["perf"], mins_ago=55)
issue(124, "Load test: 5k concurrent users", "todo", "high", "juno", 120, "ops", labels=["perf"], mins_ago=60 * 3)

# The 2.0 launch.
issue(140, "Launch Orbit 2.0", "in_progress", "high", "atlas", project="web", mins_ago=8,
      desc="Umbrella for the 2.0 release: offline mode, team billing and calendar sharing.")
issue(141, "Offline mode", "in_progress", "high", "atlas", 140, "web", mins_ago=6,
      desc="Orbit should keep working without a connection and sync cleanly when it comes back.")
issue(142, "Design doc: offline sync and conflict rules", "done", "high", "iris", 141, "web", mins_ago=60 * 28)
issue(143, "Local event store on IndexedDB", "in_progress", "high", "kai", 141, "web", mins_ago=1)
issue(144, "Conflict resolution for concurrent edits", "todo", "high", "kai", 141, "api", blocked_by=[143], mins_ago=60 * 5)
issue(145, "Sync status indicator in the header", "todo", "medium", "mira", 141, "web", labels=["ux"], mins_ago=60 * 7)
issue(146, "E2E tests: airplane-mode scenarios", "backlog", "medium", "juno", 141, "web", blocked_by=[143, 144], mins_ago=60 * 20)
issue(147, "Team billing with Stripe", "in_progress", "high", "atlas", 140, "api", mins_ago=25)
issue(148, "Stripe checkout and webhooks", "in_review", "high", "kai", 147, "api", mins_ago=25)
issue(149, "Seat-based pricing page", "todo", "medium", "mira", 147, "web", mins_ago=60 * 9)
issue(150, "Invoice emails", "backlog", "low", None, 147, "api", mins_ago=60 * 40)
issue(151, "Security review: payment webhooks", "todo", "critical", "nova", 147, "api", labels=["security"], mins_ago=30)
issue(152, "Enable billing in production", "blocked", "high", "rex", 147, "ops", blocked_by=[151, 148], mins_ago=60 * 2)
issue(153, "Calendar sharing permissions", "in_progress", "medium", "atlas", 140, "web", mins_ago=14)
issue(154, "Permission model and API", "done", "high", "kai", 153, "api", mins_ago=60 * 22)
issue(155, "Share dialog UI", "in_progress", "medium", "mira", 153, "web", labels=["ux"], mins_ago=0)
issue(156, "Audit log for shared calendars", "todo", "medium", None, 153, "api", labels=["security"], mins_ago=60 * 11)
issue(157, "QA: permission matrix", "backlog", "medium", "juno", 153, "web", blocked_by=[155], mins_ago=60 * 11)
issue(158, "Release 2.0 checklist", "backlog", "medium", "rex", 140, "ops", blocked_by=[141, 147, 153], mins_ago=60 * 30)

# Standalone work.
issue(160, "Point api.orbit.app at the new load balancer", "in_review", "high", "nova", project="ops", mins_ago=4,
      desc="The new load balancer is live in staging. Production DNS still points at the old one.")
issue(161, "Default reminder time for new users", "in_review", "medium", "atlas", project="web", mins_ago=18)
issue(162, "Dependency updates (weekly)", "done", "low", "rex", project="ops", origin="routine_execution", mins_ago=60 * 16)
issue(163, "Nightly backup check", "done", "medium", "nova", project="ops", origin="routine_execution", mins_ago=60 * 9)
issue(164, "Fix flaky test in calendar export", "in_progress", "medium", "juno", project="web", labels=["bug"], mins_ago=3)
issue(165, "Contrast issues on the settings page in dark mode", "todo", "low", "mira", project="web", labels=["ux", "bug"], mins_ago=60 * 26)
issue(166, "502s on /sync during deploys", "blocked", "high", "rex", project="ops", labels=["bug"], mins_ago=60 * 4)
issue(167, "Onboarding guide for new contributors", "backlog", "low", "iris", project="web", mins_ago=60 * 50)
issue(168, "Research: calendar apps for small teams", "done", "medium", "sol", project="web", mins_ago=60 * 70)
issue(169, "Review the Q4 roadmap", "todo", "high", None, project=None, user=ME, mins_ago=60 * 6)
issue(170, "Android widget for today's agenda", "backlog", "medium", None, project="mobile", mins_ago=60 * 80)
issue(171, "Push notifications for invites", "backlog", "medium", None, project="mobile", mins_ago=60 * 80)

for i in ISSUES.values():
    i["blockedBy"] = [{"id": BY_NUM[n]["id"], "identifier": BY_NUM[n]["identifier"], "title": BY_NUM[n]["title"],
                       "status": BY_NUM[n]["status"], "priority": BY_NUM[n]["priority"]} for n in i.pop("_blockedByNums")]
BY_NUM[166]["blockedBy"] = []
BY_NUM[161]["isUnreadForMe"] = True
BY_NUM[160]["isUnreadForMe"] = True


def ref(i):
    return {"id": i["id"], "identifier": i["identifier"], "title": i["title"], "status": i["status"], "priority": i["priority"]}


def full_issue(i):
    out = dict(i)
    anc, p = [], i.get("parentId")
    while p and p in ISSUES:
        anc.insert(0, ref(ISSUES[p]))
        p = ISSUES[p].get("parentId")
    out["ancestors"] = anc
    out["blocks"] = [ref(x) for x in ISSUES.values() if any(b["id"] == i["id"] for b in x.get("blockedBy") or [])]
    out["documentSummaries"] = [{"id": d["id"], "key": d["key"], "title": d["title"], "latestRevisionNumber": d["latestRevisionNumber"], "updatedAt": d["updatedAt"]}
                                for d in DOCUMENTS.get(i["id"], [])]
    live = [r for r in RUNS if r["contextSnapshot"].get("issueId") == i["id"] and r["status"] in ("running", "queued")]
    if live:
        out["activeRun"] = {"id": live[0]["id"], "status": live[0]["status"], "agentId": live[0]["agentId"], "startedAt": live[0]["startedAt"]}
    return out


# ---------------------------------------------------------------- runs and transcripts

RUNS = []
LOGS = {}


def claude_log(model, steps, cost=0.42):
    """steps: ("text", s) | ("think", s) | ("tool", name, input, result)."""
    lines, seq, t = [], 0, NOW - timedelta(minutes=6)

    def rec(obj, stream="stdout"):
        nonlocal seq, t
        seq += 1
        t += timedelta(seconds=7)
        chunk = obj if isinstance(obj, str) else json.dumps(obj)
        lines.append(json.dumps({"ts": t.strftime("%Y-%m-%dT%H:%M:%S.000Z"), "stream": stream, "chunk": chunk + "\n", "seq": seq}))

    rec({"type": "system", "subtype": "init", "model": model, "cwd": "/srv/orbit", "session_id": "demo"})
    for k, s in enumerate(steps):
        if s[0] == "text":
            rec({"type": "assistant", "message": {"model": model, "role": "assistant", "content": [{"type": "text", "text": s[1]}]}})
        elif s[0] == "think":
            rec({"type": "assistant", "message": {"model": model, "role": "assistant", "content": [{"type": "thinking", "thinking": s[1]}]}})
        elif s[0] == "tool":
            tid = f"toolu_{k:04d}"
            rec({"type": "assistant", "message": {"model": model, "role": "assistant", "content": [{"type": "tool_use", "id": tid, "name": s[1], "input": s[2]}]}})
            if s[3] is not None:
                rec({"type": "user", "message": {"role": "user", "content": [{"type": "tool_result", "tool_use_id": tid, "content": s[3]}]}})
    if cost is not None:
        rec({"type": "result", "subtype": "success", "is_error": False, "result": "", "total_cost_usd": cost,
             "usage": {"input_tokens": 18342, "output_tokens": 4120, "cache_read_input_tokens": 512330}})
    return "\n".join(lines) + "\n"


def codex_log(steps, done=True):
    lines, seq, t = [], 0, NOW - timedelta(minutes=12)

    def rec(obj):
        nonlocal seq, t
        seq += 1
        t += timedelta(seconds=9)
        lines.append(json.dumps({"ts": t.strftime("%Y-%m-%dT%H:%M:%S.000Z"), "stream": "stdout", "chunk": json.dumps(obj) + "\n", "seq": seq}))

    rec({"type": "thread.started", "thread_id": "demo", "model": "gpt-5.5-codex"})
    for k, s in enumerate(steps):
        if s[0] == "text":
            rec({"type": "item.completed", "item": {"id": f"i{k}", "type": "agent_message", "text": s[1]}})
        elif s[0] == "cmd":
            rec({"type": "item.started", "item": {"id": f"i{k}", "type": "command_execution", "command": s[1]}})
            if s[2] is not None:
                rec({"type": "item.completed", "item": {"id": f"i{k}", "type": "command_execution", "command": s[1], "aggregated_output": s[2], "exit_code": 0, "status": "completed"}})
    if done:
        rec({"type": "turn.completed", "usage": {"input_tokens": 40211, "cached_input_tokens": 310022, "output_tokens": 6011}})
    return "\n".join(lines) + "\n"


def run(key, agent, issue_num, status, mins_ago, log, msg=None, tool=None, snippet=None, err=None, source="assignment"):
    rid = uid("run/" + key)
    started = ago(minutes=mins_ago)
    r = {
        "id": rid, "companyId": CID, "agentId": A[agent], "status": status, "invocationSource": source,
        "triggerDetail": "system", "startedAt": started, "createdAt": started,
        "finishedAt": None if status in ("running", "queued") else ago(minutes=max(0, mins_ago - 6)),
        "error": err, "errorCode": "adapter_failed" if err else None,
        "logBytes": len(log.encode()), "lastOutputBytes": len(log.encode()),
        "contextSnapshot": {"issueId": BY_NUM[issue_num]["id"]} if issue_num else {},
        "currentStatusMessage": msg, "currentToolName": tool, "lastAssistantSnippet": snippet,
        "livenessState": "active" if status == "running" else "completed",
        "usageJson": {"inputTokens": 18342, "outputTokens": 4120, "cachedInputTokens": 512330, "costUsd": 0.42},
    }
    RUNS.append(r)
    LOGS[rid] = log
    return r


MIRA_STEPS = [
    ("think", "The share dialog needs the role picker from the permission model in LUM-154. I'll check the API shape first."),
    ("tool", "Bash", {"command": "rg -n \"role\" packages/api/src/sharing/permissions.ts | head"},
     "12:export type ShareRole = 'viewer' | 'editor' | 'owner'\n31:export function canGrant(actor: ShareRole, target: ShareRole) {"),
    ("text", "The API exposes three roles and `canGrant()`. I'll mirror that in the dialog so people can only grant roles up to their own."),
    ("tool", "Read", {"file_path": "apps/web/src/components/share/ShareDialog.tsx"}, "export function ShareDialog({ calendar }: Props) {\n  const [invites, setInvites] = useState<Invite[]>([])\n  …"),
    ("tool", "Edit", {"file_path": "apps/web/src/components/share/ShareDialog.tsx", "old_string": "<RoleSelect />", "new_string": "<RoleSelect max={myRole} />"}, "The file has been updated."),
    ("tool", "Bash", {"command": "pnpm --filter web test share"},
     " ✓ ShareDialog › lists current members (41 ms)\n ✓ ShareDialog › hides roles above your own (18 ms)\n ✓ ShareDialog › sends one invite per email (22 ms)\n\nTest Files  1 passed (1)\n     Tests  3 passed (3)"),
    ("text", "Tests pass. Next: the empty state and the copy-link row, then screenshots for review."),
    ("tool", "Bash", {"command": "pnpm --filter web storybook:shot ShareDialog"}, None),
]
run("mira-155", "mira", 155, "running", 6, claude_log("claude-sonnet-5-5", MIRA_STEPS, cost=None),
    msg="Capturing screenshots", tool="Bash", snippet="Tests pass. Next: the empty state and the copy-link row…")

KAI_STEPS = [
    ("text", "Picking up LUM-143. Plan: an `EventStore` over IndexedDB with an outbox table for unsynced writes."),
    ("cmd", "ls apps/web/src/sync", "client.ts\nindex.ts\nqueue.ts"),
    ("cmd", "pnpm add idb@8", "+ idb 8.0.0\nDone in 2.1s"),
    ("text", "Added `idb`. Writing the store and its migration from the in-memory cache now."),
    ("cmd", "pnpm --filter web test sync", None),
]
run("kai-143", "kai", 143, "running", 12, codex_log(KAI_STEPS, done=False),
    msg="Running sync tests", tool="command", snippet="Writing the store and its migration from the in-memory cache now.")

run("juno-164", "juno", 164, "queued", 1, "", msg="Queued")

ATLAS_STEPS = [
    ("text", "Checking the 2.0 tree for anything stuck before the morning report."),
    ("tool", "Bash", {"command": "paperclipai issue list --parent LUM-140 --status blocked"}, "LUM-152  Enable billing in production  blocked  (by LUM-151, LUM-148)"),
    ("text", "LUM-152 waits on Nova's webhook review (LUM-151, critical). I've pinged Nova and left LUM-148 in review."),
]
run("atlas-140", "atlas", 140, "succeeded", 35, claude_log("claude-opus-5-5", ATLAS_STEPS, cost=0.18), source="timer")
run("nova-160", "nova", 160, "succeeded", 9, claude_log("claude-opus-5-5", [("text", "Staging is healthy on the new load balancer. Asked Alex to switch the production record.")], cost=0.09))
run("rex-166", "rex", 166, "failed", 240, codex_log([("cmd", "kubectl rollout status deploy/sync", "error: deployment \"sync\" exceeded its progress deadline")]),
    err="Deployment exceeded its progress deadline")
for k, (agent, n, m) in enumerate([("kai", 148, 30), ("kai", 123, 70), ("iris", 142, 60 * 28), ("nova", 163, 60 * 9), ("rex", 162, 60 * 16), ("juno", 124, 60 * 3)]):
    run(f"hist-{k}", agent, n, "succeeded", m, claude_log("claude-opus-5-5", [("text", "Done; see the issue thread.")], cost=0.05))
RUN_BY_ID = {r["id"]: r for r in RUNS}


def live_run(r):
    a = AGENT_BY_ID[r["agentId"]]
    return {k: r.get(k) for k in ("id", "status", "agentId", "startedAt", "finishedAt", "createdAt", "currentStatusMessage", "currentToolName", "lastAssistantSnippet", "invocationSource")} | {
        "agentName": a["name"], "adapterType": a["adapterType"], "issueId": r["contextSnapshot"].get("issueId"), "lastEventAt": ago(minutes=0)}


# ---------------------------------------------------------------- comments, interactions, documents

COMMENTS = {}
INTERACTIONS = {}
DOCUMENTS = {}
WORK_PRODUCTS = {}


def comment(n, who, body, mins_ago, kind=None):
    c = {"id": str(uuid.uuid4()), "issueId": BY_NUM[n]["id"], "body": body, "createdAt": ago(minutes=mins_ago), "updatedAt": ago(minutes=mins_ago)}
    if who == "me":
        c.update(authorType="user", authorUserId=ME)
    else:
        c.update(authorType="agent", authorAgentId=A[who])
    if kind:
        c["presentation"] = {"kind": kind, "tone": "neutral"}
    COMMENTS.setdefault(BY_NUM[n]["id"], []).append(c)


comment(160, "nova", "Staging has run on the new load balancer for 48 h with no errors, and p95 latency dropped from 210 ms to 140 ms.\n\nThe last step is a DNS change in the registrar, which only you can make. I've put the exact steps in the card below.", 9)
comment(160, "atlas", "Nova owns this one. Alex, it's about 5 minutes and safe to undo.", 7)
comment(143, "kai", "Started on the store. Using `idb` instead of Dexie: it's 1.2 kB and we only need two tables.\n\n- `events`: mirror of the server rows\n- `outbox`: unsynced writes, replayed in order", 40)
comment(143, "atlas", "Sounds good. Keep the outbox replay idempotent; LUM-144 depends on it.", 32)
comment(141, "atlas", "**Status:** the design is signed off (LUM-142). Kai is on the local store; conflict resolution starts when that lands. Mira can do the sync indicator in parallel.", 6)
comment(148, "kai", "PR #212 is ready: checkout session, the `invoice.paid` and `customer.subscription.updated` webhooks, and signature checks. Waiting on Nova's review (LUM-151).", 25)
comment(161, "atlas", "Three teams in the beta asked for this. I'd rather you choose than guess: it changes what every new user sees on day one.", 18)
comment(155, "mira", "Role picker done; it only offers roles up to your own. Working on the copy-link row.", 2)
comment(148, "nova", "Review of PR #212 is done. Two things to fix before merge, the rest can follow:\n\n"
        "| Severity | File | Finding | Suggested fix |\n"
        "|:--|:--|:--|:--|\n"
        "| **High** | `billing/webhooks.ts` | `invoice.paid` is processed twice when Stripe retries after a timeout | Store the event id and skip ones already seen |\n"
        "| Medium | `billing/checkout.ts` | The success URL trusts the `plan` query parameter | Read the plan from the session on the server |\n"
        "| Low | `billing/prices.ts` | Price ids are hard-coded for test mode | Load them from config per environment |\n"
        "| Low | `README.md` | Missing local webhook setup steps | Add the `stripe listen` command |\n\n"
        "Happy to re-review once the first two are in.", 20)

INTERACTIONS[BY_NUM[160]["id"]] = [{
    "id": uid("int/dns"), "issueId": BY_NUM[160]["id"], "kind": "request_confirmation", "status": "pending",
    "title": "Switch api.orbit.app to the new load balancer (~5 min)", "summary": "One DNS change in the registrar.",
    "createdByAgentId": A["nova"], "createdAt": ago(minutes=9),
    "payload": {
        "version": 1, "allowDeclineReason": True,
        "prompt": "Please update one DNS record. Staging has been healthy on the new load balancer for 48 h. Accept when it's done and I'll verify and close this.",
        "detailsMarkdown": (
            "**Time:** about 5 minutes\n\n"
            "1. In the registrar, open **orbit.app → DNS**.\n"
            "2. Edit the `api` CNAME:\n\n"
            "   | Field | Value |\n   |---|---|\n   | Target | `lb-2.orbit.app` |\n   | TTL | `300` |\n\n"
            "3. Check it from any terminal:\n\n"
            "```bash\ndig +short api.orbit.app CNAME\n# expect: lb-2.orbit.app.\ncurl -s https://api.orbit.app/health\n# expect: {\"ok\":true}\n```\n\n"
            "**Undo:** set the target back to `lb-1.orbit.app`."
        ),
    },
}]
INTERACTIONS[BY_NUM[161]["id"]] = [{
    "id": uid("int/reminder"), "issueId": BY_NUM[161]["id"], "kind": "ask_user_questions", "status": "pending",
    "title": "Default reminder for new users", "createdByAgentId": A["atlas"], "createdAt": ago(minutes=18),
    "payload": {"version": 1, "questions": [
        {"id": "default", "prompt": "When should new users get their first reminder before an event?", "selectionMode": "single", "required": True,
         "options": [{"id": "10", "label": "10 minutes before", "recommended": True}, {"id": "30", "label": "30 minutes before"}, {"id": "none", "label": "No reminder by default"}]},
        {"id": "channels", "prompt": "Which channels should be on by default?", "selectionMode": "multi", "required": False,
         "options": [{"id": "push", "label": "Push"}, {"id": "email", "label": "Email"}, {"id": "sms", "label": "SMS"}]},
    ]},
}]
INTERACTIONS[BY_NUM[148]["id"]] = [{
    "id": uid("int/stripe"), "issueId": BY_NUM[148]["id"], "kind": "request_confirmation", "status": "accepted",
    "title": "Use Stripe Checkout instead of a custom form", "createdByAgentId": A["kai"], "createdAt": ago(hours=30), "resolvedAt": ago(hours=29),
    "resolvedByUserId": ME, "payload": {"version": 1, "prompt": "Checkout keeps card data off our servers (PCI SAQ A). OK to go with it?"},
    "result": {"version": 1, "outcome": "accepted"},
}]

DOCUMENTS[BY_NUM[141]["id"]] = [{
    "id": uid("doc/plan"), "key": "plan", "title": "Plan", "latestRevisionId": uid("rev/plan"), "latestRevisionNumber": 3,
    "updatedAt": ago(hours=20), "updatedByAgentId": A["atlas"],
    "body": "# Offline mode\n\n## Goal\nOrbit keeps working without a connection and syncs without losing edits.\n\n"
            "## Steps\n1. **Local store** (LUM-143, Kai): IndexedDB mirror plus an outbox of unsynced writes.\n"
            "2. **Conflicts** (LUM-144, Kai): last-writer-wins per field; deletes win over edits.\n"
            "3. **UI** (LUM-145, Mira): a sync indicator in the header.\n4. **Tests** (LUM-146, Juno): airplane-mode scenarios.\n\n"
            "## Risks\n- Safari storage limits on iOS: cap the local window at 90 days.\n",
}]
WORK_PRODUCTS[BY_NUM[148]["id"]] = [{
    "id": uid("wp/212"), "type": "pull_request", "provider": "github", "title": "feat(billing): Stripe Checkout and webhooks",
    "url": "https://github.com/lumen-labs/orbit/pull/212", "status": "ready_for_review", "reviewState": "needs_review", "isPrimary": True,
    "summary": "+612 −48 across 14 files", "createdAt": ago(minutes=25),
}]

# ---------------------------------------------------------------- feeds

ACTIVITY = []


def act(mins, actor, action, n=None, details=None, entity="issue", eid=None):
    i = BY_NUM.get(n) if n else None
    d = {"identifier": i["identifier"], "title": i["title"]} if i else {}
    d.update(details or {})
    ACTIVITY.append({
        "id": str(uuid.uuid4()), "actorType": "user" if actor == "me" else "agent", "actorId": ME if actor == "me" else A[actor],
        "agentId": None if actor == "me" else A[actor], "action": action, "entityType": entity,
        "entityId": eid or (i["id"] if i else None), "details": d, "createdAt": ago(minutes=mins),
    })


act(0, "mira", "issue.comment_added", 155)
act(1, "kai", "issue.checked_out", 143)
act(3, "juno", "issue.updated", 164, {"status": "in_progress"})
act(4, "nova", "issue.updated", 160, {"status": "in_review"})
act(9, "nova", "issue.thread_interaction_created", 160)
act(18, "atlas", "issue.thread_interaction_created", 161)
act(25, "kai", "issue.work_product_created", 148)
act(25, "kai", "issue.updated", 148, {"status": "in_review"})
act(30, "atlas", "issue.updated", 151, {"priority": "critical"})
act(40, "kai", "issue.comment_added", 143)
act(55, "kai", "issue.updated", 123, {"status": "in_review"})
act(120, "rex", "issue.updated", 152, {"status": "blocked"})
act(180, "me", "issue.created", 169)
act(240, "rex", "issue.updated", 166, {"status": "blocked"})
act(300, "sol", "agent.paused", entity="agent", eid=A["sol"], details={"name": "Sol Fable"})
act(60 * 9, "nova", "issue.updated", 163, {"status": "done"})
act(60 * 20, "atlas", "issue.document_upserted", 141)
act(60 * 22, "kai", "issue.updated", 154, {"status": "done"})

APPROVAL_ID = uid("approval/hire")
APPROVALS = [{
    "id": APPROVAL_ID, "type": "hire_agent", "status": "pending", "requestedByAgentId": A["atlas"], "createdAt": ago(hours=2), "updatedAt": ago(hours=2),
    "payload": {"agent": {"name": "Tess Sonnet", "role": "engineer", "title": "Mobile engineer"}, "adapterType": "claude_local",
                "reason": "orbit-mobile starts next week and nobody on the team owns React Native."},
}]


DECISION_ID = uid("decision/rollout")
DECISIONS = [{
    "id": DECISION_ID, "title": "How should the offline mode ship?", "status": "open", "originAgentId": None, "originIssueId": None,
    "body": "Offline mode is ready behind a flag. Pick how it reaches users:\n\n"
            "| Option | Reach in week 1 | Risk | Rollback |\n|:--|--:|:--|:--|\n"
            "| Beta group | ~120 users | Low | Flip the flag |\n| 10% rollout | ~2,400 users | Medium | Flip the flag; cached data stays |\n"
            "| Everyone | ~24,000 users | Higher | Needs an app update |",
    "options": [
        {"id": "beta", "label": "Beta group first", "description": "Atlas creates the beta task and assigns Kai.", "style": "primary", "effects": [{"type": "comment_on_issue"}]},
        {"id": "ten", "label": "10% rollout", "description": None, "style": "default", "effects": [{"type": "comment_on_issue"}]},
        {"id": "hold", "label": "Hold for now", "description": "Nothing ships; the task goes back to backlog.", "style": "destructive", "effects": [{"type": "update_issue_status"}]},
    ],
    "inputs": [{"id": "note", "label": "Note for Atlas", "placeholder": "Anything they should know", "required": False}],
    "expiresAt": ahead(days=2), "createdAt": ago(minutes=50), "targetChanged": {}, "metadata": {},
}, {
    "id": uid("decision/old"), "title": "Use Postgres or SQLite for the sync log?", "status": "decided", "chosenOptionId": "pg", "executionStatus": "succeeded",
    "options": [{"id": "pg", "label": "Postgres", "effects": []}, {"id": "lite", "label": "SQLite", "effects": []}],
    "body": "", "decidedAt": ago(days=1), "createdAt": ago(days=2), "expiresAt": ago(days=1), "metadata": {},
}]
DISMISSALS = {}
TRIAGE = {}


def attention(params=None):
    params = params or {}
    items = []

    def add(kind, subject, why, sev, mins, origin, detail, rel=None, verbs=None):
        key = f"attention:{subject['id']}"
        dm = DISMISSALS.get(key)
        items.append({"id": f"{kind}:{subject['id']}", "sourceKind": kind, "subject": subject, "relatedIssue": rel, "whyNow": why,
                      "severity": sev, "activityAt": ago(minutes=mins), "createdAt": ago(minutes=mins), "dismissalKey": key,
                      "originAgentName": origin, "inlineResolvable": True, "detail": detail, "decisionVerbs": verbs or [],
                      "dismissal": dm, "decideBy": TRIAGE.get(subject["id"]), "shelf": False, "expiresAt": None})

    d = DECISIONS[0]
    if d["status"] == "open":
        add("decision", {"kind": "decision", "id": d["id"], "title": d["title"], "status": "open", "href": f"/LUM/decisions?decisionId={d['id']}"},
            "Atlas needs a call on the rollout plan", "medium", 50, "Atlas Opus", {"kind": "generic", "summaryExcerpt": "Offline mode is ready behind a flag."},
            None, [{"id": "decide", "label": "Review"}])
        items[-1]["expiresAt"] = d["expiresAt"]
        if d["id"] not in TRIAGE:
            items[-1]["decideBy"] = "today"

    def subj_issue(n):
        i = BY_NUM[n]
        return {"kind": "issue", "id": i["id"], "title": i["title"], "identifier": i["identifier"], "status": i["status"], "href": f"/LUM/issues/{i['identifier']}"}

    dns = INTERACTIONS[BY_NUM[160]["id"]][0]
    if dns["status"] == "pending":
        add("issue_thread_interaction", {"kind": "interaction", "id": dns["id"], "title": dns["title"], "status": "pending", "href": "/LUM/issues/LUM-160"},
            "Confirmation requested on an issue thread", "high", 9, "Nova Opus", {"kind": "confirmation", "promptExcerpt": dns["payload"]["prompt"]}, subj_issue(160))
    q = INTERACTIONS[BY_NUM[161]["id"]][0]
    if q["status"] == "pending":
        add("issue_thread_interaction", {"kind": "interaction", "id": q["id"], "title": q["title"], "status": "pending", "href": "/LUM/issues/LUM-161"},
            "Questions waiting for an answer", "medium", 18, "Atlas Opus", {"kind": "questions", "promptExcerpt": "When should new users get their first reminder?"}, subj_issue(161))
    if APPROVALS[0]["status"] == "pending":
        add("approval", {"kind": "approval", "id": APPROVAL_ID, "title": "Hire Tess Sonnet (Mobile engineer)", "status": "pending"},
            "Atlas wants to hire a mobile engineer", "medium", 120, "Atlas Opus", {"kind": "approval"})
    r = RUN_BY_ID[uid("run/rex-166")]
    add("failed_run", {"kind": "run", "id": r["id"], "title": "Rex Codex run failed", "status": "failed"},
        "Run failed after automatic retries were exhausted", "high", 240, "Rex Codex",
        {"kind": "failed_run", "failureReasonExcerpt": r["error"]}, subj_issue(166))
    add("blocker_attention", subj_issue(152), "Blocked by a critical review (LUM-151)", "medium", 120, "Rex Codex", {"kind": "blocker"})
    if params.get("includeDismissed") != "true":
        items = [i for i in items if not (i["dismissal"] or {}).get("isActive")]
    lim = int(params.get("limit") or 50)
    return {"totalCount": len(items), "deskBadgeCount": len(items), "nextCursor": None, "items": items if params.get("all") == "true" else items[:lim]}


def dashboard():
    open_ = [i for i in ISSUES.values() if i["status"] not in ("done", "cancelled")]
    days = []
    for d in range(13, -1, -1):
        day = (NOW - timedelta(days=d)).strftime("%Y-%m-%d")
        s = 18 + (d * 7) % 13 + (6 if d < 4 else 0)
        f = [0, 1, 0, 2, 0, 0, 1][d % 7]
        days.append({"date": day, "succeeded": s, "failed": f, "recovered": int(d % 3 == 0), "other": 1, "total": s + f + 1 + int(d % 3 == 0)})
    return {
        "agents": {"active": sum(a["status"] == "idle" for a in AGENTS), "running": sum(a["status"] == "running" for a in AGENTS),
                   "paused": sum(a["status"] == "paused" for a in AGENTS), "error": 0},
        "tasks": {"open": len(open_), "inProgress": sum(i["status"] == "in_progress" for i in open_),
                  "blocked": sum(i["status"] == "blocked" for i in open_), "done": sum(i["status"] == "done" for i in ISSUES.values())},
        "costs": {"monthSpendCents": COMPANY["spentMonthlyCents"], "monthBudgetCents": COMPANY["budgetMonthlyCents"],
                  "monthUtilizationPercent": round(100 * COMPANY["spentMonthlyCents"] / COMPANY["budgetMonthlyCents"], 1)},
        "pendingApprovals": sum(a["status"] == "pending" for a in APPROVALS),
        "budgets": {"activeIncidents": 0, "pendingApprovals": 0, "pausedAgents": 1, "pausedProjects": 0},
        "runActivity": days,
    }


COST_BY_AGENT = [
    {"agentId": A[k], "agentName": AGENT_BY_ID[A[k]]["name"], "agentStatus": AGENT_BY_ID[A[k]]["status"], "costCents": c,
     "inputTokens": c * 900, "cachedInputTokens": c * 21000, "outputTokens": c * 230, "apiRunCount": api, "subscriptionRunCount": sub}
    for k, c, api, sub in [("atlas", 6420, 0, 212), ("kai", 5310, 140, 0), ("mira", 3980, 0, 160), ("nova", 2770, 0, 88),
                           ("juno", 1640, 52, 0), ("rex", 1130, 61, 0), ("iris", 410, 0, 37), ("sol", 180, 0, 9)]
]
COST_BY_PROVIDER = [
    {"provider": "anthropic", "biller": "anthropic", "billingType": "subscription_included", "model": "claude-opus-5-5", "costCents": 9190, "inputTokens": 8_300_000, "outputTokens": 2_100_000, "apiRunCount": 0, "subscriptionRunCount": 300},
    {"provider": "anthropic", "biller": "anthropic", "billingType": "subscription_included", "model": "claude-sonnet-5-5", "costCents": 3980, "inputTokens": 3_500_000, "outputTokens": 920_000, "apiRunCount": 0, "subscriptionRunCount": 160},
    {"provider": "openai", "biller": "openai", "billingType": "api", "model": "gpt-5.5-codex", "costCents": 6440, "inputTokens": 5_800_000, "outputTokens": 1_480_000, "apiRunCount": 201, "subscriptionRunCount": 0},
    {"provider": "google", "biller": "google", "billingType": "api", "model": "gemini-3-pro", "costCents": 1640, "inputTokens": 1_470_000, "outputTokens": 380_000, "apiRunCount": 52, "subscriptionRunCount": 0},
]
QUOTAS = {"asOf": ago(minutes=3), "results": [
    {"provider": "anthropic", "ok": True, "windows": [
        {"label": "5-hour", "usedPercent": 46, "resetsAt": ahead(hours=2, minutes=14)},
        {"label": "Weekly", "usedPercent": 71, "resetsAt": ahead(days=2, hours=5)},
        {"label": "Weekly (Opus)", "usedPercent": 58, "resetsAt": ahead(days=2, hours=5)}]},
    {"provider": "openai", "ok": True, "windows": [
        {"label": "5-hour", "usedPercent": 22, "resetsAt": ahead(hours=3, minutes=40)},
        {"label": "Weekly", "usedPercent": 39, "resetsAt": ahead(days=4)}]},
]}
BUDGETS = {"policies": [{"id": uid("budget/co"), "scopeType": "company", "scopeId": CID, "scopeName": "Lumen Labs", "amount": 60000, "observedAmount": 21840, "status": "ok", "windowKind": "calendar_month_utc"}],
           "activeIncidents": [], "pausedAgentCount": 1, "pausedProjectCount": 0, "pendingApprovalCount": 0}

ROUTINES = [
    {"id": uid("routine/deps"), "title": "Dependency updates", "description": "Bump patch and minor versions, run the suite, open one PR.", "status": "active",
     "assigneeAgentId": A["rex"], "projectId": P["ops"], "lastTriggeredAt": ago(hours=16),
     "triggers": [{"id": "t1", "kind": "schedule", "label": "Mondays 06:00", "enabled": True, "cronExpression": "0 6 * * 1", "nextRunAt": ahead(days=1, hours=13)}],
     "lastRun": {"id": "x", "status": "succeeded", "createdAt": ago(hours=16)}, "priority": "medium",
     "concurrencyPolicy": "coalesce_if_active", "catchUpPolicy": "skip_missed", "latestRevisionId": str(uuid.uuid4()), "variables": []},
    {"id": uid("routine/backup"), "title": "Nightly backup check", "description": "Restore last night's backup into a scratch database and compare row counts.", "status": "active",
     "assigneeAgentId": A["nova"], "projectId": P["ops"], "lastTriggeredAt": ago(hours=9),
     "triggers": [{"id": "t2", "kind": "schedule", "label": "Daily 03:00", "enabled": True, "cronExpression": "0 3 * * *", "nextRunAt": ahead(hours=10)}],
     "lastRun": {"id": "y", "status": "succeeded", "createdAt": ago(hours=9)}},
    {"id": uid("routine/report"), "title": "Morning report", "description": "Summarise yesterday's progress and what needs Alex today.", "status": "active",
     "assigneeAgentId": A["atlas"], "projectId": None, "lastTriggeredAt": ago(hours=7),
     "triggers": [{"id": "t3", "kind": "schedule", "label": "Weekdays 08:30", "enabled": True, "cronExpression": "30 8 * * 1-5", "nextRunAt": ahead(hours=16)}],
     "lastRun": {"id": "z", "status": "succeeded", "createdAt": ago(hours=7)}},
]

INSTRUCTIONS = {
    "AGENTS.md": "# {name}\n\nYou are **{name}**, {title} at Lumen Labs.\n\n## How you work\n- Pick up issues assigned to you, smallest unblocked first.\n- Comment with what changed and how you verified it.\n- Escalate to Atlas before asking Alex.\n\nSee `common.md` for team rules.\n",
    "common.md": "# Team rules\n\n- Alex's time is the scarcest resource. When you need Alex to act, give ready-to-run steps and a way to undo.\n- Never merge into `main`.\n",
}

# ---------------------------------------------------------------- HTTP


def jdump(o):
    return json.dumps(o).encode()


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    avatars_from = None
    delay_ms = 0
    bytes_per_second = 0
    disable_websocket = False
    trace_requests = False

    def log_message(self, fmt, *args):
        pass

    def send_json(self, obj, code=200):
        body = jdump(obj)
        if self.delay_ms:
            time.sleep(self.delay_ms / 1000)
        if self.trace_requests:
            print(f"{time.monotonic():.3f} {self.command} {self.path} {code} {len(body)}B", flush=True)
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        try:
            if self.bytes_per_second:
                chunk_size = max(1, self.bytes_per_second // 10)
                for start in range(0, len(body), chunk_size):
                    chunk = body[start:start + chunk_size]
                    self.wfile.write(chunk)
                    self.wfile.flush()
                    time.sleep(len(chunk) / self.bytes_per_second)
            else:
                self.wfile.write(body)
        except (BrokenPipeError, ConnectionResetError):
            pass

    def body(self):
        n = int(self.headers.get("Content-Length") or 0)
        if not n:
            return {}
        try:
            return json.loads(self.rfile.read(n))
        except Exception:
            return {}

    def do_GET(self):
        self.route("GET")

    def do_POST(self):
        self.route("POST")

    def do_PATCH(self):
        self.route("PATCH")

    def do_PUT(self):
        self.route("PUT")

    def do_DELETE(self):
        self.route("DELETE")

    def route(self, method):
        u = urllib.parse.urlparse(self.path)
        path, q = u.path, {k: v[-1] for k, v in urllib.parse.parse_qs(u.query).items()}
        if path.endswith("/events/ws") and self.headers.get("Upgrade", "").lower() == "websocket":
            if self.disable_websocket:
                return self.send_json({"error": "Realtime disabled for network simulation"}, 503)
            return self.websocket()
        if path.startswith("/api/agent-avatars/"):
            return self.avatar(path + ("?" + u.query if u.query else ""))
        if not path.startswith("/api/"):
            return self.send_json({"error": "not found"}, 404)
        body = self.body() if method in ("POST", "PATCH", "PUT") else {}
        try:
            res = self.api(method, path[4:], q, body)
        except KeyError:
            return self.send_json({"error": "not found"}, 404)
        if isinstance(res, tuple):
            return self.send_json(res[0], res[1])
        self.send_json(res)

    # -- API
    def api(self, m, p, q, b):
        seg = p.strip("/").split("/")
        if p == "/health":
            return {"status": "ok"}
        if p == "/auth/get-session":
            return {"session": {"id": "demo", "userId": ME}, "user": USER}
        if p == "/auth/sign-in/email":
            return {"user": USER, "token": "demo"}
        if p == "/auth/sign-out":
            return {"success": True}
        if p == "/companies":
            return [COMPANY]
        if seg[0] == "companies" and len(seg) >= 3:
            return self.company_api(m, seg[2:], q, b)
        if seg[0] == "issues":
            return self.issue_api(m, seg[1], seg[2:], q, b)
        if seg[0] == "agents":
            a = AGENT_BY_ID[seg[1]]
            rest = seg[2:]
            if not rest:
                if m == "PATCH":
                    a.update(b)
                return a
            if rest == ["pause"]:
                a["status"] = "paused"
                return a
            if rest == ["resume"]:
                a["status"] = "idle"
                return a
            if rest == ["runtime-state"]:
                return {"sessionId": "demo-session", "lastRunStatus": "succeeded", "totalInputTokens": 9_800_000, "totalOutputTokens": 2_200_000,
                        "totalCachedInputTokens": 180_000_000, "totalCostCents": next((c["costCents"] for c in COST_BY_AGENT if c["agentId"] == a["id"]), 0)}
            if rest == ["instructions-bundle"]:
                return {"entryFile": "AGENTS.md", "editable": True, "files": [{"path": f, "size": len(t), "isEntryFile": f == "AGENTS.md", "editable": True} for f, t in INSTRUCTIONS.items()]}
            if rest == ["instructions-bundle", "file"]:
                path = q.get("path") or b.get("path")
                if m == "PUT":
                    INSTRUCTIONS[path] = b.get("content", "")
                return {"path": path, "content": INSTRUCTIONS.get(path, "").format(name=a["name"], title=a["title"].lower()), "editable": True}
            if rest[0] == "heartbeat" or rest[0] == "wakeup":
                return RUNS[0]
            return {}
        if seg[0] == "heartbeat-runs":
            r = RUN_BY_ID[seg[1]]
            rest = seg[2:]
            if not rest:
                return r
            if rest == ["log"]:
                data = LOGS.get(r["id"], "").encode()
                off = int(q.get("offset") or 0)
                return {"runId": r["id"], "content": data[off:].decode(), "nextOffset": None}
            if rest == ["events"]:
                return [{"seq": 1, "eventType": "lifecycle", "stream": "system", "level": "info", "message": "run started", "createdAt": r["startedAt"]}]
            if rest == ["issues"]:
                iid = r["contextSnapshot"].get("issueId")
                return [ref(ISSUES[iid])] if iid else []
            if rest == ["cancel"]:
                r["status"] = "cancelled"
                return {"ok": True}
        if seg[0] == "approvals":
            ap = next(a for a in APPROVALS if a["id"] == seg[1])
            rest = seg[2:]
            if not rest:
                return ap
            if rest == ["comments"]:
                return [] if m == "GET" else {"id": str(uuid.uuid4()), "authorUserId": ME, "body": b.get("body", ""), "createdAt": ago()}
            if rest == ["issues"]:
                return []
            ap["status"] = {"approve": "approved", "reject": "rejected"}.get(rest[0], ap["status"])
            return ap
        if seg[0] == "projects":
            return PROJECT_BY_ID[seg[1]] | {"taskCount": sum(i["projectId"] == seg[1] for i in ISSUES.values())}
        if seg[0] == "routines":
            r = next(x for x in ROUTINES if x["id"] == seg[1])
            rest = seg[2:]
            if not rest:
                if m == "PATCH":
                    b.pop("baseRevisionId", None)
                    r.update(b)
                    r["latestRevisionId"] = str(uuid.uuid4())
                return r
            if rest == ["triggers"]:
                t = {"id": str(uuid.uuid4()), "kind": b["kind"], "label": None, "enabled": True, "cronExpression": b.get("cronExpression"),
                     "timezone": b.get("timezone"), "nextRunAt": ahead(hours=20), "archived": False}
                r["triggers"].append(t)
                return {"trigger": t}
            if rest == ["run"]:
                return {"id": str(uuid.uuid4()), "status": "issue_created"}
            return {}
        if seg[0] == "routine-triggers":
            t = next(t for r in ROUTINES for t in r["triggers"] if t["id"] == seg[1])
            t.update(b)
            return t
        if seg[0] == "decisions":
            d = next(x for x in DECISIONS if x["id"] == seg[1])
            rest = seg[2:]
            if rest == ["decide"]:
                d.update(status="decided", chosenOptionId=b["optionId"], executionStatus="succeeded", decidedAt=ago())
            elif rest == ["dismiss"]:
                d.update(status="decided", chosenOptionId="dismissed", decidedAt=ago())
            return d
        raise KeyError(p)

    def company_api(self, m, seg, q, b):
        head = seg[0]
        if head == "issues":
            if m == "POST":
                return self.create_issue(b)
            return self.list_issues(q)
        if head == "user-directory":
            return USERS
        if head == "dashboard":
            return dashboard()
        if head == "sidebar-badges":
            return {"inbox": attention({"all": "true"})["totalCount"], "approvals": sum(a["status"] == "pending" for a in APPROVALS), "failedRuns": 1, "joinRequests": 0}
        if head == "activity":
            out = ACTIVITY
            if q.get("agentId"):
                out = [e for e in out if e["agentId"] == q["agentId"]]
            return out[: int(q.get("limit") or 50)]
        if head == "live-runs":
            return [live_run(r) for r in RUNS if r["status"] in ("running", "queued")] + [live_run(r) for r in RUNS if r["status"] not in ("running", "queued")][: max(0, int(q.get("minCount") or 0) - 3)]
        if head == "attention":
            return attention(q)
        if head == "inbox-dismissals":
            if m == "DELETE":
                DISMISSALS.pop(urllib.parse.unquote(seg[1]), None)
                return {}
            kind = b.get("kind", "dismiss")
            DISMISSALS[b["itemKey"]] = {"kind": kind, "dismissedAt": ago(), "snoozedUntil": b.get("snoozedUntil"), "isActive": True}
            return {"id": str(uuid.uuid4()), "itemKey": b["itemKey"], "kind": kind}
        if head == "decisions":
            return [d for d in DECISIONS if not q.get("status") or d["status"] == q["status"]]
        if head == "decision-triage":
            TRIAGE[urllib.parse.unquote(seg[2])] = b.get("decideBy")
            return {"decideBy": b.get("decideBy")}
        if head == "labels":
            return LABELS
        if head == "agents":
            return AGENTS
        if head == "heartbeat-runs":
            out = [r for r in RUNS if not q.get("agentId") or r["agentId"] == q["agentId"]]
            return sorted(out, key=lambda r: r["startedAt"], reverse=True)
        if head == "approvals":
            return [a for a in APPROVALS if not q.get("status") or a["status"] == q["status"]]
        if head == "costs":
            return {"summary": {"spendCents": COMPANY["spentMonthlyCents"], "budgetCents": COMPANY["budgetMonthlyCents"], "utilizationPercent": 36.4},
                    "by-agent": COST_BY_AGENT, "by-provider": COST_BY_PROVIDER, "quota-windows": QUOTAS}[seg[1]]
        if head == "budgets":
            return BUDGETS
        if head == "projects":
            return [p | {"taskCount": sum(i["projectId"] == p["id"] for i in ISSUES.values())} for p in PROJECTS]
        if head == "goals":
            return GOALS
        if head == "routines":
            return ROUTINES
        if head == "search":
            s = q.get("q", "").lower()
            hits = [i for i in ISSUES.values() if s in (i["identifier"] + " " + i["title"]).lower()]
            return {"results": [{"id": i["id"], "type": "issue", "title": i["title"], "href": f"/LUM/issues/{i['identifier']}", "issue": ref(i), "updatedAt": i["updatedAt"]} for i in hits[:40]]}
        raise KeyError(head)

    def list_issues(self, q):
        out = list(ISSUES.values())
        if q.get("status"):
            st = set(q["status"].split(","))
            out = [i for i in out if i["status"] in st]
        for key in ("assigneeAgentId", "projectId", "parentId"):
            if q.get(key):
                out = [i for i in out if i.get(key) == q[key]]
        if q.get("assigneeUserId"):
            want = ME if q["assigneeUserId"] == "me" else q["assigneeUserId"]
            out = [i for i in out if i.get("assigneeUserId") == want]
        if q.get("unreadForUserId"):
            out = [i for i in out if i.get("isUnreadForMe")]
        if q.get("touchedByUserId"):
            out = [i for i in out if i.get("assigneeUserId") == ME or i.get("isUnreadForMe") or i["issueNumber"] in (148, 140)]
        if q.get("q"):
            s = q["q"].lower()
            out = [i for i in out if s in (i["identifier"] + " " + i["title"]).lower()]
        if q.get("updatedSince"):
            out = [i for i in out if i["updatedAt"] >= q["updatedSince"]]
        if q.get("sortField") == "id":
            out.sort(key=lambda i: i["id"])
            if q.get("afterId"):
                out = [i for i in out if i["id"] > q["afterId"]]
        else:
            out.sort(key=lambda i: i["updatedAt"], reverse=True)
        off, lim = int(q.get("offset") or 0), int(q.get("limit") or 50)
        return out[off: off + lim]

    def create_issue(self, b):
        n = max(BY_NUM) + 1
        i = issue(n, b.get("title", "Untitled"), b.get("status", "todo"), b.get("priority", "medium"), None,
                  project=None, mins_ago=0, desc=b.get("description"))
        i.pop("_blockedByNums", None)
        i["blockedBy"] = []
        for k in ("parentId", "projectId", "assigneeAgentId", "assigneeUserId"):
            if k in b:
                i[k] = b[k]
        return i

    def issue_api(self, m, key, rest, q, b):
        i = ISSUES.get(key) or next((x for x in ISSUES.values() if x["identifier"] == key), None)
        if i is None:
            raise KeyError(key)
        iid = i["id"]
        if not rest:
            if m == "PATCH":
                for k, v in b.items():
                    if k in i or k in ("assigneeAgentId", "assigneeUserId", "projectId", "parentId"):
                        i[k] = v
                i["updatedAt"] = i["lastActivityAt"] = ago()
                if "comment" in b and b["comment"]:
                    COMMENTS.setdefault(iid, []).append({"id": str(uuid.uuid4()), "issueId": iid, "authorType": "user", "authorUserId": ME, "body": b["comment"], "createdAt": ago()})
            return full_issue(i)
        r = rest[0]
        if r == "comments":
            if m == "POST":
                c = {"id": str(uuid.uuid4()), "issueId": iid, "authorType": "user", "authorUserId": ME, "body": b.get("body", ""), "createdAt": ago(), "updatedAt": ago()}
                COMMENTS.setdefault(iid, []).append(c)
                return c
            cs = sorted(COMMENTS.get(iid, []), key=lambda c: c["createdAt"], reverse=q.get("order") == "desc")
            return cs
        if r == "interactions":
            ints = INTERACTIONS.get(iid, [])
            if len(rest) >= 3:
                it = next(x for x in ints if x["id"] == rest[1])
                action = rest[2]
                it["status"] = {"accept": "accepted", "reject": "rejected", "skip": "skipped", "cancel": "cancelled"}.get(action, "answered")
                it["resolvedAt"] = ago()
                it["resolvedByUserId"] = ME
                it["result"] = {"version": 1, **b}
                return it
            return ints
        if r == "documents":
            docs = DOCUMENTS.get(iid, [])
            if len(rest) == 1:
                return docs
            d = next((x for x in docs if x["key"] == rest[1]), None)
            if m == "PUT":
                d = d or {"id": str(uuid.uuid4()), "key": rest[1], "latestRevisionNumber": 0}
                d.update(title=b.get("title"), body=b.get("body", ""), updatedAt=ago(), latestRevisionNumber=d["latestRevisionNumber"] + 1)
                if d not in docs:
                    DOCUMENTS.setdefault(iid, []).append(d)
            if d is None:
                raise KeyError(rest[1])
            return d
        if r == "work-products":
            return WORK_PRODUCTS.get(iid, [])
        if r == "attachments":
            return []
        if r == "activity":
            return [e for e in ACTIVITY if e["entityId"] == iid]
        if r == "runs":
            return [{"runId": x["id"], "id": x["id"], "agentId": x["agentId"], "status": x["status"], "startedAt": x["startedAt"], "finishedAt": x["finishedAt"], "createdAt": x["createdAt"], "invocationSource": x["invocationSource"]}
                    for x in RUNS if x["contextSnapshot"].get("issueId") == iid]
        if r == "live-runs":
            return [live_run(x) for x in RUNS if x["contextSnapshot"].get("issueId") == iid and x["status"] in ("running", "queued")]
        if r == "cost-summary":
            n = sum(x["contextSnapshot"].get("issueId") == iid for x in RUNS)
            return {"costCents": 42 * n, "inputTokens": 18342 * n, "outputTokens": 4120 * n, "runCount": n, "runtimeMs": 360000 * n}
        if r in ("read", "inbox-archive"):
            if r == "read":
                i["isUnreadForMe"] = m == "DELETE"
            return {"ok": True}
        raise KeyError(r)

    # -- avatars
    def avatar(self, path):
        if not self.avatars_from:
            return self.send_json({"error": "no avatars"}, 404)
        try:
            with urllib.request.urlopen(self.avatars_from.rstrip("/") + path, timeout=5) as r:
                data, ctype = r.read(), r.headers.get("Content-Type", "image/png")
        except Exception:
            return self.send_json({"error": "avatar unavailable"}, 404)
        self.send_response(200)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "max-age=86400")
        self.end_headers()
        self.wfile.write(data)

    # -- live events: accept the socket, answer pings, never push anything.
    def websocket(self):
        key = self.headers.get("Sec-WebSocket-Key", "")
        accept = base64.b64encode(hashlib.sha1((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").encode()).digest()).decode()
        self.send_response(101)
        self.send_header("Upgrade", "websocket")
        self.send_header("Connection", "Upgrade")
        self.send_header("Sec-WebSocket-Accept", accept)
        self.end_headers()
        self.wfile.flush()
        sock = self.connection
        try:
            while True:
                hdr = self.rfile.read(2)
                if len(hdr) < 2:
                    break
                op, ln = hdr[0] & 0x0F, hdr[1] & 0x7F
                if ln == 126:
                    ln = struct.unpack(">H", self.rfile.read(2))[0]
                elif ln == 127:
                    ln = struct.unpack(">Q", self.rfile.read(8))[0]
                mask = self.rfile.read(4) if hdr[1] & 0x80 else b"\0\0\0\0"
                data = bytes(c ^ mask[k % 4] for k, c in enumerate(self.rfile.read(ln)))
                if op == 0x8:
                    sock.sendall(b"\x88\x00")
                    break
                if op == 0x9:
                    sock.sendall(bytes([0x8A, len(data)]) + data)
        except (ConnectionError, socket.error):
            pass
        self.close_connection = True


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--port", type=int, default=3100)
    ap.add_argument("--avatars-from", help="Paperclip base URL to proxy agent avatars from, e.g. http://127.0.0.1:3100")
    ap.add_argument("--delay-ms", type=int, default=0, help="Delay each JSON response to simulate latency")
    ap.add_argument("--bytes-per-second", type=int, default=0, help="Throttle each response body")
    ap.add_argument("--disable-websocket", action="store_true", help="Exercise polling fallback")
    ap.add_argument("--trace-requests", action="store_true", help="Print demo request timings/counts")
    a = ap.parse_args()
    if a.delay_ms < 0 or a.bytes_per_second < 0:
        ap.error("Network simulation values must be non-negative")
    Handler.delay_ms = a.delay_ms
    Handler.bytes_per_second = a.bytes_per_second
    Handler.disable_websocket = a.disable_websocket
    Handler.trace_requests = a.trace_requests
    Handler.avatars_from = a.avatars_from
    srv = ThreadingHTTPServer((a.host, a.port), Handler)
    print(f"Demo Paperclip on http://{a.host}:{a.port} ({len(ISSUES)} tasks, {len(AGENTS)} agents)")
    srv.serve_forever()


if __name__ == "__main__":
    main()
