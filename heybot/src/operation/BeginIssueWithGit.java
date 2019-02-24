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
import java.util.Scanner;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.LsRemoteCommand;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
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
import static org.apache.http.util.TextUtils.isEmpty;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.lib.BranchTrackingStatus;

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
    private String project;
    private boolean hasRemoteBranch;
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
            if (listRepositoryLocalStatus(cachePath) > 0) {
                pullCurrentBranchOnRepository(cachePath);
            }
            File localBranch;
            if ((localBranch = getLocalBranch(prop)) != null) {
                localBranch = new File(localBranch.getAbsolutePath() + "/" + project);
                if (createOrReuseLocalBranchIfExists(cachePath, localBranch)) {
                    System.out.println();
                }
            }
        }

        return true;
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
                ListBranchCommand branchList = git.branchList();
                List<Ref> refs = branchList.call();
                if (refs.size() > 0) {
                    System.out.println("\t[i] Branch list of repository: ");
                    refs.forEach((ref) -> {
                        System.out.print("\t\t" + ref.getName());
                        Optional.ofNullable(ref.getObjectId()).ifPresent(objectId -> System.out.print(" " + objectId.getName()));
                        System.out.println();
                    });
                }
                System.out.println("\t[i] You're currently on branch '" + gitRepo.getBranch() + "'.");
            } catch (GitAPIException ex) {
                System.out.println("\t[e] Git listing local branches failed! " + ex.getClass().getCanonicalName() + " " + ex.getMessage());
            }
        } catch (IOException ex) {
            System.out.println("\t[e] Git listing local branches failed! " + ex.getClass().getCanonicalName() + " " + ex.getMessage());
        }
    }

    private int listRepositoryLocalStatus(File cachePath) {
        try (Repository gitRepo = openRepository(cachePath.getAbsolutePath())) {
            try (Git git = new Git(gitRepo)) {
                System.out.print("\t[i] Status of branch '" + gitRepo.getBranch() + "': ");
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
                return listBranchTrackingStatus(gitRepo, gitRepo.getBranch());
            } catch (GitAPIException ex) {
                System.out.println("\t[e] Git listing local branches failed! " + ex.getClass().getCanonicalName() + " " + ex.getMessage());
            }
        } catch (IOException ex) {
            System.out.println("\t[e] Git listing local branches failed! " + ex.getClass().getCanonicalName() + " " + ex.getMessage());
        }
        return 0;
    }

    private void listModifiedFiles(String title, Set<String> files) {
        if (files.size() > 0) {
            System.out.println("\t\t" + title + ": (" + files.size() + ")");
            files.forEach((file) -> {
                System.out.println("\t\t\t" + file);
            });
        }
    }

    private int listBranchTrackingStatus(Repository repository, String branchName) throws IOException {
        int[] status = getTrackingStatus(repository, branchName);
        if (status != null && (status[0] > 0 || status[1] > 0)) {
            System.out.println("\t[i] Branch '" + branchName + "' is now (" + status[0] + ") commits ahead, (" + status[1] + ") commits behind.");
            return status[1];
        }
        return 0;
    }

    private int[] getTrackingStatus(Repository repository, String branchName) throws IOException {
        BranchTrackingStatus trackingStatus = BranchTrackingStatus.of(repository, branchName);
        if (trackingStatus != null) {
            int[] counts = new int[2];
            counts[0] = trackingStatus.getAheadCount();
            counts[1] = trackingStatus.getBehindCount();
            return counts;
        }
        return null;
    }

    private boolean pullCurrentBranchOnRepository(File cachePath) {
        System.out.println("\t[*] Pulling remote changes ...");
        try (Repository gitRepo = openRepository(cachePath.getAbsolutePath())) {
            try (Git git = new Git(gitRepo)) {
                PullCommand pull = git.pull();
                pull.setCredentialsProvider(credentialsProvider);
                pull.setTransportConfigCallback(transportConfigCallback);
                pull.setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out)));
                PullResult result = pull.call();
                if (result.isSuccessful()) {
                    FetchResult fetchResult = result.getFetchResult();
                    if (!StringUtils.isBlank(fetchResult.getMessages())) {
                        System.out.println("\t[i] Messages: " + fetchResult.getMessages());
                    }
                    MergeResult mergeResult = result.getMergeResult();
                    if (mergeResult.getMergeStatus().isSuccessful()) {
                        System.out.println("\t[✓] Git pull completed.");
                        return true;
                    }
                }
            } catch (GitAPIException ex) {
                System.out.println("\t[e] Git fetch failed! " + ex.getClass().getCanonicalName() + " " + ex.getMessage());
            }
        } catch (IOException ex) {
            System.out.println("\t[e] Git fetch failed! " + ex.getClass().getCanonicalName() + " " + ex.getMessage());
        }
        return false;
    }

    private String getProject(Properties prop) {
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
                    isReady = true;
                }
            } else {
                System.out.println("\t\t[✓] Ok, working copy will be reused.");
                isReady = true;
            }
        } else {
            if (copy(cachedBranch.getAbsolutePath(), localBranch.getAbsolutePath())) {
                System.out.println("\t\t[✓] Ok, working copy is created.");
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
}
