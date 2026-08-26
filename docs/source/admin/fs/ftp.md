(ftp-settings)=
# FTP settings

You can index files remotely using FTP.

Here is a list of FTP settings:

| Name               | Environment Variable         | Default value   | Documentation                      |
|--------------------|------------------------------|-----------------|------------------------------------|
| `fs.provider`      | `FSCRAWLER_FS_PROVIDER`      | `"local"`       | Set it to `"ftp"` for FTP crawling |
| `fs.ftp.hostname`  | `FSCRAWLER_FS_FTP_HOSTNAME`  | `null`          | Hostname                           |
| `fs.ftp.port`      | `FSCRAWLER_FS_FTP_PORT`      | `21`            | Port                               |
| `fs.ftp.username`  | `FSCRAWLER_FS_FTP_USERNAME`  | `anonymous`     | {ref}`ftp_login`                   |
| `fs.ftp.password`  | `FSCRAWLER_FS_FTP_PASSWORD`  | `null`          | {ref}`ftp_login`                   |


```{deprecated} 3.1

The top-level `server.*` settings (`server.hostname`, `server.port`, `server.username`,
`server.password`, `server.protocol`) are deprecated and will be removed in a future version.
Use `fs.provider: "ftp"` and `fs.ftp.*` instead.

When a deprecated `server.*` field is used, FSCrawler logs a WARN that shows the replacement
key, for example:

`Setting server.hostname is deprecated and will be removed in a future version. Please use fs.ftp.hostname: "mynode.mydomain.com" instead.`
```

(ftp_login)=
## Username / Password

Let's say you want to index from a remote server using FTP:

-  FS URL: `/path/to/data/dir/on/server`
-  Server: `mynode.mydomain.com`
-  Username: `username` (default to `anonymous`)
-  Password: `password`
-  Provider: `ftp`
-  Port: `21` (default to `21`)

```yaml
name: "test"
fs:
  provider: "ftp"
  url: "/path/to/data/dir/on/server"
  ftp:
    hostname: "mynode.mydomain.com"
    port: 21
    username: "username"
    password: "password"
```
