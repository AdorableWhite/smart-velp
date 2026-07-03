# 001 - Local First Content Pack MVP

## 1. Overview

Product name: Smart Velp

Iteration goal: build a local-first language learning app based on downloadable content packs. Users should be able to download or import a prepared learning pack, then immediately study through sentence-level listening, shadowing, vocabulary collection, and review.

Product positioning:

> Practice listening and speaking with real video lessons in 15 minutes a day.

This iteration intentionally avoids a full SaaS architecture. User content, progress, vocabulary, and study records are stored locally on the user's own device. Cloud storage is used only for publishing downloadable content packs.

## 2. Background

The earlier direction of selling a general AI generation tool creates too much friction for normal language learners. Requiring users to find videos, import subtitles, configure API keys, select models, generate lessons, and sync files across devices is too complex for the first version.

The first product should sell a clear learning outcome instead of a configurable tool:

- Users open the app and start a prepared lesson.
- The app handles playback, subtitles, shadowing, vocabulary, and review.
- The creator only needs to update content packs and upload them to cloud file storage.

## 3. Goals

- Validate whether users are willing to pay for prepared language learning content packs.
- Provide a complete learning loop without requiring user-side AI configuration.
- Keep all user learning data local by default.
- Avoid building a traditional backend in the first iteration.
- Keep a clean migration path toward a future SaaS resource platform.

## 4. Non-Goals

This iteration will not include:

- User account system.
- Cloud learning record sync.
- Community or social features.
- User-generated public resource sharing.
- Full AI lesson generation for end users.
- Direct YouTube video downloading.
- Self-built payment system.
- Self-built license server.
- Full teacher or creator dashboard.

## 5. Target Users

### 5.1 Normal Language Learners

Users who want to practice listening and speaking but do not want to search for videos, download subtitles, organize vocabulary, or build Anki cards manually.

### 5.2 Spoken English Learners

Users preparing for workplace communication, interviews, meetings, study abroad, or everyday conversation.

### 5.3 Power Learners

Users who already practice intensive listening, shadowing, Anki review, or sentence mining and want a more integrated workflow.

### 5.4 Future Creators

Teachers, course creators, and language bloggers who may later create and publish their own `.smartvelp` content packs.

## 6. User Pain Points

- Finding suitable real video material is time-consuming.
- YouTube and video learning often becomes passive entertainment.
- Subtitles, vocabulary, notes, recordings, and flashcards are scattered across tools.
- Users do not want to configure AI API keys, base URLs, or model parameters.
- Cross-device sync is hard, but users still want to study on phone, tablet, and PC.
- Direct YouTube download creates compliance risk.

## 7. Product Principle

The first version should feel like a course player, not a technical tool.

The main user flow should be:

```text
Download or import content pack
Open today's lesson
Listen sentence by sentence
Shadow and record
Save useful expressions
Review cards
Finish the lesson
```

Advanced creation and AI generation features can be added later, but should not block the basic learning path.

## 8. Product Form

Recommended product form:

- Desktop app: for importing larger packs and studying on PC.
- Mobile or tablet app: for daily study.
- Web/PWA preview: optional later, not required in this iteration.

Recommended technology direction:

- Frontend: React + TypeScript.
- Cross-platform shell: Tauri 2 or Capacitor.
- Local database: SQLite.
- Large files: local file system.
- Static cloud content: Cloudflare R2, S3, Supabase Storage, or Firebase Storage.
- Early sales and delivery: Lemon Squeezy, Gumroad, Xiaoe-tech, or another digital product platform.

## 9. Content Pack Model

Content pack extension:

```text
.smartvelp
```

The content pack is a zip-based package containing metadata, lesson data, subtitles, assets, and optionally authorized media files.

Example:

```text
business-english-30days.smartvelp
  manifest.json
  lessons/
    lesson-001.json
    lesson-002.json
  captions/
    lesson-001.vtt
    lesson-002.vtt
  media/
    lesson-001.mp4
  assets/
    cover.jpg
```

If the lesson is based on YouTube content, the pack must not contain the YouTube video file. It should only contain metadata and learning material:

```text
youtubeVideoId
startTime
endTime
captions
explanations
vocabulary
exercises
shadowingTasks
```

Only self-produced, licensed, purchased, or redistributable media may be included in `media/`.

## 10. Manifest Schema

Example `manifest.json`:

