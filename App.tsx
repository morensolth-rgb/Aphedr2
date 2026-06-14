import React, {useEffect, useState} from 'react';
import {NavigationContainer} from '@react-navigation/native';
import {createBottomTabNavigator} from '@react-navigation/bottom-tabs';
import {SafeAreaProvider} from 'react-native-safe-area-context';
import {View, Text, StyleSheet} from 'react-native';

import HomeScreen from './src/screens/HomeScreen';
import AppsScreen from './src/screens/AppsScreen';
import ScriptScreen from './src/screens/ScriptScreen';
import ConsoleScreen from './src/screens/ConsoleScreen';

const Tab = createBottomTabNavigator();

const TabIcon = ({name, color}: {name: string; color: string}) => (
  <Text style={{color, fontSize: 20}}>{name}</Text>
);

export default function App() {
  return (
    <SafeAreaProvider>
      <NavigationContainer>
        <Tab.Navigator
          screenOptions={{
            tabBarStyle: {backgroundColor: '#0d0d0d', borderTopColor: '#1a1a1a'},
            tabBarActiveTintColor: '#00ff88',
            tabBarInactiveTintColor: '#555',
            headerStyle: {backgroundColor: '#0d0d0d'},
            headerTintColor: '#00ff88',
            headerTitleStyle: {fontFamily: 'monospace', fontWeight: 'bold'},
          }}>
          <Tab.Screen
            name="Home"
            component={HomeScreen}
            options={{
              title: 'FridaCtl',
              tabBarIcon: ({color}) => <TabIcon name="⚡" color={color} />,
            }}
          />
          <Tab.Screen
            name="Apps"
            component={AppsScreen}
            options={{
              title: 'Apps',
              tabBarIcon: ({color}) => <TabIcon name="📱" color={color} />,
            }}
          />
          <Tab.Screen
            name="Script"
            component={ScriptScreen}
            options={{
              title: 'Script',
              tabBarIcon: ({color}) => <TabIcon name="📝" color={color} />,
            }}
          />
          <Tab.Screen
            name="Console"
            component={ConsoleScreen}
            options={{
              title: 'Console',
              tabBarIcon: ({color}) => <TabIcon name="🖥" color={color} />,
            }}
          />
        </Tab.Navigator>
      </NavigationContainer>
    </SafeAreaProvider>
  );
}
