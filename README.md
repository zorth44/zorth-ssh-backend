# WebSSH: A Web-Based SSH Client

## 1. Vision

To provide a secure, convenient, and centralized web interface for managing and accessing multiple SSH servers. Users can store their SSH connection details and initiate an interactive SSH session directly within their web browser, eliminating the need for local SSH clients for quick access.

## 2. Project Overview

WebSSH allows users to register, log in, save their SSH server details (host, port, username, credentials), and launch a web-based terminal connected to these servers. It consists of a **Next.js frontend** for the user interface and a **Java (Spring Boot) backend** to handle business logic, security, and the crucial SSH connection proxying.

## 3. Key Features

* **User Authentication:** Secure user registration and login.
* **SSH Profile Management:** CRUD (Create, Read, Update, Delete) operations for SSH connection profiles.
* **Secure Credential Storage:** Options for storing passwords (encrypted) or managing SSH key references. **(Security is paramount here!)**
* **One-Click Connection:** Launch an SSH session with a single click from the dashboard.
* **Interactive Web Terminal:** A fully functional terminal emulator in the browser (`xterm.js`).
* **Real-time Communication:** Utilizes WebSockets for low-latency terminal interaction.
* **Session Management:** Handles the lifecycle of an SSH session.

## 4. Architecture

The system follows a client-server architecture:

```
+-----------------+      (HTTPS / REST API)      +-----------------+      (SSH)      +-----------------+
|                 | <--------------------------> |                 | <-------------> |                 |
| Next.js Frontend|                              |  Java Backend   |                 | Target SSH Server|
| (Browser)       |                              | (Spring Boot)   |                 |                 |
|   - UI (React)  | <--------------------------> |                 |                 |                 |
|   - xterm.js    |      (WSS / WebSockets)      |                 |                 |                 |
+-----------------+                              +-----------------+                 +-----------------+
```


* **Next.js Frontend:** Renders the user interface, manages user interactions, handles API calls for data management, and establishes a WebSocket connection for the terminal session.
* **Java Backend:**
    * Provides REST APIs for user authentication and SSH profile management.
    * Manages user sessions.
    * Handles WebSocket connections.
    * **Crucially, acts as an SSH proxy:** It initiates *actual* SSH connections to the target servers using Java SSH libraries and relays data between the SSH server and the user's WebSocket connection.
* **Target SSH Server:** Any standard SSH server the user wants to connect to.

## 5. Technology Stack

* **Frontend:**
    * **Framework:** Next.js
    * **UI Library:** React
    * **Terminal Emulator:** `xterm.js`
    * **WebSocket Client:** `socket.io-client` or Native Browser WebSockets
    * **Styling:** Tailwind CSS / Material UI / Chakra UI (Choose one)
    * **State Management:** React Context / Redux Toolkit / Zustand (Optional)
* **Backend:**
    * **Language:** Java (JDK 17+)
    * **Framework:** Spring Boot 3+
    * **Security:** Spring Security (JWT or Session-based Auth)
    * **WebSockets:** Spring WebSockets
    * **SSH Library:** `JSch` or `SSHJ`
    * **Database:** PostgreSQL / MySQL / MariaDB
    * **ORM:** Spring Data JPA / Hibernate
    * **Build Tool:** Maven / Gradle
* **Database:** PostgreSQL (Recommended) or MySQL.
* **Deployment (Examples):** Docker, Nginx (as reverse proxy/SSL termination), Kubernetes (optional).

## 6. Backend (Java) Design - Core Components

1.  **UserController / AuthController:** Handles user registration, login, and profile management via REST APIs. Uses Spring Security.
2.  **SSHProfileController:** Handles CRUD operations for SSH profiles via REST APIs. Ensures users can only access their own profiles.
3.  **SSHProfileService:** Contains business logic for managing profiles. Interacts with the repository layer.
4.  **SSHProfileRepository:** Spring Data JPA repository for database interactions (`ssh_profiles` table).
5.  **WebSocketHandler (e.g., `TerminalWebSocketHandler`):**
    * Manages WebSocket lifecycle (`afterConnectionEstablished`, `handleTextMessage`, `afterConnectionClosed`).
    * On connection: Authenticates the user (e.g., via token passed in URL or initial message).
    * On "connect" message: Receives a request to start an SSH session (with a profile ID). It calls the `SSHService`.
    * Relays messages: Forwards incoming WebSocket messages to the corresponding `SSHService` instance and sends outgoing SSH data back over the WebSocket.
6.  **SSHService:**
    * Manages individual SSH connections.
    * Uses `JSch` or `SSHJ` to establish the connection (handling authentication - password/key).
    * Creates PipedInput/OutputStreams (or uses library listeners) to manage data flow to/from the SSH channel.
    * Listens for data from the SSH server and sends it back to the `WebSocketHandler`.
    * Receives input data from the `WebSocketHandler` and writes it to the SSH server.
    * Handles SSH channel events (resize, disconnect).
7.  **SecurityConfiguration:** Configures Spring Security for API endpoint protection and potentially WebSocket handshake authentication.
8.  **EncryptionService:** A dedicated service for encrypting and decrypting sensitive data (like stored SSH passwords or key passphrases) before storing/retrieving them. **Never store these in plaintext.**

## 7. Frontend (Next.js) Design - Core Components

