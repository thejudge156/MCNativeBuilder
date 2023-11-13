# MCNativeBuilder
### A tool for building native Minecraft images utilizing [GraalVM](https://github.com/oracle/graal).

Very in alpha, expect bugs and major changes!

## Why did I do this?
### Building native images of Minecraft is usally an annoying, time consuming, and complex process that requires not only a lot of knowledge of the JVM, but also Minecraft itself. This tool is meant to allow for quick and easy building of native Minecraft images without much work on the end users side.

## How do you use it?
### Go to the releases page and download the latest build then run ```java -jar <downloadedJarPath> -<minecraftVersion>``` (eg. 1.20.2) inside of Command Prompt/Terminal.

## How do you run the game?
### After the image has finished building, go to the ```native-build``` folder and find the executable for the version you built (eg. 1.20.2.exe), now open Command Prompt/Terminal where that executable is and run ```1.20.2.exe (or the version you built) --accessToken <accessToken> --assetIndex 8 --username <username> --uuid <uuid> --version MCNative```

## So you're getting errors?
### 1. Make sure that when you run your commnds, you remove the ```(text)``` and replace the ```<text>``` blocks with the information you want. For instance, if it says ```<username>``` you need to replace that entire line with ```TheJudge156``` or whatever your username is.
