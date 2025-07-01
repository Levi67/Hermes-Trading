shell
    adb shell dumpsys deviceidle whitelist +YOUR_PACKAGE_NAME
    # Example: adb shell dumpsys deviceidle whitelist +com.levi.hermes_trading
    # To remove:
    # adb shell dumpsys deviceidle whitelist -YOUR_PACKAGE_NAME