package operation;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineManagerFactory;
import com.taskadapter.redmineapi.bean.Issue;
import com.taskadapter.redmineapi.bean.Version;
import com.taskadapter.redmineapi.bean.VersionFactory;
import model.VersionTag;
import org.apache.commons.lang3.StringUtils;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.util.FS;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.TagsApi;
import org.gitlab4j.api.models.AccessLevel;
import utilities.Properties;

import java.io.*;
import java.util.*;

import static org.apache.http.util.TextUtils.isEmpty;

/**
 * @author onuryilmaz
 */
public class NextVersion extends Operation
{
    //<editor-fold desc="parameters">
    // mandatory
    private final static String PARAMETER_REDMINE_TOKEN = "REDMINE_TOKEN";
    private final static String PARAMETER_REDMINE_URL = "REDMINE_URL";
    private final static String PARAMETER_FILTER_PROJECT = "FILTER_PROJECT";
    private final static String PARAMETER_FILTER_QUERY = "FILTER_QUERY";
    private final static String PARAMETER_VERSION_TITLE = "VERSION_TITLE";
    private final static String PARAMETER_MAJOR_TRACKER = "MAJOR_TRACKER";
    private final static String PARAMETER_MINOR_TRACKER = "MINOR_TRACKER";
    private final static String PARAMETER_PATCH_TRACKER = "PATCH_TRACKER";
    // optional
    private final static String PARAMETER_CLOSE_PREVIOUS = "CLOSE_PREVIOUS";
    private final static String PARAMETER_APPEND_CURRENT = "APPEND_CURRENT";
    private final static String PARAMETER_CREATE_GIT_TAG = "CREATE_GIT_TAG";
    private final static String PARAMETER_GIT_REPOSITORY = "GIT_REPOSITORY";
    private final static String PARAMETER_GIT_PROTOCOL = "GIT_PROTOCOL";
    private final static String PARAMETER_GIT_USERNAME = "GIT_USERNAME";
    private final static String PARAMETER_GIT_PASSWORD = "GIT_PASSWORD";
    private final static String PARAMETER_SSH_PRIVATE_KEY = "SSH_PRIVATE_KEY";
    private final static String PARAMETER_GIT_CONFIG_USER_NAME = "GIT_CONFIG_USER_NAME";
    private final static String PARAMETER_GIT_CONFIG_USER_EMAIL = "GIT_CONFIG_USER_EMAIL";
    private final static String PARAMETER_APP_VERSION_FILE_PATH = "APP_VERSION_FILE_PATH";
    private final static String PARAMETER_APP_VERSION_FILE_PATTERN = "APP_VERSION_FILE_PATTERN";
    private final static String PARAMETER_APP_VERSION_FILE_UPCMD = "APP_VERSION_FILE_UPCMD";
    private final static String PARAMETER_APP_BUILD_FILE_PATH = "APP_BUILD_FILE_PATH";
    private final static String PARAMETER_APP_BUILD_FILE_PATTERN = "APP_BUILD_FILE_PATTERN";
    private final static String PARAMETER_APP_BUILD_FILE_UPCMD = "APP_BUILD_FILE_UPCMD";
    private final static String PARAMETER_APP_BUILD_OFFSET = "APP_BUILD_OFFSET";
    private final static String PARAMETER_GITLAB_URL = "GITLAB_URL";
    private final static String PARAMETER_GITLAB_TOKEN = "GITLAB_TOKEN";
    private final static String PARAMETER_GITLAB_PROJECT_ID = "GITLAB_PROJECT_ID";
    // internal
    private final static String PARAMETER_VERSION_TAG = "VERSION_TAG";
    private final static String PARAMETER_PREVIOUS_VERSION_TAG = "PREVIOUS_VERSION_TAG";
    private final static String PARAMETER_VERSION_ID = "VERSION_ID";
    //</editor-fold>

