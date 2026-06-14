import React, {useEffect, useState} from 'react';
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  StyleSheet,
  TextInput,
  ActivityIndicator,
} from 'react-native';
import {rootBridge, AppInfo} from '../native/RootBridge';
import AsyncStorage from '@react-native-async-storage/async-storage';

export default function AppsScreen() {
  const [apps, setApps] = useState<AppInfo[]>([]);
  const [filtered, setFiltered] = useState<AppInfo[]>([]);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(false);
  const [selected, setSelected] = useState<string>('');

  useEffect(() => {
    loadSelected();
    loadApps();
  }, []);

  useEffect(() => {
    const q = search.toLowerCase();
    setFiltered(apps.filter(a =>
      a.appName.toLowerCase().includes(q) || a.packageName.toLowerCase().includes(q)
    ));
  }, [search, apps]);

  const loadSelected = async () => {
    const pkg = await AsyncStorage.getItem('selectedApp');
    if (pkg) setSelected(pkg);
  };

  const loadApps = async () => {
    setLoading(true);
    try {
      const list = await rootBridge.getInstalledApps();
      setApps(list);
      setFiltered(list);
    } catch (e) {
      console.error(e);
    }
    setLoading(false);
  };

  const selectApp = async (pkg: string) => {
    setSelected(pkg);
    await AsyncStorage.setItem('selectedApp', pkg);
  };

  return (
    <View style={s.container}>
      <TextInput
        style={s.search}
        placeholder="Search apps..."
        placeholderTextColor="#444"
        value={search}
        onChangeText={setSearch}
      />
      {loading ? (
        <ActivityIndicator color="#00ff88" style={{marginTop: 40}} />
      ) : (
        <FlatList
          data={filtered}
          keyExtractor={item => item.packageName}
          renderItem={({item}) => (
            <TouchableOpacity
              style={[s.item, selected === item.packageName && s.itemSelected]}
              onPress={() => selectApp(item.packageName)}>
              <Text style={s.appName}>{item.appName}</Text>
              <Text style={s.pkg}>{item.packageName}</Text>
              {selected === item.packageName && (
                <Text style={s.badge}>● TARGET</Text>
              )}
            </TouchableOpacity>
          )}
        />
      )}
    </View>
  );
}

const s = StyleSheet.create({
  container: {flex: 1, backgroundColor: '#0d0d0d'},
  search: {
    margin: 10,
    backgroundColor: '#111',
    borderRadius: 8,
    padding: 10,
    color: '#00ff88',
    fontFamily: 'monospace',
    borderWidth: 1,
    borderColor: '#1e1e1e',
  },
  item: {
    padding: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#1a1a1a',
    flexDirection: 'column',
  },
  itemSelected: {backgroundColor: '#0a1f0f'},
  appName: {color: '#eee', fontFamily: 'monospace', fontSize: 14},
  pkg: {color: '#555', fontFamily: 'monospace', fontSize: 11, marginTop: 2},
  badge: {color: '#00ff88', fontFamily: 'monospace', fontSize: 10, marginTop: 4},
});
