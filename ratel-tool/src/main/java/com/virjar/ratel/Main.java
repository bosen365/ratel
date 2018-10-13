package com.virjar.ratel;

import com.google.common.io.Files;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import brut.androlib.Androlib;
import brut.androlib.AndrolibException;
import brut.androlib.ApkDecoder;
import brut.androlib.ApkOptions;
import brut.androlib.res.util.ExtFile;
import brut.androlib.res.xml.ResXmlPatcher;
import brut.directory.Directory;
import brut.directory.DirectoryException;

/**
 * Created by virjar on 2018/10/6.
 */

public class Main {

    private static final String driverAPKPath = "ratel-driver.apk";

    private static void decodeOriginAPK(File originAPK, File outDir) throws AndrolibException, IOException, DirectoryException {
        System.out.println("对目标apk解包...");
        ApkDecoder decoder = new ApkDecoder();
        decoder.setApkFile(originAPK);

        decoder.setOutDir(outDir);
        //不对源码进行解码
        decoder.setDecodeSources(ApkDecoder.DECODE_SOURCES_NONE);
        decoder.setKeepBrokenResources(true);
        decoder.setForceDelete(true);
        decoder.setForceDecodeManifest(ApkDecoder.FORCE_DECODE_MANIFEST_FULL);
        decoder.setDecodeAssets(ApkDecoder.DECODE_ASSETS_NONE);
        decoder.decode();
        decoder.close();
    }

    private static void cleanOriginOut(File originBuildDir) throws IOException {
        File[] files = originBuildDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().equalsIgnoreCase(".dex")) {
                    FileUtils.forceDelete(file);
                } else if (file.isDirectory()) {
                    String name = file.getName();
                    if (name.equalsIgnoreCase("unknown")
                            || name.equalsIgnoreCase("assets")
                            || name.equalsIgnoreCase("lib")
                            || name.equalsIgnoreCase("libs")
                            || name.equalsIgnoreCase("kotlin")
                            || name.startsWith("smali")) {
                        FileUtils.forceDelete(file);
                    }
                }
            }
        }
    }

    private static void repairManifest(File originBuildDir) throws ParserConfigurationException, SAXException, IOException, TransformerException {
        System.out.println("修正AndroidManifest.xml...");

        File manifestFile = new File(originBuildDir, manifestFileName);
        Document document = ResXmlPatcher.loadDocument(manifestFile);
        NodeList applicationNodeList = document.getElementsByTagName("application");
        if (applicationNodeList.getLength() == 0) {
            throw new IllegalStateException("the manifest xml file must has application node");
        }
        Element applicationElement = (Element) applicationNodeList.item(0);
        String applicationClass = applicationElement.getAttribute("android:name");
        applicationElement.setAttribute("android:name", driverApplicationClass);
        applicationElement.removeAttribute("android:qihoo");
        if (StringUtils.isNotBlank(applicationClass)) {
            //原始的Application配置，防止到meta中，让驱动器负责加载原始Application
            Element applicationMeta = document.createElement("meta-data");
            applicationMeta.setAttribute("android:name", APPLICATION_CLASS_NAME);
            applicationMeta.setAttribute("android:value", applicationClass);
            Node firstChild = applicationElement.getFirstChild();
            if (firstChild != null) {
                applicationElement.insertBefore(applicationMeta, firstChild);
            } else {
                applicationElement.appendChild(applicationMeta);
            }
            // item.appendChild(applicationMeta);
        }

        //如果当前设备不支持epic，那么跳转到这个页面，提示用户不支持
        Element activityElement = document.createElement("activity");
        activityElement.setAttribute("android:name", "com.virjar.retal_driver.NotSupportActivity");
        applicationElement.appendChild(activityElement);

        ResXmlPatcher.saveDocument(manifestFile, document);
    }

    public static void main(String[] args) throws Exception {
        Param param = Param.parseAndCheck(args);
        if (param == null) {
            //参数检查失败
            return;
        }

        //工作目录准备
        File workDir = cleanWorkDir();
        System.out.println("clean working directory:" + workDir.getAbsolutePath());
        File driverAPKFile = new File(workDir, driverAPKPath);
        System.out.println("release ratel container apk ,into :" + driverAPKFile.getAbsolutePath());
        IOUtils.copy(Main.class.getClassLoader().getResourceAsStream(driverAPKPath), new FileOutputStream(driverAPKFile));

        File originBuildDir = new File(workDir, "ratel_origin_apk");
        decodeOriginAPK(param.originApk, originBuildDir);

        System.out.println("移出原apk中无用文件");
        cleanOriginOut(originBuildDir);

        File assetsDir = new File(originBuildDir, "assets");
        assetsDir.mkdirs();
        System.out.println("嵌入扩展apk文件");
        Files.copy(param.originApk, new File(assetsDir, originAPKFileName));
        Files.copy(param.xposedApk, new File(assetsDir, xposedBridgeApkFileName));


        System.out.println("植入容器代码...");
        ExtFile driverAPKImage = new ExtFile(driverAPKFile);
        Directory unk = driverAPKImage.getDirectory();
        try {
            // loop all items in container recursively, ignoring any that are pre-defined by aapt
            Set<String> driverFiles = unk.getFiles(true);
            for (String file : driverFiles) {
                //代码，lib库，使用driver的，因为driver会使用epic的so文件
                if (file.endsWith(".dex") || file.startsWith("lib") || file.startsWith("libs")) {
                    unk.copyToDir(originBuildDir, file);
                }
            }
        } catch (DirectoryException ex) {
            throw new AndrolibException(ex);
        }
        driverAPKImage.close();

        repairManifest(originBuildDir);

        System.out.println("重新打包apk...");
        File outFile = new File(workDir.getParentFile(),
                param.originApkMeta.getPackageName() + "_" + param.originApkMeta.getVersionName() + "_" + param.originApkMeta.getVersionCode() + "_ratel_unsigned.apk");
        ApkOptions apkOptions = new ApkOptions();
        apkOptions.forceBuildAll = true;
        new Androlib(apkOptions).build(originBuildDir, outFile);

        System.out.println("清除工作目录..");
        FileUtils.deleteDirectory(workDir);
        System.out.println("输出apk路径：" + outFile.getAbsolutePath());

    }

    private static final String APPLICATION_CLASS_NAME = "APPLICATION_CLASS_NAME";
    private static final String driverApplicationClass = "com.virjar.retal_driver.RetalDriverApplication";
    private static final String manifestFileName = "AndroidManifest.xml";
    private static final String originAPKFileName = "ratel_origin_apk.apk";
    private static final String xposedBridgeApkFileName = "ratel_xposed_module.apk";


    private static File cleanWorkDir() {
        File workDir = new File("ratel_work_dir");
        if (!workDir.exists()) {
            workDir.mkdirs();
            return workDir;
        }
        if (!workDir.isDirectory()) {
            workDir.delete();
        }
        File[] files = workDir.listFiles();
        if (files == null) {
            return workDir;
        }

        for (File file : files) {
            FileUtils.deleteQuietly(file);
        }
        return workDir;
    }
}
