# com.realityexpander.ktor-catsvdogs
Ktor server for "Cats Vs Dogs" 2-player game for Android

Android app here: https://github.com/realityexpander/CatsVsDogsAndroid

[<img src="https://user-images.githubusercontent.com/5157474/211963104-ac29263a-7c03-489b-b69c-a083abc9fb6c.png" width=300/>](https://github.com/realityexpander/CatsVsDogsAndroid)

## Deploy steps

1. Download Git Bash (only if on Windows)

2. Go to your users folder and open the .ssh folder. Then open Git Bash / Terminal there and generate a key pair:<br>
`ssh-keygen -m PEM -t rsa`

3. Copy the key to your server:<br>
   `ssh-copy-id -i <keyname> <user>@<host>`

5. Login to your Ubuntu server via SSH:<br>
   `ssh -i <keyname> <user>@<host>`

6. Update dependencies:<br>
   `sudo apt update`

7. Install Java:<br>
   `sudo apt-get install default-jdk`

8. Open /etc/ssh/sshd_config:<br>
   `sudo nano /etc/ssh/sshd_config`

9. Put this string in there, save with `Ctrl+S` and exit with `Ctrl+X`:<br>
   `KexAlgorithms curve25519-sha256@libssh.org,ecdh-sha2-nistp256,ecdh-sha2-nistp384,ecdh-sha2-nistp521,diffie-hellman-group-exchange-sha256,diffie-hellman-group14-sha1,diffie-hellman-group-exchange-sha1,diffie-hellman-group1-sha1`

10. Restart the sshd service:<br>
    `sudo systemctl restart sshd`

11. Create a systemd service for your Ktor server:<br>
    `sudo nano /etc/systemd/system/jwtauth.service`

12. Paste this configuration in this service, then save with `Ctrl+S` and exit with `Ctrl+X`:<br>

```
[Unit]
    Description=Auth Service
    After=network.target
    StartLimitIntervalSec=10
    StartLimitBurst=5

[Service]
Type=simple
Restart=always
RestartSec=1
User=root
EnvironmentFile=/etc/environment
ExecStart=/usr/lib/jvm/default-java/bin/java  -jar /root/jwtauth/jwtauth.jar

[Install]
WantedBy=multi-user.target
```

13. Launch the service:<br>
    `sudo systemctl start jwtauth`

14. Create a symlink to automatically launch the service on boot up:<br>
    `sudo systemctl enable jwtauth`

15. Make sure, your ports are open and you forward the traffic from the standard HTTP port to 8080:<br>
```
    iptables -t nat -I PREROUTING -p tcp --dport 80 -j REDIRECT --to-ports 8080
    sudo iptables -A INPUT -p tcp --dport 80 -j ACCEPT
    sudo iptables -A INPUT -p tcp --dport 8080 -j ACCEPT
```
16. Then, save your iptables rules:<br>
    `sudo apt-get install iptables-persistent`

17. Add `JWT_SECRET=<your-secret>` and `MONGO_PW=<your-mongo-db-pw>` to your environment variables:<br>
    `sudo nano /etc/environment`

