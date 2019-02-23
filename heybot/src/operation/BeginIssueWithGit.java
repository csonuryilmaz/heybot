package operation;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineManagerFactory;
import com.taskadapter.redmineapi.bean.Issue;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.LsRemoteCommand;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.util.FS;
import utilities.Properties;

public class BeginIssueWithGit extends Operation
{

    // mandatory
    private final static String PARAMETER_REDMINE_URL = "REDMINE_URL";
    private final static String PARAMETER_REDMINE_TOKEN = "REDMINE_TOKEN";

    private final static String PARAMETER_ISSUE = "ISSUE";
    private final static String PARAMETER_GIT_REPOSITORY = "GIT_REPOSITORY";
    private final static String PARAMETER_GIT_PROTOCOL = "GIT_PROTOCOL";

    // conditional optional
    private final static String PARAMETER_GIT_USERNAME = "GIT_USERNAME";
    private final static String PARAMETER_GIT_PASSWORD = "GIT_PASSWORD";
    private final static String PARAMETER_SSH_PRIVATE_KEY = "SSH_PRIVATE_KEY";

    // optional
    private final static String PARAMETER_BEGAN_STATUS = "BEGAN_STATUS";
    private final static String PARAMETER_ASSIGNEE_ID = "ASSIGNEE_ID";

    private final static String PARAMETER_WORKSPACE_PATH = "WORKSPACE_PATH";
    private final static String PARAMETER_IDE_PATH = "IDE_PATH";

    private final static String PARAMETER_REMOTE_HOST = "REMOTE_HOST";
    private final static String PARAMETER_REMOTE_USER = "REMOTE_USER";
    private final static String PARAMETER_REMOTE_PASS = "REMOTE_PASS";
    private final static String PARAMETER_REMOTE_EXEC = "REMOTE_EXEC";
    private final static String PARAMETER_REMOTE_PORT = "REMOTE_PORT";

    private RedmineManager redmineManager;

    private final static HashSet<String> SUPPORTED_PROTOCOLS = new HashSet<>(Arrays.asList("http", "https", "ssh"));
    private CredentialsProvider credentialsProvider;
    private TransportConfigCallback transportConfigCallback;
    private String repository;

    public BeginIssueWithGit() {
	super(new String[]{
	    PARAMETER_ISSUE, PARAMETER_REDMINE_TOKEN, PARAMETER_REDMINE_URL, PARAMETER_GIT_REPOSITORY, PARAMETER_GIT_PROTOCOL
	});
    }

    @Override
    protected void execute(Properties prop) throws Exception {
	if (areMandatoryParametersNotEmpty(prop)) {
	    String redmineAccessToken = getParameterString(prop, PARAMETER_REDMINE_TOKEN, false);
	    String redmineUrl = getParameterString(prop, PARAMETER_REDMINE_URL, false);
	    redmineManager = RedmineManagerFactory.createWithApiKey(redmineUrl, redmineAccessToken);

	    Issue issue;
	    if ((issue = getIssue(redmineManager, getParameterInt(prop, PARAMETER_ISSUE, 0))) == null) {
		return;
	    }
	    System.out.println("#" + issue.getId() + " - " + issue.getSubject());
	    if (!isIssueAssigneeOk(issue, prop) || !isGitCredentialsOk(prop)) {
		return;
	    }
	    createBranch(issue, prop);
	}
    }

    private boolean isIssueAssigneeOk(Issue issue, Properties prop) {
	int assigneeId = getParameterInt(prop, PARAMETER_ASSIGNEE_ID, 0);
	System.out.println("[i] #" + issue.getId() + " is assigned to " + issue.getAssigneeName() + ".");
	if (assigneeId > 0) {
	    System.out.println("[*] Checking issue assignee ...");
	    if (issue.getAssigneeId() != assigneeId) {
		System.out.println("\t[e] Issue assignee failed!");
		System.out.println("\t[i] (suggested option!) Change assignee to yourself if you're sure to begin this issue.");
		System.out.println("\t[i] (not suggested but) You can comment " + PARAMETER_ASSIGNEE_ID + " from config file to disable assignee check.");
		return false;
	    } else {
		System.out.println("\t[✓] Issue assignee is ok.");
	    }
	} else {
	    System.out.println("[i] Issue assignee check is disabled.");
	}
	return true;
    }

