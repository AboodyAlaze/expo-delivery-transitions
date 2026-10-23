import { Link } from 'expo-router';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import {
  TransitionLink,
  TransitionPressable,
  type TransitionName,
} from 'expo-delivery-transitions';

type Demo = { id: string; title: string; subtitle: string; color: string; name: TransitionName };

const DEMOS: Demo[] = [
  {
    id: 'delivery-bike',
    title: 'Delivery · Bike',
    subtitle: 'A courier delivers the page as parcels',
    color: '#F7BE01',
    name: 'deliveryBike',
  },
  {
    id: 'delivery-car',
    title: 'Delivery · Car',
    subtitle: 'Brakes, opens the trunk, the parcel flies out',
    color: '#0D5379',
    name: 'deliveryCar',
  },
  {
    id: 'bag-bike',
    title: 'Delivery · Bike (one bag)',
    subtitle: 'One bag to the middle of the screen, it grows into the page',
    color: '#F7BE01',
    name: 'deliveryBikeBag',
  },
  {
    id: 'bag-car',
    title: 'Delivery · Car (one bag)',
    subtitle: 'The car stops, throws one bag that opens into the page',
    color: '#0D5379',
    name: 'deliveryCarBag',
  },
  {
    id: 'card-expand',
    title: 'App Store card',
    subtitle: 'The tapped card grows into the page',
    color: '#E84393',
    name: 'cardExpand',
  },
  {
    id: 'liquid-pour',
    title: 'Liquid glass pour',
    subtitle: 'The page pours down like thick glass',
    color: '#00A884',
    name: 'liquidPour',
  },
  {
    id: 'ball-zoom',
    title: 'Ball zoom',
    subtitle: 'A rubber ball inflates under your finger',
    color: '#6C5CE7',
    name: 'ballZoom',
  },
  {
    id: 'radar-sweep',
    title: 'Radar sweep',
    subtitle: 'A radar line reveals the page',
    color: '#16A085',
    name: 'radarSweep',
  },
  {
    id: 'hex-aperture',
    title: 'Hex aperture',
    subtitle: 'A hexagonal iris spins open',
    color: '#E17055',
    name: 'hexAperture',
  },
  {
    id: 'curtain-drop',
    title: 'Curtain drop',
    subtitle: 'The page falls in as bouncing strips',
    color: '#D63384',
    name: 'curtainDrop',
  },
];

export default function Home() {
  return (
    <ScrollView contentContainerStyle={styles.content}>
      <Text style={styles.title}>Transitions</Text>
      <Text style={styles.hint}>Tap a card. Tap the screen while it plays to skip it.</Text>

      {DEMOS.map((demo, index) => {
        const href = {
          pathname: '/item/[id]',
          params: { id: demo.id, title: demo.title, color: demo.color },
        } as const;
        const card = (
          <>
            <View style={[styles.badge, { backgroundColor: demo.color }]} />
            <View style={styles.cardText}>
              <Text style={styles.cardTitle}>{demo.title}</Text>
              <Text style={styles.cardSubtitle}>{demo.subtitle}</Text>
            </View>
          </>
        );
        // The first card is a <TransitionLink> (a drop-in <Link>), the others are <TransitionPressable>s
        return index === 0 ? (
          <TransitionLink
            key={demo.id}
            href={href}
            asChild
            transition={{ name: demo.name, accent: demo.color }}>
            <Pressable style={styles.card}>{card}</Pressable>
          </TransitionLink>
        ) : (
          <TransitionPressable
            key={demo.id}
            href={href}
            transition={{ name: demo.name, accent: demo.color }}
            style={styles.card}>
            {card}
          </TransitionPressable>
        );
      })}

      <Text style={[styles.title, styles.section]}>Normal navigation</Text>
      <Text style={styles.hint}>
        A plain {'<Link>'}: Expo Router&apos;s default native animation, untouched by this package.
      </Text>
      <Link href="/plain" asChild>
        <Pressable style={styles.card}>
          <View style={[styles.badge, { backgroundColor: '#9CA3AF' }]} />
          <View style={styles.cardText}>
            <Text style={styles.cardTitle}>Default Expo Router</Text>
            <Text style={styles.cardSubtitle}>Native slide, swipe-back, back button</Text>
          </View>
        </Pressable>
      </Link>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  content: { padding: 16, paddingTop: 64, paddingBottom: 48, gap: 12 },
  title: { fontSize: 30, fontWeight: '700' },
  section: { marginTop: 24 },
  hint: { color: '#6b7280', marginBottom: 4 },
  card: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
    padding: 14,
    borderRadius: 22,
    backgroundColor: 'white',
  },
  badge: { width: 52, height: 52, borderRadius: 16 },
  cardText: { flex: 1 },
  cardTitle: { fontSize: 17, fontWeight: '600' },
  cardSubtitle: { fontSize: 13, color: '#6b7280' },
});
