package operation;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.taskadapter.redmineapi.*;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.util.FS;
import utilities.Properties;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * Operation: cleanup-git
 *
 * @author onuryilmaz
 */
public class CleanupGit extends Operation
{

    private RedmineManager redmineManager;
    private final Locale trLocale = new Locale("tr-TR");

    //<editor-fold defaultstate="collapsed" desc="parameters">
    // mandatory
    private final static String PARAMETER_REDMINE_STATUS = "REDMINE_STATUS";
    private final static String PARAMETER_REDMINE_TOKEN = "REDMINE_TOKEN";
    private final static String PARAMETER_REDMINE_URL = "REDMINE_URL";
    private final static String PARAMETER_GIT_REPOSITORY = "GIT_REPOSITORY";
    private final static String PARAMETER_GIT_PROTOCOL = "GIT_PROTOCOL";
    // conditional optional
    private final static String PARAMETER_GIT_USERNAME = "GIT_USERNAME";
    private final static String PARAMETER_GIT_PASSWORD = "GIT_PASSWORD";
    private final static String PARAMETER_SSH_PRIVATE_KEY = "SSH_PRIVATE_KEY";
    // optional
    private final static String PARAMETER_LIMIT = "LIMIT";
    private final static String PARAMETER_GIT_CONFIG_USER_NAME = "GIT_CONFIG_USER_NAME";
    private final static String PARAMETER_GIT_CONFIG_USER_EMAIL = "GIT_CONFIG_USER_EMAIL";
    //</editor-fold>

    private final static HashSet<String> SUPPORTED_PROTOCOLS = new HashSet<>(Arrays.asList("http", "https", "ssh"));
    private String repository;
    private CredentialsProvider credentialsProvider;
    private TransportConfigCallback transportConfigCallback;

    public CleanupGit() {
        super(new String[]
            {
                PARAMETER_REDMINE_STATUS, PARAMETER_REDMINE_TOKEN, PARAMETER_REDMINE_URL,
                PARAMETER_GIT_REPOSITORY, PARAMETER_GIT_PROTOCOL
            }
        );
    }

    @Override
    public void execute(Properties prop) {
        if (areMandatoryParametersNotEmpty(prop) && isGitCredentialsOk(prop)) {
            File repoPath;
            if ((repoPath = tryGetRepoPath(prop)) != null) {
                String redmineAccessToken = getParameterString(prop, PARAMETER_REDMINE_TOKEN, false);
                String redmineUrl = getParameterString(prop, PARAMETER_REDMINE_URL, false);
                redmineManager = RedmineManagerFactory.createWithApiKey(redmineUrl, redmineAccessToken);
                GitBranch[] gitBranches = getAllRemoteBranches(repoPath);
                if (gitBranches.length > 0) {
                    deleteStaleBranches(prop, repoPath, gitBranches);
                } else {
                    System.out.println("[✓] There is no issue related remote branch at repository.");
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

    private GitBranch[] getAllRemoteBranches(File repoPath) {
        System.out.println("[*] Getting all issue related remote branches ...");
        try (Repository gitRepo = openRepository(repoPath.getAbsolutePath())) {
            try (Git git = new Git(gitRepo)) {
                ListBranchCommand branchList = git.branchList();
                branchList.setListMode(ListBranchCommand.ListMode.REMOTE);
                List<Ref> refs = branchList.call();
                List<GitBranch> gitBranches = new ArrayList<>();
                refs.forEach((ref) -> {
                    try {
                        gitBranches.add(new GitBranch(ref));
                    } catch (Exception e) {
                        // master or other non-issue related branches
                    }
                });
                System.out.println("\t[i] Total: " + gitBranches.size());
                return gitBranches.toArray(new GitBranch[0]);
            } catch (GitAPIException ex) {
                System.out.println("\t[e] Git listing local branches failed! " + ex.getClass().getCanonicalName() + " " + ex.getMessage());
            }
        } catch (IOException ex) {
            System.out.println("\t[e] Git listing local branches failed! " + ex.getClass().getCanonicalName() + " " + ex.getMessage());
        }
        return new GitBranch[0];
    }

    private void deleteStaleBranches(Properties prop, File repoPath, GitBranch[] gitBranches) {
        HashSet<String> statuses = getParameterStringHash(prop, PARAMETER_REDMINE_STATUS, true);
        int max = getParameterInt(prop, PARAMETER_LIMIT, Integer.MAX_VALUE);
        for (GitBranch gitBranch : gitBranches) {
            System.out.println("\t\t[*] " + gitBranch.toString());
            if (gitBranch.deleteIfStale(repoPath, statuses) && --max == 0) {
                break; // limit reached!
            }
        }
    }

    class GitBranch
    {

        private final int issueId;
        private final Ref branch;
        private final String name;

        GitBranch(Ref branch) throws Exception {
            this.name = branch.getName().replace("refs/remotes/origin/", "");
            String numbers = this.name.replaceAll("[^0-9]", "");
            if (numbers.length() > 0) {
                this.issueId = Integer.parseInt(numbers);
            } else {
                throw new Exception("Branch not related with an issue.");
            }
            this.branch = branch;
        }

        @Override
        public String toString() {
            return "#" + issueId + " -> " + name + " " + branch.getObjectId().getName();
        }

        boolean deleteIfStale(File repoPath, HashSet<String> statuses) {
            String status = getStatus();
            System.out.println("\t\t\t[i] Status: " + status);
            if (statuses.contains(status)) {
                return delete(repoPath);
            }
            return false;
        }

        private boolean delete(File repoPath) {
            System.out.println("\t\t\t[*] Deleting ...");
            try (Repository gitRepo = openRepository(repoPath.getAbsolutePath())) {
                try (Git git = new Git(gitRepo)) {
                    git.branchDelete().setBranchNames(branch.getName()).setForce(true).call();
                    RefSpec refSpec = new RefSpec()
                        .setSource(null)
                        .setDestination("refs/heads/" + name);
                    git.push().setRefSpecs(refSpec).setRemote("origin")
                        .setCredentialsProvider(credentialsProvider)
                        .setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out)))
                        .call();
                    System.out.println("\t\t\t[✓] Deleted.");
                    return true;
                } catch (GitAPIException ex) {
                    System.out.println("\t\t\t[e] Git delete failed! " + ex.getClass().getCanonicalName() + " " + ex.getMessage());
                }
            } catch (IOException ex) {
                System.out.println("\t\t\t[e] Git delete failed! " + ex.getClass().getCanonicalName() + " " + ex.getMessage());
            }
            return false;
        }

        private String getStatus() {
            try {
                return redmineManager.getIssueManager().getIssueById(issueId).getStatusName().toLowerCase(trLocale);
            } catch (RedmineException ex) {
                if (ex instanceof NotFoundException) {
                    return "404";
                } else if (ex instanceof NotAuthorizedException) {
                    return "403";
                }
            }
            return "?"; // unknown!
        }
    }

}
