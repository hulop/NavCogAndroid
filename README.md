<!--
The MIT License (MIT)

Copyright (c) 2016, 2024 IBM Corporation, Carnegie Mellon University and others
Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
-->
 
# NavCogAndroid

Android version of example application "NavCog".

## About
[About HULOP](https://github.com/hulop/00Readme)

## License
[MIT](http://opensource.org/licenses/MIT)

## Prerequisites
- Android Studio and Android SDK (Android 13 SDK 33 or later)
- CocoaPods (1.6.1 or later)
- cmake (3.15.2 or later)

## Dependent libraries
- [HULOP blelocpp](https://github.com/hulop/blelocpp) (MIT License)
- [OpenCV (OpenCV-Dynamic) 4.0.1](https://opencv.org/releases/) (BSD License)

### About libopencv_java4.so

Please put **libopencv_java4.so** from Android pack OpenCV-android-sdk (under /sdk/native/libs folder) into folders below:

- /app/src/main/jniLibs/arm64-v8a
- /app/src/main/jniLibs/armeabi-v7a
- /app/src/main/jniLibs/x86_64

OpenCV-android-sdk can be found at [https://opencv.org/releases/](https://opencv.org/releases/).

(Currently, HULOP uses version [4.0.1](https://sourceforge.net/projects/opencvlibrary/files/4.0.1/). If needed, please use an appropriate version of so file.)

## How to build

### Install CocoaPods (If you have not installed)
- Install and update Homebrew
- $brew install python
- $pip install mercurial
- Install CocoaPods

### Install cmake (version 3.15.2)
- $brew instal cmake

### Build bleloc Framework for Android
1. Follow instructions of [blelocpp](https://github.com/hulop/blelocpp/tree/master)
  - If you want to build Android version only, you don't need to do the final step ($sh build.sh Release).

2. If OpenCV-Dynamic4.0.1 can't install well. Please try to use Homebrew as below.
    
    1. $brew install opencv
    2. $cd blelocpp/platform/ios
    3. Comment out the line below from Podfile.
       ```
       pod 'OpenCV-Dynamic', '4.0.1'
       ```
    4. $pod install
    5. Modify **NavCogAndroid/app/CMakeLists.txt** to include OpenCV as below.
       ```
       # Use OpenCV via Homebrew
       include_directories(/usr/local/opt/opencv/include/opencv4)
       ```
    6. Go to Build step. (Step 3 below is not required.)

3. If `Pods/Headers/Public/OpenCV-Dynamic` does not exist, copy header files into `Pods/Headers/Public/OpenCV-Dynamic`.

    ```
    cd blelocpp/platform/ios
    mkdir -p Pods/Headers/Public/OpenCV-Dynamic/opencv2
    cp -R Pods/OpenCV-Dynamic/opencv2.framework/Headers/ Pods/Headers/Public/OpenCV-Dynamic/opencv2
    ```

### Build
Build the example app by using Android Studio.

## How to use local serverlist file for test.
1. Enable adb shell
  - Install adb with Android Studio, and then add PATH for it. (For example, add line below into ~/.bashrc)
    ```
    export PATH=$PATH:$HOME/Library/Android/sdk/platform-tools
    ```
2. Find target folder in the Android device.
  * Connect target Android device with Mac, and then start ```adb shell```
  * Find target folder (```/storage/self/primary/Android/data/hulop.navcog/files``` is the default.)
  * After confirm the target folder, ```exit``` from adb.
3. Copy serverlist file(serverlist.txt or serverlist.json) into the target folder as below.
  * ```adb push serverlist.[txt|json] /storage/self/primary/Android/data/hulop.navcog/files```

## Troubleshooting
### build fail related to blelocpp
Please try one of steps below, and try build again.
  - Delete `blelocpp/platform/ios/Pods`, and then `pod install`
  - **Build→Clean Project**, **Build→Refresh Linked C++ Projects** in Android Studio.
