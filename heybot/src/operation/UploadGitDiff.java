package operation;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelSftp.LsEntry;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.SftpProgressMonitor;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;
import org.apache.commons.lang3.StringUtils;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import utilities.Properties;

public class UploadGitDiff extends Operation
{

    // mandatory
    private final static String PARAMETER_REMOTE_HOST = "REMOTE_HOST";
    private final static String PARAMETER_REMOTE_USER = "REMOTE_USER";
    private final static String PARAMETER_REMOTE_PASS = "REMOTE_PASS";
    private final static String PARAMETER_MODE = "MODE";

    // optional
    private final static String PARAMETER_REMOTE_PORT = "REMOTE_PORT";
    // P2P
    private final static String PARAMETER_REMOTE_PATH = "REMOTE_PATH";
    private final static String PARAMETER_SOURCE_PATH = "SOURCE_PATH";
    // W2W
    private final static String PARAMETER_SOURCE_WORKSPACE = "SOURCE_WORKSPACE";
    private final static String PARAMETER_REMOTE_WORKSPACE = "REMOTE_WORKSPACE";
    private final static String PARAMETER_PROJECT_PATH = "PROJECT_PATH";
    private final static String PARAMETER_ISSUE = "ISSUE";

    private Status status;

    public UploadGitDiff() {
        super(new String[]{
            PARAMETER_REMOTE_HOST, PARAMETER_REMOTE_USER, PARAMETER_REMOTE_PASS, PARAMETER_MODE
        });
    }

    @Override
    protected void execute(Properties prop) throws Exception {
        if (areMandatoryParametersNotEmpty(prop)) {
            switch (getParameterString(prop, PARAMETER_MODE, true)) {
                case "p2p":
                    executeInP2PMode(prop);
                    break;
                case "w2w":
                    executeInW2WMode(prop);
                    break;
                default:
                    System.out.println("[e] Unknown working MODE! Eligible values: p2p, w2w");
                    break;
            }
        }
    }

    private boolean hasLocalWorkingCopyChanges(File sourceDir) {
        System.out.println("[*] Checking local working copy changes ...");
        try (Repository gitRepo = openRepository(sourceDir.getAbsolutePath())) {
            try (Git git = new Git(gitRepo)) {
                status = git.status().call();
                if (status.isClean()) {
                    System.out.println("\t[i] Nothing to upload, working tree clean.");
                } else {
                    if (status.getConflicting().size() > 0) {
                        listFiles("Conflicting", status.getConflicting());
                        System.out.println("\t[e] Please, resolve conflicts before upload.");
                    } else {
                        int count = 0;
                        count += listFiles("Added", status.getAdded());
                        count += listFiles("Changed", status.getChanged());
                        count += listFiles("Modified", status.getModified());
                        count += listFiles("Untracked", status.getUntracked());
                        count += listFiles("Removed", status.getRemoved());
                        count += listFiles("Missing", status.getMissing());
                        System.out.println("[✓] Total changes: " + count);
                        return count > 0;
                    }
                }
            } catch (GitAPIException ex) {
                System.out.println("\t[e] Git status failed! " + ex.getClass().getCanonicalName() + " " + ex.getMessage());
            }
        } catch (IOException ex) {
            System.out.println("\t[e] Git status failed! " + ex.getClass().getCanonicalName() + " " + ex.getMessage());
        }
        return false;
    }

    private Repository openRepository(String path) throws IOException {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        builder.setGitDir(new File(path + "/.git"));
        builder.setMustExist(true);
        return builder.readEnvironment().build();
    }

    private int listFiles(String title, Set<String> files) {
        if (files.size() > 0) {
            System.out.println("\t" + title + ": " + files.size());
            files.forEach((file) -> {
                System.out.println("\t\t" + file);
            });
        }
        return files.size();
    }

