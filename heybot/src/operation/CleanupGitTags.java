package operation;

import com.g00fy2.versioncompare.Version;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.util.FS;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import utilities.Properties;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * Operation: cleanup-git-tags
 *
 * @author onuryilmaz
 */
public class CleanupGitTags extends Operation
{
    //<editor-fold defaultstate="collapsed" desc="parameters">
    // mandatory
    private final static String PARAMETER_VERSION = "VERSION";
    private final static String PARAMETER_GIT_REPOSITORY = "GIT_REPOSITORY";
    private final static String PARAMETER_GIT_PROTOCOL = "GIT_PROTOCOL";
    // conditional optional
    private final static String PARAMETER_GIT_USERNAME = "GIT_USERNAME";
    private final static String PARAMETER_GIT_PASSWORD = "GIT_PASSWORD";
    private final static String PARAMETER_SSH_PRIVATE_KEY = "SSH_PRIVATE_KEY";
    // optional
    private final static String PARAMETER_IS_EQUAL = "IS_EQUAL";
    private final static String PARAMETER_LOWER_THAN = "LOWER_THAN";
    private final static String PARAMETER_LIMIT = "LIMIT";
    private final static String PARAMETER_GIT_CONFIG_USER_NAME = "GIT_CONFIG_USER_NAME";
    private final static String PARAMETER_GIT_CONFIG_USER_EMAIL = "GIT_CONFIG_USER_EMAIL";
    private final static String PARAMETER_GITLAB_URL = "GITLAB_URL";
    private final static String PARAMETER_GITLAB_TOKEN = "GITLAB_TOKEN";
    //</editor-fold>

    private final static HashSet<String> SUPPORTED_PROTOCOLS = new HashSet<>(Arrays.asList("http", "https", "ssh"));
    private String repository;
    private CredentialsProvider credentialsProvider;
    private TransportConfigCallback transportConfigCallback;

    public CleanupGitTags() {
        super(new String[]
            {
                PARAMETER_VERSION, PARAMETER_GIT_REPOSITORY, PARAMETER_GIT_PROTOCOL
            }
        );
    }

    @Override
    public void execute(Properties prop) {
        if (areMandatoryParametersNotEmpty(prop) && isGitCredentialsOk(prop)) {
            File repoPath;
            if ((repoPath = tryGetRepoPath(prop)) != null) {
                GitTag[] gitTags = getAllVersionedTags(repoPath);
                if (gitTags.length > 0) {
                    deleteStaleTags(prop, repoPath, gitTags);
                } else {
                    System.out.println("[✓] There is no versioned tag found in repository.");
                }
            }
        }
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
            isRepoReady = fetchRepository(repoPath);
        }

        if (isRepoReady) {
            configProject(prop, repoPath);
            return repoPath;
        }
        return null;
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
                //fetch.setTagOpt(TagOpt.FETCH_TAGS);
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

    private GitTag[] getAllVersionedTags(File repoPath) {
        System.out.println("[*] Getting all versioned tags ...");
        try (Repository gitRepo = openRepository(repoPath.getAbsolutePath())) {
            try (Git git = new Git(gitRepo)) {
                ListTagCommand tagList = git.tagList();
                List<Ref> refs = tagList.call();
                List<GitTag> gitTags = new ArrayList<>();
                refs.forEach((ref) -> {
                    try {
                        gitTags.add(new GitTag(ref));
                    } catch (Exception e) {
                        // non-versioned custom tags ignored
                    }
                });
                System.out.println("\t[i] Total: " + gitTags.size() + " tag(s) found.");
                return gitTags.toArray(new GitTag[0]);
            } catch (GitAPIException ex) {
                System.out.println("\t[e] Git, get all tags failed! " + ex.getClass().getCanonicalName() + " " + ex.getMessage());
            }
        } catch (IOException ex) {
            System.out.println("\t[e] Git, get all tags failed! " + ex.getClass().getCanonicalName() + " " + ex.getMessage());
        }
        return new GitTag[0];
    }

    private void deleteStaleTags(Properties prop, File repoPath, GitTag[] gitTags) {
        boolean lowerThan = getParameterBoolean(prop, PARAMETER_LOWER_THAN);
        boolean isEqual = getParameterBoolean(prop, PARAMETER_IS_EQUAL);
        if (!lowerThan && !isEqual) {
            System.out.println("\t\t[e] Stale comparison parameters are misconfigured! Both or one of the below parameters should be set as 'true';");
            System.out.println("\t\t[e] " + PARAMETER_LOWER_THAN + "," + PARAMETER_IS_EQUAL);
            return;
        }

        GitLabApi gitlabApi = tryGetGitlabApi(prop);

        int max = getParameterInt(prop, PARAMETER_LIMIT, Integer.MAX_VALUE);
        String staleVersion = getParameterString(prop, PARAMETER_VERSION, true);
        int cnt = 0;
        for (GitTag gitTag : gitTags) {
            if (gitTag.deleteIfStale(repoPath, lowerThan, isEqual, staleVersion, gitlabApi) && ++cnt == max) {
                break; // limit reached!
            }
        }
        System.out.println("\t[i] Total: " + cnt + " tag(s) deleted.");
    }

