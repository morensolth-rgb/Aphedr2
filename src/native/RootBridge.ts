import {NativeModules} from 'react-native';

const {RootBridge} = NativeModules;

export interface AppInfo {
  packageName: string;
  appName: string;
}

export const rootBridge = {
  checkRoot: (): Promise<boolean> => RootBridge.checkRoot(),
  startFridaServer: (): Promise<string> => RootBridge.startFridaServer(),
  stopFridaServer: (): Promise<void> => RootBridge.stopFridaServer(),
  isFridaRunning: (): Promise<boolean> => RootBridge.isFridaRunning(),
  getInstalledApps: (): Promise<AppInfo[]> => RootBridge.getInstalledApps(),
  runScript: (packageName: string, script: string): Promise<string> =>
    RootBridge.runScript(packageName, script),
  injectGadget: (packageName: string, script: string): Promise<string> =>
    RootBridge.injectGadget(packageName, script),
  execShell: (cmd: string): Promise<string> => RootBridge.execShell(cmd),
};
