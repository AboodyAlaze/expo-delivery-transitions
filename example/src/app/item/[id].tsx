import { useLocalSearchParams } from 'expo-router';
import { ScrollView, StyleSheet, Text } from 'react-native';
import { DeliveryTarget, useTransitionRouter } from 'expo-delivery-transitions';

export default function Item() {
  // Params, layouts, deep links: all normal Expo Router
  const { id, title, color } = useLocalSearchParams<{ id: string; title?: string; color?: string }>();
  const router = useTransitionRouter();
  const accent = color ?? '#00A884';

  return (
    <ScrollView style={styles.screen} contentContainerStyle={styles.content}>
      {/* The delivery transitions throw their parcels exactly at these (max 5) elements */}
      <DeliveryTarget index={0} style={[styles.header, { backgroundColor: accent }]}>
        <Text style={styles.headerTitle}>{title ?? id}</Text>
        <Text style={styles.headerSub}>route: /item/{id}</Text>
      </DeliveryTarget>

      <DeliveryTarget index={1} style={styles.stats}>
        <Text style={styles.stat}>12 min</Text>
        <Text style={styles.stat}>Free delivery</Text>
        <Text style={styles.stat}>4.8 ★</Text>
      </DeliveryTarget>

      <DeliveryTarget index={2} style={styles.card}>
        <Text style={styles.body}>
          This is a normal React Native screen rendered by Expo Router. The transition photographs it and plays on top.
        </Text>
      </DeliveryTarget>

      <DeliveryTarget index={3} style={styles.card}>
        <Text style={styles.body}>Going back is a quick cross-fade: nobody is made to wait. Tap anywhere to skip.</Text>
      </DeliveryTarget>

      <DeliveryTarget index={4} style={[styles.button, { backgroundColor: accent }]}>
        <Text style={styles.buttonText} onPress={() => router.back()}>
          Back to list
        </Text>
      </DeliveryTarget>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  screen: { backgroundColor: '#F4F4F8' },
  content: { padding: 16, paddingTop: 64, gap: 14 },
  header: { height: 200, borderRadius: 28, padding: 20, justifyContent: 'flex-end' },
  headerTitle: { color: 'white', fontSize: 32, fontWeight: '700' },
  headerSub: { color: 'rgba(255,255,255,0.85)' },
  stats: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    borderRadius: 24,
    backgroundColor: 'white',
    paddingVertical: 18,
  },
  stat: { fontSize: 16, fontWeight: '700' },
  card: { borderRadius: 24, backgroundColor: 'white', padding: 18 },
  body: { fontSize: 15, lineHeight: 22, color: '#55555C' },
  button: { borderRadius: 24, padding: 18, alignItems: 'center' },
  buttonText: { color: 'white', fontSize: 16, fontWeight: '700' },
});
