#!/usr/bin/env bash

# Source: https://stackoverflow.com/questions/59895/how-do-i-get-the-directory-where-a-bash-script-is-located-from-within-the-script
# gets the full directory name where the script is executed from no matter where it is called from
SOURCE=${BASH_SOURCE[0]}
while [ -L "$SOURCE" ]; do # resolve $SOURCE until the file is no longer a symlink
  DIR=$( cd -P "$( dirname "$SOURCE" )" >/dev/null 2>&1 && pwd )
  SOURCE=$(readlink "$SOURCE")
  [[ $SOURCE != /* ]] && SOURCE=$DIR/$SOURCE # if $SOURCE was a relative symlink, we need to resolve it relative to the path where the symlink file was located
done
DIR=$( cd -P "$( dirname "$SOURCE" )" >/dev/null 2>&1 && pwd )

checkCTRLC() {
  if test ${QUIT} == "Yes"
  then
    echo -e "\nCTRL+C detected, shutting down Server and exiting script!\n" >&2
    echo "If you want to exit the script immediatly, press CTRL+Z, you will need to kill the server yourself though." >&2
    stopServer
    echo "Exiting script"
    exit 100
  fi
}

# Check if CTRL+C has been pressed and if yes, set variable 'QUIT' to 'Yes'
trap 'QUIT=Yes' INT

## VARIABLES ##
# Do not manually edit
# This accounts as the "totalState" variable for the ProgressBar function
_end=100
# Start value for the ProgressBar function
_start=0
BACKUPHARDWAREINFORMATIONCSV="${DIR}/hardware-information.csv"
BENCHMARKTIME_SECONDS_MILLISECONDS="do_not_edit"
BENCHMARKTIME_SUM="0"
BUFFERCOUNTER="1"
CPS_AVERAGE="0"
CPS_TOTALAVERAGE="0"
CUSTOMDATAPACKFOLDER="${DIR}/custom_datapacks"
CONFIG="dh-benchmark.toml"
DATAPACKFOLDER="${DIR}/server/world/datapacks"
DBSIZE_SUM="0"
ETA_HOUR="0"
ETA_MINUTE="0"
ETA_SECONDS="0"
EULAFILE="${DIR}/server/eula.txt"
FINISHED="false"
LATESTLOG="${DIR}/server/logs/latest.log"
PROGRESS="false"
PROGRESSFILE="${DIR}/benchmark-progress.txt"
QUIT="NO"
RESULTSCSVFILE="${DIR}/benchmark-results.csv"
RUN="1"
SERVERPROPERTIES="${DIR}/server/server.properties"
SCREEN="dh-automation-benchmark"
WORLDFOLDER="${DIR}/server/world"

## Arrays ##
ARRAYDEVTYPES=(ata scsi nvme sat usbcypress usbjmicron usbprolific usbsunplus sntasmedia sntjmicron sntrealtek auto)
CPS=()
CPS_CSV=()
DBSIZES=()
RUNTIMES=()



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
# 10 = Folder "server" could not be created, check if you have valid permissions
# 11 = Config file could not be created
# 12 = Invalid Answer in configCheck
# 13 = Not able to use any editor to edit the config file
# 14 = Clean progress file could not be created
# 100 = CTRL+C detected, shut down server and exited script






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

    if test ${debug_mode} == "true" 2>/dev/null
      then
        echo "${1} could not be found." >&2
        echo "Downloading ${2}" >&2
        echo "from ${3}" >&2 
        sleep 1s
      else
      echo "Downloading ${2}" >&2
    fi
    

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
      if test ${debug_mode} == "true" 2>/dev/null
      then
        echo "true" 
        sleep 1s
      fi
    else
      if test ${debug_mode} == "true" 2>/dev/null
      then
        echo "false" 
        sleep 1s
      fi
    fi

  else
    if test ${debug_mode} == "true" 2>/dev/null
    then
      echo "${1} present."
      echo "false" 
      sleep 1s
    fi
  fi
}


createConfig(){
  echo '#################################################### 
# DISTANT HORIZONS COMMUNITY AUTO-BENCHMARK-SCRIPT # 
#################################################### 


#-------#
# DEBUG #
#-------#

# Debug mode: enables verbose output which helps debugging potential bugs
# true / false
# Default: false
debug_mode="false"


#-----------------# 
# SERVER SETTINGS #
#-----------------#
 
# RAM allocated to the server in GB. 
# Default: 8
ram_gb="8"

# Extra JVM arguments to pass to the server.
# Example: "-XX:+UseZGC -XX:AllocatePrefetchStyle=1 -XX:-ZProactive"
# Note: You do not need to include the -Xmx flag here, it is set automatically based on the ram_gb config value.
# Default: None
extra_jvm_args=""
 

#----------------#
# WORLD SETTINGS #
#----------------#
 
# List of world seeds to use for the benchmark. 
seeds=(5057296280818819649 2412466893128258733 3777092783861568240 -8505774097130463405 4753729061374190018)


#--------------- 
# DH SETTINGS # 
#--------------- 
 
# This controls the Distant Horizons thread preset used when generating chunks. 
# Available Presets are: MINIMAL_IMPACT, LOW_IMPACT, BALANCED, AGGRESSIVE, I_PAID_FOR_THE_WHOLE_CPU. 
# Default: Default: I_PAID_FOR_THE_WHOLE_CPU
thread_preset="I_PAID_FOR_THE_WHOLE_CPU" 
# The radius in chunks of the area to generate around the center of the world.
# Default: 256
generation_radius="256"
# The URL to download the Fabric server jar from.
fabric_download_url="https://meta.fabricmc.net/v2/versions/loader/1.21.1/0.17.2/1.1.0/server/jar" 
# The URL to download the Distant Horizons mod jar from. 
dh_download_url="https://cdn.modrinth.com/data/uCdwusMi/versions/P14vqscU/DistantHorizons-2.3.4-b-1.21.1-fabric-neoforge.jar"' >${CONFIG}
}


# check if config file exists, otherwise create config file and fill it with necessary entries
configCheck() {
  if test -s ${DIR}/${CONFIG} 2>/dev/null
  then
    if test ${PROGRESS} == "true"
    then
      if test ${debug_mode} == "true" 2>/dev/null
      then
        echo "Config file exists and progress is available, using available config"
      fi
    else
      # Config file exists
      read -r -p "Do you want to edit the current config, keep the current config or do a standardized run? (edit/keep/standard): " CONFIGANSWER

      case ${CONFIGANSWER} in
        Edit | edit)
          if test ${debug_mode} == "true" 2>/dev/null
          then
            echo "Using locally set editor" 
            sleep 1s
          fi
          if "${EDITOR:-nano}" ${CONFIG}
          then
            echo "Using edited config for the benchmark"
          else
            if test ${debug_mode} == "true" 2>/dev/null
            then
              echo "Using nano as the editor" 
              sleep 1s
            fi
            if nano ${CONFIG}
            then
              echo "Using edited config for the benchmark"
            else
              echo "Could not use any editor for the config"
              echo "Exiting script.."
              exit 13
            fi
          fi
        ;;
        Keep | keep)
          echo "Using current available config"
        ;;
        Standard | standard)
          echo "Using standardized config!"
          createConfig
        ;;
        *)
          echo "Invalid Answer: ${CONFIGANSWER}"
          exit 12
        ;;
      esac
    fi
  else
    if test ${PROGRESS} == "true"
    then
      read -r -p "There is progress but no config file, would you like to redo the config or start a new run? (r/n) " PROGRESSCONFIGANSWER

      case ${PROGRESSCONFIGANSWER} in
        R | r)
          if createConfig
          then
            # Debug output
            if test ${debug_mode} == "true" 2>/dev/null
            then
              echo "Config file created" 
            fi
            
            # Debug output
            if test ${debug_mode} == "true" 2>/dev/null
            then
              echo "Using locally set editor" 
              sleep 1s
            fi

            if "${EDITOR:-vi}" ${CONFIG}
            then
              echo "Using edited config for the benchmark"
            else
              if test ${debug_mode} == "true" 2>/dev/null
              then
                echo "Using nano as the editor" 
                sleep 1s
              fi
              if nano ${CONFIG}
              then
                echo "Using edited config for the benchmark"
              else
                echo "Could not use any editor for the config"
                echo "Exiting script.."
                exit 13
              fi
            fi
          else
            echo "Config could not be created!"
            exit 11
          fi
        ;;
        N | n)
          if createConfig
          then
            # Debug output
            if test ${debug_mode} == "true" 2>/dev/null
            then
              echo "Config file created"
            fi
          else
            echo "Config could not be created!"
            exit 11
          fi

          if createEmptyProgressFile
          then
            # Debug output
            if test ${debug_mode} == "true" 2>/dev/null
            then
              echo "Created clean progress file" 
            fi
          fi
          RUN="1"
        ;;
      esac
    else
      if test ${debug_mode} == "true" 2>/dev/null
      then
        echo "Config file does not exist, creating fresh config file" 
        sleep 1s
      fi
  
      if createConfig
      then
        # Debug output
        if test ${debug_mode} == "true" 2>/dev/null
        then
          echo "Config file created" 
        fi
        
        # User input
        read -r -p "Do you want to edit the config and do a custom run? (Y/N): " CONFIGANSWER

        # Reacting to user input
        if test ${CONFIGANSWER} == "Y" || test ${CONFIGANSWER} == "y"
        then 
          if "${EDITOR:-vi}" ${CONFIG}
            then
              echo "Using edited config for the benchmark"
            else
              if test ${debug_mode} == "true" 2>/dev/null
              then
                echo "Using nano as the editor" 
                sleep 1s
              fi

              if nano ${CONFIG}
              then
                echo "Using edited config for the benchmark"
              else
                echo "Could not use any editor for the config"
                echo "Exiting script.."
                exit 13
              fi
            fi
        else
          echo "Using standardized benchmark config!"
        fi
      else
        echo "Config could not be created!"
        exit 11
      fi
    fi
fi
}


# check if screen is installed
screenCheck() {
  if commandAvailable screen
  then
    if test ${debug_mode} == "true" 2>/dev/null
    then
      echo "Screen is installed"
      sleep 1s
    fi
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
enable-status=false
enforce-secure-profile=true
enforce-whitelist=true
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
white-list=true" >${SERVERPROPERTIES}
}


serverPropertiesCheck() {
  if test -s ${SERVERPROPERTIES}
  then
    if test ${debug_mode} == "true" 2>/dev/null
    then
    echo "server.properties exists"
      sleep 1s
    fi
  else
    serverPropertiesCreate
  fi
}


downloadFabricAndDistantHorizons() {
  downloadIfNotExist ${DIR}/server/fabricserver.jar ${DIR}/server/fabricserver.jar ${fabric_download_url}
  
  if test -d ${DIR}/server/mods
  then
    if test ${debug_mode} == "true" 2>/dev/null
    then
      echo "mods folder exists"
      sleep 1s
    fi
  else
    if test ${debug_mode} == "true" 2>/dev/null
    then
    echo "mods folder does not exist, creating folder..."
      sleep 1s
    fi

    if mkdir ${DIR}/server/mods
    then
      if test ${debug_mode} == "true" 2>/dev/null
      then
      echo 'folder "mods" created' 
        sleep 1s
      fi
    else
      echo 'folder "mods" could not be created'
      exit 3
    fi
  fi

  downloadIfNotExist ${DIR}/server/mods/DistantHorizons-neoforge-fabric-2.3.2-b-1.21.1.jar ${DIR}/server/mods/DistantHorizons-neoforge-fabric-2.3.2-b-1.21.1.jar ${dh_download_url}
}


eulaCheck() {
  # Source: https://github.com/Griefed/ServerPackCreator/blob/7.1.3/serverpackcreator-api/src/main/resources/de/griefed/resources/server_files/default_template.sh
  if [[ ! -s "${EULAFILE}" ]]
  then

    echo "Mojang's EULA has not yet been accepted. In order to run a Minecraft server, you must accept Mojang's EULA."
    echo "Mojang's EULA is available to read at https://aka.ms/MinecraftEULA"
    echo "If you agree to Mojang's EULA then type 'I agree'"
    echo -n "Response: "
    read -r ANSWER

    if [[ "${ANSWER}" == "I agree" ]]
    then
      echo "User agreed to Mojang's EULA."
      echo "#By changing the setting below to TRUE you are indicating your agreement to our EULA (https://aka.ms/MinecraftEULA)." >${EULAFILE}
      echo "eula=true" >>${EULAFILE}
    else
      echo "User did not agree to Mojang's EULA. Entered: ${ANSWER}. You can not run a Minecraft server unless you agree to Mojang's EULA."
      exit 4
    fi

  fi
}


startServer() {
  printf "Starting Server! ▀ "
  # Go into the server folder to have all server files being created in the server folder
  cd ${DIR}/server
  screen -d -m -S ${SCREEN} java -Xmx${ram_gb}G ${extra_jvm_args} -jar ${DIR}/server/fabricserver.jar nogui &
  
  # Wait for the server to generate a new 'latest.log'
  while ! test -f ${DIR}/server/logs/latest.log
  do
    sleep 1s
  done

  # Wait for server to succesfully start
  while ! grep -w "Done" <${LATESTLOG} 1>/dev/null 2>/dev/null
  do
    checkCTRLC
    if test ${BUFFERCOUNTER} -lt 300
    then
      BUFFERCOUNTER=$((++BUFFERCOUNTER))
      printf "\rStarting Server! ▌ "
      sleep 0.5
      printf "\rStarting Server! ▄ "
      sleep 0.5
      printf "\rStarting Server! ▐ "
      sleep 0.5
      printf "\rStarting Server! ▀ "
      sleep 0.3
      sleep 1s
    else
      echo "Server did not start correctly, exiting script..."
      exit 7
    fi
  done
  echo " - Server started!"
  # Go back to the original directory to prevent bugs
  cd ${DIR}
}


setSeed() {
  if test ${1} -le ${#seeds[@]} && test ${1} -gt 0
  then
    echo "level-seed=${seeds[$((${1}-1))]}" >>${SERVERPROPERTIES}
    if test ${debug_mode} == "true" 2>/dev/null
      then
        echo "Set seed to ${seeds[$((${1}-1))]}"
        sleep 1s
      fi
  else
    echo "Seed #${1} does not exist!"
    exit 5
  fi
}


setThreadPreset() {
  if screen -S $SCREEN -X stuff "dh config common.threadPreset ${thread_preset}"^M
  then
    # Wait for the thread preset to be active
    while ! tail -n 5 <${LATESTLOG} | grep -w "preset active: ${thread_preset}" >/dev/null
    do
      sleep 1s
    done
    echo  "Set Thread Preset to ${thread_preset}" 
  else
    echo "Could not set common.threadPreset to ${thread_preset}"
    exit 6
  fi
  # Wait another five seconds to prevent the pregen of instantly being queued when the threadPreset is being set
  sleep 5s
}


stopServer() {
  printf "Shutting down server ▀ "
  sleep 12s 
  screen -S ${SCREEN} -X stuff "/stop^M"
  sleep 3s
  STOPCOUNTER="0"

  while screen -ls | grep -w ${SCREEN} >/dev/null
  do
    if test ${STOPCOUNTER} -ge 120
    then
      echo "Server did not stop gracefully, force closing server..."
      screen -S ${SCREEN} -X kill
    else
      printf "\rShutting down server ▌ "
      sleep 0.5
      printf "\rShutting down server ▄ "
      sleep 0.5
      printf "\rShutting down server ▐ "
      sleep 0.5
      printf "\rShutting down server ▀ "
      sleep 0.3
    fi
      sleep 1s
  done
  echo " - Server stopped!"
}


worldDelete() {
  if test -d ${DIR}/server/world
  then
    if test ${debug_mode} == "true" 2>/dev/null
    then 
      echo "World folder detected, trying to delete..."
      sleep 1s
    fi

    if rm -r ${DIR}/server/world
    then
      if test ${debug_mode} == "true" 2>/dev/null
      then
        echo "Successfully deleted previous world!" 
        sleep 1s
      fi

    else
      echo "Could not delete previous world, exiting script..."
      exit 9
    fi
  else
    if test ${debug_mode} == "true" 2>/dev/null
    then
      echo "No world folder detected!"
      sleep 1s
    fi
  fi
}


removeFileIfExists() {
  for FILE in $@
  do
    if test -s ${FILE}
    then
      if test ${debug_mode} == "true" 2>/dev/null
      then
        echo "File ${FILE} exists, trying to delete..."
        sleep 1s
      fi
 
      if rm ${FILE}
      then
        if test ${debug_mode} == "true" 2>/dev/null
        then
          echo "Successfully removed File ${FILE}"
          sleep 1s
        fi
      else
        echo "Could not delete File ${FILE}, trying to overwrite..."
        if echo "" >${FILE}
        then
          if test ${debug_mode} == "true" 2>/dev/null
          then
            echo "Successfully overwritten File ${FILE}"
            sleep 1s
          fi

        else
          echo "Could not overwrite File ${FILE}, exiting script..."
          exit 8
        fi
      fi
    else
      if test ${debug_mode} == "true" 2>/dev/null
      then
        echo "Could not find ${FILE}"
        sleep 1s
      fi
    fi
  done
}


# 1. Create ProgressBar function
# 1.1 Input is currentState($1) and totalState($2)
ProgressBar() {
  # Process data
  if test ${1} = "0"
  then
    _progress="0"
    _fill=""
    _empty="░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░"
  else
      let _progress=(${1}*100/${2}*100)/100 
      let _done=(${_progress}*4)/10 
      let _left=40-$_done 
  # Build progressbar string lengths
      _fill=$(printf "%${_done}s")
      _empty=$(printf "%${_left}s")
  fi
  # 1.2 Build progressbar strings and print the ProgressBar line
  # 1.2.1 Output example:
  # 1.2.1.1 Progress : [########################################] 100%
  printf "\rProgress: [${_fill// /▓}${_empty// /░}] ${_progress}%% ($(printTimeElapsed) / $(printETA))"

}


getDatapacksIfExist() {

if test -d ${CUSTOMDATAPACKFOLDER}
then
  if test ${debug_mode} == "true" 2>/dev/null
  then
    echo "${CUSTOMDATAPACKFOLDER} folder exists"
    sleep 1s
  fi
  if test -n "$(ls ${CUSTOMDATAPACKFOLDER} 2>/dev/null)" 
  then
    if test ${debug_mode} == "true" 2>/dev/null
    then
      echo "Datapacks exist, moving them"
      sleep 1s
    fi

    if mkdir -p ${DATAPACKFOLDER}
    then
      if test ${debug_mode} == "true" 2>/dev/null
      then
        echo "created ${DATAPACKFOLDER}"
        sleep 1s
      fi
    else
      echo "Could not create ${DATAPACKFOLDER}"
      echo "Using Vanilla world generation"
      # Exit the function since datapacks cannot be added
      return 1
    fi

    for datapack in $(ls ${CUSTOMDATAPACKFOLDER})
    do
      if cp ${CUSTOMDATAPACKFOLDER}/${datapack} ${DATAPACKFOLDER}
      then
        if test ${debug_mode} == "true" 2>/dev/null
        then
          echo "Successfully copied ${datapack}"
          sleep 1s
        fi
      else
        if test ${debug_mode} == "true" 2>/dev/null
          then
            echo "Could not copy ${datapack}"
            sleep 1s
          fi
      fi
    done
  else
    echo "No datapacks in ${CUSTOMDATAPACKFOLDER}, did you forget to delete the folder?"
  fi
else
  if test ${debug_mode} == "true" 2>/dev/null
  then
    echo "${CUSTOMDATAPACKFOLDER} folder does not exist, datapacks will not be used."
    sleep 1s
  fi
fi

}


collectHardwareInformation() {
  # Hardware Information #
  HARDWAREINFORMATION=()
  # Data preparation
  echo "Collecting CPU information..."
  CPU=$(lscpu | grep 'Model name' | cut -f 2 -d ":" | awk '{$1=$1}1')
    if test -z "${CPU}"
    then
      CPU="UNKNOWN"
    fi
  # Clean up cpu naming scheme
  CPU=$(echo "${CPU}" | sed -E 's/\b[0-9]+(st|nd|rd|th) Gen[[:space:]]+//g' | sed 's/(R)//g; s/(TM)//g' | 
        sed -E 's/[[:space:]]*@[[:space:]]*[0-9]+(\.[0-9]+)?[[:space:]]*GHz//g' | sed 's/ with Radeon Graphics//g' | sed 's/ CPU//g' | 
        sed -E 's/[[:space:]]+[0-9]+-Core[[:space:]]+Processor//g' | sed 's/[[:space:]]\{2,\}/ /g' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
  

  CPUCORES=$(lscpu | awk '/^Socket\(s\):/ {sockets=$2} /^Core\(s\) per socket:/ {cores=$4} END {print sockets * cores}')
    if test -z "${CPUCORES}"
    then
      CPUCORES="x"
    fi
  CPUTHREADS=$(nproc --all)
    if test -z "${CPUTHREADS}"
    then
      CPUTHREADS="x"
    fi
  echo "CPU: ${CPU} ${CPUCORES}C/${CPUTHREADS}T"
  # Add CPU information to HARDWAREINFORMATION array
  HARDWAREINFORMATION+=("${CPU} ${CPUCORES}C/${CPUTHREADS}T,")

  read -r -p "The following commands have to be executed with elevated privileges to get the full data automatically, you may be asked multiple times, do you agree? (Yes/No): " ANSWERHARDWAREDATA
  if test ${ANSWERHARDWAREDATA} == "Yes"
  then
    echo 'Please make sure that the package "dmidecode" is installed with your linux system otherwise the following data collection will not work.'
    sleep 3s
    # Collect RAM information
      if test ${debug_mode} == "true" 2>/dev/null
      then
        echo "Collecting RAM information..."
        sleep 1s
      fi

      
      RAMSIZEONCE=$(sudo dmidecode -t memory | grep -w "Size" | grep -w "GB" | cut -d " " -f 2 | head -n 1)
      RAMSTICKSDOUBLE=$(sudo dmidecode -t memory | grep -w "Size" | grep -w "GB" | wc -l)
      if test ${RAMSTICKSDOUBLE} -eq 1
      then
        RAMSTICKS=${RAMSTICKSDOUBLE}
      else
        RAMSTICKS=$((RAMSTICKSDOUBLE / 2))
      fi

      # Get/calculate each variable and set to default value if not successful
      RAMSIZETOTAL=$((RAMSIZEONCE * RAMSTICKS))
      if test -z "${RAMSIZETOTAL}"
      then
        echo "RAM Size could not be collected!"
        RAMSIZETOTAL="x"
      fi

      RAMTYPE=$(sudo dmidecode -t memory | grep "DDR" | cut -d " " -f 2 | head -n 1)
      if test -z "${RAMTYPE}"
      then
        echo "RAM Type could not be determined!"
        RAMTYPE="Unknown"
      fi

      RAMSPEED=$(sudo dmidecode -t memory | grep "Configured Memory Speed" | cut -d " " -f 4,5 | head -n 1)
      if test -z "${RAMSPEED}"
      then
        echo "RAM Speed could not be determined!"
        RAMSPEED="xxxxMT/s"
      fi

      echo "RAM: ${RAMSIZETOTAL}GB ${RAMTYPE} ${RAMSPEED}"
      # Add RAM information to HARDWAREINFORMATION array
      HARDWAREINFORMATION+=("${RAMSIZETOTAL}GB ${RAMTYPE} ${RAMSPEED},")

    # Collect disk information
      if test ${debug_mode} == "true" 2>/dev/null
      then
        echo "Collecting Disk information..."
        sleep 1s
      fi

      if commandAvailable findmnt
      then
        DISKMOUNT=$(findmnt -T ${DIR} -n -o SOURCE)
      else
        DISKPARTITION=$(df ${DIR} | grep dev | cut -d " " -f 1 | cut -d "/" -f 3)
        DISKID=$(ls -l /dev/disk/by-id | grep ${DISKPARTITION} | cut -d " " -f 9 | head -n 1 | cut -d "-" -f 1,2)
        DISKMOUNT=$(ls -l /dev/disk/by-id | grep ${DISKID} | head -n 1 | cut -d ">" -f 2 | cut -d "/" -f 3)
        DISKMOUNT="/dev/"${DISKMOUNT}
      fi

      # Get Disk Model
      for ARGDEVTYPE in ${ARRAYDEVTYPES[@]}
      do
        if test -z "${DISKMODEL}"
        then
          DISKMODEL=$(sudo smartctl -d ${ARGDEVTYPE} -a ${DISKMOUNT} | grep -w "Model" | tr -s " " | cut -d ":" -f 2 | tr -d " ")
        else
          if test ${debug_mode} == "true" 2>/dev/null
          then
            echo "Found Device Type: ${DISKMODEL} with argument: ${ARGDEVTYPE}"
            sleep 1s
          fi
          break
        fi
      done

      # Get Disk Capacity
      for ARGDEVTYPE in ${ARRAYDEVTYPES[@]}
      do
        if test -z "${DISKSIZE}"
        then
          DISKSIZE=$(sudo smartctl -d ${ARGDEVTYPE} -a ${DISKMOUNT} | grep "Capacity" | head -n 1 | cut -d "[" -f 2 | cut -d " " -f 1)
        else
          if test ${debug_mode} == "true" 2>/dev/null
          then
            echo "Found Capacity: ${DISKSIZE} with argument: ${ARGDEVTYPE}"
            sleep 1s
          fi
          break
        fi
      done

    # Do seperate check for both variables
      if test -z "${DISKMODEL}"
      then  
        DISKMODEL="UNKNOWN"
      fi

      if test -z "${DISKSIZE}"
      then
        DISKSIZE="UNKNOWN"
      fi

    echo "DISK: ${DISKMODEL} ${DISKSIZE}GB"
    # Add disk information to HARDWAREINFORMATION array
    HARDWAREINFORMATION+=("${DISKMODEL} ${DISKSIZE}GB")

    HARDWAREINFORMATION=($(echo ${HARDWAREINFORMATION[@]} | tr " " "_" | tr "," " "))
    HARDWAREINFORMATIONCSV=${HARDWAREINFORMATION[@]}
    # Add hardware information to separate csv file for Backup-purposes
    if echo ${HARDWAREINFORMATIONCSV} >${BACKUPHARDWAREINFORMATIONCSV}
    then

      if test ${debug_mode} == "true" 2>/dev/null
      then
        echo "Hardware information added to backup csv file!"
        sleep 1s
      fi
      
      # Fetching results from the runs and adding the hardware-information to it
      INCOMPLETEDATA=($(tail -n 1 <${RESULTSCSVFILE} | tr " " "_" | tr "," " "))
      INCOMPLETEDATA[0]=${HARDWAREINFORMATION[0]}
      INCOMPLETEDATA[1]=${HARDWAREINFORMATION[1]}
      INCOMPLETEDATA[2]=${HARDWAREINFORMATION[2]}

      INCOMPLETEDATA=($(echo ${INCOMPLETEDATA[@]} | tr " " "," | tr "_" " "))

      if test ${debug_mode} == "true" 2>/dev/null
      then
        # Labeling Results CSV file for debug purposes
        if echo "CPU,RAM,DRIVE,Allocated RAM,Run 1,Run 2,Run 3,Run 4,Run 5,Average Time,Average Chunks per Second,DB Size Run 1,DB Size Run 2,DB Size Run 3,DB Size Run 4,DB Size Run 5,Average DB Size,----DEBUG TRUE----" >${RESULTSCSVFILE} \
          && echo ${INCOMPLETEDATA[@]} >>${RESULTSCSVFILE}
        then
        removeFileIfExists ${BACKUPHARDWAREINFORMATIONCSV}
        else
          benchmarkResultsCSVCreate
          echo "Could not add hardware information to main csv file, please provide the following files when submitting:"
          echo ${BACKUPHARDWAREINFORMATIONCSV}
          echo ${RESULTSCSVFILE}
        fi
        sleep 1s
      else
        # Not labeling Results CSV file for automatic importing
        if echo ${INCOMPLETEDATA[@]} >${RESULTSCSVFILE}
        then
          if test ${debug_mode} == "true" 2>/dev/null
          then
            echo "Added cleaned csv formatted data to csv file"
            sleep 1s
          fi
          
          removeFileIfExists ${BACKUPHARDWAREINFORMATIONCSV}
        else
          benchmarkResultsCSVCreate
          echo "Could not add hardware information to main csv file, please provide the following files when submitting:"
          echo ${BACKUPHARDWAREINFORMATIONCSV}
          echo ${RESULTSCSVFILE}
        fi
      fi
    else
      echo "Adding Hardware information to separate csv file"
      if echo -e "CPU,RAM,DRIVE\n"${HARDWAREINFORMATIONCSV} >>${BACKUPHARDWAREINFORMATIONCSV}
      then
        echo "Added hardware information to separate csv file"
      else
        echo "Could not add hardware information to separate csv file, please provide this information when submitting:"
        echo "${CPU} ${CPUCORES}C/${CPUTHREADS}T,${RAMSIZETOTAL}GB ${RAMTYPE} ${RAMSPEED},${DISKMODEL} ${DISKSIZE}GB"
      fi
      echo "Could not add hardware information to csv file, please provide both ${BACKUPHARDWAREINFORMATIONCSV} and ${RESULTSCSVFILE} when submitting!"
    fi
  else
    echo 'The user did not accept, please execute the script with elevated privileges as shown to add the hardware information to the .csv file: "./dh-autobenchmark.sh -h"'
  fi

  echo "-----------------------"
  echo "- The Script finished -"
  echo "-----------------------"

  exit 0
}


calculateAverages() {
  # Calculate averages for the benchmarktimes and db sizes and convert them to the csv format
  BENCHMARKTIME_AVERAGE=$(awk -v sum="${BENCHMARKTIME_SUM}" 'BEGIN {printf "%.3f", sum / 5}')
  BENCHMARKTIME_AVERAGE_CSV=$(awk -v time="${BENCHMARKTIME_AVERAGE}" 'BEGIN {printf "%02d\n", (time/60)/60}'):$(awk -v time="${BENCHMARKTIME_AVERAGE}" 'BEGIN {printf "%02d\n", (time / 60) % 60}'):$(awk -v time="${BENCHMARKTIME_AVERAGE}" 'BEGIN {printf "%06.3f\n", time % 60}')","

  DBSIZE_AVERAGE=$(awk -v sum="${DBSIZE_SUM}" 'BEGIN {printf "%d", sum / 5}')
  DBSIZE_AVERAGE_CSV=${DBSIZE_AVERAGE}"MB"
}


fillBenchmarkResultsCSV() {
  calculateAverages

  # Save RUNTIMES Array and DBSIZES Array to a variable to convert to csv format later
  RUNTIMESVARIABLE=${RUNTIMES[@]}
  DBSIZESVARIABLE=${DBSIZES[@]}
  CPSVARIABLE=${CPS_CSV[@]}


  if test ${debug_mode} == "true" 2>/dev/null
  then
    # Add the time and db size of every run to the main csv file
    if echo "CPU,RAM,DRIVE,Allocated RAM,Run 1,Run 2,Run 3,Run 4,Run 5,Average Time,Average Chunks per Second,DB Size Run 1,DB Size Run 2,DB Size Run 3,DB Size Run 4,DB Size Run 5,Average DB Size" >${RESULTSCSVFILE} \
        && echo "CPU,RAM,DRIVE,"${ram_gb}"GB,"$(echo ${RUNTIMESVARIABLE} | tr -d " ")${BENCHMARKTIME_AVERAGE_CSV}${CPS_TOTALAVERAGE}","$(echo ${DBSIZESVARIABLE} | tr -d " ")${DBSIZE_AVERAGE_CSV} >>${RESULTSCSVFILE}
    then
        echo "Added run times and db sizes and averages to main csv file!"
        sleep 1s

        if test ${PROGRESS} == "true"
          then
            removeFileIfExists ${PROGRESSFILE}
        fi
    else
      echo "Could not add run times and db sizes to main csv file."
      echo "Please provide this information when submitting:"
      echo ${RUNTIMES[@]} 
      echo ${DBSIZES[@]}
      echo "Average Run Time: "${BENCHMARKTIME_AVERAGE_CSV}
      echo "Average DB Size: "${DBSIZE_AVERAGE_CSV}
    fi
  else
    # Add the time and db size of every run to the main csv file
    if echo "CPU,RAM,DRIVE,"${ram_gb}"GB,"$(echo ${RUNTIMESVARIABLE} | tr -d " ")${BENCHMARKTIME_AVERAGE_CSV}${CPS_TOTALAVERAGE}","$(echo ${DBSIZESVARIABLE} | tr -d " ")${DBSIZE_AVERAGE_CSV} >${RESULTSCSVFILE}
    then
      if test ${debug_mode} == "true" 2>/dev/null
      then
        echo "Added run times and db sizes and averages to main csv file!"
        sleep 1s
      fi
      
      if test ${PROGRESS} == "true" 2>/dev/null || test ${FINISHED} == "true" 2>/dev/null
          then
            removeFileIfExists ${PROGRESSFILE}
      fi
    else
      echo "Could not add run times and db sizes to main csv file."
      echo "Please provide this information when submitting:"
      echo ${RUNTIMES[@]} 
      echo ${DBSIZES[@]}
      echo "Average Run Time: "${BENCHMARKTIME_AVERAGE_CSV}
      echo "Average DB Size: "${DBSIZE_AVERAGE_CSV}
    fi
  fi
}


benchmarkResultsCSVCreate() {
  if touch ${RESULTSCSVFILE}
  then
    fillBenchmarkResultsCSV
  else
    if test ${debug_mode} == "true" 2>/dev/null
    then
      echo "${RESULTSCSVFILE} could not be created beforehand"
      echo "Filling ${RESULTSCSVFILE} with data without creating it beforehand"
      sleep 1s
    fi
    fillBenchmarkResultsCSV
  fi
}


printETA() {

  ETA=$(echo ${GREPLATESTLOG} | cut -d ":" -f 6)

  for time in ${ETA}
    do
      if [[ "${time}" == *"h"* ]]
      then
          time=$(echo ${time} | tr -d "h" | tr -d " ")
          ETA_HOUR=${time}
      elif [[ "${time}" == *"m"* ]]
      then
          time=$(echo ${time} | tr -d "m" | tr -d " ")
          ETA_MINUTE=${time}
      elif [[ "${time}" == *"s"* ]]
      then
          time=$(echo ${time} | tr -d "s" | tr -d " ")
          ETA_SECONDS=${time}
      else
          echo "${time} cannot be recognized!"
      fi
    done
    echo $(awk -v timehour="${ETA_HOUR}" 'BEGIN {printf "%02d", timehour}'):$(awk -v timeminute="${ETA_MINUTE}" 'BEGIN {printf "%02d", timeminute}'):$(awk -v timeseconds="${ETA_SECONDS}" 'BEGIN {printf "%02d", timeseconds}')

}


printTimeElapsed() {
  echo $(awk -v time="${SECONDS}" 'BEGIN {printf "%02d\n", (time/60)/60}'):$(awk -v time="${SECONDS}" 'BEGIN {printf "%02d\n", (time / 60) % 60}'):$(awk -v time="${SECONDS}" 'BEGIN {printf "%02d\n", time % 60}')
}


calculateCpsAverageOneRun() {
  CPS_AVERAGE=$(awk -v btimesm="${BENCHMARKTIME_SECONDS_MILLISECONDS}" -v totalchunks="${generation_radius}" 'BEGIN {printf "%d", ((totalchunks * 2)^2 / btimesm)}')
  # Calculate average and save in an array in csv format to later add to the main csv file
  CPS_CSV+=(${CPS_AVERAGE})
}


calculateCpsAverageTotal(){
  CPSTOTAL_SUM="0"
  for value in ${CPS_CSV[@]}
  do
    CPSTOTAL_SUM=$((CPSTOTAL_SUM + value))
  done

  CPS_TOTALAVERAGE=$((CPSTOTAL_SUM / 5))
}

createServerFolderIfNotExist(){
  if test -d ${DIR}/server 2>/dev/null
  then
    if test ${debug_mode} == "true" 2>/dev/null
    then
      echo "${DIR}/server exists!" 
    fi
  else
    if mkdir ${DIR}/server 
    then
      if test ${debug_mode} == "true" 2>/dev/null
      then
        echo "${DIR}/server created successfully!" 
      fi
    else
      echo "Could not create ${DIR}/server, please check your permissions!"
      echo "Exiting Script.."
      exit 10
    fi
  fi
}


createEmptyProgressFile(){
  if echo -ne "CPU,RAM,DRIVE\n">${PROGRESSFILE}
  then
    if test ${debug_mode} == "true" 2>/dev/null
    then
      echo "Created clean progress file"
    fi
  else
    echo "Could not create progress file, please report this bug to the author!"
    exit 14
  fi
}


readProgressFileAndAppendToArrays(){
  # Progressdatei einlesen und die Werte zu den zugehörigen Arrays hinzufügen
        
  PROGRESSFILEREAD=$(tail -n $((PROGRESSLINES-1)) <${PROGRESSFILE} |  tr "," " ")
  TEMPCOUNT=1

  for DATA in ${PROGRESSFILEREAD}
  do
      if test ${TEMPCOUNT} -ge 4
      then
          TEMPCOUNT=1
      fi
      if test ${TEMPCOUNT} -eq 1
      then
          RUNTIMES+=($(awk -v time="${DATA}" 'BEGIN {printf "%02d\n", (time/60)/60}'):$(awk -v time="${DATA}" 'BEGIN {printf "%02d\n", (time / 60) % 60}'):$(awk -v time="${DATA}" 'BEGIN {printf "%06.3f\n", time % 60}')",")


          BENCHMARKTIME_SUM=$(awk -v sum="${BENCHMARKTIME_SUM}" -v benchtimeonerun="${DATA}" 'BEGIN {printf "%.3f", sum + benchtimeonerun}')","
      fi
      if test ${TEMPCOUNT} -eq 2
      then
          DBSIZES+=(${DATA}",")

          DBSIZE_SUM=$(awk -v sum="${DBSIZE_SUM}" -v dbsizeonerun="${DATA}" 'BEGIN {printf "%.3f", sum + dbsizeonerun}')
      fi
      if test ${TEMPCOUNT} -eq 3
      then
          CPS_CSV+=(${DATA})

          TEMPCOUNT_RUN=$((++TEMPCOUNT_RUN))
      fi
      TEMPCOUNT=$((++TEMPCOUNT))
  done
}


checkProgress(){
  if test -s ${PROGRESSFILE}
  then
    PROGRESSLINES=$(wc -l <${PROGRESSFILE})
    if test ${PROGRESSLINES} -gt 1
    then
      PROGRESS="true"
      if test ${PROGRESSLINES} -gt 5
      then
        echo "Runs already completed, converting to csv format"
        readProgressFileAndAppendToArrays

        calculateCpsAverageTotal

        benchmarkResultsCSVCreate

        collectHardwareInformation
      fi

      read -r -p "There is already known progress for $((PROGRESSLINES-1)) run(s), would you like to continue on run ${PROGRESSLINES}? (Yes/No): " PROGRESSANSWER
      
      if test ${PROGRESSANSWER} == "Yes" || test ${PROGRESSANSWER} == "yes"
      then
        readProgressFileAndAppendToArrays

        echo "Continuing benchmark from run ${PROGRESSLINES} onwards"
        RUN=${PROGRESSLINES}
      else
        echo "Starting the benchmark from run 1 in 20 seconds!"
        sleep 20s
        removeFileIfExists ${DIR}/${CONFIG}
        removeFileIfExists ${PROGRESSFILE}
        createEmptyProgressFile
      fi
    else
      if test ${debug_mode} == "true" 2>/dev/null
      then
        echo "${PROGRESSFILE} exists but progresslines is <=1, creating clean progress file"
      fi
      createEmptyProgressFile
    fi
  else
    if test ${debug_mode} == "true" 2>/dev/null
    then
      echo "${PROGRESSFILE} does not exist, creating clean progress file"
    fi
    createEmptyProgressFile
  fi
}

appendProgressToProgressFile(){
  # Add Runtime to txt file
  echo -ne ${BENCHMARKTIME_SECONDS_MILLISECONDS}"," >>${PROGRESSFILE}
  # Add DBSize to txt file
  echo -ne ${DBSIZEMB}"MB," >>${PROGRESSFILE}
  # Add Average CPS from the run to txt file
  echo -ne ${CPS_AVERAGE}"\n" >>${PROGRESSFILE}
}

## Main structure

# Check for options first
while getopts ":h" OPTION
  do
    case $OPTION in
      h)
        collectHardwareInformation
      exit;;
      *)
        echo "Invalid option: $OPTION"
      exit;;
    esac
  done

# Check for older benchmark progress
checkProgress

# Check if the folder "server" exists, otherwise create it
createServerFolderIfNotExist

# Check for dh-benchmark.toml
configCheck

# Get variables from config file
source ${CONFIG}

# Check for screen, fabricserver.jar and Distant Horizons mod file 
screenCheck
downloadFabricAndDistantHorizons

# Check if eula exists and if not, ask the user to accept it
eulaCheck

# Five runs (one for every seed)
while ! test ${RUN} -ge 6
do
  serverPropertiesCreate
  
  setSeed ${RUN}
  # Delete previous world
  worldDelete
  # Add datapacks if put into folder "custom_datapacks"
  getDatapacksIfExist
  
  # Display configuration to user only on first run
  if test ${RUN} -eq "1"
  then
    echo "Loaded configuration:"
    echo "- RAM (GB): ${ram_gb}"
    echo "- Seeds: ${seeds[@]}"
    echo "- Thread Preset: ${thread_preset}"
    echo "- Benchmark Radius: ${generation_radius}"
    echo "- Fabric Download URL: ${fabric_download_url}"
    echo "- Distant Horizons Download URL: ${dh_download_url}" 
    echo "- Extra JVM Args: ${extra_jvm_args}"
    echo "- Debug Mode: ${debug_mode}"
  fi

  startServer

  # Set thread preset to the preset set in the config
  setThreadPreset
  
  # User output
  echo "Starting Pregen Run ${RUN} with radius ${generation_radius} for seed ${seeds[$((${RUN}-1))]}"

  # Reset/create CPS Array because it contains data from last run (if not first run)
  CPS=()

  # Send command to start pregen and wait for pregen to start
  if screen -S ${SCREEN} -X stuff "dh pregen start minecraft:overworld 0 0 ${generation_radius}"^M
  then
    while ! grep -w "Starting pregen" <${LATESTLOG} 1>/dev/null 2>/dev/null
    do 
      sleep 1s
    done
    # Get start time in nanoseconds
    start_at=$(date +%s,%N | tr "," ".")
    SECONDS="0"
    
    # Wait for pregen to finish and show and update progressbar
    while ! grep -w "Pregen is complete" <${LATESTLOG} >/dev/null 2>/dev/null
    do
      sleep 0.001s

      # Check if CTRL+C has been pressed
      checkCTRLC
    if test ${debug_mode} == "true" 2>/dev/null
    then
      GREPLATESTLOG=$(grep -w "Generated radius" <${LATESTLOG} | tail -n 1)
      echo ${GREPLATESTLOG}
      sleep 1s
    else
      # Save last line from log in a variable to only access the file once
      GREPLATESTLOG=$(grep -w "Generated radius" <${LATESTLOG} | tail -n 1)
      PERCENTFINISHED=$(echo ${GREPLATESTLOG} | cut -d "%" -f 1 | cut -d " " -f 12 | cut -d "." -f 1)
      
      ProgressBar ${PERCENTFINISHED} ${_end}
    fi
      
    done
    
    if test ${debug_mode} == "true" 2>/dev/null
    then
      echo "Pregen completed 100%"
    else
      # Show finished Progressbar to not have unfinished Progressbar's showing on CLI
      ProgressBar 100 100
    fi

    # Get end time in nanoseconds
    end_at=$(date +%s,%N | tr "," ".")

    # Calculate time spent, save it and add the time to a sum to calculate the average time later
    BENCHMARKTIME_SECONDS_MILLISECONDS=$(awk -v start=${start_at} -v end=${end_at} 'BEGIN {printf "%.3f", end - start}')
    BENCHMARKTIME_SUM=$(awk -v sum="${BENCHMARKTIME_SUM}" -v benchtimeonerun="${BENCHMARKTIME_SECONDS_MILLISECONDS}" 'BEGIN {printf "%.3f", sum + benchtimeonerun}')

    # Calculate average cps of current run and save in array
    calculateCpsAverageOneRun

    # Add benchmark times to RUNTIMES array 
    RUNTIMES+=($(awk -v time="${BENCHMARKTIME_SECONDS_MILLISECONDS}" 'BEGIN {printf "%02d\n", (time/60)/60}'):$(awk -v time="${BENCHMARKTIME_SECONDS_MILLISECONDS}" 'BEGIN {printf "%02d\n", (time / 60) % 60}'):$(awk -v time="${BENCHMARKTIME_SECONDS_MILLISECONDS}" 'BEGIN {printf "%06.3f\n", time % 60}')",")
    
    # Add db sizes to DBSIZES array
    DBSIZE=$(stat -c '%s' ${WORLDFOLDER}/data/DistantHorizons.sqlite)
    DBSIZEMB=$((DBSIZE / (1024*1024)))
    DBSIZE_SUM=$(awk -v sum="${DBSIZE_SUM}" -v dbsizeonerun="${DBSIZEMB}" 'BEGIN {printf "%.3f", sum + dbsizeonerun}')
    
    DBSIZES+=(${DBSIZEMB}"MB,")

    # Add results to another txt file to save the progress
    appendProgressToProgressFile

    # User output
    echo -e "\nPregen completed in $(awk -v time="${BENCHMARKTIME_SECONDS_MILLISECONDS}" 'BEGIN {printf "%02d\n", (time/60)/60}'):$(awk -v time="${BENCHMARKTIME_SECONDS_MILLISECONDS}" 'BEGIN {printf "%02d\n", (time / 60) % 60}'):$(awk -v time="${BENCHMARKTIME_SECONDS_MILLISECONDS}" 'BEGIN {printf "%06.3f\n", time % 60}'), Average Chunks per second: $(echo ${CPS_CSV[$((RUN-1))]} | tr -d ","), Database size: ${DBSIZEMB}MB"

    stopServer
  fi  
  RUN=$((++RUN)) 
done
FINISHED="true"
calculateCpsAverageTotal

benchmarkResultsCSVCreate

collectHardwareInformation