```json
{
  "id": "business-english-30days",
  "title": "30 Days of Business English Intensive Listening",
  "version": "1.0.0",
  "language": "en",
  "targetUserLanguage": "zh-CN",
  "level": "B1-B2",
  "lessonCount": 30,
  "license": "paid",
  "createdAt": "2026-07-03",
  "publisher": "Smart Velp",
  "checksum": "sha256..."
}
```

Required fields:

- `id`: stable package id.
- `title`: display title.
- `version`: semantic version.
- `language`: learning language.
- `targetUserLanguage`: explanation language.
- `level`: target difficulty.
- `lessonCount`: number of lessons.
- `license`: `free`, `paid`, or `private`.
- `publisher`: content publisher.
- `checksum`: integrity hash.

## 11. Lesson Schema

Example `lesson-001.json`:

```json
{
  "id": "lesson-001",
  "title": "Introducing Yourself in a Meeting",
  "description": "A short workplace introduction scene.",
  "durationSeconds": 180,
  "sourceType": "local-media",
  "mediaPath": "media/lesson-001.mp4",
  "captionPath": "captions/lesson-001.vtt",
  "level": "B1",
  "tags": ["workplace", "meeting", "self-introduction"],
  "sentences": [
    {
      "id": "s001",
      "startMs": 1200,
      "endMs": 4500,
      "text": "Thanks everyone, I'm excited to join the team.",
      "translation": "谢谢大家，我很高兴加入这个团队。",
      "notes": ["I'm excited to... is useful for positive introductions."],
      "vocabulary": [
        {
          "term": "excited to",
          "meaning": "很高兴做某事",
          "example": "I'm excited to work with you."
        }
      ],
      "shadowingPrompt": "Repeat with the same rhythm and stress."
    }
  ],
  "exercises": [
    {
      "type": "dictation",
      "sentenceId": "s001",
      "answer": "Thanks everyone, I'm excited to join the team."
    }
  ]
}
```

## 12. Cloud Content Publishing

Cloud storage only hosts static files. It does not store user progress or private user data.

Recommended structure:

```text
content/
  catalog.json
  packs/
    business-english-30days/
      1.0.0/
        pack.smartvelp
        cover.jpg
      1.0.1/
        pack.smartvelp
        cover.jpg
```

Example `catalog.json`:

```json
{
  "packs": [
    {
      "id": "business-english-30days",
      "title": "30 Days of Business English Intensive Listening",
      "version": "1.0.0",
      "level": "B1-B2",
      "lessonCount": 30,
      "size": 248000000,
      "coverUrl": "https://cdn.smartvelp.com/packs/business-english-30days/1.0.0/cover.jpg",
      "downloadUrl": "https://cdn.smartvelp.com/packs/business-english-30days/1.0.0/pack.smartvelp",
      "sha256": "xxx"
    }
  ]
}
```

## 13. Core User Flows

### 13.1 Import Local Content Pack

1. User opens the app.
2. User selects `Import Pack`.
3. User chooses a `.smartvelp` file.
4. App validates the package structure and checksum.
5. App imports lessons and assets into local storage.
6. User sees the pack in the library.

### 13.2 Download Content Pack

1. App fetches remote `catalog.json`.
2. User browses available packs.
3. User taps `Download`.
4. App downloads the pack to local storage.
5. App verifies `sha256`.
6. App imports the pack into the local library.

### 13.3 Study a Lesson

1. User opens a pack.
2. User selects today's lesson.
3. App opens the sentence-level player.
4. User listens sentence by sentence.
5. User repeats selected sentences.
6. User saves vocabulary or full sentences.
7. User completes exercises.
8. App saves local progress.

### 13.4 Review Vocabulary

1. User opens review.
2. App shows saved words, phrases, or sentences.
3. User marks each card as remembered or forgotten.
4. App updates review status locally.

## 14. Functional Requirements

### P0 - Must Have

- Import `.smartvelp` content pack from local file.
- Validate package manifest and required files.
- Display imported content packs in the library.
- Display lesson list with progress.
- Play local video or audio from a content pack.
- Display sentence-level subtitles.
- Click subtitle sentence to seek playback.
- Play, pause, seek, and change speed.
- Repeat current sentence.
- Show or hide target-language text.
- Show or hide translation.
- Save vocabulary, phrases, or sentences.
- Store all learning progress locally.
- Basic local data backup by exporting user data or full pack with progress.

### P1 - Should Have