    public NextVersion() {
        super(new String[]
            {
                PARAMETER_REDMINE_TOKEN, PARAMETER_REDMINE_URL, PARAMETER_FILTER_PROJECT, PARAMETER_FILTER_QUERY, PARAMETER_VERSION_TITLE, PARAMETER_MAJOR_TRACKER, PARAMETER_MINOR_TRACKER, PARAMETER_PATCH_TRACKER
            }
        );
    }

    @Override
    public void execute(Properties prop) throws Exception {
        if (areMandatoryParametersNotEmpty(prop)) {
            String redmineAccessToken = getParameterString(prop, PARAMETER_REDMINE_TOKEN, false);
            String redmineUrl = getParameterString(prop, PARAMETER_REDMINE_URL, false);
            RedmineManager redmineManager = RedmineManagerFactory.createWithApiKey(redmineUrl, redmineAccessToken);
            String filterProject = getParameterString(prop, PARAMETER_FILTER_PROJECT, true);
            int projectId = tryGetProjectId(redmineManager, filterProject);
            if (projectId > 0) {
                maintainRedmineVersion(prop, filterProject, redmineManager, projectId);
                maintainGitTag(prop, redmineManager, getParameterInt(prop, PARAMETER_VERSION_ID, 0));
            } else {
                System.out.println("[e] Couldn't find project. Operation works only on valid redmine project.");
            }
        }
    }

    //<editor-fold desc="Redmine Version">
    private void maintainRedmineVersion(Properties prop, String filterProject, RedmineManager redmineManager, int projectId) throws Exception {
        String filterQuery = getParameterString(prop, PARAMETER_FILTER_QUERY, true);
        String versionTitle = getParameterString(prop, PARAMETER_VERSION_TITLE, false);
        Issue[] issues = getReadyUnversionedIssues(redmineManager, filterProject, filterQuery);
        if (issues.length > 0) {
            Version version;
            int versionId = getParameterInt(prop, PARAMETER_VERSION_ID, 0);
            boolean closePreviousVersion = getParameterBoolean(prop, PARAMETER_CLOSE_PREVIOUS);
            boolean appendCurrentVersion = getParameterBoolean(prop, PARAMETER_APPEND_CURRENT);
            if (appendCurrentVersion) {
                version = appendVersion(redmineManager, prop, issues, versionId, versionTitle);
            } else {
                if (closePreviousVersion) {
                    closeVersion(redmineManager, versionId);
                }
                version = createVersion(redmineManager, prop, issues, projectId, versionTitle);
            }
            assignTargetVersion(redmineManager, issues, version);
        }
    }

    private void closeVersion(RedmineManager redmineManager, int versionId) {
        if (versionId > 0) {
            try {
                System.out.println("[*] Getting version from redmine: " + versionId);
                Version version = redmineManager.getProjectManager().getVersionById(versionId);
                if (!version.getStatus().equals("closed")) {
                    version.setStatus("closed");
                    redmineManager.getProjectManager().update(version);
                    System.out.println("[✓] Version " + version.getName() + " is updated as closed.");
                } else {
                    System.out.println("[✓] Version " + version.getName() + " is already closed.");
                }
            } catch (RedmineException ex) {
                System.out.println("[e] Couldn't complete closing last version! " + ex.getMessage());
            }
        }
    }

    private Version createVersion(RedmineManager redmineManager, int projectId, String versionTitle, String versionTag) {
        String versionName = versionTitle + "-" + versionTag;
        try {
            System.out.println("[*] Creating new redmine version: " + versionName);
            Version version = VersionFactory.create(projectId, versionName);
            version.setStatus("open");
            version = redmineManager.getProjectManager().createVersion(version);
            System.out.println("[✓] VERSION_ID=" + version.getId());
            return version;
        } catch (RedmineException ex) {
            System.out.println("[e] Can't create new version. " + ex.getMessage());
        }
        return null;
    }

