# NOTEBOOK-SECTIONS.md

Detailed rules for the notebook content and visual system, kept out of `SKILL.md` to keep activation lightweight. Load this when creating or editing notebook sections, diagrams, or deciding how to scale the notebook.

## Learning check

Before writing a section, confirm the notebook already knows:

- the configured explanation level (store: **Intern, New Grad, Junior, Mid-Level, Senior, Staff, Principal, Distinguished**)
- which sections exist and why
- whether this is a compact-repo notebook (fewer sections) or a full notebook

Explanation level changes **depth and terminology**, not rigor. Per-level behavior lives in `ARCHITECTURE-CHECK.md`.

## Section definitions

Keep the core notebook stable. Sections may be omitted when genuinely irrelevant. See `SKILL.md` section 9 (Compact-repo mode) for when to omit.

### A. Project Map

A one-screen map of the major system.

Show:

- clients / entry points
- major components
- persistent stores
- queues / caches
- external systems
- important trust/runtime boundaries

Prefer a diagram plus terse annotations.

### B. System Architecture

Explain:

- major layers
- responsibilities
- dependency direction
- important invariants
- process/runtime boundaries
- architectural style where useful

Do not list every file.

### C. Core Components

For each important component:

```text
Purpose
Owns
Does NOT own
Public interface / entry points
Dependencies
Important state
Failure behavior
```

"Does NOT own" is required when ownership could otherwise be ambiguous.

### D. Key Flows

Document important end-to-end flows.

Each flow should answer:

```text
Where does it enter?
Which components touch it?
Where is state read/written?
What external system is called?
What leaves the system?
Where can it fail?
```

Examples:

- sign in
- create invoice
- execute inference request
- upload file
- generate report

### E. Data / State Ownership

For meaningful state, show:

- owner
- source of truth
- storage location
- mutation path
- read path
- cache relationship if present
- tenant/user boundary if relevant

Pay special attention to authorization and tenant isolation.

### F. Decisions & Tradeoffs

Record only decisions a future engineer may reasonably question.

For each:

```text
Decision
Why
Alternatives considered
What we gain
What we give up
Evidence / assumption
When to revisit
```

Do not create decision-record theater for trivial choices.

### G. Failure Modes / Risks

For important paths:

```text
What can fail?
What does the user/system experience?
How is it detected?
How is it recovered?
What data can be lost, duplicated, delayed, or exposed?
Could a security or tenant boundary be crossed?
```

### H. Current vs Proposed Architecture

Clearly distinguish:

- **Current** — exists in code now
- **Proposed** — planned but not implemented

Use solid visual treatment for current architecture and dashed/dotted visual treatment for proposed architecture<br>
(solid border/edge = current, dashed/dotted border/edge = proposed).

Never present a proposed architecture as implemented reality.

### I. Recent Architectural Changes

Maintain a short reverse-chronological feed:

```text
Date / task
What changed structurally
Why
Affected components / flows
New tradeoff, risk, or dependency
```

Do not duplicate raw git history.

### J. Dependencies & External Contracts

Track dependencies that materially affect how the system works.

Include only meaningful dependencies, not every npm/pip package.

For each relevant dependency:

```text
Dependency / service
Why it exists
Where it is used
What contract we rely on
Failure / timeout behavior
Version or compatibility constraint if important
Replacement / fallback considerations if relevant
```

Examples:

- PostgreSQL
- Redis
- Stripe
- S3
- model runtime
- message broker
- external identity provider
- public/internal API consumed across subsystem boundaries

Also document important internal contracts:

- API shapes
- events/messages
- data schemas
- interface invariants

### K. Verification & Evidence

Record how important architectural claims were validated.

Prefer:

- tests
- benchmarks
- profiling
- measurements
- production/local traces
- official documentation
- reproducible commands

Over:

- tutorial convention
- "best practice" without context
- infrastructure fashion
- unmeasured performance assumptions

Example:

```text
Claim:
Database reads are the current bottleneck.

Evidence:
P95 endpoint = 420 ms
SQL query = 310 ms average across 50 local test requests

Decision:
Index/query work is justified before introducing a cache.
```

Do not fill this section with routine unit-test noise. Capture evidence that affects engineering decisions.

## Diagrams and readability

Before saving an updated notebook, inspect each diagram for:

- dimensional fit
- text clipping
- overlap
- edge crossings
- label readability
- consistent alignment
- clean visual hierarchy
- browser resizing
- current/proposed distinction
- unnecessary decorative elements

If a diagram is unreadable at normal laptop width, fix it.

Do not assume the first generated diagram is acceptable.

Prefer simple system diagrams that communicate ownership and flow over decorative complexity.

## Scaling to larger repositories

Default single file:

```text
engineering-notebook.html
```

For genuinely large repositories, it may become a bookmark index linking to:

```text
engineering-notebook/
  auth.html
  billing.html
  inference.html
```

Split only when the single notebook becomes meaningfully difficult to navigate.

## Existing vs new repositories

### Existing repositories

When bootstrapping:

1. inspect repository structure
2. identify runtime entry points
3. identify meaningful dependencies
4. identify persistent stores
5. identify major components
6. trace 2–5 important flows
7. inspect state ownership
8. inspect auth/tenant boundaries where present
9. inspect tests and available measurements
10. create the notebook
11. mark unknown rationale/uncertain architecture explicitly

Never invent historical rationale. Use:

```text
Rationale: Not established from repository evidence.
```

when necessary.

### New repositories

For new projects:

1. create a minimal notebook from agreed architecture
2. mark unbuilt components as proposed
3. keep current architecture limited to real code
4. update after meaningful tasks
5. use evidence as implementation matures