# ARCHITECTURE-CHECK.md

Deep-dive for architectural decision-making. Load this when the task is classified as an architectural change, when recommending performance/scale/reliability architecture, or when calibrating explanation depth.

## Explanation-level calibration

Level changes **depth and terminology**, not the underlying architecture or technical rigor.

Do not claim the levels map universally to company-specific L3/L4/L5/etc. Engineering ladders vary by organization.

### Intern / New Grad / Junior

- explain unfamiliar infrastructure concepts when they become relevant
- define jargon briefly at first use
- explicitly connect a design choice to the current repository
- show the simplest credible alternative
- use it as a teachable moment, not a lecture
- keep the same technical substance but add context

### Mid-Level / Senior

- assume familiarity with common application architecture
- focus on boundaries, ownership, consistency, latency, operational cost, and failure modes
- explain uncommon or repository-specific concepts
- keep background explanation compact

### Staff / Principal / Distinguished

- optimize for decision density
- emphasize system constraints, second-order effects, interfaces, invariants, operational consequences, and alternatives
- avoid introductory explanations unless requested
- surface uncertainty and evidence directly

## Architectural-change checklist

Run an Architecture Check before coding when the change:

- adds/removes a service or process
- adds a database, cache, queue, or persistent store
- changes source-of-truth ownership
- introduces a new external dependency/API
- changes authentication/authorization boundaries
- changes tenant isolation behavior
- is a meaningful schema redesign
- introduces new async/concurrency behavior
- moves responsibility between major components
- changes a public/internal interface used across subsystem boundaries
- splits or merges major components
- changes deployment/runtime topology
- introduces a new consistency model

Routine work (bug fixes, styling, validation, tests, internal helpers, implementation details behind existing interfaces, small performance cleanup that does not alter topology) proceeds without an Architecture Check.

## Architecture Check format

Keep the check compact enough to be useful.

```md
## Architecture Check

### Requested change
What is being requested.

### Relevant current architecture
Only the part of the system affected.

### Proposed architecture
What would structurally change.

### Why this may help
Concrete benefit tied to a real constraint.

### Costs / tradeoffs
Complexity, consistency, latency, operations, security, coupling, cost, etc.

### Evidence
What measurements, tests, docs, or observed behavior support the change?
If evidence is missing, say so.

### Alternatives
Include the simplest credible alternative.

### Concepts worth knowing
Explain only what is necessary to evaluate this decision, calibrated to the configured engineering level.

### Recommendation
Give a recommendation with assumptions and uncertainty made explicit.

### Decision
State the smallest decision the repository owner needs to make.
```

Do not use vague wording such as "what the human needs to understand." The notebook and the check are written for the repository owner/engineer.

If a concept is unfamiliar:

- name it
- explain the problem it solves
- explain the problems it introduces
- relate it to this repository
- cite or point to official documentation when research tools are available
- do not substitute senior-sounding jargon for reasoning

## Measurement before complexity

Before recommending architecture primarily for performance, scale, or reliability:

1. identify the claimed bottleneck/risk
2. check whether it is worth measuring
3. measure when reasonably possible
4. prefer the least complex approach satisfying the requirement
5. record the evidence in the Verification & Evidence section

### Cache example (Redis)

Do not recommend Redis merely because reads exist.

First route through the questions above:

- are reads actually slow?
- is the database the bottleneck?
- would indexing/query changes solve it?
- what staleness is acceptable?
- what invalidates the cache?

Then the check might read:

```md
Redis would sit between ProductService and PostgreSQL.

Potential benefit:
Repeated reads can avoid the database.

New complexity:
Cached data can become stale, invalidation logic is required, and Redis
becomes another runtime dependency.

Evidence status:
We have not yet shown that PostgreSQL reads are the bottleneck.

Simpler alternative:
Measure the query and inspect indexing before adding a cache.
```

### Other burden

Do not introduce a queue merely because work is asynchronous.

First ask:

- is request latency unacceptable?
- does work need retries?
- does it need durable execution?
- can an in-process/background mechanism satisfy the current requirement?

Do not split a service because the repository is large or a diagram looks messy.

Require a real boundary or operational need.

## Evidence-first rule

Prefer, as evidence for an important choice:

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

Record how important claims were validated. Never fabricate historical rationale — when unknown, write:

```text
Rationale: Not established from current evidence.
```