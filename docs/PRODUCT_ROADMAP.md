# ESOFTMEDIA VIDEO EDITOR — Product Roadmap

The product is an Android video editor that works **fully offline**. Media files remain on the device; there are no accounts, uploads, cloud projects, analytics calls, or runtime internet requirement.

## Product principles
- Non-destructive editing: keep the original media untouched and store trim/effect/layer instructions in the project.
- Local ownership: projects, thumbnails, audio, and exports stay on-device.
- Phone-first workflow: touch-friendly controls, readable timeline labels, portrait and landscape support.
- Predictable performance: proxy/low-resolution preview options, background work, storage checks, and clear progress.
- Honest export: never show an Export Complete state until a playable output file has been finalized.

## Phases and acceptance criteria

### Phase 0 — Foundation and project safety
- [ ] Separate UI, editor state, media metadata, project storage, and rendering responsibilities.
- [ ] Create, rename, reopen, duplicate, and delete local projects.
- [ ] Autosave edit decisions locally and recover the last valid draft after process death.
- [ ] Undo/redo for edit actions; project versioning and safe-save behavior.
- [ ] Request only platform media access through the system picker; no internet permission.

### Phase 1 — Real video timeline
- [ ] Read duration and dimensions for every imported source.
- [ ] Multi-clip video track with thumbnails, selection, reorder, duplicate, and delete.
- [ ] Frame/time-based trim handles in milliseconds, with precise time readout.
- [ ] Split the selected clip at the playhead without changing total source coverage.
- [ ] Timeline zoom, horizontal scroll, playhead scrubbing, snapping, and clip-edge markers.
- [ ] Playback follows the ordered clip sequence, not only the first imported URI.

### Phase 2 — Audio workspace
- [ ] Multiple music, sound-effect, and voiceover clips on independent tracks.
- [ ] Clip volume, mute, fades, trim, move, and delete.
- [ ] Extract audio from a selected video clip.
- [ ] Record voiceover locally and show track waveform/level.
- [ ] Music ducking under voiceover as an optional mix setting.

### Phase 3 — Text, captions, and overlay layers
- [ ] Editable text layers with font, size, color, stroke, shadow, alignment, and background.
- [ ] Per-layer in/out timing and simple entrance/exit animation.
- [ ] Stickers, shapes, watermark, and picture-in-picture media.
- [ ] Layer ordering, transform handles, and safe-area guides.

### Phase 4 — Visual editing
- [ ] Crop, rotate, flip, aspect ratio, canvas color/blur background.
- [ ] Color controls and filter presets with reset and before/after preview.
- [ ] Speed changes, freeze frame, reverse, blur, zoom, and simple effects.
- [ ] Transitions between adjacent clips and project-wide preview consistency.

### Phase 5 — Rendering and export
- [ ] Local MP4 render pipeline using Media3 Transformer.
- [ ] Resolution, frame-rate, and quality presets supported by the device.
- [ ] Progress, cancel, error recovery, free-space preflight, and export history.
- [ ] Save to Movies/ESOFTMEDIA through Android MediaStore and offer the system share sheet.
- [ ] Verify the finished file can be reopened and played before reporting success.

### Phase 6 — Polish and release readiness
- [ ] Project gallery, recent projects, templates, and reusable title cards.
- [ ] Lightweight preview mode for low-memory phones.
- [ ] Accessibility, responsive layouts, rotation handling, and long-project performance.
- [ ] Unit tests for timeline math plus device tests for import, playback, editing, and export.
- [ ] Release signing, versioning, crash-free acceptance checks, and install test.

## New product ideas to include
1. **Smart Snap:** align clip edges, text starts, and audio beats using optional magnetic snapping; snapping can be toggled off for fine edits.
2. **Quick Story:** one-tap 9:16, 1:1, and 16:9 project setup with a title-safe guide.
3. **Auto-save + Undo:** protect long editing sessions and make experimentation safe.
4. **Low-memory Preview:** reduce preview resolution without lowering export quality.
5. **Reusable Brand Kit:** save local fonts/colors/logo overlays for consistent intros, outros, and watermarks.
6. **Export Queue:** queue multiple local renders and show storage estimates before starting.

## Current implementation audit (based on the repository)
The current repository has a Compose editor shell, one-video ExoPlayer preview, a multi-select picker, basic adjustment/filter UI, and a GitHub Actions debug APK workflow. Several tool panels remain UI placeholders. The current percentage range slider is not yet connected to an exact media trim/export operation, and the current split callback does not perform a true source-range split. Phases above are therefore acceptance criteria, not claims that those features are already complete.
