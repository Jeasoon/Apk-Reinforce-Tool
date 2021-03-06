package org.jeson.reinforce.shell.$$$.processor;

import android.app.Application;
import android.app.Instrumentation;
import android.content.ContentProvider;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import org.jeson.reinforce.shell.$$$.util.ApplicationUtil;
import org.jeson.reinforce.shell.$$$.util.ReflectUtil;
import org.jeson.reinforce.shell.$$$.util.Util;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class ApplicationProcessor {

    public static final ApplicationProcessor DEFAULT = ReflectUtil.newInstance(ProcessorDefault.DEFAULT_PROCESSOR_NAME);

    public void onAppClassInit() {
        File cacheDexDir = ApplicationUtil.getCacheDexDir();
        if (Util.isEmptyDir(cacheDexDir) || ApplicationUtil.getVersionCode() != ApplicationUtil.getLatestVersionCode()) {
            File tmpDexFile = ApplicationUtil.getTmpDexFile();
            Util.delete(cacheDexDir);
            Util.delete(tmpDexFile);
            boolean isSuccess = loadSourceDex(ApplicationUtil.getSrcApkFile(), tmpDexFile);
            isSuccess &= splitDex(tmpDexFile, cacheDexDir);
            String mainClassName = ApplicationUtil.loadMainAppClassNameFromFile(tmpDexFile);
            ApplicationUtil.setMainAppClassName(mainClassName);
            Util.delete(tmpDexFile);
            if (isSuccess) {
                ApplicationUtil.setLatestVersionCode(ApplicationUtil.getVersionCode());
            } else {
                Util.delete(ApplicationUtil.getLatestVersionFile());
            }
        }
        ClassLoader newClassLoader = ApplicationUtil.newDexClassLoader(cacheDexDir, ApplicationUtil.getOdexDir(), ApplicationUtil.getLibDir(), ApplicationUtil.getAppClassLoader());
        ApplicationUtil.setAppClassLoader(newClassLoader);
    }

    protected abstract boolean loadSourceDex(File apkFile, File tempFile);

    protected abstract boolean splitDex(File sourceDexFile, File dexCacheDir);

    public void onAppConstructorInit() {
    }

    public void onAppAttachBaseContext(Context context) {
    }

    public void onAppCreate(Application application) {
        Application mainApp = replaceApplication(application, ApplicationUtil.getMainAppClassName());
        if (mainApp != null) {
            mainApp.onCreate();
        }
    }

    @SuppressWarnings("all")
    private Application replaceApplication(Application application, String mainClassName) {
        if (Util.isEmptyText(mainClassName)) {
            return null;
        }
        Object currentActivityThread = ApplicationUtil.getActivityThread();
        Object boundApplication = ReflectUtil.field(currentActivityThread, "mBoundApplication");
        Object loadedApkInfo = ApplicationUtil.getLoadedApk();
        ReflectUtil.field(loadedApkInfo, "mApplication", null);
        Application initialApplication = (Application) ReflectUtil.field(currentActivityThread, "mInitialApplication");
        List allApplications = (List) ReflectUtil.field(currentActivityThread, "mAllApplications");
        allApplications.remove(initialApplication);
        ApplicationInfo applicationInfo = ApplicationUtil.getApplicationInfo();
        ApplicationInfo appInfo = (ApplicationInfo) ReflectUtil.field(boundApplication, "appInfo");
        applicationInfo.className = mainClassName;
        appInfo.className = mainClassName;
        Application mainApplication = (Application) ReflectUtil.invoke(loadedApkInfo, "makeApplication", new Class[]{boolean.class, Instrumentation.class}, new Object[]{false, null});
        ReflectUtil.field(currentActivityThread, "mInitialApplication", mainApplication);
        Map providerMap = (Map) ReflectUtil.field(currentActivityThread, "mProviderMap");
        for (Object obj : providerMap.values()) {
            Object localProvider = ReflectUtil.field(obj, "mLocalProvider");
            if (localProvider == null) {
                continue;
            }
            Field[] fields = ContentProvider.class.getDeclaredFields();
            for (Field field : fields) {
                if (Context.class.getName().equals(field.getType().getName())) {
                    try {
                        field.setAccessible(true);
                        field.set(localProvider, mainApplication);
                    } catch (IllegalAccessException ignored) {
                    }
                }

            }
        }
        Context baseContext = application.getBaseContext();
        ReflectUtil.field(mainApplication, "mBase", baseContext);
        ReflectUtil.invoke(baseContext, "setOuterContext", new Class[]{Context.class}, new Object[]{mainApplication});
        replaceListField(application, mainApplication, "mAssistCallbacks");
        replaceListField(application, mainApplication, "mComponentCallbacks");
        replaceListField(application, mainApplication, "mActivityLifecycleCallbacks");
        return mainApplication;
    }

    @SuppressWarnings("all")
    private void replaceListField(Object oldObj, Object newObj, String fieldName) {
        ArrayList oldList = (ArrayList) ReflectUtil.field(oldObj, fieldName);
        ArrayList newList = (ArrayList) ReflectUtil.field(newObj, fieldName);

        if (oldList != null) {
            if (newList != null) {
                newList.addAll(oldList);
            } else {
                ReflectUtil.field(newObj, fieldName, new ArrayList<>(oldList));
            }
            oldList.clear();
        }
    }

}
