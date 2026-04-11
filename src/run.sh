#!/bin/bash

G='\033[92m'
DIM='\033[2;32m'
RED='\033[31m'
BOLD='\033[1m'
R='\033[0m'

clear

echo -e "${G}${BOLD}"
echo "  ██████╗██╗  ██╗ █████╗ ████████╗"
echo " ██╔════╝██║  ██║██╔══██╗╚══██╔══╝"
echo " ██║     ███████║███████║   ██║   "
echo " ██║     ██╔══██║██╔══██║   ██║   "
echo " ╚██████╗██║  ██║██║  ██║   ██║   "
echo "  ╚═════╝╚═╝  ╚═╝╚═╝  ╚═╝   ╚═╝  "
echo -e "${R}"
echo -e "${G}  ────────────────────────────────────${R}"
echo -e "${G}  Secure Java Chat App${R}"
echo -e "${G}  ────────────────────────────────────${R}"
echo ""

# ── Check Java ───────────────────────────────────────────────
echo -e "${DIM}  Checking Java...${R}"
if ! command -v java &>/dev/null; then
  echo -e "${RED}  Java not found. Installing...${R}"
  sudo apt update -q && sudo apt install -y default-jdk
  if ! command -v java &>/dev/null; then
    echo -e "${RED}  Java install failed. Run: sudo apt install default-jdk${R}"
    exit 1
  fi
fi
echo -e "${G}  Java ready.${R}"
echo ""

# ── Compile ──────────────────────────────────────────────────
echo -e "${DIM}  Compiling...${R}"
cd "$(dirname "${BASH_SOURCE[0]}")"

if ! ls *.java 1>/dev/null 2>&1; then
  echo -e "${RED}  No .java files found in: $(pwd)${R}"
  exit 1
fi

javac *.java
if [ $? -ne 0 ]; then
  echo -e "${RED}  Compilation failed. Fix the errors above and try again.${R}"
  exit 1
fi

echo -e "${G}  Compiled successfully.${R}"
echo ""
echo -e "${G}  ────────────────────────────────────${R}"
echo ""

# ── Menu ─────────────────────────────────────────────────────
echo -e "${G}${BOLD}  What do you want to do?${R}"
echo ""
echo -e "${G}  1) Start SERVER  (same WiFi network)${R}"
echo -e "${G}  2) Start SERVER  (with ngrok — anyone on internet can join)${R}"
echo -e "${G}  3) Join as CLIENT${R}"
echo ""
echo -ne "${G}${BOLD}  Enter 1, 2 or 3 > ${R}\033[32m"
read choice
echo -e "${R}"

case $choice in

1)
  LOCAL_IP=$(ip addr show | grep 'inet ' | grep -v '127.0.0.1' | awk '{print $2}' | cut -d/ -f1 | head -1)
  echo -e "${G}  ────────────────────────────────────${R}"
  echo -e "${G}${BOLD}  SERVER STARTING (Local Network)${R}"
  echo ""
  echo -e "${G}  Share these details with everyone on the same WiFi:${R}"
  echo ""
  echo -e "${G}${BOLD}      IP    →  $LOCAL_IP${R}"
  echo -e "${G}${BOLD}      Port  →  5000${R}"
  echo ""
  echo -e "${DIM}  Press Ctrl+C to stop.${R}"
  echo -e "${G}  ────────────────────────────────────${R}"
  echo ""
  java Server
  ;;

