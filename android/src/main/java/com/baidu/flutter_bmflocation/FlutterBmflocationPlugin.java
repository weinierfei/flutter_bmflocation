package com.baidu.flutter_bmflocation;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.baidu.flutter_bmflocation.handlers.HandlersFactory;
import com.baidu.location.LocationClient;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;

/** FlutterBmflocationPlugin */
public class FlutterBmflocationPlugin implements FlutterPlugin, MethodCallHandler,ActivityAware {

  private static MethodChannel locationChannel;

  private static MethodChannel geofenceChannel;
  public static Context mContext = null;

  public static Activity mActivity = null;

  /**
   * 多引擎安全补丁（Flywear local patch，基线 3.8.5）：
   * workmanager 后台任务会拉起第二个 FlutterEngine 并自动注册全部插件，
   * 原版 onAttachedToEngine 会无条件覆盖 static channel 单例，
   * 后台引擎销毁后单例指向已 detach 的死 channel，导致进程级定位失败。
   * ownerMessenger 记录持有通道的引擎，后续引擎 attach/detach 均不得影响持有者通道。
   *
   * 极端时序说明（后台引擎先于主引擎 attach）：此时 ownerMessenger 会先被后台引擎占用，
   * 主引擎随后 attach 时 locationChannel 非 null 故跳过注册 → 主引擎无法收到回调。
   * 该场景概率极低（App 冷启动主引擎通常先 attach），且后台引擎销毁时会清空 ownerMessenger，
   * 主引擎下次 resume 重新 attach 时可自愈。如需彻底解决，可在 onAttachedToActivity
   * 时把 ownerMessenger 升级为 Activity 引擎（当前版本暂不实现）。
   */
  private static BinaryMessenger ownerMessenger = null;

   /* 新版接口 */
  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    if (null == flutterPluginBinding) {
      return;
    }
    if (null == mContext) {
      mContext = flutterPluginBinding.getApplicationContext();
    }
    initMethodChannel(flutterPluginBinding.getBinaryMessenger());
  }

  private void initMethodChannel(BinaryMessenger binaryMessenger) {
    if (null == binaryMessenger) {
      return;
    }

    // 多引擎安全：主引擎已持有通道时，后台/二级引擎不得覆盖单例
    if (locationChannel != null && ownerMessenger != null && ownerMessenger != binaryMessenger) {
      return;
    }
    ownerMessenger = binaryMessenger;

    locationChannel = new MethodChannel(binaryMessenger, Constants.MethodChannelName.LOCATION_CHANNEL);
    locationChannel.setMethodCallHandler(this);

    MethodChannelManager.getInstance().putLocationChannel(locationChannel);

    geofenceChannel = new MethodChannel(binaryMessenger, Constants.MethodChannelName.GEOFENCE_CHANNEL);
    geofenceChannel.setMethodCallHandler(this);

    MethodChannelManager.getInstance().putGeofenceChannel(geofenceChannel);
  }

   /* 旧版接口 */
//  public static void registerWith(PluginRegistry.Registrar registrar) {
//    if (null == registrar) {
//      return;
//    }
//    if (null == mContext) {
//      mContext = registrar.context();
//    }
//    initStaticMethodChannel(registrar.messenger());
//  }

  private static void initStaticMethodChannel(BinaryMessenger binaryMessenger) {
    if (null == binaryMessenger) {
      return;
    }

    FlutterBmflocationPlugin flutterBmfPlugin = new FlutterBmflocationPlugin();

    locationChannel = new MethodChannel(binaryMessenger, Constants.MethodChannelName.LOCATION_CHANNEL);
    locationChannel.setMethodCallHandler(flutterBmfPlugin);
    MethodChannelManager.getInstance().putLocationChannel(locationChannel);

    geofenceChannel = new MethodChannel(binaryMessenger, Constants.MethodChannelName.GEOFENCE_CHANNEL);
    geofenceChannel.setMethodCallHandler(flutterBmfPlugin);
    MethodChannelManager.getInstance().putLocationChannel(geofenceChannel);
  }


  @Override
  public void onAttachedToActivity(ActivityPluginBinding binding) {
    mActivity = binding.getActivity();
  }

  @Override
  public void onDetachedFromActivityForConfigChanges() {
    onDetachedFromActivity();
  }

  @Override
  public void onReattachedToActivityForConfigChanges(ActivityPluginBinding binding) {
    onAttachedToActivity(binding);
  }

  @Override
  public void onDetachedFromActivity() {

  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
    if (mContext == null) {
      result.error("-1", "context is null", null);
    }

    if (call.method.equals(Constants.MethodID.LOCATION_SETAGREEPRIVACY)) {
      try {
        boolean isAgreePrivacy = (Boolean) call.arguments;
        LocationClient.setAgreePrivacy(isAgreePrivacy);
      } catch (Exception e) {
      }
    }
    
    HandlersFactory.getInstance(mContext).dispatchMethodHandler(mContext, call, result);
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    // 多引擎安全：仅持有者引擎销毁时才清理通道；后台引擎 detach 不得清空主引擎通道
    if (ownerMessenger != null && binding.getBinaryMessenger() != ownerMessenger) {
      return;
    }
    if (locationChannel != null) {
      locationChannel.setMethodCallHandler(null);
      locationChannel = null;
    }
    if (geofenceChannel != null) {
      geofenceChannel.setMethodCallHandler(null);
      geofenceChannel = null;
    }
    ownerMessenger = null;
  }
}
