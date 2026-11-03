#!/bin/bash
set -e

# Update and install Java (JDK) and dependencies required for Android SDK
sudo apt-get update && sudo apt-get install -y openjdk-17-jdk wget unzip

# Create SDK directory
mkdir -p $HOME/android-sdk/cmdline-tools

# Download Android Command Line Tools
wget https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip -O cmdline.zip
unzip -q cmdline.zip -d $HOME/android-sdk/cmdline-tools
mv $HOME/android-sdk/cmdline-tools/cmdline-tools $HOME/android-sdk/cmdline-tools/latest
rm cmdline.zip

# Set Environment Variables for the current session and persistent shell profile
export ANDROID_HOME=$HOME/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

echo "export ANDROID_HOME=$HOME/android-sdk" >> ~/.bashrc
echo "export PATH=\$PATH:\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools" >> ~/.bashrc

# Accept Licenses and Install platform tools
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
