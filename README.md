# heybot

[![GitHub stars](https://img.shields.io/github/stars/csonuryilmaz/heybot.svg?style=social&label=Star)](http://bit.ly/2ROXHzb) [![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE) [![GitHub Latest Release](https://img.shields.io/github/downloads/csonuryilmaz/heybot/latest/total.svg)](https://github.com/csonuryilmaz/heybot/releases/latest) [![Latest](https://img.shields.io/badge/release-v2.7.2.8-red.svg)](https://github.com/csonuryilmaz/heybot/releases/latest) [![Issues](https://img.shields.io/github/issues/csonuryilmaz/heybot.svg)](https://github.com/csonuryilmaz/heybot/issues) [![GitHub Releases](https://img.shields.io/github/downloads/csonuryilmaz/heybot/total.svg)](https://github.com/csonuryilmaz/heybot/releases/latest)

## Table of Contents

<!-- markdownlint-disable -->

<!-- TOC -->

- [heybot](#heybot)
    - [Table of Contents](#table-of-contents)
        - [1. Introduction](#1-introduction)
        - [2. Requirements](#2-requirements)
        - [3. Installation](#3-installation)
        - [4. Usage](#4-usage)
        - [5. Operations](#5-operations)
            - [5.1. Begin-Issue](#51-begin-issue)
            - [5.2. Begin-Issue-With-Git](#52-begin-issue-with-git)
            - [5.3. Upload](#53-upload)
            - [5.4. Upload Git Diff](#54-upload-git-diff)
            - [5.5. Upload File](#55-upload-file)
                - [5.5.1 Example Configuration for PhpStorm File Watcher](#551-example-configuration-for-phpstorm-file-watcher)
            - [5.6. Cleanup](#56-cleanup)
            - [5.7. Cleanup-Svn](#57-cleanup-svn)
            - [5.8. Review](#58-review)
            - [5.9. Check-New](#59-check-new)
            - [5.10. Sync-Issue](#510-sync-issue)
            - [5.11. Next-Version](#511-next-version)
            - [5.12. Release](#512-release)
            - [5.13. Snapshot](#513-snapshot)
        - [6. Notes](#6-notes)
            - [6.1. How to obtain slack incoming webhook URL](#61-how-to-obtain-slack-incoming-webhook-url)
        - [Test Platforms](#test-platforms)
        - [License](#license)
        - [Authors](#authors)

<!-- markdownlint-enable -->

<!-- markdownlint-disable MD013 -->

### 1. Introduction

**heybot** is my best friend on my daily development activities. It's designed to help developers on their day-to-day development activities. It eases *some* chore development activities and improves your productivity. *It's worth trying it.*

Happy coding! :coffee:

### 2. Requirements

**heybot** is mainly designed to work in

- **Redmine**
- **Slack**
- **Svn** or **Git**

environment. If you use [redmine](http://www.redmine.org/) as project management and issue tracking application, [svn](https://subversion.apache.org/) or [git](https://git-scm.com/) as version control system, [slack](https://slack.com/) and *conventional* e-mail as communication environment then **heybot** can help you.

**Note:** `git` support is brand new in `v2.*` of heybot. So, not all operations have `git` support. We're working on the issue, follow new releases for more `git` support.

For other ecosystems, you can fork the project or contribute directly to this project. All feedbacks and [contributions](https://github.com/csonuryilmaz/heybot/blob/master/CONTRIBUTING.md) are welcome. Source code is a java project which is developed in [Netbeans](https://netbeans.apache.org/) IDE.

### 3. Installation

Heybot is tested and works on Mac OS X and Gnu/Linux distributions. The most important dependency is Java Runtime Environment for `heybot`.

Java 1.8+ (jre/jdk) have to be installed before **heybot** installation. You can check whether it's installed and version is sufficient by,

```bash
$ java -version
java version "1.8.0_65"
Java(TM) SE Runtime Environment (build 1.8.0_65-b17)
Java HotSpot(TM) 64-Bit Server VM (build 25.65-b01, mixed mode)
```

To **install** or **update** `heybot` easily, you can use the [install.sh](https://raw.githubusercontent.com/csonuryilmaz/heybot/master/install.sh) script using `cURL`:

```bash
bash -c "$(curl -sSL https://raw.githubusercontent.com/csonuryilmaz/heybot/master/install.sh)"
```

or `wget`:

```bash
bash -c "$(wget -qO- https://raw.githubusercontent.com/csonuryilmaz/heybot/master/install.sh)"
```

If `cURL` or `wget` method doesn't work for you, you can follow below steps manually;

- download latest version of **heybot** from [latest release](https://github.com/csonuryilmaz/heybot/releases/latest)
- extract archive
- execute installation script

```bash
wget https://github.com/csonuryilmaz/heybot/releases/download/2.7.2.8/heybot-2.7.2.8.tar.gz
tar -zxvf heybot-2.7.2.8.tar.gz
cd heybot-2.7.2.8
chmod a+x install.sh
./install.sh
```

Above command will downlod **heybot** archive into current directory.

In order to download into different directory, for example `~/Downloads`

```bash
wget https://github.com/csonuryilmaz/heybot/releases/download/2.7.2.8/heybot-2.7.2.8.tar.gz -P ~/Downloads
tar -zxvf ~/Downloads/heybot-2.7.2.8.tar.gz
chmod a+x heybot-2.7.2.8/install.sh
heybot-2.7.2.8/install.sh
```

It will ask for sudo permission at installation process, so your user should be a sudoer. (Or maybe you can run installation script as root, but it's not recommended.)
If everything is okay, you should see (ascii) logo and version information when you execute

```bash
$ heybot -v
  _           _       _
 | |_ ___ _ _| |_ ___| |_
 |   | -_| | | . | . |  _|
 |_|_|___|_  |___|___|_|
         |___|

 2.7.2.8

Copyright (c) 2017 Onur Yılmaz
MIT License: <https://github.com/csonuryilmaz/heybot/blob/master/LICENSE.txt>
This is free software: you are free to change and redistribute it.
There is NO WARRANTY, to the extent permitted by law.
```

When you call **heybot** with no parameter it'll print help documentation

```bash
$ heybot
  _           _       _
 | |_ ___ _ _| |_ ___| |_
 |   | -_| | | . | . |  _|
 |_|_|___|_  |___|___|_|
         |___|

 2.7.2.8

usage: heybot -d <arg> [-h] [-v] [-l] [-lp <arg>] [-i <arg>] [-s <arg>]
       [-o <arg>] [-r <arg>] [-e <arg>] [-c <arg>]

Designed to help developers on their day-to-day development activities.
Works in subversion and redmine ecosystem.

 -d,--do <arg>             Heybot operation file with .hb extension.
                           something.hb
 -h,--help                 Prints this help message.
 -v,--version              Prints detailed version information.
 -l,--list                 Lists all operation files in workspace.
 -lp,--list-prefix <arg>   Lists operation files in workspace which starts
                           with given value.
 -i,--insert <arg>         Inserts (by replacing if exists) given
                           parameter values of an operation.
 -s,--select <arg>         Selects all parameters with values of an
                           operation
 -o,--open <arg>           Opens operation file by default external editor
                           in order to view or modify.
 -r,--remove <arg>         Removes operation file from workspace.
 -e,--editor <arg>         Sets default external editor used when a file
                           needs to be viewed or edited.
 -c,--copy <arg>           Copies an operation file as a new operation
                           file in workspace.

Report bugs to "csonuryilmaz@gmail.com"
  or drop an issue @ http://bit.ly/2S83xvy
Releases: http://bit.ly/2BukdrR
Document: http://bit.ly/2Sd8zqQ

Happy coding!
```

### 4. Usage

It's very easy to use **heybot** and its call syntax is like commanding a bot. It's as easy as saying this

```bash
heybot -do something.hb
```

Also you can shorten command like

```bash
heybot something
```

This is the main syntax and now you've learned most of it. You write details into a configuration file with extension **.hb**. Then you say to **heybot**

> Hey bot! Do my job (operation) which is defined in the file I gave.

For example; I want to begin a redmine issue with creating a branch and checking it out to my workspace, I call this

```bash
heybot -do begin-issue-1293.hb
```

or shortly

```bash
heybot begin-issue-1293
```

In [templates](https://github.com/csonuryilmaz/heybot/tree/master/heybot/templates) folder you can find example **.hb** templates for each operation. You can copy them, and define your own operation by filling parameters.

**Note:** :no_entry_sign: :memo: @todo **templates** Will be reviewed and updated!

Let's dive into some details and explain them with examples.

### 5. Operations

Below there is a list of operations that you can use with heybot.

#### 5.1. Begin-Issue

It's used when starting to develop a Redmine issue. It creates a new branch from trunk, downloads Subversion branch into local disk, updates issue status and triggers your IDE to begin working.

Required parameters:

- REDMINE_URL= Redmine API url. (most of the time this is root url of your redmine installation)
- REDMINE_TOKEN= Redmine API access key taken from [my account page](http://www.redmine.org/projects/redmine/wiki/RedmineAccounts).
- ISSUE= Redmine issue number which will be developed. (integer)
- REPOSITORY_PATH= Subversion repository path. (Most of the time `Repository Root` field when `svn info` is executed on local working copy.)
- TRUNK_PATH= Subversion (project) trunk folder, which is defined **relative** to `Repository Root`.
- BRANCHES_PATH= Subversion branches folder, which is defined **relative** to `Repository Root`.

Optional parameters:

- ASSIGNEE_ID= Redmine `user_id` which identifies Redmine user. (After you logged in Redmine, click on your username on the left-hand side to open your activity page. Redmine `user_id` is the number at the end of the URL. For example, if URL is `http://kyrepo-apps.sourcerepo.com/redmine/kyrepo/users/5` then `5` is your Redmine `user_id`.)
  - **Note:** If you use this parameter, heybot will check whether you're `ASSIGNEE` of the given `ISSUE`. So it'll prevent you from mistakenly begining another developer's issue.
- BEGAN_STATUS= Redmine issue status which will be updated to. For example, `in progress` for a started issue.
- WORKSPACE_PATH= If not empty, heybot will download created branch into your local disk. Equivalent to `svn checkout` of your branch.
- IDE_PATH= If not empty, heybot will trigger IDE with downloaded project folder. So you can start working immediately. Parameter is used in two ways
  - When only IDE executable path is given, it will append project folder at the end of the parameter and run. Example;
    - `/home/onur/netbeans-8.2/bin/netbeans`
  - When a command is given with an insertion mask about project folder (`%s` syntax is used), it will replace `%s` with project folder and run. Example;
    - `open %s/Kitapyurdu.xcodeproj/`
- REFRESH_IF_EXISTS= While creating new branch from trunk, if branch exists normally heybot will do nothing and continue to other steps. (checkout, issue status update vs.) But if you want to refresh branch and create a new one at repository although it exists, then give `true` value for this parameter. This is an exceptional case and most of the its value will be `false`.
- CACHE_ENABLED= Heybot uses cache internally to optimize checkout process. I strongly recommend this option to be `true` for faster beginning of issus. Otherwise it'll always fresh checkout project; depending on your internet speed and projectg size it may be a slow process.
- SWITCH_FROM_ISSUE= Also there is another option to speed up checkout process when you don't use `CACHE_ENABLED`. If you have completed development of some issues and you don't need them in the future; by giving its number as value, heybot will convert it into new `ISSUE` so you will save up from local disk space and also it'll be a faster checkout because of `svn switch`.
- CHECKOUT_IF_SWITCH_FAILS= If switching from an existing issue fails in someway, and you don't want the checkout process to be broken, setting it `true` will checkout project folder in traditional, slower way.

If you want to execute some command at the end of begin issue process below use below **optional** parameters for SSH credentials and executable.

- REMOTE_HOST= Hostname or IP of remote machine which has an eligible SSH access with username and password.
- REMOTE_USER= SSH user's username.
- REMOTE_PASS= SSH user's password.
- REMOTE_PORT= If empty, default 22 is assumed. Enter integer value if server has custom port for SSH connections.
- REMOTE_EXEC= Enter any bash command here. It may be a simple executable bash file or a combination of piped commands. Internally when heybot connected to host by SSH, working directory is user's home directory. For example; `/home/onur/` So for successful execution, test your command and be sure it's working on remote host's user home directory. Then it'll work in heybot without any problem. If entered command contains **$issue** keyword, all occurences will be replaced with the value of **ISSUE** parameter.

Example:

```properties
# File: begin-i3436.hb

OPERATION=begin-issue

# ************
# * required *
# ************

# credentials (redmine)
REDMINE_URL=https://test-apps.sourcerepo.com/redmine/test
REDMINE_TOKEN=abab3a53c34f66b92fg5cdcbb3bb95a3c78d862e

# issue
ISSUE=3436

# repository (svn)
REPOSITORY_PATH=https://test.sourcerepo.com/test/projectrepo
TRUNK_PATH=awesomeproj
BRANCHES_PATH=branches

# ************
# * optional *
# ************

CACHE_ENABLED=true
REFRESH_IF_EXISTS=false

# issue
BEGAN_STATUS=in progress
ASSIGNEE_ID=23

# local
WORKSPACE_PATH=/home/onur/sourcerepo/test/branches
IDE_PATH=/home/onur/netbeans-8.2/bin/netbeans
SWITCH_FROM_ISSUE=
CHECKOUT_IF_SWITCH_FAILS=

# remote command execution
REMOTE_HOST=192.10.10.12
REMOTE_USER=onuryilmaz
REMOTE_PASS=nbvT03js
REMOTE_EXEC=php create-issue/app.php $issue
REMOTE_PORT=
```

#### 5.2. Begin-Issue-With-Git

This operation usually is used when you begin a new Redmine development issue. (feature, bug fix vs.) Although some parameters are optional, if you use all of them, `heybot` provides below features:

- Checks whether issue is assigned to developer for possible mistakes
- Checks whether `git` credentials are valid for other `git` operations
- Supports `ssh` public key or `http(s)` authentication for remote `git` operations
- To eliminate possible repository failures, uses internal cache for repository, so clones repository from remote once, and on every new issue provides you fresh consistent branch fastly
- Creates new branch on local workspace, switches to that branch and if the branch doesn't exist on remote, pushes the branch
- If branch exists on local workspace, pulls new updates to branch and if the branch is behind, for example you've pushed some new commits from another machine or your colleague pushed some new commmits, pulls new commits with rebase and fast-forward the branch
- Supports and uses `git stash` feature when fast-forward the branch if the branch has local uncommitted modifications
- Makes `git config` modifications on the branch which doesn't affect your global git configuration, supports `git config` *user.name* and *user.email* modifications
- Updates Redmine issue status to defined status, for example `in progress`
- If defined, executes remote commands using `ssh` connection on a remote machine, for example used when application under development needs some configuration for runtime
- Triggers your favorite IDE with ready branch, and from now on you can begin working on the issue

Required parameters:

- REDMINE_URL= Redmine API URL which is, most of the time, root URL of your redmine installation.
- REDMINE_TOKEN= Redmine API access key taken from [my account page](http://www.redmine.org/projects/redmine/wiki/RedmineAccounts).
- ISSUE= Redmine issue number which will be developed. `integer`
- GIT_PROTOCOL= Used protocol for remote git operations. `ssh || https || http`
- GIT_REPOSITORY= Repository URL for master branch. Don't need to prefix with `ssh` or `http(s)` because of GIT_PROTOCOL parameter. But repository URL must be compatible with GIT_PROTOCOL.
- If GIT_PROTOCOL == `https || http` then
  - GIT_USERNAME= Git user, username to authenticate `git`.
  - GIT_PASSWORD= Git user, password to authenticate `git`.
- If GIT_PROTOCOL == `ssh` then
  - SSH_PRIVATE_KEY= `ssh` key file path on local machine, to authenticate `git`. To generate your `ssh` public key, follow `git` server's instructions, for example [gitlab](https://docs.gitlab.com/ee/ssh/)
- WORKSPACE_PATH= Location on your machine which keeps local branches. `heybot` creates a new branch folder with ISSUE number for every begun issue and every branch is isolated from each other on local workspace.

Optional parameters:

- ASSIGNEE_ID= Redmine `user_id` which identifies Redmine user. `integer`
  - If parameter is filled, `heybot` checks whether issue is assigned to user so prevents developer from mistakenly begining another developer's issue.
  - After you logged in Redmine, click on your username on the left-hand side to open your activity page. Redmine `user_id` is the number at the end of the URL. For example, if URL is `http://www.example.com/users/5` then `5` is your Redmine `user_id`.
- BEGUN_STATUS= Redmine issue status which will be updated to, for example, `in progress` for a started issue.
  - If parameter is filled, `heybot` tries to update issue status. If the issue status is already in given status, it won't be a problem. In order to be successful, Redmine workflow configuration must be compatible with update, otherwise the issue status will remain as is.
- IDE_PATH= Local path of IDE executable file.
  - If parameter is filled, used as a last step when the local branch is ready. IDE is triggered with the local branch parameter so you can start working immediately on the issue.
  - Parameter can be used in two ways:
    - When only IDE executable path is given, for example like this `/home/onur/netbeans-8.2/bin/netbeans`, the local branch folder will be appended at the end of the parameter and the result command'll be executed.
    - When a command is given with an insertion mask about the local branch folder, `%s` syntax is used for example like this `open %s/Kitapyurdu.xcodeproj/`, `%s` will be replaced with the local branch folder and the result command'll be executed.
- GIT_CONFIG_USER_NAME= Git `user.name` configuration on the local branch.
  - If parameter is filled, `heybot` makes `git config` to modify `user.name` on the local branch.
  - It won't affect other branches, `git` projects or your global `git` configuration.
  - If desired, this configuration can be made by developer manually with `git config` at a later time, or global config can be used.
- GIT_CONFIG_USER_EMAIL= Git `user.email` configuration on the local branch.
  - If parameter is filled, `heybot` makes `git config` to modify `user.email` on the local branch.
  - It won't affect other branches, `git` projects or your global `git` configuration.
  - If desired, this configuration can be made by developer manually with `git config` at a later time, or global config can be used.
- PROJECT_NAME= If you need to override project name for some reason, which is extracted from repository url, you can use this optional parameter. Project name is also used in branch naming. By default, when heybot creates local working copy (or in other words, local branch), it uses last token from repository URL as project name. For example;
  - When `GIT_REPOSITORY=git@gitlab.example.com:root/foobarproj.git` then
    - created local branch is `$WORKSPACE_PATH/i$ISSUE/foobarproj`, checkout branch name is `i$ISSUE/foobarproj`
  - If `PROJECT_NAME=FooBarProj` is given as project name in this case, then
    - created local branch is `$WORKSPACE_PATH/i$ISSUE/FooBarProj`, checkout branch name is `i$ISSUE/FooBarProj`

If you need to execute some commands when the local branch is ready, use below optional parameters for `ssh` connection and remote command execution.

- REMOTE_HOST= Hostname or IP of remote machine(s) which has an eligible `ssh` access with username and password.
  - Comma seperated multiple machines are accepted. Remote command exection will be done in order, one by one.
- REMOTE_USER= `ssh` user's username.
- REMOTE_PASS= `ssh` user's password.
- REMOTE_PORT= If empty, default port 22 is assumed. Fill in `integer` value if the `ssh` server has custom port for `ssh` connections.
- REMOTE_EXEC= Fill in any bash command. It may be a simple executable bash file or a combination of piped commands. Internally when `heybot` connected to host with `ssh`, current working directory is user's home directory. For example, `/home/onur/`. So, for successful execution, test your command and be sure it's working on remote host's user home directory. Then it'll work in heybot without any problem.
  - If the command contains **$issue** keyword, all occurences will be replaced with the value of ISSUE parameter.

If you need to execute some **local** commands when the local branch is ready, use below optional parameter. Its purpose is to trigger some post operations after local branch checkout. For example, `composer` or `maven` updates.

- LOCAL_EXEC= Fill in any bash command. It may be a simple executable bash file or a combination of piped commands.
  - `heybot` executes given command at local, in local branch folder directory. So, for successful execution, test your command and be sure it's working in project folder.

**Note:** For some reason git pull can make local (uncommitted) modifications which may cause branch checkout failure. (`git pull`=`git fetch + git merge`) Cache is designed to be used by `heybot` for internal purposes. It should not contain any local changes, made by `git` or **user**. So, if cache contains uncommitted or untracked changes, heybot cleans local working copy before `git pull` and branch checkout. In other words, to increase success of git pull, we make sure our working area is clean before doing the pull.

Example: `http` || `https` Git Credentials

```properties
# File: begin-aws-task.hb

OPERATION=begin-issue-with-git

# ************
# * required *
# ************

# credentials (redmine)
REDMINE_URL=https://test-apps.sourcerepo.com/redmine/test
REDMINE_TOKEN=abab3a53c34f66b92fg5cdcbb3bb95a3c78d862e

# issue
ISSUE=5214

# git repo
GIT_PROTOCOL=https
GIT_REPOSITORY=gitlab.example.com/root/project1.git
GIT_USERNAME=onur.yilmaz
GIT_PASSWORD=pwdT03js

# location which keeps local branches
WORKSPACE_PATH=/home/awesomedev/projects

# ************
# * optional *
# ************

# project/branch naming
PROJECT_NAME=Project1

# git config
GIT_CONFIG_USER_NAME=awesome.dev
GIT_CONFIG_USER_EMAIL=awesome.dev@example.com

# issue
BEGUN_STATUS=in progress
ASSIGNEE_ID=23

# open ide to start development
IDE_PATH=/opt/netbeans/bin/netbeans

# remote command execution
REMOTE_HOST=192.10.10.12
REMOTE_USER=AwesomeDev
REMOTE_PASS=pwdT03js
REMOTE_EXEC=php create-issue/app.php $issue
REMOTE_PORT=
```

Example: `ssh` Git Credentials

```properties
# File: begin-aws-task.hb

OPERATION=begin-issue-with-git

# ************
# * required *
# ************

# credentials (redmine)
REDMINE_URL=https://test-apps.sourcerepo.com/redmine/test
REDMINE_TOKEN=abab3a53c34f66b92fg5cdcbb3bb95a3c78d862e

# issue
ISSUE=5214

# git repo
GIT_PROTOCOL=ssh
GIT_REPOSITORY=git@gitlab.example.com:root/project1.git
SSH_PRIVATE_KEY=~/.ssh/id_rsa

# location which keeps local branches
WORKSPACE_PATH=/home/awesomedev/projects

# ************
# * optional *
# ************

# project/branch naming
PROJECT_NAME=Project1

# git config
GIT_CONFIG_USER_NAME=awesome.dev
GIT_CONFIG_USER_EMAIL=awesome.dev@example.com

# issue
BEGUN_STATUS=in progress
ASSIGNEE_ID=23

# open ide to start development
IDE_PATH=/opt/netbeans/bin/netbeans

# remote command execution
REMOTE_HOST=192.10.10.12
REMOTE_USER=AwesomeDev
REMOTE_PASS=pwdT03js
REMOTE_EXEC=php create-issue/app.php $issue
REMOTE_PORT=
```

#### 5.3. Upload

It uploads local changes in the working copy (output of `svn st` command) to a remote server by SFTP protocol.

Required parameters:

- HOST= IP or hostnames of remote server(s) to connect. (Comma seperated multiple servers are accepted.)
- USERNAME= Username to login remote server.
- PASSWORD= Password to login remote server.
- REMOTE_PATH= Remote working directory to send changes.

Optional parameters:

- SOURCE_PATH= Local working directory to take changes from. If not given or empty, **current working directory** (where heybot command is triggered) is assumed. (`pwd`)

Example:

```properties
# File: send-local-changes-to-test-servers.hb

OPERATION=upload

# ************
# * required *
# ************

HOST=192.168.2.1,testWeb
USERNAME=smith
PASSWORD=sm123
REMOTE_PATH=/var/www/html/myproject/

# ************
# * optional *
# ************

SOURCE_PATH=/Users/smith/NetBeansProjects/myproject/
```

#### 5.4. Upload Git Diff

It uploads `git diff` changes in the local working copy to a remote server by SFTP protocol. It's more powerful and functional when compared to `upload` operation. With optional parameters, `upload-git-diff` operation responds to various cases described with below examples.

Required parameters:

- REMOTE_HOST= Hostname or IP of remote machine which has an eligible SFTP access with username and password. (Comma seperated multiple servers are accepted.)
- REMOTE_USER= SFTP user's username.
- REMOTE_PASS= SFTP user's password.
- MODE= Value is either `P2P` or `W2W`
  - `P2P`: Path-to-path working mode. Used for uploading changes from one local path to one remote path. No need to be logical relation between local path and remote path. For example, you're working on a branch and while developing you upload local changes to `test` or `preprod` environment to make some tests.
  - `W2W`: Workspace-to-workspace working mode. Used for uploading local branch changes to remote deployed branch. It's meaningful when used with `begin-with-git` operation. You can configure local workspace and remote workspace once, and then only by modifying (on-the-fly) `ISSUE` parameter you can upload any branch's changes to its remote equivalent.

Optional parameters:

- REMOTE_PORT= If empty, default 22 is assumed. Enter `integer` value if server has custom port for SFTP connections.
- When `MODE` is **W2W**:
  - ISSUE= Redmine issue number which is in progress or being developed. `integer`
  - SOURCE_WORKSPACE= It's the path (parent folder) where you keep all the branches in your localhost. Root directory which contains different branches.
  - REMOTE_WORKSPACE= It's the equivalent root path of local workspace directory on remote host. For example, it's the folder where you keep all the branches on remote host.
  - PROJECT_PATH= Identifies the project folder structure in **workspace**. For example, `SOURCE_WORKSPACE` is `/home/onur/awesomeProjs` and we have two branches related with two different `Redmine` issues: `i100/aProj` and `i200/aProj`. In this case, `PROJECT_PATH` should be `i$issue/aProj`. `$issue` marker will be replaced with `ISSUE` value at runtime.
- When `MODE` is **P2P**:
  - SOURCE_PATH= Local working directory to take changes (`git diff`) from.  If not given or empty, **current working directory** (where `heybot` is triggered) is assumed. (`pwd`)
  - REMOTE_PATH= Remote working directory to send/upload changes.

Example:

```properties
# File: send-git-diff-to-test.hb

OPERATION=upload-git-diff

# ************
# * required *
# ************

# working mode
MODE=P2P

# remote (ssh)
REMOTE_HOST=192.168.2.1,testWeb
REMOTE_USER=smith
REMOTE_PASS=gsg727s

# ************
# * optional *
# ************

SOURCE_PATH=/Users/smith/NetBeansProjects/aProj
REMOTE_PATH=/var/www/html/test/aProj
```

When you run `heybot send-git-diff-to-test`, `heybot` will upload `git diff` of local `/Users/smith/NetBeansProjects/aProj` to `/var/www/html/test/aProj`.

If you remove `SOURCE_PATH`, you can run the operation from any project folder. Current working directory will be set as `SOURCE_PATH` automatically. It's useful when working with different branches at the same time. You can upload local `branch 1` or `branch 2` with the same configuration file to same remote path.

```properties
# File: send-git-diff.hb

OPERATION=upload-git-diff

# ************
# * required *
# ************

# working mode
MODE=W2W

# remote (ssh)
REMOTE_HOST=192.168.2.1,testWeb
REMOTE_USER=smith
REMOTE_PASS=gsg727s

# ************
# * optional *
# ************

SOURCE_WORKSPACE=/Users/smith/gitlab/branches
REMOTE_WORKSPACE=/var/www/html/branches
PROJECT_PATH=i$issue/aProj
ISSUE=3714
```

When you run `heybot send-git-diff 100`, `heybot` will upload `git diff` of local `/Users/smith/gitlab/branches/i100/aProj` to remote `/var/www/html/branches/i100/aProj`. With same configuration file, only updating ISSUE on-the-fly parameter, you can upload several different branches to their remote equivalents.

#### 5.5. Upload File

It's designed to work with IDE file watchers. File watcher allows you to automatically run a command-line tool like compilers, formatters, or linters when you change or save a file in the IDE. If you want to upload saved or modified file to remote server, you can use upload file operation. Its one file processing logic, is more efficient than above upload operation which uploads all changes when triggered. File watcher doesn't trigger when a file or directory is deleted because after delete there is no file to work on. As similar, file watcher don't trigger when an empty directory is created, because it's not a file. But don't worry! When you add a new file into that empty directory it'll be triggered and when you create a new file with the same name, which was previously deleted, but probably has different content, it'll be triggered. On both cases you'll have the ability to upload meaningful (up-to-date) changes to server although server has some additional changes that you don't have. But those are redundant, mostly passive changes.

In a nutshell, by combining with an IDE file watcher, upload-file operation enables you to work with remote servers. While you're editing project files, it'll automatically upload saved files to remote host and keep changes in sync with your local project.

Required parameters:

- REMOTE_HOST= Hostname or IP of remote machine which has an eligible SFTP access with username and password. (Comma seperated multiple servers are accepted.)
- REMOTE_USER= SFTP user's username.
- REMOTE_PASS= SFTP user's password.
- REMOTE_WORKSPACE= It's the equivalent root path of local workspace directory on remote host. For example, it's the folder where you keep all the branches on remote host.
- SOURCE_WORKSPACE= It's the path (parent folder) where you keep all the branches in your localhost. Root directory which contains different branches.

Optional parameters:

- REMOTE_PORT= If empty, default 22 is assumed. Enter integer value if server has custom port for SFTP connections.

Example:

```properties
# File: upload-saved-file.hb

OPERATION=upload-file

# ************
# * required *
# ************

REMOTE_HOST=192.10.11.12
REMOTE_USER=onuryilmaz
REMOTE_PASS=nbvT03js
REMOTE_WORKSPACE=/var/www/html/branch
SOURCE_WORKSPACE=/home/onur/sourcerepo/web/branch

# ************
# * optional *
# ************

REMOTE_PORT=
```

Example scenario about given example .hb file:

- I keep all branches at `/home/onur/sourcerepo/web/branch` localhost.
- I open branch related with redmine issue #5421 for code editing.
- Local branch path is `/home/onur/sourcerepo/web/branch/i5421/hiPhpProject`
- I edited and saved `index.php` file whose local path is `/home/onur/sourcerepo/web/branch/i5421/hiPhpProject/index.php`.
- File watcher is triggered and heybot uploads file to remote path `/var/www/html/branch/i5421/hiPhpProject/index.php`.

Heybot assumes the same folder structure between local and remote host, so it replaces *source workspace path* with *remote workspace path*. By means of this assumption, you don't need any modification to hb file between different projects. You can use the same file watcher globally for all projects.

##### 5.5.1 Example Configuration for PhpStorm File Watcher

Open `File > Settings > Tools > File Watchers`. You will see list of file watchers. Heybot upload-file operation can be used both `Global` level or `Project` level.

Click `+` (plus) button to add a new file watcher and select `<custom>` template for new file watcher.

![tools-file-watchers](doc-images/tools-file-watchers.png)

Below, suggested settings for heybot:

![edit-file-watcher](doc-images/edit-file-watcher.png)

- Give you file watcher a `Name` related with its function.
- `File Type` should be set to `Any` but limiting to some types only can make minimal network traffic.
- `Scope` should be set to `Project Files`. You can also use other optionns to make minimal network traffic.
- As heybot is installed globally, you can enter `heybot` into `Program` textbox. Also full executable path can be used.
- Enter two arguments into `Arguments` textbox. First argument is heybot file, and second one is saved or edited file's full path. (`$FileDir$/$FileName$`)
- `Working Directory` should be set to `$ProjectFileDir$`.
- For `Advanced Options` only second option is suggested for minimal network traffic. But you can use other options also.
- `Always` or at least `On Error` is suggested for `Show Console` option in order to track file watcher activity.

Below is exported xml of above file watcher:

```xml
<TaskOptions>
  <TaskOptions>
    <option name="arguments" value="up-wktp-t12 $FileDir$/$FileName$" />
    <option name="checkSyntaxErrors" value="true" />
    <option name="description" />
    <option name="exitCodeBehavior" value="ALWAYS" />
    <option name="fileExtension" value="*" />
    <option name="immediateSync" value="false" />
    <option name="name" value="sftp-upload" />
    <option name="output" value="" />
    <option name="outputFilters">
      <array />
    </option>
    <option name="outputFromStdout" value="false" />
    <option name="program" value="heybot" />
    <option name="runOnExternalChanges" value="true" />
    <option name="scopeName" value="Project Files" />
    <option name="trackOnlyRoot" value="false" />
    <option name="workingDir" value="$ProjectFileDir$" />
    <envs />
  </TaskOptions>
</TaskOptions>
```

#### 5.6. Cleanup

It deletes issues (branches) which is **closed**, **deployed**, **merged** or any status you defined, from local workspace. Redmine issue statuses are completely configurable. By using `cleanup` operation, you can get rid of stale branches and save some disk space.

It is most useful, when using seperate folders for different issues. This is the default behaviour of `begin-issue-with-git`. It creates new folder specific to issue for each began issue. If you 're using one local repository and switch between issues by switching branches, you don't need this function since you have one folder for all branches.

Required parameters:

- REDMINE_URL= Redmine API URL which is, most of the time, root URL of your redmine installation.
- REDMINE_TOKEN= Redmine API access key taken from [my account page](http://www.redmine.org/projects/redmine/wiki/RedmineAccounts).
- REDMINE_STATUS= Redmine issue status which denotes a stale issue (or branch). One or more string, *comma seperated* values are accepted. When issue status is one of the given values, its local branch will be deleted from disk.
- WORKSPACE_PATH= Location on your machine which keeps local branches. `heybot` detects issues automatically from workspace, checks Redmine statuses and deletes if compatible with `REDMINE_STATUS` values.

Optional parameters:

- LIMIT= Maximum count to delete branches. If not given or empty, *unlimited* is assumed. For example, when `2` is given then `heybot` runs until `2` local branches are deleted. When it reaches `2` deleted branch count, it terminates.
- DELETE_WHEN_ISSUE_NOT_FOUND= Indicates that if issue is not found on Redmine, `heybot` will delete local branch or not. Boolean value `true | false`. It's useful when issue is deleted from Redmine instead of updating its status.

Example:

```properties
# File: delete_closed_branches.hb

OPERATION=cleanup

# ************
# * required *
# ************

REDMINE_URL=https://test-apps.sourcerepo.com/redmine/test
REDMINE_TOKEN=abab3a53c34f66b92fg5cdcbb3bb95a3c78d862e
REDMINE_STATUS=closed,deployed,canceled,merged

WORKSPACE_PATH=/Users/smith/NetBeansProjects

# ************
# * optional *
# ************

DELETE_WHEN_ISSUE_NOT_FOUND=true
LIMIT=10

```

#### 5.7. Cleanup-Svn

:no_entry_sign: :memo: @todo Will be reviewed and updated!

It deletes issues which is *closed*,*deployed* or any status you defined, from subversion branches path. By deleting passive branches it removes some garbage from your subversion. It is meaningful when you use *one issue is resolved in one branch* paradigm.

Required parameters:

- BRANCH_PATH= Subversion branch path that you keep your branches.
- STATUS= Status/Statuses to check for cleanup operation.(comma seperated)
- REDMINE_TOKEN= Redmine API access key taken from [my account page](http://www.redmine.org/projects/redmine/wiki/RedmineAccounts).
- REDMINE_URL= Redmine API url. (most of the time this is root url of your redmine installation)

Optional parameters:

- LIMIT= Maximum count to delete branches. If not given or empty, *unlimited* is assumed.

Example:

```properties
# File: remove_done_branches.hb

# operation
OPERATION=cleanup

# required parameters
BRANCH_PATH=https://test.sourcerepo.com/test/web/branch
STATUS=Closed,Deployed
REDMINE_TOKEN=abab3a53c34f66b92fg5cdcbb3bb95a3c78d862e
REDMINE_URL=https://test-apps.sourcerepo.com/redmine/test

# optional parameters
LIMIT=10

```

#### 5.8. Review

:no_entry_sign: :memo: @todo Will be reviewed and updated!

It updates the status of issue as *testing*, *in review* or anything you defined in your redmine configuration. Then tries to merge changes from an issue to an existing working copy. (most of the time, existing working copy is trunk) It preemptively postpones any conflicts for later resolution.

Required Parameters:

- ISSUE= Issue number(or ID) to review. (merge from)
- ISSUE_STATUS_TO_UPDATE= Issue status will be updated to defined parameter, which indicates the operation done.
- SUBVERSION_PATH= Branch subversion directory where all branches are kept.
- REDMINE_TOKEN= Redmine API access key taken from [my account page](http://www.redmine.org/projects/redmine/wiki/RedmineAccounts).
- REDMINE_URL= Redmine API url. (most of the time this is root url of your redmine installation)

Optional parameters:

- SOURCE_PATH= Existing working copy to make changes. (merge to) If not given or empty, *current working directory* is assumed. (pwd)
- ISSUE_STATUS_SHOULD_BE= The status of issue that can be reviewed and eligible to test. If issue status is not equals to this parameter, operation won't begin.

Example:

```properties
# File: review_issue.hb

# operation
OPERATION=review

# required parameters
ISSUE=892
ISSUE_STATUS_TO_UPDATE=Testing
SUBVERSION_PATH=https://test.sourcerepo.com/test/web/branch
REDMINE_TOKEN=abab3a53c34f66b92fg5cdcbb3bb95a3c78d862e
REDMINE_URL=https://test-apps.sourcerepo.com/redmine/test

# optional parameters
SOURCE_PATH=/Users/smith/NetBeansProjects/myproject/
ISSUE_STATUS_SHOULD_BE=Resolved

```

#### 5.9. Check-New

:no_entry_sign: :memo: @todo Will be reviewed and updated!

It checks whether *redmine* has new issues with given status(optional) and sends a notification message to defined *slack* channel/user. (Post to channel configuration is done from slack panel. Please, check out below notes.) For example; you can get notifications about newly added issues from a redmine project.

Required Parameters:

- PROJECT= Project name to follow new issues.
- REDMINE_TOKEN= Redmine API access key taken from [my account page](http://www.redmine.org/projects/redmine/wiki/RedmineAccounts).
- REDMINE_URL= Redmine API url. (most of the time this is root url of your redmine installation)
- WEBHOOK_URL= Slack incoming webhook to send notifications.

Optional Parameters:

- ISSUE_STATUS= Check status of newly added issue with this defined value. If empty, there will be no status check.

Internal Parameters:

- LAST_ISSUE= Last issue number that has been notified about. (This parameter will be modified by *heybot* after each execution.) If doesn't have value, 0 assumed.
- LAST_CREATED_ON= Last notified issue's created timestamp. (This parameter will be modified by *heybot* after each execution.) When last notified issue is deleted or unreachable this timestamp is used to filter new issues.

Example:

```properties
# File: check_new_issue.hb

# operation
OPERATION=check-new

# required parameters
PROJECT=support
REDMINE_TOKEN=abab3a53c34f66b92fg5cdcbb3bb95a3c78d862e
REDMINE_URL=https://test-apps.sourcerepo.com/redmine/test
WEBHOOK_URL=https://hooks.slack.com/services/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

# optional parameters
ISSUE_STATUS=New

# internal parameters
LAST_ISSUE=892
LAST_CREATED_ON=2017-04-03 11\:40\:10

```

Also you can schedule this operation with a crontab entry. For example, below entry will check new issues at 5 minutes interval;

```bash
*/5 * * * * /usr/local/bin/heybot -d check_new.hb 1> /dev/null 2> /var/www/html/heybot.log
```

#### 5.10. Sync-Issue

:no_entry_sign: :memo: @todo Will be reviewed and updated!

In our software development life cycle, we have a redmine project which is used like a _ticket system_ for our customers. When they discover a bug or need a revision, they open a new issue on that redmine project. Let's name it **support** project. All those issues on **support** project are physically related to development issues which reside in our internal development projects. Although life-cycle of a typical development issue is very detailed, life-cycle of a typical **support** issue is very simple: New -> In Progress -> Closed. (Sometimes it can have this cycle when it's paused for some time: In Progress -> On Hold -> In Progress) These states are enough for customers to track their issues roughly. When they need to see the details, they can open an issue and look at detailed statuses of related issues just in a minute.

Although we manage and update statuses of issues at our internal development projects very often in order to track our internal process, we don't have any time to sync state of internal issues to their related **support** issues. This process contains not only syncing statuses but also adding internal issues' assignees as watchers to support issues and updating start dates, end dates of support issues from internal issues correctly. Why adding assignee as watcher? Because developer, who's working with internal issue, should get latest changes and notifications from its related support issue in order to get things done correctly and on time. Why updating start and end dates? Because most of the issues aren't handled as soon as it's opened. They wait in queue for days and sometimes weeks before they are handled.

This operation is built to help us manage this mechanical process. Goal is to automatically manage status, assignee-watcher, start date and end date of an **support** issue by processing its related internal or development issues.

Required Parameters:

- SUPPORT_PROJECT= Support or ticket project name which is used by customers. (N)
- SUPPORT_NEW_STATUS= Support status which is corresponding to **new** status.
- SUPPORT_IN_PROGRESS_STATUS= Support status which is corresponding to **in progress** status.
- SUPPORT_ON_HOLD_STATUS= Support status which is corresponding to **on hold** status.
- SUPPORT_CLOSED_STATUS= Support status which is corresponding to **closed** status.
- SUPPORT_SYNC_START_DATE= Modify support issue start date from internal issues. (new -> *) **true** || **false**
- SUPPORT_SYNC_DUE_DATE= Modify support issue due date from internal issues. (* -> closed) **true** || **false**
- INTERNAL_PROJECT= Internal development projects which are used by your team. (N)
- INTERNAL_NEW_STATUS= Internal statuses which are corresponding to **new** status of SUPPORT. (N)
- INTERNAL_IN_PROGRESS_STATUS=  Internal statuses which are corresponding to **in progress** status of SUPPORT. (N)
- INTERNAL_ON_HOLD_STATUS= Internal statuses which are corresponding to **on hold** status of SUPPORT. (N)
- INTERNAL_CLOSED_STATUS= Internal statuses which are corresponding to **closed** status of SUPPORT. (N)
- SUPPORT_WATCHER= Internal issue fields to check as support issue watcher. (N)
- REDMINE_TOKEN= Redmine API access key taken from [my account page](http://www.redmine.org/projects/redmine/wiki/RedmineAccounts).
- REDMINE_URL= Redmine API url. (most of the time this is root url of your redmine installation)

Internal Parameters:

- LAST_CHECK_TIME= Last execution time of this operation. (This parameter will be modified by *heybot* after each execution. It's designed to improve efficiency of the operation. It will process changes from last execution time. So you can execute the operation very often, in short durations. If empty, current timestamp will be set as last check time.)

Notes:

- **(N)** If there are more than one value to enter, you can write all of them as comma-separated values. Please check examples.
- **NEW** In order to update *support* issue as *new* status, **all** of the related *internal* issues must be in *new* status.
- **IN_PROGRESS** In order to update *support* issue as *in progress* status **one** of the related *internal* issues is enough to be in *in progress* status.
- **ON_HOLD** In order to update *support* issue as *on hold* status **one** of the related *internal* issues is enough to be in *on hold* status.
- **CLOSED** In order to update *support* issue as *closed* status, **all** of the related *internal* issues must be in *closed* status.
- It detects **start date** from *in progress* status logs of related internal issues. Minimum internal *in progress* date will set as start date of support issue.
- It detects **due date** from *closed* status logs of related internal issues. Maximum internal *closed* date will set as due date of support issue.
- It maintains watchers of support issues automatically. First detects all watcher candidates from internal issues defined by SUPPORT_WATCHER fields. Then compares them with support issue watchers. After comparison it adds or deletes watchers.
- Some updates on issues (for example, adding a relation to an issue) don't update issue's **updated on** timestamp. In this case, heybot sync-issue operation won't catch the updated issue. This situation totally comes from redmine design, so it's not a bug. [(details)](https://github.com/csonuryilmaz/projects/issues/19)

Example:

```properties
# File: sync_support_issue.hb

# operation
OPERATION=sync-issue

# required parameters
SUPPORT_PROJECT=Support
SUPPORT_NEW_STATUS=New
SUPPORT_IN_PROGRESS_STATUS=In Progress
SUPPORT_ON_HOLD_STATUS=Feedback
SUPPORT_CLOSED_STATUS=Closed
SUPPORT_SYNC_START_DATE=true
SUPPORT_SYNC_DUE_DATE=true
INTERNAL_PROJECT=web,mobile,cronint
INTERNAL_NEW_STATUS=New
INTERNAL_IN_PROGRESS_STATUS=Rework,In Progress,Resolved,In Review,Reviewed,In Test,Accepted
INTERNAL_ON_HOLD_STATUS=Feedback
INTERNAL_CLOSED_STATUS=Closed,Canceled,Deployed
SUPPORT_WATCHER=Assignee,Reviewer
REDMINE_TOKEN=abab3a53c34f66b92fg5cdcbb3bb95a3c78d862e
REDMINE_URL=https://test-apps.sourcerepo.com/redmine/test

# internal parameters
LAST_CHECK_TIME=Sat Nov 19 15:45:11 EET 2016
```

#### 5.11. Next-Version

:no_entry_sign: :memo: @todo Will be reviewed and updated!

It enables you to maintain your new release or version of software easily, with friction free. It orchestrates redmine *version*, its related *issues* and your *codebase*. Very briefly, when you're ready to release a new version of your software;

- checks waiting issues ready to relase
- closes old version
- computes new version number (like [semantic versioning](http://semver.org/))
- creates a new version
- assigns new version to your waiting issues
- updates your codebase with new version number
- creates subversion tag for new release

@todo Documentation about *next-version* will be detailed for different use cases.

Required Parameters:

- REDMINE_TOKEN= Redmine API access key taken from [my account page](http://www.redmine.org/projects/redmine/wiki/RedmineAccounts).
- REDMINE_URL= Redmine API url. (most of the time this is root url of your redmine installation)
- MAJOR_TRACKER= Redmine issue tracker which increments feature number. [1]
- MINOR_TRACKER= Redmine issue tracker which increments revision number. [1]
- PATCH_TRACKER= Redmine issue tracker which increments bug/refactoring number. [1]
- FILTER_PROJECT= Redmine project which your software and version belongs to.
- FILTER_QUERY= List of issues (a redmine saved query) which gives ready to release and version issues.
- VERSION_TITLE= Mostly this is codename of your software and it's prefix of version title. Ex; debian-2.3.1.2. Version title is *debian*.

Optional Parameters:

- APPEND_CURRENT= Set true when you don't create a new version and append ready issues to current version. **true** || **false**
- CREATE_SVN_TAG= Set true when you want to create subversion tag for version and update your codebase with new version number. **true** || **false**
- CLOSE_PREVIOUS= Set true if you want to close previous version. It's meaningful when APPEND_CURRENT is false. Because you're creating new one, and want to close existing one. **true** || **false**
- *Note: below parameters are used when CREATE_SVN_TAG is true.*
- REPOSITORY_PATH= Subversion repository of your codebase.
- TRUNK_PATH= Trunk path of your project. (relative to REPOSITORY PATH) Don't write full path.
- APP_VERSION_FILE_PATH= Files in your project that keeps version number and must be updated every new release. These files will be modified and committed.
- APP_VERSION_FILE_PATTERN= Line which contains version information. The value of concrete version must be masked. Please look at example.
- TAGS_PATH= Tags folder path in which you keep subversion tags.

Internal Parameters:

- VERSION_TAG= Current, existing version number. TITLE + TAG is the name of redmine version. Ex; debian-2.3.1.2. Tag is *2.3.1.2*.
- VERSION_ID= Current, existing version's redmine id.
- PREVIOUS_VERSION_TAG= Previous version number. (latest before current version) It's used to recompute version tag when APPEND_CURRENT is true.

**todo:** Example and notes will be added for *next-version* .

#### 5.12. Release

:no_entry_sign: :memo: @todo Will be reviewed and updated!

#### 5.13. Snapshot

:no_entry_sign: :memo: @todo Will be reviewed and updated!

### 6. Notes

Here are some general notes that can help you in various ways.

#### 6.1. How to obtain slack incoming webhook URL

Go to yourteam.slack.com/apps/build/custom-integration and click on *Incoming Webhooks*, then select a channel or user you want to post your messages to.

Once done, you’ll see your incoming webhook integration’s configuration page.

Scroll down and there’ll be a Webhook URL in the format `https://hooks.slack.com/services/TXXXXXXXX/BXXXXXXXX/token`. Save that URL somewhere, we’ll need it later. You can further change the icon and name of the integration in this page itself.

[1]: [Detailed documentation about software versioning](https://github.com/csonuryilmaz/notes/blob/master/how_to/version_your_software_(en).md)

### Test Platforms

I've tested app on below platforms and it works fine:

- Debian GNU/Linux 7.x (wheezy)
- Debian GNU/Linux 8.x (jessie) / java 1.8.0_181
- Debian GNU/Linux 9.x (stretch) / java 1.8.0_162

If something goes wrong, let me know the situation by opening an issue. See the [contributing](CONTRIBUTING.md) file for details. Also see our [code of conduct](CODE_OF_CONDUCT.md) file for other social details.

### License

This project is licensed under the MIT License. See the [license](LICENSE.txt) file for details.

### Authors

- **Onur Yılmaz**

[![Twitter Follow](https://img.shields.io/twitter/follow/csonuryilmaz.svg?style=social)](http://bit.ly/2RCmMgx)

[![Stackoverflow Profile](https://stackoverflow.com/users/flair/1750142.png)](http://bit.ly/2qu14zS)
