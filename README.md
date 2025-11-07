---

# 🎥 @pigeonmal/react-native-youtube-downloader

A **React Native TurboModule** for extracting YouTube stream information (audio & video) directly from video IDs.
Provides detailed playback metadata including formats, loudness data, and stream URLs.

A special thanks for Innertube implementation in android,
newPipeExtractor, and [Metrolist](https://github.com/mostafaalagamy/Metrolist)

---

## 🚀 Features

* Extract YouTube video and audio stream URLs.
* Supports configurable **audio** and **video** quality.
* Returns rich metadata (bitrate, MIME type, quality label, etc.).
* Built on **React Native TurboModule** for high performance.
* Fully typed with **TypeScript**.

---

## 📦 Installation

```bash
# Using npm
npm install @pigeonmal/react-native-youtube-downloader

# Or with yarn
yarn add @pigeonmal/react-native-youtube-downloader
```

> For React Native 0.71+ this module should link automatically.
> If not, follow standard TurboModule setup in your native code.

---

## 🔧 Usage

```ts
import YoutubeDownloader, { VideoQuality } from '@pigeonmal/react-native-youtube-downloader';
import type { ExtractStreamProps, PlaybackData } from '@pigeonmal/react-native-youtube-downloader';

const options: ExtractStreamProps = {
  videoId: 'dQw4w9WgXcQ', // YouTube video ID
  audioQuality: 'HIGH',
  videoQuality: VideoQuality.QUALITY_1080P,
};

async function getStreamData() {
  try {
    const streamData: PlaybackData = await YoutubeDownloader.extractYoutubeStream(options);
    console.log('Stream Data:', streamData);
  } catch (error) {
    console.error('Failed to extract stream:', error);
  }
}
```

---

## 🧠 API Reference

### `extractYoutubeStream(options: ExtractStreamProps): Promise<PlaybackData>`

Extracts the playback data for a YouTube video.

#### Parameters

| Name               | Type                        | Required | Description                                |
| ------------------ | --------------------------- | -------- | ------------------------------------------ |
| `videoId`          | `string`                    | ✅        | The YouTube video ID                       |
| `audioQuality`     | `'AUTO' \| 'LOW' \| 'HIGH'` | ✅        | Audio quality preference                   |
| `playlistId`       | `string`                    | ❌        | Optional playlist ID for context           |
| `videoQuality`     | `number`                    | ❌        | Desired video resolution (e.g., 720, 1080) |
| `cookie`           | `string`                    | ❌        | Optional authentication cookie             |
| `forceVisitorData` | `string`                    | ❌        | Optional YouTube visitor data override     |

#### Returns

A `Promise` resolving to a **PlaybackData** object containing:

* `audioStream`: `StreamPlayback` — audio stream info and URL.
* `videoStream`: `StreamPlayback` — video stream info and URL (if applicable).
* `videoDetails`: `VideoDetails` — metadata like title, author, and duration.
* `audioConfig`: `AudioConfig` — loudness and perceptual loudness levels.
* `streamExpiresInSeconds`: number of seconds until stream expiration.

---

## 📄 Type Definitions

### `PlaybackData`

```ts
interface PlaybackData {
  audioConfig?: AudioConfig;
  videoDetails?: VideoDetails;
  playbackTracking?: PlaybackTracking;
  streamExpiresInSeconds: number;
  audioStream: StreamPlayback;
  videoStream?: StreamPlayback;
  clientName: string;
}
```

### `AudioQuality`

```ts
type AudioQuality = 'AUTO' | 'LOW' | 'HIGH';
```

AUTO = if wifi ? HIGHT else LOW

### `VideoQuality` (enum)

```ts
enum VideoQuality {
  AUTO = -1,
  QUALITY_144P = 144,
  QUALITY_240P = 240,
  QUALITY_360P = 360,
  QUALITY_480P = 480,
  QUALITY_720P = 720,
  QUALITY_1080P = 1080,
  QUALITY_1440P = 1440,
  QUALITY_2160P = 2160,
}
```
AUTO = if wifi ? 1080p else 720p
---

## 🧑‍💻 Contributing

Pull requests are welcome!
If you encounter issues or want to suggest improvements, please open an issue.

---

## 🪪 License

This project is open source and available under the MIT License

---