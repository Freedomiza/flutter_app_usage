import 'dart:async';

import 'package:flutter/services.dart';
import 'dart:io' show Platform;

/// Custom Exception for the plugin.
/// Thrown whenever the plugin is used on platforms other than Android.
class AppUsageException implements Exception {
  String _cause;

  AppUsageException(this._cause);

  @override
  String toString() => _cause;
}

class AppUsageInfo {
  late String _packageName, _appName;
  late int _event;
  late Duration _usage;
  DateTime _startDate, _endDate, _lastForeground;
  bool _isSystemApp = false;

  AppUsageInfo(
    String name,
    this._appName,
    double usageInSeconds,
    this._startDate,
    this._endDate,
    this._lastForeground,
    this._event,
    this._isSystemApp,
  ) {
    List<String> tokens = name.split('.');
    _packageName = name;
    if (_appName.isEmpty) {
      _appName = tokens.last;
    }
    _usage = Duration(seconds: usageInSeconds.toInt());
  }

  /// The name of the application
  String get appName => _appName;

  /// The name of the application package
  String get packageName => _packageName;

  /// The amount of time the application has been used
  /// in the specified interval
  Duration get usage => _usage;

  /// The start of the interval
  DateTime get startDate => _startDate;

  /// The end of the interval
  DateTime get endDate => _endDate;

  /// Last time app was in foreground
  DateTime get lastForeground => _lastForeground;

  bool get isSystemApp => _isSystemApp;

  String get event {
    switch (_event) {
      case 0:
        return 'Unknown';

      case 1:
        return 'ACTIVITY_RESUMED';
      case 2:
        return 'ACTIVITY_PAUSED';
      case 5:
        return 'CONFIGURATION_CHANGE';
      case 6:
        return 'SYSTEM_INTERACTION';
      case 7:
        return 'USER_INTERACTION';
      case 9:
        return 'CHOOSER_ACTION';
      case 10:
        return 'NOTIFICATION_SEEN';
      case 11:
        return 'STANDBY_BUCKET_CHANGED';
      case 12:
        return 'NOTIFICATION_INTERRUPTION';
      case 14:
        return 'SLICE_PINNED';
      case 15:
        return 'SCREEN_INTERACTIVE';
      case 16:
        return 'SCREEN_NON_INTERACTIVE';
      case 17:
        return 'KEYGUARD_SHOWN';
      case 18:
        return 'KEYGUARD_HIDDEN';
      case 19:
        return 'FOREGROUND_SERVICE_START';
      case 20:
        return 'FOREGROUND_SERVICE_STOP';
      case 23:
        return 'ACTIVITY_STOPPED';
      case 24:
        return 'ACTIVITY_DESTROYED';
      case 26:
        return 'DEVICE_SHUTDOWN';
      case 27:
        return 'DEVICE_STARTUP';

      case 28:
        return 'USER_UNLOCKED';
      case 29:
        return 'USER_STOPPED';

      default:
        return 'Unknown';
    }
  }

  AppUsageInfo clone() {
    return AppUsageInfo(
      _packageName,
      _appName,
      _usage.inSeconds.toDouble(),
      _startDate,
      _endDate,
      _lastForeground,
      _event,
      _isSystemApp,
    );
  }

  void setDuration(Duration duration) {
    _usage = duration;
  }

  void setEndDate(DateTime endDate) {
    _endDate = endDate;
  }

  @override
  String toString() {
    return 'App Usage: $packageName - $appName, duration: $usage [$startDate, $endDate]';
  }
}

class AppUsage {
  static const MethodChannel _methodChannel =
      const MethodChannel("app_usage.methodChannel");

  static final AppUsage _instance = AppUsage._();
  AppUsage._();
  factory AppUsage() => _instance;

  Future<List<AppUsageInfo>> getAppUsage(
    DateTime startDate,
    DateTime endDate,
  ) async {
    if (Platform.isAndroid) {
      /// Convert dates to ms since epoch
      int end = endDate.millisecondsSinceEpoch;
      int start = startDate.millisecondsSinceEpoch;

      /// Set parameters
      Map<String, int> interval = {'start': start, 'end': end};

      /// Get result and parse it as a Map of <String, List<double>>
      Map usage = await _methodChannel.invokeMethod('getUsage', interval);

      // Convert to list of AppUsageInfo
      List<AppUsageInfo> result = [];
      for (String key in usage.keys) {
        List<double> temp = List<double>.from(usage[key]);
        if (temp[0] > 0) {
          result.add(AppUsageInfo(
            key,
            "",
            temp[0],
            DateTime.fromMillisecondsSinceEpoch(temp[1].round() * 1000),
            DateTime.fromMillisecondsSinceEpoch(temp[2].round() * 1000),
            DateTime.fromMillisecondsSinceEpoch(temp[3].round() * 1000),
            0,
            false,
          ));
        }
      }

      return result;
    }
    throw AppUsageException('AppUsage API is only available on Android');
  }

