package ga.uabart.disableturningoff;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.BatteryManager;
import android.os.Build;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import static de.robv.android.xposed.XposedHelpers.findClass;

public class Main implements IXposedHookLoadPackage {

    private static final String PKG_SYSTEM = "android";
    private static final String PKG_BATTERY_SERVICE = "com.android.server.BatteryService";
    private static final String PKG_ACTIVITY_MANAGER_NATIVE = "android.app.ActivityManagerNative";

    private Context context;
    private NotificationManager notificationManager;

    @Override
    public void handleLoadPackage(LoadPackageParam loadPackageParam) throws Throwable {

        if (!loadPackageParam.packageName.equals(PKG_SYSTEM)) return;

        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.KITKAT) {
            XposedBridge.log("The Disable Turning Off Xposed module will only work on " +
                    "KitKat - exiting now");
            return;
        }

        try {
            applyHooks(loadPackageParam);
        } catch (Throwable t) {
            XposedBridge.log("Disable Turning Off failed to apply hooks: " + t.toString());
        }
    }

    private void applyHooks(LoadPackageParam loadPackageParam) throws Throwable {

        final Class<?> batteryService = findClass(PKG_BATTERY_SERVICE, loadPackageParam.classLoader);
        final Class<?> amn = findClass(PKG_ACTIVITY_MANAGER_NATIVE, loadPackageParam.classLoader);


        XposedBridge.hookAllConstructors(batteryService, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                context = (Context) param.args[0];
                notificationManager = (NotificationManager) context
                        .getSystemService(Context.NOTIFICATION_SERVICE);
            }
        });

        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                final Object thiz = param.thisObject;
                Object battery_prop = XposedHelpers.getObjectField(thiz, "mBatteryProps");
                int batteryLevel = XposedHelpers.getIntField(battery_prop, "batteryLevel");
                boolean isPowered = (Boolean) XposedHelpers.callMethod(thiz, "isPowered",
                        (BatteryManager.BATTERY_PLUGGED_AC | BatteryManager.BATTERY_PLUGGED_USB | BatteryManager.BATTERY_PLUGGED_WIRELESS));
                boolean isSystemReady = (Boolean) XposedHelpers.callStaticMethod(amn,
                        "isSystemReady");
                if (batteryLevel == 0 && !isPowered && isSystemReady) {
                    notifyUser();
                    param.setResult(null);
                }
            }
        };

        XposedBridge.hookAllMethods(batteryService, "shutdownIfNoPower", hook);
        XposedBridge.hookAllMethods(batteryService, "shutdownIfNoPowerLocked", hook);
    }

    private void notifyUser() {
        Notification.Builder build = new Notification.Builder(context);
        build.setOngoing(true);
        build.setSmallIcon(android.R.drawable.ic_dialog_alert);
        build.setContentTitle("Shutdown blocked");
        build.setContentText("Android tried to shutdown");

        notificationManager.notify(31337, build.build());

    }
}
