# Android GNSS Logger to RINEX converter

A tool that converts logs from Android GNSS measurement tools to RINEX (version 3.03).

## Requirements
To build and run this project, you need the following:

* **Java Development Kit (JDK):** Version 22 (Recommended: Oracle OpenJDK 22)
    * *Note: The project's language level is configured to Java 16 compatibility.*
* **Build Tool:** Gradle (configured via the included Gradle Wrapper)

## Usage

This converter processes raw GNSS measurement data collected from Android devices and transforms it into the standard RINEX format.

### Source Data
To collect compatible raw data logs from an Android device, use the official Google GNSS Measurement application. The source code and installation instructions for the mobile application can be found in the following repository:
[https://github.com/google/gps-measurement-tools](https://github.com/google/gps-measurement-tools).

The mobile application generates a raw text file containing the navigation and satellite measurement parameters.

### RINEX Formatting

Converting data to the RINEX provides a standardized, platform-independent formatting that enables further post-processing of the satellite measurements from any device for high-precision positioning.

### How to use
Clone the repository and execute the application using the Gradle Wrapper from your terminal.

#### Run application:
```bash
.\gradlew run --args="<input_file_path> -o <output_file> [flags]"
```

#### Command Line Flags
The application supports specific execution flags to control time synchronization and clock stabilization parameters during the conversion process:

* **-o, --output** (Required): Specifies the path and name for the generated output RINEX file.
* **-i, --integerize**: Rounds the epoch timestamps to the closest integer second and applies adjustment corrections to the pseudoranges using the PseudoRangeRateMeterPerSecond values.
* **-b, --fixedBias**: Maintains a fixed FullBiasNanos value throughout the conversion execution rather than applying instantaneous values. This constraint eliminates the 256 ns clock jumps every 3 seconds that trigger code-phase divergence issues.

#### File Extensions
* **Input File:** Plain text log files with a `.txt` extension.
* **Output File:** Standardized RINEX observation files. The recommended extension format follows the traditional RINEX convention (e.g., `.17o` for observation data collected in the year 2017).
