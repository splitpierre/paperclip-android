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
- files on LUM-148 (an image, a PDF, Markdown, plain text, an unknown type and an image with no content type) and a comment that links to each one

Point the app at `http://<host>:3199` and sign in with any email and password (signing out really signs you out until you sign in again). Sign-in sets a session cookie; file downloads and the session check need it (or a board key), as on a real server, so a file link that leaks to the external browser fails there the same way. On an emulator, run `adb reverse tcp:3199 tcp:3199` and use `http://localhost:3199`.

Writes are accepted and kept in memory: answering a request, moving a task, editing its properties (parent, blockers, labels, work mode…), adding a comment or uploading a file changes what you see until the server restarts. Task edits are validated like Paperclip's: a task can't become its own parent or ancestor, or block itself. Timestamps are relative to when the server started.

## Options

| Option | Default | |
|---|---|---|
| `--host` | `127.0.0.1` | Interface to listen on. Use `0.0.0.0` to reach it from a phone on the same network. |
| `--port` | `3199` | Port to listen on. |
| `--avatars-from` | none | Base URL of a real Paperclip server to proxy agent avatars from. Without it, agents show initials. |
| `--max-attachment-bytes` | `10485760` | Uploads larger than this are rejected with a 422, as Paperclip does. Lower it to try a failed upload. |
| `--fail-text` | none | Comments and new tasks whose text contains this are rejected with a 500. Use it to check that a failed send keeps the draft, e.g. `--fail-text '[fail]'`. |
| `--reject-edits` | off | Answer every task edit with a 409, to see the app roll back an optimistic change (Properties tab, board moves). |