- Fetch remote `catalog.json`.
- Download pack from cloud storage.
- Verify downloaded file hash.
- Detect package updates by version.
- Preserve user progress when updating a pack.
- Record shadowing audio per sentence.
- Replay original audio and user's recording.
- Basic review cards.
- Daily progress summary.

### P2 - Nice to Have

- License-code based download access.
- Anki export.
- Local network transfer between PC and mobile.
- User-created pack editor.
- AI-assisted content generation for creators.
- Cloud account system.
- SaaS shared resource library.

## 15. Local Data Requirements

All user-specific data is stored locally:

- Imported pack list.
- Lesson completion status.
- Playback position.
- Saved vocabulary.
- Saved sentences.
- Review history.
- Shadowing recordings.
- App settings.

Recommended local storage:

```text
SmartVelp Library/
  library.sqlite
  assets/
    sha256_xxx.mp4
    sha256_xxx.vtt
  packs/
    business-english-30days/
      manifest.json
      lessons/
      captions/
      media/
  recordings/
    lesson-001/
      s001.m4a
```

## 16. Payment and Access

First iteration recommended options:

### Option A - External Digital Product Delivery

Use Lemon Squeezy, Gumroad, Xiaoe-tech, or a similar platform to sell and deliver `.smartvelp` files. The app only imports files.

Pros:

- No custom account system.
- No custom payment system.
- Fastest to launch.

Cons:

- Download experience is outside the app.
- Links or files may be shared.

### Option B - Static Catalog with Public Downloads

Host public packs on Cloudflare R2 or similar object storage. App downloads from `catalog.json`.

Pros:

- Very simple.
- Good for free packs and demos.

Cons:

- Not suitable for protected paid packs.

### Option C - License-Based Download Later

Use a third-party license service or serverless function to validate license codes and return signed download URLs.

Pros:

- Better paid content protection.
- Still avoids a traditional backend.

Cons:

- More implementation work.
- Requires an external trusted service.

## 17. Compliance Requirements

- Do not provide direct YouTube video downloading.
- Do not extract YouTube audio as downloadable files.
- YouTube content should be played through official YouTube playback methods.
- Content packs must not include unauthorized YouTube video files.
- Packaged media must be self-produced, licensed, purchased with redistribution rights, or otherwise legally redistributable.
- User-uploaded or user-imported content should include a notice that users are responsible for content rights.

## 18. MVP Content Recommendation

First paid content pack:

```text
30 Days of Business English Intensive Listening
```

Each lesson:

- 1 to 3 minute real or realistic workplace video/audio.
- Sentence-level English subtitles.
- Chinese explanations.
- 5 to 10 useful expressions.
- 3 key shadowing sentences.
- 3 dictation or comprehension exercises.
- Review cards generated from key expressions.

## 19. Success Metrics

Product validation:

- Pack purchase conversion rate.
- Pack import success rate.
- Lesson completion rate.
- 7-day retention.
- Average study minutes per user.
- Shadowing usage rate.
- Vocabulary save rate.
- Review card completion rate.
- Repeat purchase intent or second-pack purchase rate.

Technical quality:

- Import failure rate.
- Download failure rate.
- Checksum validation failure rate.
- Playback error rate.
- Local database corruption rate.

## 20. Acceptance Criteria

This iteration is acceptable when:

- A user can import a valid `.smartvelp` pack and see it in the library.
- A user can open a lesson and play the associated media.
- Subtitles are synchronized at sentence level.
- A user can loop a sentence and save a word or sentence.
- Lesson progress persists after closing and reopening the app.
- A user can complete at least one full lesson without needing an account or AI API key.
- A sample pack can be hosted remotely and downloaded into the app.
- Unauthorized YouTube media is not included in sample packs.

## 21. Future Roadmap

### v0.1

Local learning app and manual pack import.

### v0.2

Remote catalog and app-based pack download.

### v0.3

Shadowing recording, review cards, and progress dashboard.

### v0.4

License-code download access and paid-pack workflow.

### v1.0

Creator tool for building `.smartvelp` packs.

### v2.0

Account system, cloud resource library, shared content, teacher dashboard, and SaaS platform.

## 22. Open Questions

- Which platform should be shipped first: desktop, mobile, or PWA?
- Should the first content pack use fully licensed/self-produced media or YouTube embedded lessons?
- Which sales platform should be used first for content pack delivery?
- Should user progress be exportable as a separate backup file?
- Should the app support Anki export in v0.1 or defer it to P2?
