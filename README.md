# @pigeonmal/react-native-youtube-downloader

A focused React Native Nitro Module for extracting direct YouTube audio/video
URLs. The Android implementation is project-owned: it uses the YouTube player
endpoint, a small client catalog, local format selection, optional PoToken
generation, and the caller's explicit HTTP range transport.

It does not depend on InnerTubeX, NewPipe, SABR, a prebuilt extraction AAR, or
another extractor runtime.

## What it does

- Uses direct playback profiles including `ANDROID_VR`, `VISIONOS`, and
  authenticated web/TV fallbacks.
- Keeps the actual returned client name.
- Selects `AUTO`, `LOW`, and `HIGH` audio formats locally, preferring Opus/WebM
  for `AUTO` and lower-bandwidth AAC/MP4 for `LOW`.
- Selects the best video format at or below the requested height.
- Preserves the media request headers needed by the returned URL.
- Keeps a small expiry-aware playback cache and temporarily avoids a client
  after its URL fails.
- Uses `Range: bytes=start-end` for seeking and downloads. It never adds a
  `range` query parameter.
- Retains only the small BotGuard WebView implementation needed for optional
  PoToken generation.

## Installation

```bash
npm install @pigeonmal/react-native-youtube-downloader
# or
yarn add @pigeonmal/react-native-youtube-downloader
```

## Usage

```ts
import YoutubeDownloader, {
  VideoQuality,
} from '@pigeonmal/react-native-youtube-downloader';

const playback = await YoutubeDownloader.extractYoutubeStream({
  videoId: 'dQw4w9WgXcQ',
  audioQuality: 'AUTO',
  videoQuality: VideoQuality.QUALITY_1080P,
});
```

Supported options include `playlistId`, `cookie`, `forceVisitorData`, and
`authenticatedOnly`.

## Range contract

The downloader returns a direct media URL and request headers. The player or
download transport must send bounded requests such as:

```http
Range: bytes=1000000-1999999
```

Every remote read should validate `206`, `Content-Range`, and the returned
length. Pure Music implements this contract in its custom Media3 data source.

## Client behavior

The extractor tries direct anonymous profiles first for public media. If an
authenticated request is explicitly required, it uses the authenticated
profiles and supplied cookies. `TVHTML5_SIMPLY` is retained as a tokenized
fallback; it is not falsely reported as working when YouTube returns an
unplayable response.

The implementation is intentionally direct-stream-first. SABR is not included
because this package no longer carries an external SABR runtime; adding SABR
later would require a project-owned Media3 SABR data source and tests.

## Source layout

The YouTube-facing code is organized under `com.youtubedownloader`:
- `client/`: Dedicated client configurations (`VisionOsClient`, `AndroidVrClient`, `TvSimplyClient`, `WebClient`, etc.)
- `potoken/`: BotGuard and PoToken generator, fast visitorData fetcher
- `extractor/`: Request builder, response parser, and candidate selector
- `cipher/`: Decryption cipher fallback
- `models/`: Type definitions and data classes

The Nitro adapter and generated native boundary live separately under
`com.margelo.nitro.youtubedownloader`, so future extractor updates can be
reviewed without changing the module API. See
[INNERTUBEX_VENDOR.md](INNERTUBEX_VENDOR.md) for the pinned reference and
update workflow.

## Development

```bash
JAVA_HOME=/path/to/jdk-21 ./gradlew \
  :pigeonmal_react-native-youtube-downloader:testDebugUnitTest
```

Network smoke tests are opt-in:

```bash
YOUTUBE_LIVE_TEST=1 ./gradlew \
  :pigeonmal_react-native-youtube-downloader:testDebugUnitTest \
  --tests com.youtubedownloader.innertubex.YoutubeExtractorLiveTest
```

## License

MIT. The project contains no bundled InnerTubeX or other extractor runtime.
