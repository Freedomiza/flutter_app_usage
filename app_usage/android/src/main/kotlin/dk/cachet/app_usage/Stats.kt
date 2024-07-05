package dk.cachet.app_usage

import android.annotation.TargetApi
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.google.gson.Gson
import java.util.*



class Stats {




    companion object {
        private val TAG = Stats::class.java.simpleName

        /** Check if permission for usage statistics is required,
         * by fetching usage statistics since the beginning of time
         */
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @SuppressWarnings("ResourceType")
        fun checkIfStatsAreAvailable(context: Context): Boolean {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = Calendar.getInstance().timeInMillis

            // Check if any usage stats are available from the beginning of time until now
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, 0, now)

            // Return whether or not stats are available
            return stats.isNotEmpty()
        }

        /** Produces a map for each installed app package name,
         *  with the corresponding time in foreground in seconds for that application.
         */
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @SuppressWarnings("ResourceType")
        fun getUsageMap(context: Context, start: Long, end: Long): HashMap<String, List<Double>> {
            val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val usageStatsMap = manager.queryAndAggregateUsageStats(start, end)
            val usageMap = HashMap<String, List<Double>>()

            for (packageName in usageStatsMap.keys) {
                val us = usageStatsMap[packageName]
                try {
                    val timeMs = us?.totalTimeInForeground ?: 0
                    val timeSeconds = (timeMs / 1000).toDouble()
                    val timeMsFirst = us?.firstTimeStamp ?: 0
                    val timeSecondsStart = (timeMsFirst / 1000).toDouble()
                    val timeMsStop = us?.lastTimeStamp ?: 0
                    val timeSecondsStop = (timeMsStop / 1000).toDouble()

                    var timeSecondsLastUse = 0.0
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val timeMsLastUse = us?.lastTimeForegroundServiceUsed ?: 0
                        timeSecondsLastUse = (timeMsLastUse / 1000).toDouble()
                    }

                    val listT = listOf(timeSeconds, timeSecondsStart, timeSecondsStop, timeSecondsLastUse)
                    usageMap[packageName] = listT
                } catch (e: Exception) {
                    Log.d(TAG, "Getting timeInForeground resulted in an exception")
                }
            }
            return usageMap
        }


        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @SuppressWarnings("ResourceType")
        fun getQueryUsageDaily(context: Context, start: Long, end: Long): HashMap<String,  List<Double>> {
            val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val usageStats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            
            val usageMap = HashMap<String, List<Double>>()

            for (us in usageStats) {


                try {
                    Log.d(TAG, "Pkg: " + us.getPackageName() +  "\t" + "ForegroundTime: "
                            + us.getTotalTimeInForeground() + "\t" + "LastTimeUsed: "
                            + us.getLastTimeUsed());

                    val packageName = us.getPackageName()
                    val timeMs = us?.totalTimeInForeground ?: 0
                    val timeSeconds = (timeMs / 1000).toDouble()
                    val timeMsFirst = us?.firstTimeStamp ?: 0
                    val timeSecondsStart = (timeMsFirst / 1000).toDouble()
                    val timeMsStop = us?.lastTimeStamp ?: 0
                    val timeSecondsStop = (timeMsStop / 1000).toDouble()

                    var timeSecondsLastUse = 0.0
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val timeMsLastUse = us?.lastTimeForegroundServiceUsed ?: 0
                        timeSecondsLastUse = (timeMsLastUse / 1000).toDouble()
                    }

                    val listT = listOf(timeSeconds, timeSecondsStart, timeSecondsStop, timeSecondsLastUse)
                    usageMap[packageName] = listT
                } catch (e: Exception) {
                    Log.d(TAG, "Getting timeInForeground resulted in an exception")
                }

            }
            
            return usageMap
        }


        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @SuppressWarnings("ResourceType")
        fun getUsageMapFromEvents(context: Context, start: Long, end: Long): HashMap<String,  List<Long>> {
            val stateMap = HashMap<String, AppStateModel>()
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val eventList = usageStatsManager.queryEvents(start, end)

            while (eventList.hasNextEvent()) {
                val event = UsageEvents.Event()
                eventList.getNextEvent(event)

                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    val packageCheck = stateMap[event.packageName]
                    if (packageCheck != null) {
                        if (stateMap[event.packageName]?.classMap?.containsKey(event.className) == true) {
                            stateMap[event.packageName]?.className = event.className
                            stateMap[event.packageName]?.startTime = event.timeStamp
                            stateMap[event.packageName]?.classMap?.get(event.className)?.startTime = event.timeStamp
                            stateMap[event.packageName]?.classMap?.get(event.className)?.isResume = true
                        } else {
                            stateMap[event.packageName]?.className = event.className
                            stateMap[event.packageName]?.startTime = event.timeStamp
                            stateMap[event.packageName]?.classMap?.set(event.className, BoolObj(event.timeStamp, true))
                        }
                    } else {
                        val appStates = AppStateModel(event.packageName, event.className, event.timeStamp)
                        appStates.classMap[event.className] = BoolObj(event.timeStamp, true)
                        stateMap[event.packageName] = appStates
                    }
                } else if (event.eventType == UsageEvents.Event.ACTIVITY_STOPPED) {
                    val packageCheck = stateMap[event.packageName]
                    if (packageCheck != null) {
                        if (stateMap[event.packageName]?.classMap?.containsKey(event.className) == true) {

                            stateMap[event.packageName]?.totalTime = (stateMap[event.packageName]?.totalTime)!! + ((event.timeStamp - stateMap[event.packageName]?.classMap?.get(
                                event.className
                            )?.startTime!!)
                                ?: 0)
                            stateMap[event.packageName]?.endTime = event.timeStamp
                            stateMap[event.packageName]?.classMap?.get(event.className)?.isResume = false
                        }
                    }
                } else if (event.eventType == UsageEvents.Event.FOREGROUND_SERVICE_STOP) {
                    val packageCheck = stateMap[event.packageName]
                    if (packageCheck != null) {
                        if (stateMap[event.packageName]?.classMap?.containsKey(event.className) == true) {
                            stateMap[event.packageName]?.lastTimeForeground = event.timeStamp
                        }
                    }
                }
            }

            var result = HashMap<String, List<Long>>()

            for (key in stateMap.keys) {
                val appState = stateMap[key]
                if (appState != null) {
                    result[key] = appState.toMicorsecondList()
                }
            }


            return result
        }
    }
}

class AppStateModel(
    var packageName: String = "",
    var className: String = "",
    var startTime: Long = 0,
    var endTime: Long = 0,
    var totalTime: Long = 0,
    var lastTimeForeground: Long = 0,
    var classMap: MutableMap<String, BoolObj> = mutableMapOf()
) {
    fun serialize(): String {
        val gson = Gson()
        return gson.toJson(this)
    }

    fun toList(): List<Long> {
        return listOf(totalTime, startTime, endTime, lastTimeForeground)
    }

    fun toMicorsecondList(): List<Long> {
        return listOf(totalTime / 1000, startTime / 1000, endTime / 1000, lastTimeForeground / 1000)
    }
}

class BoolObj(var startTime: Long, var isResume: Boolean)
