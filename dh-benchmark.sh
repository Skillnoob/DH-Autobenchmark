#!/bin/bash

# Source: https://stackoverflow.com/questions/59895/how-do-i-get-the-directory-where-a-bash-script-is-located-from-within-the-script
# gets the full directory name where the script is executed from no matter where it is called from
SOURCE=${BASH_SOURCE[0]}
while [ -L "$SOURCE" ]; do # resolve $SOURCE until the file is no longer a symlink
  DIR=$( cd -P "$( dirname "$SOURCE" )" >/dev/null 2>&1 && pwd )
  SOURCE=$(readlink "$SOURCE")
  [[ $SOURCE != /* ]] && SOURCE=$DIR/$SOURCE # if $SOURCE was a relative symlink, we need to resolve it relative to the path where the symlink file was located
done
DIR=$( cd -P "$( dirname "$SOURCE" )" >/dev/null 2>&1 && pwd )


## VARIABLES ##
# Do not manually edit
CONFIG="dh-automation.config"
SERVERPROPERTIES=${DIR}"/server.properties"
SCREEN="dh-automation-benchmark"
BUFFERCOUNTER="1"
RUN="1"
BENCHMARKTIME_SECONDS="do_not_edit"
RESULTSCSVFILE="${DIR}/benchmark-results.csv"
BUFFERFILE="${DIR}/dh-automation-screen-buffer.txt"
BACKUPHARDWAREINFORMATIONCSV="${DIR}/hardware-information.csv"
RUNTIMES=()
DBSIZES=()


## EXIT CODES ##
# 1 = Screen not installed
# 2 = wget and curl not installed
# 3 = folder "mods" could not be created
# 4 = EULA not accepted
# 5 = There is no Seed that could be set
# 6 = Not able to set threadPreset when server is running
# 7 = Server did not start correctly
# 8 = A File could not be deleted or overwritten
# 9 = Not able to delete world folder




# commandAvailable(command)
# Check whether the command $1 is available for execution
# Source: https://github.com/Griefed/ServerPackCreator/blob/7.1.3/serverpackcreator-api/src/main/resources/de/griefed/resources/server_files/default_template.sh
commandAvailable() {
  command -v "$1" >/dev/null 2>&1
}

# downloadIfNotExist(fileToCheck,fileToDownload,downloadURL)
# Checks whether file $1 exists. If not, then it is downloaded from $3 and stored as $2. Can be used in if-statements.
# Source: https://github.com/Griefed/ServerPackCreator/blob/7.1.3/serverpackcreator-api/src/main/resources/de/griefed/resources/server_files/default_template.sh
downloadIfNotExist() {
  if [[ ! -s "${1}" ]]; then

    echo "${1} could not be found." >&2
    echo "Downloading ${2}" >&2
    echo "from ${3}" >&2

    if commandAvailable curl ; then
      curl -# -L -o "${2}" "${3}"
    elif commandAvailable wget ; then
      wget --show-progress -O "${2}" "${3}"
    else
      echo "wget or curl is required to download files"
      exit 2
    fi

    if [[ -s "${2}" ]]; then
      echo "Download complete." >&2
      echo "true" >/dev/null
    else
      echo "false" >/dev/null
    fi

  else
    echo "${1} present." >&2
    echo "false" >/dev/null
  fi
}


# check if config file exists, otherwise create config file and fill it with necessary entries
configCheck() {
  if test -s ${DIR}/dh-automation.config 2>/dev/null
  then
      echo "Config file exists"
      read -r -p "Do you want to edit the config file? (Yes/No): " CONFIGANSWER
      if test ${CONFIGANSWER} == "Yes"
      then 
        nano ${DIR}/${CONFIG}
        echo "edited config file"
      else
        echo "Using available benchmark config!"
      fi
  else
      read -r -p "Do you want to edit the config file? (Yes/No): " CONFIGANSWER

      echo "Config file does not exist, creating..."
      echo "####################################################" >${CONFIG}
      echo "# DISTANT HORIZONS COMMUNITY AUTO-BENCHMARK-SCRIPT #" >>${CONFIG}
      echo "####################################################" >>${CONFIG}
      echo "" >>${CONFIG}
      echo "#-------------------" >>${CONFIG}
      echo "# SERVER SETTINGS #" >>${CONFIG}
      echo "#-------------------" >>${CONFIG}
      echo "" >>${CONFIG}
      echo "# RAM allocated to the server in GB. (Default: 8)" >>${CONFIG}
      echo 'ram_gb="8"' >>${CONFIG}
      echo "# Extra JVM arguments to pass to the server. (Default: None)" >>${CONFIG}
      echo 'extra_jvm_args=""' >>${CONFIG}
      echo "" >>${CONFIG}
      echo "#------------------" >>${CONFIG}
      echo "# WORLD SETTINGS #" >>${CONFIG}
      echo "#------------------" >>${CONFIG}
      echo "" >>${CONFIG}
      echo "# List of world seeds to use for the benchmark." >>${CONFIG}
      echo 'seeds=(5057296280818819649 2412466893128258733 3777092783861568240 -8505774097130463405 4753729061374190018)'>>${CONFIG}
      echo "" >>${CONFIG}
      echo "#---------------" >>${CONFIG}
      echo "# DH SETTINGS #" >>${CONFIG}
      echo "#---------------" >>${CONFIG}
      echo "" >>${CONFIG}
      echo "# This controls the Distant Horizons thread preset used when generating chunks. (Default: I_PAID_FOR_THE_WHOLE_CPU)" >>${CONFIG}
      echo "# Available Presets are: MINIMAL_IMPACT, LOW_IMPACT, BALANCED, AGGRESSIVE, I_PAID_FOR_THE_WHOLE_CPU." >>${CONFIG}
      echo 'thread_preset="I_PAID_FOR_THE_WHOLE_CPU"' >>${CONFIG}
      echo "# The radius in chunks of the area to generate around the center of the world. (Default: 256)" >>${CONFIG}
      echo 'generation_radius="256"' >>${CONFIG}
      echo "# The URL to download the Fabric server jar from." >>${CONFIG}
      echo 'fabric_download_url="https://meta.fabricmc.net/v2/versions/loader/1.21.1/0.16.12/1.0.3/server/jar"' >>${CONFIG}
      echo "# The URL to download the Distant Horizons mod jar from." >>${CONFIG}
      echo 'dh_download_url="https://cdn.modrinth.com/data/uCdwusMi/versions/jkSxZOJh/DistantHorizons-neoforge-fabric-2.3.2-b-1.21.1.jar"' >>${CONFIG}
      echo "Config created."

      if test ${CONFIGANSWER} == "Yes"
      then 
        nano ${DIR}/${CONFIG}
        echo "Using edited config file!"
      else
        echo "Using standardized benchmark config!"
      fi

fi
}

# check if screen is installed
screenCheck() {
  if commandAvailable screen
  then
      echo "Screen is installed" >/dev/null
  else
      echo "Screen is not installed, please install screen" 
      echo "via 'apt install screen'" 
      exit 1
  fi
}

serverPropertiesCreate() {
  echo "#Minecraft server properties
accepts-transfers=false
allow-flight=false
allow-nether=true
broadcast-console-to-ops=true
broadcast-rcon-to-ops=true
bug-report-link=
difficulty=easy
enable-command-block=false
enable-jmx-monitoring=false
enable-query=false
enable-rcon=false
enable-status=true
enforce-secure-profile=true
enforce-whitelist=false
entity-broadcast-range-percentage=100
force-gamemode=false
function-permission-level=2
gamemode=survival
generate-structures=true
generator-settings={}
hardcore=false
hide-online-players=false
initial-disabled-packs=
initial-enabled-packs=vanilla,fabric
level-name=world
level-type=minecraft\:normal
log-ips=true
max-chained-neighbor-updates=1000000
max-players=20
max-tick-time=60000
max-world-size=29999984
motd=A Minecraft Server
network-compression-threshold=256
online-mode=true
op-permission-level=4
player-idle-timeout=0
prevent-proxy-connections=false
pvp=true
query.port=25565
rate-limit=0
rcon.password=
rcon.port=25575
region-file-compression=deflate
require-resource-pack=false
resource-pack=
resource-pack-id=
resource-pack-prompt=
resource-pack-sha1=
server-ip=
server-port=61337
simulation-distance=10
spawn-animals=true
spawn-monsters=true
spawn-npcs=true
spawn-protection=16
sync-chunk-writes=true
text-filtering-config=
use-native-transport=true
view-distance=10
white-list=true" >${DIR}/server.properties
}

serverPropertiesCheck() {
  if test -s server.properties
  then
    echo "server.properties exists" >/dev/null
  else
    serverPropertiesCreate
  fi
}

benchmarkResultsCSVCreate() {
  echo "Run 1, Run 2, Run 3, Run 4, Run 5, DB Size Run 1, DB Size Run 2, DB Size Run 3, DB Size Run 4, DB Size Run 5" >${RESULTSCSVFILE}
}

downloadFabricAndDistantHorizons() {
  downloadIfNotExist ${DIR}/fabricserver.jar ${DIR}/fabricserver.jar ${fabric_download_url}
  
  if test -d ${DIR}/mods/
  then
    echo "mods folder exists" >/dev/null
  else
    echo "mods folder does not exist, creating folder..."
    if mkdir mods
    then
      echo 'folder "mods" created' >/dev/null
    else
      echo 'folder "mods" could not be created'
      exit 3
    fi
  fi

  downloadIfNotExist ${DIR}/mods/DistantHorizons-neoforge-fabric-2.3.2-b-1.21.1.jar ${DIR}/mods/DistantHorizons-neoforge-fabric-2.3.2-b-1.21.1.jar ${dh_download_url}
}

eulaCheck() {
  # Source: https://github.com/Griefed/ServerPackCreator/blob/7.1.3/serverpackcreator-api/src/main/resources/de/griefed/resources/server_files/default_template.sh
  if [[ ! -s "${DIR}/eula.txt" ]]
  then

    echo "Mojang's EULA has not yet been accepted. In order to run a Minecraft server, you must accept Mojang's EULA."
    echo "Mojang's EULA is available to read at https://aka.ms/MinecraftEULA"
    echo "If you agree to Mojang's EULA then type 'I agree'"
    echo -n "Response: "
    read -r ANSWER

    if [[ "${ANSWER}" == "I agree" ]]
    then
      echo "User agreed to Mojang's EULA."
      echo "#By changing the setting below to TRUE you are indicating your agreement to our EULA (https://aka.ms/MinecraftEULA)." >eula.txt
      echo "eula=true" >>eula.txt
    else
      echo "User did not agree to Mojang's EULA. Entered: ${ANSWER}. You can not run a Minecraft server unless you agree to Mojang's EULA."
      exit 4
    fi

  fi
}

screenStartServer() {
  echo "Starting Server!"
  screen -d -m -S ${SCREEN} java -Xmx${ram_gb}G ${extra_jvm_args} -jar ${DIR}/fabricserver.jar nogui &
}

setSeed() {
if test ${1} -le ${#seeds[@]} && test ${1} -gt 0
then
  echo "level-seed=${seeds[$((${1}-1))]}" >>${SERVERPROPERTIES}
else
  echo "Seed #${1} does not exist!"
  exit 5
fi
}

setThreadPreset() {
  if screen -S $SCREEN -X stuff "dh config common.threadPreset ${thread_preset}"^M
  then
    echo "Set Thread Preset to ${thread_preset}"
  else
    echo "Could not set common.threadPreset to ${thread_preset}"
    exit 6
  fi
  # Wait five seconds so that the server can set the thread preset
  sleep 5s
}

stopServer() {
  screen -S ${SCREEN} -X stuff "/stop^M"

sleep 3s
STOPCOUNTER="0"

while screen -ls | grep -w ${SCREEN} >/dev/null
do
  if test ${STOPCOUNTER} -ge 60
  then
    echo "Server did not stop gracefully, force closing server..."
    screen -S ${SCREEN} -X kill
  else
    echo "Waiting for Server to stop..."
  fi
    sleep 1s
done

  echo "Server stopped!"
}

worldDelete() {
  if test -d ${DIR}/world
  then
    echo "World folder detected, trying to delete..." >/dev/null
    if rm -r ${DIR}/world
    then
      echo "Successfully deleted previous world!" >/dev/null
    else
      echo "Could not delete previous world, exiting script..."
      exit 9
    fi
  else
    echo "No world folder detected!" >/dev/null
  fi
}

removeFileIfExists() {
  for FILE in $@
  do
    if test -s ${FILE}
    then
      echo "File ${FILE} exists, trying to delete..." >/dev/null
      if rm ${FILE}
      then
        echo "Successfully removed File ${FILE}" >/dev/null
      else
        echo "Could not delete File ${FILE}, trying to overwrite..."
        if echo "" >${FILE}
        then
          echo "Successfully overwritten File ${FILE}" >/dev/null
        else
          echo "Could not overwrite File ${FILE}, exiting script..."
          exit 8
        fi
      fi
    else
      echo "Could not find ${FILE}" >/dev/null
    fi
  done
}

## Main structure

# Check for dh-automation.config
configCheck
# Get variables from config file
source ${CONFIG}

# Check for screen, fabricserver.jar and Distant Horizons mod file 
screenCheck
downloadFabricAndDistantHorizons
eulaCheck

# Five runs (one for every seed)
while ! test ${RUN} -ge 6
do
  serverPropertiesCreate
  setSeed ${RUN}
  # Delete previous world
  worldDelete
  screenStartServer

  removeFileIfExists ${BUFFERFILE}

  # Wait for Server to fully start
  sleep 2s

  # Get a hardcopy from the screen before the first check to always have a file ready
  screen -S ${SCREEN} -X hardcopy ${BUFFERFILE}

  # Wait for server to succesfully start
  while ! grep -w "Done" <(tail -n 20 ${BUFFERFILE}) ${BUFFERFILE}
  do
    if test ${BUFFERCOUNTER} -lt 300
    then
      screen -S ${SCREEN} -X hardcopy ${BUFFERFILE}
      BUFFERCOUNTER=$((++BUFFERCOUNTER))
      sleep 1s
    else
      echo "Server did not start correctly, exiting script..."
      exit 7
    fi
  done

  setThreadPreset
  
  echo "Starting Pregen Run ${RUN} with radius ${generation_radius} for seed ${seeds[$((${RUN}-1))]}"
  if screen -S ${SCREEN} -X stuff "dh pregen start minecraft:overworld 0 0 ${generation_radius}"^M
  then
    screen -S ${SCREEN} -X hardcopy ${BUFFERFILE}
    while ! grep -w "Starting pregen" <(tail -n 20 ${BUFFERFILE}) ${BUFFERFILE}
    do
      screen -S ${SCREEN} -X hardcopy ${BUFFERFILE}
      sleep 1s
    done
    SECONDS="0"

    while ! grep -w "Pregen is complete" <(tail -n 20 ${BUFFERFILE}) ${BUFFERFILE} >/dev/null
    do
      screen -S ${SCREEN} -X hardcopy ${BUFFERFILE}
      tail -n 3 <${BUFFERFILE} | grep "\n"
      sleep 1s
    done

    BENCHMARKTIME_SECONDS=${SECONDS}
    echo "Pregen for Run ${RUN} completed in $(( (BENCHMARKTIME_SECONDS / 60) / 60 )) hours, $((BENCHMARKTIME_SECONDS / 60)) minutes and $((BENCHMARKTIME_SECONDS % 60)) seconds!"
    echo "Shutting down server"

    stopServer

    # Add benchmark times to RUNTIMES array (without "," when it is the last run)
    if test ${RUN} == "5"
    then
      RUNTIMES+=($(( (BENCHMARKTIME_SECONDS / 60)/60 )):$((BENCHMARKTIME_SECONDS / 60)):$((BENCHMARKTIME_SECONDS % 60)))
    else
      RUNTIMES+=($(( (BENCHMARKTIME_SECONDS / 60)/60 )):$((BENCHMARKTIME_SECONDS / 60)):$((BENCHMARKTIME_SECONDS % 60))",")
    fi
    
    # Add db sizes to DBSIZES array (without "," when it is the last run)
    DBSIZE=$(stat -c '%s' ${DIR}/world/data/DistantHorizons.sqlite)
    DBSIZEMB=$((DBSIZE / (1024*1024)))
    if test ${RUN} == "5"
    then
      DBSIZES+=(${DBSIZEMB}"MB")
    else
      DBSIZES+=(${DBSIZEMB}"MB,")
    fi
    echo "The DB has a size of ${DBSIZEMB}MB"
  fi  
  RUN=$((++RUN)) 
done

benchmarkResultsCSVCreate

# Add the time of every run to the main csv file
if echo ${RUNTIMES[@]} >>${RESULTSCSVFILE}
then
  echo "Added run times to main csv file!"
else
  echo "Could not add run times to main csv file."
  echo "Please provide this information when submitting:"
  echo ${RUNTIMES[@]}
fi

# Add the db size of every run to the main csv file
if echo ${DBSIZES[@]} >>${RESULTSCSVFILE}
then
  echo "Added run times to main csv file!"
else
  echo "Could not add run times to main csv file."
  echo "Please provide this information when submitting:"
  echo ${DBSIZES[@]}
fi


# Hardware Information #
HARDWAREINFORMATION=()
# Data preparation
echo "Collecting CPU information..."
CPU=$(lscpu | grep 'Model name' | cut -f 2 -d ":" | awk '{$1=$1}1')
CPUCORES=$(lscpu | awk '/^Socket\(s\):/ {sockets=$2} /^Core\(s\) per socket:/ {cores=$4} END {print sockets * cores}')
CPUTHREADS=$(nproc --all)
echo "CPU: ${CPU} ${CPUCORES}C/${CPUTHREADS}T"
# Add CPU information to HARDWAREINFORMATION array
HARDWAREINFORMATION+=("${CPU} ${CPUCORES}C/${CPUTHREADS}T,")

read -r -p "The following commands have to be executed with elevated privileges to get the full data automatically, you may be asked multiple times, do you agree? (Yes/No): " ANSWERHARDWAREDATA
if test ${ANSWERHARDWAREDATA} == "Yes"
then
  echo 'Please make sure that the package "dmidecode" is installed with your linux system otherwise the following data collection will not work.'
  sleep 3s
  # Collect RAM information
  echo "Collecting RAM information..."
    RAMSIZEONCE=$(sudo dmidecode -t memory | grep -w "Size" | grep -w "GB" | cut -d " " -f 2 | head -n 1)
    RAMSTICKSDOUBLE=$(sudo dmidecode -t memory | grep -w "Size" | grep -w "GB" | wc -l)
    RAMSTICKS=$((${RAMSTICKSDOUBLE} / 2))
  if RAMSIZETOTAL=$((${RAMSIZEONCE} * ${RAMSTICKS})) \
    && RAMTYPE=$(sudo dmidecode -t memory | grep "DDR" | cut -d " " -f 2 | head -n 1) \
    && RAMSPEED=$(sudo dmidecode -t memory | grep "Configured Memory Speed" | cut -d " " -f 4,5 | head -n 1) 
  then
    echo "RAM: ${RAMSIZETOTAL}GB ${RAMTYPE} ${RAMSPEED}"
    # Add RAM information to HARDWAREINFORMATION array
    HARDWAREINFORMATION+=("${RAMSIZETOTAL}GB ${RAMTYPE} ${RAMSPEED},")
  else
    echo "RAM information could not be collected!"
  fi
  # Collect disk information
  echo "Collecting Disk information..."
    DISKPARTITION=$(df ${DIR} | grep dev | cut -d " " -f 1 | cut -d "/" -f 3)
    DISKID=$(ls -l /dev/disk/by-id | grep ${DISKPARTITION} | cut -d " " -f 9 | head -n 1 | cut -d "-" -f 1,2)
    DISKMOUNT=$(ls -l /dev/disk/by-id | grep ${DISKID} | head -n 1 | cut -d ">" -f 2 | cut -d "/" -f 3)
  if DISKMODEL=$(sudo smartctl -a /dev/${DISKMOUNT} | grep "Model Number" | tr -s " " | cut -d ":" -f 2 | tr -d " ") \
    && DISKSIZE=$(sudo smartctl -a /dev/${DISKMOUNT} | grep "Capacity" | head -n 1 | cut -d "[" -f 2 | cut -d " " -f 1)
  then
    echo "DISK: ${DISKMODEL} ${DISKSIZE}GB"
    # Add disk information to HARDWAREINFORMATION array
    HARDWAREINFORMATION+=("${DISKMODEL} ${DISKSIZE}GB")
  else
    echo "Disk information could not be collected!"
  fi


  # Add hardware information to main csv file with a gap-row
  if echo " " >>${RESULTSCSVFILE} && echo "CPU, RAM, DRIVE" >>${RESULTSCSVFILE} && echo ${HARDWAREINFORMATION[@]} >>${RESULTSCSVFILE}
  then
    echo "Hardware information added to main csv file!"
    echo "Please provide ${RESULTSCSVFILE} when submitting!"
  else
    echo "Adding Hardware information to separate csv file"
    if echo "CPU, RAM, DRIVE" >>${BACKUPHARDWAREINFORMATIONCSV} && echo ${HARDWAREINFORMATION[@]} >>${BACKUPHARDWAREINFORMATIONCSV}
    then
      echo "Added hardware information to separate csv file"
    else
      echo "Could not add hardware information to separate csv file, please provide this information when submitting:"
      echo "${CPU} ${CPUCORES}C/${CPUTHREADS}T, ${RAMSIZETOTAL}GB ${RAMTYPE} ${RAMSPEED}, ${DISKMODEL} ${DISKSIZE}GB"
    fi
    echo "Could not add hardware information to main csv file, please provide both ${BACKUPHARDWAREINFORMATIONCSV} and ${RESULTSCSVFILE} when submitting!"
  fi
else
  echo 'The user did not accept, please use the command "dmidecode -t memory" and "smartctl -a /dev/YOURDRIVE" with elevated privileges to get the information needed for submitting.'
fi

echo "-----------------------"
echo "- The Script finished -"
echo "-----------------------"


