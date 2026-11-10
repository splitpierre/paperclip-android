# User guide

How to get around Paperclip for Android. The app has five tabs: **Home**, **Decisions**, **Tasks**, **Agents** and **More**.

- [Home](#home)
- [Decisions](#decisions)
- [Inbox](#inbox)
- [Answering agents](#answering-agents)
- [Tasks](#tasks)
- [A task's page](#a-tasks-page)
- [Getting around](#getting-around)
- [Agents and runs](#agents-and-runs)
- [More](#more)
- [Settings and notifications](#settings-and-notifications)
- [Live updates](#live-updates)

## Home

<img src="screenshots/dashboard.png" alt="Dashboard" width="280" align="right">

The dashboard shows the company at a glance:

- How many agents are running and how many tasks are in progress or blocked.
- This month's spend against the budget, and approvals waiting for you.
- **Live now**: every queued or running agent run, with the task it's on and what it's doing right now. Tap one to open its transcript.
- Run activity over the last two weeks and the latest activity.

If any agent is paused, a banner offers **Resume all**.

The dot next to the company name is green while the live connection is up and yellow while it reconnects. **Search** and **Settings** are in the top bar.

<br clear="right">

## Decisions

<img src="screenshots/decisions.png" alt="Decisions" width="280" align="right">

**Decisions** is your desk: everything waiting on you, as on the web UI's Decisions page. It's grouped into:

- **Decide now**: items you've marked for today, plus anything whose deadline has passed.
- **New today**: what arrived today.
- **Earlier**: everything else.

The chips at the top filter by kind (agent requests, decisions, approvals, failed runs and so on). Tap a card to open it in place:

- **Decisions** that an agent proposed show their reasoning (Markdown, tables included), any fields to fill in, and one button per option. Choosing an option applies its effects on the server. Options that would cancel a whole task tree ask you to type the task ID first. **Dismiss — no effects** closes it without doing anything.
- **Confirmations** can be confirmed or declined right there. **Approvals** can be approved, rejected or sent back for changes.
- Questions, failed runs and blocked work show a summary; **Open** takes you to where you answer or fix them.

**When to decide…** sets a deadline (today, this week or whenever) that everyone on the board sees. **Swipe** a card away to dismiss it just for you, or **long-press** it to snooze it for an hour, four hours, until tomorrow or until next week. **Snoozed** and **Dismissed** at the bottom list what you've put aside, with **Restore**; **Decided** and **Expired** show the history.

The Decisions badge counts what's new today or due now.

<br clear="right">

<img src="screenshots/decision-card.png" alt="An open decision" width="280" align="right">

A decision opened in place: the agent's comparison table scrolls sideways, and each option says what it will do.

<br clear="right">

## Inbox

<img src="screenshots/inbox.png" alt="Inbox" width="280" align="right">

The Inbox is under **More**, with its own badge. **Needs me** is the same attention feed as a plain list: anything waiting on you, such as agent questions, confirmations, plan approvals, hire approvals, failed runs and blocked work. Each row says what kind of item it is, why it's here and which agent raised it. Tap a row to go straight to where you can act on it.

- **Swipe** a row away to dismiss it. **Undo** brings it back.
- **Long-press** a row to snooze it for a while.

**Unread** lists tasks with activity you haven't seen. **Mine** lists tasks you're involved in.

The badge on **More** counts what's in the Inbox and pending approvals.

<br clear="right">

## Answering agents

<img src="screenshots/task-confirmation.png" alt="Confirmation card" width="280" align="right">

When an agent needs something from you, a card appears at the top of the task's thread. Cards waiting for you have a yellow border.

| Card | What you do |
|---|---|
| **Confirmation** | **Approve**, or **Request changes** with a reason. Plan approvals and tool-action approvals look the same. |
| **Questions** | Pick an option, several options or **Other…** with your own text, then submit. |
| **Choose options** | Tick the options you want and confirm. |
| **Suggested tasks** | Choose which proposed tasks to create. |
| **Review items** | Give each item a verdict. |

Card details are full Markdown, so tables, code blocks and links render as they do on the web.

**Tables** keep readable columns on a phone: each column is as wide as its content (long cells wrap), and wide tables scroll sideways instead of squeezing to fit. A fade at the edge shows there's more. This applies everywhere agent output appears: threads, cards, documents, decisions and run transcripts.

<br clear="right">

## Tasks

The Tasks tab works from a local copy of every task in the company. It loads once, then fetches only what changed as live events arrive, so searching, filtering and switching views take no time.

The header shows the current view and how many tasks match, for example `Tree · 29 of 55`.

### Views

Switch views with the three icons in the header. The app remembers your choice.

| | |
|---|---|
| <img src="screenshots/tasks-list.png" alt="List" width="240"> | **List** groups tasks under collapsible headers. Group by status (the default), priority, assignee, project or parent, or not at all. |
| <img src="screenshots/tasks-tree.png" alt="Tree" width="240"> | **Tree** shows parents and their sub-tasks. Each parent shows a progress bar (done out of total sub-tasks) and pills for live runs and blocked sub-tasks. Branches whose sub-tasks are all done start collapsed. Branches you expand or collapse stay that way when you come back, even after restarting the app. |
| <img src="screenshots/tasks-board.png" alt="Board" width="240"> | **Board** has one column per status. Swipe sideways, or tap a chip above the board to jump to a column. Cards show priority, assignee, parent task and sub-task progress. The Done column shows the 40 most recent. |

Markers you'll see on tasks:

- **live**: an agent is running on it right now.
- **next**: it's ranked in **Next up** for its branch.
- A red ⊘ next to the ID means it's waiting on an unfinished blocker.
- A dimmed task doesn't match your filters. It's shown so that a matching sub-task keeps its place in the tree.

### Next up

<img src="screenshots/tasks-next-up.png" alt="Next up" width="280" align="right">

At the top of the tree, **Next up** lists what can be started now. A task qualifies when it:

- is **Todo** or **Backlog**,
- isn't waiting on a blocker that's still open,
- has no open sub-tasks of its own (otherwise its sub-tasks are the real next steps), and
- isn't under a cancelled parent.

They're ranked by priority, then by how many other tasks finishing it would unblock, then Todo before Backlog, then by task number. The pills explain the ranking: **Critical**, **High**, **Unblocks 2**, **Backlog** and **Unassigned**.

Below them, **In flight** lists what's in progress, in review or running right now.

Tap the header to collapse the card to a single line that still shows the top task.

<br clear="right">

### Focusing on one branch

Long-press a task and choose **Focus tree** to show only that task's sub-tasks. A breadcrumb at the top (**All tasks › LUM-140 › …**) takes you back up, and so does the back button. While focused, Next up and In flight only cover that branch.

### Quick edit

<img src="screenshots/tasks-quick-edit.png" alt="Quick edit" width="280" align="right">

Long-press any task, in any view, to open the quick-edit sheet:

- **Move to** another status.
- Change the **priority** or the **assignee** (an agent, a person or nobody).
- See what it's **blocked by**.
- **Focus tree** or **Open task**.

Changes show immediately. If the server rejects one, the app puts it back and tells you why.

<br clear="right">

### Filters and presets

<img src="screenshots/tasks-filters.png" alt="Filters" width="280" align="right">

The chips under the header are one-tap presets:

| Preset | Shows |
|---|---|
| **Active** | Everything not done or cancelled (the default) |
| **Mine** | Tasks assigned to you |
| **Live** | Tasks with an agent running on them |
| **Blocked** | Blocked tasks, or tasks waiting on an open blocker |
| **Unassigned** | Open tasks with no assignee |
| **Done** | Done tasks |
| **All** | Everything |

The filter icon opens the full sheet: status, priority, assignee (including **Me** and **Unassigned**), project (including **No project**), label, live only, blocked only and hiding routine runs. It also sets the sort order (last updated, priority, created, task number or plan order) and the list grouping. Active filters show as chips you can remove one at a time, and the filter icon shows how many are on.

Use the search icon to match task IDs and titles. The **⋮** menu has **Expand all**, **Collapse all**, **Reset filters** and **Reload all tasks**.

<br clear="right">

### New tasks

Tap **New task** to create one, or **New sub-task** in a task's **⋮** menu. Set its title, status, priority, work mode, assignee, project and description. The work mode chip offers **Auto mode** (the default: the assignee just does the work), **Plan mode** (the assignee proposes a plan first) and **Ask mode** (the assignee answers in the thread without doing any work). A task that isn't in Auto mode shows its mode as a chip at the top of the task. **Attach files** adds any files from your phone; each shows its name and size, and **×** removes it. When you tap **Create**, the task is created first and the files upload one at a time, each one ticked off as it lands. Then the new task opens. If a file doesn't upload, for example because it's over the server's size limit, the task is still created and a message names each file that failed and why. Attach those again from the task's thread.

### Drafts

Nothing you type is lost if you leave before sending it. The New task form, a reply in a task's thread and your answers to an agent's questions are saved on your phone as you type, and come back when you open the same screen again: after going to another task, closing the app, the phone ending the app in the background, or signing out and back in to the same server and company.

- **New task** keeps one draft for a new top-level task and one per parent task for a new sub-task, with every field and the files you picked. A line at the top says the draft is saved on this device; **Discard draft** clears the form after you confirm.
- **Thread replies** are saved per task, along with any files that already finished uploading. Tasks with an unsent reply show a grey **Draft** pill in task lists. To discard a reply, clear its text and remove its files.
- **Answers** to a questions card are saved per card until you submit them.

A draft is removed only when it is sent successfully or you clear it. If sending fails, for example because you're offline, the text stays in the box and in the draft. Drafts are stored only on this phone and are never sent anywhere until you send them yourself.

<p>
  <img src="screenshots/new-task-draft.png" alt="The New task form restored from a draft, with Discard draft" width="280">
  <img src="screenshots/task-list-draft.png" alt="A task with an unsent reply marked Draft in the task list" width="280">
</p>

<p>
  <img src="screenshots/new-task-files.png" alt="A new task with an image and a PDF attached" width="280">
  <img src="screenshots/new-task-upload-failed.png" alt="The new task opened, with a message naming the file that didn't upload" width="280">
</p>

## A task's page

The top of a task shows its title, status, priority, assignee and project. Tap any of these to change it. Below that are who created it, its cost, its number of runs and the description, which you can edit. If an agent is working on the task, a banner links to the live run.

The tabs are:

- **Thread**: agent request cards waiting for you, then the conversation in order, with older comments loaded on demand. In a long thread a round button follows your reading direction: scrolling down it jumps to the latest comment, scrolling up it flips to jump back to the top. Write comments in Markdown, @-mention agents or people, and attach photos or files with the paperclip button. Long-press a comment to copy it.
- **Docs**: the task's documents, such as plans. You can read and edit them.
- **Sub-tasks**: a tree of this task's sub-tasks with overall progress and **Next up** for this branch. **Show closed** adds finished sub-tasks. **New sub-task** is in the **⋮** menu.
- **Files**: attachments and work products such as pull requests. Images, text and Markdown files open in the app, with **Share** and **Save** at the top. Other files open in an app that handles them, such as a PDF viewer; if no app can, you can share or save the file instead. Links to files in comments, documents and run transcripts open the same way.
- **Activity**: everything that happened to the task.

The **⋮** menu also has **Copy link**, **Open in browser** and **Mark unread**.

<p>
  <img src="screenshots/file-viewer.png" alt="A Markdown attachment open in the app" width="280">
  <img src="screenshots/file-cant-open.png" alt="A file no app can open, with Share and Save" width="280">
</p>

## Getting around

Task, run and agent pages hide the bottom bar to leave room for reading, so following links from task to task can leave you several pages deep. Once you're two or more pages in, an icon for the tab you started from appears next to the back arrow: tap it to close every page at once and land back on that tab. **Long-pressing the back arrow** does the same.

## Agents and runs

<img src="screenshots/agents.png" alt="Agents" width="280" align="right">

**Agents** lists the team with each agent's status, title, model and current activity. Filter by status, or tap the org-chart icon to see who reports to whom. The pause button pauses or resumes an agent.

An agent's page has four tabs:

- **Overview**: what it's running now, this month's spend, token totals, who it reports to, and its adapter details. Buttons at the top **Run now**, **Pause** or **Resume** the agent, or **Reset session**.
- **Runs**: recent runs and their outcomes.
- **Tasks**: open tasks assigned to the agent.
- **Instructions**: the agent's instruction files, which you can read and edit.

### Run transcripts

Open a run from Home, an agent's Runs tab or a task's live banner. **Transcript** shows the run as a conversation: the agent's messages, collapsed reasoning, and tool calls you can expand to see their input and output, with token counts and cost at the end. A live run streams as it goes; **Jump to latest** follows the output (scroll up and it becomes **Jump to top**), and **Stop** cancels the run. **Raw log** and **Events** show the underlying data.

Images and other binary data that agents put in their output show as a short placeholder with the type and size, such as `[image image/png, ~29 KB]`, so very large runs stay quick to open and scroll.

Transcripts support Claude, Codex, ACP and Antigravity agents. Output from other adapters shows as plain log lines.

<br clear="right">

## More

- **Approvals**: pending and past approvals, such as hiring an agent. Approve, reject or ask for a revision, with a note.
- **Projects**: each project's lead, target date and tasks. Tap **New project** to create one: give it a name (required), an optional description and a status (it starts as Planned). It opens as soon as it's created, and it's immediately available in the project picker when you create a task. If the server rejects it, for example because the name is already taken, the reason shows in the form.
- **Inbox**: unread and assigned tasks, plus the attention feed as a list. See [Inbox](#inbox).
- **Goals**.
- **Routines**: tap one to edit it. Change the title, instructions (with a preview), default agent, project and priority, and what happens when runs overlap or schedules are missed, then **Save**. Schedules are edited on the spot: every hour, every day, weekdays, weekly, monthly or a custom cron expression, in any time zone, and each can be paused or removed (with **Undo**). The switch at the top pauses or resumes the whole routine, and ▶ runs it now. If someone else saved the routine meanwhile, you choose whether to reload or overwrite. Variables and webhook triggers are still edited on the web.
- **Costs & quotas**: spend against budget for this month, the last 7 or 30 days or all time; provider quota windows and when they reset; and cost by agent and by model.
- **Activity**: the company's activity feed, filterable by agents, people or the system.
- **Search** the company.

## Settings and notifications

Open **Settings** from Home or More:

- **Account**: who you're signed in as, the server, how you're connected and whether the live connection is up.
- **Company**: switch between companies if you have more than one.
- **Appearance**: follow the system, or always use dark or light.
- **Language**: English, Português (Brasil) or Español, or follow the phone's language (**System default**). The app switches straight away and remembers the choice. On Android 13 and later you can also set it in the phone's **Settings → Apps → Paperclip → Language**. Dates and numbers follow the chosen language; what agents and people write stays as written.
- **Notifications**: **Agent requests** checks about every 15 minutes for new questions, confirmations, approvals and failed runs, and posts a notification for each. Tapping one opens the item. **Check now** runs the check immediately.
- **Sign out** clears the app's Paperclip and sign-in proxy sessions. The email you signed in with stays pre-filled on the sign-in screen. Passwords are never stored by the app: it offers to save them in your phone's password manager (Google Password Manager or similar), and "Use a saved sign-in" fills them back in.

## Live updates

While the app is open it keeps a WebSocket to the server, so new comments, status changes and run output show up without refreshing. If the connection drops, screens poll every 15 seconds until it's back, then catch up on anything they missed. Pull down on any list to refresh it by hand.

With a board API key the WebSocket isn't available, so the app polls instead.
