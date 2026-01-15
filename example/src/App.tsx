import YoutubeDownloader, {
  type PlaybackData,
  VideoQuality,
} from '@pigeonmal/react-native-youtube-downloader';
import { useEffect, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';

export default function App() {
  const [result, setResult] = useState<PlaybackData | null>(null);

  useEffect(() => {
    const videoID = 'DGMIUHewL2c';
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
        <Text>Result: {JSON.stringify(result.videoDetails)}</Text>
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
