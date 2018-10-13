package com.virjar.ratel;

import net.dongliu.apk.parser.ApkFile;
import net.dongliu.apk.parser.bean.ApkMeta;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;

/**
 * Created by virjar on 2018/10/13.
 */

public class Param {
    public File xposedApk;
    public File originApk;
    public ApkMeta originApkMeta;

    public Param(File xposedApk, File originApk, ApkMeta originApkMeta) {
        this.xposedApk = xposedApk;
        this.originApk = originApk;
        this.originApkMeta = originApkMeta;
    }

    public static Param parseAndCheck(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.print("must pass 2 apk file ");
            System.exit(-1);
        }

        //检查输入参数
        File apk1 = new File(args[0]);
        File apk2 = new File(args[1]);
        if (!checkAPKFile(apk1)) {
            System.out.println(args[0] + " is not a illegal apk file");
            return null;
        }

        if (!checkAPKFile(apk2)) {
            System.out.println(args[1] + " is not a illegal apk file");
            return null;
        }

        //确定那个apk是原始apk，那个是xposed模块apk

        File xposedApk;
        File originApk;


        ApkFile apkFile1 = new ApkFile(apk1);
        byte[] xposedConfig = apkFile1.getFileData("assets/xposed_init");

        ApkFile apkFile2 = new ApkFile(apk2);
        byte[] xposedConfig2 = apkFile2.getFileData("assets/xposed_init");

        if (xposedConfig == null && xposedConfig2 == null) {
            System.out.println("两个文件必须有一个是xposed模块apk");
            return null;
        }
        if (xposedConfig != null && xposedConfig2 != null) {
            System.out.println("两个文件都是xposed模块apk");
            return null;
        }
        ApkMeta apkMeta;
        if (xposedConfig == null) {
            xposedApk = apk2;
            originApk = apk1;
            apkMeta = apkFile1.getApkMeta();
        } else {
            xposedApk = apk1;
            originApk = apk2;
            apkMeta = apkFile2.getApkMeta();
        }
        IOUtils.closeQuietly(apkFile1);
        IOUtils.closeQuietly(apkFile2);
        return new Param(xposedApk, originApk, apkMeta);
    }

    private static boolean checkAPKFile(File file) {
        try (ApkFile apkFile = new ApkFile(file)) {
            apkFile.getApkMeta();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
