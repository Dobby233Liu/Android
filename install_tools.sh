#!/usr/bin/env bash
mkdir "$ANDROID_HOME/licenses" || true;
echo "8933bad161af4178b1185d1a37fbf41ea5269c55" > "$ANDROID_HOME/licenses/android-sdk-license";
echo "d56f5187479451eabf01fb78af6dfcb131a6481e" > "$ANDROID_HOME/licenses/android-sdk-license";
echo "d56f5187479451eabf01fb78af6dfcb131a6481e" > "$ANDROID_HOME/licenses/android-sdk-license";
echo "24333f8a63b6825ea9c5514f83c2829b004d1fee" > "$ANDROID_HOME/licenses/android-sdk-license";
sudo /usr/local/lib/android/sdk/tools/bin/sdkmanager 'ndk;21.0.6113669'
sudo /usr/local/lib/android/sdk/tools/bin/sdkmanager tools;
echo "Fetching ndk-bundle. Suppressing output to avoid travis 4MG size limit";
sudo /usr/local/lib/android/sdk/tools/bin/sdkmanager "ndk-bundle" >/dev/null;
echo "Fetching ndk-bundle complete";
sudo /usr/local/lib/android/sdk/tools/bin/sdkmanager "system-images;android-22;default;armeabi-v7a";
yes | sdkmanager --licenses;