    private Issue[] getReadyUnversionedIssues(RedmineManager redmineManager, String filterProject, String filterQuery) {
        int filterSavedQueryId = tryGetSavedQueryId(redmineManager, filterProject, filterQuery);
        if (filterSavedQueryId > 0) {
            Issue[] issues = getProjectIssues(redmineManager, filterProject, filterSavedQueryId);
            System.out.println("[✓] Ready to release and unversioned " + issues.length + " issue(s) found.");
            return issues;
        } else {
            System.out.println("[e] Couldn't find saved query. Saved query contains ready and unversioned issues.");
        }
        return new Issue[0];
    }

    private void assignTargetVersion(RedmineManager redmineManager, Issue[] issues, Version version) {
        for (Issue issue : issues) {
            System.out.println("\t #" + issue.getId() + " [" + issue.getTracker().getName() + "]" + " " + issue.getSubject());
            issue.setTargetVersion(version);
            try {
                redmineManager.getIssueManager().update(issue);
                System.out.println("[✓] Target Version: " + version.getName());
            } catch (RedmineException ex) {
                System.out.println("[e] Can't assign target version. " + ex.getMessage());
            }
        }
    }

    private boolean updateVersion(RedmineManager redmineManager, Version version, String versionTitle, String versionTag) {
        String versionName = versionTitle + "-" + versionTag;
        try {
            System.out.println("[*] Updating redmine version: " + version.getName() + " -> " + versionName);
            version.setName(versionName);
            redmineManager.getProjectManager().update(version);
            System.out.println("[✓] VERSION_ID=" + version.getId());
            return true;
        } catch (RedmineException ex) {
            System.out.println("[*] Can't update existing version. " + ex.getMessage());
        }
        return false;
    }

    private Version createVersion(RedmineManager redmineManager, Properties prop, Issue[] issues, int projectId, String versionTitle) throws Exception {
        VersionTag versionTag = new VersionTag(getParameterString(prop, PARAMETER_VERSION_TAG, false),
            getParameterStringHash(prop, PARAMETER_MAJOR_TRACKER, true),
            getParameterStringHash(prop, PARAMETER_MINOR_TRACKER, true),
            getParameterStringHash(prop, PARAMETER_PATCH_TRACKER, true));

        versionTag.next(issues);
        Version version = createVersion(redmineManager, projectId, versionTitle, versionTag.toString());
        if (version != null) {
            setParameterString(prop, PARAMETER_PREVIOUS_VERSION_TAG, getParameterString(prop, PARAMETER_VERSION_TAG, false));
            setParameterString(prop, PARAMETER_VERSION_TAG, versionTag.toString());
            setParameterInt(prop, PARAMETER_VERSION_ID, version.getId());
        }
        return version;
    }

    private Version appendVersion(RedmineManager redmineManager, Properties prop, Issue[] issues, int versionId, String versionTitle) throws Exception {
        VersionTag versionTag = new VersionTag(getParameterString(prop, PARAMETER_VERSION_TAG, false),
            getParameterString(prop, PARAMETER_PREVIOUS_VERSION_TAG, false),
            getParameterStringHash(prop, PARAMETER_MAJOR_TRACKER, true),
            getParameterStringHash(prop, PARAMETER_MINOR_TRACKER, true),
            getParameterStringHash(prop, PARAMETER_PATCH_TRACKER, true));

        versionTag.next(issues);
        Version version = getVersion(redmineManager, versionId);
        if (version != null) {
            if (updateVersion(redmineManager, version, versionTitle, versionTag.toString())) {
                setParameterString(prop, PARAMETER_VERSION_TAG, versionTag.toString());
            }
        }
        return version;
    }
    //</editor-fold>

    //<editor-fold desc="Git Tag">
    private void maintainGitTag(Properties prop, RedmineManager redmineManager, int versionId) {
        if (getParameterBoolean(prop, PARAMETER_CREATE_GIT_TAG) && isGitCredentialsOk(prop)) {
            Version version = getVersion(redmineManager, versionId);
            if (version == null) {
                System.out.println("[e] Create git tag is enabled but couldn't get version from redmine!");
            } else {
                File repoPath;
                if ((repoPath = tryGetRepoPath(prop)) != null) {
                    if (updateAppBuild(prop, repoPath) && updateAppVersion(prop, repoPath, version)) {
                        if (pushAppUpdate(repoPath)) {
                            GitTag tag = pushTagUpdate(repoPath, version);
                            protectTagIfUnprotected(prop, tag);
                        }
                    } else {
                        System.out.println("[w] Couldn't update app version or build. No git tag is created.");
                    }
                }
            }
        }
    }

