import { NitroModules } from 'react-native-nitro-modules';
import type { YoutubeDownloader as YoutubeDownloaderSpec } from './YoutubeDownloader.nitro';

export * from './types';
export type { YoutubeDownloader as YoutubeDownloaderSpec } from './YoutubeDownloader.nitro';

export const YoutubeDownloader =
  NitroModules.createHybridObject<YoutubeDownloaderSpec>('YoutubeDownloader');

export default YoutubeDownloader;
