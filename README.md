![banner](https://github.com/thejudge156/MCNativeBuilder/blob/refactor/MCNativeBanner.png)

# MCNativeBuilder

### A tool for building native Minecraft images utilizing [GraalVM](https://github.com/oracle/graal). This project is still in alpha, expect bugs and major changes!

## Supporting:
I can only work on these projects if I can be stable financially. If you want to support me, you can here:

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/N4N1S0WVY)

## Why did I do this?
### Building native images of Minecraft is usally an annoying, time consuming, and complex process that requires not only a lot of knowledge of the JVM, but also Minecraft itself. This tool is meant to allow for quick and easy building of native Minecraft images without much work on the end users side.

## How do you use it?
### Download the Latest Build:
* Go to the [releases page](https://github.com/thejudge156/MCNativeBuilder/releases)
* Download the latest release (the MCNativeBuilder.jar file).
* Place the jar file inside of a folder named ``MCNativeBuilder``

### Run the Downloaded Build:
* Open Command Prompt or Terminal.
* Type the command: ``java -jar path-to-downloaded-jar --version <version> --graalvm <path-to-graal>``.
* Example: ``java -jar C:/Downloads/MCNativeBuilder/MCNativeBuilder.jar --version 1.20.4 --graalvm C:/Downloads/GraalVM``.
* Follow the instructions in the console/terminal to compile the native image.

## How do you run the game?
### Locate the Executable:
* After the build is complete, navigate to the ``native-build folder``.
* Find the executable file for your version (e.g., ``1.20.4.exe``).

### Run the Game:
* Open Command Prompt or Terminal at the location of the executable.
* Type the command: ``your-version.exe --accessToken yourAccessToken --assetIndex 8 --username yourUsername --uuid yourUUID --version MCNative``.
* Example: ``1.20.4.exe --accessToken abc123 --assetIndex 8 --username TheJudge156 --uuid 12345 --version MCNative.``

## Troubleshooting
### Replacing Placeholders:
* When entering commands, ensure to replace placeholder text (e.g., ``<username>``) with your specific information.
* For instance, replace ``<username>`` with your actual username like ``TheJudge156``.

### Common Errors:
* Check if the file paths and version numbers are correctly entered.
* Ensure all placeholders are filled with your specific details.
