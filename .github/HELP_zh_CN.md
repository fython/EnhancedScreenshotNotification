增强截图通知 - 帮助
====

![](./PlayStore_Post.png)

这里我只展示常见问题。如果你遇到了一些 Bugs，请在这个仓库中[创建一个 Issue](https://github.com/fython/EnhancedScreenshotNotification/issues/new)。

## Q: 如何使用这个应用？

1. 这个应用仅支持 Android 7.0 或更高版本。
2. 安装 [女娲石（Nevolution）应用](https://play.google.com/store/apps/details?id=com.oasisfeng.nevo)。增强截图通知仅仅是女娲石的插件。
3. 安装增强截图通知。你可以从 GitHub Releases 或者 Google Play 中获得预编译包。如果你拥有开发环境，可以自行编译。
4. 打开 Nevolution 并为 “系统界面（System UI）” 激活这个应用插件。如果你的 Android 系统截图通知不是由这些包发出，请告诉我来增加支持：
    - `com.android.systemui` （AOSP 原生和大部分系统）
    - `com.oneplus.screenshot` （一加系统）
    - `com.samsung.android.app.smartcapture` （三星系统）
5. 为了得到你最新的截图位置，允许增强截图通知的存储权限，否则大部分功能无法工作。
6. 现在增强截图通知应该可以运作了。你可以在设置中设置你的偏好。

## Q: 哪里可以捐赠作者呢？

如果你认为我的应用十分有用而且你乐意帮助我，你可以通过支付宝捐赠我： `fythonx#gmail.com` （将 `#` 换成 `@`）

Paypal 也是可以接受的，但它扣取大量的手续费所以更推荐支付宝。Paypal： [paypal.me/fython](https://paypal.me/fython)

## Q: 为什么我给予了增强截图通知存储权限，但它还是不能为截图通知提供“编辑”操作？

首先，阅读第一个关于如何正确安装的问题。

检查你的系统默认截图目录是否为 `<内部储存空间/外部储存空间>/Pictures/Screenshots`。如果不是，在设置中修改为正确的路径。

## Q: 我能直接用指定应用编辑截图而不用每次都选择吗？

可以，去增强截图通知设置然后设定你偏好的编辑器。

## Q: 我能自定义截图声音吗？

不能，它的声音是系统指定的。

## Q: 如何使用悬浮预览？

首先，**保证你的 Android 系统是 8.0 或者更高版本**。它依赖了 Android 8.0 的新特性——画中画接口。

启用设置然后选择是否要自动显示悬浮预览窗来取代通知，通知不会自动隐藏但它的优先级会被设置为最低。

# 对于开发者

## Q: 我能在这个项目上贡献吗？

当然可以，我很乐意和其它开发者一起工作。

如果你修复了一些 Bugs，放轻松然后直接发送 Pull Request。

如果你想添加或者改变一些特性，请在发送 Pull Request 前创建一个 Issue 或者联系我进行讨论。
