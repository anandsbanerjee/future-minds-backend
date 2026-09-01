# Future Minds — Use Case Developer Handbook / Playbook Generator

Create a professionally structured, downloadable **Word document (.docx)** that captures the complete implementation history and developer handover for the use case we have just completed in this chat.

The document must be detailed enough that a developer who was not involved in the original work can understand:

- what the use case was intended to achieve;
- how the implementation was approached;
- what decisions were made and why;
- what prompts were executed;
- what commands were run;
- what code areas changed;
- what tests were performed;
- what problems occurred;
- how those problems were diagnosed and resolved;
- what remains deferred;
- and whether the use case can be considered closed.

Use the **entire conversation thread for this use case**, including:

- all earlier prompts and responses;
- uploaded inspection reports;
- implementation reports;
- closure reports;
- troubleshooting discussions;
- testing outcomes;
- architecture decisions;
- commands;
- code snippets;
- API examples;
- user corrections and clarifications.

Do not produce merely a summary. Produce a **developer handbook / implementation playbook / engineering audit trail**.

---

# 1. Document title

Use a title in this format:

**[USE CASE ID] — [USE CASE NAME]**  
**Implementation Playbook, Developer Handbook and Closure Record**

Example:

**EP-05.5 — Update Parent Profile**  
**Implementation Playbook, Developer Handbook and Closure Record**

Include:

- Future Minds Learning Diagnostic
- relevant Epic
- use case ID
- document purpose
- document date
- implementation status

---

# 2. Executive overview

Provide a concise executive summary explaining:

- business capability delivered;
- why the use case exists;
- major backend/mobile components involved;
- final implementation outcome;
- overall closure status;
- important deferred capabilities.

This section should help a new developer understand the feature in a few minutes.

---

# 3. Business and product context

Document:

- Epic
- use case ID
- use case name
- related BRD requirement IDs
- business capability
- MVP priority
- user/persona affected
- acceptance criteria
- explicit scope
- out-of-scope items

Clearly distinguish:

- business requirement;
- architecture decision;
- implementation decision;
- deferred capability.

Do not invent BRD requirements that were not actually referenced in the thread.

---

# 4. Starting state / prerequisites

Explain what already existed before this use case started.

For example:

- previous use cases completed;
- existing authentication/session behaviour;
- existing backend endpoints;
- existing database entities;
- existing mobile screens;
- existing prototype placeholders;
- existing infrastructure assumptions.

Include a dependency diagram where useful.

Example:

EP-05.1  
↓  
EP-05.2  
↓  
EP-05.3  
↓  
EP-05.4  
↓  
EP-05.5

---

# 5. Key architecture and design decisions

Create a dedicated section documenting every material decision made during the thread.

For each decision provide:

- decision;
- problem being solved;
- alternatives considered;
- selected approach;
- reasoning;
- implications;
- risks;
- deferred consequences.

Use an ADR-style table where useful.

Examples of decisions may include:

- identity source of truth;
- app-owned versus IdP-owned fields;
- endpoint choice;
- PATCH versus PUT;
- state ownership;
- audit strategy;
- error-handling strategy;
- persistence model;
- navigation decision;
- testing approach.

Do not silently rewrite historical decisions. Preserve what was actually agreed.

---

# 6. End-to-end implementation roadmap

Reconstruct the actual numbered steps used during the use case.

For example:

1. Backend read-only inspection
2. Mobile read-only inspection
3. Architecture decision
4. Backend implementation
5. Backend implementation review
6. Backend manual verification
7. Mobile implementation
8. Mobile implementation review
9. Automated verification
10. Emulator/E2E testing
11. Regression verification
12. Closure inspection

For every step include:

- objective;
- reasoning;
- repository/folder from which it was executed;
- prompt used;
- commands used;
- expected outcome;
- actual outcome;
- status.

Where the workflow changed during the discussion, show the revised sequence rather than hiding the evolution.

---

# 7. Prompt catalogue

Create a dedicated chapter containing the important prompts used during the use case.

Include the actual useful prompts, organised under headings such as:

- Backend inspection prompt
- Mobile inspection prompt
- Architecture/regression investigation prompt
- Backend implementation prompt
- Mobile implementation prompt
- Closure prompt
- Troubleshooting prompt

For each prompt state:

- when to use it;
- which repository/directory to run Claude Code from;
- what the prompt is designed to prove or change;
- whether it is read-only or implementation-producing.

