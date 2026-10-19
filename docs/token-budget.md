# Token budget: how to measure

The Claude Code config in `.claude/` (opusplan, Haiku/Sonnet subagents, read-deny rules,
`scripts/gradle-brief.ps1`, status line) is meant to cut tokens per ticket. Measure it; don't assume it.

## Before / after on the same ticket
1. Pick one real ticket. Note the time, then run it once with the old setup.
2. Afterwards record:
   - `npx ccusage@latest blocks` (or `blocks --recent`): tokens and cost of the 5-hour block(s) the ticket used.
   - `/usage` in the Claude Code session: the 5-hour and weekly limits used.
   - The status line (`[model] ctx N% | 5h N%`) at the end of the ticket.
3. Run the same (or an equivalent) ticket with the new setup and record the same three numbers.
4. Add one change at a time (e.g. only gradle-brief first, then the subagents, then opusplan), so a
   saving or a regression can be traced to a single change. `/clear` between tickets.

## Notes
- The main conversation should never read PNGs, full Gradle logs or `build/` files; if the context % jumps
  after a build, check that the build went through `gradle-runner`.
- Headroom (github.com/headroomlabs-ai/headroom) is only a possible later experiment, not part of this
  setup: it has open Windows bugs, and routing a subscription (OAuth) session through it is a
  Terms-of-Service grey area.

## Headless (`claude -p`) pattern
`opusplan` only switches to Opus in interactive plan mode; in `-p` runs the main session is Sonnet the
whole time, so Opus never plans. Headless tickets therefore run as separate, explicit steps:
1. **Plan** - `claude -p --model opus` with read-only tools (Read, Grep, Glob, Task; Edit/Write denied).
   It uses the Haiku `Explore` agent for searching and answers with a short plan (files, signatures,
   decisions, traps), which is saved as the plan file. The same run can also review the previous
   step's `git diff` (run `git add -N .` first so new files show up).
2. **Implement** - `claude -p --model sonnet` gets the plan pasted into its prompt and edits the code
   itself (no extra `android-implementer` hop, which reads the same files a second time).
3. **Build / screenshots** - the Haiku `gradle-runner` and `screenshot-reviewer` agents, so build logs
   and PNGs never enter the Sonnet or Opus context.
4. **Review** - `claude -p --model opus`, read-only, reviews the diff against the plan and lists fixes;
   a short Sonnet run applies them.

After every run, record the per-model cost from the JSON output (`modelUsage`: cost, output,
cache-read and cache-creation tokens) and run `claude -p "/usage"` (free) to log the session and weekly
percentage, so a spike can be traced to one run.
