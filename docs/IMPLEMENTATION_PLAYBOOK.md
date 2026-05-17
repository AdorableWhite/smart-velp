# Smart VELP Implementation Playbook

## 1. Delivery Strategy

This document translates PRD v2 into execution tracks, milestones, and parallel sub-agent workstreams.

## 2. Milestones

### M1. Beta Stabilization

Goal:

- Make the current core product stable enough for repeated manual trial use.

Scope:

- Homepage restructuring
- UI copy cleanup
- Local and online learning flows
- Course and subtitle retranslation
- Responsive layout fixes

Exit criteria:

- `npm run build`
- `mvn -q -DskipTests compile`
- Core flows pass manual smoke checks

### M2. General Subtitle Model

Goal:

- Move backend subtitle logic from `en/cn` compatibility to a real `source/target` contract.

Scope:

- Domain model
- DTO contract
- Parser
- Translation manager
- Persistence reads and writes

Exit criteria:

- New subtitle writes prefer `sourceText/targetText/sourceLang/targetLang`
- Legacy data remains readable

### M3. Productization

Goal:

- Improve reliability, safety, installability, and release readiness.

Scope:

- Tests
- PWA install/update UX
- Error handling
- Security baseline
- Deployment flow cleanup

Exit criteria:

- CI-quality gates in place
- No repo hardcoded secrets
- PWA upgrade path documented and usable

## 3. Parallel Sub-agent Plan

## Agent A: Home + Library Flow

### Goal

Own the content-entry experience:

- online import
- local import
- continue learning
- library browsing

### Scope

- `frontend/src/pages/HomePage.tsx`
- `frontend/src/pages/LibraryPage.tsx`
- `frontend/src/components/library/TaskList.tsx`
- task and local media related API/service code

### Deliverables

- Homepage as a real workbench
- Continue-learning section
- Unified task card behavior
- Stable local/online list rendering

### Depends on

- Stable preferences store fields

### Can run in parallel

Yes

### Acceptance

- User can start from URL or local file
- User can reopen online or local learning content
- Library reflects online and local content accurately

### Risks

- Duplicate task-state logic between homepage and library

## Agent B: Study Page + Player

### Goal

Own the learning interaction surface.

### Scope

- `frontend/src/pages/StudyPage.tsx`
- `frontend/src/components/player/PlayerPanel.tsx`
- subtitle normalization utils
- download flow

### Deliverables

- Stable backend study route
- Stable local study route
- Course retranslation UI
- Local subtitle retranslation UI
- Subtitle modes, looping, speed, fullscreen

### Depends on

- Agent A for stable navigation flows
- Agent C for stable preferences

### Can run in parallel

Yes

### Acceptance

- `/study/backend/:id` works
- `/study/local/:id` works
- Retranslation flows visibly update status
- Language direction is visible in study UI

### Risks

- Shared style collisions in global CSS

## Agent C: Settings + Local Configuration

### Goal

Own all user-controlled local settings and model profiles.

### Scope

- `frontend/src/pages/SettingsPage.tsx`
- `frontend/src/components/settings/ProfileManager.tsx`
- `frontend/src/store/usePreferencesStore.ts`
- IndexedDB storage modules

### Deliverables

- Stable theme mode
- Stable source/target language configuration
- Stable local translation profile management
- Encrypted local API key persistence

### Depends on

- None, as long as store fields remain stable

### Can run in parallel

Yes

### Acceptance

- Changing settings affects homepage and study page
- Saved profiles can be created, selected, and deleted

### Risks

- Breaking shared store fields impacts multiple screens

## Agent D: PWA + Responsive UI + Copy Cleanup

### Goal

Own fit-and-finish.

### Scope

- `frontend/vite.config.ts`
- `frontend/index.html`
- `frontend/src/styles/global.css`
- shared layout files
- all user-facing copy cleanup

### Deliverables

- Install/update/offline polish
- Mobile/tablet/desktop layout consistency
- Unified and readable Chinese/English copy

### Depends on

- Can start in parallel
- Best finalized after other frontend agents stabilize structure

### Can run in parallel