  Future<List<AppUsageInfo>> getAppUsageDaily(
    DateTime startDate,
    DateTime endDate,
  ) async {
    if (Platform.isAndroid) {
      /// Convert dates to ms since epoch
      int end = endDate.millisecondsSinceEpoch;
      int start = startDate.millisecondsSinceEpoch;

      /// Set parameters
      Map<String, int> interval = {'start': start, 'end': end};

      /// Get result and parse it as a Map of <String, List<double>>
      Map usage = await _methodChannel.invokeMethod('getUsageDaily', interval);

      // Convert to list of AppUsageInfo
      List<AppUsageInfo> result = [];
      for (String key in usage.keys) {
        List<double> temp = List<double>.from(usage[key]);
        if (temp[0] > 0) {
          result.add(AppUsageInfo(
            key,
            "",
            temp[0],
            DateTime.fromMillisecondsSinceEpoch(temp[1].round() * 1000),
            DateTime.fromMillisecondsSinceEpoch(temp[2].round() * 1000),
            DateTime.fromMillisecondsSinceEpoch(temp[3].round() * 1000),
            0,
            false,
          ));
        }
      }

      return result;
    }
    throw AppUsageException('AppUsage API is only available on Android');
  }

  Future<List<AppUsageInfo>> getUsageMapFromEvents(
    DateTime startDate,
    DateTime endDate,
  ) async {
    if (Platform.isAndroid) {
      /// Convert dates to ms since epoch
      int end = endDate.millisecondsSinceEpoch;
      int start = startDate.millisecondsSinceEpoch;

      /// Set parameters
      Map<String, dynamic> interval = {'start': start, 'end': end};

      /// Get result and parse it as a Map of <String, List<double>>
      List<dynamic> usage =
          await _methodChannel.invokeMethod('getUsageFromEvents', interval);

      // Convert to list of AppUsageInfo
      List<AppUsageInfo> result = [];

      // for (String key in usage.keys) {
      List<Map> sessions = List<Map>.from(usage);
      if (sessions.isNotEmpty) {
        for (var session in sessions) {
          result.add(
            AppUsageInfo(
              session['packageName'],
              session['appName'],
              (session['duration'] as int?)?.toDouble() ?? 0.0,
              DateTime.fromMillisecondsSinceEpoch(
                  session['startTime'].round() * 1000),
              DateTime.fromMillisecondsSinceEpoch(
                  session['endTime'].round() * 1000),
              DateTime.fromMillisecondsSinceEpoch(0),
              session['event'],
              session['isSystemApp'],
            ),
          );
        }
      }
      // }

      return result;
    }
    throw AppUsageException('AppUsage API is only available on Android');
  }

  Future<List<AppUsageInfo>> getRawsUsageFromEvents(
    DateTime startDate,
    DateTime endDate,
  ) async {
    if (Platform.isAndroid) {
      /// Convert dates to ms since epoch
      int end = endDate.millisecondsSinceEpoch;
      int start = startDate.millisecondsSinceEpoch;

      /// Set parameters
      Map<String, dynamic> interval = {'start': start, 'end': end};

      /// Get result and parse it as a Map of <String, List<double>>
      List<dynamic> usage =
          await _methodChannel.invokeMethod('getRawUsageFromEvents', interval);

      // Convert to list of AppUsageInfo
      List<AppUsageInfo> result = [];

      // for (String key in usage.keys) {
      List<Map> sessions = List<Map>.from(usage);
      if (sessions.isNotEmpty) {
        for (var session in sessions) {
          result.add(
            AppUsageInfo(
              session['packageName'],
              session['appName'],
              (session['duration'] as int?)?.toDouble() ?? 0.0,
              DateTime.fromMillisecondsSinceEpoch(
                  session['startTime'].round() * 1000),
              DateTime.fromMillisecondsSinceEpoch(
                  session['endTime'].round() * 1000),
              DateTime.fromMillisecondsSinceEpoch(0),
              session['event'],
              session['isSystemApp'],
            ),
          );
        }
      }
      // }

      return result;
    }
    throw AppUsageException('AppUsage API is only available on Android');
  }

  Future<bool> checkAppUsagePermission() async {
    if (Platform.isAndroid) {
      bool? permission = await _methodChannel.invokeMethod('checkPermission');
      return permission ?? false;
    }
    throw AppUsageException('AppUsage API is only available on Android');
  }

  Future<bool> requestAppUsagePermission() async {
    if (Platform.isAndroid) {
      bool? permission = await _methodChannel.invokeMethod('requestPermission');
      return permission ?? false;
    }
    throw AppUsageException('AppUsage API is only available on Android');
  }
}
