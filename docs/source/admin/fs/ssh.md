(ssh-settings)=
# SSH settings

You can index files remotely using SSH/SFTP.

```{contents}
:backlinks: entry
```

Here is a list of SSH settings:

| Name               | Environment Variable         | Default value   | Documentation                      |
|--------------------|------------------------------|-----------------|------------------------------------|
| `fs.provider`      | `FSCRAWLER_FS_PROVIDER`      | `"local"`       | Set it to `"ssh"` for SSH crawling |
| `fs.ssh.hostname`  | `FSCRAWLER_FS_SSH_HOSTNAME`  | `null`          | Hostname                           |
| `fs.ssh.port`      | `FSCRAWLER_FS_SSH_PORT`      | `22`            | Port                               |
| `fs.ssh.username`  | `FSCRAWLER_FS_SSH_USERNAME`  | `null`          | {ref}`ssh_login`                   |
| `fs.ssh.password`  | `FSCRAWLER_FS_SSH_PASSWORD`  | `null`          | {ref}`ssh_login`                   |
| `fs.ssh.pem_path`  | `FSCRAWLER_FS_SSH_PEM_PATH`  | `null`          | {ref}`ssh_pem`                     |


```{deprecated} 3.1

The top-level `server.*` settings (`server.hostname`, `server.port`, `server.username`,
`server.password`, `server.pem_path`, `server.protocol`) are deprecated and will be removed
in a future version. Use `fs.provider: "ssh"` and `fs.ssh.*` instead.

When a deprecated `server.*` field is used, FSCrawler logs a WARN that shows the replacement
key, for example:

`Setting server.hostname is deprecated and will be removed in a future version. Please use fs.ssh.hostname: "mynode.mydomain.com" instead.`
```

(ssh_login)=
## Username / Password

Let's say you want to index from a remote server using SSH:

-  FS URL: `/path/to/data/dir/on/server`
-  Server: `mynode.mydomain.com`
-  Username: `username`
-  Password: `password`
-  Provider: `ssh`
-  Port: `22` (default to `22`)

```yaml
name: "test"
fs:
  provider: "ssh"
  url: "/path/to/data/dir/on/server"
  ssh:
    hostname: "mynode.mydomain.com"
    port: 22
    username: "username"
    password: "password"
```

(ssh_pem)=
## Using Username / PEM file

Let's say you want to index from a remote server using SSH with a PEM key file:

-  FS URL: `/path/to/data/dir/on/server`
-  Server: `mynode.mydomain.com`
-  Username: `username`
-  PEM File: `/path/to/private_key.pem`
-  Provider: `ssh`
-  Port: `22` (default to `22`)

```yaml
name: "test"
fs:
  provider: "ssh"
  url: "/path/to/data/dir/on/server"
  ssh:
    hostname: "mynode.mydomain.com"
    port: 22
    username: "username"
    pem_path: "/path/to/private_key.pem"
```

## Windows drives

When using Windows, you might want to index documents coming from another drive than `C:`.
To specify the drive, you need to use the following format:

```yaml
name: "test"
fs:
  provider: "ssh"
  url: "/D:/path/to/data/dir/on/server"
  ssh:
    hostname: "mynode.mydomain.com"
    port: 22
    username: "username"
    password: "password"
```
