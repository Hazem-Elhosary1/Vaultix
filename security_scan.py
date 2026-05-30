import os
import xml.etree.ElementTree as ET

PROJECT_PATH = "."  # مسار المشروع

def check_android_manifest():
    manifest_path = os.path.join(PROJECT_PATH, "app/src/main/AndroidManifest.xml")
    if not os.path.exists(manifest_path):
        print("[-] AndroidManifest.xml not found!")
        return

    print("[*] Scanning AndroidManifest.xml for security misconfigurations...")
    tree = ET.parse(manifest_path)
    root = tree.getroot()
    
    # 1. Check AllowBackup (ثغرة سحب البيانات عبر ADB backup)
    application = root.find("application")
    if application is not None:
        allow_backup = application.attrib.get("{http://schemas.android.com/apk/res/android}allowBackup")
        if allow_backup == "true":
            print("[!] WARNING: android:allowBackup is set to TRUE. An attacker with physical access could extract database files via USB debugging!")
        else:
            print("[+] SUCCESS: android:allowBackup is securely set to FALSE.")

        # 2. Check UsesCleartextTraffic (السماح بالاتصال غير المشفر)
        cleartext = application.attrib.get("{http://schemas.android.com/apk/res/android}usesCleartextTraffic")
        if cleartext == "true":
            print("[!] WARNING: Cleartext HTTP traffic is enabled. Use HTTPS only.")

        # 3. Check Exported Components (ثغرة استدعاء واجهات التطبيق من تطبيقات خبيثة)
        for child in application:
            if child.tag in ["activity", "service", "receiver", "provider"]:
                name = child.attrib.get("{http://schemas.android.com/apk/res/android}name")
                exported = child.attrib.get("{http://schemas.android.com/apk/res/android}exported")
                
                # If exported is true, check if it has intent-filters without permission
                if exported == "true":
                    print(f"[!] WARNING: Component '{name}' is EXPORTED. Other apps on the device can launch it directly!")

def scan_kotlin_files():
    print("\n[*] Scanning Kotlin source code for potential security risks...")
    sensitive_keywords = ["Log.d", "Log.v", "println", "System.out.print"]
    
    for root, dirs, files in os.walk(os.path.join(PROJECT_PATH, "app/src/main/java")):
        for file in files:
            if file.endswith(".kt"):
                file_path = os.path.join(root, file)
                with open(file_path, 'r', encoding='utf-8') as f:
                    for line_num, line in enumerate(f, 1):
                        # فحص تسريب البيانات الحساسة في السجلات (Logs)
                        for keyword in sensitive_keywords:
                            if keyword in line and any(arg in line for arg in ["password", "pin", "salt", "hash", "key"]):
                                print(f"[!] POTENTIAL LEAK in {file}:{line_num} -> Log output contains sensitive variables: {line.strip()}")

if __name__ == "__main__":
    check_android_manifest()
    scan_kotlin_files()