Partially

### Acceptance

- No obvious layout break at 390 / 768 / 1280 widths
- No major garbled copy on key pages
- PWA remains buildable

### Risks

- Highest merge-conflict probability due to shared styles and copy

## Agent E: General Subtitle Model

### Goal

Own the canonical subtitle contract on the backend.

### Scope

- `backend/.../domain/model/SubtitleLine.java`
- `backend/.../interfaces/rest/dto/SubtitleLineDto.java`
- `backend/.../infrastructure/parser/SubtitleFileParser.java`
- `subs.json` write/read points

### Deliverables

- Canonical `source/target` subtitle handling
- Compatibility bridge for legacy `en/cn`
- Stable DTO structure for frontend

### Depends on

- None, recommended to start early

### Can run in parallel

Yes

### Acceptance

- Online, local, and retranslated subtitles all produce aligned structure

### Risks

- Legacy data compatibility bugs

## Agent F: Translation Runtime Contract

### Goal

Own runtime translation behavior consistency.

### Scope

- `TranslationOptions`
- `TranslationManager`
- provider implementations
- translation factory rules

### Deliverables

- Unified runtime override rules
- Consistent handling of provider/baseUrl/model/apiKey
- Consistent behavior across all task entry points

### Depends on

- Can run in parallel with Agent E

### Can run in parallel

Yes

### Acceptance

- Analyze, local subtitles, and course retranslate all behave consistently

### Risks

- Provider-specific URL semantics differ

## Agent G: Task Orchestration + Retranslation

### Goal

Own shared backend workflow orchestration.

### Scope

- `MediaApplicationService`
- task lifecycle updates
- progress state mapping
- retranslation workflow unification

### Deliverables

- Shared orchestration skeleton across:
  - online parse
  - local subtitle task
  - course retranslate

### Depends on

- Agent E and F outputs should be mostly stable first

### Can run in parallel

Partially, but best in second wave

### Acceptance

- All task types report consistent states
- Failures are surfaced reliably

### Risks

- Async failure handling may be inconsistent

## Agent H: Repository + Quality + Release

### Goal

Own persistence stability and product quality baseline.

### Scope

- repositories
- task persistence contract
- test strategy
- release and deployment path
- PWA reliability rules

### Deliverables

- Stable task persistence
- testing baseline
- release pipeline direction
- security cleanup backlog

### Depends on

- Works in parallel as design and initial implementation track

### Can run in parallel

Yes

### Acceptance

- Task metadata remains stable after restart
- Quality gates can be defined and later automated

### Risks

- Deployment strategy is not yet fully unified

## 4. Recommended Parallel Execution Order

### Wave 1

- Agent A
- Agent B
- Agent C
- Agent E
- Agent F

### Wave 2

- Agent D
- Agent H

### Wave 3

- Agent G

### Final Merge Pass

- Agent D final polish
- cross-agent regression check

## 5. Cross-agent Contracts

### Frontend Contracts

- `usePreferencesStore` field names are stable
- `DisplaySubtitle` remains the study-page canonical client shape
- shared layout ownership should be centralized during final merge

### Backend Contracts

- `TranslationOptions` is the only runtime translation contract
- subtitle DTO must support `source/target`
- task status must include title and language metadata

## 6. Acceptance Matrix

### Build Gates

- Frontend:
  - `npm run typecheck`
  - `npm run build`
- Backend:
  - `mvn -q -DskipTests compile`
- Later target:
  - frontend tests
  - backend tests

### Manual Smoke Scenarios

1. Submit online URL and open study page
2. Import local video and subtitle
3. Import subtitle-only and open study page
4. Retranslate a course
5. Retranslate a local subtitle task
6. Switch subtitle modes
7. Switch theme mode
8. Use a custom translation profile

## 7. Immediate Next Execution Priorities

### Priority 1

- Finish homepage and shell cleanup
- Finish copy cleanup on major screens

### Priority 2

- Reduce backend write-path dependence on `en/cn`
- Prefer `source/target` on new writes

### Priority 3

- Add quality baseline and remove hardcoded secret risks