    private GitLabApi tryGetGitlabApi(Properties prop) {
        String gitlabUrl = getParameterString(prop, PARAMETER_GITLAB_URL, false);
        String gitlabToken = getParameterString(prop, PARAMETER_GITLAB_TOKEN, false);
        if (StringUtils.isBlank(gitlabUrl) || StringUtils.isBlank(gitlabToken)) {
            System.out.println("\t[w] Gitlab API credentials is empty or insufficient. (GITLAB_*)");
            System.out.println("\t[i] If tag is protected, it can't be made unprotected and deleted.");
            return null;
        }
        GitLabApi api = new GitLabApi(gitlabUrl, gitlabToken);
        try {
            org.gitlab4j.api.models.Version version = api.getVersion();
            System.out.println("\t[i] Gitlab: " + version.getVersion() + "(" + version.getRevision() + ")");
        } catch (GitLabApiException e) {
            System.out.println("\t[w] Gitlab API credentials is misconfigured or server is unreachable. (GITLAB_*)");
            System.out.println("\t[w] " + e.getMessage() + " " + e.getReason());
            System.out.println("\t[i] If tag is protected, it can't be made unprotected and deleted.");
            return null;
        }

        return api;
    }

    class GitTag
    {

        private final Ref tag;
        private final String name;
        private final Version version;

        GitTag(Ref tag) {
            this.name = tag.getName().replace("refs/tags/", "");
            this.version = new Version(this.name, true);
            this.tag = tag;
        }

        @Override
        public String toString() {
            return name + " " + tag.getObjectId().getName();
        }

        boolean deleteIfStale(File repoPath, boolean lowerThan, boolean isEqual, String staleVersion, GitLabApi gitlabApi) {
            if (isEqual && version.isEqual(staleVersion)) {
                System.out.println("\t\t[*] " + toString());
                System.out.println("\t\t\t[i] equals to " + staleVersion);
                return delete(repoPath, gitlabApi);
            }
            if (lowerThan && version.isLowerThan(staleVersion)) {
                System.out.println("\t\t[*] " + toString());
                System.out.println("\t\t\t[i] lower than " + staleVersion);
                return delete(repoPath, gitlabApi);
            }
            return false;
        }

        private boolean delete(File repoPath, GitLabApi gitlabApi) {
            System.out.println("\t\t\t[*] Deleting ...");
            try (Repository gitRepo = openRepository(repoPath.getAbsolutePath())) {
                try (Git git = new Git(gitRepo)) {
                    final List<String> deleted = git.tagDelete().setTags(tag.getName()).call();
                    if (!deleted.isEmpty()) {
                        GitTagDeleteResult result = deleteRemote(git);
                        if (result == GitTagDeleteResult.REJECT_REASON_PROTECTED
                            && gitlabApi != null && unProtectTag(git, gitlabApi)) {
                            return deleteRemote(git) == GitTagDeleteResult.SUCCESS;
                        }
                        return result == GitTagDeleteResult.SUCCESS;
                    }
                    return true;
                } catch (JGitInternalException | GitAPIException ex) {
                    System.out.println("\t\t\t[e] Git delete failed! " + ex.getClass().getCanonicalName() + " " + ex.getMessage());
                    handleLockFailureIfExists(repoPath, ex);
                }
            } catch (IOException ex) {
                System.out.println("\t\t\t[e] Git delete failed! " + ex.getClass().getCanonicalName() + " " + ex.getMessage());
            }
            return false;
        }

        private void handleLockFailureIfExists(File repoPath, Exception ex) {
            if (ex.getMessage().contains("LOCK_FAILURE")) {
                File lockFile = new File(repoPath.getAbsolutePath() + "/.git/refs/tags/" + name + ".lock");
                if (lockFile.exists()) {
                    System.out.println("\t\t\t[e] Lock file found below. Please delete it manually, and try again:");
                    System.out.println("\t\t\t[$] rm -rf " + lockFile.getAbsolutePath());
                }
            }
        }

        private boolean unProtectTag(Git git, GitLabApi gitlabApi) {
            System.out.println("\t\t\t[*] Trying to unprotect tag ...");
            System.out.println("\t\t\t[w] Not implemented yet!");
            return false;
        }

        private GitTagDeleteResult deleteRemote(Git git) throws GitAPIException {
            RefSpec refSpec = new RefSpec()
                .setSource(null)
                .setDestination("refs/tags/" + name);
            Iterable<PushResult> results = git.push().setRefSpecs(refSpec).setRemote("origin")
                .setCredentialsProvider(credentialsProvider)
                .setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out)))
                .call();
            for (PushResult result : results) {
                Collection<RemoteRefUpdate> remoteUpdates = result.getRemoteUpdates();
                for (RemoteRefUpdate refUpdate : remoteUpdates) {
                    if (refUpdate.getStatus() != RemoteRefUpdate.Status.OK) {
                        System.out.println("\t\t\t[e] " + result.getMessages());
                        System.out.println("\t\t\t[e] " + refUpdate.getMessage());
                        if (result.getMessages().toLowerCase().contains("protected tags cannot be deleted")) {
                            return GitTagDeleteResult.REJECT_REASON_PROTECTED;
                        } else {
                            return GitTagDeleteResult.REJECT_REASON_OTHER;
                        }
                    }
                }
            }
            System.out.println("\t\t\t[✓] Deleted.");
            return GitTagDeleteResult.SUCCESS;
        }
    }

    enum GitTagDeleteResult
    {
        SUCCESS,
        REJECT_REASON_PROTECTED,
        REJECT_REASON_OTHER
    }

}