    private GitTag pushTagUpdate(File repoPath, Version version) {
        GitTag tag = new GitTag(repoPath, getVersionTag(version.getName()));
        if (tagNotExistsOrUserWantsForceUpdate(tag)) {
            tag.create(repoPath);
        }
        return tag;
    }

    private boolean tagNotExistsOrUserWantsForceUpdate(GitTag tag) {
        if (tag.exists()) {
            System.out.println("[i] Tag exists in repository: " + tag);
            Scanner scanner = new Scanner(System.in);
            System.out.print("[?] Would you like to override existing tag? (y/n) ");
            String answer = scanner.next();
            if (!isEmpty(answer) && (answer.charAt(0) == 'Y' || answer.charAt(0) == 'y')) {
                System.out.println("[✓] Ok, tag push will be forced.");
                return true;
            }
            System.out.println("[i] Ok, tag push canceled.");
            return false;
        }
        return true;
    }

    private boolean pushAppUpdate(File repoPath) {
        if (isRepositoryClean(repoPath)) {
            System.out.println("[i] There is no modification to push.");
            return true;
        }
        showRepositoryDiff(repoPath);
        Scanner scanner = new Scanner(System.in);
        System.out.print("[?] Would you like to push modifications? (y/n) ");
        String answer = scanner.next();
        if (!isEmpty(answer) && (answer.charAt(0) == 'Y' || answer.charAt(0) == 'y')) {
            if (pushModifications(repoPath)) {
                System.out.println("[✓] Modifications pushed successfully.");
                return true;
            } else {
                System.out.println("[w] Modifications not pushed!");
            }
            return false;
        } else {
            System.out.println("[i] Modifications not pushed!");
            System.out.print("[?] Would you like to revert modifications? (y/n) ");
            answer = scanner.next();
            if (!isEmpty(answer) && (answer.charAt(0) == 'Y' || answer.charAt(0) == 'y')) {
                resetRepository(repoPath);
                if (isRepositoryClean(repoPath)) {
                    System.out.println("[✓] Modifications reverted successfully.");
                    return true;
                } else {
                    System.out.println("[w] Modifications can't be reverted!");
                }
                return false;
            }
        }
        return true;
    }

    private boolean updateAppBuild(Properties prop, File repoPath) {
        String[] filePaths = getParameterStringArray(prop, PARAMETER_APP_BUILD_FILE_PATH, false);
        if (filePaths.length == 0) {
            return true; // optional, user doesn't want build number to be updated.
        }
        int build = getBuild(repoPath, prop);
        if (build > 0) {
            String pattern = getParameterString(prop, PARAMETER_APP_BUILD_FILE_PATTERN, false);
            if (!StringUtils.isBlank(pattern)) {
                return updateApp(repoPath, filePaths, pattern, build + "");
            }
            String upCmd = getParameterString(prop, PARAMETER_APP_BUILD_FILE_UPCMD, false);
            if (!StringUtils.isBlank(upCmd)) {
                return updateApp(fillUpCmdWithBuild(upCmd, build + "", repoPath, filePaths[0]));
            }
        }
        return false;
    }

    private boolean updateAppVersion(Properties prop, File repoPath, Version version) {
        String[] filePaths = getParameterStringArray(prop, PARAMETER_APP_VERSION_FILE_PATH, false);
        if (filePaths.length == 0) {
            return true; // optional, user doesn't want version to be updated.
        }
        String versionTag = getVersionTag(version.getName());
        if (!StringUtils.isBlank(versionTag)) {
            String pattern = getParameterString(prop, PARAMETER_APP_VERSION_FILE_PATTERN, false);
            if (!StringUtils.isBlank(pattern)) {
                return updateApp(repoPath, filePaths, pattern, versionTag);
            }
            String upCmd = getParameterString(prop, PARAMETER_APP_VERSION_FILE_UPCMD, false);
            if (!StringUtils.isBlank(upCmd)) {
                return updateApp(fillUpCmdWithVersion(upCmd, versionTag, repoPath, filePaths[0]));
            }
        }
        return false;
    }