1.  **Pages:**
    * `/login`: User login form.
    * `/register`: User registration form.
    * `/dashboard`: Lists saved SSH profiles, "Connect" button.
    * `/profiles/new`: Form to add a new profile.
    * `/profiles/[id]`: Form to edit an existing profile.
    * `/terminal/[id]`: The main page hosting the `xterm.js` terminal.
2.  **API Client:** A service/hook (`useApi`) to interact with the Java backend REST APIs.
3.  **Auth Context/Provider:** Manages user authentication state globally.
4.  **TerminalComponent:**
    * Integrates `xterm.js`.
    * Establishes a WebSocket connection to the Java backend (`/ws/ssh`).
    * Sends an initial "connect" message with the profile ID.
    * Listens for `xterm.js` input events and sends them over the WebSocket.
    * Listens for WebSocket messages and writes them to `xterm.js`.
    * Handles terminal resizing and sends "resize" messages.
5.  **WebSocket Service:** Manages the WebSocket connection lifecycle.

## 8. Database Schema (Simplified)

* **`users`**
    * `id` (PK)
    * `username` (UNIQUE)
    * `email` (UNIQUE)
    * `password_hash` (VARCHAR)
    * `created_at`
    * `updated_at`
* **`ssh_profiles`**
    * `id` (PK)
    * `user_id` (FK to `users.id`)
    * `nickname` (VARCHAR)
    * `host` (VARCHAR)
    * `port` (INT, default 22)
    * `username` (VARCHAR)
    * `auth_type` (ENUM: 'PASSWORD', 'KEY')
    * `encrypted_password` (VARBINARY/BLOB - **NEVER PLAINTEXT**)
    * `encrypted_private_key` (VARBINARY/BLOB - **NEVER PLAINTEXT**)
    * `key_passphrase_encrypted` (VARBINARY/BLOB - **NEVER PLAINTEXT**)
    * `created_at`
    * `updated_at`

## 9. WebSocket Communication Protocol (Example)

* **Client -> Server:**
    * `{ "type": "CONNECT", "profileId": "..." }`
    * `{ "type": "INPUT", "data": "ls -l\n" }`
    * `{ "type": "RESIZE", "cols": 80, "rows": 24 }`
    * `{ "type": "DISCONNECT" }`
* **Server -> Client:**
    * `{ "type": "OUTPUT", "data": "user@host:~$ ..." }`
    * `{ "type": "CONNECTED", "message": "Connection established." }`
    * `{ "type": "ERROR", "message": "Failed to connect: ..." }`
    * `{ "type": "DISCONNECTED", "message": "Session ended." }`

## 10. Security Considerations - **CRITICAL**

* **HTTPS/WSS:** *Mandatory* for all communication. Use Nginx/Apache as a reverse proxy to handle SSL/TLS.
* **Authentication:** Use strong authentication (Spring Security). JWT is a good choice for decoupling frontend/backend.
* **Authorization:** Ensure users can *only* access their own profiles and sessions. Use Spring Security's method-level or URL-based authorization.
* **Credential Encryption:** Use strong, standard Java cryptographic libraries (like JCE with Bouncy Castle) to encrypt/decrypt credentials stored in the database. The encryption key *must* be managed securely (e.g., via environment variables, secrets managers like HashiCorp Vault, AWS KMS, etc.), **NOT** hardcoded.
* **SSH Key Handling:** Storing private keys is *extremely* risky. If done, they *must* be encrypted with strong user-provided passphrases or system-managed keys. Prefer using keys already on the *backend* server or prompting for keys/passphrases on connection.
* **Input Validation:** Validate *all* input on both frontend and backend to prevent XSS, command injection (though less likely with proper SSH library use, still be mindful), and other attacks.
* **Rate Limiting & Brute Force Protection:** Implement measures to prevent brute-force login attempts and excessive connection attempts.
* **Session Timeouts:** Enforce timeouts for both web sessions and SSH sessions.
* **Logging:** Implement comprehensive logging, but be *very careful* not to log sensitive data (passwords, keys, session content).
* **Dependency Scanning:** Regularly scan both Java and JavaScript dependencies for known vulnerabilities.
* **Least Privilege:** Run the Java backend with the minimum necessary permissions.

## 11. Development & Deployment

1.  **Setup Backend:** Configure Spring Boot, database, Spring Security, WebSockets, and SSH libraries.
2.  **Setup Frontend:** Configure Next.js, `xterm.js`, and WebSocket client.
3.  **API Development:** Implement REST endpoints and WebSocket handlers.
4.  **UI Development:** Build the Next.js pages and components.
5.  **Testing:** Implement unit, integration, and end-to-end tests. **Security testing is vital.**
6.  **Containerization:** Use Docker to package the frontend and backend.
7.  **Deployment:** Deploy using Docker Compose, Kubernetes, or other suitable platforms. Configure a reverse proxy (Nginx) for routing and SSL.

## 12. Future Enhancements

* SFTP File Browser/Transfer.
* SSH Session Recording & Playback.
* Team/Shared Connections with Permissions.
* Two-Factor Authentication (2FA).
* Support for SSH Tunnels/Port Forwarding.
* Integration with Secrets Managers (Vault).

---

This README provides a blueprint. Building a secure and reliable web SSH client is a complex task, especially regarding security. Proceed with caution and prioritize security at every step.