    private void executeInP2PMode(Properties prop) {
        System.out.println("[i] MODE: p2p (path to path)");
        File sftpSourceDir = getSftpSourceDirInP2PMode(prop);
        if (!sftpSourceDir.exists()) {
            System.out.println("[e] Couldn't find or detect any existing SOURCE_PATH to upload!");
            return;
        }
        System.out.println("[i] Source Path: " + sftpSourceDir.getAbsolutePath());
        if (hasLocalWorkingCopyChanges(sftpSourceDir)) {
            File sftpRemoteDir = getSftpRemoteDirInP2PMode(prop);
            if (sftpRemoteDir != null) {
                upload(prop, sftpSourceDir, sftpRemoteDir);
            } else {
                System.out.println("[e] Couldn't find any REMOTE_PATH to upload!");
            }
        }
    }

    private File getSftpSourceDirInP2PMode(Properties prop) {
        String sourcePath = getParameterString(prop, PARAMETER_SOURCE_PATH, false);
        if (StringUtils.isBlank(sourcePath)) {
            System.out.println("[*] Auto detecting source path ...");
            sourcePath = System.getProperty("user.dir");
        }
        return new File(sourcePath);
    }

    private File getSftpRemoteDirInP2PMode(Properties prop) {
        String remotePath = getParameterString(prop, PARAMETER_REMOTE_PATH, false);
        if (!StringUtils.isBlank(remotePath)) {
            return new File(remotePath);
        }
        return null;
    }

    private void upload(Properties prop, File sftpSourceDir, File sftpRemoteDir) {
        System.out.println("[*] Uploading changes ...");
        String sftpUser = getParameterString(prop, PARAMETER_REMOTE_USER, false);
        String sftpPass = getParameterString(prop, PARAMETER_REMOTE_PASS, false);
        int sftpPort = getParameterInt(prop, PARAMETER_REMOTE_PORT, 22);
        System.out.println("\t[i] Port: " + sftpPort);
        System.out.println("\t[i] User: " + sftpUser);
        System.out.println("\t[i] Pass: " + StringUtils.repeat("*", sftpPass.length()));
        String[] sftpHosts = getParameterStringArray(prop, PARAMETER_REMOTE_HOST, false);
        for (String sftpHost : sftpHosts) {
            upload(sftpSourceDir, sftpRemoteDir, sftpHost, sftpPort, sftpUser, sftpPass);
        }
    }

