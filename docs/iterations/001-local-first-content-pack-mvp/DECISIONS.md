# 001 - Decisions

## 2026-07-03

### Decision: First Version Is Content-First, Not Tool-First

Smart Velp v0.1 should prioritize a ready-to-study content pack experience rather than asking users to configure AI keys and generate their own lessons.

Reason:

- Normal learners want immediate learning, not a workflow tool.
- AI key configuration creates too much friction.
- Prepared content packs make the value proposition easier to understand and sell.

### Decision: Local-First Storage

User data, learning progress, vocabulary, and recordings should be stored locally on the user's device in the first version.

Reason:

- Avoids building a full backend too early.
- Keeps privacy simple.
- Reduces operating cost.
- Leaves room for future SaaS sync by abstracting storage later.

### Decision: Cloud Storage Only Hosts Static Packs

The first cloud component should be static object storage for `.smartvelp` packs and `catalog.json`.

Reason:

- The creator can update content by uploading new files.
- Users can download packs without a traditional backend.
- This supports both free demos and later paid content workflows.

### Decision: Do Not Download YouTube Videos

The app must not provide direct YouTube video downloading. YouTube-based lessons should store links, video ids, timestamps, and learning materials only.

Reason:

- Direct YouTube downloading creates compliance risk.
- Official playback and authorized/self-produced media keep the product safer.

### Decision: Keep License and Account Systems Out of v0.1

v0.1 should not include a custom account system or self-built license server.

Reason:

- This iteration should validate study experience and content demand first.
- Payment and delivery can initially be handled through external digital product platforms.