Preserve the substance of the actual prompts from the conversation.

Do not fabricate prompts that were not used unless clearly labelled as a consolidated/reusable version.

---

# 8. Commands and developer operations

Create a command reference section.

Include all relevant commands discussed or executed, grouped logically:

## Git

Examples:

- git status
- git branch --show-current
- git diff
- git log
- branch/commit commands where applicable

## Backend

Examples:

- Maven test commands
- Spring Boot startup commands
- environment-variable inspection
- profile configuration

## Mobile

Examples:

- npm test / jest
- TypeScript checking
- lint
- Expo/Metro startup

## API verification

Include:

- HTTP methods;
- URLs;
- headers;
- sample request bodies;
- expected status codes.

## Runtime/environment

Include useful Windows CMD / PowerShell commands discussed.

Explain what each command was being used to validate.

---

# 9. Backend implementation

Document the backend implementation in developer-oriented detail.

Include:

- endpoints added/changed;
- DTOs;
- controller changes;
- service changes;
- persistence/entity changes;
- Flyway migrations;
- security matcher changes;
- audit behaviour;
- transaction boundaries;
- response contract;
- validation rules;
- identity protections.

Include an API contract section with sample requests and responses.

Where appropriate include diagrams such as:

JWT subject  
↓  
ParentController  
↓  
ParentAccountService  
↓  
ParentAccountRepository  
↓  
MySQL

Explicitly mention files that changed and files intentionally left unchanged.

---

# 10. Mobile implementation

Document:

- route/screen changes;
- API client changes;
- context/provider changes;
- state ownership;
- UI modes;
- validation;
- loading/saving/error states;
- navigation;
- security/privacy behaviour;
- test additions.

Explain how the mobile layer integrates with the backend rather than only listing files.

Where appropriate include a flow such as:

Parent Details  
↓  
updateProfile()  
↓  
PATCH /parents/me  
↓  
backend response  
↓  
ParentAccountProvider  
↓  
Profile refreshes

---

# 11. Database changes

Document all schema changes.

Include:

- migration file;
- tables created;
- columns added;
- defaults;
- constraints;
- foreign keys;
- indexes;
- privacy considerations.

Explain the rationale behind the design.

If no DB change occurred, state that explicitly.

---

# 12. Security and identity model

Create a dedicated security section.

Document:

- authentication provider;
- JWT subject usage;
- role handling;
- which identity fields are immutable;
- which fields are IdP-owned;
- which fields are application-owned;
- what the client is prohibited from sending;
- how another user's record is prevented from being updated;
- how 401 differs from 403 in this implementation.

Where relevant, document token/session behaviour and Keycloak integration.

---

# 13. Automated testing

Provide a structured test inventory.

Separate:

## Backend tests

- controller/security tests
- service tests
- persistence/audit tests
- regression tests

## Mobile tests

- API tests
- provider/context tests
- screen tests
- validation tests
- regression tests

Include final counts where available.

Example:

- Backend: 55/55 passing
- Mobile: 154/154 passing
- TypeScript: PASS
- Lint: PASS

Explain important test limitations, such as mocked JWTs versus real Keycloak token validation.

---

# 14. Manual API verification

Document the actual manual verification performed.

Create a table:

| Test | Request | Expected | Actual | Result |
|---|---|---|---|---|

Examples:

- GET current user
- PATCH update
- partial PATCH
- invalid input
- unauthenticated request
- forbidden request
- persistence check
- repeat provisioning regression

Do not mark tests as PASS unless the thread says they were completed.

---

# 15. Emulator / runtime verification

Document the real mobile verification.

Include:

- sign-in;
- profile loading;
- edit/save;
- cancel;
- validation;
- preference update;
- network failure behaviour;
- app restart;
- backend persistence.

Separate automated evidence from runtime evidence.

If a particular test was deliberately out of scope, state why.

---

# 16. Troubleshooting and incident log

This section is mandatory.

For every significant problem encountered, provide:

### Problem
What happened.

### Symptoms
Observed status/error/output.

### Initial hypotheses
What was suspected.

### Investigation
Prompts, commands, logs or diffs used.

### Root cause
What was eventually established.

### Resolution
Exact configuration/code/process correction.

### Lesson learned
How to diagnose or avoid it in future.

Include environment-specific problems such as:

- authentication 401;
- issuer URI mismatch;
- localhost versus Android emulator `10.0.2.2`;
- Spring profiles;
- Keycloak token issuer;
- stale/expired tokens;
- API/network errors;
- tests passing while real runtime fails.

