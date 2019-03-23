package operation;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineManagerFactory;
import com.taskadapter.redmineapi.bean.Issue;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.LsRemoteCommand;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.api.Status;
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
import java.util.Set;
import model.Command;
import static org.apache.http.util.TextUtils.isEmpty;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.RebaseCommand;
import org.eclipse.jgit.api.RebaseResult;
import org.eclipse.jgit.api.StashApplyCommand;
import org.eclipse.jgit.api.StashCreateCommand;
import org.eclipse.jgit.api.StashDropCommand;
import org.eclipse.jgit.lib.BranchTrackingStatus;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;

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
    private final static String PARAMETER_BEGUN_STATUS = "BEGUN_STATUS";
    private final static String PARAMETER_ASSIGNEE_ID = "ASSIGNEE_ID";

    private final static String PARAMETER_WORKSPACE_PATH = "WORKSPACE_PATH";
    private final static String PARAMETER_IDE_PATH = "IDE_PATH";

    private final static String PARAMETER_PROJECT_NAME = "PROJECT_NAME";

    private final static String PARAMETER_REMOTE_HOST = "REMOTE_HOST";
    private final static String PARAMETER_REMOTE_USER = "REMOTE_USER";
    private final static String PARAMETER_REMOTE_PASS = "REMOTE_PASS";
    private final static String PARAMETER_REMOTE_EXEC = "REMOTE_EXEC";
    private final static String PARAMETER_REMOTE_PORT = "REMOTE_PORT";

    private final static String PARAMETER_GIT_CONFIG_USER_NAME = "GIT_CONFIG_USER_NAME";
    private final static String PARAMETER_GIT_CONFIG_USER_EMAIL = "GIT_CONFIG_USER_EMAIL";

    private RedmineManager redmineManager;

    private final static HashSet<String> SUPPORTED_PROTOCOLS = new HashSet<>(Arrays.asList("http", "https", "ssh"));
    private CredentialsProvider credentialsProvider;
    private TransportConfigCallback transportConfigCallback;
    private String repository;
    private String project;
    private boolean hasRemoteBranch;
    private boolean isFreshCheckout;
    private Issue issue;

    public BeginIssueWithGit() {
        super(new String[]{
            PARAMETER_ISSUE, PARAMETER_REDMINE_TOKEN, PARAMETER_REDMINE_URL, PARAMETER_GIT_REPOSITORY, PARAMETER_GIT_PROTOCOL, PARAMETER_WORKSPACE_PATH
        });
    }

    @Override
    protected void execute(Properties prop) throws Exception {
        if (areMandatoryParametersNotEmpty(prop)) {
            String redmineAccessToken = getParameterString(prop, PARAMETER_REDMINE_TOKEN, false);
            String redmineUrl = getParameterString(prop, PARAMETER_REDMINE_URL, false);
            redmineManager = RedmineManagerFactory.createWithApiKey(redmineUrl, redmineAccessToken);

            if ((issue = getIssue(redmineManager, getParameterInt(prop, PARAMETER_ISSUE, 0))) == null) {
                return;
            }
            System.out.println("#" + issue.getId() + " - " + issue.getSubject());
            if (!isIssueAssigneeOk(issue, prop) || !isGitCredentialsOk(prop)) {
                return;
            }
            createBranch(prop);
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
        repository = getRepository(prop, protocol);
        project = getProject(prop);
        setCredentials(prop, protocol);
        try {
            LsRemoteCommand lsRemote = new LsRemoteCommand(null);
            lsRemote.setCredentialsProvider(credentialsProvider);
            lsRemote.setTransportConfigCallback(transportConfigCallback);
            lsRemote.setRemote(repository);
            lsRemote.setHeads(true);
            Collection<Ref> refs = lsRemote.call();
            System.out.println("\t[i] " + refs.size() + " remote (head) refs found.");
            hasRemoteBranch = findIfRemoteBranchExists(refs);
            System.out.println("\t[✓] Git credentials are ok.");
            // https://github.com/centic9/jgit-cookbook/blob/master/src/main/java/org/dstadler/jgit/porcelain/ListRemotes.java
            return true;
        } catch (Exception ex) {
            System.out.println("\t[e] Credentials failed! " + ex.getClass().getCanonicalName() + " " + ex.getMessage());
        }
        return false;
    }

    private boolean findIfRemoteBranchExists(Collection<Ref> refs) {
        for (Ref ref : refs) {
            if (ref.getName().equals("refs/heads/i" + issue.getId() + "/" + project)) {
                System.out.println("\t\t[i] " + ref.getName() + " is found on remote repository.");
                return true;
            }
        }
        return false;
    }

    private String getRepository(Properties prop, String protocol) {
        String repository = trimRight(getParameterString(prop, PARAMETER_GIT_REPOSITORY, false), "/");
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

    private boolean createBranch(Properties prop) {
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
            listRepositoryLocalBranches(cachePath);
            if (listRepositoryLocalStatus(cachePath).isBehindRemote()) {
                pullCurrentBranchOnRepository(cachePath);
            }
            File localBranch;
            if ((localBranch = getLocalBranch(prop)) != null) {
                localBranch = new File(localBranch.getAbsolutePath() + "/" + project);
                if (createOrReuseLocalBranchIfExists(cachePath, localBranch)) {
                    if (checkoutBranch(localBranch)) {
                        if (!isFreshCheckout) {
                            pullCurrentBranchOnRepository(localBranch);
                            RepositoryStatus status = listRepositoryLocalStatus(localBranch);
                            if (status.isBehindRemote() && status.hasLocalChanges()) {
                                fastForwardBranch(localBranch);
                                System.out.println("\t[*] After fast-forward ...");
                                listRepositoryLocalStatus(localBranch);
                            }
                        }
                        configProject(prop, localBranch);
                        issueIsBegun(prop);
                        executeRemote(prop);
                        openIDE(prop, localBranch);
                    }
                }
            }
        }

        return true;
    }

    private boolean cloneRepository(File cachePath) {
        System.out.println("\t[i] Locally cached repository not found.");
        System.out.println("\t[*] Cloning once for cache ...");
        try (Git result = Git.cloneRepository()
                .setURI(repository)
                .setCredentialsProvider(credentialsProvider)
                .setTransportConfigCallback(transportConfigCallback)
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
        System.out.println("\t[i] Locally cached repository exists.");
        System.out.println("\t[*] Updating remote refs ...");
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
                    System.out.print("\t[i] Messages: " + result.getMessages());
                }
                System.out.println("\t[✓] Git fetch ok.");
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
                ListBranchCommand branchList = git.branchList();
                List<Ref> refs = branchList.call();
                if (refs.size() > 0) {
                    System.out.println("\t\t[i] Branches: ");
                    refs.forEach((ref) -> {
                        System.out.print("\t\t\t" + ref.getName());
                        Optional.ofNullable(ref.getObjectId()).ifPresent(objectId -> System.out.print(" " + objectId.getName()));
                        System.out.println();
                    });
                }
                System.out.println("\t\t[i] @branch '" + gitRepo.getBranch() + "'.");
            } catch (GitAPIException ex) {
                System.out.println("\t\t[e] Git listing local branches failed! " + ex.getClass().getCanonicalName() + " " + ex.getMessage());
            }
        } catch (IOException ex) {
            System.out.println("\t\t[e] Git listing local branches failed! " + ex.getClass().getCanonicalName() + " " + ex.getMessage());
        }
    }

    private RepositoryStatus listRepositoryLocalStatus(File repoPath) {
        try (Repository gitRepo = openRepository(repoPath.getAbsolutePath())) {
            try (Git git = new Git(gitRepo)) {
                System.out.print("\t\t[i] Status of branch '" + gitRepo.getBranch() + "': ");
                Status status = git.status().call();
                if (status.isClean()) {
                    System.out.println("nothing to commit, working tree clean");
                } else {
                    if (status.hasUncommittedChanges()) {
                        System.out.print("has uncommitted changes");
                    }
                    System.out.println();
                    listModifiedFiles("Added", status.getAdded());
                    listModifiedFiles("Changed", status.getChanged());
                    listModifiedFiles("Conflicting", status.getConflicting());
                    listModifiedFiles("IgnoredNotInIndex", status.getIgnoredNotInIndex());
                    listModifiedFiles("Missing", status.getMissing());
                    listModifiedFiles("Modified", status.getModified());
                    listModifiedFiles("Removed", status.getRemoved());
                    listModifiedFiles("Untracked", status.getUntracked());
                    listModifiedFiles("UntrackedFolders", status.getUntrackedFolders());
                }
                int[] trackingStatus = listBranchTrackingStatus(gitRepo, gitRepo.getBranch());
                return new RepositoryStatus(trackingStatus, status);
            } catch (GitAPIException ex) {
                System.out.println("\t\t[e] Git status failed! " + ex.getClass().getCanonicalName() + " " + ex.getMessage());
            }
        } catch (IOException ex) {
            System.out.println("\t\t[e] Git status failed! " + ex.getClass().getCanonicalName() + " " + ex.getMessage());
        }
        return new RepositoryStatus();
    }

    private void listModifiedFiles(String title, Set<String> files) {
        if (files.size() > 0) {
            System.out.println("\t\t" + title + ": (" + files.size() + ")");
            files.forEach((file) -> {
                System.out.println("\t\t\t" + file);
            });
        }
    }

    private int[] listBranchTrackingStatus(Repository repository, String branchName) throws IOException {
        int[] status = getTrackingStatus(repository, branchName);
        if (status[0] > 0 || status[1] > 0) {
            System.out.println("\t\t[i] Branch '" + branchName + "' is now (" + status[0] + ") commits ahead, (" + status[1] + ") commits behind.");
        }
        return status;
    }

    private int[] getTrackingStatus(Repository repository, String branchName) throws IOException {
        BranchTrackingStatus trackingStatus = BranchTrackingStatus.of(repository, branchName);
        if (trackingStatus != null) {
            int[] counts = new int[2];
            counts[0] = trackingStatus.getAheadCount();
            counts[1] = trackingStatus.getBehindCount();
            return counts;
        }
        return new int[]{0, 0};
    }

    private boolean pullCurrentBranchOnRepository(File cachePath) {
        System.out.println("\t[*] Pulling remote changes ...");
        try (Repository gitRepo = openRepository(cachePath.getAbsolutePath())) {
            try (Git git = new Git(gitRepo)) {
                PullCommand pull = git.pull();
                pull.setRebase(true);
                pull.setCredentialsProvider(credentialsProvider);
                pull.setTransportConfigCallback(transportConfigCallback);
                pull.setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out)));
                PullResult result = pull.call();
                if (result.isSuccessful()) {
                    FetchResult fetchResult = result.getFetchResult();
                    if (!StringUtils.isBlank(fetchResult.getMessages())) {
                        System.out.print("\t[i] Messages: " + fetchResult.getMessages());
                    }
                    MergeResult mergeResult = result.getMergeResult();
                    if (mergeResult != null && mergeResult.getMergeStatus().isSuccessful()) {
                        System.out.println("\t[✓] Pull merge status is successful.");
                    }
                    System.out.println("\t[✓] Git pull ok.");
                    return true;
                }
            } catch (GitAPIException ex) {
                System.out.println("\t[e] Git pull failed! " + ex.getClass().getCanonicalName() + " " + ex.getMessage());
            }
        } catch (IOException ex) {
            System.out.println("\t[e] Git pull failed! " + ex.getClass().getCanonicalName() + " " + ex.getMessage());
        }
        return false;
    }

    private String getProject(Properties prop) {
        String projectName = getParameterString(prop, PARAMETER_PROJECT_NAME, false);
        if (!StringUtils.isBlank(projectName)) {
            return projectName;
        }
        return new File(trimRight(getParameterString(prop, PARAMETER_GIT_REPOSITORY, false), "/")).getName().replace(".git", "");
    }

    private File getLocalBranch(Properties prop) {
        File localBranch = new File(trimRight(getParameterString(prop, PARAMETER_WORKSPACE_PATH, false), "/") + "/i" + issue.getId());
        System.out.println("\t[i] Local branch folder: " + localBranch);
        System.out.println("\t[*] Checking whether local branch folder exists ...");
        boolean pathExists = true;
        if (!localBranch.exists()) {
            pathExists = createFolder(localBranch.getAbsolutePath());
        }
        if (pathExists) {
            System.out.println("\t\t[✓] Local branch folder is ok.");
            return localBranch;
        }
        System.out.println("\t\t[e] Local branch folder couldn't be found in workspace!");
        return null;
    }

    private boolean createOrReuseLocalBranchIfExists(File cachedBranch, File localBranch) {
        boolean isReady = false;
        System.out.println("\t[i] Local branch: " + localBranch.getAbsolutePath());
        if (localBranch.exists()) {
            System.out.println("\t\t[i] Previously used local branch is found in workspace.");
            Scanner scanner = new Scanner(System.in);
            System.out.print("\t\t[?] Would you like to delete existing working copy for fresh checkout? (Y/N) ");
            String answer = scanner.next();
            if (!isEmpty(answer) && (answer.charAt(0) == 'Y' || answer.charAt(0) == 'y')) {
                execute(new String[]{
                    "rm", "-Rf", localBranch.getAbsolutePath()
                });
                if (copy(cachedBranch.getAbsolutePath(), localBranch.getAbsolutePath())) {
                    System.out.println("\t\t[✓] Ok, working copy is deleted and created.");
                    isFreshCheckout = true;
                    isReady = true;
                }
            } else {
                System.out.println("\t\t[✓] Ok, working copy will be reused.");
                isReady = true;
            }
        } else {
            if (copy(cachedBranch.getAbsolutePath(), localBranch.getAbsolutePath())) {
                System.out.println("\t\t[✓] Ok, working copy is created.");
                isFreshCheckout = true;
                isReady = true;
            }
        }
        if (!isReady) {
            System.out.println("\t\t[e] Local branch could not be created successfully.");
            System.out.println("\t\t[e] Please, restart operation. If still getting error? Delete local branch manually.");
        }
        return isReady;
    }

    private boolean copy(String sourcePath, String targetPath) {
        String command = "cp -r " + sourcePath + " " + targetPath;
        System.out.println(command);
        String[] output = execute(command);
        if (output == null || output[1].length() > 0) {
            System.err.println(output[1]);
            return false;
        }

        System.out.println(output[0]);
        return true;
    }

    private boolean checkoutBranch(File localBranch) {
        System.out.println("\t[*] Checkout ...");
        try (Repository gitRepo = openRepository(localBranch.getAbsolutePath())) {
            try (Git git = new Git(gitRepo)) {
                String name = "i" + issue.getId() + "/" + project;
                Ref ref = gitRepo.exactRef("refs/heads/" + name);
                if (ref == null) {
                    CreateBranchCommand createBranch = git.branchCreate();
                    createBranch.setName(name);
                    if (hasRemoteBranch) {
                        createBranch.setStartPoint("origin/" + name);
                        createBranch.setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK);
                    }
                    ref = createBranch.call();
                }
                System.out.print("\t\t" + ref.getName());
                Optional.ofNullable(ref.getObjectId()).ifPresent(objectId -> System.out.println(" " + objectId.getName()));
                if (!hasRemoteBranch) {
                    PushCommand pushCommand = git.push();
                    pushCommand.setCredentialsProvider(credentialsProvider);
                    pushCommand.setTransportConfigCallback(transportConfigCallback);
                    pushCommand.setRemote("origin");
                    pushCommand.setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out)));
                    pushCommand.setRefSpecs(new RefSpec(name + ":" + name));
                    Iterable<PushResult> resultIterable = pushCommand.call();
                    PushResult result = resultIterable.iterator().next();
                    if (!StringUtils.isBlank(result.getMessages())) {
                        System.out.print("\t\t[i] Messages: " + result.getMessages());
                    }
                    CreateBranchCommand updateBranch = git.branchCreate();
                    updateBranch.setName(name);
                    updateBranch.setStartPoint("origin/" + name);
                    updateBranch.setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK);
                    updateBranch.setForce(true);
                    updateBranch.call();
                }
                CheckoutCommand checkout = git.checkout();
                checkout.setName(name);
                checkout.setStartPoint("origin/" + name);
                checkout.setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK);
                checkout.setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out)));
                checkout.call();
                System.out.println("\t\t[✓] Git checkout ok.");
                return true;
            } catch (GitAPIException ex) {
                System.out.println("\t\t[e] Git checkout failed! " + ex.getClass().getCanonicalName() + " " + ex.getMessage());
            }
        } catch (IOException ex) {
            System.out.println("\t\t[e] Git checkout failed! " + ex.getClass().getCanonicalName() + " " + ex.getMessage());
        }
        return false;
    }

    private void issueIsBegun(Properties prop) {
        String begunStatus = getParameterString(prop, PARAMETER_BEGUN_STATUS, true);
        if (!isEmpty(begunStatus)) {
            System.out.println("[*] Updating issue status ...");
            int statusId = tryGetIssueStatusId(redmineManager, begunStatus);
            System.out.println("\t[i] Begun Status: " + begunStatus);
            if (statusId > 0) {
                try {
                    String baseStatus = issue.getStatusName();
                    issue = updateIssueStatus(redmineManager, issue, statusId);
                    System.out.println("\t[i] Issue status: "
                            + (!baseStatus.equals(issue.getStatusName()) ? baseStatus + " > " : "") + issue.getStatusName());
                    System.out.println("\t[✓] Issue status is ok.");
                } catch (Exception ex) {
                    System.out.println("\t[e] Issue status update failed! " + ex.getClass().getCanonicalName() + " " + ex.getMessage());
                }
            } else {
                System.out.println("\t[w] Begun status has invalid value!");
            }
        }
    }

    private void executeRemote(Properties prop) {
        String sshHost = getParameterString(prop, PARAMETER_REMOTE_HOST, false);
        String sshUser = getParameterString(prop, PARAMETER_REMOTE_USER, false);
        String sshPass = getParameterString(prop, PARAMETER_REMOTE_PASS, false);
        String sshExec = getParameterString(prop, PARAMETER_REMOTE_EXEC, false);

        byte validValueCount = 0;
        validValueCount += StringUtils.isBlank(sshHost) ? 0 : 1;
        validValueCount += StringUtils.isBlank(sshUser) ? 0 : 1;
        validValueCount += StringUtils.isBlank(sshPass) ? 0 : 1;
        validValueCount += StringUtils.isBlank(sshExec) ? 0 : 1;
        if (validValueCount > 0) {
            System.out.println("[*] Executing remote command ...");
            if (validValueCount < 4) {
                System.out.println("\t[e] Missing remote parameters! "
                        + "Please, set all values for REMOTE_HOST, REMOTE_USER, REMOTE_PASS, REMOTE_EXEC parameters.");
            } else {
                try {
                    int sshPort = getParameterInt(prop, PARAMETER_REMOTE_PORT, 22);
                    System.out.println("\t[i] Host: " + sshHost + getHostIPAddress(sshHost));
                    System.out.println("\t[i] User: " + sshUser);
                    System.out.println("\t[i] Port: " + sshPort);
                    System.out.println("\t[i] Exec: " + sshExec);

                    Velocity.init();
                    VelocityContext context = new VelocityContext();
                    context.put("issue", issue.getId());
                    StringWriter writer = new StringWriter();
                    Velocity.evaluate(context, writer, "TemplateName", sshExec);
                    sshExec = writer.toString();

                    JSch jsch = new JSch();
                    Session session = jsch.getSession(sshUser, sshHost, sshPort);
                    session.setPassword(sshPass);
                    java.util.Properties config = new java.util.Properties();
                    config.put("StrictHostKeyChecking", "no");
                    session.setConfig(config);
                    System.out.println("\t[*] Connecting ...");
                    session.connect();
                    System.out.println("\t[✓] Connected.");

                    ChannelExec channelExec = (ChannelExec) session.openChannel("exec");
                    System.out.println("\t[*] Executing command ...");
                    channelExec.setCommand("exec 2>&1 && " + sshExec);
                    InputStream stream = channelExec.getInputStream();
                    channelExec.connect();
                    byte[] tmp = new byte[1024];
                    while (true) {
                        while (stream.available() > 0) {
                            int i = stream.read(tmp, 0, 1024);
                            if (i < 0) {
                                break;
                            }
                            System.out.print(new String(tmp, 0, i));
                        }
                        if (channelExec.isClosed()) {
                            if (stream.available() > 0) {
                                continue;
                            }
                            break;
                        }
                        Thread.sleep(500);
                    }
                    int exitStatus = channelExec.getExitStatus();
                    System.out.println("\t[i] Command exit with " + exitStatus + ".");
                    if (exitStatus == 0) {
                        System.out.println("\t[✓] Remote execution is ok.");
                    } else {
                        System.out.println("\t[w] Remote execution failed! Inspect command logs.");
                    }
                    channelExec.disconnect();
                    session.disconnect();
                } catch (JSchException | IOException | InterruptedException ex) {
                    System.out.println("\t[e] Remote execution failed! " + ex.getClass().getCanonicalName() + " " + ex.getMessage());
                }
            }
        }
    }

    private void openIDE(Properties prop, File project) {
        String idePath = getParameterString(prop, PARAMETER_IDE_PATH, false);
        String workspacePath = trimRight(getParameterString(prop, PARAMETER_WORKSPACE_PATH, false), "/");
        if (!isEmpty(idePath) && !isEmpty(workspacePath)) {
            Scanner scanner = new Scanner(System.in);
            System.out.print("[?] Would you like open to IDE for development? (Y/N) ");
            String answer = scanner.next();
            if (!isEmpty(answer) && (answer.charAt(0) == 'Y' || answer.charAt(0) == 'y')) {
                if (project.exists()) {
                    Command cmd = new Command(idePath.contains("%s") ? String.format(idePath, project.getAbsolutePath())
                            : idePath + " " + project.getAbsolutePath());
                    System.out.println("[*] Opening IDE ...");
                    System.out.println(cmd.getCommandString());
                    cmd.executeNoWait();
                    System.out.println("[✓] IDE will be open in a few seconds. Happy coding. \\o/");
                } else {
                    System.out.print("\t[w] Project not found in path:" + project.getAbsolutePath());
                }
            }
        }
    }

    private void configProject(Properties prop, File localBranch) {
        String userName = getParameterString(prop, PARAMETER_GIT_CONFIG_USER_NAME, false);
        String userEmail = getParameterString(prop, PARAMETER_GIT_CONFIG_USER_EMAIL, false);
        if (!StringUtils.isBlank(userName) || !StringUtils.isBlank(userEmail)) {
            System.out.println("[*] Config project ...");
            try (Repository gitRepo = openRepository(localBranch.getAbsolutePath())) {
                StoredConfig config = gitRepo.getConfig();
                boolean isModified = false;

                String localUserName = config.getString("user", null, "name");
                System.out.println("\t[i] --local user.name: " + localUserName);
                if (!userName.equals(localUserName)) {
                    config.setString("user", null, "name", userName);
                    System.out.println("\t[✓] Modified as: " + userName);
                    isModified = true;
                } else {
                    System.out.println("\t[✓] user.name is ok. ");
                }
                String localUserEmail = config.getString("user", null, "email");
                System.out.println("\t[i] --local user.email: " + localUserEmail);
                if (!userEmail.equals(localUserEmail)) {
                    config.setString("user", null, "email", userEmail);
                    System.out.println("\t[✓] Modified as: " + userEmail);
                    isModified = true;
                } else {
                    System.out.println("\t[✓] user.email is ok. ");
                }
                if (isModified) {
                    config.save();
                    System.out.println("\t[✓] Modifications saved. ");
                }
            } catch (IOException ex) {
                System.out.println("\t\t[e] Git config failed! " + ex.getClass().getCanonicalName() + " " + ex.getMessage());
            }
        }
    }

    private class RepositoryStatus
    {

        private final boolean behindRemote;
        private final boolean localChanges;

        RepositoryStatus() {
            behindRemote = false;
            localChanges = false;
        }

        RepositoryStatus(int[] trackingStatus, Status status) {
            behindRemote = trackingStatus[1] > 0;
            localChanges = status.hasUncommittedChanges() || status.getUntracked().size() > 0 || status.getUntrackedFolders().size() > 0;
        }

        private boolean isBehindRemote() {
            return behindRemote;
        }

        private boolean hasLocalChanges() {
            return localChanges;
        }
    }

    private boolean fastForwardBranch(File repoPath) {
        System.out.println("\t[*] Fast-forwading branch ...");
        try (Repository gitRepo = openRepository(repoPath.getAbsolutePath())) {
            try (Git git = new Git(gitRepo)) {
                System.out.println("\t\t[*] Creating stash for changes ...");
                StashCreateCommand stashCreate = git.stashCreate();
                stashCreate.setIncludeUntracked(true);
                RevCommit stash = stashCreate.call();
                System.out.println("\t\t[i] " + stash.getFullMessage());
                System.out.println("\t\t[✓] Created stash: " + stash.getName());
                System.out.println("\t\t[*] Rebasing ...");
                RebaseCommand rebase = git.rebase();
                rebase.setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out)));
                rebase.setUpstream("origin/" + "i" + issue.getId() + "/" + project);
                RebaseResult rebaseResult = rebase.call();
                if (rebaseResult.getStatus().isSuccessful()) {
                    System.out.println("\t\t[✓] Rebase ok.");
                } else {
                    System.out.println("\t\t[w] Rebase failed!");
                }
                System.out.println("\t\t[*] Applying stash after rebase ...");
                StashApplyCommand stashApply = git.stashApply();
                stashApply.setStashRef(stash.getName());
                ObjectId applied = stashApply.call();
                System.out.println("\t\t[✓] Stash applied: " + applied);
                System.out.println("\t\t[*] Dropping applied stash ...");
                int ref = 0;
                Collection<RevCommit> stashes = git.stashList().call();
                for (RevCommit rev : stashes) {
                    if (rev.getFullMessage().equals(stash.getFullMessage())
                            && rev.getName().equals(stash.getName())) {
                        break;
                    }
                    ref++;
                }
                StashDropCommand stashDrop = git.stashDrop();
                stashDrop.setStashRef(ref);
                stashDrop.call();
                System.out.println("\t\t[i] Stash count: " + stashes.size() + " > " + git.stashList().call().size());
                System.out.println("\t\t[✓] Stash dropped. ");
            } catch (GitAPIException ex) {
                System.out.println("\t\t[e] Fast-forward failed! " + ex.getClass().getCanonicalName() + " " + ex.getMessage());
            }
        } catch (IOException ex) {
            System.out.println("\t\t[e] Fast-forward failed! " + ex.getClass().getCanonicalName() + " " + ex.getMessage());
        }
        return false;
    }
}
