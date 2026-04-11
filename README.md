# CHAT // TERMINAL

> A secure group chat application built from scratch in Java — no frameworks, no libraries.

![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=java&logoColor=white)
![Shell](https://img.shields.io/badge/Shell_Script-121011?style=for-the-badge&logo=gnu-bash&logoColor=white)
![HTML](https://img.shields.io/badge/HTML5-E34F26?style=for-the-badge&logo=html5&logoColor=white)

---

## What is this?

A group chat app where laptop users connect via a hacker-style green terminal UI and phone users connect via a browser — all chatting on the same server in real time.

Built entirely with pure Java standard library. No Spring, no Maven, no external dependencies.

---

## Features

- Password protected with SHA-256 hashing
- Terminal UI with hacker green aesthetic and timestamps
- Browser and phone support via WebSocket
- Works on same WiFi or anywhere in the world via ngrok
- Rate limiting — max 10 messages per 5 seconds per user
- Connection cap — max 20 clients to protect the host machine
- Auto detects browser vs terminal client on the same port
- One script setup — installs Java and ngrok automatically

---

## File Structure

```
ChatApplication/
└── src/
    ├── Server.java            ← main server, manages all connections
    ├── ClientHandler.java     ← handles terminal clients
    ├── WebSocketHandler.java  ← handles browser/phone clients
    ├── PushedBackSocket.java  ← helper for detecting client type
    ├── ChatTUI.java           ← hacker terminal UI for laptop users
    ├── Client.java            ← basic terminal client (no UI)
    ├── run.sh                 ← one command setup and launcher
    └── chat.html              ← web client for phone/browser users
```

---

## How to Run

### Requirements
- Linux (tested on Arch and Ubuntu)
- Java JDK (installed automatically if missing)

### Start
```bash
cd src
chmod +x run.sh
./run.sh
```

You will see:
```
  1) Start SERVER  (same WiFi network)
  2) Start SERVER  (with ngrok — anyone on internet can join)
  3) Join as CLIENT
```

### For phones
Open `chat.html` in any mobile browser, enter the server IP, port, password and username.

---

## Changing the Password

Open `Server.java` and change this line:

```java
public static final String PASSWORD_HASH = hashPassword("yourpassword123");
```

Then recompile:
```bash
javac *.java
```

---

## How it Works

```
Terminal Client  ──┐
Terminal Client  ──┤                        ┌── Terminal Client
Browser Client   ──┼──► Java Server (5000) ─┼── Browser Client
Phone Client     ──┤                        └── Phone Client
                 ──┘
```

- Server peeks at the first 4 bytes of every connection
- If it looks like an HTTP request → handled as WebSocket (browser)
- Otherwise → handled as raw socket (terminal)
- All clients share the same broadcast list — everyone sees everyone's messages

---

## Security

| Feature | Details |
|---|---|
| Password | SHA-256 hashed, never sent in plain text |
| Rate limiting | Max 10 messages per 5 seconds |
| Connection cap | Max 20 simultaneous clients |
| Username sanitization | Strips special characters, max 20 chars |
| Message length | Messages over 500 characters are dropped |
| Thread pool | Fixed size — prevents resource exhaustion |

---

## Built With

- Java standard library only
- WebSocket protocol implemented from scratch (RFC 6455)
- SHA-256 and SHA-1 via `java.security.MessageDigest`
- ngrok for internet tunneling

---

## Author

Made by [superduperpiyuxh](https://github.com/superduperpiyuxh)
