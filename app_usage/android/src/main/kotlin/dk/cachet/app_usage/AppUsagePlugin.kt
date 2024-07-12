package dk.cachet.app_usage

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result


/** AppUsagePlugin */
public class AppUsagePlugin : FlutterPlugin, MethodCallHandler, ActivityAware {
    /// The MethodChannel that will the communication between Flutter and native Android
    ///
    /// This local reference serves to register the plugin with the Flutter Engine and unregister it
    /// when the Flutter Engine is detached from the Activity
    private lateinit var channel: MethodChannel
    private lateinit var context: Context
    private val methodChannelName = "app_usage.methodChannel"
    private lateinit var activity: Activity

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, methodChannelName)
        channel.setMethodCallHandler(this);
        context = flutterPluginBinding.applicationContext;
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        when (call.method) {
            "getUsage" -> getUsage(call, result)
            "checkPermission" -> checkPermissions(call, result)
            "requestPermission" -> requestPermissions(call, result)
            "getUsageFromEvents" -> getUsageFromEvents(call, result)
            "getUsageDaily" -> getUsageDaily(call, result)
            "getRawUsageFromEvents" -> getRawUsageFromEvents(call, result)
            else -> result.notImplemented()
        }
    }



    fun getUsage(@NonNull call: MethodCall, @NonNull result: Result) {
        // Firstly, permission must be given by the user must be set correctly by the user
        handlePermissions()

        // Parse parameters, i.e. start- and end-date
        val start: Long? = call.argument("start")
        val end: Long? = call.argument("end")

        /// Query the Usage API
        val usage = Stats.getUsageMap(context, start!!, end!!)

        /// Return the result
        result.success(usage)
    }


    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    private fun checkPermissions(@NonNull call: MethodCall, @NonNull result: Result) {
        return result.success(Stats.checkIfStatsAreAvailable(context));
    }

    private fun requestPermissions(@NonNull call: MethodCall, @NonNull result: Result) {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            this.activity.startActivity(intent)
            result.success(true)
        } catch (e: Exception) {
            result.success(false)
        }
    }
    

    private fun getUsageFromEvents(@NonNull call: MethodCall, @NonNull result: Result) {
        // Firstly, permission must be given by the user must be set correctly by the user
        handlePermissions()

        // Parse parameters, i.e. start- and end-date
        val start: Long? = call.argument("start")
        val end: Long? = call.argument("end")

        /// Query the Usage API
        val usageHash = Stats.getUsageMapFromEvents(context, start!!, end!!)
        val usageMap: ArrayList<HashMap<String, Any>> = ArrayList()
        usageHash.forEach {
            usageMap.add(it.toHashMap())
        }

        /// Return the result
        result.success(usageMap)
    }

    private fun getRawUsageFromEvents(@NonNull call: MethodCall, @NonNull result: Result) {
        // Firstly, permission must be given by the user must be set correctly by the user
        handlePermissions()

        // Parse parameters, i.e. start- and end-date
        val start: Long? = call.argument("start")
        val end: Long? = call.argument("end")

        /// Query the Usage API
        val usageHash = Stats.getRawUsageMapFromEvents(context, start!!, end!!)
        val usageMap: ArrayList<HashMap<String, Any>> = ArrayList()
        usageHash.forEach {
            usageMap.add(it.toHashMap())
        }

        /// Return the result
        result.success(usageMap)
    }

    private fun getUsageDaily(@NonNull call: MethodCall, @NonNull result: Result) {
        handlePermissions()


        // Parse parameters, i.e. start- and end-date
        val start: Long? = call.argument("start")
        val end: Long? = call.argument("end")

        /// Query the Usage API
        val usage = Stats.getQueryUsageDaily(context, start!!, end!!)

        /// Return the result
        result.success(usage)
    }



    private fun handlePermissions() {
        /// If stats are not available, show the permission screen to give access to them
        if (!Stats.checkIfStatsAreAvailable(context)) {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            this.activity.startActivity(intent)
        }
    }

    override fun onDetachedFromActivity() {
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        this.activity = binding.activity
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        this.activity = binding.activity
    }

    override fun onDetachedFromActivityForConfigChanges() {
    }
}
