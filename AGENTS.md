# ItemSignature development
Use the SPEAR workflow from .agents/skills/spear-using-spear/SKILL.md.
Read docs/requirements.md, docs/implementation.md, docs/tasks.md and .claude/spear-state.json before changing code.
Use the portable project helper tools/spear/state.mjs in place of upstream Bash/jq state.sh. Run the unmodified upstream validator with node tools/spear/ears.mjs docs/requirements.md.
Follow spec -> prove -> engine -> arch -> refine for behavioral work; docs/infrastructure skip prove/engine. Require actual failure evidence before implementation.
Run mvn -B -ntp clean verify and the EARS validator before closing tasks.
Keep domain and application independent of Bukkit/Paper/Adventure/Nexo; adapters belong to infrastructure.
Do not modify DiaryKeeper or EnthusiaLoreItems repositories as part of this project.
This is a brownfield adoption: do not fabricate historical test-first evidence. No global SPEAR installation or automatic SessionStart hook is configured; project-local skills are provided.
Resolve upstream CLAUDE_PLUGIN_ROOT references to tools/spear; templates and reference Bash helpers are available there. Use the portable Node state helper on Windows.
