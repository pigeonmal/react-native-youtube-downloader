# InnerTubeX boundary

The Android extractor is intentionally split into two areas:

- `com.youtubedownloader.innertubex`: the YouTube/InnerTube-facing implementation, client profiles, cipher handling, and PoToken support.
- `com.margelo.nitro.youtubedownloader`: the small Nitro adapter and native
  package loader. It converts extractor models to generated Nitro structs; it
  does not use `WritableMap`, `ReadableMap`, or the legacy bridge serializer.

The package is still compiled directly into this library. There is no InnerTubeX
Gradle dependency, AAR, or runtime dependency.

## Upstream reference

- Repository: https://github.com/MetrolistGroup/innertubex
- Pinned reference: `01edd668dfd940e01267b83a16e0b0cc95ba8d0c`

That exact commit is a dependency-version bump, not an extractor implementation
commit. The code in this package is a small Android-specific adaptation inspired
by the upstream architecture, with the transport and public API kept specific
to this library.

## Updating later

When upstream changes its client, cipher, or token behavior:

1. Fetch and review the upstream commits.
2. Apply only the corresponding changes inside `com.youtubedownloader.innertubex`.
3. Keep the Nitro adapter and JavaScript contract unchanged unless the public
   API intentionally changes.
4. Run the unit tests, the opt-in live tests, and the Mazica dev build.

This boundary makes upstream updates reviewable and prevents upstream extraction
changes from leaking into the React Native bridge or Pure Music integration.
