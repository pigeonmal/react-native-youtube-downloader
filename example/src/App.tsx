import YoutubeDownloader, {
  type PlaybackData,
  VideoQuality,
} from '@pigeonmal/react-native-youtube-downloader';
import { useEffect, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';

export default function App() {
  const [result, setResult] = useState<PlaybackData | null>(null);

  useEffect(() => {
    const videoID = 'dQw4w9WgXcQ';
    YoutubeDownloader.extractYoutubeStream({
      videoId: videoID,
      audioQuality: 'AUTO',
      videoQuality: VideoQuality.QUALITY_1080P,
    })
      .then(setResult)
      .catch(console.error);
  }, []);
  return (
    <View style={styles.container}>
      {result != null ? (
        <Text>
          {'Result: '}
          {JSON.stringify({
            clientName: result.clientName,
            videoDetails: result.videoDetails,
            audioItag: result.audioStream.format.itag,
            videoItag: result.videoStream?.format.itag,
          })}
        </Text>
      ) : (
        <Text>WAIT</Text>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#fff',
  },
});
