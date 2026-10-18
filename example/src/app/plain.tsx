import { useRouter } from 'expo-router';
import { StyleSheet, Text, View } from 'react-native';

/** Reached with a plain <Link>: Expo Router's default native animation, back gesture and back button, untouched. */
export default function Plain() {
  const router = useRouter(); // the normal Expo Router router
  return (
    <View style={styles.screen}>
      <Text style={styles.title}>Normal Expo Router screen</Text>
      <Text style={styles.body}>
        You got here with a plain {'<Link>'}: the default native slide animation, the iOS swipe-back and the Android back
        button all work exactly as without this package.
      </Text>
      <Text style={styles.button} onPress={() => router.back()}>
        Back (router.back)
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, padding: 24, paddingTop: 96, gap: 16, backgroundColor: '#F4F4F8' },
  title: { fontSize: 26, fontWeight: '700' },
  body: { fontSize: 16, lineHeight: 24, color: '#55555C' },
  button: {
    alignSelf: 'flex-start',
    backgroundColor: '#0D5379',
    color: 'white',
    fontWeight: '700',
    paddingHorizontal: 20,
    paddingVertical: 12,
    borderRadius: 20,
    overflow: 'hidden',
  },
});