    private boolean updateApp(File repo, String[] files, String pattern, String replace) {
        files = trimLeft(files, "/");

        String[] parts = pattern.split("<>");
        if (parts.length != 3) {
            System.out.printf("[e] Pattern is not recognized! (%s)", pattern);
            return false;
        }
        String headPattern = parts[0];
        String tailPattern = parts[2];
        for (String file : files) {
            if (!replaceInFile(repo.getAbsolutePath() + "/" + file, headPattern, replace, tailPattern)) {
                System.out.print("[e] Replacing in app file could not be done!");
                return false;
            }
        }
        return true;
    }

    private boolean replaceInFile(String filePath, String headPattern, String versionTag, String tailPattern) {
        System.out.println("[*] Replacing app version file " + filePath);
        File in = new File(filePath);
        if (!in.exists()) {
            System.out.println("[e] The input file " + in + " does not exist!");
            return false;
        }
        File out = new File(filePath + "_tmp");
        if (out.exists()) {
            System.out.println("[e] The output file " + out + " already exists!");
            return false;
        }

        try {
            BufferedReader reader = new BufferedReader(new FileReader(in));
            PrintWriter writer = new PrintWriter(new FileWriter(out));

            String line;
            while ((line = reader.readLine()) != null) {
                writer.println(replaceInLine(line, headPattern, versionTag, tailPattern));
            }

            reader.close();
            writer.close();

            //noinspection ResultOfMethodCallIgnored
            in.delete();
            //noinspection ResultOfMethodCallIgnored
            out.renameTo(in);

            return true;
        } catch (IOException ex) {
            System.err.println(ex.getMessage());
        }

        return false;
    }

    private String replaceInLine(String line, String headPattern, String versionTag, String tailPattern) {
        int index = line.indexOf(headPattern);
        if (index >= 0) {
            index += headPattern.length();

            String temp = line.substring(0, index);
            temp += versionTag;

            index = line.lastIndexOf(tailPattern);
            if (index >= 0) {
                temp += line.substring(index);

                System.out.println("\n line (->): " + line);
                System.out.println("\t is replaced with");
                System.out.println(" line (<-): " + temp + "\n");

                return temp;
            }
        }

        return line;
    }

    private String fillUpCmdWithBuild(String upCmd, String build, File repoPath, String filePath) {
        Velocity.init();

        VelocityContext context = new VelocityContext();
        context.put("file", repoPath.getAbsolutePath() + "/" + filePath);
        context.put("build", build);

        StringWriter writer = new StringWriter();
        Velocity.evaluate(context, writer, "TemplateName", upCmd);
        return writer.toString();
    }

    private String fillUpCmdWithVersion(String upCmd, String version, File repoPath, String filePath) {
        Velocity.init();

        VelocityContext context = new VelocityContext();
        context.put("file", repoPath.getAbsolutePath() + "/" + filePath);
        context.put("version", version);

        StringWriter writer = new StringWriter();
        Velocity.evaluate(context, writer, "TemplateName", upCmd);
        return writer.toString();
    }

    private boolean updateApp(String upCmd) {
        String[] upCmdResult = execute(upCmd);
        System.out.println(upCmdResult[0]);
        System.out.println(upCmdResult[1]);
        System.out.println("Exit Code: " + upCmdResult[2]);
        return upCmdResult[2].equals("0");
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
            if (isRepoReady = fetchRepository(repoPath)) {
                isRepoReady = pullRepository(repoPath);
                RepositoryStatus status = getRepositoryStatus(repoPath);
                if (status.isBehindRemote() && status.hasLocalChanges()) {
                    fastForwardBranch(repoPath);
                    getRepositoryStatus(repoPath);
                }
            }
        }

