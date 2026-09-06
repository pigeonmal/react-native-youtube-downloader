import Foundation
import NitroModules

/**
 * The extractor is currently Android-only. Keeping the generated Nitro
 * surface available on iOS makes the package fail explicitly instead of
 * falling back to a legacy bridge or returning an invalid stream.
 */
final class YoutubeDownloader: HybridYoutubeDownloaderSpec {
  func extractYoutubeStream(options: ExtractStreamOptions) throws -> Promise<PlaybackData> {
    throw NSError(
      domain: "YoutubeDownloader",
      code: 1,
      userInfo: [NSLocalizedDescriptionKey: "YouTube extraction is currently supported on Android only"]
    )
  }

  func generatePoToken(videoId: String) throws -> Promise<String> {
    throw NSError(
      domain: "YoutubeDownloader",
      code: 1,
      userInfo: [NSLocalizedDescriptionKey: "PoToken generation is currently supported on Android only"]
    )
  }
}
