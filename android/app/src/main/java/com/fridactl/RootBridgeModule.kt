package com.fridactl

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.facebook.react.bridge.*
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class RootBridgeModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    override fun getName() = "RootBridge"

    companion object {
        private const val TAG = "FridaCtl"
        private const val FRIDA_PORT = 27042
        private const val FRIDA_DEST = "/data/local/tmp/frida-server"
        private const val GADGET_DEST = "/data/local/tmp/frida-gadget.so"
        private const val APKTOOL_DEST = "/data/local/tmp/apktool.jar"

        init {
            Shell.enableVerboseLogging = false
            Shell.setDefaultBuilder(
                Shell.Builder.create()
                    .setFlags(Shell.FLAG_REDIRECT_STDERR)
                    .setTimeout(30)
            )
        }
    }

    // ─── Root check ──────────────────────────────────────────────────────────

    @ReactMethod
    fun checkRoot(promise: Promise) {
        try {
            val result = Shell.cmd("id").exec()
            val out = result.out.joinToString("\n")
            promise.resolve(out.contains("uid=0"))
        } catch (e: Exception) {
            promise.resolve(false)
        }
    }

    // ─── Shell exec ──────────────────────────────────────────────────────────

    @ReactMethod
    fun execShell(cmd: String, promise: Promise) {
        try {
            val result = Shell.cmd(cmd).exec()
            promise.resolve(result.out.joinToString("\n"))
        } catch (e: Exception) {
            promise.reject("SHELL_ERROR", e.message)
        }
    }

    // ─── frida-server ────────────────────────────────────────────────────────

    @ReactMethod
    fun startFridaServer(promise: Promise) {
        Thread {
            try {
                extractBinary("frida-server-arm64", FRIDA_DEST)

                // Kill any existing instance
                Shell.cmd("pkill -f frida-server 2>/dev/null || true").exec()
                Thread.sleep(500)

                // Start frida-server
                Shell.cmd("chmod 755 $FRIDA_DEST").exec()
                Shell.cmd("$FRIDA_DEST &").exec()
                Thread.sleep(1500)

                // Verify it started
                val running = isFridaServerRunning()
                if (running) {
                    promise.resolve("frida-server started on port $FRIDA_PORT")
                } else {
                    promise.reject("START_FAILED", "frida-server started but not responding on port $FRIDA_PORT")
                }
            } catch (e: Exception) {
                promise.reject("START_ERROR", e.message)
            }
        }.start()
    }

    @ReactMethod
    fun stopFridaServer(promise: Promise) {
        try {
            Shell.cmd("pkill -f frida-server").exec()
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("STOP_ERROR", e.message)
        }
    }

    @ReactMethod
    fun isFridaRunning(promise: Promise) {
        Thread {
            promise.resolve(isFridaServerRunning())
        }.start()
    }

    private fun isFridaServerRunning(): Boolean {
        return try {
            val result = Shell.cmd("cat /proc/net/tcp6 | grep -i $(printf '%04X' $FRIDA_PORT)").exec()
            result.out.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    // ─── App list ─────────────────────────────────────────────────────────────

    @ReactMethod
    fun getInstalledApps(promise: Promise) {
        Thread {
            try {
                val pm = reactApplicationContext.packageManager
                val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                val arr = WritableNativeArray()
                for (app in apps) {
                    try {
                        val map = WritableNativeMap()
                        map.putString("packageName", app.packageName)
                        map.putString("appName", pm.getApplicationLabel(app).toString())
                        arr.pushMap(map)
                    } catch (e: Exception) {}
                }
                promise.resolve(arr)
            } catch (e: Exception) {
                promise.reject("APPS_ERROR", e.message)
            }
        }.start()
    }

    // ─── Run script via frida-server HTTP API ────────────────────────────────

    @ReactMethod
    fun runScript(packageName: String, script: String, promise: Promise) {
        Thread {
            try {
                if (!isFridaServerRunning()) {
                    // Try to start it first
                    extractBinary("frida-server-arm64", FRIDA_DEST)
                    Shell.cmd("chmod 755 $FRIDA_DEST && $FRIDA_DEST &").exec()
                    Thread.sleep(2000)
                }

                // Write script to temp file
                val scriptFile = File(reactApplicationContext.cacheDir, "hook.js")
                scriptFile.writeText(script)

                // Use frida-inject approach via shell (frida-server must be running)
                // We write a Python-like approach using frida CLI bundled or HTTP API
                val result = injectViaFridaServer(packageName, scriptFile.absolutePath)
                promise.resolve(result)
            } catch (e: Exception) {
                promise.reject("RUN_ERROR", e.message)
            }
        }.start()
    }

    private fun injectViaFridaServer(packageName: String, scriptPath: String): String {
        // Spawn or attach via frida-server HTTP API port 27042
        // POST /spawn → get pid, POST /attach/{pid}/scripts → inject
        try {
            // Step 1: spawn or find pid
            val spawnUrl = URL("http://127.0.0.1:$FRIDA_PORT/spawn")
            val spawnConn = spawnUrl.openConnection() as HttpURLConnection
            spawnConn.requestMethod = "POST"
            spawnConn.setRequestProperty("Content-Type", "application/json")
            spawnConn.doOutput = true
            spawnConn.connectTimeout = 3000
            val spawnBody = """{"identifier":"$packageName"}"""
            spawnConn.outputStream.write(spawnBody.toByteArray())
            val spawnResp = spawnConn.inputStream.bufferedReader().readText()
            spawnConn.disconnect()

            // Extract pid from response like {"pid":1234}
            val pid = Regex("\"pid\"\\s*:\\s*(\\d+)").find(spawnResp)?.groupValues?.get(1)
                ?: throw Exception("Could not get PID from frida-server: $spawnResp")

            // Step 2: create session
            val sessionUrl = URL("http://127.0.0.1:$FRIDA_PORT/session/$pid")
            val sessionConn = sessionUrl.openConnection() as HttpURLConnection
            sessionConn.requestMethod = "POST"
            sessionConn.connectTimeout = 3000
            sessionConn.getResponseCode()
            sessionConn.disconnect()

            // Step 3: inject script
            val scriptContent = File(scriptPath).readText()
            val scriptUrl = URL("http://127.0.0.1:$FRIDA_PORT/session/$pid/scripts")
            val scriptConn = scriptUrl.openConnection() as HttpURLConnection
            scriptConn.requestMethod = "POST"
            scriptConn.setRequestProperty("Content-Type", "application/json")
            scriptConn.doOutput = true
            scriptConn.connectTimeout = 5000
            val scriptBody = """{"name":"hook","source":${escapeJson(scriptContent)}}"""
            scriptConn.outputStream.write(scriptBody.toByteArray())
            val scriptResp = scriptConn.inputStream.bufferedReader().readText()
            scriptConn.disconnect()

            // Step 4: resume
            val resumeUrl = URL("http://127.0.0.1:$FRIDA_PORT/session/$pid/resume")
            val resumeConn = resumeUrl.openConnection() as HttpURLConnection
            resumeConn.requestMethod = "POST"
            resumeConn.connectTimeout = 3000
            resumeConn.getResponseCode()
            resumeConn.disconnect()

            return "Script injected, PID=$pid"
        } catch (e: Exception) {
            throw Exception("frida-server HTTP API failed: ${e.message}")
        }
    }

    private fun escapeJson(s: String): String {
        return "\"" + s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t") + "\""
    }

    // ─── Gadget injection fallback ───────────────────────────────────────────

    @ReactMethod
    fun injectGadget(packageName: String, script: String, promise: Promise) {
        Thread {
            try {
                val result = performGadgetInjection(packageName, script)
                promise.resolve(result)
            } catch (e: Exception) {
                promise.reject("GADGET_ERROR", e.message)
            }
        }.start()
    }

    private fun performGadgetInjection(packageName: String, script: String): String {
        val sb = StringBuilder()

        // 1. Extract gadget .so
        sb.appendLine("▶ Extracting frida-gadget...")
        extractBinary("frida-gadget-arm64.so", GADGET_DEST)
        Shell.cmd("chmod 755 $GADGET_DEST").exec()

        // 2. Extract apktool
        sb.appendLine("▶ Extracting apktool...")
        extractBinary("apktool.jar", APKTOOL_DEST)

        // 3. Find APK path
        val pm = reactApplicationContext.packageManager
        val appInfo = pm.getApplicationInfo(packageName, 0)
        val apkPath = appInfo.sourceDir
        sb.appendLine("▶ APK: $apkPath")

        val workDir = "/data/local/tmp/gadget_work"
        val decompDir = "$workDir/decompiled"
        val outputApk = "$workDir/patched.apk"
        val signedApk = "$workDir/signed.apk"

        // 4. Clean work dir
        Shell.cmd("rm -rf $workDir && mkdir -p $workDir").exec()

        // 5. Copy APK
        Shell.cmd("cp '$apkPath' $workDir/original.apk").exec()

        // 6. Decompile with apktool
        sb.appendLine("▶ Decompiling APK (this may take a minute)...")
        val decompResult = Shell.cmd(
            "java -jar $APKTOOL_DEST d $workDir/original.apk -o $decompDir -f 2>&1"
        ).exec()
        if (!File("/data/local/tmp/gadget_work/decompiled").exists()) {
            throw Exception("apktool decompile failed: ${decompResult.out.takeLast(200)}")
        }

        // 7. Copy gadget into lib folder
        sb.appendLine("▶ Injecting gadget library...")
        Shell.cmd("mkdir -p $decompDir/lib/arm64-v8a").exec()
        Shell.cmd("cp $GADGET_DEST $decompDir/lib/arm64-v8a/libfrida-gadget.so").exec()

        // 8. Write gadget config (auto-loads script)
        val scriptFile = "/data/local/tmp/gadget_hook.js"
        File(scriptFile).writeText(script)
        val gadgetConfig = """
{
  "interaction": {
    "type": "script",
    "path": "$scriptFile"
  }
}
""".trimIndent()
        File("/data/local/tmp/gadget_work/decompiled/lib/arm64-v8a/libfrida-gadget.config.so")
            .writeText(gadgetConfig)

        // 9. Find main activity smali and inject System.loadLibrary
        sb.appendLine("▶ Patching smali to load gadget...")
        injectSmaliLoadLibrary(decompDir)

        // 10. Recompile
        sb.appendLine("▶ Recompiling APK...")
        val recompResult = Shell.cmd(
            "java -jar $APKTOOL_DEST b $decompDir -o $outputApk 2>&1"
        ).exec()
        if (!File(outputApk).exists()) {
            throw Exception("apktool recompile failed: ${recompResult.out.takeLast(200)}")
        }

        // 11. Sign with debug key
        sb.appendLine("▶ Signing APK...")
        val keystore = File(reactApplicationContext.filesDir, "debug.keystore")
        if (!keystore.exists()) createDebugKeystore(keystore.absolutePath)

        Shell.cmd(
            "java -jar /data/local/tmp/apktool.jar 2>&1 || true"
        ).exec()

        // Use apksigner from build-tools if available, otherwise use jarsigner
        val signResult = Shell.cmd(
            "jarsigner -verbose -keystore ${keystore.absolutePath} " +
            "-storepass android -keypass android " +
            "-signedjar $signedApk $outputApk androiddebugkey 2>&1"
        ).exec()

        if (!File(signedApk).exists()) {
            // Try copying unsigned as workaround
            Shell.cmd("cp $outputApk $signedApk").exec()
        }

        // 12. Uninstall original and install patched
        sb.appendLine("▶ Reinstalling patched APK...")
        Shell.cmd("pm uninstall $packageName").exec()
        val installResult = Shell.cmd("pm install -r $signedApk 2>&1").exec()
        val installOut = installResult.out.joinToString("\n")

        if (installOut.contains("Success", ignoreCase = true)) {
            sb.appendLine("✓ Patched APK installed successfully!")
            sb.appendLine("✓ Launch $packageName — gadget will auto-load script")
        } else {
            throw Exception("Install failed: $installOut")
        }

        return sb.toString()
    }

    private fun injectSmaliLoadLibrary(decompDir: String) {
        // Find MainActivity.smali or the main launcher activity
        val result = Shell.cmd(
            "grep -r 'onCreate' $decompDir/smali* --include='*.smali' -l | head -5"
        ).exec()
        val smaliFiles = result.out.filter { it.endsWith(".smali") }

        for (smaliPath in smaliFiles) {
            val file = File(smaliPath)
            val content = file.readText()
            if (content.contains("Landroid/app/Activity;") || 
                content.contains("Landroidx/appcompat/app/AppCompatActivity;")) {
                // Inject load-library call after .method public constructor
                val injection = """
    const-string v0, "frida-gadget"
    invoke-static {v0}, Ljava/lang/System;->loadLibrary(Ljava/lang/String;)V
"""
                val patched = content.replace(
                    ".method public constructor <init>()V",
                    ".method public constructor <init>()V\n$injection"
                )
                file.writeText(patched)
                break
            }
        }
    }

    private fun createDebugKeystore(path: String) {
        Shell.cmd(
            "keytool -genkey -v -keystore $path " +
            "-alias androiddebugkey -keyalg RSA -keysize 2048 " +
            "-validity 10000 -storepass android -keypass android " +
            "-dname 'CN=Android Debug,O=Android,C=US' 2>&1"
        ).exec()
    }

    // ─── Binary extraction from assets ───────────────────────────────────────

    private fun extractBinary(assetName: String, destPath: String) {
        val destFile = File(destPath)
        if (destFile.exists() && destFile.length() > 1000) return // already extracted

        val assets = reactApplicationContext.assets
        try {
            assets.open(assetName).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            throw Exception("Failed to extract $assetName from assets: ${e.message}. Make sure the file is in android/app/src/main/assets/")
        }
    }
}
