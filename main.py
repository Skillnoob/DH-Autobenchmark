import os
import subprocess

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


def accept_eula():
    eula_file = 'server/eula.txt'
    with open(eula_file, 'r') as file:
        lines = file.readlines()

    with open(eula_file, 'w') as file:
        for line in lines:
            if line.startswith('eula='):
                file.write('eula=true\n')
            else:
                file.write(line)


def run_benchmark(seed, cmd):
    pass


def main():
    ram_gb = int(input("How much RAM do you want to allocate to the server? (in GB): "))
    cmd = ["java", f"-Xmx{ram_gb}G", "-jar", "fabric-server.jar", "nogui"]

    if download_fabric():
        print("Fabric downloaded successfully, starting the server once and accepting EULA.")
        subprocess.run(cmd, cwd="server")
        accept_eula()

    download_dh()

    seed_set = [5057296280818819649, 2412466893128258733, 3777092783861568240, -8505774097130463405, 4753729061374190018]

    results = [run_benchmark(seed, cmd) for seed in seed_set]


if __name__ == "__main__":
    main()
