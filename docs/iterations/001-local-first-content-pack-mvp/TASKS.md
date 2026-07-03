# 001 - Tasks

## Product

- Define the first sample content pack topic.
- Confirm whether sample media is self-produced, licensed, or YouTube-linked.
- Define the minimum lesson count for the demo pack.
- Decide first target platform for delivery.

## Content Pack

- Define `.smartvelp` zip package validation rules.
- Create sample `manifest.json`.
- Create sample `lesson-001.json`.
- Prepare sample subtitle file.
- Prepare sample cover image.
- Prepare sample local media or YouTube-linked lesson metadata.

## App

- Implement local pack import.
- Implement manifest validation.
- Implement local library view.
- Implement lesson list view.
- Implement sentence-level media player.
- Implement subtitle seek and current sentence highlight.
- Implement sentence repeat.
- Implement show/hide target text.
- Implement show/hide translation.
- Implement vocabulary and sentence saving.
- Persist lesson progress locally.

## Storage

- Choose local database implementation.
- Define local data schema.
- Define local asset directory layout.
- Implement pack import transaction.
- Implement pack update behavior.

## Cloud Content

- Choose object storage provider.
- Define `catalog.json` schema.
- Host one sample pack.
- Implement catalog fetch.
- Implement pack download.
- Implement checksum validation.

## Compliance

- Add content rights notice for imported user files.
- Ensure YouTube lessons do not package unauthorized video files.
- Document allowed media source types.

## Testing

- Test importing a valid pack.
- Test rejecting invalid pack structure.
- Test playback and subtitle sync.
- Test progress persistence after app restart.
- Test pack download and checksum validation.
- Test local data export or backup path if included.

## Release

- Prepare demo content pack.
- Prepare first user onboarding flow.
- Prepare product page copy.
- Prepare early user feedback form.
