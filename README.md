# StonecutterFix 🪚

A lightweight Paper plugin that fixes the **Stonecutter UI glitch** for Bedrock Edition players connected via Geyser/Floodgate.

![Java CI](https://img.shields.io/badge/Java-21-orange)
![Platform](https://img.shields.io/badge/Platform-Paper%201.21-blue)
![Geyser](https://img.shields.io/badge/Support-GeyserMC-green)

## 🛑 The Problem
Bedrock players often experience a bug where the Stonecutter menu resets or glitches when attempting to craft multiple items or using shift-click (Geyser Issue #5195). This is caused by packet desync between the Bedrock client and Java server logic regarding the Stonecutter UI.

## ✅ The Solution
**StonecutterFix** detects Bedrock players and replaces the vanilla Stonecutter interface with a **Chest GUI** (54 slots).
* **For Java Players:** The game remains 100% vanilla (standard Stonecutter UI).
* **For Bedrock Players:** A custom Chest GUI opens, allowing stable mass-crafting and shift-clicking without resets.

## ✨ Features
* 🚀 **Auto-Detection:** Uses Geyser and Floodgate APIs to correctly identify Bedrock players.
* 📦 **Chest GUI:** Replaces the unstable Stonecutter UI with a stable Chest inventory.
* ⚡ **Shift-Click Support:** Allows crafting entire stacks instantly.
* 🛠️ **Native Recipes:** Dynamically loads all Stonecutter recipes from the server.
* 🔒 **Safe:** Returns input items to the player if the inventory is closed.

## 📋 Requirements
* **Java:** JDK 21 or higher.
* **Server:** Paper 1.21.x (tested on 1.21.11).
* **Dependencies:**
    * [Geyser](https://geysermc.org/)
    * [Floodgate](https://geysermc.org/) (Recommended for better detection)

## 📥 Installation
1.  Download the latest `StonecutterFix-1.0.0.jar` from Releases.
2.  Place the file into your server's `plugins/` folder.
3.  Restart the server.
4.  **Done!** Bedrock players will now see the Chest UI when using a Stonecutter.

## ⚙️ Permissions
| Permission | Description | Default |
| :--- | :--- | :--- |
| `stonecutterfix.use` | Allows the player to use the fixed GUI. | `true` (everyone) |

## 🏗️ Building from Source
If you want to modify the code or build it yourself:

1.  Clone the repository:
    ```bash
    git clone [https://github.com/YourUsername/StonecutterFix.git](https://github.com/YourUsername/StonecutterFix.git)
    ```
2.  Open the project in VS Code or IntelliJ IDEA.
3.  Ensure you have **Maven** and **JDK 21** installed.
4.  Run the build command:
    ```bash
    mvn clean package
    ```


    I am preparing a translation into English. :)
5.  The compiled `.jar` will be in the `target/` folder.

## 🤝 Contributing
Feel free to open issues or pull requests if you find any bugs or have suggestions for improvements.

## 📄 License
This project is licensed under the MIT License.