    @SuppressWarnings("UseSpecificCatch")
    private boolean isGitCredentialsOk(Properties prop) {
	System.out.println("[*] Checking git credentials ...");
	String protocol = getParameterString(prop, PARAMETER_GIT_PROTOCOL, true);
	if (!SUPPORTED_PROTOCOLS.contains(protocol)) {
	    System.out.println("[e] Unrecognized git protocol: " + protocol);
	    System.out.println("[i] Supported protocols: " + String.join(",", SUPPORTED_PROTOCOLS));
	    return false;
	}
	repository = getRepository(trimRight(getParameterString(prop, PARAMETER_GIT_REPOSITORY, false), "/"), protocol);
	setCredentials(prop, protocol);
	try {
	    LsRemoteCommand lsRemote = new LsRemoteCommand(null);
	    lsRemote.setCredentialsProvider(credentialsProvider);
	    lsRemote.setTransportConfigCallback(transportConfigCallback);
	    lsRemote.setRemote(repository);
	    lsRemote.setHeads(true);
	    Collection<Ref> refs = lsRemote.call();
	    System.out.println("\t[i] " + refs.size() + " remote (head) refs found.");
	    System.out.println("\t[✓] Git credentials are ok.");
	    // https://github.com/centic9/jgit-cookbook/blob/master/src/main/java/org/dstadler/jgit/porcelain/ListRemotes.java
	    return true;
	} catch (Exception ex) {
	    System.out.println("\t[e] Credentials failed! " + ex.getClass().getCanonicalName() + " " + ex.getMessage());
	}
	return false;
    }

    private String getRepository(String repository, String protocol) {
	int indexOfColonWithDoubleSlash;
	if ((indexOfColonWithDoubleSlash = repository.indexOf("://")) > -1) {
	    repository = repository.substring(indexOfColonWithDoubleSlash + 3);
	}
	return protocol.equals("ssh") ? repository : protocol + "://" + repository;
    }

    private void setCredentials(Properties prop, String protocol) {
	if (protocol.equals("https") || protocol.equals("http")) {
	    String username = getParameterString(prop, PARAMETER_GIT_USERNAME, false);
	    String password = getParameterString(prop, PARAMETER_GIT_PASSWORD, false);
	    if (!StringUtils.isBlank(username) && !StringUtils.isBlank(password)) {
		credentialsProvider = new UsernamePasswordCredentialsProvider(username, password);
	    }
	} else if (protocol.equals("ssh")) {
	    final File identity = new File(getSshPrivateKey(prop));
	    if (identity.exists()) {
		final SshSessionFactory sshSessionFactory = new JschConfigSessionFactory()
		{
		    @Override
		    protected void configure(OpenSshConfig.Host hc, Session session) {
			// do nothing
		    }

		    @Override
		    protected JSch createDefaultJSch(FS fs) throws JSchException {
			JSch defaultJSch = super.createDefaultJSch(fs);
			defaultJSch.addIdentity(identity.getAbsolutePath());
			return defaultJSch;
		    }
		};
		transportConfigCallback = (Transport transport) -> {
		    SshTransport sshTransport = (SshTransport) transport;
		    sshTransport.setSshSessionFactory(sshSessionFactory);
		};
	    }
	}
    }

    private String getSshPrivateKey(Properties prop) {
	String sshPrivateKey = getParameterString(prop, PARAMETER_SSH_PRIVATE_KEY, false);
	if (!StringUtils.isBlank(sshPrivateKey)) {
	    if (sshPrivateKey.startsWith("~")) {
		sshPrivateKey = System.getProperty("user.home") + sshPrivateKey.substring(1);
	    }
	} else {
	    sshPrivateKey = System.getProperty("user.home") + "/.ssh/id_rsa";
	}
	return sshPrivateKey;
    }

