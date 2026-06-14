import React, {useState, useEffect} from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  Alert,
} from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import {rootBridge} from '../native/RootBridge';

const DEFAULT_SCRIPT = `Java.perform(function() {
  // Hook a method
  var Activity = Java.use("android.app.Activity");
  Activity.onResume.implementation = function() {
    console.log("[*] onResume called");
    this.onResume();
  };
});`;

export default function ScriptScreen() {
  const [script, setScript] = useState(DEFAULT_SCRIPT);
  const [target, setTarget] = useState('');
  const [running, setRunning] = useState(false);
  const [output, setOutput] = useState<string[]>([]);

  useEffect(() => {
    AsyncStorage.getItem('selectedApp').then(pkg => {
      if (pkg) setTarget(pkg);
    });
    AsyncStorage.getItem('savedScript').then(s => {
      if (s) setScript(s);
    });
  }, []);

  const saveScript = async () => {
    await AsyncStorage.setItem('savedScript', script);
    Alert.alert('Saved', 'Script saved locally');
  };

  const addOut = (msg: string) =>
    setOutput(prev => [`[${new Date().toLocaleTimeString()}] ${msg}`, ...prev.slice(0, 200)]);

  const runScript = async () => {
    if (!target) {
      Alert.alert('No target', 'Go to Apps tab and select a target app first');
      return;
    }
    setRunning(true);
    addOut(`▶ Running on ${target}...`);
    try {
      // Try frida-server first
      const result = await rootBridge.runScript(target, script);
      addOut('✓ Script injected via frida-server');
      addOut(result);
    } catch (e: any) {
      addOut('✗ frida-server failed: ' + e.message);
      addOut('↪ Falling back to frida-gadget injection...');
      try {
        const result = await rootBridge.injectGadget(target, script);
        addOut('✓ Gadget injected successfully');
        addOut(result);
      } catch (e2: any) {
        addOut('✗ Gadget injection failed: ' + e2.message);
      }
    }
    setRunning(false);
  };

  return (
    <View style={s.container}>
      <View style={s.targetBar}>
        <Text style={s.targetLabel}>TARGET: </Text>
        <Text style={s.targetPkg} numberOfLines={1}>
          {target || 'None — select from Apps tab'}
        </Text>
      </View>

      <TextInput
        style={s.editor}
        multiline
        value={script}
        onChangeText={setScript}
        autoCapitalize="none"
        autoCorrect={false}
        spellCheck={false}
      />

      <View style={s.btnRow}>
        <TouchableOpacity style={s.btnSave} onPress={saveScript}>
          <Text style={s.btnText}>SAVE</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[s.btnRun, running && s.btnDisabled]}
          onPress={runScript}
          disabled={running}>
          <Text style={s.btnText}>{running ? 'RUNNING...' : '▶ RUN'}</Text>
        </TouchableOpacity>
      </View>

      <View style={s.outputBox}>
        <Text style={s.outLabel}>OUTPUT</Text>
        <ScrollView>
          {output.map((l, i) => (
            <Text key={i} style={s.outLine}>{l}</Text>
          ))}
        </ScrollView>
      </View>
    </View>
  );
}

const s = StyleSheet.create({
  container: {flex: 1, backgroundColor: '#0d0d0d', padding: 10, gap: 8},
  targetBar: {
    flexDirection: 'row',
    backgroundColor: '#111',
    padding: 8,
    borderRadius: 6,
    borderWidth: 1,
    borderColor: '#1e1e1e',
  },
  targetLabel: {color: '#555', fontFamily: 'monospace', fontSize: 12},
  targetPkg: {color: '#00ff88', fontFamily: 'monospace', fontSize: 12, flex: 1},
  editor: {
    backgroundColor: '#080808',
    borderRadius: 8,
    padding: 10,
    color: '#00cc66',
    fontFamily: 'monospace',
    fontSize: 12,
    borderWidth: 1,
    borderColor: '#1e1e1e',
    minHeight: 200,
    textAlignVertical: 'top',
  },
  btnRow: {flexDirection: 'row', gap: 8},
  btnSave: {
    flex: 1,
    backgroundColor: '#111',
    padding: 12,
    borderRadius: 6,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#1e1e1e',
  },
  btnRun: {
    flex: 2,
    backgroundColor: '#003d22',
    padding: 12,
    borderRadius: 6,
    alignItems: 'center',
  },
  btnDisabled: {backgroundColor: '#1a1a1a'},
  btnText: {color: '#00ff88', fontFamily: 'monospace', fontWeight: 'bold'},
  outputBox: {
    flex: 1,
    backgroundColor: '#080808',
    borderRadius: 8,
    padding: 10,
    borderWidth: 1,
    borderColor: '#1e1e1e',
  },
  outLabel: {color: '#333', fontSize: 10, fontFamily: 'monospace', marginBottom: 6},
  outLine: {color: '#00cc44', fontFamily: 'monospace', fontSize: 11, marginBottom: 1},
});
