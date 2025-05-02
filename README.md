# Distant Horizons Auto Benchmark
This is a benchmarking tool for the [Distant Horizons](https://modrinth.com/mod/distanthorizons) mod,
meant to be used for the [Distant Horizons Community Benchmarks](https://docs.google.com/spreadsheets/d/1lFO4bC4OhHHCC6eqGjNbNlcj6OotxNAJ4mKBT396Wx8/edit?gid=703766985#gid=703766985).
The program automatically runs pregeneration tasks using the Distant Horizons server functionality on a set of seeds and collects the results in a csv.\
This project is in no way affiliated with the developers of [Distant Horizons](https://modrinth.com/mod/distanthorizons).

# Running the Application

## JAR - All Platforms (Recommended)
The config file is named `dh-benchmark.toml` and is located in the same directory as the JAR file. It gets generated the first time the JAR is run.
1. **Install Java**\
   Make sure you have JDK 21 installed. You can download it from the [Adoptium website](https://adoptium.net/temurin/releases/?version=21), or use a package manager like [sdkman](https://sdkman.io/) (Linux) or [brew](https://brew.sh/) (MacOS).
2. **Download the JAR File**\
   Download the latest JAR file from the [releases page](https://github.com/Skillnoob/DH-Autobenchmark/releases)
3. **Run the JAR File**\
   Open a terminal and navigate to the directory where you downloaded the JAR file. Then run:
   ```bash
    java -jar DH-Autobenchmark-x.x.jar
   ```
4. **Follow the Instructions**\
   The application will guide you through the process of running the benchmark.
5. **View Results**\
   After the benchmark is complete, you can view the results in the `benchmark-results.csv`.
6. **Hardware Information**\
   Run the following command to append hardware information to the result file:
    ```bash
    java -jar DH-Autobenchmark-x.x.jar --collect-hardware-info
    ```
   If you are on Linux, you need to run this command with `sudo` to collect hardware information:
   ```bash
    sudo java -jar DH-Autobenchmark-x.x.jar --collect-hardware-info
   ```

## Bash Script - Linux only
The config file is named `dh-automation.config` and is located in the same directory as the script. It gets generated the first time the script is run, but the script will ask if you want to edit it.
1. **Install screen**\
   Make sure you have `screen` installed. You can install it using your package manager.\
   Debian/Ubuntu:
   ```bash
    sudo apt-get install screen
   ```
   Fedora:
   ```bash
    sudo dnf install screen
   ```
   On Arch, due to a bug you will have to downgrade `screen`:
    ```bash
     sudo pacman -S screen downgrade
     sudo downgrade screen
    ```
   Select `4.9.1` and then select `y` when it asks to add it to the IgnorePkg.
2. **Download the Bash Script**\
   Download the `dh-benchmark.sh` script from
   the [releases page](https://github.com/Skillnoob/DH-Autobenchmark/releases)
3. **Make the Script Executable**\
   Open a terminal and navigate to the directory where you downloaded the script. Then run:
   ```bash
    chmod +x dh-autobenchmark.sh
   ```
4. **Run the Script**\
   Execute the script with the following command:
    ```bash
     ./dh-autobenchmark.sh
    ```
5. **Follow the Instructions**\
   The application will guide you through the process of running the benchmark.

# Building from Source

1. **Install Java**\
   Make sure you have JDK 21 installed. You can download it from the [Adoptium website](https://adoptium.net/temurin/releases/?version=21), or use a package manager like [sdkman](https://sdkman.io/) or [brew](https://brew.sh/).
2. **Clone the Repository**\
   Open a terminal and run the following command to clone the repository:
   ```bash
   git clone https://github.com/Skillnoob/DH-Autobenchmark.git
   ```
3. **Navigate to the Project Directory**\
   Change to the project directory:
   ```bash
   cd DH-Autobenchmark
   ```
4. **Build the Project**\
   Use Gradle to build the project:
   ```bash
   ./gradlew build
   ```
   On Windows, use:
   ```cmd
   gradlew.bat build
   ```
5. **Run the Application**\
   After building, you can find the jar in `build/libs/`. To run the application, use:
   ```bash
    java -jar build/libs/DH-Autobenchmark-x.x.jar
   ```