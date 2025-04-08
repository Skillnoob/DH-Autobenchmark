import datetime
import os
import shutil
import subprocess
import time

import requests


def download_fabric():
    path = "server/"
    name = "fabric-server.jar"

    if not os.path.exists(path):
        os.makedirs(path)

    save_path = os.path.join(path, name)

    if os.path.exists(save_path):
        print(f"Fabric already exists. Skipping download.")
        return False

    print(f"Downloading Fabric from fabricmc.")
    response = requests.get("https://meta.fabricmc.net/v2/versions/loader/1.21.1/0.16.12/1.0.3/server/jar")

    with open(save_path, "wb") as f:
        f.write(response.content)

    return True


def download_dh():
    path = "server/mods/"
    name = "distant-horizons.jar"

    save_path = os.path.join(path, name)

    if os.path.exists(save_path):
        print(f"Distant Horizons already exists. Skipping download.")
        return

    print(f"Downloading Distant Horizons from Modrinth.")
    response = requests.get("https://cdn.modrinth.com/data/uCdwusMi/versions/jkSxZOJh/DistantHorizons-neoforge-fabric-2.3.2-b-1.21.1.jar")

    with open(save_path, "wb") as f:
        f.write(response.content)


def replace_line(file_path, line_to_replace, new_line):
    with open(file_path, 'r') as file:
        lines = file.readlines()

    with open(file_path, 'w') as file:
        for line in lines:
            if line.startswith(line_to_replace):
                file.write(f'{new_line}\n')
            else:
                file.write(line)


def send_command(server_process, command):
    server_process.stdin.write(command + "\n")
    server_process.stdin.flush()


def run_benchmark(seed, cmd):
    world_path = "server/world/"
    if os.path.exists(world_path):
        shutil.rmtree(world_path)

    replace_line('server/server.properties', 'level-seed=', f'level-seed={seed}')

    server_process = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, cwd='server')

    for line in server_process.stdout:
        print(line, end='')

        if "Done" in line:
            break

    time.sleep(5)

    send_command(server_process, 'dh config common.threadPreset I_PAID_FOR_THE_WHOLE_CPU')
    time.sleep(1)
    send_command(server_process, 'dh pregen start minecraft:overworld 0 0 64')

    begin_time = 0
    elapsed_time = 0
    for line in server_process.stdout:
        print(line, end='')

        if "Starting pregen" in line:
            print("Pregen started.")
            begin_time = time.perf_counter()

        if "Pregen is complete" in line:
            elapsed_time = str(datetime.timedelta(seconds=round(time.perf_counter() - begin_time, 2))).split('.')[0]
            print("Pregen completed, shutting down server.")
            break

    time.sleep(1)
    send_command(server_process, 'stop')

    # wait until the server stops
    server_process.wait()

    # get file size of server/world/data/DistantHorizons.sqlite
    db_size = os.path.getsize("server/world/data/DistantHorizons.sqlite")

    return [elapsed_time, db_size]


def main():
    ram_gb = int(input("How much RAM do you want to allocate to the server? (in GB): "))
    cmd = ["java", f"-Xmx{ram_gb}G", "-jar", "fabric-server.jar", "nogui"]

    if download_fabric():
        print("Fabric downloaded successfully, starting the server once and accepting EULA.")
        subprocess.run(cmd, cwd="server")
        replace_line("server/eula.txt", "eula=", "eula=true")

    download_dh()

    seed_set = [5057296280818819649, 2412466893128258733, 3777092783861568240, -8505774097130463405, 4753729061374190018]

    results = [run_benchmark(seed, cmd) for seed in seed_set]

    for i, (elapsed_time, db_size) in enumerate(results):
        print(f"Seed {seed_set[i]}: Elapsed Time: {elapsed_time}, Database Size: {db_size / (1024 * 1024):.2f} MB")


if __name__ == "__main__":
    main()
