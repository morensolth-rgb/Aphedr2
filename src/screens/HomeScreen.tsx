import React, {useEffect, useState} from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  Alert,
} from 'react-native';
import {rootBridge} from '../native/RootBridge';

export default function HomeScreen() {
  const [rootStatus, setRootStatus] = useState<'checking' | 'granted' | 'denied'>('checking');
  const [fridaStatus, setFridaStatus] = useState<'stopped' | 'starting' | 'running' | 'error'>('stopped');
  const [log, setLog] = useState<string[]>([]);

  const addLog = (msg: string) =>
    setLog(prev => [`[${new Date().toLocaleTimeString()}] ${msg}`, ...prev.slice(0, 50)]);

  useEffect(() => {
    checkRoot();
  }, []);

  const checkRoot = async () => {
    try {
      const hasRoot = await rootBridge.checkRoot();
      setRootStatus(hasRoot ? 'granted' : 'denied');
      addLog(hasRoot ? '✓ Root access granted' : '✗ Root access denied');
      if (hasRoot) checkFridaStatus();
    } catch (e) {
      setRootStatus('denied');
      addLog('✗ Root check failed: ' + e);
    }
  };

  const checkFridaStatus = async () => {
    try {
      const running = await rootBridge.isFridaRunning();
      setFridaStatus(running ? 'running' : 'stopped');
      addLog(running ? '✓ frida-server already running' : '○ frida-server stopped');
    } catch (e) {
      addLog('frida check error: ' + e);
    }
  };

  const toggleFrida = async () => {
    if (fridaStatus === 'running') {
      try {
        await rootBridge.stopFridaServer();
        setFridaStatus('stopped');
        addLog('○ frida-server stopped');
      } catch (e) {
        addLog('Stop error: ' + e);
      }
    } else {
      setFridaStatus('starting');
      addLog('▶ Starting frida-server...');
      try {
        const result = await rootBridge.startFridaServer();
        setFridaStatus('running');
        addLog('✓ ' + result);
      } catch (e) {
        setFridaStatus('error');
        addLog('✗ Start failed: ' + e);
      }
    }
  };

  const statusColor = {
    checking: '#888',
    granted: '#00ff88',
    denied: '#ff4444',
  }[rootStatus];

  const fridaColor = {
    stopped: '#888',
    starting: '#ffaa00',
    running: '#00ff88',
    error: '#ff4444',
  }[fridaStatus];

  return (
    <View style={s.container}>
      <View style={s.card}>
        <Text style={s.label}>ROOT STATUS</Text>
        <Text style={[s.status, {color: statusColor}]}>
          {rootStatus.toUpperCase()}
        </Text>
      </View>

      <View style={s.card}>
        <Text style={s.label}>FRIDA-SERVER</Text>
        <Text style={[s.status, {color: fridaColor}]}>
          {fridaStatus.toUpperCase()}
        </Text>
        <TouchableOpacity
          style={[s.btn, fridaStatus === 'running' ? s.btnRed : s.btnGreen]}
          onPress={toggleFrida}
          disabled={fridaStatus === 'starting' || rootStatus !== 'granted'}>
          <Text style={s.btnText}>
            {fridaStatus === 'running' ? 'STOP' : fridaStatus === 'starting' ? 'STARTING...' : 'START'}
          </Text>
        </TouchableOpacity>
      </View>

      <View style={[s.card, {flex: 1}]}>
        <Text style={s.label}>LOG</Text>
        <ScrollView>
          {log.map((l, i) => (
            <Text key={i} style={s.logLine}>{l}</Text>
          ))}
        </ScrollView>
      </View>
    </View>
  );
}

const s = StyleSheet.create({
  container: {flex: 1, backgroundColor: '#0d0d0d', padding: 12, gap: 10},
  card: {backgroundColor: '#111', borderRadius: 8, padding: 14, borderWidth: 1, borderColor: '#1e1e1e'},
  label: {color: '#444', fontSize: 11, fontFamily: 'monospace', marginBottom: 4},
  status: {fontSize: 22, fontFamily: 'monospace', fontWeight: 'bold'},
  btn: {marginTop: 10, padding: 10, borderRadius: 6, alignItems: 'center'},
  btnGreen: {backgroundColor: '#003d22'},
  btnRed: {backgroundColor: '#3d0000'},
  btnText: {color: '#00ff88', fontFamily: 'monospace', fontWeight: 'bold'},
  logLine: {color: '#00cc66', fontFamily: 'monospace', fontSize: 11, marginBottom: 2},
});
