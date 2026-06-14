package com.fridactl

import android.content.pm.PackageManager
import com.facebook.react.bridge.*
import com.topjohnwu.superuser.Shell
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class RootBridgeModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String = "RootBridge"

    companion object {
        private const val FRIDA_PORT = 27042
        private const val FRIDA_DEST = "/data/local/tmp/frida-server"
        private const val GADGET_DEST = "/data/local/tmp/frida-gadget.so"
        private const val APKTOOL_DEST = "/data/local/tmp/apktool.jar"
    }

    init {
        Shell.enableVerboseLogging = false
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(30)
        )
    }

    // ───────────────── ROOT CHECK ─────────────────
    @ReactMethod
    fun checkRoot(promise: Promise) {
        try {
            val res = Shell.cmd("id").exec()
            promise.resolve(res.out.joinToString().contains("uid=0"))
        } catch (e: Exception) {
            promise.resolve(false)
        }
    }

    // ───────────────── SHELL EXEC ─────────────────
    @ReactMethod
    fun execShell(cmd: String, promise: Promise) {
        try {
            val res = Shell.cmd(cmd).exec()
            promise.resolve(res.out.joinToString("\n"))
        } catch (e: Exception) {
            promise.reject("SHELL_ERROR", e.message)
        }
    }

    // ───────────────── FRIDA SERVER ─────────────────
    @ReactMethod
    fun startFridaServer(promise: Promise) {
        Thread {
            try {
                Shell.cmd("pkill -f frida-server || true").exec()
                Thread.sleep(500)

                Shell.cmd("chmod 755 $FRIDA_DEST").exec()
                Shell.cmd("$FRIDA_DEST &").exec()

                Thread.sleep(1500)

                promise.resolve("frida-server started")
            } catch (e: Exception) {
                promise.reject("FRIDA_START", e.message)
            }
        }.start()
    }

    @ReactMethod
    fun stopFridaServer(promise: Promise) {
        try {
            Shell.cmd("pkill -f frida-server").exec()
            promise.resolve("stopped")
        } catch (e: Exception) {
            promise.reject("FRIDA_STOP", e.message)
        }
    }

    @ReactMethod
    fun isFridaRunning(promise: Promise) {
        Thread {
            try {
                val res = Shell.cmd("pidof frida-server").exec()
                promise.resolve(res.out.isNotEmpty())
            } catch (e: Exception) {
                promise.resolve(false)
            }
        }.start()
    }

    // ───────────────── APPS LIST ─────────────────
    @ReactMethod
    fun getInstalledApps(promise: Promise) {
        Thread {
            try {
                val pm = reactApplicationContext.packageManager
                val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

                val arr = WritableNativeArray()

                for (app in apps) {
                    val map = WritableNativeMap()
                    map.putString("packageName", app.packageName)
                    map.putString("appName", pm.getApplicationLabel(app).toString())
                    arr.pushMap(map)
                }

                promise.resolve(arr)
            } catch (e: Exception) {
                promise.reject("APPS_ERROR", e.message)
            }
        }.start()
    }

    // ───────────────── RUN SCRIPT ─────────────────
    @ReactMethod
    fun runScript(packageName: String, script: String, promise: Promise) {
        Thread {
            try {
                val file = File(reactApplicationContext.cacheDir, "hook.js")
                file.writeText(script)

                val result = injectViaFrida(packageName, file)
                promise.resolve(result)

            } catch (e: Exception) {
                promise.reject("RUN_ERROR", e.message)
            }
        }.start()
    }

    private fun injectViaFrida(pkg: String, scriptFile: File): String {
        val spawnUrl = URL("http://127.0.0.1:$FRIDA_PORT/spawn")
        val conn = spawnUrl.openConnection() as HttpURLConnection

        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")

        val body = """{"identifier":"$pkg"}"""
        conn.outputStream.write(body.toByteArray())

        val resp = conn.inputStream.bufferedReader().readText()
        conn.disconnect()

        return "Injected: $resp"
    }

    // ───────────────── GADGET ─────────────────
    @ReactMethod
    fun injectGadget(packageName: String, script: String, promise: Promise) {
        Thread {
            try {
                promise.resolve("Gadget mode not fully enabled in this build")
            } catch (e: Exception) {
                promise.reject("GADGET_ERROR", e.message)
            }
        }.start()
    }

    // ───────────────── FILE EXTRACT ─────────────────
    private fun extractBinary(assetName: String, destPath: String) {
        val file = File(destPath)

        if (file.exists() && file.length() > 1000) return

        val input = reactApplicationContext.assets.open(assetName)
        val output = FileOutputStream(file)

        input.copyTo(output)

        input.close()
        output.close()
    }
}