        if (isRepoReady) {
            configProject(prop, repoPath);
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

    private void configProject(Properties prop, File localBranch) {
        String userName = getParameterString(prop, PARAMETER_GIT_CONFIG_USER_NAME, false);
        String userEmail = getParameterString(prop, PARAMETER_GIT_CONFIG_USER_EMAIL, false);
        if (!StringUtils.isBlank(userName) || !StringUtils.isBlank(userEmail)) {
            System.out.println("[*] Git config repository ...");
            try (Repository gitRepo = openRepository(localBranch.getAbsolutePath())) {
                StoredConfig config = gitRepo.getConfig();
                boolean isModified = false;

                String localUserName = config.getString("user", null, "name");
                System.out.println("[i] --local user.name: " + localUserName);
                if (!userName.equals(localUserName)) {
                    config.setString("user", null, "name", userName);
                    System.out.println("[✓] Modified as: " + userName);
                    isModified = true;
                } else {
                    System.out.println("[✓] user.name is ok. ");
                }
                String localUserEmail = config.getString("user", null, "email");
                System.out.println("[i] --local user.email: " + localUserEmail);
                if (!userEmail.equals(localUserEmail)) {
                    config.setString("user", null, "email", userEmail);
                    System.out.println("[✓] Modified as: " + userEmail);
                    isModified = true;
                } else {
                    System.out.println("[✓] user.email is ok. ");
                }
                if (isModified) {
                    config.save();
                    System.out.println("[✓] Modifications saved. ");
                }
            } catch (IOException ex) {
                System.out.printf("[e] Git config failed! %s %s%n", ex.getClass().getCanonicalName(), ex.getMessage());
            }
        }
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

    private RepositoryStatus getRepositoryStatus(File repoPath) {
        try (Repository gitRepo = openRepository(repoPath.getAbsolutePath())) {
            try (Git git = new Git(gitRepo)) {
                System.out.print("[i] Status of branch '" + gitRepo.getBranch() + "': ");
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
                System.out.printf("[e] Git status failed! %s %s%n", ex.getClass().getCanonicalName(), ex.getMessage());
            }
        } catch (IOException ex) {
            System.out.printf("[e] Git status failed! %s %s%n", ex.getClass().getCanonicalName(), ex.getMessage());
        }
        return new RepositoryStatus();
    }

    private int[] listBranchTrackingStatus(Repository repository, String branchName) throws IOException {
        int[] status = getTrackingStatus(repository, branchName);
        if (status[0] > 0 || status[1] > 0) {
            System.out.println("[i] Branch '" + branchName + "' is now (" + status[0] + ") commits ahead, (" + status[1] + ") commits behind.");
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

    private void listModifiedFiles(String title, Set<String> files) {
        if (files.size() > 0) {
            System.out.println("\t\t" + title + ": (" + files.size() + ")");
            files.forEach((file) -> System.out.println("\t\t\t" + file));
        }
    }

    private void fastForwardBranch(File repoPath) {
        System.out.println("[*] Fast-forwarding branch ...");
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
                rebase.setUpstream("origin/master");
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
                System.out.printf("\t\t[e] Fast-forward failed! %s %s%n", ex.getClass().getCanonicalName(), ex.getMessage());
            }
        } catch (IOException ex) {
            System.out.printf("\t\t[e] Fast-forward failed! %s %s%n", ex.getClass().getCanonicalName(), ex.getMessage());
        }
    }

    private int getBuild(File repoPath, Properties prop) {
        try (Repository gitRepo = openRepository(repoPath.getAbsolutePath())) {
            try (Git git = new Git(gitRepo)) {
                Iterable<RevCommit> commits = git.log().call();
                int count = getParameterInt(prop, PARAMETER_APP_BUILD_OFFSET, 0);
                for (RevCommit ignored : commits) {
                    count++;
                }
                return count;
            } catch (GitAPIException ex) {
                System.out.printf("\t[e] git rev-list --count failed! %s %s%n", ex.getClass().getCanonicalName(), ex.getMessage());
            }
        } catch (IOException ex) {
            System.out.printf("\t[e] git rev-list --count failed! %s %s%n", ex.getClass().getCanonicalName(), ex.getMessage());
        }
        return 0;
    }

    private void showRepositoryDiff(File cachePath) {
        System.out.println("[*] Git diff ...");
        try (Repository gitRepo = openRepository(cachePath.getAbsolutePath())) {
            try (Git git = new Git(gitRepo)) {
                git.diff().setOutputStream(System.out).call();
            } catch (GitAPIException ex) {
                System.out.printf("[e] Git diff failed! %s %s%n", ex.getClass().getCanonicalName(), ex.getMessage());
            }
        } catch (IOException ex) {
            System.out.printf("[e] Git diff failed! %s %s%n", ex.getClass().getCanonicalName(), ex.getMessage());
        }
    }

    private boolean pushModifications(File cachePath) {
        System.out.println("[*] Push modifications to remote ...");
        try (Repository gitRepo = openRepository(cachePath.getAbsolutePath())) {
            try (Git git = new Git(gitRepo)) {
                git.add().addFilepattern(".").call();
                git.commit().setMessage("App version or build is modified.").call();
                PushCommand pushCommand = git.push();
                pushCommand.setCredentialsProvider(credentialsProvider);
                pushCommand.setTransportConfigCallback(transportConfigCallback);
                pushCommand.setRemote("origin");
                pushCommand.setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out)));
                Iterable<PushResult> resultIterable = pushCommand.call();
                PushResult result = resultIterable.iterator().next();
                if (!StringUtils.isBlank(result.getMessages())) {
                    System.out.print("[i] Messages: " + result.getMessages());
                }
                return result.getRemoteUpdate("refs/heads/master").getStatus() == RemoteRefUpdate.Status.OK;
            } catch (GitAPIException ex) {
                System.out.printf("[e] Git commit+push failed! %s %s%n", ex.getClass().getCanonicalName(), ex.getMessage());
            }
        } catch (IOException ex) {
            System.out.printf("[e] Git commit+push failed! %s %s%n", ex.getClass().getCanonicalName(), ex.getMessage());
        }
        return false;
    }

    private boolean isRepositoryClean(File repoPath) {
        try (Repository gitRepo = openRepository(repoPath.getAbsolutePath())) {
            try (Git git = new Git(gitRepo)) {
                return !git.status().call().hasUncommittedChanges();
            } catch (GitAPIException ex) {
                System.out.printf("[e] Git status failed! %s %s%n", ex.getClass().getCanonicalName(), ex.getMessage());
            }
        } catch (IOException ex) {
            System.out.printf("[e] Git status failed! %s %s%n", ex.getClass().getCanonicalName(), ex.getMessage());
        }
        return true;
    }

    private void resetRepository(File repoPath) {
        try (Repository gitRepo = openRepository(repoPath.getAbsolutePath())) {
            try (Git git = new Git(gitRepo)) {
                git.reset()
                    .setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out)))
                    .setMode(ResetCommand.ResetType.HARD).call();
            } catch (GitAPIException ex) {
                System.out.printf("[e] Git reset failed! %s %s%n", ex.getClass().getCanonicalName(), ex.getMessage());
            }
        } catch (IOException ex) {
            System.out.printf("[e] Git reset failed! %s %s%n", ex.getClass().getCanonicalName(), ex.getMessage());
        }
    }

    private class GitTag
    {
        private final com.g00fy2.versioncompare.Version version;
        private Ref ref;

        GitTag(File repo, String version) {
            this.version = new com.g00fy2.versioncompare.Version(version, true);
            this.ref = tryGetTagRef(repo);
        }

        public boolean exists() {
            return ref != null;
        }

        private Ref tryGetTagRef(File repo) {
            try (Repository gitRepo = openRepository(repo.getAbsolutePath())) {
                try (Git git = new Git(gitRepo)) {
                    ListTagCommand listTagCommand = git.tagList();
                    List<Ref> refs = listTagCommand.call();
                    for (Ref ref : refs) {
                        if (ref.getName().endsWith(getName())) {
                            return ref;
                        }
                    }
                } catch (GitAPIException ex) {
                    System.out.printf("[e] Git tag list failed! %s %s%n", ex.getClass().getCanonicalName(), ex.getMessage());
                }
            } catch (IOException ex) {
                System.out.printf("[e] Git tag list failed! %s %s%n", ex.getClass().getCanonicalName(), ex.getMessage());
            }
            return null;
        }

        @Override
        public String toString() {
            if (ref != null) {
                return getName() + " " + ref.getObjectId().getName();
            }
            return getName();
        }

        void create(File repo) {
            try (Repository gitRepo = openRepository(repo.getAbsolutePath())) {
                try (Git git = new Git(gitRepo)) {
                    TagCommand tagCommand = git.tag();
                    tagCommand.setName(getName());
                    tagCommand.setForceUpdate(true);
                    this.ref = tagCommand.call();
                    if (this.ref.getObjectId() != null) {
                        PushCommand pushCommand = git.push();
                        pushCommand.setCredentialsProvider(credentialsProvider);
                        pushCommand.setTransportConfigCallback(transportConfigCallback);
                        pushCommand.setRemote("origin");
                        pushCommand.setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out)));
                        pushCommand.add(this.ref);
                        Iterable<PushResult> resultIterable = pushCommand.call();
                        PushResult result = resultIterable.iterator().next();
                        if (!StringUtils.isBlank(result.getMessages())) {
                            System.out.print("\t[i] Messages: " + result.getMessages());
                        }
                        if (result.getRemoteUpdate("refs/tags/" + getName()).getStatus() == RemoteRefUpdate.Status.OK) {
                            System.out.println("[✓] Tag is created successfully on remote.");
                        }
                    }
                } catch (GitAPIException ex) {
                    System.out.printf("[e] Git tag create failed! %s %s%n", ex.getClass().getCanonicalName(), ex.getMessage());
                }
            } catch (IOException ex) {
                System.out.printf("[e] Git tag create failed! %s %s%n", ex.getClass().getCanonicalName(), ex.getMessage());
            }
        }

        public String getName() {
            return version.getOriginalString();
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
    //</editor-fold>

    //<editor-fold desc="GitLab">
    private void protectTagIfUnprotected(Properties prop, GitTag tag) {
        GitLabApi gitlabApi = tryGetGitlabApi(prop);
        if (gitlabApi == null) {
            System.out.println("[i] Protect tag functions will be disabled.");
            return;
        }
        int gitlabProjectId = getParameterInt(prop, PARAMETER_GITLAB_PROJECT_ID, 0);
        if (isTagProtected(gitlabApi, gitlabProjectId, tag)) {
            System.out.println("[✓] Tag has already been protected.");
            return;
        }
        protectTag(gitlabApi, gitlabProjectId, tag);
    }

    private void protectTag(GitLabApi gitlabApi, int gitlabProjectId, GitTag tag) {
        TagsApi tagsApi = gitlabApi.getTagsApi();
        try {
            tagsApi.protectTag(gitlabProjectId, tag.getName(), AccessLevel.MAINTAINER);
            System.out.println("[✓] Tag protected successfully.");
        } catch (GitLabApiException e) {
            System.out.println("[w] Can't protect tag! " + e.getMessage());
        }
    }

    private boolean isTagProtected(GitLabApi gitlabApi, int gitlabProjectId, GitTag tag) {
        TagsApi tagsApi = gitlabApi.getTagsApi();
        try {
            tagsApi.getProtectedTag(gitlabProjectId, tag.getName());
            return true;
            //gitlabApi.getTagsApi().unprotectTag(gitlabProjectId, protectedTag.getName());
        } catch (GitLabApiException e) {
            if (e.getHttpStatus() == 404 && e.getReason().equals("Not Found")) {
                return false;
            }
            System.out.println("[w] Can't determine whether tag is protected! " + e.getMessage());
        }
        return false;
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

}