2)
  # ── Install ngrok if missing ──────────────────────────
  if ! command -v ngrok &>/dev/null; then
    echo -e "${RED}  ngrok not found. Installing...${R}"

    ARCH=$(uname -m)
    if [[ "$ARCH" == "x86_64" ]]; then
      NGROK_URL="https://bin.equinox.io/c/bNyj1mQVY4c/ngrok-v3-stable-linux-amd64.tgz"
    elif [[ "$ARCH" == "aarch64" ]]; then
      NGROK_URL="https://bin.equinox.io/c/bNyj1mQVY4c/ngrok-v3-stable-linux-arm64.tgz"
    else
      echo -e "${RED}  Unsupported CPU. Install ngrok manually from ngrok.com${R}"
      exit 1
    fi

    curl -s -o /tmp/ngrok.tgz "$NGROK_URL"
    if [ $? -ne 0 ]; then
      echo -e "${RED}  Download failed. Check your internet connection.${R}"
      exit 1
    fi

    tar -xzf /tmp/ngrok.tgz -C /tmp
    sudo mv /tmp/ngrok /usr/local/bin/ngrok
    echo -e "${G}  ngrok installed.${R}"
  fi

  # ── Add auth token if not already saved ──────────────
  if ! grep -q "authtoken" ~/.config/ngrok/ngrok.yml 2>/dev/null; then
    echo ""
    echo -e "${G}  ngrok needs a free account token (one time only).${R}"
    echo -e "${G}  1. Go to: https://dashboard.ngrok.com/signup${R}"
    echo -e "${G}  2. Sign up for free${R}"
    echo -e "${G}  3. Copy your authtoken from the dashboard${R}"
    echo ""
    echo -ne "${G}${BOLD}  Paste your ngrok authtoken > ${R}\033[32m"
    read -r token
    echo -e "${R}"

    if [ -z "$token" ]; then
      echo -e "${RED}  No token entered. Aborting.${R}"
      exit 1
    fi

    ngrok config add-authtoken "$token"
  fi

  echo -e "${G}  ────────────────────────────────────${R}"
  echo -e "${G}${BOLD}  SERVER STARTING with ngrok...${R}"
  echo ""

  # Start Java server in background
  echo -e "${DIM}  Starting Java server...${R}"
  java Server &
  SERVER_PID=$!
  sleep 1

  # Check server actually started
  if ! kill -0 $SERVER_PID 2>/dev/null; then
    echo -e "${RED}  Server failed to start. Check for port conflicts.${R}"
    exit 1
  fi

  # Start ngrok in background
  echo -e "${DIM}  Opening ngrok tunnel...${R}"
  ngrok tcp 5000 --log=stdout >/tmp/ngrok.log 2>&1 &
  NGROK_PID=$!
  sleep 3

  # Pull public address from ngrok local API
  NGROK_ADDR=$(curl -s http://localhost:4040/api/tunnels 2>/dev/null |
    grep -o '"public_url":"tcp://[^"]*"' |
    cut -d'"' -f4 |
    sed 's|tcp://||')

  echo ""
  if [ -z "$NGROK_ADDR" ]; then
    echo -e "${RED}  Could not get ngrok address. Check /tmp/ngrok.log${R}"
    echo -e "${DIM}  Server is still running on port 5000 locally.${R}"
  else
    NGROK_HOST=$(echo "$NGROK_ADDR" | cut -d: -f1)
    NGROK_PORT=$(echo "$NGROK_ADDR" | cut -d: -f2)
    echo -e "${G}  ────────────────────────────────────${R}"
    echo -e "${G}${BOLD}  Share these with everyone:${R}"
    echo ""
    echo -e "${G}${BOLD}      IP    →  $NGROK_HOST${R}"
    echo -e "${G}${BOLD}      Port  →  $NGROK_PORT${R}"
    echo ""
    echo -e "${DIM}  Works from anywhere in the world.${R}"
    echo -e "${G}  ────────────────────────────────────${R}"
  fi

  echo ""
  echo -e "${DIM}  Press Ctrl+C to stop everything.${R}"

  # Clean up both processes on exit
  trap "echo ''; echo -e '${DIM}  Shutting down...${R}'; kill $SERVER_PID $NGROK_PID 2>/dev/null; wait $SERVER_PID 2>/dev/null; echo -e '${G}  Done.${R}'" EXIT

  wait $SERVER_PID
  ;;

3)
  echo -ne "${G}${BOLD}  Enter server IP > ${R}\033[32m"
  read -r ip
  echo -e "${R}"

  if [ -z "$ip" ]; then
    echo -e "${RED}  No IP entered.${R}"
    exit 1
  fi

  echo -ne "${G}${BOLD}  Enter port (press Enter for 5000) > ${R}\033[32m"
  read -r port
  echo -e "${R}"

  if [ -z "$port" ]; then
    port="5000"
  fi

  # Validate port is a number
  if ! [[ "$port" =~ ^[0-9]+$ ]]; then
    echo -e "${RED}  Invalid port number.${R}"
    exit 1
  fi

  echo -e "${G}  Launching client...${R}"
  sleep 0.3

  # Pass IP and port as arguments — no file patching needed
  java ChatTUI "$ip" "$port"
  ;;

*)
  echo -e "${RED}  Invalid choice. Run the script again.${R}"
  exit 1
  ;;
esac
