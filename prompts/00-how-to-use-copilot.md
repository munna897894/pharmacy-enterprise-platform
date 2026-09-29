# How to use these prompts in IntelliJ

## One-time preparation

1. Install/enable GitHub Copilot and sign in.
2. Open the repository root, not an individual service module.
3. Confirm `.github/copilot-instructions.md` is present.
4. Keep the relevant `docs/*.md` and current prompt file available as context.
5. Use Copilot Chat/agent editing only after it has shown a plan.

GitHub Copilot features differ by plugin version. If repository instructions are not automatically applied in your IntelliJ version, paste `.github/copilot-instructions.md` at the beginning of the chat, then the current prompt. Do not assume Copilot remembers a previous chat.

## Session starter to paste before every numbered prompt

```text
Work only on the active numbered prompt. First read .github/copilot-instructions.md and the documentation files referenced by this prompt. Inspect the existing repository before proposing changes. Do not code yet. Return:
1. your understanding of the bounded scope;
2. exact files/modules you plan to create or change;
3. dependencies or migrations you plan to add;
4. tests and commands you will run;
5. assumptions or conflicts with the documented contracts.
Wait for my approval before editing.
```

After reviewing the plan, reply:

```text
Proceed with the approved plan. Implement the smallest complete vertical slice. Do not add unrelated features. Run the relevant build/tests, correct failures, and finish with a concise report listing changed files, commands/results, risks, and the five most important code paths I should study.
```

## Review questions after each implementation

Ask Copilot these questions separately:

1. `Trace one request/event through the exact classes and methods you created.`
2. `Which transaction boundaries exist, and what can fail before or after each commit?`
3. `Show me where input validation, authorization and error sanitization occur.`
4. `Which generated code would you change before a real production deployment, and why?`
5. `Give me one controlled failure to create and diagnose without telling me the fix.`

## Guardrails

- Never ask `build the whole system`.
- Never accept a large change whose tests were not run.
- Never let Copilot delete a failing test or weaken an assertion without explaining why.
- Never paste passwords, cloud keys or tokens into chat/files.
- Review every Flyway migration before running it.
- Review security filters, Kafka error handling, Docker/Kubernetes YAML and Terraform line by line.
- Commit after every completed prompt so rollback is easy.

## Suggested Git commit pattern

```text
chore: bootstrap multi-module build
feat(product): implement medication catalog
feat(security): add rsa jwt authentication
feat(order): create event-driven order workflow
test(inventory): cover duplicate reservation events
infra(k8s): add health probes and resource limits
observability: add metrics traces and dashboards
```

## When Copilot gets stuck

Ask it to stop editing and provide:

```text
Summarize the failure using: observed symptom, exact command, relevant error lines, likely layer, three ranked hypotheses, and the smallest diagnostic command for each. Do not change code until we identify the cause.
```