    private boolean createBranch(Issue issue, Properties prop) {
	System.out.println("[*] Creating branch ...");
	String cacheDir = getWorkingDirectory() + "/" + "cache/git-data/repositories";
	if (!createFolder(cacheDir)) {
	    System.out.println("[e] Cache directory is unreachable! " + cacheDir);
	    return false;
	}
	File cachePath = new File(cacheDir + "/"
		+ new File(trimRight(getParameterString(prop, PARAMETER_GIT_REPOSITORY, false), "/")).getName());
	boolean isCacheReady;
	if (!cachePath.exists()) {
	    isCacheReady = cloneRepository(cachePath);
	} else {
	    isCacheReady = fetchRepository(cachePath);
	}
	if (isCacheReady) {
	    listCurrentStatus(cachePath);
	}

	return true;
    }

    private void listCurrentStatus(File cachePath) {
	System.out.println("\t[*] Listing current status in local cached repository ...");
	listRepositoryLocalBranches(cachePath);
	//listRepositoryLocalStatus(cachePath);
	System.out.println("\t[✓] Done.");
    }

    private boolean cloneRepository(File cachePath) {
	System.out.println("\t[*] Locally cached repository not found. Cloning once ...");
	try (Git result = Git.cloneRepository()
		.setURI(repository)
		.setCredentialsProvider(credentialsProvider)
		.setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out)))
		.setDirectory(cachePath)
		.call()) {
	    System.out.println("\t[✓] Cloned repository: " + result.getRepository().getDirectory());
	    return true;
	} catch (GitAPIException gae) {
	    System.out.println("\t[e] Git clone failed with error! " + gae.getClass().getCanonicalName() + " " + gae.getMessage());
	}
	return false;
    }

    private boolean fetchRepository(File cachePath) {
	System.out.println("\t[*] Locally cached repository exists. Updating remote refs ...");
	try (Repository gitRepo = openRepository(cachePath.getAbsolutePath())) {
	    try (Git git = new Git(gitRepo)) {
		FetchCommand fetch = git.fetch();
		fetch.setCredentialsProvider(credentialsProvider);
		fetch.setTransportConfigCallback(transportConfigCallback);
		fetch.setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out)));
		fetch.setCheckFetchedObjects(true);
		fetch.setRemoveDeletedRefs(true);
		FetchResult result = fetch.call();
		if (!StringUtils.isBlank(result.getMessages())) {
		    System.out.println("\t[i] Messages: " + result.getMessages());
		}
		System.out.println("\t[✓] Git fetch completed.");
		return true;
	    } catch (GitAPIException ex) {
		System.out.println("\t[e] Git fetch failed! " + ex.getClass().getCanonicalName() + " " + ex.getMessage());
	    }
	} catch (IOException ex) {
	    System.out.println("\t[e] Git fetch failed! " + ex.getClass().getCanonicalName() + " " + ex.getMessage());
	}
	return false;
    }

    private Repository openRepository(String cachePath) throws IOException {
	FileRepositoryBuilder builder = new FileRepositoryBuilder();
	builder.setGitDir(new File(cachePath + "/.git"));
	builder.setMustExist(true);
	return builder
		.readEnvironment() // scan environment GIT_* variables
		.build();
    }

    private void listRepositoryLocalBranches(File cachePath) {
	try (Repository gitRepo = openRepository(cachePath.getAbsolutePath())) {
	    try (Git git = new Git(gitRepo)) {
		System.out.println("\t[i] On branch: * " + gitRepo.getBranch());
		ListBranchCommand branchList = git.branchList();
		List<Ref> refs = branchList.call();
		if (refs.size() > 0) {
		    System.out.println("\t[i] Branch list: ");
		    refs.forEach((ref) -> {
			System.out.print("\t\t" + ref.getName());
			Optional.ofNullable(ref.getObjectId()).ifPresent(objectId -> System.out.print(" " + objectId.getName()));
			System.out.println();
		    });
		}
	    } catch (GitAPIException ex) {
		System.out.println("\t[e] Git listing local branches failed! " + ex.getClass().getCanonicalName() + " " + ex.getMessage());
	    }
	} catch (IOException ex) {
	    System.out.println("\t[e] Git listing local branches failed! " + ex.getClass().getCanonicalName() + " " + ex.getMessage());
	}
    }
}
