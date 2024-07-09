package dk.cachet.app_usage

import android.annotation.TargetApi
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.util.Log
//import com.google.gson.Gson
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
                        packageCheck.sessions.add(
                            SessionObj(event.timeStamp, 0)
                        )
                    } else {
                        val appStates = AppStateModel(event.packageName, event.timeStamp)
                        appStates.sessions.add(SessionObj(event.timeStamp, 0 ))
                        stateMap[event.packageName] = appStates
                    }
                } else if (event.eventType == UsageEvents.Event.ACTIVITY_STOPPED) {
                    val packageCheck = stateMap[event.packageName]

                    if (packageCheck != null) {

                        val lastSession = packageCheck.sessions.last()


                        if(lastSession.endTime == 0L) {
                            // close sessions
                            lastSession.endTime = event.timeStamp
                        } else {
                            // This case should not occur when there a session without endTime
                            // but in case it happened just record it without startTime
                            packageCheck.sessions.add(SessionObj(0, event.timeStamp))
                        }
                    } else {
                        // event record of prev session filter
                        val appStates = AppStateModel(event.packageName, start, event.timeStamp)
                        appStates.sessions.add(SessionObj(start, event.timeStamp))
                        stateMap[event.packageName] = appStates
                    }
                } else if (event.eventType == UsageEvents.Event.FOREGROUND_SERVICE_STOP) {
                    val packageCheck = stateMap[event.packageName]
                    if (packageCheck != null) {
                        packageCheck.lastTimeForeground = event.timeStamp
                    }
                }
            }


//            for (key in stateMap.keys) {
//                val appState = stateMap[key]
//                if(appState != null) {
//                    if(appState.startTime != 0L && appState.endTime == 0L) {
//                        appState.endTime = end
//                        appState.totalTime = end - appState.startTime
//                    }
//                }
//            }

            //TODO: Loop all array and calc start sessions without closed event
            val result = HashMap<String, List<Long>>()

            for (key in stateMap.keys) {
                val appState = stateMap[key]
                if (appState != null) {
                    appState.calculateSessionData(
                        start,
                        end
                    )
                    result[key] = appState.toMicrosecondList()
                }
            }


            return result
        }
    }
}

class AppStateModel(
    var packageName: String = "",
    var startTime: Long = 0,
    var endTime: Long = 0,
    var totalTime: Long = 0,
    var lastTimeForeground: Long = 0,
    var sessions: ArrayList<SessionObj> = arrayListOf()
) {


    fun toList(): List<Long> {
        return listOf(totalTime, startTime, endTime, lastTimeForeground)
    }

    fun toMicrosecondList(): List<Long> {
        return listOf(totalTime / 1000, startTime / 1000, endTime / 1000, lastTimeForeground / 1000)
    }

    fun calculateSessionData(
        startFilter: Long,
        endFilter: Long
    ) {
        startTime = sessions.first().startTime
        if(startTime == 0L) {
            startTime = startFilter
        }
        endTime = sessions.last().endTime
        if(endTime == 0L) {
            endTime = endFilter
        }
        // Calculate duration
        for (session in sessions) {
            totalTime += session.duration()
        }

    }

}

class SessionObj(var startTime: Long, var endTime: Long) {
    fun duration(): Long {
        if(startTime == 0L || endTime == 0L) return 0L
        val duration = endTime - startTime
        if( duration > 0L ) return duration
        return 0L
    }
}
