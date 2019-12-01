package operation;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import net.bis5.mattermost.client4.ApiResponse;
import net.bis5.mattermost.client4.hook.IncomingWebhookClient;
import net.bis5.mattermost.model.IncomingWebhookRequest;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.util.FS;
import org.gitlab4j.api.Constants;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.MergeRequest;
import org.gitlab4j.api.models.Participant;
import org.gitlab4j.api.models.Project;
import utilities.Properties;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.logging.Level;

public class NotifyConflictedBranch extends Operation
{
    //<editor-fold desc="parameters">
    // mandatory
    private final static String PARAMETER_GITLAB_URL = "GITLAB_URL";
    private final static String PARAMETER_GITLAB_TOKEN = "GITLAB_TOKEN";
    private final static String PARAMETER_GITLAB_PROJECT_ID = "GITLAB_PROJECT_ID";
    private final static String PARAMETER_GIT_REPOSITORY = "GIT_REPOSITORY";
    private final static String PARAMETER_GIT_PROTOCOL = "GIT_PROTOCOL";
    private final static String PARAMETER_MATTERMOST_INCOMING_WEBHOOK = "MATTERMOST_INCOMING_WEBHOOK";
    // conditional optional
    private final static String PARAMETER_GIT_USERNAME = "GIT_USERNAME";
    private final static String PARAMETER_GIT_PASSWORD = "GIT_PASSWORD";
    private final static String PARAMETER_SSH_PRIVATE_KEY = "SSH_PRIVATE_KEY";
    //</editor-fold>

    public NotifyConflictedBranch() {
        super(new String[]{PARAMETER_GITLAB_URL, PARAMETER_GITLAB_TOKEN, PARAMETER_GITLAB_PROJECT_ID, PARAMETER_GIT_REPOSITORY, PARAMETER_GIT_PROTOCOL, PARAMETER_MATTERMOST_INCOMING_WEBHOOK});
    }

    @Override
    protected void execute(Properties prop) throws Exception {
        if (areMandatoryParametersNotEmpty(prop)) {
            GitLabApi gitlabApi = tryGetGitlabApi(prop);
            if (gitlabApi == null) {
                System.out.println("[e] Operation works on GitLab merge requests!");
                return;
            }
            List<MergeRequest> mergeRequests = getOpenedMergeRequests(prop, gitlabApi);
            System.out.println("[i] Total " + mergeRequests.size() + " opened merge request(s) found.");
            mergeRequests = filterUnmergeableMergeRequests(mergeRequests);
            System.out.println("[i] Total " + mergeRequests.size() + " unmergeable merge request(s) found.");
            notifyConflictedMergeRequests(prop, gitlabApi, mergeRequests);
        }
    }

    private void notifyConflictedMergeRequests(Properties prop, GitLabApi gitlabApi, List<MergeRequest> mergeRequests) throws GitLabApiException {
        File repoPath;
        if (isGitCredentialsOk(prop) && (repoPath = tryGetRepoPath(prop)) != null) {
            for (MergeRequest mergeRequest : mergeRequests) {
                System.out.println("[*] " + mergeRequest.getTitle());
                if (!checkoutBranch(repoPath, mergeRequest.getSourceBranch())) {
                    continue;
                }
                resetRepository(repoPath);
                if (!checkoutBranch(repoPath, mergeRequest.getTargetBranch())) {
                    continue;
                }
                List<String> conflicts = getConflictsWith(repoPath, mergeRequest.getSourceBranch());
                pushNotification(prop, gitlabApi, mergeRequest, conflicts);
                resetRepository(repoPath);
            }
        }
    }