Do not hide failed hypotheses; present them as part of the diagnostic trail where useful.

---

# 17. Configuration notes

Document configuration needed for different execution contexts.

Where relevant distinguish:

## Host/Postman

For example:

localhost

## Android emulator

For example:

10.0.2.2

Explain why the values differ.

Document relevant:

- application.properties
- application-local.properties
- environment variables
- Keycloak URLs
- backend base URL
- issuer URI
- JWK set URI

Do not include secrets or real credentials.

---

# 18. Regression analysis

Show which earlier completed use cases were retested or protected.

Create a matrix such as:

| Earlier use case | Capability | Regression status |
|---|---|---|
| EP-05.2 | Provision account | PASS |
| EP-05.3 | Sign in/session | PASS |
| EP-05.4 | Load current parent | PASS |
| Current | Update profile | PASS |

Explain important regression conditions.

---

# 19. Deferred capabilities and non-goals

Explicitly document anything intentionally not implemented.

For each deferred item state:

- capability;
- why deferred;
- likely future use case / epic;
- architectural constraints.

Examples may include:

- verified email change;
- sign-out;
- global HTTP error handling;
- admin flows;
- advanced audit tooling.

Do not allow deferred items to be mistaken for completed capabilities.

---

# 20. Closure report

Include the final closure decision and evidence.

Structure:

- Overall status
- Backend status
- Mobile status
- Security status
- Testing status
- Manual verification status
- Runtime verification status
- Deferred items
- Remaining risks
- Final recommendation

Use one of:

- PASS
- PASS WITH DEFERRED CAPABILITIES
- FAIL

If the conversation ultimately resolved earlier blockers, ensure the final document reflects the final resolution rather than leaving obsolete blocker conclusions as the final status.

---

# 21. Developer quick-start / future maintenance guide

Add a concise section for a future developer answering:

- Where is this feature implemented?
- Which endpoints are involved?
- Which mobile screens are involved?
- Which DB migration introduced it?
- Which tests protect it?
- Which configuration values matter?
- What must never be changed casually?
- What future use case should be considered before extending it?

---

# 22. File inventory

Provide separate tables for:

## Backend files

| File | New/Modified | Purpose |

## Mobile files

| File | New/Modified | Purpose |

Include important test files.

---

# 23. Lessons learned

Summarise reusable engineering lessons from the use case.

Examples:

- inspect before implementing;
- resolve source-of-truth ownership explicitly;
- do not infer runtime authentication from mock-JWT tests;
- keep identity fields structurally absent from client DTOs;
- separate provisioning from user-driven profile changes;
- perform real API and emulator verification before closure;
- distinguish environment/configuration failures from code regressions.

Only include lessons supported by the actual thread.

---

# 24. Formatting requirements

Create a polished Word document suitable for:

- developer handover;
- GitHub/engineering records;
- audit trail;
- onboarding;
- future troubleshooting.

Use:

- title page;
- table of contents if practical;
- numbered headings;
- tables;
- code blocks;
- callout boxes for important decisions;
- clear PASS / deferred / risk labels;
- Australian English;
- professional formatting.

Keep prompts and command blocks readable and monospaced.

Avoid excessive decorative design.

---

# 25. Accuracy requirements

Before finalising:

1. Review the entire relevant chat thread.
2. Use attached reports where available.
3. Reconcile early findings with later corrections.
4. Do not leave superseded conclusions presented as current facts.
5. Clearly label any item that was proposed but not actually implemented.
6. Clearly distinguish:
   - verified fact;
   - design decision;
   - test evidence;
   - assumption;
   - deferred capability.
7. Do not invent file names, tests, status results or commands.
8. Do not expose secrets, passwords, access tokens or sensitive personal data.

---

# 26. Quality assurance

After creating the document:

- render/visually inspect it;
- check tables do not overflow;
- check code/prompt sections remain readable;
- check page breaks;
- verify heading hierarchy;
- verify there are no duplicated or contradictory sections;
- confirm the final closure status reflects the latest discussion.

---

# Deliverable

Generate the completed **downloadable Word document (.docx)**.

Use a filename in this pattern:

`[USE-CASE-ID]_[Short_Name]_Implementation_Playbook_and_Developer_Handbook.docx`

Example:

`EP-05.5_Update_Parent_Profile_Implementation_Playbook_and_Developer_Handbook.docx`

After generation, provide the download link.