# Distant Horizons Benchmark Tool

This project is a tool mainly meant to automate benchmarking Distant Horizons on different configurations.

## Requirements

1. **Java Development Kit (JDK)**  
   Make sure you have JDK 17 or newer installed. You can check your Java version by running:
   ```bash
    java -version
   ```

2. **Git**  
   Required to clone the repository.

## Steps to Clone and Build the Project

1. **Clone the Repository**  
   Open a terminal and run the following command to clone the repository:
   ```bash
   git clone https://github.com/Skillnoob/DH-Autobenchmark.git
   ```

2. **Navigate to the Project Directory**
   ```bash
   cd DH-Autobenchmark
   ```

3. **Build the Project**  
   Use the Gradle to build the project:
   ```bash
   ./gradlew build
   ```
   On Windows, use:
   ```cmd
   gradlew.bat build
   ```

4. **Run the Application**  
   After building, you can find the jar in `build/libs/`. To run the application, use:
   ```bash
    java -jar build/libs/DH-Autobenchmark-1.0.jar
   ```

## Notes

- The project uses **NightConfig** for configuration management. The first time you run the application, it will create a configuration file named `dh-benchmark.toml` in the current directory. You can modify this file to change the default settings.
- Benchmark results will be output to the console and saved in a CSV file named `benchmark-results.csv` in the current directory.
