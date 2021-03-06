package com.demo.wechataccessibility;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by agentchen on 2016/12/1.
 * Email agentchen97@gmail.com
 */

public class MyAccessibilityService extends AccessibilityService {
    private static final String TAG = "MyAccessibilityService";
    private static final String OPEN_BUTTON_ID = "com.tencent.mm:id/bg7";
    private static final String BACK_BUTTON_ID = "com.tencent.mm:id/gd";
    private static final String SLOW_TEXT_ID = "com.tencent.mm:id/bg6";
    private boolean isAccessibility = false;
    private boolean isMeUnLock = false;
    private boolean beforeLauncherUI = false;
    private PowerManager pm;
    private PowerManager.WakeLock wakeLock;
    private KeyguardManager km;
    private KeyguardManager.KeyguardLock keyguardLock;
    private List<AccessibilityNodeInfo> parents;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        parents = new ArrayList<>();
        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "bright");
        keyguardLock = km.newKeyguardLock("");
        Log.d(TAG, "onServiceConnected: ");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        int eventType = event.getEventType();
        switch (eventType) {
//            通知栏发生变化
            case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED:
                List<CharSequence> texts = event.getText();
                if (!texts.isEmpty()) {
                    for (CharSequence text : texts) {
                        String content = text.toString();
                        Log.d(TAG, "onAccessibilityEvent: Notification " + content);
                        if (content.contains("[微信红包]")) {
                            if (event.getParcelableData() != null &&
                                    event.getParcelableData() instanceof Notification) {
                                Notification notification = (Notification) event.getParcelableData();
                                PendingIntent pendingIntent = notification.contentIntent;
                                try {
                                    isAccessibility = true;
                                    Log.d(TAG, "onAccessibilityEvent: start Accessibility");
                                    autoUnlock();
                                    pendingIntent.send();
//                                  如果开启抢红包之前就处在LauncherUI则手动getLastPacket();
                                    if (beforeLauncherUI) {
                                        try {
                                            Thread.sleep(500);
                                            getLastPacket();
                                        } catch (InterruptedException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                } catch (PendingIntent.CanceledException e) {
                                    e.printStackTrace();
                                    isAccessibility = false;
                                }
                            }
                        }
                    }
                }
                break;
//            窗口状态发生变化
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                Log.d(TAG, "onAccessibilityEvent: eventContent " + event.toString());
//                如果抢红包之前就处于launchUI，则不会触发WindowChange事件，在这记录抢红包之前最后一次是否处在launchUI
                beforeLauncherUI = event.getClassName().toString().equals("com.tencent.mm.ui.LauncherUI");
                if (!isAccessibility) {
                    return;
                }
                String className = event.getClassName().toString();
                Log.d(TAG, "onAccessibilityEvent: Window: " + className);
                if (className.equals("com.tencent.mm.ui.LauncherUI")) {
                    getLastPacket();
                } else if (className.equals("com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyReceiveUI")) {
                    inputClick(OPEN_BUTTON_ID);
                    Log.d(TAG, "onAccessibilityEvent: 开红包");
                    slow(SLOW_TEXT_ID);//手慢了
                } else if (className.equals("com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyDetailUI")) {
                    inputClick(BACK_BUTTON_ID);
                    Log.d(TAG, "onAccessibilityEvent: 退出红包");
                    isAccessibility = false;
                    doBack(800);
                    autoLock();
                    Log.d(TAG, "onAccessibilityEvent: stop Accessibility");
                    isAccessibility = false;
                }
                break;
        }
    }

    private void inputClick(String clickId) {
//        点击指定id Node
        AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
        if (nodeInfo != null) {
            List<AccessibilityNodeInfo> list = nodeInfo.findAccessibilityNodeInfosByViewId(clickId);
            for (AccessibilityNodeInfo accessibilityNodeInfo : list) {
                Log.d(TAG, "inputClick: " + accessibilityNodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK));
            }
        }
    }

    public void getLastPacket() {
//        点击最后一个红包
        Log.d(TAG, "getLastPacket: 进入微信");
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        recycle(rootNode);
        if (parents.size() > 0) {
            Log.d(TAG, "getLastPacket: click " + parents.get(parents.size() - 1).performAction(AccessibilityNodeInfo.ACTION_CLICK));
            Log.d(TAG, "onAccessibilityEvent: 点击最后一个红包");
        } else {
            Log.d(TAG, "getLastPacket: 没有红包");
            doBack(500);
            isAccessibility = false;
            autoLock();
        }
    }

    private void recycle(AccessibilityNodeInfo rootNode) {
        parents.clear();
        if (rootNode.getChildCount() == 0) {
            if (rootNode.getText() != null) {
                if ("领取红包".equals(rootNode.getText().toString())) {
                    if (rootNode.isClickable()) {
                        Log.d(TAG, "recycle: click " + rootNode.performAction(AccessibilityNodeInfo.ACTION_CLICK));
                    }
                    AccessibilityNodeInfo parent = rootNode.getParent();
                    while (parent != null) {
                        if (parent.isClickable()) {
                            parents.add(parent);
                            Log.d(TAG, "recycle: list add parent");
                            break;
                        }
                        parent = parent.getParent();
                    }
                }
            }
        } else {
            for (int i = 0; i < rootNode.getChildCount(); i++) {
                if (rootNode.getChild(i) != null) {
                    recycle(rootNode.getChild(i));
                }
            }
        }
    }

    private void slow(String id) {
//        手慢了情况下
        AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
        if (nodeInfo != null) {
            List<AccessibilityNodeInfo> list = nodeInfo.findAccessibilityNodeInfosByViewId(id);
            for (AccessibilityNodeInfo accessibilityNodeInfo : list) {
                String s = accessibilityNodeInfo.getText().toString();
                if (s.contains("手慢了")) {
                    Log.d(TAG, "slow: 手慢了");
                    doBack(0);
                    doBack(800);
                    autoLock();
                    Log.d(TAG, "slow: stop Accessibilty");
                    isAccessibility = false;
                }
            }
        }
    }

    private void autoUnlock() {
        if (!pm.isScreenOn()) {
            wakeLock.acquire();
            Log.d(TAG, "onAccessibilityEvent: 亮屏");
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            if (km.isDeviceLocked()) {
                Log.d(TAG, "autoUnlock: sdk >= 22: 屏幕被密码锁柱");
                wakeLock.release();
                beforeLauncherUI = false;
            } else {
                if (km.inKeyguardRestrictedInputMode()) {
                    keyguardLock.disableKeyguard();
                    Log.d(TAG, "onAccessibilityEvent: 尝试解锁");
                    isMeUnLock = true;
                }
            }
        } else {
            if (km.isKeyguardLocked()) {
                Log.d(TAG, "autoUnlock: sdk < 22 屏幕被密码锁柱");
                wakeLock.release();
                beforeLauncherUI = false;
            } else {
                if (km.inKeyguardRestrictedInputMode()) {
                    keyguardLock.disableKeyguard();
                    Log.d(TAG, "onAccessibilityEvent: 尝试解锁");
                    isMeUnLock = true;
                }
            }
        }
    }

    private void autoLock() {
        if (isMeUnLock) {
            keyguardLock.reenableKeyguard();
            Log.d(TAG, "autoLock: 自动锁");
            if (wakeLock != null && pm.isScreenOn()) {
                wakeLock.release();
                Log.d(TAG, "autoLock: 自动灭");
            }
            isMeUnLock = false;
            beforeLauncherUI = false;
        }
    }

    private void doBack(int time) {
        if (time == 0) {
            this.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
            return;
        }
        try {
            Thread.sleep(time);
            this.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
        } catch (InterruptedException e) {
            e.printStackTrace();
            isAccessibility = false;
        }
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "onInterrupt: ");
    }
}