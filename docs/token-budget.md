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

## Hard limits (v2)
Why: on Oct 18-19 2026 one medium feature (Theatrum V23, player gestures) used **+26 session points**
($10.09). Output was small (at most 79k tokens per run). Nearly all of the cost was **cache reads**: every
tool turn re-sends the whole session context. One long Sonnet session re-read 7.0M tokens; another,
after partial limits, 5.8M. Scoped runs were cheap: an Opus review given a narrow diff (0.49M) and a
Sonnet fix run with no build (0.30M, $0.31). These rules are not optional.

1. **Small steps, fresh sessions.** The Opus plan splits the work into steps of **at most ~30 turns
   each**. Each step runs in a **fresh** `claude -p --model sonnet` session. Its prompt holds only its plan
   slice and the files and line ranges it may touch. Never continue a long session into the next step.
2. **No Sonnet helper subagents.** Sonnet edits the code itself. Subagents are Haiku only: `gradle-runner`
   (builds/logs), `screenshot-reviewer` (PNGs) and `Explore` (search). `android-implementer` and
   `general-purpose` are denied in `.claude/settings.json`. `CLAUDE_CODE_SUBAGENT_MODEL` is `haiku`.
3. **Line ranges, not whole files.** Do not Read a file over 200 lines whole. Grep for the symbol, then
   Read with offset/limit. Do not re-read a range you already have unless you edited it.
4. **Builds run outside Claude.** Full builds and verification (`clean assembleDebug testDebugUnitTest
   recordRoborazziDebug` via `scripts/gradle-brief.ps1`, test counts, screenshots) are run by the
   operator between sessions. A session runs **at most 1** build (through `gradle-runner`), and only
   when the step can't finish without it.
5. **Opus review gets a pre-generated diff.** The operator runs `git diff -- <1-3 key files>` (after
   `git add -N .` for new files) and pastes that diff into the review prompt. Do not hand Opus a file
   list or let it run `git diff` and explore. The review answers with numbered fixes (file:line + change).
6. **Measure every run.** After each `claude -p` run, record in `METRICS.md` the per-model cost and tokens
   from the JSON output (`modelUsage`: costUSD, outputTokens, cacheReadInputTokens,
   cacheCreationInputTokens) and the result of `claude -p "/usage"` (free; session % and week %).
   **Target: under 8 session points per medium feature.**
7. **On a spike, keep working.** If a feature climbs fast, find the cause (which model? cache reads vs.
   cache creation vs. output? big file reads, long logs, re-read context?), apply **one** optimization
   right away and continue. Do not pause the work. If the spike was not warranted by the task, write a
   short 5 Whys / 8D in `METRICS.md` after the work is done.

### Step commands
- Plan or review (read-only): `claude -p --model opus --output-format json --allowedTools Read Grep Glob Agent --disallowedTools Edit Write`.
- Implement one step: `claude -p --model sonnet --output-format json --max-budget-usd 1.50`. The prompt is
  the plan slice plus the rules above. The ~30-turn limit goes in the prompt: this CLI documents no
  `--max-turns`, so `--max-budget-usd` is the hard stop.
- Agent turn caps are set in `.claude/agents/*.md` (`maxTurns`).
- Then the operator builds, checks screenshots, runs `/usage` and logs.