    private void pushNotification(Properties prop, GitLabApi gitLabApi, MergeRequest mergeRequest, List<String> conflicts) throws GitLabApiException {
        System.out.println("\t[i] " + conflicts.size() + " conflict(s) found.");
        if (conflicts.size() > 0) {
            System.out.println("\t[*] Mattermost notification ... ");
            Project project = gitLabApi.getProjectApi().getProject(getParameterInt(prop, PARAMETER_GITLAB_PROJECT_ID, 0));
            List<Participant> participants = gitLabApi.getMergeRequestApi().getParticipants(project.getId(), mergeRequest.getIid());
            IncomingWebhookRequest payload = new IncomingWebhookRequest();
            StringBuilder text = new StringBuilder();
            text.append(String.format("`%s` can no longer be merged due to merge conflict with `%s`\n", mergeRequest.getSourceBranch(), mergeRequest.getTargetBranch()));
            text.append("Conflicts:\n");
            for (String conflict : conflicts) {
                text.append(String.format("* %s\n", conflict));
            }
            text.append(String.format("@%s a gentle reminder to resolve conflicts :point_right: [%s!%d](%s)\n", mergeRequest.getAuthor().getUsername(), project.getPathWithNamespace(), mergeRequest.getIid(), mergeRequest.getWebUrl()));
            StringBuilder cc = new StringBuilder();
            for (Participant participant : participants) {
                if (participant.getUsername().equals(mergeRequest.getAuthor().getUsername())) {
                    continue;
                }
                cc.append(String.format("@%s", participant.getUsername()));
            }
            if (cc.length() > 0) {
                text.append(String.format("**cc/** %s", cc.toString()));
            }
            payload.setText(text.toString());
            IncomingWebhookClient webhookClient = new IncomingWebhookClient(
                getParameterString(prop, PARAMETER_MATTERMOST_INCOMING_WEBHOOK, false), Level.WARNING);
            ApiResponse<Boolean> response = webhookClient.postByIncomingWebhook(payload);
            if (!response.hasError()) {
                System.out.println("[✓] Mattermost notification sent.");
            } else {
                System.out.println("[e] " + response.readError().getMessage());
            }
        }
    }

    //<editor-fold desc="GitLab">
    private List<MergeRequest> filterUnmergeableMergeRequests(List<MergeRequest> mergeRequests) {
        List<MergeRequest> unmergeable = new ArrayList<>();
        for (MergeRequest mergeRequest : mergeRequests) {
            if (mergeRequest.getMergeStatus().equals("cannot_be_merged")) {
                unmergeable.add(mergeRequest);
            }
        }
        return unmergeable;
    }

    private List<MergeRequest> getOpenedMergeRequests(Properties prop, GitLabApi gitlabApi) throws GitLabApiException {
        int gitlabProjectId = getParameterInt(prop, PARAMETER_GITLAB_PROJECT_ID, 0);
        return gitlabApi.getMergeRequestApi().getMergeRequests(gitlabProjectId, Constants.MergeRequestState.OPENED);
    }

    private GitLabApi tryGetGitlabApi(Properties prop) {
        String gitlabUrl = getParameterString(prop, PARAMETER_GITLAB_URL, false);
        String gitlabToken = getParameterString(prop, PARAMETER_GITLAB_TOKEN, false);
        if (StringUtils.isBlank(gitlabUrl) || StringUtils.isBlank(gitlabToken)) {
            System.out.println("[w] Gitlab API credentials is empty or insufficient. (GITLAB_*)");
            return null;
        }
        GitLabApi api = new GitLabApi(gitlabUrl, gitlabToken);
        try {
            org.gitlab4j.api.models.Version version = api.getVersion();
            System.out.println("[i] Gitlab: " + version.getVersion() + "(" + version.getRevision() + ")");
        } catch (GitLabApiException e) {
            System.out.println("[w] Gitlab API credentials is misconfigured or server is unreachable. (GITLAB_*)");
            System.out.println("[w] " + e.getMessage() + " " + e.getReason());
            return null;
        }

        return api;
    }
    //</editor-fold>

