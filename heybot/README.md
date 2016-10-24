# heybot

###### Purpose:

**heybot** is my best friend on my daily development activities. It's designed to help developers on their day-to-day development activities. It eases *some* chore development activities and improves your productivity. *It's worth trying it.*

###### Requirements:

**heybot** is mainly designed to work in **subversion**, **redmine** and **slack** environment. If you use [redmine](http://www.redmine.org/) as project management/issue tracking application, [subversion](https://subversion.apache.org/) as version control system and [slack](https://slack.com/) as communication environment then **heybot** can help you. For other ecosystems, you can fork the project or contribute directly to this project. All useful contributions are wellcome.

###### Usage:

It's very easy to use **heybot** and its call syntax is like commanding a bot. It's as easy as saying this:

```
$ heybot -do something.hb
```

This is the main syntax and now you've learned most of it. You write details into a file with extension **.hb**. Then you say to **heybot**:
	
> Hey bot! Do my job which is defined in the file I gave.
	
For example; I want to begin a redmine issue with creating a branch and checking it out to my workspace, I call this:
	
```
$ heybot -do begin_issue.hb
```

As a common practice a **.hb** file starts with parameter *OPERATION*  and it is followed by required parameters to do that operation metioned. In [workspace](https://github.com/csonuryilmaz/utilities/tree/master/heybot/workspace) you can find example **.hb** templates for each operation. You can copy them, and define your own operation by filling parameters. Also it's a best practice to give you **.hb** files a readable name. For ex: send_local_changes_to_test_env.hb

Let's dive into some details and explain them with examples. Below there is a list of operations that you can use with heybot.
	
**1. OPERATION= Upload** 

It uploads local changes in the working copy (output of *svn st* command) to a remote server by SFTP protocol.

Required parameters:

- HOST= IP of remote server to connect.
- USERNAME= Username to login remote server.
- PASSWORD= Password to login remote server.
- REMOTE_PATH= Remote working directory to send changes.

Optional parameters:

- SOURCE_PATH= Local working directory to take changes from. If not given or empty, *current working directory* is assumed. (pwd)

Example:

```
# File: send_local_changes_to_test_env.hb

# operation
OPERATION=upload

# required parameters
HOST=192.168.2.1
USERNAME=smith
PASSWORD=sm123
REMOTE_PATH=/var/www/html/myproject/

# optional parameters
SOURCE_PATH=/Users/smith/NetBeansProjects/myproject/

```
	
**2. OPERATION= Cleanup**

It deletes issues (branches) which is *closed*,*deployed* or any status you defined, from local working directory. By deleting passive branches it saves your disk space. It is meaningful when you use *one issue is resolved in one branch* paradigm.

Required parameters:

- LOCAL_PATH= Branch local working directory used as workspace.
- STATUS= Status/Statuses to check for cleanup operation.(comma seperated)
- REDMINE_TOKEN= Redmine API access key taken from [my account page](http://www.redmine.org/projects/redmine/wiki/RedmineAccounts).
- REDMINE_URL= Redmine API url. (most of the time this is root url of your redmine installation)

Optional parameters:

- LIMIT= Maximum count to delete branches. If not given or empty, *unlimited* is assumed.

Example:

```
# File: delete_closed_branches.hb

# operation
OPERATION=cleanup

# required parameters
LOCAL_PATH=/Users/smith/NetBeansProjects/web/branch
STATUS=Closed,Deployed
REDMINE_TOKEN=abab3a53c34f66b92fg5cdcbb3bb95a3c78d862e
REDMINE_URL=https://test-apps.sourcerepo.com/redmine/test

# optional parameters
LIMIT=10

```
	
**3. Deploy**

*todo*

**4. OPERATION= Review**

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
- MERGE_ROOT= Normally branch is created with the same name (or hierarchy) of trunk project. If a custom method is applied and you need to state the root project (path) to merge, fill this parameter with an eligible value.

Example:

```
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
MERGE_ROOT=/myproject

```

**4. OPERATION= Check-New**

It checks whether *redmine* has new issues with given status(optional) and sends a notification message to defined *slack* channel/user. (Post to channel configuration is done from slack panel. Please, check out below notes.) For example; you can get notifications about newly added issues from a redmine project.

Required Parameters:

- LAST_ISSUE= Last issue number that has been notified about. (This parameter will be modified by *heybot* after each execution.) If doesn't have value, 0 assumed.
- PROJECT= Project name to follow new issues.
- REDMINE_TOKEN= Redmine API access key taken from [my account page](http://www.redmine.org/projects/redmine/wiki/RedmineAccounts).
- REDMINE_URL= Redmine API url. (most of the time this is root url of your redmine installation)
- WEBHOOK_URL= Slack incoming webhook to send notifications.

Optional Parameters:

- ISSUE_STATUS= Check status of newly added issue with this defined value. If empty, there will be no status check.

Example:

```
# File: check_new_issue.hb

# operation
OPERATION=check-new

# required parameters
LAST_ISSUE=892
PROJECT=support
REDMINE_TOKEN=abab3a53c34f66b92fg5cdcbb3bb95a3c78d862e
REDMINE_URL=https://test-apps.sourcerepo.com/redmine/test
WEBHOOK_URL=https://hooks.slack.com/services/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

# optional parameters
ISSUE_STATUS=New

```


###### Notes:

**1. How to obtain slack incoming webhook URL?**

Go to yourteam.slack.com/apps/build/custom-integration and click on *Incoming Webhooks*, then select a channel or user you want to post your messages to.

Once done, you’ll see your incoming webhook integration’s configuration page.

Scroll down and there’ll be a Webhook URL in the format https://hooks.slack.com/services/TXXXXXXXX/BXXXXXXXX/token. Save that URL somewhere, we’ll need it later. You can further change the icon and name of the integration in this page itself.




**things to do on my day off**

- [ ] Send new version is deployed e-mail to some recipients like newsletter when a version is deployed. (after deploy operation)
- [ ] Auto sync start date (in progress),end date (deployed/closed) and *status* with related issues. Support <--> Other projects (web,mobile,cronint)
- [ ] Auto add related issue's assignee to other related issue's watcher list. Support <--> Other projects (web,mobile,cronint)
- [ ] When a branch is detected in a repository then auto-start the related issue.
- [ ] Cleanup: Append issue number to commit messages.
- [ ] Release script to execute when heybot new version is ready.