    private void upload(File sftpSourceDir, File sftpRemoteDir, String sftpHost, int sftpPort, String sftpUser, String sftpPass) {
        ChannelSftp channelSftp = null;
        Channel channel = null;
        Session session = null;
        try {
            System.out.println("\t==========================[ FTP ]==========================");
            System.out.println("\t[i] Host: " + sftpHost + getHostIPAddress(sftpHost));
            System.out.println("\t[*] Connecting ...");
            JSch jsch = new JSch();
            session = jsch.getSession(sftpUser, sftpHost, sftpPort);
            session.setPassword(sftpPass);
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect();
            channel = session.openChannel("sftp");
            channel.connect();
            System.out.println("\t[✓] Ok.");
            System.out.println("\t[*] Changing path ...");
            channelSftp = (ChannelSftp) channel;
            channelSftp.cd(sftpRemoteDir.getAbsolutePath());
            System.out.println("\t[✓] Path: " + sftpRemoteDir.getAbsolutePath());

            insert(channelSftp, sftpSourceDir, status.getAdded(), "Added");
            insert(channelSftp, sftpSourceDir, status.getChanged(), "Changed");
            insert(channelSftp, sftpSourceDir, status.getModified(), "Modified");
            insert(channelSftp, sftpSourceDir, status.getUntracked(), "Untracked");
            HashSet<String> parents = new HashSet<>();
            delete(channelSftp, sftpSourceDir, status.getRemoved(), "Removed", parents);
            delete(channelSftp, sftpSourceDir, status.getMissing(), "Missing", parents);
            for (String parent : parents) {
                deleteParent(channelSftp, sftpSourceDir, new File(parent));
            }

            System.out.println("\t[✓] All changes are uploaded. \\o/ ");
        } catch (JSchException | SftpException | FileNotFoundException ex) {
            System.out.println("\t[e] Upload failed! " + ex.getClass().getCanonicalName() + " " + ex.getMessage());
        } finally {
            System.out.println("\t[*] Disconnecting ...");
            if (channelSftp != null) {
                channelSftp.disconnect();
            }
            if (channel != null) {
                channel.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
            System.out.println("\t[✓] Ok.");
        }
    }

    private void insert(ChannelSftp channelSftp, File sftpSourceDir, Set<String> files, String group) throws SftpException, FileNotFoundException {
        if (files.size() > 0) {
            System.out.println("\t[*] " + group + " ...");
            for (String file : files) {
                insert(channelSftp, sftpSourceDir, file);
            }
        }
    }

    private void insert(ChannelSftp channelSftp, File sftpSourceDir, String file) throws SftpException, FileNotFoundException {
        File f = new File(file);
        insertParent(channelSftp, f.getParentFile());
        System.out.println("\t\t[+] " + file);
        channelSftp.put(new FileInputStream(sftpSourceDir.getAbsolutePath() + "/" + file), file, new UploadProgressMonitor(), ChannelSftp.OVERWRITE);
    }

    private void insertParent(ChannelSftp channelSftp, File parent) throws SftpException {
        if (parent != null) {
            String path = parent.getPath();
            try {
                channelSftp.stat(path);
            } catch (SftpException ex) {
                if (ex.id == 2) {
                    insertParent(channelSftp, parent.getParentFile());
                    System.out.println("\t\t[+] " + path);
                    System.out.print("\t\t|-> ");
                    channelSftp.mkdir(path);
                    System.out.println("[✓] mkdir.");
                }
            }
        }
    }

    private void delete(ChannelSftp channelSftp, File sftpSourceDir, Set<String> files, String group, HashSet<String> parents) throws SftpException, FileNotFoundException {
        if (files.size() > 0) {
            System.out.println("\t[*] " + group + " ...");
            for (String file : files) {
                delete(channelSftp, sftpSourceDir, file, parents);
            }
        }
    }

    private void delete(ChannelSftp channelSftp, File sftpSourceDir, String file, HashSet<String> parents) throws SftpException, FileNotFoundException {
        System.out.println("\t\t[-] " + file);
        File f = new File(file);
        try {
            System.out.print("\t\t|-> ");
            channelSftp.rm(file);
            System.out.println("[✓] removed.");
        } catch (SftpException ex) {
            if (ex.id == 2) {
                System.out.println("[✓] removed.");
            } else {
                System.out.println("[x] " + ex.getMessage() + " " + ex.getClass().getCanonicalName());
            }
        }
        if (f.getParentFile() != null) {
            parents.add(f.getParent());
        }
    }

    private void deleteParent(ChannelSftp channelSftp, File sftpSourceDir, File parent) throws SftpException {
        if (parent != null) {
            String path = parent.getPath();
            if (!new File(sftpSourceDir.getAbsolutePath() + "/" + path).exists()) {
                try {
                    channelSftp.stat(path);
                } catch (SftpException e) {
                    System.out.println("\t\t[-] " + path);
                    System.out.print("\t\t|-> ");
                    System.out.println("[✓] removed.");
                    return;
                }
                @SuppressWarnings("unchecked")
                Vector<LsEntry> ls = channelSftp.ls(path);
                int size = 0;
                if (ls != null) {
                    size = ls.size();
                    for (LsEntry e : ls) {
                        if (e.getFilename().equals(".") || e.getFilename().equals("..")) {
                            size--;
                        }
                    }
                }
                if (size == 0) {
                    System.out.println("\t\t[-] " + path);
                    try {
                        System.out.print("\t\t|-> ");
                        channelSftp.rmdir(path);
                        System.out.println("[✓] removed.");
                    } catch (SftpException ex) {
                        if (ex.id == 2) {
                            System.out.println("[✓] removed.");
                        } else {
                            System.out.println("[x] " + ex.getMessage() + " " + ex.getClass().getCanonicalName());
                        }
                    }
                }
                deleteParent(channelSftp, sftpSourceDir, parent.getParentFile());
            }
        }
    }

    private void executeInW2WMode(Properties prop) {
        System.out.println("[i] MODE: w2w (workspace to workspace)");

        int issue = getParameterInt(prop, PARAMETER_ISSUE, 0);
        System.out.println("[i] Issue: " + issue);
        if (issue == 0) {
            System.out.println("[e] ISSUE parameter can't be blank in w2w working mode!");
            return;
        }

        String projectPath = getProjectPath(prop, issue);
        System.out.println("[i] Project Path: " + projectPath);
        if (StringUtils.isBlank(projectPath)) {
            System.out.println("[e] PROJECT_PATH parameter can't be blank in w2w working mode!");
            return;
        }

        File sftpSourceDir = getSftpSourceDirInW2WMode(prop, projectPath);
        System.out.print("[i] Source Path: ");
        if (StringUtils.isBlank(sftpSourceDir.getPath())) {
            System.out.println("\n[e] Couldn't find any existing project in SOURCE_WORKSPACE to upload!");
            return;
        }
        System.out.println(sftpSourceDir.getAbsolutePath());
        if (!sftpSourceDir.exists()) {
            System.out.println("[e] Couldn't find any existing project in SOURCE_WORKSPACE to upload!");
            return;
        }

        if (hasLocalWorkingCopyChanges(sftpSourceDir)) {
            File sftpRemoteDir = getSftpRemoteDirInW2WMode(prop, projectPath);
            System.out.print("[i] Remote Path: ");
            if (StringUtils.isBlank(sftpRemoteDir.getPath())) {
                System.out.println("\n[e] Couldn't find any REMOTE_WORKSPACE to upload!");
                return;
            }
            System.out.println(sftpRemoteDir.getAbsolutePath());
            upload(prop, sftpSourceDir, sftpRemoteDir);
        }
    }

    private String getProjectPath(Properties prop, int issue) {
        String projectPath = getParameterString(prop, PARAMETER_PROJECT_PATH, false);
        if (!StringUtils.isBlank(projectPath)) {
            Velocity.init();
            VelocityContext context = new VelocityContext();
            context.put("issue", issue);
            StringWriter writer = new StringWriter();
            Velocity.evaluate(context, writer, "TemplateName", projectPath);
            projectPath = writer.toString();
            projectPath = StringUtils.strip(projectPath, "/");
        }
        return projectPath;
    }

    private File getSftpSourceDirInW2WMode(Properties prop, String projectPath) {
        String sourceWorkspace = getParameterString(prop, PARAMETER_SOURCE_WORKSPACE, false);
        System.out.println("[i] Source Workspace: " + sourceWorkspace);
        if (!StringUtils.isBlank(sourceWorkspace)) {
            return new File(sourceWorkspace + "/" + projectPath);
        }
        return new File("");
    }

    private File getSftpRemoteDirInW2WMode(Properties prop, String projectPath) {
        String remoteWorkspace = getParameterString(prop, PARAMETER_REMOTE_WORKSPACE, false);
        System.out.println("[i] Remote Workspace: " + remoteWorkspace);
        if (!StringUtils.isBlank(remoteWorkspace)) {
            return new File(remoteWorkspace + "/" + projectPath);
        }
        return new File("");
    }

    public class UploadProgressMonitor implements SftpProgressMonitor
    {

        public UploadProgressMonitor() {
        }

        public void init(int op, java.lang.String src, java.lang.String dest, long max) {
            System.out.print("\t\t|-> ");
        }

        private long total;

        @Override
        public boolean count(long bytes) {
            total += bytes;
            return (true);
        }

        @Override
        public void end() {
            System.out.println("[✓] " + total + " bytes sent.");
        }
    }

}
