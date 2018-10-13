package com.virjar.ratel_wifilocating;

import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.util.Log;

import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Created by virjar on 2018/10/13.
 * <br>
 * hook WiFi万能钥匙 ，
 * package为：com.snda.wifilocating，
 * app名字为：WifiKey
 */

public class WifiLocatingHook implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (StringUtils.equalsIgnoreCase(lpparam.packageName, "com.snda.wifilocating")) {
            throw new IllegalStateException("this plugin only support com.snda.wifilocating");
        }
        final XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                WifiConfiguration wifiConfiguration = (WifiConfiguration) param.args[0];
                if (StringUtils.isBlank(wifiConfiguration.preSharedKey)) {
                    return;
                }
                Log.i("weijia1", "wifi: " + wifiConfiguration.SSID + "  密码:" + wifiConfiguration.preSharedKey);
            }
        };
        Method connect = XposedHelpers.findMethodExactIfExists(WifiManager.class, "connect"
                , WifiConfiguration.class, XposedHelpers.findClassIfExists("android.net.wifi.WifiManager.ActionListener", WifiManager.class.getClassLoader()));
        if (connect != null) {
            XposedBridge.hookMethod(connect, hook);
        }

        XposedHelpers.findAndHookMethod(WifiManager.class, "addNetwork"
                , WifiConfiguration.class, hook);
    }
}
