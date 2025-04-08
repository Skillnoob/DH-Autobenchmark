import datetime
import os
import shutil
import subprocess
import time

import requests

# Paths
SERVER_DIR = "server"
MODS_DIR = os.path.join(SERVER_DIR, "mods")
WORLD_DIR = os.path.join(SERVER_DIR, "world")
DATA_DIR = os.path.join(WORLD_DIR, "data")

# Files
FABRIC_JAR = "fabric-server.jar"
DH_JAR = "distant-horizons.jar"
SERVER_PROPERTIES_FILE = os.path.join(SERVER_DIR, "server.properties")
EULA_FILE = os.path.join(SERVER_DIR, "eula.txt")
DH_DB_FILE = os.path.join(DATA_DIR, "DistantHorizons.sqlite")

# URLs
FABRIC_DOWNLOAD_URL = "https://meta.fabricmc.net/v2/versions/loader/1.21.1/0.16.12/1.0.3/server/jar"
DH_DOWNLOAD_URL = "https://cdn.modrinth.com/data/uCdwusMi/versions/jkSxZOJh/DistantHorizons-neoforge-fabric-2.3.2-b-1.21.1.jar"


def ensure_directory_exists(directory_path: str) -> None:
    """
    Ensure that the given directory exists
    """
    if not os.path.exists(directory_path):
        os.makedirs(directory_path)


def download_file(url: str, save_path: str, description: str) -> bool:
    """
    Download a file from a given URL if it does not already exist
    """
    if os.path.exists(save_path):
        print(f"{description} already exists. Skipping download.")
        return False

    print(f"Downloading {description} from {url}.")
    response = requests.get(url)
    if response.status_code == 200:
        with open(save_path, "wb") as f:
            f.write(response.content)
        return True
    else:
        print(f"Failed to download {description} (status code {response.status_code}).")
        return False


def download_fabric() -> bool:
    """
    Download the Fabric server jar file if it does not already exist
    """
    ensure_directory_exists(SERVER_DIR)
    fabric_path = os.path.join(SERVER_DIR, FABRIC_JAR)
    return download_file(FABRIC_DOWNLOAD_URL, fabric_path, "Fabric server jar")


def download_distant_horizons() -> None:
    """
    Download the Distant Horizons mod jar file if it does not already exist
    """
    ensure_directory_exists(MODS_DIR)
    dh_path = os.path.join(MODS_DIR, DH_JAR)
    download_file(DH_DOWNLOAD_URL, dh_path, "Distant Horizons mod")


def update_config_line(file_path: str, line_prefix: str, new_line: str) -> None:
    """
    Replace a line in a configuration file that starts with a specific prefix
    """
    with open(file_path, "r") as file:
        lines = file.readlines()

    with open(file_path, "w") as file:
        for line in lines:
            if line.startswith(line_prefix):
                file.write(new_line + "\n")
            else:
                file.write(line)


def send_command(server_process: subprocess.Popen, command: str) -> None:
    """
    Send a command to the running server process
    """
    server_process.stdin.write(command + "\n")
    server_process.stdin.flush()


def run_benchmark(seed: int, cmd: list) -> list:
    """
    Run a Distant Horizons benchmark with a specific seed
    """
    # Remove the previous world directory
    if os.path.exists(WORLD_DIR):
        shutil.rmtree(WORLD_DIR)

    # Select the specific seed
    update_config_line(SERVER_PROPERTIES_FILE, "level-seed", f"level-seed={seed}")

    # Start the server
    server_process = subprocess.Popen(cmd,
                                      stdin=subprocess.PIPE,
                                      stdout=subprocess.PIPE,
                                      stderr=subprocess.STDOUT,
                                      text=True,
                                      cwd=SERVER_DIR)

    # Wait for the server to finish startup
    for line in server_process.stdout:
        print(line, end="")
        if "Done" in line:
            break

    time.sleep(5)  # Wait until everything initialized

    send_command(server_process, "dh config common.threadPreset I_PAID_FOR_THE_WHOLE_CPU")
    time.sleep(1)
    send_command(server_process, "dh pregen start minecraft:overworld 0 0 64")

    # Track pre-generation time
    benchmark_start = None
    elapsed_time = ""
    for line in server_process.stdout:
        print(line, end="")

        if "Starting pregen" in line:
            print("Pregen started.")
            benchmark_start = time.perf_counter()

        if "Pregen is complete" in line:
            if benchmark_start is not None:
                elapsed_seconds = round(time.perf_counter() - benchmark_start)
                elapsed_time = str(datetime.timedelta(seconds=elapsed_seconds))

            print("Pregen completed, shutting down server.")
            break

    time.sleep(1)
    send_command(server_process, "stop")
    server_process.wait()

    # Get the file size of the Distant Horizons database
    dh_db_size = os.path.getsize(DH_DB_FILE) if os.path.exists(DH_DB_FILE) else 0

    return [elapsed_time, dh_db_size]


def main():
    """
    Main function to run the entire benchmark workflow
    """
    try:
        ram_gb = int(input("How much RAM do you want to allocate to the server? (in GB): "))
    except ValueError:
        print("Invalid input for RAM. Please enter an integer value.")
        return

    server_cmd = ["java", f"-Xmx{ram_gb}G", "-jar", FABRIC_JAR, "nogui"]

    # Download Fabric if needed and accept the EULA as well as enabling the white-list to prevent anyone joining
    if download_fabric():
        print("Fabric downloaded successfully. Starting the server once to accept the EULA.")
        subprocess.run(server_cmd, cwd=SERVER_DIR)
        update_config_line(EULA_FILE, "eula", "eula=true")
        update_config_line(SERVER_PROPERTIES_FILE, "white-list", "white-list=true")

    download_distant_horizons()

    seed_list = [5057296280818819649,
                 2412466893128258733,
                 3777092783861568240,
                 -8505774097130463405,
                 4753729061374190018]

    results = [run_benchmark(seed, server_cmd) for seed in seed_list]

    # Print benchmark results
    for i, (elapsed, db_size) in enumerate(results):
        db_size_mb = db_size / (1024 * 1024)
        print(f"Seed {seed_list[i]}: Elapsed Time: {elapsed}, Database Size: {db_size_mb:.2f} MB")


if __name__ == "__main__":
    main()
