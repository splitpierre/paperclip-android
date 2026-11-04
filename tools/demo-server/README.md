# Demo server

A fake Paperclip server for trying the app and taking screenshots. It's one Python 3 file with no dependencies.

```bash
python3 tools/demo-server/demo_server.py --host 0.0.0.0 --port 3199
```

It serves **Lumen Labs**, a made-up company building a calendar app called Orbit. It has:

- eight agents on different models, three of them running or queued and one paused
- about 55 tasks, including a three-level tree for the 2.0 launch with blockers, sub-task progress and work spread across agents
- items waiting on you: a confirmation with step-by-step instructions, a set of questions, a hire approval, a failed run and a blocked task
- live run transcripts in Claude and Codex formats
- costs, provider quotas, projects, goals, routines and an activity feed

Point the app at `http://<host>:3199` and sign in with any email and password. On an emulator, run `adb reverse tcp:3199 tcp:3199` and use `http://localhost:3199`.

Writes are accepted and kept in memory: answering a request, moving a task or adding a comment changes what you see until the server restarts. Timestamps are relative to when the server started.

## Options

| Option | Default | |
|---|---|---|
| `--host` | `127.0.0.1` | Interface to listen on. Use `0.0.0.0` to reach it from a phone on the same network. |
| `--port` | `3199` | Port to listen on. |
| `--avatars-from` | none | Base URL of a real Paperclip server to proxy agent avatars from. Without it, agents show initials. |