    //<editor-fold desc="Git">
    private final static HashSet<String> SUPPORTED_PROTOCOLS = new HashSet<>(Arrays.asList("http", "https", "ssh"));
    private String repository;
    private CredentialsProvider credentialsProvider;
    private TransportConfigCallback transportConfigCallback;

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
        setCredentials(prop, protocol);
        try {
            LsRemoteCommand lsRemote = new LsRemoteCommand(null);
            lsRemote.setCredentialsProvider(credentialsProvider);
            lsRemote.setTransportConfigCallback(transportConfigCallback);
            lsRemote.setRemote(repository);
            lsRemote.setHeads(true);
            Collection<Ref> refs = lsRemote.call();
            System.out.println("[i] " + refs.size() + " remote refs found.");
            System.out.println("[✓] Git credentials are ok.");
            return true;
        } catch (Exception ex) {
            System.out.println("[e] Credentials failed! " + ex.getClass().getCanonicalName() + " " + ex.getMessage());
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
                        defaultJSch.removeAllIdentity();
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

    private File tryGetRepoPath(Properties prop) {
        String cacheDir = getWorkingDirectory() + "/" + "cache/git-data/repositories";
        if (!createFolder(cacheDir)) {
            System.out.println("[e] Cache directory is unreachable! " + cacheDir);
            return null;
        }
        File repoPath = new File(cacheDir + "/"
            + new File(trimRight(getParameterString(prop, PARAMETER_GIT_REPOSITORY, false), "/")).getName());

        boolean isRepoReady;
        if (!repoPath.exists()) {
            isRepoReady = cloneRepository(repoPath);
        } else {
            if (hasUncommittedChanges(repoPath)) {
                resetRepository(repoPath);
            }
            if (isRepoReady = fetchRepository(repoPath)) {
                isRepoReady = pullRepository(repoPath);
            }
        }
        if (isRepoReady) {
            return repoPath;
        }
        return null;
    }

    private boolean cloneRepository(File cachePath) {
        System.out.println("[i] Locally cached repository not found.");
        System.out.println("[*] Cloning once for cache ...");
        try (Git result = Git.cloneRepository()
            .setURI(repository)
            .setCredentialsProvider(credentialsProvider)
            .setTransportConfigCallback(transportConfigCallback)
            .setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out)))
            .setDirectory(cachePath)
            .call()) {
            System.out.println("[✓] Cloned repository: " + result.getRepository().getDirectory());
            return true;
        } catch (GitAPIException gae) {
            System.out.printf("[e] Git clone failed with error! %s %s%n", gae.getClass().getCanonicalName(), gae.getMessage());
        }
        return false;
    }

    private boolean fetchRepository(File cachePath) {
        System.out.println("[i] Locally cached repository exists.");
        System.out.println("[*] Updating remote refs ...");
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
                    System.out.print("[i] Messages: " + result.getMessages());
                }
                System.out.println("[✓] Git fetch ok.");
                return true;
            } catch (GitAPIException ex) {
                System.out.printf("[e] Git fetch failed! %s %s%n", ex.getClass().getCanonicalName(), ex.getMessage());
            }
        } catch (IOException ex) {
            System.out.printf("[e] Git fetch failed! %s %s%n", ex.getClass().getCanonicalName(), ex.getMessage());
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

    private boolean pullRepository(File cachePath) {
        System.out.println("[*] Pulling remote changes ...");
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
                        System.out.print("[i] Messages: " + fetchResult.getMessages());
                    }
                    MergeResult mergeResult = result.getMergeResult();
                    if (mergeResult != null && mergeResult.getMergeStatus().isSuccessful()) {
                        System.out.println("[✓] Pull merge status is successful.");
                    }
                    System.out.println("[✓] Git pull ok.");
                    return true;
                }
            } catch (GitAPIException ex) {
                System.out.printf("[e] Git pull failed! %s %s%n", ex.getClass().getCanonicalName(), ex.getMessage());
            }
        } catch (IOException ex) {
            System.out.printf("[e] Git pull failed! %s %s%n", ex.getClass().getCanonicalName(), ex.getMessage());
        }
        return false;
    }

    private void resetRepository(File repoPath) {
        try (Repository gitRepo = openRepository(repoPath.getAbsolutePath())) {
            try (Git git = new Git(gitRepo)) {
                git.reset()
                    .setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out)))
                    .setMode(ResetCommand.ResetType.HARD).call();
                git.checkout()
                    .setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out)))
                    .addPath(".").call();
            } catch (GitAPIException ex) {
                System.out.printf("[e] Git reset failed! %s %s%n", ex.getClass().getCanonicalName(), ex.getMessage());
            }
        } catch (IOException ex) {
            System.out.printf("[e] Git reset failed! %s %s%n", ex.getClass().getCanonicalName(), ex.getMessage());
        }
    }

    private boolean hasUncommittedChanges(File repoPath) {
        try (Repository gitRepo = openRepository(repoPath.getAbsolutePath())) {
            try (Git git = new Git(gitRepo)) {
                return git.status().call().hasUncommittedChanges();
            } catch (GitAPIException ex) {
                System.out.printf("[e] Git status failed! %s %s%n", ex.getClass().getCanonicalName(), ex.getMessage());
            }
        } catch (IOException ex) {
            System.out.printf("[e] Git status failed! %s %s%n", ex.getClass().getCanonicalName(), ex.getMessage());
        }
        return true;
    }

    private boolean checkoutBranch(File repoPath, String branch) {
        System.out.println("\t[*] Checkout branch: " + branch);
        try (Repository gitRepo = openRepository(repoPath.getAbsolutePath())) {
            try (Git git = new Git(gitRepo)) {
                CheckoutCommand checkout = git.checkout();
                checkout.setCreateBranch(gitRepo.exactRef("refs/heads/" + branch) == null);
                checkout.setName(branch);
                checkout.setStartPoint("origin/" + branch);
                checkout.setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK);
                checkout.setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out)));
                Ref ref = checkout.call();
                System.out.println("\t\t[✓] " + ref.getObjectId().getName());
                return true;
            } catch (GitAPIException ex) {
                System.out.printf("\t[e] Git checkout failed! %s %s%n", ex.getClass().getCanonicalName(), ex.getMessage());
            }
        } catch (IOException ex) {
            System.out.printf("\t[e] Git checkout failed! %s %s%n", ex.getClass().getCanonicalName(), ex.getMessage());
        }
        return false;
    }

    private List<String> getConflictsWith(File repoPath, String branch) {
        System.out.println("\t[*] Checking conflict state with: " + branch);
        List<String> conflicts = new ArrayList<>();
        try (Repository gitRepo = openRepository(repoPath.getAbsolutePath())) {
            try (Git git = new Git(gitRepo)) {
                MergeCommand mergeCommand = git.merge();
                mergeCommand.include(gitRepo.exactRef("refs/heads/" + branch));
                mergeCommand.setCommit(false);
                mergeCommand.setFastForward(MergeCommand.FastForwardMode.NO_FF);
                mergeCommand.setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out)));
                MergeResult result = mergeCommand.call();

                if (result.getMergeStatus() == MergeResult.MergeStatus.CONFLICTING) {
                    for (Map.Entry<String, int[][]> entry : result.getConflicts().entrySet()) {
                        conflicts.add(entry.getKey());
                    }
                    System.out.println("\t\t[✓] Has conflicts.");
                } else {
                    System.out.println("\t\t[-] Nothing.");
                }
            } catch (GitAPIException ex) {
                System.out.printf("\t[e] Git checkout failed! %s %s%n", ex.getClass().getCanonicalName(), ex.getMessage());
            }
        } catch (IOException ex) {
            System.out.printf("\t[e] Git checkout failed! %s %s%n", ex.getClass().getCanonicalName(), ex.getMessage());
        }
        return conflicts;
    }
    //</editor-fold>

}
